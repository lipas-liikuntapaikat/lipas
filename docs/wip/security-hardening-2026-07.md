# Security hardening — 2026-07

Findings from a whole-codebase security analysis (2026-07-29) covering the
backend HTTP surface, authentication/authorization, the LLM features, and
deployment configuration.

Branch: `hardening/security-critical-high`.

Working order: **critical → high**, one commit per finding. Medium and low are
tracked here but deliberately deferred until the critical/high set is closed.

Status legend: `pending` · `in progress` · `fixed` · `wontfix` · `deferred`

---

## Critical

### C1 — Password-reset host injection → account takeover

**Status:** fixed

`webapp/src/clj/lipas/backend/handler.clj:900` → `core.clj:162`

`POST /api/actions/request-password-reset` declares `:parameters {:body {:email
string?}}` but the handler passes raw `body-params` into
`core/send-password-reset-link!`, which reads `:reset-url` from it and builds
the magic link as `(str url "?token=" token)`. Reitit coercion writes to
`:parameters`, never `:body-params`, so the extra key is never validated.

Verified live, unauthenticated:

```
POST /api/actions/request-password-reset
{"email": "admin@lipas.fi", "reset-url": "https://evil.example.com/x"}
→ 200; email sent to admin@lipas.fi containing
  https://evil.example.com/x?token=<7-day login JWT>
```

`/actions/order-magic-link` already carries the correct defence — the
`magic-link-login-url` domain whitelist in `schema/handler.cljc:103` with a
regression test. `request-password-reset` was missed.

**Also found while fixing:** the whitelist itself was a bare
`str/starts-with?` prefix check, so it was decorative — every attacker host
that merely *began* with an allowed origin walked through it
(`https://lipas.fi.attacker.example/`, `https://localhost.attacker.example/`),
as did userinfo forms (`https://lipas.fi@attacker.example/`). That affected the
already-live `order-magic-link` and `update-user-permissions` sinks too, so
fixing only `request-password-reset` would have left the hole open.

**Fixed:**

- `schema/handler.cljc` — `magic-link-url?` now *parses* the URL and matches
  the **host** against a set, requires `https`, and rejects userinfo. Replaces
  the prefix check for every caller.
- `handler.clj:900` — closed body schema, `:reset-url` typed as
  `magic-link-login-url`, handler reads `:parameters :body` instead of the raw
  `body-params`.
- `handler.clj:1008` — `send-magic-link` `:login-url` was still bare `string?`;
  now whitelisted like every other sink (admin-gated, so never the exposed one).
- Tests: `request-password-reset-url-whitelist-test` (off-domain, prefix
  bypass, missing url) and an expanded
  `order-magic-link-login-url-whitelist-test` (prefix bypass, userinfo,
  non-https, plus the real hosts still accepted).

**Verified live** after the fix — `evil.example.com`,
`lipas.fi.attacker.example`, `lipas.fi@attacker.example` and `http://lipas.fi`
all return 400 and send no mail; `https://localhost/passu-hukassa` still sends.

### C2 — nREPL published on 0.0.0.0:7888

**Status:** fixed

`webapp/src/clj/lipas/backend/config.clj:99`, `system.clj:71`,
`docker-compose.yml:187` / `:202`

The production `backend` container starts an unauthenticated nREPL server and
compose publishes `'7888:7888'`, which binds all host interfaces. nREPL is
unconditional arbitrary code execution in the backend JVM.

**Accepted by the maintainer as by-design capability** — the prod host firewall
blocks inbound 7888 and server access requires SSH over VPN; the REPL is
reached through an SSH tunnel. Treated here as defence-in-depth only, not as an
open hole.

**Fixed:** `docker-compose.yml` now publishes `127.0.0.1:7888:7888` on both
`backend` and `backend-dev`. Verified with `docker compose config`
(`host_ip: 127.0.0.1` on both). The nREPL process keeps binding `0.0.0.0`
*inside* the container — that is the container's own network namespace, and
Docker cannot forward a published port to a container-loopback listener, so
"hardening" the bind would just break REPL access. `config.clj` carries a
comment saying so, to stop a well-meaning future change.

`ssh -L 7888:localhost:7888 lipas-prod` terminates on the host's loopback and
keeps working unchanged.

Worth knowing: Docker installs its own iptables rules ahead of ufw, so a
published port is not necessarily covered by the host firewall — which is why
this was worth doing even with the firewall in place.

**Residual (follow-up, not addressed):** compose declares no networks, so every
service shares the default bridge and geoserver / kibana / mapproxy / logstash
can all still reach `backend:7888`. Geoserver in particular has a long RCE CVE
history, so that is a real lateral-movement path. Fixing it means splitting the
compose network topology — larger than this pass.

---

## High

### H1 — Unauthenticated arbitrary Elasticsearch query execution

**Status:** fixed

`handler.clj:840` → `core.clj:927` → `search.clj:520`

`POST /api/actions/search` forwards `body-params` verbatim as the Elasticsearch
`_search` body. Same passthrough in `query-finance-report`, `query-subsidies`,
`create-sports-sites-report` (arbitrary `:search-query`), `search-schools`,
`search-population`.

Verified live, all unauthenticated, all HTTP 200:

- `script_score` with a Painless `:source` — executed
- `runtime_mappings` `{:script {:source "emit(42)"}}` + sum agg — returned
  `2395302.0`, i.e. Painless ran once per document across the index
- 5000-bucket terms aggregation — executed

The indexed data is public open data, so disclosure is not the issue. Arbitrary
Painless plus unbounded aggregation cardinality is a cheap anonymous CPU/heap
DoS against the cluster the whole site depends on, and it leaves the deployment
permanently exposed to Elasticsearch scripting sandbox CVEs.

**Fixed:** new `lipas.backend.search-guard`, applied at the six public handler
boundaries only — `core/search`, `search/search` and `search/scroll` are
untouched, so internal callers that legitimately build large queries
(`core/org-sites` `:size 2000`, `core/calculate-stats` agg `:size 400`,
`core/search-fields` `:size 1000`) are unaffected.

- **Scripting** — rejects any map key whose lower-cased name *contains*
  `"script"`, plus `runtime_mappings`. A substring test rather than a set of
  known names: ES spells scripting into a long and growing list of parameters
  and one missed name is a full bypass. Deliberately over-broad — it also
  rejects `description` (de-**script**-ion). Verified that no key in
  `lipas.backend.search/mappings` contains the substring and no client sends
  one as a query key, so the over-breadth costs nothing today; a test pins the
  behaviour so a future field name fails loudly in CI rather than in prod.
  `function_score`, which the FE does use, contains "score" not "script".
