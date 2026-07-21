# PTV integration — onboarding & hardening prompt

Paste this as the opening message of a fresh agent session when working on
the LIPAS ↔ PTV integration. It forces orientation before action,
documents the gotchas that take an hour each to rediscover, and teaches
the data-driven validation pattern that produces real numbers rather
than hand-waved estimates.

---

You're being asked to work on the LIPAS ↔ PTV integration. Before changing
anything, build a complete mental model of what PTV is, how LIPAS maps
to it, and where the historical surface area lives. Then validate any
assumption against real data in the local dev DB before recommending fixes.

## 1. What PTV is and why LIPAS integrates with it

PTV = "Palvelutietovaranto", the Finnish national service catalog operated
by DVV (Digital and Population Data Services Agency). It's the canonical
public-facing registry for government services, service points, contact
details, addresses, and the channels that serve them. End-user UIs that
consume PTV include suomi.fi and municipal portals.

LIPAS is Finland's authoritative database of sports facilities. The PTV
integration publishes sports-sites as PTV `ServiceLocation` channels so
they appear in citizen-facing service directories. Sync is one-way
(LIPAS → PTV) with audit-back: PTV is a downstream consumer, not a peer
source of truth. LIPAS edits propagate to PTV when (a) a site is
PTV-eligible, (b) its `:ptv :sync-enabled` is true, and (c) the user has
the right role.

Read the integration entry points first to confirm this picture for
yourself, in this order:

  1. `webapp/src/cljc/lipas/data/ptv.cljc`              — payload shape, `->ptv-service-location`
  2. `webapp/src/clj/lipas/backend/ptv/core.clj`        — `sync-ptv!`, `upsert-ptv-service-location!*`
  3. `webapp/src/clj/lipas/backend/ptv/integration.clj` — HTTP layer, auth, error envelope
  4. `webapp/src/clj/lipas/backend/ptv/handler.clj`     — REST endpoints
  5. `webapp/src/cljs/lipas/ui/ptv/`                    — wizard, dialog, site-tab, audit
  6. `webapp/src/cljs/lipas/ui/ptv/site_view.cljs`      — per-site PTV tab
  7. `webapp/src/cljc/lipas/i18n/{fi,en,se}/ptv.edn`    — every user-visible string

Don't skim — read the comments. They document non-obvious PTV behavior
(API GET/PUT format mismatch, deleteAll* flag quirks, source-id semantics,
PTV canonical UUIDs vs. version IDs) that the code alone won't reveal.

## 2. Where the historical PTV source code lives

A 2020 DVV snapshot of the PTV server-side codebase is checked out at:

    /Users/tipo/lipas/ptv/lol/ptv-releases/

It's authoritative for validation behavior — when PTV rejects something
and the error message is vague, the validator source tells you the real
rule. The folders that matter for integration work:

  - `src/PTV.Application.OpenApi/DataValidators/`        — `PostalCodeValidator`,
                                                           `AddressValidator`,
                                                           `StreetAddressValidator`,
                                                           `MunicipalityCodeValidator`
  - `src/PTV.Framework/Attributes/ValidationAttributes/` — `PTVUrlAttribute`,
                                                           `PTVEmailAddressAttribute`,
                                                           regex-based validators
  - `src/PTV.Domain.Model/Models/OpenApi/`               — DTO models with
                                                           `[Required]` / `[MaxLength]` /
                                                           `[PTVUrl]` annotations
  - `src/PTV.TaskScheduler/jobsettings.json`             — refresh cadences
                                                           (`PostiPostalCodesJob` is
                                                           monthly, first Monday 03:00)
  - `src/PTV.TaskScheduler/Jobs/PostiPostalCodesJob.cs`  — source of the
                                                           `IsValid` postal-code flag

When investigating an error returned by PTV, grep the snapshot for the
error-message substring (e.g. `"was not found"`, `"must be a string with
a maximum length"`) to find the exact validator and its rule. That's
faster than guessing from the swagger docs.

## 3. Hunting integration caveats we haven't discovered yet

A PTV integration caveat lives at the boundary between LIPAS data shapes
and PTV's strict validators. To find new ones:

