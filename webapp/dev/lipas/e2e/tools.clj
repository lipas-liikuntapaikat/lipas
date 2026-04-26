(ns lipas.e2e.tools
  "REPL helpers for end-to-end scenarios. Lives under dev/ so it's only on
  the dev classpath — never production.

  See .claude/skills/lipas-e2e/ for scenario recipes.

  v0 status: helpers below cover the create/update scenarios. Several
  functions are best-effort — they delegate to lipas.backend.core where
  possible, fall back to direct SQL otherwise. Marked TODOs are unverified
  paths that should be tightened the first time they bite."
  (:require
    [lipas.backend.core :as core]
    [next.jdbc :as jdbc])
  (:import
    (java.time Instant)))

;; ---------------------------------------------------------------------------
;; System component access (mirrors user/ helpers)
;; ---------------------------------------------------------------------------

(defn db [] ((requiring-resolve 'user/db)))
(defn search [] ((requiring-resolve 'user/search)))
(defn ptv [] ((requiring-resolve 'user/ptv)))
(defn admin-user [] ((requiring-resolve 'user/get-robot-user)))

(declare revision-count jobs-for)

;; ---------------------------------------------------------------------------
;; Lookup
;; ---------------------------------------------------------------------------

(defn find-site
  "Return the first lipas-id whose name contains `name-fragment`
  (case-insensitive) in the current DB view, or nil.

  Uses sports_site_current view to avoid pulling all revisions.
  TODO: switch to ES if call sites need fuzzy/multilingual matching."
  [name-fragment]
  (let [rows (jdbc/execute! (db)
                            ["select lipas_id from sports_site_current
                 where document->>'name' ILIKE ?
                 limit 1"
                             (str "%" name-fragment "%")])]
    (some-> rows first :sports_site_current/lipas_id)))

;; ---------------------------------------------------------------------------
;; Seeding
;; ---------------------------------------------------------------------------

(def ^:private default-point-feature
  {:type "Feature"
   :properties {}
   :geometry {:type "Point"
              :coordinates [24.94 60.17]}}) ;; Helsinki city centre

(defn seed-site!
  "Save a minimal valid Point sports site and return its lipas-id.

  Required opts:
    :type-code  int  — must be a Point-geometry type (see catalog)
    :city-code  int

  Optional opts:
    :name       str  — default 'E2E test site'
    :user       map  — author; default robot/admin
    :coords     [lon lat] — default Helsinki centre
    :extras     map  — merged into the site doc (overrides defaults)

  For LineString/Polygon sites, build the doc manually — this helper is
  Point-only by design (covers ~80% of e2e cases)."
  [{:keys [type-code city-code name user coords extras]
    :or {name "E2E test site"
         coords [24.94 60.17]}}]
  (let [u (or user (admin-user))
        feature (assoc-in default-point-feature [:geometry :coordinates] coords)
        site (merge
               {:type {:type-code type-code}
                :name name
                :owner "city"
                :admin "city-sports"
                :status "active"
                :event-date (str (Instant/now))
                :location {:city {:city-code city-code}
                           :address "Testikatu 1"
                           :postal-code "00100"
                           :postal-office "Helsinki"
                           :geometries {:type "FeatureCollection"
                                        :features [feature]}}
                :properties {}}
               extras)
        saved (core/save-sports-site! (db) (search) (ptv) u site)]
    (:lipas-id saved)))

;; ---------------------------------------------------------------------------
;; Snapshot & coherence
;; ---------------------------------------------------------------------------

(defn snapshot
  "Snapshot cross-layer state for one site. Returns
    {:db   <doc-or-nil>
     :es   <doc-or-nil>
     :revs <int-or-nil>     ;; raw revision count from sports_site
     :jobs [<job-rows>]}.

  Each layer is best-effort: a missing layer returns nil and doesn't throw.
  Useful for failure diagnosis — paste into a report when an assertion fails."
  [lipas-id]
  {:db (try (core/get-sports-site (db) lipas-id)
            (catch Exception _ nil))
   :es (try (core/get-sports-site2 (search) lipas-id)
            (catch Exception _ nil))
   :revs (try (revision-count lipas-id) (catch Exception _ nil))
   :jobs (try (jobs-for lipas-id) (catch Exception _ []))})

(def ^:private coherence-fields
  "Fields compared between DB and ES by `coherent?`. Vector entries are
  get-in paths; keyword entries are top-level keys. Extend when a scenario
  needs more comparisons."
  [:name
   :status
   [:type :type-code]
   [:location :city :city-code]])

(defn coherent?
  "Compare DB and ES representations on a small set of key fields.
  Returns {:ok? bool :drift [{:field ... :db ... :es ...}]}.

  Use immediately after `save-sports-site!` (which is synchronous through
  ES) to confirm the write propagated coherently."
  [lipas-id]
  (let [{:keys [db es]} (snapshot lipas-id)
        drift (for [f coherence-fields
                    :let [path (if (vector? f) f [f])
                          a (get-in db path)
                          b (get-in es path)]
                    :when (not= a b)]
                {:field f :db a :es b})]
    {:ok? (and (some? db) (some? es) (empty? drift))
     :drift (vec drift)}))

;; ---------------------------------------------------------------------------
;; Revisions
;; ---------------------------------------------------------------------------

(defn revision-count
  "Number of raw revisions in the sports_site table for this lipas-id.

  NOTE: core/get-sports-site-history queries the sports_site_by_year view,
  which collapses multiple same-year revisions into one row. Use this when
  asserting that a save actually created a new revision."
  [lipas-id]
  (-> (jdbc/execute-one! (db)
                         ["select count(*) as c from sports_site where lipas_id = ?" lipas-id])
      :c))

;; ---------------------------------------------------------------------------
;; Async jobs (analysis, elevation, PTV sync)
;;
;; save-sports-site! indexes ES synchronously (the :sync flag inside
;; core/save-sports-site!), so create/update assertions don't need any
;; waiting. The async surface is the jobs table — that's what these helpers
;; are for.
;; ---------------------------------------------------------------------------

(defn jobs-for
  "Return job rows for a lipas-id, newest first. Optional :type filters by
  job type (\"analysis\", \"elevation\", \"webhook\", ...)."
  [lipas-id & {:keys [type]}]
  (if type
    (jdbc/execute! (db)
                   ["select id, type, status, attempts, last_error, created_at
        from jobs
        where (payload->>'lipas-id')::int = ? and type = ?
        order by created_at desc"
                    lipas-id type])
    (jdbc/execute! (db)
                   ["select id, type, status, attempts, last_error, created_at
        from jobs
        where (payload->>'lipas-id')::int = ?
        order by created_at desc"
                    lipas-id])))

(defn wait-for-job
  "Poll the jobs table until a job for `lipas-id` of `type` reaches a
  terminal status (completed, failed, dead). Returns the matching row,
  or nil on timeout. The newest matching job wins.

  Use this for assertions about elevation enrichment, PTV sync, analysis
  recompute, etc. — anything that survives past the synchronous save."
  [lipas-id type & {:keys [timeout-ms poll-ms]
                    :or {timeout-ms 10000 poll-ms 200}}]
  (let [deadline (+ (System/currentTimeMillis) timeout-ms)
        terminal? #{"completed" "failed" "dead"}]
    (loop []
      (let [job (first (jobs-for lipas-id :type type))]
        (cond
          (and job (terminal? (:jobs/status job))) job
          (> (System/currentTimeMillis) deadline) nil
          :else (do (Thread/sleep ^long poll-ms) (recur)))))))

;; ---------------------------------------------------------------------------
;; Cleanup
;; ---------------------------------------------------------------------------

(defn cleanup!
  "Soft delete: flip status to 'out-of-service-permanently' so the site
  stops appearing in default searches. Append-only model means no real
  delete is possible without nuking the DB.

  For a true clean slate, use lipas.test-utils/prune-db! — but ONLY on a
  test database. There is no safety check here."
  [lipas-id]
  (when-let [current (try (core/get-sports-site (db) lipas-id)
                          (catch Exception _ nil))]
    (let [modified (-> current
                       (assoc :status "out-of-service-permanently")
                       (assoc :event-date (str (Instant/now))))]
      (core/save-sports-site! (db) (search) (ptv) (admin-user) modified)))
  nil)

(comment
  ;; Smoke test (run in nREPL after a fresh `(user/reset)`)

  (require '[lipas.e2e.tools :as e2e] :reload)
  (require '[lipas.backend.core :as core])

  ;; Use the seeded city-manager (limindemo)
  (def liminka-user (core/get-user (e2e/db) "liminka@lipas.fi"))

  ;; Seed a swimming pool in Liminka — save is sync through ES, no wait needed
  (def lid (e2e/seed-site! {:type-code 3110
                            :city-code 425
                            :name "Smoke test pool"
                            :user liminka-user}))

  (e2e/coherent? lid)        ;; → {:ok? true :drift []}
  (e2e/revision-count lid)   ;; → 1
  (e2e/snapshot lid)

  ;; Update
  (let [current (core/get-sports-site (e2e/db) lid)]
    (core/save-sports-site! (e2e/db) (e2e/search) (e2e/ptv) liminka-user
                            (-> current
                                (assoc :name "Smoke test pool (updated)")
                                (assoc :event-date (str (java.time.Instant/now))))))

  (e2e/revision-count lid)   ;; → 2
  (e2e/coherent? lid)        ;; → {:ok? true :drift []}

  ;; Async job assertion
  (e2e/wait-for-job lid "analysis" :timeout-ms 30000)

  ;; Find by name
  (e2e/find-site "Smoke test")

  ;; Cleanup
  (e2e/cleanup! lid))