- **Sizes** — top-level `:size`/`:from` capped at 10000 (ES's own default
  `index.max_result_window`; largest real client value is 5000), every nested
  `:size` capped at 2000 (largest real value is 1000, the age-structure
  `composite` agg). Only numeric values are capped, so a field literally named
  `size` (`{:range {:size {:gte 1}}}`) isn't mistaken for a limit.
- Rejects with 400 rather than clamping or stripping — silently clamping a
  5000-row report to 2000 hands the user a quietly-wrong file.

**Frontend change (deliberate, verify before merge):** the FE was sending
Painless on *every* map search. `->es-search-body` merged `add-distance-fields`
whenever the map centre had lat/lon — i.e. always — producing three
`script_fields` (`arcDistance`) per hit, on `/actions/search` and on the report
flow. Nothing ever read the result: distance *sorting* uses a native
`_geo_distance` sort, and every consumer of the response reads only `_source`
and `_score` (`::search-results-table-data`, `::search-results-list-data`,
`map.subs/::geometries-fast`). It was pure cluster cost, and the only obstacle
to a blanket scripting rejection, so it was deleted rather than allowlisted.
Dates to `dc602e8b`.

**Verified end-to-end over HTTP** against the running system: `script_score`,
`runtime_mappings`, a 100k-bucket terms agg and `size: 1000000` all return 400;
a normal search and a 5000-row analysis-mode page return 200 with hits.

**Browser-checked:** with the guard live, the app's real search request returns
200 — the guard does not reject what the FE actually sends. The local dev app
shows "0 hakutulosta" and an empty map, but that reproduces *identically* on
unmodified `HEAD` (checked by reverting the FE file, recompiling and reloading),
so it is pre-existing local index/env drift, not a regression. A positive
"results render" check still needs an environment with a working local index.

**Noted, not acted on:** `/actions/search-schools` and
`/actions/search-population` have no callers anywhere in the repo — the
analysis code builds its own queries via `analysis.common`. They look like dead
endpoints worth deleting. `/actions/find-fields` and `/actions/search-lois`
build their queries from closed schemas and are *not* passthroughs.

### H2 — Archived / deactivated users can still log in

**Status:** fixed

`auth.clj:26`, `auth.clj:41`, `handler.clj:878`

`user-status` is `[:enum "active" "archived"]`, `update-user-status!` writes it
and `gdpr-remove-user!` sets it, but nothing reads it during authentication —
not `basic-auth`, not `token-backend`, not `refresh-login`.

Verified live: archived `admin@lipas.fi`, then `POST /api/actions/login` with
the correct password returned **200 with a fresh 6h token**. So
`/actions/update-user-status` — the admin "deactivate user" control — has no
security effect, and a GDPR-archived user keeps renewing sessions via
`refresh-login` indefinitely.

**Fixed:** new `auth/active?`, checked on both paths that MINT a token —
`auth/basic-auth` and the `refresh-login` handler (401). In `basic-auth` the
status is checked *after* the password so an archived account can't be
distinguished from a wrong password by response timing.

A token already issued stays cryptographically valid until it expires;
revoking those needs per-request state we don't keep (tracked as M7). Blocking
issuance bounds an archived account to one remaining token lifetime instead of
forever.

**Verified live** — `auth/basic-auth` against the real dev DB returns a session
for an active admin and `false` for the same account archived (status restored
afterwards). Tests: `archived-user-cannot-log-in-test`,
`archived-user-cannot-refresh-login-test`, and
`active-user-can-still-log-in-and-refresh-test` as the control that the new
check doesn't lock out normal users.

### H3 — Nothing structurally prevents a route from shipping unauthenticated

**Status:** fixed

`middleware.clj:51`

```clojure
(fn [route-data _opts]
  (if-let [required-privilege (:require-privilege route-data)]
    ...gate...
    {}))          ; no key ⇒ no middleware ⇒ fully public
```

Fail-open by omission. This is the structural root cause behind H4 and M1: a
newly added route that forgets `:require-privilege` silently ships public, and
nothing catches it.

**Fixed:** `lipas.backend.route-auth-test` walks the reitit router, enumerates
every route/method pair, and requires each to be either gated or listed in an
explicit `public-routes` allowlist. Adding a route becomes a forced choice:
protect it, or write it down and say why.

Three tests:

1. every ungated route must be declared — the invariant;
2. no stale allowlist entries (route deleted, or since protected), so the
   allowlist can't quietly rot into meaninglessness;
3. a hardcoded spot check that ~30 named sensitive routes (all LLM endpoints,
   user/permission admin, org admin, content writes, job control) are gated —
   belt and braces, since the invariant is only as good as the allowlist and
   someone could "fix" a failure by pasting the offending route into it.

The router is built from an **empty ctx** — route data is pure, handlers close
over db/search but building the router never touches them — so this needs no
database, no Elasticsearch and no fixtures, and is fast enough that nobody has
a reason to skip it.

**Current surface: 146 route/method pairs, 81 gated, 65 public.** All 65 are
now written down with a reason; the M1 and M2 entries carry `TODO` markers.

**Failure mode proven,** not assumed: dropping one entry from the allowlist
(simulating a route that forgets `:require-privilege`) makes the invariant fail
with the offending route named and an explanation of the fail-open behaviour.

### H4 — No auth-regression tests on the LLM endpoints

**Status:** fixed

The gates are correct — `assistant-chat` / `assistant-escalate` require
`:ai-assistant/use`, all PTV generation requires `:ptv/manage` — but no test
asserts any of it. `assistant_test.clj` contains only pure-function unit tests
and never goes through the HTTP stack.

Privileged endpoints with no 401/403 test:

| Group | Endpoints |
|---|---|
| LLM | `assistant-chat`, `assistant-escalate`, `generate-ptv-descriptions`, `generate-ptv-descriptions-from-data`, `generate-ptv-descriptions-batch`, `generate-ptv-service-descriptions`, `translate-to-other-langs` |
| Admin | `GET /api/users`, `gdpr-remove-user`, `get-all-orgs`, `list-org-takeover-requests`, `approve-org-takeover`, `deny-org-takeover`, `create-org` (403 case), `remove-org-member`, `revoke-site-edit`, `save-help-data`, `save-help-draft`, `get-help-versions`, `get-help-version`, `save-loi`, `upload-utp-image` |

40 other privileged endpoints already have explicit 401/403 tests (org
management, jobs, PTV audit, workbench, impersonation, user management), so the
gap is specific and closable.

**Fixed:** two new namespaces, `lipas.backend.llm-auth-test` and
`lipas.backend.admin-auth-test` — 9 tests, 77 assertions. Every endpoint above
gets 401 (no token) and 403 (signed-in, wrong privilege); `upload-utp-image`
gets 401 only, since it has no privilege gate (that is M3).

Three things make these worth more than the assertion count suggests:

- **A provider tripwire.** Coercion runs *before* `privilege-middleware`, so
  every LLM case runs with `clj-http`'s entry points replaced by a recorder
  that throws, and each test asserts the recorder is *empty*. No unauthorized
  request can reach OpenAI/Gemini even if a gate regresses — it becomes a loud
  failure instead of a bill.
- **Positive controls.** `privileged-caller-passes-the-gate-test` and
  `bodies-clear-coercion-test` assert the same bodies with a *correct* token
  must not yield 400/401/403. Without these, a body that drifted out of sync
  with its route schema would make every 401/403 assertion pass while proving
  nothing — the classic test-passes-for-the-wrong-reason failure.
- **Cross-privilege and cross-org cases.** An `assistant-tester` gets 403 on
  the PTV routes and a `ptv-manager` gets 403 on the assistant routes, so one
  over-broad role can't silently open everything. An org-admin of a *different*
  org gets 403 on the org-scoped endpoints, pinning the scoping that a
  plain-regular-user 403 would not.

**Mutation-tested:** with `roles/check-privilege` forced to `(constantly true)`
not one endpoint still returns 403, so every 403 assertion is load-bearing.
Four of the seven LLM routes provably hit the provider boundary when ungated.

**Independently re-verified** against the running system: all six model-calling
routes return 401 anonymous / 403 for a token with no roles, with zero provider
calls attempted.

**Found while testing:** `upload-utp-image` returns **400**, not 401, for a
bare POST — the route mounts `multipart-middleware` at route level, i.e. after
the global `coerce-request-middleware`, so coercion rejects the nil
`:multipart-params` before auth runs. Not exploitable (the handler stays
unreachable), but any future test posting a bare body there would pass for the
wrong reason. The test sends a real multipart body so its 401 is genuine.

**Gap:** the 401 side is not mutation-tested — removing a gate means deleting
route data, which can't be simulated with `with-redefs`. H3 covers that angle
statically.

---

## Medium

Working order (most-real-impact first): **M1 → M6 → M3 → M2 → M4 → M7.**
**M5 is deliberately deferred out of this branch** — the failure mode of a
buddy/bouncycastle bump is "nobody can log in", and with only dev and prod (no
staging) that risks making the whole branch un-mergeable with confidence. It
belongs in its own PR where authentication is the only thing under test.

| # | Status | Finding | Location |
|---|---|---|---|
| M1 | **fixed** | Two privilege checks commented out, exposing the endpoints: `#_#_:require-privilege :analysis-tool/experimental` on `create-heatmap` / `get-heatmap-facets`; `search-lois` privilege commented out | `handler.clj:1421`, `:1437`, `:1312` |
| M2 | **fixed** (M2a rate limiting + M2b enumeration) | Unauthenticated email sending + account enumeration; no rate limiting anywhere (`nginx` has no `limit_req`). `request-password-reset` returns 404 `:email-not-found` vs 200 → existence oracle; `order-magic-link` mail-bombing; `send-feedback` / `register` → ops inbox; `subscribe-newsletter` | `handler.clj:900`, `:975`, `:1254`, `:857`, `:1227` |
| M3 | **fixed** | `upload-utp-image` is auth-only with no privilege, no content-type check, no size cap, no extension allowlist | `handler.clj:1276` |
| M4 | **fixed** | LLM abuse controls thin: PTV LLM endpoints have no rate limit and unbounded input (`translate-to-other-langs` takes bare `:string` with no `:max`); assistant limiter is an in-process atom that resets on deploy, multiplies per instance, and never evicts | `ptv/handler.clj:87`, `assistant.clj:741` |
| M5 | **deferred** | Stale crypto stack: `buddy/buddy 2.0.0` → buddy-core 1.4.0 / buddy-sign 2.2.0 / buddy-hashers 1.3.0 / `bcprov-jdk15on 1.58` (2017, EOL artifact line). JWT alg is correctly pinned to HS512, so no alg-confusion — this is dependency hygiene | `deps.edn` |
| M6 | **fixed** | `ptv-read-access?` is not org-scoped: `:ptv/manage` for any single city grants read of any org's PTV data via the `fetch-ptv-*` endpoints, which take `:org-id` from the body | `ptv/handler.clj:12` |
| M8 | **fixed** | Same any-city weakness on four more PTV endpoints, three of them writes to the real PTV API — `fetch-ptv-service-channel`, `save-ptv-service`, `save-ptv-service-location`, `save-ptv-meta`. Found while fixing M6; not in the original analysis | `ptv/handler.clj` |
| M7 | **fixed** | No token revocation. Roles baked into the 6h JWT; magic-link tokens live 7 days; `reset-password` accepts any valid token (including an impersonation token) with no old-password check | `jwt.clj:20`, `handler.clj:910` |

## Low — deferred

| # | Status | Finding | Location |
|---|---|---|---|
| L1 | pending | `clojure.core/read-string` in `utils/->number` honours `*read-eval*`, so `#=(...)` evaluates. Every caller traced (`maintenance.clj` CLI, `analysis/*` ES data, `db/city.clj` JSONB keys) — **not reachable from HTTP today**. A landmine, not a hole; one-line fix to `clojure.edn/read-string` | `utils.cljc:85` |
| L2 | pending | 500 responses echo `(.getMessage e)` | `handler.clj:54` |
| L3 | pending | `:parameters {:lipas-id int?}` is not a valid reitit parameter kind, so no coercion runs on the two accessibility routes | `handler.clj:1149`, `:1160` |
| L4 | pending | `/api/swagger.json` and `/api/swagger-ui` are public and enumerate the whole internal admin API | `handler.clj:170`, `:1479` |
| L5 | pending | Bulk-ops authz denial returns 500, not 403 | `bulk_operations_test.clj:138` |
| L6 | pending | `docker-compose.yml` also publishes `8091:8091` on all host interfaces, bypassing nginx. Prod nginx reaches the backend over the compose network (`proxy.conf` → `http://backend`), so the host publish is unnecessary there; local dev uses `host.docker.internal:8091` and does need it | `docker-compose.yml:188`, `:203` |

---

## Verified sound (no action)

Recorded so future review effort goes where it is needed:

- **Site-save authorization.** `upsert-sports-site!` checks permissions against
  both the stored revision and the submitted document, carries
  `:owner-org-id` / `:edit-grants` forward server-side so they cannot be
  injected to grant access, then separately authorizes any change to them.
- **Org management authz** — org-scoped role contexts from the body,
  re-authorization in the core layer, per-site re-checks in bulk ops,
  GDPR-aware email gating on history endpoints. Well tested.
- **No SQL injection.** Everything is hugsql / next.jdbc parameterized; the few
  string-built statements are in migrations and CLI tooling.