**a) Enumerate every field LIPAS sends.**
   - Open `->ptv-service-location` in `data/ptv.cljc`
   - Note each field name and value source
   - Cross-reference with PTV's model (e.g. `VmOpenApiServiceLocation*`
     in the PTV source) and note the `[Required]`, `[MaxLength]`, regex
     and custom validator attributes for each.

**b) Compare LIPAS-permitted vs PTV-permitted values.**
   For each LIPAS source field, find the LIPAS schema in
   `webapp/src/cljc/lipas/schema/sports_sites.cljc` and note its
   constraints. A gap (LIPAS allows X, PTV rejects X) is a caveat.
   Examples already found:
   - postal-code: LIPAS = any string ≤10, PTV = must be in Posti registry
   - www: LIPAS = string 1..500, PTV = scheme + valid FQDN host
   - phone-number: LIPAS = free-form, PTV = digits-only `^\d{1,20}$`
   - description/summary: LIPAS = `:closed false`, PTV = MaxLength-bounded

**c) Audit the format-normalization helpers.**
   The existing helpers in `data/ptv.cljc` — `parse-phone-number`,
   `parse-email`, `parse-www` — each codify a caveat we already
   discovered. Any LIPAS field that flows into the PTV payload *without*
   a parse-helper is a candidate for an undiscovered caveat.

**d) Inspect the in-the-wild failures.**
   Read the catch block in `sync-ptv!` (`backend/ptv/core.clj`). Anything
   that ends up on `:ptv :error` is a known failure mode. A query like

       SELECT lipas_id, document->'ptv'->'error'
       FROM sports_site_current
       WHERE document->'ptv'->'error' IS NOT NULL;

   shows you which sites are currently broken, with the validator name
   and message in the payload.

**e) Map known caveats to error-detection code.**
   Look at the existing error-handling in `ui/ptv/events.cljs`:
   `ptv-modified-error?`, `ptv-invalid-postal-code`. These are the
   caveats we've codified. Anything in the wild that doesn't match those
   predicates is a generic failure today and an opportunity to surface
   better.

## 4. Data-driven verification with the local dev DB

The local Postgres + Elasticsearch contain a fresh dump from prod. Use
that — don't guess from samples.

**REPL access**: nREPL on port 7888, invoke with `clj-nrepl-eval -p 7888`
(use a heredoc for multiline). Always require with `:reload` after
editing source. Common entry points:

    (user/db)                    — JDBC connection map
    (user/search)                — Elasticsearch client
    (user/ptv)                   — :lipas/ptv component (from running system)
    integrant.repl.state/system  — full system map (no @)

