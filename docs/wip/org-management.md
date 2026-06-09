# Organization Management — Technical Specification

This document describes how organization management works in LIPAS: the data
model, the permission engine, and the organization-management UI. It is the
technical companion to the high-level overview in `docs/organizations.md`.

Scope: the org data model + the permission-resolution engine + the
org-management UI.

---

## 1. What this provides

LIPAS has organizations and a user-level permission system, and they are
connected:

- Sites carry an **org reference**, so "which facilities does org X own" is a
  cheap query.
- Org membership can confer **editing** rights, scoped by data.
- The `org` table is an **append-only revision log** — every change is an
  auditable revision.

The system meets these goals:

1. **Q1** — answer "which facilities are owned by org X" efficiently (ES).
2. **Q2** — answer "who can edit site Z" (orgs *and* legacy users).
3. **Immutable org history** — every change (config *and* membership) is an
   append-only, auditable revision, mirroring the `sports_site` event log.
4. **Org self-service** — org admins manage members and grant edit on their sites
   to other orgs, without a LIPAS admin in the loop.
5. **Opt-in / additive** — the user-level permission system is independent; a user
   with no org membership behaves exactly as a user always has.
6. **Elegant simplicity** — one frozen matching rule; authority is data.

### Non-goals (v1)

- Org hierarchies (ylä-/ala-organisaatiot). The one feature that would justify a
  rules engine; not built.
- Per-member permission overrides — a member receives each assigned role's grants
  uniformly (no per-member tweaking of what a role confers).
- Approval workflows on edits — none (bottlenecks reduce data freshness). Take-over
  *ownership* has a light approval flow; per-edit approval does not exist.
- Migrating away from direct user roles — they remain first-class.

---

## 2. Design principles

### 2.1 Authority is **data**, matched by **one frozen rule**

The matching engine (`lipas.roles/select-role` + `check-privilege`) has **no
org-specific concepts**. Authority is role data with context sets
(`:city-code :type-code :lipas-id :activity :org-id`); a single intersection rule
decides access. `:org-id` is **multi-valued (set-intersection), exactly like
`:activity`** — because a *resource* (site) can have several editor orgs (owner +
grants). Per-org variation is expressed as data (the role-template catalog), never
as bespoke rules.

> Litmus test: anything answerable in both directions (forward "what can X edit" /
> reverse "who can edit Z") and shown in the FE is **data matched by the rule**,
> compiled to an ES query for the reverse direction — never a rule that computes.

### 2.2 Derived permissions are **computed at login, never persisted**

The only org fact stored is **membership** (in the org document, §4). Everything a
membership confers — editor / activity / ptv roles from the template — is
**projected at token-creation time and lives only in the JWT** (≤6h life).

Consequence: there is **no stored derived state to drift**. A template edit or
ownership-rule change propagates to everyone at their next token refresh, with zero
recompute machinery. The per-request `token-backend` path is untouched; only login
does the extra read.

### 2.3 Immutability via append-only revisions (mirror `sports_site`)

- **Org state** (config + membership) → append-only `org` revisions + `org_current` view.
- **Site ownership & edit-grants** → fields on the **site document**, riding the
  existing `sports_site` event log.

Two history surfaces, both copies of a production pattern. No bespoke audit tables.

### 2.4 Opt-in is **structural**, not conditional