- **No XSS surface.** No `dangerouslySetInnerHTML` in the CLJS tree;
  `react-markdown` without `rehype-raw`; `sanitize-answer-links` allowlists
  `https?://` and `mailto:` and degrades everything else to plain text.
- **No CSRF exposure.** Bearer-token-only auth, no cookies, so
  `Access-Control-Allow-Origin: *` is not a vector.
- **JWT algorithm pinned** to HS512 in both signing and verification.
- **Impersonation** refuses self and privileged targets, 1h tokens,
  `:impersonator` claim preserved across refresh so sessions cannot be
  laundered, audit events on both users, tested.
- **No secrets in git history.** `.env*` is ignored; the only `AUTH_KEY=` hits
  in history are `***FILL_THIS***` in the sample file.

---

## Medium — detail on what was fixed

### M1 — Commented-out privilege checks

**Status:** fixed

Three routes had their privilege check commented out from an experimental
phase, so the *only* enforcement was in the frontend.

The reassuring part: in both cases the FE was already doing the right thing, so
this was a server-side omission rather than a genuinely open feature.

- `create-heatmap` / `get-heatmap-facets` — `#_#_:require-privilege
  :analysis-tool/experimental`. The FE already sent the `Authorization` header
  (`analysis/heatmap/events.cljs:91`, `:123`) and already hid the tab behind the
  same privilege (`analysis/subs.cljs:17`). Uncommenting needed **no FE change
  at all**.
- `search-lois` — a `;`-commented `:loi/view` block with the note "Tests don't
  use auth for this endpoint now". The FE already refuses to issue the request
  without `:loi/view` (`loi/events.cljs::search`), but sent no token
  (`#_#_:headers`). Restored the server gate and uncommented the header. The
  client-side check stays — it saves a round-trip for users who would only get
  a 403.

On the maintainer's question of whether a "registered user" privilege exists:
**no, and one shouldn't be added.** `:default` is folded into `check-privilege`
for *every* caller including anonymous ones, so it cannot express
"authenticated". The codebase's existing idiom for that is an explicit
`:middleware [mw/token-auth mw/auth]` with `:require-privilege nil`
(`get-current-user-orgs`, `get-site-editors`, the reminder endpoints), and
`route-auth-test` already recognises it as gated. Neither of these three
endpoints needed it — both had a real privilege to name.

Tests: `search-lois-requires-loi-view-test` and
`heatmap-requires-experimental-privilege-test`, each asserting 401 / 403 / and a
privileged caller clearing both coercion and the gate. The heatmap test also
pins that `:analysis-tool/use` (which every regular editor has via `basic`) is
**not** enough — only `:analysis-tool/experimental` is.

`route-auth-test`'s stale-entry check did its job here: removing the three
allowlist entries was forced by the test, not remembered.

Public surface after this: **62 public route/method pairs, 84 gated** (was
65/81).

### M3 — Unrestricted upload to the UTP CMS

**Status:** fixed

Worse than first written up. Measured against the running system with the CMS
call stubbed: a token carrying **zero roles** returned 200 and the UTP CMS
upload **was reached**. Not "could in principle" — it worked.

```
before                                     after
anon, multipart          401               401   cms=0
no-roles token           200  cms reached  403   cms=0
city-manager token       200  cms reached  403   cms=0
activities-manager       200  cms=1        200   cms=1
text/html content-type   200  cms=1        400   cms=0
SVG bytes as image/png   200  cms=1        400   cms=0
```

**Fixed, in three layers:**

1. **Privilege** — `utp-image-upload-access?`: `:activity/edit` OR
   `:loi/create-edit`, because the one endpoint serves two editors (the UTP
   activity editor at `activities/views.cljs:628` and the LOI editor at
   `loi/views.cljs:69`). Both privileges live on the same two roles today
   (`activities-manager`, `admin`), so the OR is about intent rather than
   reach — whichever editor a future role is granted, its upload keeps working.
   Excluded as intended: every `basic` role holds `:activity/`**`view`**, never
   `:activity/edit`.
2. **Declared type and size** — in the route schema, so coercion produces the
   400 for free. `:content-type` enum of png/jpeg/jpg/webp (mirroring both file
   pickers' `accept`), `:size` capped at 20 MB (nginx already caps bodies at
   50m; phone photos run 5-10 MB; the point is bounding abuse, not optimising
   storage).
3. **Magic bytes** — a content-type header is trivially spoofed, so
   `core/upload-utp-image!` verifies the leading bytes are actually PNG, JPEG or
   WebP before anything reaches the CMS, throwing `:invalid-image` → 400. Put in
   the single funnel so any future caller inherits it. Signature bytes are
   compared with `unchecked-byte` (0x89 is negative as a Java byte) and read
   from a fresh stream so the file the upload streams stays intact.

A pleasant side effect: **SVG uploads are now rejected**, including SVG content
declared as `image/png`. SVG can carry script, so a CMS that serves it back is
a stored-XSS vector; the sniff closes that without anyone having to think about
it.

**Premise correction.** I had assumed the bare-POST-400 came from the global
`coerce-request-middleware` running before route middleware, and told the
implementer to leave the ordering alone. The real mechanism is that reitit's
default coercion has no `:multipart` source at all — `reitit.ring.middleware.
multipart` does its *own* coercion inside its `:wrap`, and it was listed first
in the route's `:middleware` vector, ahead of `mw/token-auth`. Because
`:require-privilege` mounts its gate in the global chain (outside that vector),
adding it moved authorization *ahead* of multipart parsing as a side effect. So
bare POSTs now answer 401/403 instead of 400, and no tempfile is written for an
unauthorized caller. Strictly better, and the "latent trap" is gone rather than
merely documented.

**Known limits, accepted:**

- Sniffing is a signature check, not validation: a file with a PNG header and
  arbitrary tail still passes. Bounding that means decoding via ImageIO — a
  heavier decision, deliberately not taken.
- Only PNG/JPEG/WebP. Anyone who had been uploading GIF/AVIF despite the
  picker's `accept` now gets a 400. Historical CMS uploads were not audited
  (no read path from here).
- A file whose extension the browser doesn't recognise gets
  `Content-Type: application/octet-stream` from `File.type` and is now a 400.
  The picker's `accept` filter makes it hard to reach.

### M6 — PTV read access was not org-scoped

**Status:** fixed

`ptv-read-access?` asked only "does this caller hold `:ptv/manage` for *any*
city they happen to have?" — it never looked at what the request asked for. The
`fetch-ptv-*` endpoints take an `:org-id` from the body, so a `ptv-manager`
scoped to one municipality could read every other organisation's PTV data.

