# Organization Management — Design, UX & Implementation

Status: **Implemented** on branch `feat/org-management` (backend P1–P3, UI P1–P3,
dashboard read endpoints, site-form ownership, instructions, legacy-editor
lookup). This document consolidates and supersedes the three earlier working
docs (`org-management-design-spec.md`, `org-management-ux-plan.md`,
`org-management-ux-followup.md`).

Scope: data model + permission engine + the org-management UI. It records the
decisions already made, the model as built, where reality diverged from the
original plan, and the open items.

---

## 1. Background & goals

LIPAS has organizations and a user-level permission system, but historically they
were disconnected:

- Sites carried **no org reference** — no answer to "which facilities does org X own".
- Org membership granted only `:org/member` / `:org/manage` — never editing.
- The `org` table was a **single mutable row per org** — no history, no audit.

Goals (all addressed):

1. **Q1** — answer "which facilities are owned by org X" efficiently (ES). ✅
2. **Q2** — answer "who can edit site Z" (orgs *and* legacy users). ✅
3. **Immutable org history** — every change (config *and* membership) is an
   append-only, auditable revision, mirroring the `sports_site` event log. ✅
4. **Org self-service** — org admins manage members and grant edit on their sites
   to other orgs, without a LIPAS admin in the loop. ✅
5. **Opt-in / additive** — the existing user-level permission system is unchanged;
   a user with no org membership behaves exactly as before. ✅
6. **Elegant simplicity** — one frozen matching rule; authority is data. ✅

### Non-goals (v1)

- Org hierarchies (ylä-/ala-organisaatiot) — deferred. The one feature that would
  justify a rules engine; explicitly not built.
- Per-member permission overrides — a member receives the org's template uniformly.
- Approval workflows on edits — none (bottlenecks reduce data freshness). Take-over
  *ownership* has a light approval flow; per-edit approval does not exist.
- Migrating away from direct user roles — they remain first-class indefinitely.

---

## 2. Design principles (the decisions already made)

### 2.1 Authority is **data**, matched by **one frozen rule**

The matching engine (`lipas.roles/select-role` + `check-privilege`) gained **no new
concepts**. Authority is role data with context sets
(`:city-code :type-code :lipas-id :activity :org-id`); a single intersection rule
decides access. The one engine change was making `:org-id` **multi-valued
(set-intersection), exactly like `:activity`** — because a *resource* (site) can
have several editor orgs (owner + grants). Per-org variation is expressed as data
(the role-template catalog), never as bespoke rules.

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

