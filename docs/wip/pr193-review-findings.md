# PR #193 (feat/org-management) — Code review findings tracker

Multi-agent review run 2026-06-09 (7 finder angles → 1-vote verification, most findings
REPL-verified against the running dev system). 34/35 candidates survived verification.

Status legend: `[ ]` open · `[x]` fixed · `[-]` won't fix (add reason)

File:line references are against the branch head at review time (2ac31c4c).

**Progress log**
- 2026-06-10 Wave 1 (F1, F4, F6, F8, F9, F11, F12, F14, F23-cljs, F28, F29, F30):
  full suite green (510 tests / 11731 assertions / 0 failures), cljs compile clean.
  Browser smoke PASS (login, org views, site edit+save w/ DB+ES coherence, ownership
  fields, new-site wizard).
- 2026-06-10 Wave 2 (F2; F3, F7, F13, F21, F22, F26, F27, F32): full-suite gate green
  (517 tests / 11767 assertions / 0 failures), cljs compile clean.
  Follow-ups carried to wave 3: localize already-member 409 toast in invite flow;
  delete orphaned user-updates schema in schema/org.cljc.
- 2026-06-10 Wave 3 (F5, F10, F15, F16, F20 partial + both wave-2 follow-ups):
  gate green (org/business-logic/ptv/handler/roles test-ns), cljs compile clean.
  NOTE: F15 adds `search-meta.owner-org-name` to the strict ES mapping
  (search.clj) — deploy requires a full search reindex or saves fail with
  strict_dynamic_mapping_exception (same reindex this PR already needs for
  owner-org-id/editor-org-ids). Dev + test indices recreated/reindexed.
  Wave-2 follow-ups done: already-member 409 → localized toast
  (`::invite-member-failure` + `:lipas.org/already-member` fi/se/en);
  orphaned `user-updates` schema deleted (grep: zero references).
- 2026-06-10 Wave 4 / final (F17, F18, F19, F23-clj, F24, F25, F31): gate green
  (org/business-logic/bulk-ops/ptv/handler/roles test-ns), cljs compile clean
  ({:status :ok}, zero warnings). All 35 findings now closed ([x]) except
  F20 which stays intentionally partial ([~]).
- 2026-06-10 FINAL GATE: full suite 522 tests / 11792 assertions / 0 failures
  (25 net-new regression tests vs pre-fix baseline of ~497). Final browser smoke
  PASS, all 6 flows (org tabs + count chips, who-can-edit drawer, site edit+save,
  owner-org name display, admin role editor lists, bulk-ops massapäivitys), zero
  console errors, zero failed API calls.
- 2026-06-10 post-review live-testing fix (F33): org members can view org sites
  list read-only; bulk actions gated by :site/create-edit; list-fetch errors now
  rendered. Lead-verified gate: full suite 523 tests / 11835 assertions /
  0 failures, cljs compile {:status :ok}. Also fixed: bulk-ops test fixture
  minted an impossible JWT (org-editor without the org-user baseline that real
  projection always adds) — fixtures must carry both; cleaned 11 leftover test
  indices from dev ES that broke handler-test fixtures.

**Remaining follow-ups (out of scope for this effort)**
- DEPLOY: full search reindex REQUIRED after deploy (strict ES mapping gained
  search-meta.owner-org-name; without reindex, saves fail with
  strict_dynamic_mapping_exception). Dev/test indices already done.
- PM/DPO: legacy accounts whose USERNAME is their email still show it to
  non-admins in edit-history/site-editors (pre-existing policy caveat).
- F20: site-editors still scans full documents of catalog-bearing orgs per
  request — revisit if org count grows (index-backed narrowing).
- F18: org-sites non-count path keeps :size 2000 — latent truncation if a future
  consumer reads :sites for an org owning >2000 sites.

---

## P0 — Security / authorization

