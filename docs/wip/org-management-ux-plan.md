# UX Plan — Organization Management

Status: Draft for review
Companion to: `org-management-design-spec.md` (backend/permission model)
Base: the existing `lipas.ui.org` views (`orgs-list-view` + tabbed `org-view`).

---

## 1. Principles

1. **One interface, capability-gated.** LIPAS admins and org admins use the *same* screens. Every control is shown/enabled by a capability check, never by a separate admin-only component. (Today there are two near-identical member-add components — `admin-user-management` vs `org-admin-user-management` — that get **unified**.)
2. **Make the ceiling visible.** Org admins assign from a catalog they cannot edit; the catalog is shown to them read-only so the menu they pick from is legible.
3. **Data-collection first.** Org features never get in the way of editing sites. A member with no org needs nothing here.
4. **Transparency over control.** Owners *see* who can edit their sites (incl. ambient activity editors) even where they don't *manage* it.
5. **History is first-class.** The append-only org log is surfaced as a readable timeline, not hidden.

## 2. Capability model (drives all gating)

Reuse existing subs `::subs/is-lipas-admin`, `::subs/is-org-admin? org-id`, `::subs/is-org-member? org-id`. Introduce one helper sub `::subs/can? capability org-id` resolving the matrix from spec §10:

| Capability | lipas-admin | org-admin | member |
|---|---|---|---|
| `:org/create`, `:org/edit-type+ownership`, `:org/edit-catalog` | ✓ | – | – |
| `:org/edit-contact`, `:org/edit-ptv` | ✓ | ✓ | – |
| `:org/manage-members` (invite / remove / set-role / assign-templates) | ✓ | ✓ | – |
| `:org/grant-site-edit` (cross-org) | ✓ | ✓ | – |
| `:org/view` (roster, sites, history, catalog read-only) | ✓ | ✓ | ✓ |
| edit **direct** user-level roles | ✓ (existing user-mgmt UI, *separate from org UI*) | – | – |

Note: direct user-permission editing stays in the existing admin user-management screen — it is the *other* plane and intentionally **not** part of the org UI.

---

## 3. Screen 1 — Organizations list (`orgs-list-view`, evolve)

Entry point; same component, scope differs by role.

- **LIPAS admin:** all orgs, with a search/filter field; `+ New organization` FAB (exists).
- **Org admin / member:** their org cards (exists).
- **Card content (enriched):** org name · type chip · **# sites owned** · # members · your-role badge. The site count is the first taste of the "our sites" value (Q1) and the coverage story (spec Vaihe 4). Empty state for non-members unchanged ("contact admin").

---

## 4. Screen 2 — Organization detail (`org-view`, evolve the tabs)

Keep the tabbed shell. Tabs (✎ = new/changed):

### 4.1 Overview ✎ (was "Contact")
- Name, **type**, primary contact (phone/email/website/reservations-link). Contact fields editable by `:org/edit-contact`.
- **Type + ownership-claim rule** (`:city-codes` + `:owners`): editable by `:org/edit-type+ownership` (lipas-admin) only; **read-only** to org-admin (with a tooltip: "defines which sites this org may own — set by LIPAS"). This is where the ceiling for ownership lives.
- Save (gated). New org: only this tab (as today).

### 4.2 Members ✎ (the core self-service surface)
Unify the two add components into one **Invite member** control:
- Email field (works for anyone). If the email matches an existing account → added immediately; otherwise → an **invitation** is created (magic-link). LIPAS admin additionally gets the existing user autocomplete as a convenience, but the email path is canonical.
- Initial assignment in the same row: **org-role** (Admin / Member) + **templates** multi-select (chips from the catalog).

**Members table** — columns:
| Member | Org-role | Assigned templates | (actions) |
|---|---|---|---|
| name / email | Admin ▾ / Member ▾ (inline, gated) | template chips, add/remove inline (gated) | remove |

- Template chips are drawn **from the org's catalog only** (the ceiling). Hovering a chip shows what it grants in plain language ("Editing of this org's sites", "Melonta activity editing").
- Members (non-admins) see the roster read-only (transparency) — no controls.

**Pending invitations** sub-section (below the table): email · assigned templates · invited-by · expiry · `Resend` / `Revoke`. Visible to admins.

