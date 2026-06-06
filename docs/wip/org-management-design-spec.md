# Design Spec — Opt-in Organization Management & Permission Extension

Status: Draft for review
Date: 2026-06
Scope: backend data model + permission engine extension. UI is out of scope here (separate spec).

---

## 1. Background & goals

LIPAS has organizations and a user-level permission system, but they are disconnected:

- Sites carry **no org reference** — there is no answer to "which facilities does org X own".
- Org membership grants only `:org/member` / `:org/manage` — never editing.
- The `org` table is a **single mutable row per org** — no history, no audit. This was a mistake and is the primary thing this spec corrects.

Goals:

1. **Q1** — answer "which facilities are owned by org X" efficiently (bulk / ES).
2. **Q2** — answer "who can edit site Z".
3. **Immutable org history** — every change to an org (config *and* membership) is an append-only, auditable revision. Mirror the `sports_site` event-log model the team already trusts.
4. **Org self-service** — org admins manage their own members and can grant *edit* on their sites to other orgs, without a LIPAS admin in the loop.
5. **Opt-in / additive** — the existing user-level permission system continues unchanged. A user with no org membership behaves exactly as today.
6. **Elegant simplicity** — minimal new machinery; no second authorization brain to maintain.

## 2. Non-goals (v1)

- Org hierarchies (ylä-/ala-organisaatiot) — deferred (spec "Vaihe 5"). This is the one feature that would justify a rules engine; we explicitly do not build for it now.
- Per-member permission overrides — a member receives the org's template, uniformly.
- Approval workflows on edits — none (per product decision: bottlenecks reduce data freshness).
- Migrating away from direct user roles — they remain first-class indefinitely.

---

## 3. Design principles (the decisions already made)

### 3.1 Authority is **data**, matched by **one frozen rule**

The matching engine (`lipas.roles/select-role` + `check-privilege`) gains **no new concepts**. Authority is expressed as **role data with context sets** (`:city-code :type-code :lipas-id :activity :org-id`); a single intersection rule decides access. `select-role` already has an `:org-id` branch (roles.cljc:204-206) — the one change (verified in §14) is to make it **multi-valued (set-intersection), exactly like the existing `:activity` branch**, because a *resource* can have several editor orgs just as it can have several activities. That is a strengthening within the existing pattern, not a redesign. Per-org variation is expressed as **data** (the role-template catalog), never as bespoke rules.

> Litmus test for any future change: if it must be answerable in both directions (forward "what can X edit" and reverse "who can edit Z") and shown in the FE, it is **data matched by the rule**, compiled to an ES query for the reverse direction — never a rule that computes an answer.

### 3.2 Derived permissions are **computed at login, never persisted**

The *only* org fact stored is **membership** (and it lives in the org document, §4). Everything a membership confers — editor / activity / ptv roles from the template — is **projected at token-creation time and lives only in the JWT** (≤6h life, same as today).

Consequence: there is **no stored derived state to drift**. A template edit or ownership-rule change propagates to everyone at their next token refresh, automatically, with **zero recompute machinery, no provenance tags, no reconcile jobs**. We delete the entire drift-management problem by never creating the thing that drifts. The per-request `token-backend` path is untouched; only login does the extra read.

### 3.3 Immutability via append-only revisions (mirror `sports_site`)

- **Org state** (config + membership) → append-only `org` revisions + `org_current` view.
- **Site ownership & edit-grants** → fields on the **site document**, riding the *existing* `sports_site` event log.

These are the only two history surfaces, both copies of a pattern already in production. No bespoke event/audit tables.

### 3.4 Opt-in is **structural**, not conditional