**The data model** (established against dev data, not assumed): a LIPAS org's
document carries `[:ptv-data :org-id]` (the PTV organisation UUID) and
`[:ptv-data :city-codes]`. That city-code list is the **only** concrete
org→municipality bridge — and it's the same field `lipas.data.ptv/
resolve-ptv-org-id` already uses to match a site to an org, so the fix reuses
the established bridge rather than inventing one.

Org **membership is not usable** for this: `:ptv-manager` is a hand-assignable
city-scoped role with no org context, and in dev the real PTV managers are not
members of the orgs whose PTV data they maintain.

**The rule:** `:ptv/audit` keeps global read (auditors review every org — that
was the intent of the original comment). Everyone else must hold `:ptv/manage`
for a municipality belonging to the org the request names. An `:org-id` that
resolves to no LIPAS org is denied, with no permissive fallthrough. Denials log
the reason at info level with the account id (not the email), so a mis-scoped
role is diagnosable instead of just looking like a broken feature.

**My premise was wrong about the endpoint shapes,** and following it blindly
would have produced a deny-all disguised as a fix. Three request shapes exist,
so there are three gates:

| Shape | Endpoints | Gate |
|---|---|---|
| PTV org UUID | `fetch-ptv-org`, `fetch-ptv-services`, `fetch-ptv-service-channels`, `fetch-ptv-service-collections` | `ptv-org-read-access?` → `get-org-by-ptv-org-id` |
| **LIPAS** org uuid | `fetch-ptv-service-audits` | `lipas-org-read-access?` → `get-org` |
| a sports-site, no org at all | `check-ptv-service-channel-link` | `site-ptv-read-access?` → the site's own city-code |

Feeding the LIPAS org uuid to `get-org-by-ptv-org-id` would have resolved to
nothing and denied every non-auditor.

**Type normalisation was a real trap.** Role contexts arrive as `Long` (JWT →
JSON), org `[:ptv-data :city-codes]` as `Integer` (jsonb). Those compare fine,
but a *string* city-code never matches a number in a set lookup and would have
turned the gate into a silent deny-all. `->city-code` normalises everything to
`long`, and a test asserts an org with `["91"]` string city-codes still grants
access. Note it deliberately avoids `utils/->int`, whose `->number` goes through
`clojure.core/read-string` — see L1; reader eval does not belong on an authz
path.

`get-ptv-integration-candidates` was deliberately left at its existing strength:
it is a fixed filter over the same search index `/actions/search` serves
anonymously, so there is nothing cross-tenant to protect, and the FE sends the
whole org's `city-codes` vector — which for a multi-municipality PTV org can be
wider than any single manager's scope, so scoping it could 403 a legitimate
wizard.

Tests: `test/clj/lipas/backend/ptv/read_access_test.clj` — own-city manager can
read (positive control, asserting **200** so a schema drift can't make the
negatives vacuous), other-city manager denied on every endpoint, auditor reads
any org, unmapped `:org-id` denied, anonymous 401, and the city-code type test.
A PTV-API tripwire ensures no denial path reaches the real PTV API.

### M2a — No rate limiting anywhere

**Status:** fixed

Nothing in LIPAS was rate limited: nginx has no `limit_req`, and the AI
assistant carried its own private limiter. The unauthenticated mail-sending
endpoints were usable as a mail-bomb or an ops-inbox flood, and free work for
anyone who wanted it.

**A blocker had to be fixed first, and it would have been an outage.** None of
the three nginx `/api` blocks set any forwarded header, so the backend sees the
nginx container's IP as `:remote-addr` for *every* proxied request. An IP-keyed
limiter would have collapsed into a single global bucket and throttled all users
at once. `proxy.conf`, `proxy_dev.conf` and `proxy_local.conf` now set
`X-Real-IP` and `X-Forwarded-For`.

The limiter keys on **`X-Real-IP`, never `X-Forwarded-For`**: nginx *overwrites*
the former with the real peer, but `$proxy_add_x_forwarded_for` *appends* to
whatever the client sent, so trusting it would let any caller prepend junk and
rotate buckets at will. `spoofed-forwarded-for-cannot-rotate-buckets-test` pins
exactly that.

`lipas.backend.rate-limit` is declarative per route:

```clojure
:rate-limit {:key :ip :window-ms rate-limit/hour-ms :max 10}
```

`:key :ip` for unauthenticated endpoints, `:key :user` for authenticated ones so
that colleagues behind one municipal NAT don't consume each other's budget. It
is mounted *inside* the privilege check, so a request about to be rejected with
401/403 spends nobody's budget.

Budgets: password reset and magic link 10/h/IP, feedback 10/h/IP, register and
newsletter 5/h/IP. Deliberately not tight — LIPAS users are municipal staff and
a whole municipality often shares one public address, so a low cap would lock
real colleagues out of password reset. 10/h still turns "unlimited" into a
nuisance.

Also fixes the assistant limiter's flaw: buckets are now evicted, so a stream of
one-shot addresses can't grow the map without bound.

**Stated limitations** (in the namespace docstring, because they bound how far
to trust this): state is per JVM process — exact today since LIPAS runs a single
backend container, but it would multiply by instance count if ever scaled out,
and it resets on deploy. Moving to Postgres/Redis is the fix and is deliberately
not done, since it would trade a working control for a much bigger change. And
IP limiting does not stop an attacker with many source addresses; this raises
the cost of casual abuse and bounds accidental loops.

**Not done, deliberately:** per-recipient limiting (capping how much mail one
*victim* address can receive regardless of source) would be the real defence
against targeted mail-bombing. It is a refinement for a threat model a municipal
facility registry is unlikely to face, and keying on an attacker-supplied email
adds a spoofable-key surface, so it is noted rather than built.

Tests: `rate-limit-test` (11 tests — budget, per-key isolation, window expiry,
`Retry-After`, spoofing resistance, eviction) and `rate-limit-http-test`
(5 tests — a route-data walk asserting every endpoint that needs a budget
declares a valid one, plus behavioural bursts proving the wiring rejects, that
budgets are per-IP rather than global, and that `/actions/search` — which the
map fires on every pan — is *not* limited).

### M8 — The same any-city weakness on the PTV write endpoints (new finding)

**Status:** fixed

Found while fixing M6, and **not in the original analysis** — I had only flagged
the `ptv-read-access?` helper. But `[{:city-code ::roles/any} :ptv/manage]`
carries exactly the same weakness ("holds `:ptv/manage` for *any* city"), and it
guarded four more endpoints that take `:org-id` from the body — three of them
**writes that push to the real PTV API**:

| Endpoint | Was | Now |
|---|---|---|
| `fetch-ptv-service-channel` (read) | `[{:city-code ::roles/any} :ptv/manage]` | `ptv-org-read-access?` |
| `save-ptv-service` | same | `ptv-org-write-access?` |
| `save-ptv-service-location` | same | `ptv-org-write-access?` |
| `save-ptv-meta` | same | `ptv-meta-write-access?` |

So a `ptv-manager` scoped to Utajärvi could publish into Raahe's entry in the
national service register. Worse than the read this branch already fixed.

**A privilege escalation was waiting in the obvious implementation.** Reusing
`ptv-org-read-access?` for the writes would have been wrong: that gate
short-circuits on `:ptv/audit`, and `:ptv-auditor` carries *only* `:ptv/audit`
and cannot write anything today (verified). Reusing it would have handed every
auditor write access to every organisation — inside a commit labelled as a
security fix. `ptv-org-write-access?` therefore has no auditor branch, and
`ptv-audit-does-not-grant-write-test` pins that.

**`save-ptv-meta` needed its own gate.** Its body is `{lipas-id -> ptv-meta}`
with one `:org-id` per *entry*, not one per request, so the gate requires **all**
of them. With `some`, a caller could smuggle a foreign organisation through
alongside a legitimate one — `save-ptv-meta-rejects-a-mixed-batch-test` covers
exactly that.

LIPAS admins are admitted explicitly on the write path. They already hold
unrestricted `:ptv/manage`, so the branch only covers the degenerate case of an
org with no PTV municipalities configured, where nobody could otherwise fix it.

**Deliberately left alone:** the five LLM *generation* endpoints
(`generate-ptv-descriptions*`, `translate-to-other-langs`,
`generate-ptv-service-descriptions`) keep `[{:city-code ::roles/any}
:ptv/manage]`. They take no `:org-id`, write nothing, and their source data is
public open data — so there is no cross-tenant impact. Their real exposure is
LLM cost, which is M4's job.

**A trap worth recording for future tests here:** `save-ptv-meta` writes a
sports-site revision, so a bare `ptv-manager` is refused by the *site-save*
authorization in `core`, not by the PTV gate. My first positive control failed
for that reason and looked like a broken gate. The test now uses a caller
holding `ptv-manager` **and** `city-manager` for the same city — privileged
enough to reach the handler, but not an admin, since admins short-circuit the
gate and would make both halves of the test vacuous.

### M2b — Account-existence oracle

**Status:** fixed

`request-password-reset` answered 404 `:email-not-found` for an unknown address
and 200 for a known one; `order-magic-link` did the same via
`:user-not-found`. Either gave an unauthenticated caller a clean
account-existence check.

Both now answer **200 for every address**, sending mail only when the account
exists. The cost of this leak was genuinely small — LIPAS addresses are
municipal work addresses, frequently published on the municipality's own site —
so this is hygiene rather than a serious hole. It is closed because it is free
to close and removes a whole category of question.

The UX tradeoff is handled in the copy rather than left to mislead: all three
locales now say *"if this address has an account, we have sent it a reset
link"* instead of claiming a mail was sent. Someone who mistypes their address
is told the truth.

Two frontend branches could no longer fire and were removed rather than left as
dead code — the "register instead" affordances in
`forgot_password/views.cljs` and `login/views.cljs` that were shown *only* on
the enumeration response. Comments at both sites explain why, so nobody
reinstates the 404 to make the buttons reappear. Password login still returns a
bare "Not authorized" without distinguishing a bad password from an unknown
user, which is the behaviour we want.

The test that codified the oracle is inverted:
`request-password-reset-email-not-found-test` (asserting 404) became
`request-password-reset-does-not-leak-account-existence-test`, which asserts a
known and an unknown address are indistinguishable **by both status and body**,
plus the same for `order-magic-link`.

### M4 — LLM endpoints unbounded in rate and input size

**Status:** fixed

Five PTV routes call a paid provider and were gated only by a privilege, with no
rate limit at all. `translate-to-other-langs` was the worst: `:summary`,
`:description` and `:user-instruction` were bare `:string` with no `:max`, so a
caller could push arbitrary volume straight into a prompt.

**Budgets** (all `:key :user`, via the M2a limiter, so colleagues behind one
municipal NAT don't consume each other's allowance):

| Route | Budget | Basis |
|---|---|---|
| `generate-ptv-descriptions` | 600/h | dispatched only from per-site buttons, never a loop |
| `-from-data` | 600/h | same, from the editor's PTV tab |
| `-batch` | **3000/h** | the largest municipality has 2369 PTV-eligible sites → 237 sequential requests at `batch-size` 10. The FE sets `:halt?` on failure, so a mid-run 429 kills the whole pass |
| `translate-to-other-langs` | 600/h | one explicit button press per call |
| `generate-ptv-service-descriptions` | 600/h | "generate all" loops over 28 sub-categories |
| `assistant-chat` | 300/h | raised from the private limiter's 30/h |
| `assistant-escalate` | 5/day | **deliberately not raised** — it mails lipasinfo through the job queue rather than calling a model, so it bounds an ops-inbox flood, not a bill |

**These were multiplied by 10 after the first pass, on the maintainer's call.**
The original numbers were sized to what normal use produces; the revised ones
are sized to be unreachable by a human, so the budgets act purely as a
runaway/abuse backstop rather than rationing legitimate work. The reasoning:
a limit a real user can hit mid-task costs more in support than it saves in
tokens. Two consequences worth stating plainly:

- At 3000/h the batch ceiling is **non-binding** for the sequential queue — it
  is a runaway backstop, not a cost cap.
- `assistant-chat` at 300/h is 300 *conversations*, and one conversation can
  fan out to `assistant/max-tool-iterations` (8) rounds of model calls, so the
  provider-call ceiling is a multiple of the number in the table.

**Input bounds** reference the documented PTV limits in `lipas.data.ptv`
(`max-summary-length` 150, `max-description-length` 5000) as vars rather than
literals, so they can't drift. Two judgement calls worth recording:

- `translate-to-other-langs`'s `:summary` is bounded at 5000, **not** 150.
  Generation can overshoot and the FE clamps only the *result*, so an over-long
  draft summary is a normal editing state; rejecting it would break the
  translate step itself with a generic error. PTV rejects it at sync time, which
  is where that belongs.
- `:from` / `:to` get the tightest bounds (10 chars, max 5) because they are the
  only fields interpolated *raw* into the prompt.

**The assistant's private limiter is gone**, replaced by the shared one:
`rate-state`, `rate-limited?` and both `if` branches in `chat!`/`escalate!` are
deleted. Gained: rejection happens before the handler and its tool loop runs,
plus `Retry-After` and bucket eviction.

**A regression this introduced, and its fix.** The shared limiter answers with
one generic English message, and the assistant frontend *preferred* the server's
string over its own Finnish copy — so Finnish users would have seen English in a
chat bubble. Fixed in `assistant/events.cljs` by dropping the server-string
preference: the backend owns the status code, the UI owns the wording. That was
arguably a pre-existing wart; the limiter just exposed it.

**The one number to revisit from production:** the 300/h batch budget assumes a
batch call takes ≥12s, which makes the limit unreachable by the sequential
queue. Real call latency was not measured. If calls are faster, a very large
org's run could hit the limit and the FE would halt the queue.

Tests: `llm-budget-test` — a route-data walk over all seven routes, a burst
proving `budget+1` is 429 and that **exactly `budget` provider calls were
attempted** (the 429 bought nothing), per-user isolation, over-length rejections
with zero provider calls, and a control asserting a payload at PTV's own limits
plus the FE's real 10-id batch shape is *not* rejected.

### M7 — No token revocation

**Status:** fixed

Roles are baked into a 6h JWT, so stripping them or archiving an account took up
to 6h to take effect (7 days for a magic-link token). H2 closed the *minting*
side; this closes the *using* side.

**KISS as chosen:** one nullable column, `account.tokens_valid_from`, and one
comparison — reject a token whose `:iat` predates it. No session table, no token
stored anywhere. NULL means never revoked, which is the state every existing
account is in, so **deploying the migration logs nobody out**.

The check lives in `mw/auth`, the single choke point every gated route passes
through (`privilege-middleware` calls it internally; manual routes list it), so
one check covers everything and routes added later are covered by construction.
`mw/wrap-db` is mounted first in the global chain to give it database access.

Revocation fires on `update-user-status!` (archiving), `update-user-permissions!`
(roles changed), `reset-password!` and `gdpr-remove-user!`.

**Two lockout bugs were in my design brief and got caught during
implementation** — worth recording, because both would have hit real users:

1. **`mw/auth` also guards `/actions/login`**, where the identity comes from
   basic auth and has no `:iat`. My stated rule ("no `:iat` + `tokens_valid_from`
   set ⇒ reject") would have **permanently locked a user out of password login**
   the moment an admin touched their roles. `token-auth` now marks the request so
   the revocation branch only applies to token identities;
   `revocation-does-not-block-password-login-test` pins it, and I verified it
   live.
2. **Sub-second precision.** `:iat` is seconds-granular by JWT convention, so if
   the revocation point kept sub-second precision, a token minted microseconds
   *after* a revocation would have `:iat` < `tokens_valid_from` and be rejected
   on arrival — which is exactly the magic link in the permissions-updated email
   and the token `refresh-login` hands back. `revoke!` truncates to whole
   seconds so the same-second case *allows*. The cost is that a token minted in
   the same second just before a revocation survives; against a 6h lifetime that
   is noise, and the trade errs toward not locking anyone out.

That second trade has a consequence for tests, and it produced a genuinely bad
test: the original "the reset link is spent afterwards" assertion used a
freshly-minted token, so it depended on crossing a second boundary and **failed
about 4 runs in 5**. Worse, it asserted a guarantee the design does not make.
Replaced with `password-reset-link-cannot-be-replayed-test`, which backdates the
link by 60s — the realistic case, since a reset link travels through email — and
now passes deterministically (verified over six consecutive runs). Replay
protection holds for any real elapsed time; it is not unconditional.

**Fails closed** — a failed lookup, or a missing `:lipas/db`, answers 401. An
auth outage is loud, bounded and recoverable; a token that silently keeps working
after an account was archived is none of those. A 5s cache keeps this from being
a DB round-trip per request, and `revoke!` drops the entry itself so in-process
revocation is immediate.

**Reset-flow ordering:** `revoke!` runs *after* the password write, so the
request holding the reset token completes. In `update-user-permissions!` it runs
*before* the email, so the magic link that mail carries is minted on the valid
side — there is a test for that link still working.

**TTL split** (as decided): `create-magic-link` takes an optional
`:valid-seconds`; only the password-reset caller passes 24h. Magic login,
permissions-updated and org invitations keep 7 days, untouched by omission, so
onboarding is unaffected.

**Verified live** against the running system, with state restored afterwards:

```
tokens_valid_from initially NULL        true
existing token, not revoked            200
password login, not revoked            200
--- revoke! ---
same token after revoke                401
PASSWORD LOGIN after revoke            200   <- must not lock out
freshly minted token after revoke      200
```

Mutation-tested both directions: forcing `revoked?` to `false` fails the five
revocation tests; forcing it to `true` fails *every* test, so the positive
controls are load-bearing rather than decorative.

**Residual, deliberately not done:**

- **Org membership changes still leak stale roles for up to 6h.**
  `org/set-member-roles!`, `add-member!` and `remove-org-member` change roles
  that `enrich-org-roles` bakes into the token, but do not revoke. Outside the
  agreed minimum; worth a follow-up.
- `ensure-permission!` (which grants a site creator `:site-manager`)
  intentionally does not revoke — it would log a user out immediately after
  creating a site.
- **Multi-node caveat:** `revoke!` clears only the local cache, so a second
  backend node could accept a revoked token for up to 5s. Single-container
  deployments are unaffected.
- **Fail-closed has a wide blast radius by design:** if the global chain is
  reordered so `wrap-db` no longer runs first, every authenticated request 401s.
  It fails loudly and the suite goes red, but it is worth knowing.
- The down migration was not executed.

---

## Found while testing the branch end-to-end (2026-07-31)

A pass over the real user-facing flows against the running system — password
reset and magic link through MailHog, login/refresh/revocation, map search,
reports and statistics, editor saves, PTV, the assistant. Everything held; the
two items below are what it turned up.

### M7-frontend — a revoked token failed silently for up to 15 minutes

**Status:** fixed

M7 closed the *server* side: a token minted before `tokens_valid_from` is
rejected. Nothing closed the client side. Only `::refresh-login` recognised the
resulting 401, and it runs on a 15-minute timer — until it fired, every other
request answered with its own generic "epäonnistui" message and the user went
on clicking a session that was already dead.

**Fixed** with a re-frame **global interceptor** (`login/events.cljs`), not by
wrapping the `:http-xhrio` effect: that key belongs to
`day8.re-frame.http-fx`, so re-registering it depends on load order and would
fail silently if anything claimed it later. The interceptor also sees the event
id, which is how the two events that own their own 401 opt out.

The failure map carries neither URI nor Authorization header, so the gate is
`:logged-in?` rather than the request. That is the better test anyway: with no
session there is nothing to expire, so the login form's own 401 is excluded
without inspecting anything.

The decision lives in `lipas.ui.login.session-expiry` (`.cljc`, following
`lipas.ui.ptv.diff`) so it is testable on the JVM — 22 assertions covering
401 vs 403, opted-out events, logged-out callers, a domain map that merely
carries `:status 401`, and the response sitting anywhere in the event vector.

**Two bugs in the first implementation, both caught by driving it in a browser
rather than by the tests:**

1. **The message never survived.** `::logout` resets db to `default-db`, wiping
   the `:active-notification` that had just been set. The message is now
   *resolved* before the logout (while the user's own locale is still in db —
   the reset also drops the translator back to `:fi`) and *dispatched* after it.
2. **`::session-expired` fired once per failed request.** A page has several
   requests in flight, and every copy is queued *before* the first `::logout`
   runs, so `:logged-in?` cannot deduplicate them. While impersonating that
   meant two `::exit-impersonation` runs: the first restores the admin session
   and consumes the stash, the second finds it empty and **logs the admin out
   entirely**. A `:session-expiring?` flag, set as the handler's own `:db`
   effect, is visible to the copies and stands them down.

**Also folded in:** `::login-refresh-failure` now delegates to
`::session-expired` instead of duplicating the logout/impersonation branch. The
periodic refresh is the path that discovers a dead session most often, and it
used to bounce the user to the login page without saying anything.

**Verified in the browser** against the running system: an ordinary 401 and a
refresh 401 both log out with the message; 403 and a wrong password on the real
login form do not; a genuinely revoked token hitting `/admin` logs out once;
impersonation plus two simultaneous 401s returns to the admin's own session
with the stash consumed once; normal browsing is unaffected.

**Known cosmetic detail:** the failing page's own handler still flashes its
generic message for a few hundred ms before the session message replaces it.
Suppressing that would mean the interceptor dropping other handlers' effects —
a much bigger hammer for a sub-second flash.

### `search-lois` answered 500 when the request omitted `:location`

**Status:** fixed

Not a security finding — a plain bug, surfaced because M1 put the endpoint
behind `:loi/view` and it got exercised properly for the first time.

`:location` is optional in `search-lois-payload`, but `core/->lois-es-query`
computed `offset`/`scale` eagerly in its `let`, so a request without one died on
`(* nil ...)`. The function's own `default-query` fallback had therefore never
been reachable. The arithmetic moved inside the branch, and the status filter
became conditional too — `:loi-statuses` is optional as well, and
`{:terms {:status.keyword nil}}` would have been the next failure.

Test: `search-lois-without-location-test` — 200 with the status filter still
applied (a retired LOI stays excluded, so it cannot pass vacuously), 200 on an
empty body, and 400 for a *partial* location, pinning that coercion is what
guarantees `:distance` is never nil once `:location` is present.

### Deploy note, and the `X-Real-IP` check — **verified on lipas-dev**

`nginx/*.conf` gained `X-Real-IP` for the M2a limiter, and the proxy container
must actually be recreated on deploy: the local one had been running six days
and was still serving the old template, which would have keyed every request on
the nginx container's address and collapsed all users into one bucket. That
still applies to the prod deploy.

This could not be checked locally at all — Docker Desktop rewrites every source
address to the VM gateway, so `X-Real-IP` came out as `192.168.65.1` for every
client whether the config was right or wrong. Worse, a black-box test from a
single source cannot tell "header set correctly" from "header absent, everyone
sharing the container's address": both look like one server-controlled bucket
with the same budget.

**Checked on lipas-dev after deployment (2026-07-31)** over an SSH tunnel to the
backend's nREPL, read-only, by sending one `request-password-reset` for an
address with no account — which under M2b sends no mail — and reading the
limiter's buckets:

| Check | Result |
|---|---|
| bucket key after one request | `130.234.239.166` |
| the calling machine's egress IPv4, looked up independently | the same address |
| the dev server's own address | `130.234.6.92` — so the key is not the host echoing itself |
| what the failure mode would look like | a `172.x` / `10.x` Docker address |
| spoofed `X-Real-IP` + `X-Forwarded-For` | ignored; still one bucket, hits 1 → 2 |

So on a Linux host Docker's iptables DNAT preserves the client source, nginx
copies it into `X-Real-IP`, and the limiter keys per client. Because nginx
*overwrites* that header rather than appending to it, a caller cannot rotate
buckets by supplying it themselves — which is precisely why the limiter reads
`X-Real-IP` and never `X-Forwarded-For`.

Worth knowing for the budget sizing: both the probe and the maintainer's own
traffic left JYU address space, so colleagues behind a shared institutional
egress really do land in one bucket. That is the municipal-NAT case M2a sized
the budgets for — now observed rather than assumed, and a reason not to tighten
them.

### CI — 120 failures in two namespaces, and the fixture bug behind them

**Status:** fixed

Both CI runs on PR #218 failed identically (`750 tests, 120 failures, 3
errors`), so despite the shape of the numbers nothing here was flaky. Two of
this branch's changes had test fallout that the branch's own new tests did not
cover:

- **M1, the heatmap gate** — 72 failures + 2 errors in
  `analysis.heatmap-test`, all of them "expected 200, got 401". Restoring
  `:require-privilege :analysis-tool/experimental` on `create-heatmap` /
  `get-heatmap-facets` left that namespace calling both endpoints anonymously.
  The gate itself was pinned in
  `handler-test/heatmap-requires-experimental-privilege-test`; the namespace
  that exercises what the endpoints *return* was simply never updated. Every
  request there now authenticates as a holder of the privilege.

- **M7, token revocation** — 48 failures + 1 error in
  `jobs.dead-letter-handler-test`, which authenticated as a hand-written map
  with `:id 1`. The revocation check looks the caller's `:id` up in `account`,
  so `"1"` reaches Postgres as a uuid literal, the lookup throws, and the check
  does what it is designed to do: fails closed, 401. It was the only place in
  the tree minting tokens for users that are not real rows; those callers are
  persisted accounts now.

Fixing them uncovered a genuine order-dependent bug, and this is the part worth
remembering: **`clojure.test/use-fixtures` ASSOCs the namespace's fixture list
rather than appending to it**, so a second `(use-fixtures :each ...)` call
silently REPLACES the first. Three namespaces this branch adds —
`token-revocation-test`, `rate-limit-http-test`, `llm-budget-test` — registered
their own reset fixture as a separate call and thereby dropped
`full-system-fixture`'s `:each`, which is what prunes Postgres and
Elasticsearch between tests.

Nothing pointed at it, because each of those namespaces still passed its own
assertions. What it actually does is leave the namespace inheriting whatever
the previously-run one left behind — and `tests.edn` sets `:randomize? true`,
so that is a different namespace on every run. `heatmap-test/empty-results-test`
is what made it visible: given the same fixture mistake it failed in two runs
out of three locally, always with the four sites an earlier test had indexed.

All four namespaces now pass both fixtures to a single `use-fixtures` call, and
`test-utils/full-system-fixture`'s docstring states the rule so the next one
does too.