Org admins self-serve, capped by a **role-template catalog** only a **lipas-admin**
can edit. An org admin may *assign* catalog templates to members and invite/remove
members — never *define* what a template grants, nor escalate beyond the catalog.
Enforced **structurally at projection**: `derive-org-roles` only expands templates
found in the catalog, so an out-of-catalog assignment yields nothing.

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

 :ptv-data { ... }            ; unchanged content; now inside the document

 ;; --- the CEILING: named template catalog, editable only by lipas-admin ---
 :role-templates
 {:editor  {:label "Muokkaaja"     :roles [{:role :org-editor}]}           ; org-id injected at projection
  :ptv     {:label "PTV-hallinta"  :roles [{:role :ptv-manager :city-code [564]}]}
  :melonta {:label "Melonta (UTP)" :roles [{:role :activities-manager :activity ["melonta"]}]}}

 ;; --- which sites this org may claim ownership of (bulk take-over rule, §6.5) ---
 :ownership {:city-codes [564] :type-codes [] :owners ["city" "city-main-owner"]}

 ;; --- membership + per-member template ASSIGNMENTS (set by org-admin) → immutable history ---
 :members
 [{:user-id #uuid "..." :org-role :admin  :templates [:editor :ptv]}    ; :org-role → mgmt capability
  {:user-id #uuid "..." :org-role :member :templates [:melonta]}]}      ; :templates ⊆ keys of :role-templates
```

Two levels of per-org data: **`:role-templates`** (the *ceiling*, lipas-admin only)
defines what each template grants; member **`:templates`** (set by org-admin) defines
who gets what, bounded by the catalog. Member **`:org-role`** (`:admin` → `:org/manage`,
`:member` → `:org/member`) is orthogonal to the edit/activity grants from templates.

Membership-in-document makes member + assignment history immutable, with no separate
membership audit log.

### 3.3 Membership — resolved at login only

The account stores **only direct (legacy) roles**. No `:org-id` roles are persisted
on accounts. At login, `org/derive-user-org-roles db user-id` reads `org_current`
rows whose `document.members[].user-id` contains the user and projects that member's
assigned catalog templates into role data, baked into the JWT.

### 3.4 Site ownership & edit-grants — fields on the site document

```clojure
{:owner-org-id #uuid "..."         ; nil for un-owned (legacy) sites
 :edit-grants  [#uuid "..." ...]}   ; orgs granted edit by the owner (cross-org collaboration)
```

In the document → ownership history, "on whose behalf" (`acting_org_id`), and ES
indexing come free from the existing `sports_site` revision log.

### 3.5 The one new role

```clojure
:org-editor
{:sort 62
 :assignable false                 ; never hand-assigned; only ever projected from a template
 :catalog-assignable true
 :privileges lipas.roles/basic     ; :site/save-api :site/create-edit :activity/view :analysis-tool/use
 :required-context-keys [:org-id]
 :optional-context-keys []}
```

The only thing the existing city/type/lipas-id roles couldn't express
(ownership-scoped edit). UTP reuses `:activities-manager`; PTV reuses `:ptv-manager`.

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

### 4.1 Projection (the only new logic)

```clojure
(def org-scoped-roles #{:org-editor})

(defn derive-org-roles [orgs]                ; orgs the user is a member of (org_current docs)
  (mapcat (fn [{:keys [org-id document member]}]
            (let [catalog       (:role-templates document)
                  org-role-data (case (:org-role member)
                                  :admin  {:role :org-admin :org-id #{org-id}}
                                  :member {:role :org-user  :org-id #{org-id}})
                  assigned      (->> (:templates member)
                                     (keep catalog)          ; <- CEILING enforced here
                                     (mapcat :roles))]
              (cons org-role-data
                    (for [spec assigned]
                      (cond-> spec
                        (org-scoped-roles (:role spec)) (assoc :org-id #{org-id}))))))
          orgs))
```

`(keep catalog)` is the structural ceiling: an assignment naming a non-catalog
template expands to nothing. All produced fresh, none persisted.

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
  MUST be a set** — a scalar throws. (Already handled in `lipas.ui.org.subs`.)
- `check-privilege`, `check-permissions!`, and the per-request auth path are untouched.

### 4.4 ES (reverse / bulk direction)

- `enrich*` indexes `search-meta.owner-org-id` and `search-meta.editor-org-ids`.
- `wrap-es-query-site-has-privilege` has an `:org-id` term branch, so an `:org-editor`
  role compiles to `owner-org-id OR edit-grants` term filters.

---

## 5. Answering the two questions

**Q1 — facilities owned by / editable by org X**: ES term query on
`search-meta.owner-org-id` (owned) or `editor-org-ids` (owned ∪ granted). O(query).

**Q2 — who can edit site Z** (`core/site-editors`, returned by `POST /actions/site-editors`):
1. **owner org** — off the site document.
2. **grantee orgs** — off `:edit-grants`.
3. **activity-editor orgs** — enumerate org catalogs whose template grants an activity
   the site has (orgs ≈ hundreds, trivial).
4. **legacy direct users** — **now implemented** (was the deferred unindexed part).
   See §5.1.

The UI shows orgs by name (no member expansion — by product decision) plus the
legacy individual users.

### 5.1 Legacy direct-user lookup (the formerly-unindexed part)

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
- **Index** — migration `20260607120000-account-permissions-gin` adds
  `account_permissions_gin` (`gin (permissions jsonb_path_ops)`). The planner uses it
  for selective predicates (Bitmap Index Scan); for broad activity predicates it may
  seq-scan, still ms-scale at current size.

**Verified in REPL** (see §11): candidate result == exhaustive naive scan, exactly,
across diverse sites; single-digit-to-low-double-digit ms.

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
5. **History is first-class** (but admin-only — see §6.6).

### 6.2 Capability model (`::subs/can? capability org-id`)

| Capability | lipas-admin | org-admin | member |
|---|---|---|---|
| `:org/create`, `:org/edit-type+ownership`, `:org/edit-catalog` | ✓ | – | – |
| `:org/edit-contact`, `:org/edit-ptv`, `:org/edit-instructions` | ✓ | ✓ | – |
| `:org/manage-members` (invite / remove / set-role / assign-templates) | ✓ | ✓ | – |
| `:org/grant-site-edit` (cross-org) | ✓ | ✓ | – |
| `:org/view-history` | ✓ | ✓ | – |
| `:org/view` (roster, sites, catalog read-only) | ✓ | ✓ | ✓ |
| edit **direct** user-level roles (separate user-mgmt UI) | ✓ | – | – |

Direct user-permission editing stays in the existing admin user-management screen —
the *other* plane, intentionally not part of the org UI.

### 6.3 Tabs (`org-view`)

Order: **Ohjeet · Kohteet · Yleistiedot · Jäsenet · Käyttöoikeudet · PTV · Historia**.

- **Ohjeet** is first (§6.4). **Kohteet** is the **default landing tab** (most-used);
  its data loads on org open via the normal tab path. *(Open item: whether Ohjeet, as
  the first tab, should also be the default landing — see §10.)*
- **Historia** is shown only to admins (`:org/view-history`).
- Terminology: "Kohteet" (was "Kohteemme"), "Käyttöoikeudet" (was "Oikeudet ja
  omistus").

### 6.4 Ohjeet (instructions) — NEW

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

Unified **invite** control (email + org-role + templates multi-select from the org
catalog). Members table: org-role inline select, template chips (add/remove from the
catalog only), remove. Members see the roster read-only. **No "pending invitations"
table by design** — there is no invitation table; an invited-but-not-logged-in user
is just an account member (optionally surface via login status later).

### 6.7 Käyttöoikeudet (roles & templates)

Read for everyone, write for lipas-admin (`:org/edit-catalog`). Lists each catalog
entry (label · plain-language description · assigned-member count). The menu org
admins assign from, made explicit.

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
- **Manage edit-grants** on owned sites → `POST/DELETE /actions/site-edit-grants`.
- **Claim ownership** → `POST /actions/org-takeover-request`; lipas-admin approval
  queue via `/actions/org-takeover-requests…`.

### 6.9 Historia (audit) — admin-only

Reverse-chronological timeline over `org` revisions: who changed what when (contact,
catalog, members, template assignments, instructions, ownership rule, …). **UUIDs
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
- **Backend authz** (`owner-org-assignment-authorized?` middleware on
  `POST /sports-sites`): a claimed `:owner-org-id` requires `:site/create-edit` on
  that org (org-editor of it, or lipas-admin). Legacy saves (no owner-org-id) pass
  through; site-level edit is still enforced in core.
- **Deferred caveat:** the middleware can't see current site state, so it doesn't
  enforce existing-site ownership immutability against a hand-crafted API call (the FE
  doesn't expose it; not an escalation — they could already edit the site). A ~3-line
  "compare to current revision" core guard could add defense-in-depth later.

### 6.11 Re-frame surface (selected)

Subs: `::can? ::user-orgs ::ownable-orgs ::org-templates ::org-history
::site-editors ::is-lipas-admin ::is-org-admin? ::is-org-member?`. Events:
`::invite-member ::set-member-org-role ::set-member-templates ::remove-member
::edit-template-catalog ::grant-site-edit ::revoke-site-edit ::request-takeover
::approve-takeover ::deny-takeover ::edit-org ::save-org ::get-site-editors
::get-org-history`.

---

## 7. Endpoints (all under `/api`, mostly `POST /actions/*`)

| Method · path | Privilege | Notes |
|---|---|---|
| `POST /actions/current-user-orgs` | any | orgs the user belongs to (admins/ptv-auditors see all) |
| `POST /actions/all-orgs` | `:org/admin` | all orgs (lipas-admin) |
| `POST /actions/create-org` | `:org/admin` | create org |
| `POST /actions/update-org` | `:org/manage` | details; `:type`/`:ownership` pinned for non-lipas-admins; **members + catalog preserved** |
| `POST /actions/org-members` | `:org/member` | member accounts + `:org-role`/`:templates` |
| `POST /actions/update-org-role-templates` | `:users/manage` | the catalog (ceiling) |
| `POST /actions/invite-org-member` | `:org/manage` | catalog-validated; creates account + emails unknown addresses |
| `PUT  members/:user-id/role`, `…/templates`, `DELETE …/:user-id` | `:org/manage` | promote/demote, assign, remove |
| `POST /actions/check-is-existing-user` | `:org/manage` | invite-form existence probe (boolean only; org-scoped) |
| `POST /actions/org-sites` | `:org/member` | owned / editable site lists (ES) |
| `POST /actions/site-editors` | (site-scoped) | Q2: owner + grantees + activity orgs + **legacy users** |
| `POST /actions/org-history` | `:org/manage` | revisions + computed diff (admin-only) |
| `POST /actions/site-edit-grants` (POST/DELETE) | `:org/manage` (owner org) | cross-org grant / revoke |
| `POST /actions/org-takeover-request` | `:org/manage` | request to claim matching sites |
| `GET/POST org-takeover-requests[/…/approve|deny]` | `:org/admin` | lipas-admin queue |
| `POST /sports-sites` | (site authz in core) + `owner-org-assignment-authorized?` | save; gates claimed `:owner-org-id` |
| `POST /actions/update-org-ptv-config` | `:users/manage` | unchanged |

`:login-url` for invites must start with a known LIPAS domain
(`lipas.schema.handler/magic-link-login-url`).

---

## 8. Database changes

1. **`org` revision table + `org_current` view** — `org_id` == old `org.id` to
   preserve references. GIN index on `document` for the login reverse-membership
   query. Seeded one revision per existing org; member backfill from legacy
   `:org-admin`/`:org-user` account roles.
2. **Site document fields** — `:owner-org-id`, `:edit-grants` (jsonb; no schema
   migration). Added to the malli site schema. ES mapping gains
   `search-meta.owner-org-id` / `editor-org-ids` (reindex).
3. **`account_permissions_gin`** (`20260607120000`) — GIN `jsonb_path_ops` on
   `account.permissions` for the legacy-editor lookup (§5.1).
4. **`acting_org_id`** on site revisions ("on whose behalf") — on the table; not in
   `sports_site_current` (blocked by dependent views; query the table for history).

No destructive change to `account`. The legacy account `:org-admin`/`:org-user`
roles are removed only in a *follow-up* migration after derive-at-login is verified
in production (until then they coexist and are deduped at login).

---

## 9. Audit & history

| Change | Recorded as | Actor |
|---|---|---|
| Rename / contact / instructions / PTV / **catalog** edit | new `org` revision | `author_id` |
| Member add / remove / template assignment / promote | new `org` revision (members in document) | `author_id` |
| Org archival | new `org` revision, `status 'archived'` | `author_id` |
| Site ownership take-over / transfer | new **site** revision (`:owner-org-id`) | site `author_id` |
| Cross-org edit grant / revoke | new **site** revision (`:edit-grants`) | site `author_id` |
| "On whose behalf" | optional `acting_org_id` on the site revision | — |

Two append-only logs, both pre-existing patterns. The org-history view diffs
consecutive revisions and resolves all referenced account ids to email/username.

---

## 10. Open questions & decisions

1. **Default landing tab** — Ohjeet is the first tab; Kohteet is currently the
   default selected (most-used). Decide whether the first tab should also be the
   landing tab.
2. **Legacy-user email display (GDPR)** — emails are shown to admins only; **left
   as-is pending PM/DPO feedback**. Recommended mitigation if needed: name/username
   first (email for lipas-admin or behind a reveal) + audience scoping, **not** email
   masking (pseudonymisation ≠ anonymisation; defeats the purpose in a narrow
   audience). Do not change unprompted.
3. **Admins in the who-can-edit list** — currently excluded (they can edit
   everything). Option: include with an `Ylläpitäjä` tag.
4. **Take-over / transfer approval depth** — take-over has a requested→approved
   flow; transfer (owner → new owner) confirmation UX is light. Revisit if needed.
5. **Owner-enum locking** — hard-locked from `org.type` (done) vs default + warn.
6. **Revocation latency** — removing a member / unassigning a template takes effect
   at next token refresh (≤6h). Acceptable, or add an immediate-revoke path?
7. **Catalog editing scope** — lipas-admin-only (current). Keep, or allow a "lead"
   org-admin within a lipas-admin-set boundary? (Recommend keep — it's the ceiling
   guarantee.)
8. **Existing-site ownership immutability** — FE-gated + middleware-authz'd, not
   enforced server-side against raw API (§6.10). Add the core guard if desired.

---

## 11. Verification record

### 11.1 Matcher proof (Phase 0)

Standalone against pure `lipas.roles` cljc. Surfaced the one correction (`:org-id`
set-intersection). All assertions green: owner edits owned; non-member denied;
grantee allowed only on cross-granted sites; activity (melonta) editor edits activity
data; org-admin scoped to own org; admin edits anything; legacy roles untouched.
20/20 access-pattern coverage end-to-end (projection → conform-roles →
check-privilege), incl. the **ceiling** (bogus template grants nothing).

### 11.2 Scaling (≈ ≤500 orgs, ~57k sites, users mostly in 1 org)

- **No per-site fan-out** — derived authority is O(orgs × templates), independent of
  owned-site count (org owning 50 000 sites → still 3 derived roles).
- JWT payload stays KB-scale even at pathological membership; `check-privilege` µs-scale.
- Login reverse-membership query GIN-indexed (`jsonb_path_ops`, Bitmap Index Scan), ms-scale.
- ES bulk filter (Q1) sub-millisecond at full site count.

### 11.3 Legacy-editor lookup (§5.1) — measured in REPL

Real dev stack (2 346 accounts, ~2 021 with roles, 57 030 sites):

- **Correctness:** for 6 diverse sites, candidate pre-filter result == exhaustive
  naive scan, exactly (0 missing, 0 extra).
- **Performance:** ~1–14 ms per site (reused connection) vs ~207 ms naive (which is
  dominated by loading *all* accounts into the JVM). The real win is the `WHERE`-clause
  candidate filter (~50–264 rows vs 2 346), not the index per se.
- **Index:** `EXPLAIN ANALYZE` confirms Bitmap Index Scan for selective predicates
  (~1.9 ms); broad activity predicates seq-scan (~5 ms) at current table size.

### 11.4 Test coverage

`bb test-ns lipas.roles-test lipas.backend.org-test` — green. Org-test includes:
owner-org assignment authz (own org ✓ / other 403 / non-member 403); history authz
(member 403); history member-email resolution; org instructions round-trip + route;
site-editors composition; site-editors legacy-users (direct user found, admin
excluded, candidate == naive scan).

---

## 12. Invariants that keep this cheap forever

1. **One frozen matching rule** — future scenarios arrive as data (template entries,
   site fields), never new code branches.
2. **No persisted derived state** — derived roles exist only in tokens; nothing to
   reconcile.
3. **Two append-only logs, both pre-existing patterns** — history/audit is structural.
4. **User document untouched by org code** — opt-in and legacy compatibility by
   construction.