Orgs reference users; the **user document is never touched by org code**. There is no `if org-enabled?` branch — the legacy path is simply the empty-projection case (no membership → no derived roles → today's behavior exactly).

### 3.5 Bounded delegation — the org-admin ceiling is **data**

Org admins self-serve, but their power is capped by a **role-template catalog** that only a **lipas-admin** can edit (§4.4). An org admin may *assign* catalog templates to members and invite/remove members — they can never *define* what a template grants, nor escalate beyond the catalog. The ceiling is enforced **structurally at projection time**: `derive-org-roles` only expands templates found in the catalog, so an assignment outside the catalog yields nothing regardless of how it got there. Assignment-time validation is then just early UX, not the security boundary.

---

## 4. Core model

### 4.1 Org as append-only revisions (the centerpiece)

Replace the mutable single-row `org` with an event log identical in shape to `sports_site`:

```
org (revision table)
  id          uuid   PK   -- per-revision id
  org_id      uuid        -- STABLE org identity (== old org.id), preserved across revisions
  event_date  timestamptz -- logical change time
  created_at  timestamptz
  author_id   uuid        -- who made the change (FK account) — the audit actor
  status      text        -- 'active' | 'archived'
  document    jsonb        -- full org state snapshot (see 4.2)

org_current (view) -- latest active revision per org_id  (mirror of sports_site_current)
```

Every change — rename, contact edit, PTV config, template change, **member add/remove**, ownership take-over rule change, archival — is a **new revision** authored by the actor. The diff between consecutive revisions *is* the audit log. "Who was a member of org X on date D, and who changed it" is a query over revisions. The mutable-table flaw is gone.

> Decision: fold `name` / `data` / `ptv_data` into a single `document` jsonb (as `sports_site` does), rather than keeping separate columns. Cleaner, consistent, one snapshot per change.

### 4.2 The org document

```clojure
{:name "Oulun kaupunki"

 :type :city                 ; :city | :sports-federation | :private | :state | ...
                             ; drives owner-enum locking (§4.6) and UI defaults

 :primary-contact {:phone .. :email .. :website .. :reservations-link ..}

 :ptv-data { ... }           ; unchanged content; now lives inside the document

 ;; --- the CEILING: named template catalog, editable only by lipas-admin (§4.4) ---
 :role-templates
 {:editor  {:label "Muokkaaja"     :roles [{:role :org-editor}]}            ; org-id injected at projection
  :ptv     {:label "PTV-hallinta"  :roles [{:role :ptv-manager :city-code [564]}]}
  :melonta {:label "Melonta (UTP)" :roles [{:role :activities-manager :activity ["melonta"]}]}}

 ;; --- which sites this org may claim ownership of (bulk take-over rule, §6) ---
 :ownership {:city-codes [564] :owners ["city" "city-main-owner"]}

 ;; --- membership + per-member template ASSIGNMENTS (set by org-admin) → immutable in org history ---
 :members
 [{:user-id #uuid "..." :org-role :admin  :templates [:editor :ptv]}   ; :org-role → mgmt capability
  {:user-id #uuid "..." :org-role :member :templates [:melonta]}]}      ; :templates ⊆ keys of :role-templates
```

Two levels of per-org data:

- **`:role-templates`** — a named catalog (the *ceiling*). Only a lipas-admin edits it (§4.4, §10). Defines *what each template grants*.
- **member `:templates`** — which catalog entries an org-admin has assigned to *that* member. Defines *who gets what*, bounded by the catalog.
- **member `:org-role`** — `:admin` (can manage the org: invite/remove/assign) → `:org/manage`; `:member` → `:org/member`. Orthogonal to the edit/activity grants from `:templates`.

Membership-in-document makes member history *and* assignment history immutable, removing any need for a separate membership audit log. Member lists are small (tens–low hundreds); snapshotting per change is cheap and matches `sports_site`.

> `:ownership` overlaps with `ptv-data`'s existing `city-codes`/`owners`. Decision: keep a dedicated `:ownership` key (ownership ≠ PTV concern); seed it from `ptv-data` at migration.

### 4.3 Membership — resolved at login only

The user account stores **only direct (legacy) roles**. No `:org-id` roles are persisted on accounts.

At login, `derive-user-org-roles db user-id` (§5) reads `org_current` rows whose `document.members[].user-id` contains the user, and projects that member's *assigned* catalog templates into role data, baked into the JWT.

### 4.4 Role-template catalog — the per-org variation, as data

The catalog (`:role-templates`) is the per-org permission vocabulary, **curated by lipas-admin** and the **ceiling** on what org-admins can hand out. Each entry maps a name → `{:label :roles [role-specs]}`. Org-admins then assign entries to members (member `:templates`). Projection (§5) injects `:org-id` for ownership-scoped roles. Archetypes differ **only** in catalog data — new archetype = new data, never new code:

| Archetype | Catalog (set by lipas-admin) |
|---|---|
| Municipality | `{:editor {:roles [{:role :org-editor}]} :ptv {:roles [{:role :ptv-manager :city-code [c]}]}}` |
| Sports federation (UTP) | `{:melonta {:roles [{:role :activities-manager :activity ["melonta"]}]}}` |
| View-only | `{}` (members can only ever get `:org/member`) |

A member assigned no templates gets only their `:org-role` privilege (`:org/manage` or `:org/member`) — no editing. "View-only member" is just the empty `:templates`.

### 4.5 Site ownership & edit-grants — fields on the site document

Add to the sports-site document (no SQL schema change — it's jsonb, event-sourced already):

```clojure
{:owner-org-id #uuid "..."          ; nil for un-owned (legacy) sites
 :edit-grants  [#uuid "..." ...]}    ; orgs granted edit by the owner (cross-org collaboration)
```

Because these live in the document, **ownership history, "on whose behalf", and ES indexing come free** from the existing `sports_site` revision log. A take-over or a cross-org grant is just a normal site revision authored by the actor.

### 4.6 The one new role

Exactly one addition to `lipas.roles/roles`:

```clojure
:org-editor
{:sort 62
 :assignable false                 ; never hand-assigned; only ever projected from a template
 :privileges lipas.roles/basic     ; :site/save-api :site/create-edit :activity/view :analysis-tool/use
 :required-context-keys [:org-id]
 :optional-context-keys []}
```

This is the only thing the existing city/type/lipas-id roles cannot express (ownership-scoped edit). UTP reuses `:activities-manager` verbatim; PTV reuses `:ptv-manager`. The matcher itself is unchanged.

---

## 5. Permission resolution flow

### 5.1 Projection (the only new logic)

```clojure
(def org-scoped-roles #{:org-editor})   ; roles whose context is the org itself

(defn derive-org-roles
  "Pure projection: (member assignments × catalog) -> role data.
   The single place template semantics live, and the structural ceiling: only
   templates present in the catalog are expanded — unknown assignments vanish."
  [orgs]                                 ; orgs the user is a member of (org_current docs)
  (mapcat (fn [{:keys [org-id document member]}]
            (let [catalog       (:role-templates document)
                  org-role-data (case (:org-role member)
                                  :admin  {:role :org-admin :org-id #{org-id}}
                                  :member {:role :org-user  :org-id #{org-id}})
                  assigned      (->> (:templates member)
                                     (keep catalog)         ; <- CEILING enforced here
                                     (mapcat :roles))]
              (cons org-role-data
                    (for [spec assigned]
                      (cond-> spec
                        (org-scoped-roles (:role spec)) (assoc :org-id #{org-id}))))))
          orgs))
```

`:org-admin`/`:org-user` carry the management/membership privileges; the assigned catalog templates carry edit/activity/ptv. `(keep catalog)` is the structural ceiling (§3.5): an assignment naming a non-catalog template expands to nothing, so an org-admin can never project authority the lipas-admin didn't define. All produced fresh, none persisted.

### 5.2 Login wiring

`jwt/create-token` selects `[:id :email :username :permissions]` and has no DB. So enrich just before signing, at the (few) call sites that have `db`:

```clojure
;; in basic-auth / login / token-refresh, before jwt/create-token:
(update-in user [:permissions :roles]
           into (org/derive-user-org-roles db (:id user)))
```

`derive-user-org-roles` = reverse jsonb-containment query over `org_current` (GIN-indexable, mirrors today's `user-orgs`), then `derive-org-roles`. Persisted account doc is never modified.

### 5.3 Matching

- `site-roles-context` gains `:org-id` as a **set** of the site's editor orgs (owner + grants):

  ```clojure
  :org-id (some-> (concat (some-> site :owner-org-id vector) (:edit-grants site))
                  set not-empty)
  ```

- `select-role` `:org-id` branch: `contains?` → **set-intersection** (one line, mirrors `:activity`). Required because a site has *multiple* editor orgs; verified in §14.
- The existing scalar `:org-id` role-contexts (org-management routes — `handler.clj` lines 335/352/359/369, `{:org-id (str …)}`) are wrapped to sets: `{:org-id #{(str …)}}`. 4 call sites, one file.
- A user with `{:role :org-editor :org-id #{O}}` and a site whose context `:org-id` ∩ `#{O}` ≠ ∅ → `select-role` returns it → edit granted. Cross-org: site lists grantee in `:edit-grants` → grantee's members match identically.
- `check-privilege`, `check-permissions!` (core.clj:309), and the per-request auth path are **untouched**.

### 5.4 ES (reverse / bulk direction)

- `enrich*` (core.clj:434) indexes `owner-org-id` and `edit-grants` into `search-meta`.
- `wrap-es-query-site-has-privilege` gains an `:org-id` term branch (mirrors the existing city/type/lipas-id branches) so that an `:org-editor` role compiles to `owner-org-id OR edit-grants` term filters.

---

## 6. Answering the two questions

**Q1 — facilities owned by org X**: ES term query `search-meta.owner-org-id = X`.
"Facilities org X may edit": `owner-org-id = X OR edit-grants contains X` — via `wrap-es-query`. O(query), no scan.

**Q2 — who can edit site Z**:
1. `editor-orgs(Z) = {owner-org-id} ∪ edit-grants` — read off the site document, O(1).
2. Members of those orgs whose template includes an edit role — read `org_current.document.members`.
3. Activity-editor orgs whose template grants an activity present on Z (system-wide, ownership-independent) — enumerate org templates (orgs ≈ hundreds, trivial).
4. Legacy direct-permission users — best-effort scan of `account.permissions.roles` by city/type/lipas-id. Honest caveat: the only unindexed part, and precisely what org-management deprecates; transparency goal (spec 3.2) is met by (1)–(3).

---

## 7. Database changes

1. **`org` → revision table + `org_current` view** (migration mirrors `sports_site`):
   - New `org` revision table (§4.1). `org_id` == old `org.id` to preserve all existing references (user `:org-id` roles, ptv config, `lipas_id` joins).
   - `org_current` view = latest active revision per `org_id`.
   - GIN index on `document` (or `document -> 'members'`) for the login reverse query.
2. **Seed**: one revision per existing org row, `document` = `{name, data→primary-contact, ptv-data, role-template [], ownership (from ptv city-codes/owners), members []}`, `author_id` = migrating admin, `status 'active'`.
3. **Member backfill**: scan accounts; for each `:org-admin`/`:org-user` role, append `{user-id, org-role}` to that org's seed revision `members`.
4. **Site document fields**: no schema migration (jsonb). Add to malli site schema; bulk take-over job (§9) populates `:owner-org-id`.

No destructive change to `account`. The legacy user-side `:org-admin`/`:org-user` roles are removed only in a *follow-up* migration after derive-at-login is verified in production (§9).

---

## 8. Backend touch points (small, enumerated)

| File | Change |
|---|---|
| migrations | `org` revision table + `org_current` view + seed/backfill |
| `roles.cljc` `roles` | +1 entry `:org-editor` |
| `roles.cljc` `site-roles-context` | emit `:org-id` as a set (~3 lines) |
| `roles.cljc` `wrap-es-query-...` | +`:org-id` term branch |
| `roles.cljc` `select-role` | `:org-id` → set-intersection (1 line, like `:activity`); `check-privilege` **none** |
| `handler.clj` | wrap 4 scalar `{:org-id (str …)}` role-contexts → sets (lines 335/352/359/369) |
| `core.clj` `enrich*` | index `owner-org-id` / `edit-grants` |
| `core.clj` `check-permissions!` / per-request auth | **none** |
| `backend/org.clj` | reads → `org_current`; writes → append revision; `derive-user-org-roles`; `derive-org-roles`; template-aware member ops |
| `backend/auth.clj` (+ login/refresh handlers) | enrich permissions with derived roles before `create-token` |
| `schema/org.cljc`, site schema | `:type`, `:role-templates`, `:ownership`, `:members` (`:org-role`+`:templates`); site `:owner-org-id`, `:edit-grants` |
| handler endpoints | catalog edit (lipas-admin); member invite/remove/assign + cross-org grant (org-admin); see §10 |

The matcher and the per-request authentication path — the two highest-risk assets — are not modified.

---

## 9. Audit & history — how each change is recorded immutably

| Change | Recorded as | Actor captured |
|---|---|---|
| Org rename / contact / PTV config / **catalog** edit | new `org` revision | `author_id` (lipas-admin) |
| Member add / remove / **template assignment** / promote | new `org` revision (members in document) | `author_id` (org-admin) |
| Invitation sent / consumed / expired | `org_invitation` row; consumption also = org revision | `invited_by` / acceptor |
| Org archival | new `org` revision, `status 'archived'` | `author_id` |
| Site ownership take-over / transfer | new **site** revision (`:owner-org-id`) | site `author_id` |
| Cross-org edit grant / revoke | new **site** revision (`:edit-grants`) | site `author_id` |
| "On whose behalf" an edit was made | optional `acting_org_id` on the site revision (small `marshall` addition) | — |

Two append-only logs, both already-proven patterns. No new audit subsystem.

---

## 10. Authority model & self-service

Three actors, three existing privileges — **no new privilege is introduced**:

| Action | lipas-admin (`:admin`) | org-admin (`:org/manage` @ org-id) | org-member |
|---|---|---|---|
| Create org; set `:type`, `:ownership` rule | ✓ | – | – |
| Edit **role-template catalog** (the ceiling) | ✓ | – | – |
| Edit **direct user-level roles** (non-org / legacy) | ✓ | – | – |
| Invite / remove members | ✓ | ✓ (own org) | – |
| **Assign / unassign catalog templates** to members | ✓ | ✓ (own org) | – |
| Promote / demote member ↔ org-admin | ✓ | ✓ (own org) | – |
| Grant / revoke cross-org **edit** on owned sites | ✓ | ✓ (own org) | – |

Privilege mapping (all already exist in `lipas.roles`):
- **`:org/admin`** (held via `:admin`) → catalog edit, org creation, ownership rules. *Lipas-admin only.*
- **`:org/manage`** (scoped by `:org-id`) → member invite/remove/assign, promote, cross-org grants. *Org-admin, own org.*
- **`:users/manage`** (held via `:admin`) → the existing user-permission editor for **direct** roles. *Lipas-admin only* — this is requirement "lipas-admin can manage user-level permissions", unchanged.

So **lipas-admin manages both planes**: the org plane (catalog/members via the org editor) *and* the legacy user plane (direct roles via the existing user-management UI). **Org-admin** is confined to the org plane, own org, bounded by the catalog.

### Self-service mechanics

- **Invite** (`:org/manage`): org-admin supplies an email + an assignment (`:templates ⊆ catalog`, `:org-role`). The endpoint validates the assignment against the catalog (early UX; §3.5 is the real guard).
  - *Existing user* → append an org revision adding the member with that assignment.
  - *New user* → create a pending `org_invitation` (token + expiry, mirrors the `account.reset_token` pattern); on magic-link registration the invitation is consumed → append an org revision adding the member. Removes the current "user must pre-register" constraint in `update-org-users!`.
- **Assign / unassign templates** (`:org/manage`): org-admin edits a member's `:templates` → append an org revision. Takes effect at the member's next token refresh (≤6h), like every permission change today.
- **Remove member** (`:org/manage`): append an org revision dropping the member. Derived roles vanish at next refresh; for immediate revocation rely on token TTL (see open Q).
- **Grant edit to another org** (`:org/manage`): owner-org admin adds a grantee org-id to a site's `:edit-grants` → a site revision. No user records touched; grantee's members gain edit via their `:org-editor` projection.
- **Multi-org users**: union of derived roles across all memberships — falls out of `derive-org-roles` (`mapcat` over all the user's orgs).

---

## 11. Rollout (phased, non-breaking)

- **Phase 0 — proof of the core thesis. ✅ DONE (see §14).** Surfaced one correction (`:org-id` set-intersection) now folded into the spec; all 11 assertions green.
- **Phase 1 — Immutable org + ownership link.** Org revision table + `org_current` + seed/backfill; site `:owner-org-id`/`:edit-grants` fields; bulk take-over job (sites where `city ∈ ownership.city-codes ∧ owner ∈ ownership.owners`); ES indexing. Delivers **Q1** and an immutable org store. Permissions unchanged yet.
- **Phase 2 — Org-derived permissions.** `:org-editor` role; `site-roles-context` `:org-id`; `derive-org-roles` + login wiring; `wrap-es-query` org branch. Delivers **Q2** and org-driven editing. Heavy test focus (authorization backbone).
- **Phase 3 — Self-service & invitations.** Catalog editor (lipas-admin); member invite/remove/assign endpoints (org-admin, catalog-validated); `org_invitation` table + magic-link accept; cross-org grant endpoint. Then the follow-up migration removing now-redundant user-side `:org-user`/`:org-admin` roles.
- **Phase 4 — Dashboard / analytics** (separate UI spec): "my org's sites", coverage/freshness, change history with org context.

---

## 12. Invariants that keep this cheap forever

1. **One frozen matching rule.** Future scenarios arrive as data (template entries, site fields), never as new code branches. Every pull to "add a rule" is converted to a template entry. This discipline *is* the maintenance strategy.
2. **No persisted derived state.** Derived roles exist only in tokens; nothing to reconcile.
3. **Two append-only logs, both pre-existing patterns.** History/audit is structural, not bespoke.
4. **User document untouched by org code.** Opt-in and legacy compatibility are guaranteed by construction.

---

## 13. Open questions

1. **Take-over / transfer approval** (spec 2.8): model `requested → approved` as org/site revision states, or keep informal in v1?
2. **Owner-enum locking** (spec 3.1): hard-lock `:owner` on org-owned sites from `org.type`, or default + warn?
3. **`acting_org_id` on site revisions**: add now for multi-org users, or defer?
4. **Audit posture**: org revisions cover membership history; do we still want a per-action command stream (e.g. for "invitation sent but not accepted"), or is the revision diff sufficient?
5. **GIN index shape** for the login reverse-membership query — confirm against expected org/member cardinality.
6. **Org-admin promoting org-admins**: allow an org-admin to promote another member to org-admin (assumed ✓ in §10), or reserve promotion for lipas-admin?
7. **Revocation latency**: removing a member / unassigning a template takes effect at next token refresh (≤6h). Acceptable, or do we need an immediate-revoke path (shorten TTL, or a token deny-list) for sensitive cases?
8. **Catalog editing scope**: is the catalog purely lipas-admin (current assumption), or should a "lead" org-admin be allowed to define templates within a lipas-admin-set boundary? (Recommend: keep lipas-admin-only in v1 — it's the whole ceiling guarantee.)

---

## 14. Verification record — Phase 0 (matcher proof)

Run standalone against the pure `lipas.roles` cljc (babashka, `bb -cp src/cljc`), no running system. The proposed `:org-editor` role was injected and `select-role` patched in-REPL to simulate the code change.

**Setup.** Users: `owner-member` `{:org-editor :org-id #{O1}}`, `grantee-member` `{:org-editor :org-id #{O2}}`, `non-member` `[]`, `melonta-editor` `{:activities-manager :activity #{melonta}}`, `org-admin-O1` `{:org-admin :org-id #{O1}}`. Site contexts emit `:org-id` as a **set**: `ctx-Z` (owner `#{O1}`, melonta), `ctx-grant` (owner+grant `#{O1 O2}`).

**Finding.** With the *unmodified* matcher, `:org-id` uses `contains?` (scalar): single-owner with a scalar context passes, but **every set-valued context fails** — so cross-org grants (a site with >1 editor org) do **not** match. Fix: `:org-id` → set-intersection, identical to the existing `:activity` branch; site-roles-context and the 4 `handler.clj` org-route contexts emit/pass sets.

**Result with the fix — all green:**

| Assertion | Expect | Got |
|---|---|---|
| owner edits owned site | true | ✅ |
| non-member denied | false | ✅ |
| grantee denied on non-granted site | false | ✅ |
| grantee allowed on cross-granted site | true | ✅ |
| owner still ok on cross-granted site | true | ✅ |
| activity (melonta) editor edits activity data | true | ✅ |
| org-editor has no `:org/manage` on another org | false | ✅ |
| org-admin manages own org (`:org/manage`) | true | ✅ |
| org-admin denied on another org | false | ✅ |
| owner cannot edit a foreign site | false | ✅ |
| admin edits anything | true | ✅ |

**Conclusion.** The core thesis holds: with `:org-editor` + site context carrying a set `:org-id`, the *existing* engine grants/denies correctly for ownership, cross-org grants, activity (UTP), org-management scope, and admin — and legacy roles are untouched. The only engine change is the one-line `:org-id` set-intersection, which keeps `:org-id` consistent with `:activity` (multi-valued resources) rather than the scalar keys.

### 14.1 Access-pattern coverage

20/20 patterns pass end-to-end (projection → `conform-roles` → `check-privilege`): municipality editor (edits owned, PTV in own city only, no foreign edit), view-only member (`:org/member` only), org-admin scope (own org yes / other no), UTP activity editor (edits melonta data anywhere, no basic-data edit on non-owned, no PTV), multi-org union, **ceiling** (a bogus template assignment grants nothing — editor still works, no phantom `:users/manage`), cross-org grant (grantee edits, non-member denied), **legacy untouched** (a pure `:city-manager` still works and gains no org privilege), admin override.

### 14.2 Scaling (simulated; realistic LIPAS ≈ ≤500 orgs, ~40k sites, users mostly in 1 org)

| Experiment | Result | Why it matters |
|---|---|---|
| **S1 — no per-site fan-out** | org owning 10 / 1 000 / **50 000** sites → user always has **3** derived roles | Membership never references sites; ownership lives on sites. Derived authority is **O(orgs × templates)**, independent of owned-site count — the central scaling property. |
| **S2 — token & matcher** | 1 org → 4 roles, 183 B, 2.4 µs/check · 3 orgs → 12 roles, 527 B, 4.9 µs · **20 orgs (pathological)** → 80 roles, 3.5 KB, 20 µs | JWT permissions payload stays KB-scale even at absurd membership; `check-privilege` latency negligible (it's per-decision, not per-site). |
| **S3 — reverse "who can edit Z"** | scan **500 orgs** → editor-orgs + 96 users in **0.77 ms** | The derived transparency view (owner + grants + activity-editors + members) is sub-millisecond well past real scale. No materialization needed. |
| **S4 — ES bulk filter** | terms list = **#orgs the user belongs to** (≈ a dozen), independent of #sites owned | Q1 / reverse-Q2 compile to a small bounded `terms` filter on `search-meta.editor-org-ids`; ES load does not grow with ownership. |

### 14.3 Live infra verification (PostgreSQL 17.5 + Elasticsearch, real dev stack)

Verified against the running system (2 342 accounts, 20 orgs, **57 030 sites**) using throwaway `zz_`-prefixed prototypes (a 500-org × 3-revision `org`-style table + `org_current` view, and a 57 030-doc ES index), all dropped afterwards; real tables untouched.

| Concern | Method | Result |
|---|---|---|
| **Login membership query** | `org_current` view + `WHERE document->'members' @> ?::jsonb`, `EXPLAIN ANALYZE`, 500 orgs × 3 revs | Correct; **GIN index (`jsonb_path_ops`) is used** (Bitmap Index Scan); ~6 ms exec. Runs once/~6h/user → trivial. |
| **`org_current` view** | latest-revision-per-org group-by-max (identical to `sports_site_current`) | Exactly 500 current rows from 1 500 revisions; correct. |
| **ES bulk filter (Q1 / reverse-Q2)** | dedicated index, **57 030 docs**, `terms` filter on `editor_org_ids` for a 12-org user, 8 runs | **0 ms** ES `took` (sub-ms) every run, 1 667 matches; combined org-terms + `city_code` ~1 ms. |
| **`dynamic: strict` mapping** | first index attempt with an undeclared field | Rejected — confirms the new `editor-org-ids` field **must be added to the mapping** (a reindex), as flagged. |

**Verdict:** every access pattern is correct *and* every scaling concern is now confirmed on real infra. The structural risk (per-site fan-out) is eliminated (S1, 50 000 sites); the login query is GIN-indexed and ms-scale; the ES bulk direction is sub-millisecond at full site count. No open performance risk remains — the only residual wiring task (not a risk) is adding `editor-org-ids` to the real `sports_sites_current` mapping + reindex when Phase 1 lands.
