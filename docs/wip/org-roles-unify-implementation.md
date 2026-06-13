# Implementation plan — unify `:org-role` + `:templates` → one `:roles` list

Execution checklist for the refactor designed in `org-management.md` §13 (read that
for rationale). Single PR on branch `feat/org-management`. Pre-production, so a clean
cutover — no dual-read/back-compat shims.

## Target model (recap)

- Member document entry: `{:user-id … :roles ["admin" "editor" …]}` — one list, drawn
  from `#{"admin"}` ∪ keys of the org's `:role-templates` catalog.
- `:admin` = **reserved engine role** (mapped in code, NOT stored in the catalog) →
  projects `:org-admin` (`:org/manage`). So a catalog edit can never strip admins.
- `:org/member` = **baseline**, projected unconditionally for everyone in `:members`
  (membership ⟺ `:org/member`). Never stored, never assignable.
- Catalog key stays `:role-templates` (only the per-member field becomes `:roles`).

## Invariants to preserve (verify these still hold)

1. Ceiling: a `:roles` entry that is neither `"admin"` nor a catalog key expands to
   nothing.
2. Catalog (lipas-admin, wholesale replace) can never remove `:admin`.
3. Membership ⇒ `:org/member` baseline, even with `:roles []`.
4. Matching engine unchanged except adding `:org-admin` to `org-scoped-roles`.
5. FE capability gating already uses `check-privilege :org/manage`/`:org/member`
   (`org/subs.cljs ::is-org-admin?`/`::is-org-member?`) — must stay correct, no change.

---

## Step 1 — projection + helpers (`webapp/src/clj/lipas/backend/org.clj`)

- [ ] `org-scoped-roles` (≈L390): add `:org-admin` → `#{:org-editor :org-admin}`.
- [ ] Add `reserved-roles` def: `{:admin {:role "org-admin"}}` (string role to match the
      stored wire shape used elsewhere in `member->roles`).
- [ ] `member->roles` (≈L394-412): rewrite to
      - always `(cons {:role "org-user" :org-id [org-id-str]} …)` (baseline),
      - expand `(:roles member)` via `(or (some-> (reserved-roles (keyword k)) vector)
        (:roles (get catalog (keyword k))))`,
      - inject `:org-id` for `org-scoped-roles` (now incl. `:org-admin`).
      Drop the `:org-role` `case`.
- [ ] `validate-assignment!` (≈L310-326): drop the `:org-role` enum branch; validate
      `:roles ⊆ (conj catalog-keys "admin")`. Signature now `{:keys [roles]}`.
- [ ] Merge `set-member-org-role!` + `set-member-templates!` → **`set-member-roles!`**
      `[db org-id user-id roles author-id]` (validate + `update-document!` assoc member
      `:roles`).
- [ ] `add-member!` (≈L349): assignment arg `{:roles …}`; `upsert-member-full`
      (≈L295-302) takes `roles` and writes `{:user-id … :roles (mapv name roles)}`.
- [ ] `upsert-member` (≈L250-253) + `update-org-users!` (≈L275-279) +
      `add-org-user-by-email!` (≈L433): legacy account-role path → write
      `:roles ["admin"]` for `org-admin`, `:roles []` for `org-user`.
- [ ] `get-org-users` (≈L118-135): augment accounts with `:roles` (drop
      `:org-role`/`:templates`).
- [ ] `get-history` diff `diff-org-docs` (≈L175-177): replace the two member branches
      (org-role change, templates change) with one
      `"Roolit muuttui: «name» [a] → [b]"`.

## Step 2 — schema (`webapp/src/cljc/lipas/schema/org.cljc`)

- [ ] `members` (≈L94-98): entry → `[:map [:user-id [:or :uuid :string]]
      [:roles {:optional true} [:vector :string]]]`. `role-templates`/`role-spec` unchanged.

## Step 3 — endpoints (`webapp/src/clj/lipas/backend/handler.clj`)

- [ ] `invite-org-member` (≈L505-537): body `[:map [:email …] [:roles {:optional true}
      [:vector :string]]]`; handler `select-keys [:email :roles]`.
- [ ] Merge `set-org-member-role` + `set-org-member-templates` → **`set-org-member-roles`**
      (≈L561-601): body `{:org-id :user-id :roles}`, calls `org/set-member-roles!`.
      Remove the other route.

## Step 4 — migration (new, self-contained — NO `org/*` calls; heed §8 lesson)