**Pattern for data-driven verification**:

  1. **Query the universe** of values, not a sample. There are ~41k sports
     sites; full-table queries return in seconds. Use:

         (require '[clojure.java.jdbc :as cj])
         (cj/query (user/db)
           ["SELECT lipas_id, document->>'www' AS www
             FROM sports_site_current
             WHERE document->>'www' IS NOT NULL"])

     `sports_site_current` is the latest revision per `lipas_id` (the
     `sports_site` table is append-only event log; you want the view).

  2. **Explore the shape of the data before writing a validator.** Most
     real-world failures come from value shapes you didn't anticipate.
     Before you can validate, you need to know what's actually out
     there. The recipe:

         (let [rows  (cj/query (user/db) [...])
               vals  (map :www rows)
               cat   (fn [v]
                       (cond
                         (re-find #"^https?://" v)         :ok-with-scheme
                         (re-find #"^ftp://" v)            :ftp
                         (re-find #"^www\." v)             :www-no-scheme
                         (re-find #"[\s,;]" v)             :multi-or-spaces
                         (str/blank? v)                    :blank
                         (re-find #"\.(fi|com|net|org)" v) :plausible-host
                         :else                             :other))
               buckets (group-by cat vals)]
           (-> buckets
               (update-vals count)
               (assoc :samples-of-other (take 15 (get buckets :other)))))

     The `:other` bucket is where the unknown unknowns live. Sample it,
     read those values, and let them tell you what categories are
     missing. Iterate: each pass refines the categorization and shrinks
     `:other` until it's either empty or genuinely unsalvageable.
     This is how scheme typos (`hhttps://`, `htpps://`, `hpps://`)
     surfaced — they weren't in the PTV validator source, only in the
     data.

  3. **Count per-site, not per-distinct-value.** Distinct-value counts
     dramatically overstate failure rates because common bad strings
     cluster on few sites and most sites use a small set of well-formed
     values. The right metric is "how many sites would fail / total
     sites with the field set". (A real example from this codebase: a
     distinct-value sample suggested ~3% of `:www` values were
     PTV-incompatible; per-site measurement showed 0.36%.)

  4. **Replicate the PTV validator in Clojure**, run it against every
     value, and compare old vs new code paths. Example for URL validation:

         (defn ptv-url-valid? [v]
           (and (string? v) (not (str/blank? v))
                (<= (count v) 500)
                (not (re-find #"\s" v))
                (boolean (re-find #"(?i)^(https?|ftp)://[^./\s]+\.[^/\s]" v))))

     This isn't a perfect replica but it's grounded in the actual PTV
     validator source (see section 2). Tune as you find discrepancies.

  5. **Report saved / failed / regressed counts**, plus a sample of
     remaining failures. The structure that's useful:

         {:total-sites N
          :old-fail-count M
          :new-fail-count K
          :sites-saved (count saved)
          :sites-regressed (count regressed)
          :sample-remaining-failures [...]}

     If `sites-regressed` is non-zero, stop and investigate before
     committing.

  6. **Iterate on the remaining-failures sample.** When the count is
     non-zero, the offending rows reveal the next pattern. Each
     iteration of (write fix → re-measure → inspect remaining failures)
     converges on a complete handler. Example from this codebase: the
     first parse-www pass left 13 failing sites, all schemeless
     multi-URL inputs the splitter didn't reach; adding a second split
     branch took them to 0. You wouldn't have seen that pattern
     without inspecting the leftovers.

  7. **Permissions caveat**: in dev, role data is stored as strings
     (`"admin"`) while `lipas.roles/check-privilege` keys the role map
     by keywords (`:admin`). Role normalization happens in HTTP
     middleware, so a direct REPL call to `check-privilege` may report
     "no permission" for users who are actually admins. If you need to
     test save-path code in the REPL, use `upsert-sports-site!*` (the
     trusted variant) or hit the HTTP endpoint with a real token, don't
     go through `core/upsert-sports-site!` directly.

  8. **Beware unserializable values on `:ptv :error`**: clj-http's
     response carries a live `HttpClient` Java handle in ex-data. If you
     ever extend the catch block in `sync-ptv!`, do not store raw ex-data
     — Cheshire can't serialize it to JSONB, the tx rolls back, and the
     whole save returns 500 even though only the downstream PTV call
     failed. (Also: ex-data carries the PTV bearer token in
     `:req :headers`. Don't persist it.)

## 5. Testing against the PTV test environment

PTV runs a separate test instance at `*.trn.suomi.fi`. The local dev env
is already wired up to it — `webapp/../.env.sh` exports
`PTV_API_URL=https://api.palvelutietovaranto.trn.suomi.fi/api`,
`PTV_ENV=test`, and a default API user/password. **You can do real PTV
round-trips from the REPL right now.**

**Surfaces**:

  - REST API base:  `https://api.palvelutietovaranto.trn.suomi.fi/api`
  - Auth (token):   `https://palvelutietovaranto.trn.suomi.fi/api/auth/api-login`
  - Browser UI:     `https://palvelutietovaranto.trn.suomi.fi`
                    (open a ServiceLocation channel in the browser
                    after a sync to inspect what PTV actually stored —
                    great for verifying GET-vs-PUT shape mismatches)
  - Tokens are valid for 24h in test (vs ~1h in prod).
  - `integration/test-env?` is the runtime predicate; it switches the
    token-response field name (`:ptvToken` vs `:serviceToken`) and the
    auth flow. Don't bypass it.

**Test organizations** that have real PTV-side counterparts (see
`data/ptv.cljc :: test-organizations`):

  | LIPAS org | PTV org-id                                | City code |
  |-----------|-------------------------------------------|-----------|
  | Utajärvi  | `3d1759a2-e47a-4947-9a31-cab1c1e2512b`    | 889       |
  | Raahe     | `92374b0f-7d3c-4017-858e-666ee3ca2761`    | 678       |
  | Liminka   | `7fdd7f84-e52a-4c17-a59a-d7c2a3095ed5`    | 425       |

These orgs map to specific test API credentials in
`integration/get-test-credentials` (org-id → username/password). The
creds are intentionally hardcoded and "public" (per the comment in
that fn) — don't worry about leaking them, they only work against the
test API. To sync a sports-site through the test environment, pick a
site whose `:location :city :city-code` matches one of the rows above.

**REPL recipes**:

  - **Look up a real PTV org** in either env without going through the
    full integration:

        (require '[user :refer [ptv-lookup-org]])
        (ptv-lookup-org "3d1759a2-e47a-4947-9a31-cab1c1e2512b" {:env :test})
        (ptv-lookup-org "Liminka" {:env :test})  ; search by name

  - **Get a fresh token** to poke at the API directly:

        (require '[lipas.backend.ptv.integration :as ptv])
        (ptv/get-token (user/ptv)
                       "7fdd7f84-e52a-4c17-a59a-d7c2a3095ed5")  ; Liminka

  - **Dry-run validation against PTV** without committing to LIPAS DB.
    The cheapest way is to build a payload from a real site and `PUT`
    it directly to the ServiceLocation endpoint, letting PTV's
    validators run. The error response (status 400 + ModelState map)
    tells you what would have failed:

        (require '[lipas.backend.core :as core])
        (require '[lipas.backend.ptv.integration :as ptv])
        (require '[lipas.backend.gis :as gis])
        (require '[lipas.data.ptv :as ptv-data])
        (let [site    (core/get-sports-site (user/db) <lipas-id>)
              payload (ptv-data/->ptv-service-location
                        "7fdd7f84-e52a-4c17-a59a-d7c2a3095ed5"   ; org-id
                        gis/wgs84->tm35fin-no-wrap
                        (str (java.time.Instant/now))
                        (core/enrich site))]
          (try
            (ptv/http (user/ptv)
                      "7fdd7f84-e52a-4c17-a59a-d7c2a3095ed5"
                      {:url (str (:api-url (user/ptv)) "/v11/ServiceChannel/ServiceLocation")
                       :method :post
                       :form-params payload})
            :ok
            (catch Exception e (ex-data e))))

    Use the test org-ids only. The HTTP layer caches the token after
    the first call.

  - **Switch envs without restarting the system**: edit `PTV_ENV` and
    `PTV_API_URL` in `.env.sh`, then `(user/reset)` in the REPL.
    Prefer keeping dev pointed at test — accidentally syncing test
    data to prod PTV is unrecoverable.

**Guardrails**:

  - **Test-only sites**: when developing, only mark sync-enabled on
    sites whose `:city-code` is in the test-organizations table above.
    Anything else will use the prod-org fallback config (see
    `data/ptv.cljc :: org-id->params`) and the test API will reject
    the request, but the LIPAS DB will still capture a confusing
    `:ptv :error`.
  - **Token expiry**: if you see 401s after a long break, the token
    cached on `(:tokens (user/ptv))` may be stale; `(swap!
    (:tokens (user/ptv)) {})` clears it and forces a fresh login.
  - **Cleanup**: things you `POST` to test PTV stay there until
    manually archived. Use the browser UI (Suomi.fi management UI) or
    a `PUT` with `:publishingStatus "Deleted"` to retire test artifacts
    you created.

## Output expectations

When you finish investigating, report:
  - The mental model in 4–6 sentences (what flows where).
  - The list of caveats you found, ranked by whether they're observed
    in the current DB.
  - For each observed caveat: per-site failure count + total, and a
    one-line fix sketch.
  - For each *un*observed caveat: why you think it could bite (which
    PTV validator + which LIPAS field shape), and a plan to either
    confirm it's safe or pre-empt it.
  - For any fix that touches the catch path in `sync-ptv!` or the PTV
    payload: a dry-run against the test environment proving the fix
    actually passes PTV's validators, not just yours.

Don't propose code changes until you've verified the issue exists with
data from the local DB. If a caveat doesn't show up in 41k sites, it's
not worth pre-emptive hardening unless the PTV validator is unusually
strict.