Orgs reference users; the **user document is never touched by org code**. There is
no `if org-enabled?` branch — the legacy path is simply the empty-projection case
(no membership → no derived roles → today's behavior exactly).

### 2.5 Bounded delegation — the org-admin ceiling is **data**

Org admins self-serve, capped by a **role catalog** only a **lipas-admin** can edit.
An org admin may *assign* roles to members and invite/remove members — never *define*
what a role grants, nor escalate beyond the catalog. Enforced **structurally at
projection**: `derive-org-roles` only expands roles found in the catalog (plus the
reserved engine roles, §3.5), so an out-of-catalog assignment yields nothing.

A member holds a single flat `:roles` list. Its entries are drawn from two sources:

- **reserved engine roles** — `:admin` (→ `:org-admin`/`:org/manage`). Defined in the
  role registry, **not** in the per-org catalog, so a lipas-admin's wholesale catalog
  edit can never strip an org of its admins.
- **catalog (data-edit) roles** — `:editor`/`:ptv`/`:melonta`/… defined per-org by
  lipas-admin (the ceiling).

Plain membership (presence in `:members`) confers the **`:org/member` baseline**
(view the org) with no role assigned — so a member with `:roles []` can still see
their org. This baseline and the reserved `:admin` role are the only two authorities
that do not come from the catalog.

---

## 3. Core model

### 3.1 Org as append-only revisions

```
org (revision table)
  id          uuid PK     -- per-revision id
  org_id      uuid        -- STABLE org identity (== old org.id), preserved across revisions
  event_date  timestamptz
  created_at  timestamptz
  author_id   uuid        -- who made the change (the audit actor)
  status      text        -- 'active' | 'archived'
  document    jsonb       -- full org state snapshot (see 3.2)

org_current (view) -- latest active revision per org_id (mirror of sports_site_current)
```

Every change — rename, contact edit, PTV config, template change, **member
add/remove**, instructions edit, ownership-rule change, archival — is a **new
revision** authored by the actor. The diff between consecutive revisions *is* the
audit log.

### 3.2 The org document

```clojure
{:name "Oulun kaupunki"
 :type :city                  ; :city | :municipal-consortium | :state | :private | :sports-federation
                              ; drives owner-enum locking (§3.6) and UI defaults

 :primary-contact {:phone .. :email .. :website .. :reservations-link ..}

 :instructions {:fi ".." :se ".." :en ".."}   ; member-facing guidance (org-admin writes; §6.4)

 :ptv-data { ... }            ; inside the document

 ;; --- the CEILING: named data-edit role catalog, editable only by lipas-admin ---
 :role-templates
 {:editor  {:label "Muokkaaja"     :roles [{:role :org-editor}]}           ; org-id injected at projection
  :ptv     {:label "PTV-hallinta"  :roles [{:role :ptv-manager :city-code [564]}]}
  :melonta {:label "Melonta (UTP)" :roles [{:role :activities-manager :activity ["melonta"]}]}}

 ;; --- which sites this org may claim ownership of (bulk take-over rule, §6.5) ---
 :ownership {:city-codes [564] :type-codes [] :owners ["city" "city-main-owner"]}

 ;; --- membership + per-member ASSIGNED ROLES (one list; set by org-admin) → immutable history ---
 :members
 [{:user-id #uuid "..." :roles [:admin :ptv]}   ; :admin = reserved engine role (org management)
  {:user-id #uuid "..." :roles [:melonta]}        ; plain member + a catalog data role
  {:user-id #uuid "..." :roles []}]}              ; plain member, view-only (baseline)
```

One per-member field, **`:roles`** — a flat list drawn from `#{:admin}` ∪ keys of
`:role-templates`. The **`:role-templates`** catalog (the *ceiling*, lipas-admin only)
defines what each *data* role grants; the reserved **`:admin`** role lives in the role
registry (§3.5), not the catalog. Assigning `:admin` makes a member an org-admin
(`:org/manage`); assigning data roles grants editing; assigning nothing leaves the
member with the `:org/member` view baseline that membership alone confers.

There is **no separate "org role" field** — management is just one more assignable
role (§2.5). Membership-in-document makes member + assignment history immutable, with
no separate membership audit log.

### 3.3 Membership — resolved at login only

The account stores **only direct (legacy) roles**. No `:org-id` roles are persisted
on accounts. At login, `org/derive-user-org-roles db user-id` reads `org_current`
rows whose `document.members[].user-id` contains the user and projects that member's
assigned roles (the `:org/member` baseline + reserved/catalog roles) into role data,
baked into the JWT.

### 3.4 Site ownership & edit-grants — fields on the site document

```clojure
{:owner-org-id #uuid "..."         ; nil for un-owned (legacy) sites
 :edit-grants  [#uuid "..." ...]}   ; orgs granted edit by the owner (cross-org collaboration)
```

In the document → ownership history, "on whose behalf" (`acting_org_id`), and ES
indexing come from the existing `sports_site` revision log.

### 3.5 The org roles in the registry

Three registry roles back org membership; all are projected with `:org-id` context
and matched by the one rule. None is hand-assigned to *accounts* — they are projected
at login from the member's `:roles` list (or, for the baseline, from mere membership).

```clojure
:org-editor                        ; the one genuinely new role (ownership-scoped edit)
{:sort 62
 :assignable false                 ; never on an account; only projected from a catalog role
 :catalog-assignable true
 :privileges lipas.roles/basic     ; :site/create-edit :activity/view :analysis-tool/use
 :required-context-keys [:org-id]
 :optional-context-keys []}

:org-admin                         ; reserved management role — :roles [:admin] projects this
{:assignable false                 ; membership-only; never hand-assigned to an account
 :privileges #{:org/manage :org/member}
 :required-context-keys [:org-id]} ; org-scoped (org-id injected at projection)

:org-user                          ; the view baseline — projected from mere membership, never assigned
{:assignable false
 :privileges #{:org/member}
 :required-context-keys [:org-id]}
```

`:org-editor` is the only thing the existing city/type/lipas-id roles couldn't express
(ownership-scoped edit). UTP reuses `:activities-manager`; PTV reuses `:ptv-manager`.
`:admin` is a **reserved role key** (engine-level, not in any org's catalog) so a
catalog edit can never remove it; `:org-user` is the floor conferred by membership.
All three org-scoped roles are **`:assignable false`** — they exist only as
projections of membership, so the admin role editor never offers them and they can't
be granted on an account directly.

**Save-endpoint gate.** `:org-editor`'s privileges are `lipas.roles/basic`, which is
`#{:site/create-edit :activity/view :analysis-tool/use}` — note it does **not** carry
`:site/save-api`. The save endpoint (`core/check-permissions!`) accepts
`:site/create-edit` **OR** `:site/save-api`, so a general editor needs no separate
save-api grant. `:site/save-api` is carried only by the aspect-specific editor roles
(`:activities-manager` / `:floorball-manager` / `:itrs-assessor`) that may persist a
partial edit without being general editors.

### 3.6 Owner-enum locking

When a site is org-owned, its `:owner` enum is **locked** to the value implied by
the owning org's `:type`, via `lipas.data.owners/org-type->owner` (shared by backend
and FE so the form derives the same value):

| Org type | Locked `:owner` |
|---|---|
| `city` | `city` |
| `municipal-consortium` | `municipal-consortium` |
| `state` | `state` |
| `private` | `company-ltd` |
| `sports-federation` | `registered-association` |

Enforced on save by `core/check-owner-lock!` (throws `:owner-locked` on divergence;
no-op for non-org-owned sites).

---

## 4. Permission resolution flow

### 4.1 Projection (the only org-specific logic)

```clojure
(def org-scoped-roles #{:org-editor :org-admin})   ; org-id injected for these

(def reserved-roles                                  ; engine roles, not in any catalog
  {:admin {:role "org-admin"}})

(defn- member->roles [org-id catalog member]
  (let [org-id-vec (vector (str org-id))
        baseline   {:role "org-user" :org-id org-id-vec}   ; membership ⇒ :org/member
        assigned   (->> (:roles member)                    ; the single assigned-role list
                        (mapcat (fn [k]
                                  (or (some-> (reserved-roles (keyword k)) vector)  ; reserved (:admin)
                                      (:roles (get catalog (keyword k))))))         ; <- CEILING here
                        (remove nil?))]
    (->> (cons baseline assigned)
         (map (fn [spec]
                (cond-> spec
                  (org-scoped-roles (keyword (:role spec))) (assoc :org-id org-id-vec)))))))
```

The double miss (neither `reserved-roles` nor `catalog`) is the structural ceiling:
a role key that is neither reserved nor in the catalog expands to nothing. The
`:org-user` baseline comes from membership itself, independent of `:roles`. All
produced fresh, none persisted.

### 4.2 Login wiring

In basic-auth / login / token-refresh, before `jwt/create-token`:

```clojure
(update-in user [:permissions :roles] into (org/derive-user-org-roles db (:id user)))
```

`derive-user-org-roles` = reverse jsonb-containment query over `org_current`
(GIN-indexable), then `derive-org-roles`. Persisted account doc never modified.

### 4.3 Matching

- `site-roles-context` emits `:org-id` as a **set** of the site's editor orgs:
  ```clojure
  :org-id (some-> (concat (some-> site :owner-org-id vector) (:edit-grants site)) set not-empty)
  ```
- `select-role` `:org-id` branch is **set-intersection** (one line, mirrors `:activity`).
- The scalar `:org-id` role-contexts at org-management call sites are wrapped to sets
  (`{:org-id #{(str …)}}`). **Any `:org-id` role-context passed to `check-privilege`
  MUST be a set** — a scalar throws. (Handled in `lipas.ui.org.subs`.)
- `check-privilege`, `check-permissions!`, and the per-request auth path are untouched.

### 4.4 ES (reverse / bulk direction)

- `enrich*` indexes `search-meta.owner-org-id` and `search-meta.editor-org-ids`.
- `wrap-es-query-site-has-privilege` has an `:org-id` term branch, so an `:org-editor`
  role compiles to `owner-org-id OR edit-grants` term filters.

### 4.5 Ownership & edit-grant authorization (the business rule)

`:owner-org-id` and `:edit-grants` are security-sensitive (they decide cross-org edit
access) yet live on the same site document as ordinary content, so the generic save
endpoint must not let them be used to grant access. The rule is one place — pure
predicates in `lipas.backend.core`, authorized against the **stored** site (the
current revision), never the submitted body:

```clojure
(lipas-admin? user)                                   ; :users/manage
(owns-site-org? user owner-org-id)                    ; :org/manage on the OWNING org
(ownership-change-authorized? user creating? prev next)
  ;; unchanged → ok; creating → may claim an org you can :site/create-edit on (or none);
  ;; existing-site change → LIPAS admin only (others use the take-over flow, §6.5)
(edit-grant-change-authorized? user owner-org-id)     ; lipas-admin OR owning-org admin
```

- **`upsert-sports-site!`** loads the stored site and (a) checks content-edit
  permission against the **stored** context — so a caller can't manufacture
  `:site/save-api` by injecting their org into the body's `:owner-org-id`/`:edit-grants`
  (the matcher's `:org-id` set comes from the stored site); and (b) rejects any
  unauthorized change to `:owner-org-id`/`:edit-grants`. Permission is checked before
  the existence check, so a posted-but-missing `lipas-id` still 403s before 404.
- **`grant-site-edit!` / `revoke-site-edit!`** both gate through
  `edit-grant-change-authorized?`, so granting and revoking share one rule.

Invariants (exhaustively unit-tested in `business-logic-test`, with handler regression
tests in `org-test`): only a LIPAS admin changes an existing site's owner; only the
owning-org admin (or LIPAS admin) changes grants; a new site may be owner-claimed for
an org the creator can edit; org-editors edit content but cannot alter ownership/grants.

---

## 5. Answering the two questions

**Q1 — facilities owned by / editable by org X**: ES term query on
`search-meta.owner-org-id` (owned) or `editor-org-ids` (owned ∪ granted). O(query).

**Q2 — who can edit site Z** (`core/site-editors`, returned by `POST /actions/site-editors`):
1. **owner org** — off the site document.
2. **grantee orgs** — off `:edit-grants`.
3. **activity-editor orgs** — enumerate org catalogs whose template grants an activity
   the site has (orgs ≈ hundreds, trivial).
4. **legacy direct users** — see §5.1.

The UI shows orgs by name (no member expansion — by product decision) plus the
legacy individual users.

### 5.1 Legacy direct-user lookup

`db/users-with-permissions-matching` pre-filters candidate accounts with jsonb
containment on `account.permissions`, then `core/site-editors` confirms with the
exact `check-privilege`:

- **Candidate pre-filter** — `permissions @> {"roles":[{"city-code":[C]}]}` (and
  `type-code`, `lipas-id`, `activity`), narrowing ~2.3k accounts → a handful.
  Containment params are Clojure maps (auto-converted to jsonb by `ISQLValue`).
  - **Both key spellings** (`city-code` *and* `city_code`, etc.) are queried,
    because the raw JSONB column carries both; the app normalizes (kebab) only on
    read (`db/user.clj` `unmarshall`).
- **Exact check** — candidates are normalized (`->kebab-case-keywords` +
  `conform-roles`) and passed through `roles/check-privilege ctx :site/create-edit`.
  This handles the matcher's "absent context key = matches any" semantics that SQL
  containment can't.
- **Admins excluded** — they can edit everything; listing them under every site is
  noise.
- **Index** — `account_permissions_gin` (`gin (permissions jsonb_path_ops)`). The
  planner uses it for selective predicates (Bitmap Index Scan); for broad activity
  predicates it may seq-scan, still ms-scale at current size.

**Caveats:** admins are excluded by design; a hypothetical *context-free* non-admin
edit role (all context keys absent) would be missed by the value pre-filter — but
in practice every edit role carries city/type/lipas-id/activity.

---

## 6. The UI

Base files: `src/cljs/lipas/ui/org/{views,events,subs}.cljs`. Reagent 2.0 `r/defc` +
`reagent.hooks`, explicit MUI requires.

### 6.1 Principles

1. **One interface, capability-gated.** LIPAS admins and org admins use the *same*
   screens; every control is shown/enabled by a capability check.
2. **Make the ceiling visible.** Org admins assign from a catalog shown read-only.
3. **Data-collection first.** Org features never block site editing; a member with no
   org needs nothing here.
4. **Transparency over control.** Owners *see* who can edit their sites, even where
   they don't *manage* it.
5. **History is first-class** (but admin-only — §6.6).

### 6.2 Capability model (`::subs/can? capability org-id`)

| Capability | lipas-admin | org-admin | member |
|---|---|---|---|
| `:org/create`, `:org/edit-type+ownership`, `:org/edit-catalog` | ✓ | – | – |
| `:org/edit-contact`, `:org/edit-ptv`, `:org/edit-instructions` | ✓ | ✓ | – |
| `:org/manage-members` (invite / remove / assign roles incl. `:admin`) | ✓ | ✓ | – |
| `:org/grant-site-edit` (cross-org) | ✓ | ✓ | – |
| `:org/view-history` | ✓ | ✓ | – |
| `:org/view` (roster, sites, catalog read-only) | ✓ | ✓ | ✓ |
| edit **direct** user-level roles (separate user-mgmt UI) | ✓ | – | – |

Direct user-permission editing stays in the existing admin user-management screen —
the *other* plane, intentionally not part of the org UI. That card is labelled **"Suorat
käyttöoikeudet"** with a pointer to the Organisaatiot view, and its role picker does
not offer the org-scoped roles (they are membership-only, §3.5).

### 6.3 Tabs (`org-view`)

Order: **Ohjeet · Kohteet · Yleistiedot · Jäsenet · Käyttöoikeudet · PTV · Historia**.

- **Ohjeet** is first (§6.4). **Kohteet** is the **default landing tab** (most-used);
  its data loads on org open via the normal tab path. *(Open item: whether Ohjeet, as
  the first tab, should also be the default landing — §10.)*
- **Historia** is shown only to admins (`:org/view-history`).
- Terminology: "Kohteet", "Käyttöoikeudet".

### 6.4 Ohjeet (instructions)

Org admin / lipas-admin write member-facing instructions, members read-only. Stored
in the org payload under `:instructions {:fi :se :en}` (malli schema + marshall +
`update-org!` whitelist; members/catalog preserved). The three language variants are
**language sub-tabs** (Suomi / Svenska / English) with one editable text area at a
time. Empty + read-only shows a "no instructions yet" message. Audit diff records
"Ohjeet päivitetty".

### 6.5 Yleistiedot (overview)

Name, **type**, primary contact. Contact editable by `:org/edit-contact`; **type +
ownership rule** editable only by lipas-admin (`:org/edit-type+ownership`),
read-only to org-admin (the ownership ceiling). The handler pins `:type`/`:ownership`
to current values for non-lipas-admins, so a non-admin edit is a no-op.

### 6.6 Jäsenet (members)

Unified **invite** control (email + a single **roles** multi-select). The role options
are one list: the reserved **Ylläpitäjä** (`:admin`) plus the org's catalog roles
(`:editor`/`:ptv`/…). Members table: one roles multi-select per member (add/remove,
same option list), remove. No separate "org role" control — admin is just a role in
the list. Members see the roster read-only. **No "pending invitations" table** — there
is no invitation table; an invited-but-not-logged-in user is just an account member.

**Adding a member (the invite flow).**
1. Inviter types an email; an existence probe (`POST /actions/check-is-existing-user`)
   decides the account path: an **unknown** address gets an account created on the fly
   and a magic-link email; a **known** one is simply added.
2. The roles multi-select **starts empty**. Empty = a **plain member** (the
   `:org/member` view baseline that membership alone confers). "Member" is not a
   pickable value — it is what you are by being in the org.
3. The inviter may grant any roles up front, **including `Ylläpitäjä` (`:admin`)** — so
   a lipas-admin can create an org's first admin in one step, and an org-admin can
   invite another admin, all through the same control. `:roles []` is a valid
   submission (membership-only viewer).
4. Submit → `POST /actions/invite-org-member` with `{:email :roles}`
   (catalog-validated: `:roles` ⊆ `#{:admin}` ∪ catalog keys).

### 6.7 Käyttöoikeudet (role catalog)

Read for everyone, write for lipas-admin (`:org/edit-catalog`). Lists each catalog
role (label · plain-language description · assigned-member count) — the data-edit
roles org admins assign from, made explicit. The reserved **Ylläpitäjä** (`:admin`)
role is shown here too for transparency but is **not catalog-editable** (engine-level),
so a catalog edit can never remove it.

**Self-describing roles.** Catalog grant text and the role-spec editor describe roles
and their privileges from i18n data, not hardcoded per-role cases:

- `role-grant-text` looks up a role's `:role-descriptions` entry, falling back to its
  translated name, so no catalog role renders as "Tuntematon oikeus".
- The role-spec editor lists the selected role's privileges inline ("Sisältää
  käyttöoikeudet:"), driven by a `:privilege-descriptions` i18n map covering all
  privileges — so a LIPAS admin doesn't have to remember what each role grants. The
  same primitive (`lipas.ui.roles.editor`) is shared by the org catalog editor and the
  admin user-management form.
- (Privilege keys are namespaced, e.g. `:site/create-edit`; tongue drops a namespaced
  leaf's namespace, so the i18n map munges `/` → `.` and the lookup mirrors it.)

**Validation & sanitation on save.**
- `update-catalog!` / `validate-catalog!` validate each spec beyond structural
  conformance: a context-scoped role must include its required context key (e.g. a
  `:ptv-manager` spec must carry `:city-code`), else it is rejected
  (`:invalid-catalog`). Without the key the role would project an unscoped, org-wide
  grant — `select-role` treats an *absent* context key as "matches anything". A
  present-but-empty value is allowed (it matches nothing; the admin fills it in later).
  `:org-id` is excepted — it's injected at projection.
- `sanitize-catalog` (FE, before save) drops half-filled rows and collapses
  exact-duplicate role-specs (`distinct`), so a template can never carry the same grant
  twice (it would render twice and grant nothing extra).

### 6.8 Kohteet (our sites) + who-can-edit

- One dataset: the org's **editable sites** (owned ∪ granted, each flagged `:owned?`),
  from bulk-operations. Owned vs shared, content filters, and bulk-edit selection are
  facets of this one list. Card site-count badge on the orgs list uses the same data.
- **Per-site "who can edit" drawer** (`site-editors-detail`, on row expand): a single
  scannable list answering "who can edit this site", each row tagged by **reason** with
  a tooltip:
  - **Omistaja** (owner org, primary color) — *"Tähän organisaatioon kuuluvat henkilöt
    saavat muokkausoikeuden organisaation kautta."*
  - **Jaettu** (grantee org; carries the revoke ✕) — *"Omistajaorganisaatio on jakanut
    muokkausoikeuden tälle organisaatiolle."*
  - **Aktiviteetti** (activity-editor org) — *"Organisaatio saa muokata kohteen
    aktiviteettitietoja vastuuaktiviteettiensa kautta."*
  - **Suora oikeus** (legacy direct user, by email) — *"LIPAS-ylläpidon suoraan
    henkilölle myöntämä muokkausoikeus."*
  - Below a divider, a **"Jaa muokkausoikeus toiselle organisaatiolle"** action
    (org selector + grant button) for those who can grant.
  - Fetched lazily, **one request per expand**, cached per lipas-id (accordion: one row
    open at a time). No N+1.
- **Manage edit-grants** on owned sites → `POST /actions/grant-site-edit` /
  `revoke-site-edit`.
- **Edit history** (timestamp + editor email) is shown in the same drawer, lazily
  fetched per expand via `POST /actions/get-site-edit-history` (any authenticated user).
- **Claim ownership** → `request-org-takeover` (org-admin) / `reclaim-org-sites`
  (lipas-admin direct); lipas-admin approval queue via `list-org-takeover-requests` +
  `approve-org-takeover` / `deny-org-takeover`.

### 6.9 Historia (audit) — admin-only

Reverse-chronological timeline over `org` revisions: who changed what when (contact,
catalog, members, role assignments, instructions, ownership rule, …). **UUIDs
are resolved to human-readable identifiers** — both revision authors and member
user-ids referenced in the diff resolve to email/username (one batched account
lookup in `get-history`). Admin-only (`:org/view-history`; route `:org/manage`).

### 6.10 Sports-site form — Omistajuus (ownership)

The site form's ownership section (`lipas.ui.sports-sites.views/ownership-fields`):

| Context | Owner-org autocomplete | Legacy "Omistaja" select |
|---|---|---|
| New site, owner-capable user | shown, **pre-filled when exactly one** ownable org; clearable | hidden when org set; shown when cleared |
| New site, no ownable orgs | – | shown (legacy) |
| Existing site, lipas-admin | **editable** (assign/change/clear ownership) | hidden when org set |
| Existing site, non-admin, org-owned | org name **read-only** | hidden |
| Existing site, non-admin, legacy | – | shown |
| Read-only view, org-owned | org name (read-only) | hidden |

- Selecting an org sets `:owner-org-id` **and** locks `:owner` to the org-type enum
  (§3.6, shared `org-type->owner`). Clearing reverts to the manual select.
- **Pre-fill:** new sites default to the single ownable org (org-owned) — the creator
  clears it to record a legacy/non-org owner (e.g. a private rink a city worker
  maintains on behalf of others). `::init-new-site` sets it; skipped when a template
  fixes `:owner` (analysis/planning sites).
- **Backend authz** — enforced in **core** against the stored site, not the request
  body (§4.5). A new site may be claimed for an org the creator can `:site/create-edit`;
  an existing site's owner can only be changed by a LIPAS admin (others use take-over);
  `:edit-grants` change only by the owning-org admin / LIPAS admin. Content edits are
  authorized against the stored site, so the body can't grant itself access.

### 6.11 Re-frame surface (selected)

Subs: `::can? ::user-orgs ::ownable-orgs ::org-templates ::org-history
::site-editors ::is-lipas-admin ::is-org-admin? ::is-org-member?`. Events:
`::invite-member ::set-member-roles ::remove-member
::edit-template-catalog ::grant-site-edit ::revoke-site-edit ::request-takeover
::approve-takeover ::deny-takeover ::edit-org ::save-org ::get-site-editors
::get-org-history`.

---

## 7. Endpoints (all under `/api`, mostly `POST /actions/*`)

| Method · path | Privilege | Notes |
|---|---|---|
| `POST /actions/get-current-user-orgs` | any | orgs the user belongs to (admins/ptv-auditors see all) |
| `POST /actions/get-all-orgs` | `:org/admin` | all orgs (lipas-admin) |
| `POST /actions/create-org` | `:org/admin` | create org |
| `POST /actions/update-org` | `:org/manage` | details; `:type`/`:ownership`/`:ptv-data` pinned for non-lipas-admins; members + catalog preserved |
| `POST /actions/get-org-members` | `:org/member` | member accounts + their `:roles` |
| `POST /actions/update-org-role-templates` | `:users/manage` | the data-role catalog (ceiling); validated + sanitized (§6.7) |
| `POST /actions/invite-org-member` | `:org/manage` | body `{:email :roles}`; catalog-validated; creates account + emails unknown addresses |
| `POST /actions/set-org-member-roles`, `DELETE …/:user-id` | `:org/manage` | assign `:roles` (incl. `:admin`), remove |
| `POST /actions/check-is-existing-user` | `:org/manage` | invite-form existence probe (boolean only; org-scoped) |
| `POST /actions/get-org-sites` | `:org/member` | owned / editable site lists (ES) |
| `POST /actions/get-org-sites-for-bulk` | `:site/create-edit` (org) | bulk-edit candidate sites |
| `POST /actions/get-site-editors` | (site-scoped) | Q2: owner + grantees + activity orgs + legacy users |
| `POST /actions/get-site-edit-history` | (any authenticated) | per-revision timestamp + editor email (Kohteet drawer) |
| `POST /actions/get-org-history` | `:org/manage` | revisions + computed diff (admin-only) |
| `POST /actions/grant-site-edit`, `revoke-site-edit` | `:org/manage` + core rule | cross-org grant / revoke; core authorizes against the **owning** org (§4.5) |
| `POST /actions/preview-org-takeover` | `:org/manage` | claim impact preview (count + relabel + sample) |
| `POST /actions/request-org-takeover` | `:org/manage` | org-admin requests to claim matching sites |
| `POST /actions/reclaim-org-sites` | `:org/admin` | lipas-admin direct reclaim (create+approve) |
| `POST /actions/list-org-takeover-requests`, `approve-org-takeover`, `deny-org-takeover` | `:org/admin` | lipas-admin queue; approve/deny require a still-`requested` row (guarded `requested→decided`; re-approve/double-claim → 409 `:invalid-takeover-state`) |
| `POST /sports-sites` | authenticated; **all authz in core** | save; core authorizes content + `:owner-org-id`/`:edit-grants` against the stored site (§4.5) |
| `POST /actions/update-org-ptv-config` | `:users/manage` | PTV config |

`:login-url` for invites must start with a known LIPAS domain
(`lipas.schema.handler/magic-link-login-url`).

---

## 8. Database

1. **`org` revision table + `org_current` view** — `org_id` == old `org.id` to
   preserve references. GIN index on `document` for the login reverse-membership query.
   - Members carry a single `:roles` list (`{:user-id :roles}`). The `:role-templates`
     catalog and `role-spec` shape are independent of the per-member field.
   - Org-scoped roles (`:org-admin`/`:org-user`/`:org-editor`) live **only** in
     membership/projection — they are not stored on `account.permissions`.
2. **Site document fields** — `:owner-org-id`, `:edit-grants` (jsonb; no schema
   migration). In the malli site schema. ES mapping carries
   `search-meta.owner-org-id` / `editor-org-ids`.
3. **`account_permissions_gin`** — GIN `jsonb_path_ops` on `account.permissions` for
   the legacy-editor lookup (§5.1).
4. **`acting_org_id`** on site revisions ("on whose behalf") — on the table; not in
   `sports_site_current` (blocked by dependent views; query the table for history).

Account org-scoped roles are not persisted: membership is the single source for them.
Other direct account roles (admin, type/city/ptv managers, etc.) are first-class.

---

## 9. Audit & history

| Change | Recorded as | Actor |
|---|---|---|
| Rename / contact / instructions / PTV / **catalog** edit | new `org` revision | `author_id` |
| Member add / remove / role assignment (incl. `:admin`) | new `org` revision (members in document) | `author_id` |
| Org archival | new `org` revision, `status 'archived'` | `author_id` |
| Site ownership take-over / transfer | new **site** revision (`:owner-org-id`) | site `author_id` |
| Cross-org edit grant / revoke | new **site** revision (`:edit-grants`) | site `author_id` |
| "On whose behalf" | optional `acting_org_id` on the site revision | — |

Two append-only logs, both pre-existing patterns. The org-history view diffs
consecutive revisions and resolves all referenced account ids to email/username. A
member role change renders as a single **"Roolit muuttui: «name» [a] → [b]"** line.

---

## 10. Open questions & decisions

1. **Default landing tab** — Ohjeet is the first tab; Kohteet is the default selected
   (most-used). Decide whether the first tab should also be the landing tab.
2. **Legacy-user email display (GDPR)** — emails are shown to admins only; **left
   as-is pending PM/DPO feedback**. Recommended mitigation if needed: name/username
   first (email for lipas-admin or behind a reveal) + audience scoping, **not** email
   masking (pseudonymisation ≠ anonymisation; defeats the purpose in a narrow
   audience). Do not change unprompted.
3. **Admins in the who-can-edit list** — currently excluded (they can edit
   everything). Option: include with an `Ylläpitäjä` tag.
4. **Take-over / transfer approval depth** — take-over has a requested→approved
   flow; transfer (owner → new owner) confirmation UX is light. Revisit if needed.
5. **Owner-enum locking** — hard-locked from `org.type` (current) vs default + warn.
6. **Revocation latency** — removing a member / unassigning a template takes effect
   at next token refresh (≤6h). Acceptable, or add an immediate-revoke path?
7. **Catalog editing scope** — lipas-admin-only (current). Keep, or allow a "lead"
   org-admin within a lipas-admin-set boundary? (Recommend keep — it's the ceiling
   guarantee.)
8. **Per-site ownership-claim requests (correction / cross-org transfer)** — today
   an org-admin has **no reachable claim trigger at all**: both entry points (the
   ownership-rule editor button and setup-checklist step ③) are lipas-admin-only,
   and claim scope is fixed to the org's ownership rule (rule itself is lipas-admin
   only). The `org_takeover_request` state machine *does* support an org-admin
   `request` path (`POST /actions/request-org-takeover`, `:org/manage`) — it just
   has no UI. Two follow-ups, sharing that existing machine:
   - **(a) Surface the org-admin bulk-request trigger** — a "request claim" button
     in Kohteet for `:org/manage` that opens the impact dialog in `request` mode.
     Closes the half-wired request→approve loop with no new backend.
   - **(b) Per-site claim of a site the org doesn't own** — for *wrong owner entered*
     and *private → municipality* transfers. A request row with a hand-picked
     `lipas-id` instead of a rule-expansion (same table, same approval queue, same
     `approve!` apply path; owner-enum lock relabels `:owner` to the org type in the
     same revision — exactly the private→city semantic). Open design surface:
     - *Contested vs un-owned* — claiming a **legacy/un-owned** site is a low-risk
       correction; claiming one **owned by another org** is a transfer *away* from
       org A → that owner should be notified (or consent), not silently reassigned.
     - *Guardrail* — fully open (admin judgment) vs a soft check that the site's
       city-code is one the requesting org plausibly governs (catches fat-finger
       requests for the wrong municipality).
     - *Entry point* — the site's who-can-edit drawer (`site-editors-detail`) or the
       site-form ownership section: a "Pyydä omistajuutta" action when an org-admin
       does **not** own the site.
     - *Request payload* — tag the row (`kind` / explicit `lipas-ids`) so the queue
       renders "bulk rule claim (N)" vs "single-site request: «name»", and the
       approver sees the current owner for the contested case.
9. **Org-site list result caps** — `get-org-sites` (`:size 2000`) and the bulk
   editable-sites query (`:size 10000`) silently truncate for a very large org
   (`:total` stays accurate via `track_total_hits`, but the returned list — and thus
   the bulk-edit authorized set — is capped). Edge case at current scale; add
   pagination or at least log truncation when it matters.

---

## 11. Performance, scaling & test coverage

### 11.1 Scaling (≈ ≤500 orgs, ~57k sites, users mostly in 1 org)

- **No per-site fan-out** — derived authority is O(orgs × templates), independent of
  owned-site count (org owning 50 000 sites → still 3 derived roles).
- JWT payload stays KB-scale even at pathological membership; `check-privilege` µs-scale.
- Login reverse-membership query GIN-indexed (`jsonb_path_ops`, Bitmap Index Scan), ms-scale.
- ES bulk filter (Q1) sub-millisecond at full site count.

### 11.2 Legacy-editor lookup (§5.1)

Real dev stack (2 346 accounts, ~2 021 with roles, 57 030 sites):

- **Correctness:** candidate pre-filter result == exhaustive naive scan, exactly
  (0 missing, 0 extra), across diverse sites.
- **Performance:** ~1–14 ms per site (reused connection) vs ~207 ms naive (which is
  dominated by loading *all* accounts into the JVM). The win is the `WHERE`-clause
  candidate filter (~50–264 rows vs 2 346), not the index per se.
- **Index:** Bitmap Index Scan for selective predicates (~1.9 ms); broad activity
  predicates seq-scan (~5 ms) at current table size.

### 11.3 Test coverage

`bb test-ns lipas.roles-test lipas.backend.org-test lipas.backend.business-logic-test`
— green. Coverage includes:
- **Ownership/edit-grant business rule (§4.5)** — `business-logic-test` exhaustively
  unit-tests the four predicates (unchanged / create / existing-change × admin /
  owner-admin / editor / nobody, incl. nil-owner edges); `org-test` adds handler
  regressions: an org-editor cannot self-authorize a foreign-site edit by injecting
  `:edit-grants`, cannot change ownership/grants of an existing site, but can edit its
  own org's site; grant *and* revoke require the owning-org admin.
- **Roles** — member fixtures/endpoints use `:roles`; projection baseline + reserved
  `:admin` + ceiling; a catalog edit can't strip `:admin`; invite `:roles []` (plain
  member) and `:roles ["admin"]` (first admin).
- **Catalog validation** — a context-scoped role missing its required key is rejected;
  present-but-empty is accepted.
- **Take-over** — approve claims matching sites; re-approving or denying a decided
  request is rejected (`:invalid-takeover-state`).
- Plus: owner-org assignment authz; history authz + member-email resolution; org
  instructions round-trip; site-editors composition + legacy-user lookup (candidate ==
  naive scan, admin excluded).

---

## 12. Invariants that keep this cheap forever

1. **One frozen matching rule** — future scenarios arrive as data (template entries,
   site fields), never new code branches.
2. **No persisted derived state** — derived roles exist only in tokens; nothing to
   reconcile.
3. **Two append-only logs, both pre-existing patterns** — history/audit is structural.
4. **User document untouched by org code** — opt-in and legacy compatibility by
   construction.