- [x] **F1 — Scope escape on save**: `webapp/src/clj/lipas/backend/core.clj:431`
  ✅ 2026-06-10: save-scope-escape-test (org_test)
  `upsert-sports-site!` checks `:site/create-edit` only against the **stored** revision
  (`(check-permissions! user (or stored sports-site) draft?)`); master checked the submitted body.
  Nothing else validates the body's city-code/type-code, so a city-manager scoped to 889 can edit
  an 889 site and set `:location :city :city-code 91` (or retype), relocating it outside their
  scope — and can't edit it back. REPL-verified.
  *Fix direction: check both stored and submitted contexts (or the union).*

- [x] **F2 — Bulk-ops bypasses core business rules**: `webapp/src/clj/lipas/backend/bulk_operations/core.clj:63`
  ✅ 2026-06-10: authorization-uses-db-state-test, no-clobber-of-newer-db-revision-test
  (both fail on the old impl). ES kept for candidate listing only; writes now batch-load DB
  current revisions, authorize org editability against DB state, and route each save through
  `core/upsert-sports-site!` (carry-forward keeps stored ownership fields authoritative); fresh
  :event-date + trusted :acting-org-id per revision; ES bulk-indexed after commit from what was
  actually written.
  `mass-update-org-sites-contacts!` authorizes via ES `search-meta.editor-org-ids` (stale cache;
  revoked grants still work until reindex), reads ES `_source`, merges contact updates keeping the
  ES doc's `:owner-org-id`/`:edit-grants`, and writes via `db/upsert-sports-site!` directly —
  bypassing `core/upsert-sports-site!` invariants + `check-owner-lock!`, and able to clobber a
  concurrent un-reindexed DB revision (e.g. revert a just-approved takeover's owner change).
  *Fix direction: load stored revisions from DB and route writes through `core/upsert-sports-site!`.*

- [x] **F3 — Invite wipes existing member's roles**: `webapp/src/clj/lipas/backend/core.clj:522`
  ✅ 2026-06-10: invite-existing-member-conflict-test — invite of an existing member now throws
  `:already-member` (409 via handler mapping) before any side effect; roles intact, no email.
  `invite-org-member!` has no already-a-member guard; `upsert-member-full` replaces `:roles` with
  the form's selection, which defaults to empty. Re-inviting an existing org-admin's email silently
  demotes them to plain member (plus a misleading "you've been added" email).
  *Fix direction: reject or merge when the resolved user is already a member.*

- [x] **F5 — Edit-history email harvesting**: `webapp/src/clj/lipas/backend/handler.clj:439`
  ✅ 2026-06-10: site-edit-history-email-privacy-test (route-level; fails on old —
  old returned `(or email username)` to every caller). `resolve-account-names` takes an
  `emails?` flag; route passes `{:emails? (check-privilege identity {} :users/manage)}` —
  non-admins get usernames only. Response shape unchanged (FE drawer reads `:author`).
  Caveat: legacy accounts whose USERNAME is their email still leak it to non-admins
  (~same caveat as the site-editors policy; PM/DPO follow-up).
  `/actions/get-site-edit-history` has `:require-privilege nil` (any authenticated user, any
  lipas-id) and `core/site-edit-history` → `resolve-account-names` returns
  `(or (:email a) (:username a))` for every revision author, unmasked. Contradicts the
  admin-only policy for legacy-user emails in `get-site-editors`.
  *Fix direction: return display names / gate emails behind `:users/manage` like site-editors does.*

- [x] **F14 — `:acting-org-id` audit forgery**: `webapp/src/clj/lipas/backend/db/sports_site.clj:20`
  ✅ 2026-06-10: acting-org-id-not-forgeable-via-save-test
  `marshall` lifts `:acting-org-id` from the user-submitted document into the `acting_org_id`
  audit column; save schema is `:closed false` and the route passes raw `body-params`, so any
  editor can forge "on whose behalf" metadata via POST /sports-sites.
  *Fix direction: dissoc `:acting-org-id` from user payloads in `save-sports-site!`; only trusted
  paths (grant/takeover) may set it.*

## P1 — Functional bugs

- [x] **F4 — Org members can't create sites in UI**: `webapp/src/cljs/lipas/ui/user/subs.cljs:53`
  ✅ 2026-06-10: org-id-role-context-test (roles_test) + user/subs.cljs contexts fixed
  `::can-add-sports-sites?`, `::permission-to-cities`, `::permission-to-types` omit
  `:org-id ::roles/any` from the role context, so catalog-projected org roles (`:org-editor` etc.)
  never match (`select-role` needs the context key). A user whose only role is org-editor sees no
  "add sports site" button and empty city/type pickers despite backend support. REPL-verified.
  `::can-add-lois?` (same file) shows the fix pattern (`:activity ::roles/any`).

- [x] **F6 — Absent `:owner-org-id`/`:edit-grants` treated as removal → 403**: `webapp/src/clj/lipas/backend/core.clj:434`
  ✅ 2026-06-10: save-preserves-ownership-fields-when-absent-test
  Plain keyword lookups, no contains?/carry-forward; both fields `:optional` in the save schema.
  Any client not round-tripping them (external integrations, GET-modify-POST scripts) gets 403
  "Not authorized to change site ownership" on a pure content edit of an org-owned site.
  *Fix direction: carry stored values forward server-side when keys are absent; authorize only
  explicit changes.*

- [x] **F7 — Members tab breaks on fresh orgs**: `webapp/src/clj/lipas/backend/org.clj:127`
  ✅ 2026-06-10: get-org-users-empty-org-test — memberless org returns `[]`; route emits `[]` JSON.
  `get-org-users` returns nil (not `[]`) for a memberless org (`(when (seq ids) ...)`); handler
  emits 200 with literally empty body; cljs-ajax `JSON.parse("")` throws → `{:failure :parse}` →
  spurious error toast on every freshly created org (create-org doesn't auto-add the creator).
  Verified end-to-end. *Fix: return `[]`.*

- [x] **F8 — Duplicate (lipas_id, event_date) revisions**: `webapp/src/clj/lipas/backend/core.clj:464`
  ✅ 2026-06-10: grant-stamps-fresh-event-date-test, takeover-stamps-fresh-event-date-test
  + `webapp/src/clj/lipas/backend/org_takeover.clj:161`
  `set-site-edit-grants!` and takeover `approve!` append revisions without a fresh `:event-date`;
  marshall passes it through verbatim. FE keys history by event-date (`lipas.ui.utils/add-to-db`),
  so the grant revision clobbers the prior one in history views; takeovers stamp claimed sites
  with the old edit timestamp. REPL-verified. *Fix: assoc fresh timestamp before upsert.*

- [x] **F9 — "other"-type org can never own a site**: `webapp/src/cljs/lipas/ui/sports_sites/views.cljs:103`
  ✅ 2026-06-10: owners.cljc "other" mapping + legacy owner select fallback when :owner nil
  `lipas.data.owners/org-type->owner` has no `"other"` key (org schema's type enum includes it;
  `:type` is even optional → nil too). Selecting such an org sets required `:owner` to nil while
  hiding the legacy owner select → form permanently unsaveable with no actionable error. Same nil
  prefill in `::init-new-site` (`ui/org/events.cljs:240`).

- [x] **F10 — Catalog-granted PTV managers never get audit emails**: `webapp/src/clj/lipas/backend/ptv/core.clj:686`
  ✅ 2026-06-10: get-ptv-managers-catalog-grant-test (ptv_test; fails on old — catalog
  member invisible). `get-ptv-managers` now also projects each member's catalog roles via
  `backend-org/derive-org-roles` (the login-path projection, not a reimplementation) and
  applies the same ptv-manager+city predicate over stored ∪ projected roles.
  PR makes `:ptv-manager` catalog-assignable, but catalog grants are projection-only (JWT, never
  persisted to account.permissions). `get-ptv-managers` filters stored `:permissions :roles`, so
  `send-audit-notification!` finds no managers and silently sends nothing (`:sent 0`).
  Direct account-role managers still work.

## P2 — Correctness, lower severity

- [x] **F11 — `:owner-locked` surfaces as HTTP 500**: `webapp/src/clj/lipas/backend/core.clj:338`
  ✅ 2026-06-10: owner-locked-returns-conflict-test (409; also added :sports-site-not-found→404)
  `check-owner-lock!` throws `{:type :owner-locked}` but `handler.clj` exception-handlers map
  (lines 56–72) has no entry → `::exception/default` → 500 internal-server-error instead of 4xx
  with the lock reason.

- [x] **F12 — Grant on unknown lipas-id creates phantom revision**: `webapp/src/clj/lipas/backend/core.clj:474`
  ✅ 2026-06-10: grant-on-unknown-lipas-id-404-test
  `grant-site-edit!`/`revoke-site-edit!` don't check existence; for lipas-admin, authorization
  passes on nil site, `set-site-edit-grants!` upserts a fragment → fresh lipas-id allocated from
  the sequence, NOT NULL violation → 500 instead of 404 (+ burned sequence value).

- [x] **F13 — Org history author NULL + partial-payload wipe**: `webapp/src/clj/lipas/backend/handler.clj:489`
  ✅ 2026-06-10: org-write-routes-record-author-test (create/update/ptv-config record caller as
  author) + update-org-partial-payload-preserves-fields-test (only payload-present keys merged;
  :instructions handled via the F32 whitelist restructure).
  `/actions/update-org`, `/actions/update-org-ptv-config`, `/actions/create-org` never pass
  `(-> req :identity :id)` → `author_id NULL` → History tab shows no editor (member ops do pass it).
  Related: `org/update-org!`'s marshall+select-keys merge overwrites `:ptv-data`/`:instructions`
  with nil on partial payloads (admin path unpinned; non-admin pin list misses `:instructions`).
  API-only — current FE sends the full org map.

- [x] **F15 — Owner org shown as raw UUID to non-members**: `webapp/src/cljs/lipas/ui/sports_sites/views.cljs:96`
  ✅ 2026-06-10: enrich-denormalizes-owner-org-name-test (fails on old — 1-arity enrich).
  `core/enrich`/`enrich*`/`index!` take an optional `org-name-by-id` map (`core/org-names`,
  one small query, resolved once per batch in bulk paths: search_indexer, org_takeover,
  bulk_operations, ptv sync/audit, elevation job); `search-meta.owner-org-name` added to
  the strict ES mapping (search.clj — REINDEX REQUIRED on deploy). FE display chain:
  user-org name → `:owner-org-name` (display-data, from ES search-meta) → UUID fallback.
  Name resolved only via `::user-org-by-id` (user's own orgs); ES search-meta has no org name and
  there's no public org-name lookup → anonymous/non-member viewers see the UUID in
  Omistajaorganisaatio. *Fix direction: index org name into search-meta or add a public name lookup.*

- [x] **F16 — who-can-edit drawer drifts from enforcement**: `webapp/src/clj/lipas/backend/core.clj:842`
  ✅ 2026-06-10: site-editors-catalog-enforcement-test (fails on old, both directions).
  Each catalog template is projected like login would (`org/derive-org-roles` on a
  hypothetical member) and tested with `roles/check-privilege` over `site-roles-context`:
  `:site/create-edit` ⇒ new `:catalog-editor-orgs` (owner/grantees excluded — already
  listed); `:activity/edit` ⇒ `:activity-editor-orgs` (now city/type-scoped correctly).
  Drawer renders the new list with `:lipas.org/role-catalog` chip (fi/se/en added).
  `site-editors` interprets catalogs by string-matching only `{:role "activities-manager"}` +
  `:activity`. Catalog-assigned city/type/site-managers (which grant `:site/create-edit`) are
  never listed (under-report); city/type-scoped activities-managers are listed for all cities
  (over-report). *Fix direction: evaluate catalogs through `roles/check-privilege` over
  `site-roles-context` — the same mechanism enforcement uses.*

## P3 — Efficiency

- [x] **F17 — Takeover approve! N+1 in open transaction**: `webapp/src/clj/lipas/backend/org_takeover.clj:161`
  ✅ 2026-06-10: approve! batch-loads all current revisions in ONE IN-query
  (`db/get-sports-sites-by-lipas-ids tx` + per-doc `core/enrich-activities`, so each doc
  equals what `core/get-sports-site` returned) before the write loop; every write still
  routes through `core/upsert-sports-site!*` with fresh :event-date + :acting-org-id.
  Existing takeover tests (approve/reclaim/idempotent/fresh-event-date/acting-org-id) green
  on the batched path — contract unchanged, no new assertions needed.
  One SELECT + one upsert per lipas-id, sequential, tx held open (Helsinki scale ≈ 3700 sites →
  ~7400 round trips). `db/get-sports-sites-by-lipas-ids` (batch IN-query) already exists, unused.

- [x] **F18 — 2000 docs fetched to show a count**: `webapp/src/clj/lipas/backend/core.clj:790`
  ✅ 2026-06-10: `core/org-sites` gained a `{:count-only? true}` arity (`:size 0`, same
  `{:total n :sites []}` shape); route accepts optional `:count-only`; FE `::get-org-sites`
  sends `:count-only true` (its only consumer is the owned-count) and `::owned-sites-count`
  simplified to `(or (:total owned) 0)`. org-sites-test extended: count-only :total ==
  full :total + `:sites []`, fn- and route-level (fails on old: 4-arity didn't exist).
  Default (no count-only) behavior unchanged for other callers.
  FE's only consumer of `org-sites` "owned" is `::owned-sites-count` reading `:total`. Use
  `:size 0` + track_total_hits (or `_count`). Also a latent truncation hazard for any future
  consumer of `:sites` (>2000 owned sites is real: Helsinki has ~3700).

- [x] **F19 — ES aggregation runs for zero-org users**: `webapp/src/clj/lipas/backend/handler.clj:355`
  ✅ 2026-06-10: guarded with `(when (seq orgs) ...)`; `(get nil k 0)` keeps the mapv
  total-noop for zero orgs. Covered by the existing zero-org get-current-user-orgs test +
  current-user-orgs-site-count-test (both green).
  `get-current-user-orgs` (hit by every authenticated session at init/refresh) runs
  `org-owned-site-counts` unconditionally; most users have no orgs. Guard with `(when (seq orgs) ...)`.

- [~] **F20 — site-editors loads all orgs per request**: `webapp/src/clj/lipas/backend/core.clj:828`
  ✅ 2026-06-10 PARTIAL: `orgs-relevant-to-site` fetches only owner/grantee orgs (id IN)
  plus catalog-bearing orgs (`document->'role-templates' <> '{}'::jsonb` — catalog-less
  orgs can never appear in the answer). Still scans every catalog-bearing org's full
  document per request; fine at today's org counts, no index-backed narrowing — left open
  if org count grows.
  `org/all-orgs` (full documents) + in-memory scan per call, endpoint open to any authenticated
  user per site view. Target owner/grantee orgs by id + jsonb predicate for catalog matches.

- [x] **F21 — Double org fetch + TOCTOU in member ops**: `webapp/src/clj/lipas/backend/org.clj:367`
  ✅ 2026-06-10: add-member!/set-member-roles! fetch the doc once and validate+write against the
  same revision; REPL-instrumented current-row count = 1 per op (was 2).
  `add-member!`/`set-member-roles!` SELECT the org doc twice (validate → update), no tx around the
  pair. Low risk (stale-validated roles degrade to no-op grants), but trivially collapsible by
  passing the fetched doc through.

## P4 — Cleanup / dead code / altitude

- [x] **F22 — `upsert-member-full` ≡ `upsert-member`**: `webapp/src/clj/lipas/backend/org.clj:293`
  ✅ 2026-06-10: deleted `upsert-member-full`; `add-member!` calls the surviving `upsert-member`.
  Covered by existing add/invite/set-roles tests (all green).

- [x] **F23 — Three private `->uuid` copies**: `org_takeover.clj:16`, `org.clj:26`,
  ✅ 2026-06-10: cljs part done (6 call sites → utils/->uuid-safe); clj copies now deleted
  too — all org.clj (5) + org_takeover.clj (6) call sites use `utils/->uuid-safe`.
  Behavior-identical for every call site: the clj copies used `parse-uuid` which already
  returned nil (not throw) on garbage strings; verified `(org/get-org db "not-a-uuid")` → nil
  before and after, and `get-history` output byte-identical pre/post on a live org.
  `ui/org/events.cljs:249` — `lipas.utils/->uuid-safe` (utils.cljc:91, cljc) already does this.
  (Note: -safe returns nil on garbage where the clj copies throw.)

- [x] **F24 — Duplicated list subs**: `webapp/src/cljs/lipas/ui/roles/editor.cljs:35`
  ✅ 2026-06-10: admin/subs.cljs copies deleted; admin/views.cljs user-dialog now
  subscribes `::role-editor/{cities,types,sites,activities}-list` (alias already required).
  Grep-verified: no other subscriber of either set; cljs compile clean.
  `->list-entry` + `::types-list/::cities-list/::sites-list/::activities-list` copied from
  `admin/subs.cljs:90-125`; comment says "relocated" but originals remain and admin/views still
  uses them. Delete one set.

- [x] **F25 — Duplicate account-name resolver**: `webapp/src/clj/lipas/backend/core.clj:867`
  ✅ 2026-06-10: ONE shared `org/resolve-account-names [db ids emails?]` (honeysql; keeps
  the F5 emails? mask). `core/site-edit-history` delegates to it; `org/get-history` calls it
  with `emails? true` (admin-only :org/manage endpoint). REPL-verified get-org-history output
  byte-identical before/after; masked vs email resolver behavior verified live.
  `resolve-account-names` (hand-built IN-clause SQL) duplicates `org/get-history`'s honeysql
  resolution (org.clj:196-203), same output shape. Extract one shared fn (GDPR-sensitive — masking
  rule changes must land once).

- [x] **F26 — Near-identical org emails**: `webapp/src/clj/lipas/backend/email.clj:132`
  ✅ 2026-06-10: one private `send-org-membership-email!` builder (intros + per-variant action
  templates); REPL-compared old vs new output for both variants — byte-equivalent.

- [x] **F27 — Legacy member-mutation path is test-only**: `webapp/src/clj/lipas/backend/org.clj:445`
  ✅ 2026-06-10: deleted `add-org-user-by-email!`/`update-org-users!`/`apply-member-change`/
  `resolve-user-id`; test_utils `gen-org-member-user` + ptv_test + org_test ported to
  `add-member!`/`remove-member!` ("org-admin"→roles ["admin"], "org-user"→[]); ptv-test green.

- [x] **F28 — Dead `::get-all-users` fetch**: `webapp/src/cljs/lipas/ui/org/events.cljs:50`
  ✅ 2026-06-10: deleted; grep-verified zero consumers
  Fetches the full PII-heavy /users list into `[:org :all-users]` on every admin org-view init;
  `::all-users`/`::all-users-options` subs have zero consumers. Delete event + subs + dispatch.

- [x] **F29 — Polling loop with silent give-up**: `webapp/src/cljs/lipas/ui/org/events.cljs:111`
  ✅ 2026-06-10: replaced polling with {:then} continuation on ::get-user-orgs; failure now surfaces error toast
  `::wait-for-orgs-then-init` retries 100ms×10 then silently assoc's nil editing-org (blank page,
  no error) if the orgs fetch takes >1s; duplicates `::init-view`'s org lookup. Replace with an
  on-success continuation on `::get-user-orgs`.

- [x] **F30 — Misc dead code in org/events.cljs**: write-only `[:org :current-org-id]` (line 196),
  ✅ 2026-06-10: deleted dead key/event/requires; grep-verified
  never-dispatched `::init-catalog-editor` (line 354), unused requires `cognitect.transit` and
  `reitit.frontend.easy`.

- [x] **F31 — Catalog dedup only in FE**: `webapp/src/cljs/lipas/ui/org/events.cljs:406`
  ✅ 2026-06-10: catalog-normalized-on-save-test — `update-catalog!` normalizes before
  validate+store (per template: drop role-less specs, `distinct` exact duplicates; mirrors
  FE sanitize-catalog). Fails on old two ways (REPL-shown): role-less `{}` spec threw
  :invalid-catalog, and duplicates passed schema → stored verbatim. Non-sequential `:roles`
  still 400s (normalization can't mask validation). Schema unchanged (permissive).
  `sanitize-catalog` dedups role-specs client-side; backend `org/update-catalog!` and the
  role-templates schema still accept duplicates — direct API callers reintroduce the gunk.
  Normalize in `update-catalog!` (or give schema distinctness).

- [x] **F32 — Org field-pinning at the wrong altitude**: `webapp/src/clj/lipas/backend/handler.clj:462`
  ✅ 2026-06-10: update-org-details-whitelist-test — ceiling moved into
  `org/update-org-details!` (whitelist name/contact/instructions); route dispatches on
  `:users/manage`; existing org-admin-cannot-change-type-or-ownership-test +
  org-admin-can-set-instructions-test still green.

---

## Post-review live-testing findings

- [x] **F33 — Org-admin without editor template sees a silently empty Kohteet list**:
  `webapp/src/clj/lipas/backend/handler.clj:422` + `webapp/src/cljs/lipas/ui/bulk_operations/events.cljs:42`
  ✅ 2026-06-10: org-admin-without-editor-template-reads-but-cannot-write-test (bulk-operations-test)
  Live testing: a member whose roles are only `["admin"]` projects org-admin + org-user —
  no `:site/create-edit` (REPL-verified: `{:org-member? true :org-manage? true
  :site-create-edit? false}`). The Kohteet count chips (`/actions/get-org-sites`, gated
  `org-member-or-admin?`) showed e.g. 210 owned, but the list endpoint
  (`/actions/get-org-sites-for-bulk`, gated `:site/create-edit`) 403'd and the FE swallowed
  it (`::get-editable-sites-failure` only console.error'd into an unrendered
  `[:bulk-operations :error]`) → empty list under a non-zero badge.
  Fix: (1) list endpoint re-gated `org-member-or-admin?` — read-only candidate listing is
  member-visible; `/actions/mass-update-org-sites` keeps `:site/create-edit` AND re-authorizes
  per-site against DB state (F2). (2) FE gates the ACTIONS, not the list: new
  `::can-bulk-edit?` sub (org/subs.cljs, `roles/check-privilege` `{:org-id #{org-id}}`
  `:site/create-edit`) hides selection checkboxes + Massapäivitys launcher for plain
  members; list + who-can-edit drawer stay visible. (3) list-fetch errors rendered via
  `:lipas.org/sites-load-failed` (fi/se/en) and cleared on refetch.
  Regression test fails on old gate 3 ways (list 403, empty id-set; verified by
  temporarily restoring the old gate); write-stays-403 + doc-untouched assertions
  pass on both.

---

## Refuted during review (no action)

- Migration `org-roles-account-cleanup` stripping kebab-spelled `org-id` roles: all write paths
  snake-case permission keys; live-DB check found no kebab org-role keys and no post-migration
  leftovers.
