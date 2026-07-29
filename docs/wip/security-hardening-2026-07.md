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

**Status:** pending

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

**Fix:** backfill 401 (no token) + 403 (wrong-privilege token) tests for every
endpoint above.

---

## Medium — deferred

| # | Finding | Location |
|---|---|---|
| M1 | Two privilege checks commented out, exposing the endpoints: `#_#_:require-privilege :analysis-tool/experimental` on `create-heatmap` / `get-heatmap-facets`; `search-lois` privilege commented out | `handler.clj:1421`, `:1437`, `:1312` |
| M2 | Unauthenticated email sending + account enumeration; no rate limiting anywhere (`nginx` has no `limit_req`). `request-password-reset` returns 404 `:email-not-found` vs 200 → existence oracle; `order-magic-link` mail-bombing; `send-feedback` / `register` → ops inbox; `subscribe-newsletter` | `handler.clj:900`, `:975`, `:1254`, `:857`, `:1227` |
| M3 | `upload-utp-image` is auth-only with no privilege, no content-type check, no size cap, no extension allowlist | `handler.clj:1276` |
| M4 | LLM abuse controls thin: PTV LLM endpoints have no rate limit and unbounded input (`translate-to-other-langs` takes bare `:string` with no `:max`); assistant limiter is an in-process atom that resets on deploy, multiplies per instance, and never evicts | `ptv/handler.clj:87`, `assistant.clj:741` |
| M5 | Stale crypto stack: `buddy/buddy 2.0.0` → buddy-core 1.4.0 / buddy-sign 2.2.0 / buddy-hashers 1.3.0 / `bcprov-jdk15on 1.58` (2017, EOL artifact line). JWT alg is correctly pinned to HS512, so no alg-confusion — this is dependency hygiene | `deps.edn` |
| M6 | `ptv-read-access?` is not org-scoped: `:ptv/manage` for any single city grants read of any org's PTV data via the `fetch-ptv-*` endpoints, which take `:org-id` from the body | `ptv/handler.clj:12` |
| M7 | No token revocation. Roles baked into the 6h JWT; magic-link tokens live 7 days; `reset-password` accepts any valid token (including an impersonation token) with no old-password check | `jwt.clj:20`, `handler.clj:910` |

## Low — deferred

| # | Finding | Location |
|---|---|---|
| L1 | `clojure.core/read-string` in `utils/->number` honours `*read-eval*`, so `#=(...)` evaluates. Every caller traced (`maintenance.clj` CLI, `analysis/*` ES data, `db/city.clj` JSONB keys) — **not reachable from HTTP today**. A landmine, not a hole; one-line fix to `clojure.edn/read-string` | `utils.cljc:85` |
| L2 | 500 responses echo `(.getMessage e)` | `handler.clj:54` |
| L3 | `:parameters {:lipas-id int?}` is not a valid reitit parameter kind, so no coercion runs on the two accessibility routes | `handler.clj:1149`, `:1160` |
| L4 | `/api/swagger.json` and `/api/swagger-ui` are public and enumerate the whole internal admin API | `handler.clj:170`, `:1479` |
| L5 | Bulk-ops authz denial returns 500, not 403 | `bulk_operations_test.clj:138` |
| L6 | `docker-compose.yml` also publishes `8091:8091` on all host interfaces, bypassing nginx. Prod nginx reaches the backend over the compose network (`proxy.conf` → `http://backend`), so the host publish is unnecessary there; local dev uses `host.docker.internal:8091` and does need it | `docker-compose.yml:188`, `:203` |

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
