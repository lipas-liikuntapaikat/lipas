(ns lipas.backend.route-auth-test
  "Whole-router invariant: every route makes an explicit authentication
   decision.

   `lipas.backend.middleware/privilege-middleware` is fail-OPEN by omission:

     (if-let [required-privilege (:require-privilege route-data)]
       ...gate...
       {})   ; no key ⇒ no middleware at all ⇒ fully public

   So a new route that simply forgets `:require-privilege` ships to production
   unauthenticated, and nothing anywhere says so. Per-endpoint 401/403 tests
   cannot catch that — you only write one for a route you already remembered to
   protect.

   This test closes the loop from the other side: it enumerates every
   route/method pair in the real router and requires each one to be either
   gated or listed in `public-routes` below. Adding a route is then a forced
   choice — protect it, or write it down here and say why.

   Note the router is built from an EMPTY ctx. Route data is pure; the handlers
   close over db/search/etc but building the router never touches them. So this
   invariant needs no database, no Elasticsearch and no fixtures, and stays
   cheap enough to never be the reason someone skips the suite."
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [lipas.backend.handler :as handler]
            [lipas.backend.middleware :as mw]
            [reitit.core :as r]
            [reitit.ring :as ring]))

(def ^:private http-methods
  #{:get :post :put :delete :patch :head :options})

(def public-routes
  "Route/method pairs that are deliberately reachable without authentication.

   Every entry is a decision, not an oversight. Entries carrying a TODO are
   known findings that are tracked and deliberately not fixed in this pass —
   see docs/wip/security-hardening-2026-07.md."
  #{;; --- Infrastructure -------------------------------------------------
    [:get "/favicon.ico"]
    [:get "/index.html"]
    [:get "/api/health"]
    ;; TODO(L4): enumerates the whole internal admin API to anonymous callers.
    [:get "/api/swagger.json"]

    ;; --- Public open data: sports sites & LOIs ---------------------------
    ;; LIPAS sports-site data is open data by mandate. These are the read
    ;; paths the public map, the portals and third-party integrators use.
    [:get "/api/sports-sites/:lipas-id"]
    [:get "/api/sports-sites/history/:lipas-id"]
    [:get "/api/sports-sites/type/:type-code"]
    [:get "/api/lois"]
    [:get "/api/lois/:loi-id"]
    [:get "/api/lois/type/:loi-type"]
    [:get "/api/lois/category/:loi-category"]
    [:get "/api/lois/status/:status"]
    [:post "/api/actions/autocomplete-sports-site"]
    [:post "/api/actions/check-sports-site-name"]
    [:post "/api/actions/find-fields"]
    [:post "/api/actions/get-front-page-stats"]
    [:post "/api/actions/get-help-data"]

    ;; --- Search & reporting over the open data ---------------------------
    ;; Bodies reaching Elasticsearch are validated by
    ;; lipas.backend.search-guard; see lipas.backend.search-guard-test.
    [:post "/api/actions/search"]
    [:post "/api/actions/search-schools"]
    [:post "/api/actions/search-population"]
    [:post "/api/actions/query-finance-report"]
    [:post "/api/actions/query-subsidies"]
    [:post "/api/actions/create-sports-sites-report"]
    [:post "/api/actions/create-data-model-report"]
    [:post "/api/actions/create-energy-report"]
    [:post "/api/actions/create-finance-report"]
    [:post "/api/actions/calculate-stats"]
    [:post "/api/actions/get-accessibility-statements"]

    ;; --- Analysis tools ---------------------------------------------------
    [:post "/api/actions/calc-distances-and-travel-times"]
    [:post "/api/actions/calc-diversity-indices"]
    [:post "/api/actions/create-analysis-report"]

    ;; --- Unauthenticated by necessity: you have no session yet -----------
    ;; All four send mail, so all four carry an IP-keyed `:rate-limit` instead
    ;; of an auth gate — see lipas.backend.rate-limit-http-test, which is what
    ;; requires them to declare one.
    [:post "/api/actions/register"]
    [:post "/api/actions/request-password-reset"]
    [:post "/api/actions/order-magic-link"]
    [:post "/api/actions/send-feedback"]

    ;; --- Newsletter -------------------------------------------------------
    [:post "/api/actions/get-newsletter"]
    [:post "/api/actions/subscribe-newsletter"]

    ;; --- Public API v1 (legacy) — read-only open data ---------------------
    ;; Also reachable through the nginx /rest/* and api.lipas.fi rewrites.
    [:get "/v1"]
    [:get "/v1/"]
    [:get "/v1/sports-places"]
    [:head "/v1/sports-places"]
    [:get "/v1/sports-places/:sports-place-id"]
    [:head "/v1/sports-places/:sports-place-id"]
    [:get "/v1/deleted-sports-places"]
    [:get "/v1/categories"]
    [:get "/v1/sports-place-types"]
    [:get "/v1/sports-place-types/:type-code"]
    [:get "/v1/swagger.json"]
    [:get "/v1/openapi.json"]
    [:get "/v1/swagger-ui"]
    [:get "/v1/swagger-ui/*"]

    ;; --- Public API v2 — read-only open data ------------------------------
    [:get "/v2"]
    [:get "/v2/"]
    [:get "/v2/sports-sites"]
    [:get "/v2/sports-sites/{lipas-id}"]
    [:get "/v2/sports-site-categories"]
    [:get "/v2/sports-site-categories/{type-code}"]
    [:get "/v2/lois"]
    [:get "/v2/lois/{loi-id}"]
    [:get "/v2/openapi.json"]
    [:get "/v2/swagger-ui"]
    [:get "/v2/swagger-ui/*"]})

(defn- gated?
  "True when this method's route data makes an authentication decision.

   Two shapes count, matching the two the codebase actually uses:

   - `:require-privilege` present — privilege-middleware mounts token-auth and
     auth beneath the privilege check. An explicit `nil` value counts too: it
     is how routes say \"authenticate, but authorize in the handler/core\"
     (e.g. POST /api/sports-sites, whose authz needs the STORED revision), and
     those always pair it with an explicit middleware vector.
   - a route-level `:middleware` vector containing `mw/auth` — the manual
     form, used where there is no single privilege to name."
  [method-data]
  (or (contains? method-data :require-privilege)
      (boolean (some #{mw/auth} (:middleware method-data)))))

(defn- all-routes
  "Every [method path method-data] in the real router.

   Built from an empty ctx — see the namespace docstring."
  []
  (let [router (ring/get-router (handler/create-app {}))]
    (for [[path data] (r/routes router)
          [method method-data] data
          :when (and (http-methods method) (map? method-data))]
      [method path method-data])))

(defn- fmt [pairs]
  (->> pairs
       sort
       (map (fn [[method path]] (str "    [" method " \"" path "\"]")))
       (str/join "\n")))

(deftest every-route-declares-an-auth-decision-test
  (let [ungated (->> (all-routes)
                     (remove (fn [[_ _ md]] (gated? md)))
                     (map (fn [[method path _]] [method path]))
                     set)
        undeclared (set/difference ungated public-routes)]
    (is (empty? undeclared)
        (str "These routes are reachable WITHOUT authentication and are not "
             "listed in `public-routes`.\n\n"
             "privilege-middleware is fail-open: a route with no "
             ":require-privilege and no auth middleware is public.\n"
             "If that is intended, add it to `public-routes` with a comment "
             "saying why. Otherwise add :require-privilege.\n\n"
             (fmt undeclared)
             "\n"))))

(deftest public-allowlist-has-no-stale-entries-test
  ;; Without this, a route that later gets protected would leave a dead entry
  ;; behind, and the allowlist would slowly stop meaning anything.
  (let [routes (all-routes)
        existing (set (map (fn [[method path _]] [method path]) routes))
        ungated (->> routes
                     (remove (fn [[_ _ md]] (gated? md)))
                     (map (fn [[method path _]] [method path]))
                     set)
        gone (set/difference public-routes existing)
        now-gated (set/difference (set/intersection public-routes existing)
                                  ungated)]
    (testing "no entry for a route that no longer exists"
      (is (empty? gone)
          (str "Remove these from `public-routes` — the routes are gone:\n\n"
               (fmt gone) "\n")))

    (testing "no entry for a route that is now authenticated"
      (is (empty? now-gated)
          (str "Remove these from `public-routes` — they are authenticated "
               "now, so listing them as public is misleading:\n\n"
               (fmt now-gated) "\n")))))

(deftest sensitive-routes-are-gated-test
  ;; A belt-and-braces spot check. The invariant above is the real guard, but
  ;; it is only as good as `public-routes` — someone could "fix" a failure by
  ;; pasting the offending route into the allowlist. These must never be
  ;; public, whatever the allowlist says.
  (let [gated-set (->> (all-routes)
                       (filter (fn [[_ _ md]] (gated? md)))
                       (map (fn [[method path _]] [method path]))
                       set)]
    (doseq [route [;; LLM — these cost money per call
                   [:post "/api/actions/assistant-chat"]
                   [:post "/api/actions/assistant-escalate"]
                   [:post "/api/actions/generate-ptv-descriptions"]
                   [:post "/api/actions/generate-ptv-descriptions-from-data"]
                   [:post "/api/actions/generate-ptv-descriptions-batch"]
                   [:post "/api/actions/generate-ptv-service-descriptions"]
                   [:post "/api/actions/translate-to-other-langs"]
                   [:post "/api/actions/run-ptv-workbench-experiment"]

                   ;; User & permission administration
                   [:get "/api/users"]
                   [:post "/api/actions/gdpr-remove-user"]
                   [:post "/api/actions/impersonate"]
                   [:post "/api/actions/update-user-permissions"]
                   [:post "/api/actions/update-user-status"]
                   [:post "/api/actions/send-magic-link"]

                   ;; Org administration
                   [:post "/api/actions/create-org"]
                   [:post "/api/actions/get-all-orgs"]
                   [:post "/api/actions/reclaim-org-sites"]
                   [:post "/api/actions/approve-org-takeover"]
                   [:post "/api/actions/deny-org-takeover"]
                   [:post "/api/actions/update-org-role-templates"]
                   [:post "/api/actions/set-org-member-roles"]
                   [:post "/api/actions/remove-org-member"]

                   ;; Writes to shared content
                   [:post "/api/sports-sites"]
                   [:post "/api/actions/save-loi"]
                   [:post "/api/actions/save-help-data"]
                   [:post "/api/actions/mass-update-org-sites"]

                   ;; Background job control
                   [:post "/api/actions/search-jobs"]
                   [:get "/api/actions/get-dead-letter-jobs"]
                   [:post "/api/actions/reprocess-dead-letter-jobs"]]]
      (is (contains? gated-set route)
          (str route " must require authentication")))))