- [ ] New `webapp/resources/migrations/20260608NNNNNN-org-roles-unify.{up,down}.sql`
      **or** an EDN+clj code migration (`lipas.migrations.org-roles-unify`). Code
      migration is easier for the jsonb reshape. For each `org_current` doc append ONE
      new `org` revision with members rewritten:
      `:roles = (cond-> (vec (:templates m)) (= "admin" (:org-role m)) (conj "admin"))`,
      dropping `:org-role`/`:templates`. Use `next.jdbc` + the `db.utils` jsonb
      protocol (require `lipas.backend.db.utils` for the side-effecting extensions).
- [ ] Leave `org-event-log` (the seed) as-is — it still seeds old-shape members; this
      migration runs after it on fresh DBs and over existing dev/staging DBs alike.
- [ ] Date it after `20260607120000`.

## Step 5 — frontend org (`webapp/src/cljs/lipas/ui/org/{views,events,subs}.cljs`)

- [ ] `views` invite form (≈L360-383): remove org-role `Select`; one **roles**
      multi-select. Options = `{:value "admin" :label (tr :lipas.org/role-admin)}` ++
      catalog entries. Default empty.
- [ ] `views` members table (≈L440-465): remove org-role select column; keep/extend the
      roles multi-select (≈L399-426) to include the `admin` option.
- [ ] `events` `::invite-member` (≈L305-315): build `{:email … :roles …}` (drop
      org-role/templates).
- [ ] `events`: merge `::set-member-org-role` (≈L336) + `::set-member-templates`
      (≈L348) → `::set-member-roles` → `POST set-org-member-roles {:org-id :user-id
      :roles}`.
- [ ] Role-options helper: catalog keys + `"admin"`; reused by invite + table.
- [ ] `subs` `::is-org-admin?`/`::is-org-member?`: NO change (already check-privilege).

## Step 6 — frontend admin add-user-to-org (`webapp/src/cljs/lipas/ui/admin/{views,events,subs}.cljs`)

- [ ] `subs ::add-user-to-org-role` (≈322), `views` (≈566-588, 645), events
      `::set-add-user-to-org-role`: the admin screen's "add user to org" still uses an
      org-role select. Map to `:roles` (`admin`→`["admin"]`, `member`→`[]`), or switch
      to a roles multi-select. Backend path is `update-org-users!` (Step 1).

## Step 7 — i18n (`webapp/src/cljc/lipas/i18n/{fi,se,en}/lipas_org.edn`)

- [ ] Add `:role-admin` — fi "Ylläpitäjä" · se "Administratör" · en "Administrator".
- [ ] Members column/label: reuse/repurpose `:org-role` ("Rooli"/"Roll"/"Role") →
      prefer a plural `:roles` label ("Roolit"/"Roller"/"Roles"); drop `:org-role` if
      unused after FE changes.

## Step 8 — tests

- [ ] `webapp/test/clj/lipas/backend/org_test.clj`: member fixtures + endpoint bodies
      → `:roles`; `set-org-member-roles` endpoint; invite with `:roles []` (plain
      member ⇒ only `:org/member`) and `:roles ["admin"]` (first-admin).
- [ ] `webapp/test/clj/lipas/roles_test.clj` (and/or org-test projection): baseline
      present for any member; reserved `:admin` ⇒ `:org/manage`; ceiling (bogus key ⇒
      nothing); a catalog edit dropping all data roles does NOT remove `:admin`.

## Sequencing & verification

1. Steps 1-2-4 first (engine/projection/schema/migration). Verify in REPL:
   - fresh-DB migration repro (pattern already used for the ptv-organizations fix:
     create throwaway DB, `migratus/migrate`, assert `org_current` members have
     `:roles` and no `:org-role`).
   - `derive-org-roles` for an equivalent member yields the same `{:role :org-id}` set
     as the old split (baseline + admin + data roles; ceiling drops bogus).
   - `bb test-ns lipas.roles-test lipas.backend.org-test` green.
2. Step 3 (endpoints), then re-run org-test.
3. Steps 5-6-7 (FE + i18n): `(user/compile-cljs)` clean; `(user/reset)`; browser smoke
   of invite + role change + admin role.
4. Full `bb test-ns …` green; push; confirm CI green.

## Notes / decisions already made

- Catalog key stays `:role-templates`; renaming to `:role-catalog` is optional polish,
  out of scope.
- No dual-read back-compat: clean cutover (pre-prod).
- `:admin` reserved in `reserved-roles` (org.clj), not in `roles.cljc` registry as
  catalog-assignable — keeps the catalog purely data-edit and admins un-strippable.
- `roles.cljc` needs NO change (`:org-admin`/`:org-user`/`:org-editor` already defined
  with `:org-id` context); the only projection-set change is in org.clj.