### 4.3 Roles & templates ✎ NEW
Read for everyone, write for lipas-admin (`:org/edit-catalog`).
- Lists each catalog entry: label · plain-language description of what it grants (translate the underlying role specs) · # members currently assigned.
- LIPAS admin: add/edit/remove templates, each composed from the existing role vocabulary (org-editor / activities-manager+activity / ptv-manager+city). Org-admin: **read-only** — this is the menu they assign from, made explicit.

### 4.4 Our sites ✎ NEW (Q1 + transparency + grants; spec Vaihe 4)
- **Owned** filter (default): sites where `owner-org-id = this org` (ES). Columns: name · type · city · last-edited · completeness/freshness indicator. This is the org dashboard.
- **Editable** filter: owned ∪ granted (sites this org can edit but doesn't own).
- **Per-site "who can edit" (derived, on expand / drawer):** owner org · grantee orgs · **activity-editor orgs & users** (the melonta-association case) · any legacy direct users. Answers Q2 at the site, read-only.
- **Manage edit-grants** (`:org/grant-site-edit`): on owned sites, grant/revoke another org's edit (single or bulk select) → site revision.
- **Claim ownership**: "Claim N sites matching your ownership rule" action (from §4.1's rule); take-over may require LIPAS approval (see §6). Bulk-ops (existing tab) folds in here.

### 4.5 PTV (existing)
Unchanged; gated by `:org/edit-ptv`.

### 4.6 History ✎ NEW (immutable audit)
- Reverse-chronological timeline over `org` revisions + the org's site-ownership/grant revisions: *who* changed *what* *when* — contact edits, catalog changes, member add/remove, template (re)assignments, ownership take-overs, cross-org grants.
- Read-only; visible to admins (optionally members, for transparency). Backed directly by the append-only stores — no separate audit feature to build.

---

## 5. Site-page integration (optional, later phase)

On the sports-site page, an **"Editing rights"** panel: owner org + the derived `site-editors` list (owner / grantees / activity editors / legacy), and — for the owner-org admin — the grant control inline. This puts the Q2 answer where a user actually stands on a site, complementing §4.4.

---

## 6. Approval flows (spec 2.8)

- **Take-over** (org claims sites): org-admin requests → appears in a LIPAS-admin **"Pending approvals"** area (a filter on the orgs list or a small admin queue) → approve/deny. Model as revision states (`requested → approved`).
- **Transfer** (owner → new owner): both org admins must confirm; surface as an action with a pending-acceptance badge on both orgs' Our-sites tab.
- Keep v1 light; if approval adds friction, ship take-over as lipas-admin-initiated first and add org-requested later.

---

## 7. Re-frame surface (additions)

**Subs:** `::can? cap org-id`, `::org-owned-sites org-id`, `::org-editable-sites org-id`, `::site-editors lipas-id`, `::org-templates org-id`, `::org-history org-id`, `::pending-invitations org-id`, `::member-template-options org-id` (= catalog keys).

**Events:** `::invite-member`, `::resend-invitation`, `::revoke-invitation`, `::set-member-org-role`, `::set-member-templates`, `::remove-member`, `::edit-template-catalog` (admin), `::grant-site-edit`, `::revoke-site-edit`, `::claim-ownership`, `::request-takeover` / `::approve-takeover`.

**Refactor:** merge `admin-user-management` + `org-admin-user-management` → one `invite-member` component parameterized by capability (autocomplete branch only when `is-lipas-admin?`). Replace the org-role `Select` with org-role + templates assignment. The member-row delete already exists; generalize to role/template edits.

---

## 8. Phasing (track backend phases)

- **UX-P1 (with backend Phase 2–3):** evolve **Members** (templates + unified invite + pending invitations) and add the read-only **Roles & templates** tab. This delivers self-service assignment — the headline ask.
- **UX-P2 (with backend Phase 1 + 4):** **Our sites** tab (owned list + completeness, derived "who can edit", edit-grants, claim ownership). Delivers Q1/Q2 in the UI and the dashboard.
- **UX-P3:** **History** tab; **site-page editing-rights** panel; list-view coverage hints; approval queue.

## 9. Open UX questions

1. Should **members** (non-admins) see the History and full roster, or only admins? (Spec leans transparency → show, read-only.)
2. Template chips: show the **underlying privileges** to admins, or only the human label? (Label for org-admins, expandable detail for lipas-admins.)
3. "Our sites" completeness/freshness metric — reuse an existing data-quality signal or define a simple "last-edited age" first?
4. Where does **claim ownership / bulk take-over** live — inside "Our sites", or keep the existing Bulk-operations tab as its home?
