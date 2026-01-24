(ns user
  "Utilities for reloaded workflow using `integrant.repl`."
  (:require
   [clojure.core.async :as async]
   [clojure.tools.namespace.repl]
   [shadow.cljs.devtools.api :as shadow]
   [integrant.repl :refer [reset-all halt go]]
   [integrant.repl.state]
   [migratus.core :as migratus]
   [lipas.wfs.core :as wfs]))

(integrant.repl/set-prep! (fn []
                            (dissoc @(requiring-resolve 'lipas.backend.config/system-config) :lipas/nrepl)))

#_(clojure.tools.namespace.repl/set-refresh-dirs "/src" "/test")

(defn current-config []
  integrant.repl.state/config)

(defn current-system []
  integrant.repl.state/system)

(defn assert-running-system []
  (assert (current-system) "System is not running. Start the system first."))

(defn current-config []
  integrant.repl.state/config)

(defn db
  "Returns the :lipas/db key of the currently running system. Useful for
  REPL sessions when a function expects `db` as an argument."
  []
  (assert-running-system)
  (:lipas/db (current-system)))

(defn search
  "Returns the :lipas/search key of the currently running system. Useful
  for REPL sessions when a function expects `search` as an argument."
  []
  (assert-running-system)
  (:lipas/search (current-system)))

(defn ptv
  []
  (assert-running-system)
  (:lipas/ptv (current-system)))

(defn ptv-lookup-org
  "Look up a PTV organization by ID or search by name.

   Usage:
     (ptv-lookup-org \"7eca397e-7a0e-4f79-bf1b-6248560a7268\")  ; lookup by ID
     (ptv-lookup-org \"Vaala\")                                  ; search by name
     (ptv-lookup-org \"328aabaf-59a6-4813-b826-f7f30f101398\")  ; check if ID exists

   Returns organization details if found, or search results if searching by name.
   Useful for verifying organization IDs received from DVV.

   Options:
     :env - :prod (default) or :test"
  ([query] (ptv-lookup-org query {}))
  ([query {:keys [env] :or {env :prod}}]
   (let [client (requiring-resolve 'clj-http.client/request)
         base-url (case env
                    :prod "https://api.palvelutietovaranto.suomi.fi/api"
                    :test "https://api.palvelutietovaranto.trn.suomi.fi/api")
         uuid-pattern #"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
         uuid? (re-matches uuid-pattern (str query))]
     (if uuid?
       ;; Direct lookup by ID
       (let [resp (client {:url (str base-url "/v11/Organization/" query)
                           :method :get
                           :as :json
                           :throw-exceptions false})]
         (if (= 200 (:status resp))
           (let [org (:body resp)]
             {:found? true
              :id (:id org)
              :name (some #(when (= "fi" (:language %)) (:value %)) (:organizationNames org))
              :type (:organizationType org)
              :names (:organizationNames org)})
           {:found? false
            :id query
            :message (str "Not a valid PTV organization ID. "
                          "This might be an internal auth ID (prod-org-id).")}))
       ;; Search by name
       (let [results (atom [])
             fetch-page (fn [page]
                          (-> (client {:url (str base-url "/v11/Organization")
                                       :method :get
                                       :as :json
                                       :query-params {:page page :status "Published"}})
                              :body))]
         ;; Fetch all pages and search
         (loop [page 1]
           (let [resp (fetch-page page)
                 ;; List endpoint returns :name directly, not :organizationNames
                 matches (->> (:itemList resp)
                              (filter (fn [org]
                                        (re-find (re-pattern (str "(?i)" query))
                                                 (or (:name org) "")))))]
             (swap! results concat matches)
             (when (< page (:pageCount resp))
               (recur (inc page)))))
         ;; Format results
         (if (seq @results)
           {:found? true
            :count (count @results)
            :results (mapv (fn [org]
                             {:id (:id org)
                              :name (:name org)})
                           @results)}
           {:found? false
            :query query
            :message "No organizations found matching that name."}))))))

(defn reindex-search!
  []
  ((requiring-resolve 'lipas.search-indexer/main) (db) (search) "search"))

(defn reindex-lois!
  []
  ((requiring-resolve 'lipas.search-indexer/index-search-lois!) (db) (search)))

(defn reindex-analytics!
  []
  ((requiring-resolve 'lipas.search-indexer/main) (db) (search) "analytics"))

(defn reset-password!
  [email password]
  (let [user ((requiring-resolve 'lipas.backend.core/get-user) (db) email)]
    ((requiring-resolve 'lipas.backend.core/reset-password!) (db) user password)))

(defn reset-admin-password!
  [password]
  (reset-password! "admin@lipas.fi" password))

(defn get-robot-user
  "Returns the always existing robot user who has admin permissions."
  []
  ((requiring-resolve 'lipas.backend.core/get-user) (db) "robot@lipas.fi"))

(defn run-db-migrations!
  []
  (migratus/migrate {:store :database :db (db)}))

(defn reset []
  (integrant.repl/reset))

(defn refresh-all
  "Performs a full refresh of all namespaces using tools.namespace.
   Unlike the incremental refresh, this reloads everything from scratch.
   Useful when incremental refresh gets into a bad state."
  []
  (clojure.tools.namespace.repl/refresh-all))

(defn browser-repl
  []
  (shadow/repl :app))

(defn compile-cljs
  []
  (let [status (shadow/watch-compile! :app)
        worker (shadow.cljs.devtools.api/get-worker :app)
        build-state (-> worker :state-ref deref :build-state)]
    (tap> build-state)
    {:build-exit-code status
     :warnings (-> build-state
                   :shadow.build/build-info
                   :sources
                   (->> (mapcat :warnings)))
     :errors (-> build-state
                 :shadow.build/build-info
                 :sources
                 (->> (mapcat :errors)))}))

(defn validate-sports-sites
  "Scrolls through all sports sites in Elasticsearch and validates them against
   the Malli schema. Returns a map with :total, :valid, :invalid counts and
   :errors (a seq of {:lipas-id, :error} maps for invalid documents).

   Options:
     :limit    - Stop after processing this many documents (nil = all)
     :verbose? - Print progress every 1000 documents (default: true)"
  ([] (validate-sports-sites {}))
  ([{:keys [limit verbose?] :or {verbose? true}}]
   (assert-running-system)
   (let [search-comp (search)
         scroll-fn (requiring-resolve 'lipas.backend.search/scroll)
         schema @(requiring-resolve 'lipas.schema.sports-sites/sports-site)
         m-validate (requiring-resolve 'malli.core/validate)
         m-explain (requiring-resolve 'malli.core/explain)
         me-humanize (requiring-resolve 'malli.error/humanize)

         idx-name (get-in search-comp [:indices :sports-site :search])
         scroll-chan (scroll-fn (:client search-comp) idx-name
                                {:query {:match_all {}} :size 500})

         ;; Channel -> lazy seq of docs
         all-docs (cond->> (->> (repeatedly #(async/<!! scroll-chan))
                                (take-while some?)
                                (mapcat #(-> % :body :hits :hits))
                                (map :_source))
                    limit (take limit))

         ;; Validate with progress reporting
         results (reduce
                  (fn [{:keys [total valid errors] :as acc} doc]
                    (when (and verbose? (pos? total) (zero? (mod total 1000)))
                      (println (format "Processed %d documents..." total)))
                    (if (m-validate schema doc)
                      (-> acc (update :total inc) (update :valid inc))
                      (-> acc
                          (update :total inc)
                          (update :errors conj
                                  {:lipas-id (:lipas-id doc)
                                   :type-code (-> doc :type :type-code)
                                   :error (me-humanize (m-explain schema doc))}))))
                  {:total 0 :valid 0 :errors []}
                  all-docs)

         results (assoc results :invalid (count (:errors results)))]

     (when verbose?
       (println (format "\n=== Validation Complete ==="))
       (println (format "Total: %d | Valid: %d | Invalid: %d"
                        (:total results) (:valid results) (:invalid results))))
     results)))

(comment
  (go)
  (reset)
  (reindex-search!)
  (reindex-analytics!)
  (reset-admin-password! "kissa13")
  (reset-password! "valtteri.harmainen@gmail.com" "kissa13")

  (compile-cljs)

  (require '[migratus.core :as migratus])
  (migratus/create nil "activities_status" :sql)
  (migratus/create nil "roles" :edn)
  (migratus/create nil "year_round_use" :sql)
  (migratus/create nil "versioned_data" :sql)
  (run-db-migrations!)

  (require '[lipas.maintenance :as maintenance])
  (require '[lipas.backend.core :as core])

  (def robot (core/get-user (db) "robot@lipas.fi"))

  (maintenance/merge-types (db) (search) (ptv) robot 4530 4510)
  (maintenance/merge-types (db) (search) (ptv) robot 4520 4510)
  (maintenance/merge-types (db) (search) (ptv) robot 4310 4320)

  (require '[lipas.data.types :as types])
  (require '[lipas.data.types-old :as types-old])
  (require '[lipas.data.prop-types :as prop-types])
  (require '[lipas.data.prop-types-old :as prop-types-old])
  (require '[clojure.set :as set])

  (def new-types (set/difference
                  (set (keys types/all))
                  (set (keys types-old/all))))

  (require '[clojure.string :as str])

  (for [type-code (conj new-types 113)]
    (println
     (format "INSERT INTO public.liikuntapaikkatyyppi(
        id, tyyppikoodi, nimi_fi, nimi_se, kuvaus_fi, kuvaus_se, liikuntapaikkatyyppi_alaryhma, geometria, tason_nimi, nimi_en, kuvaus_en)
        VALUES (%s, %s, '%s', '%s', '%s', '%s', %s, '%s', '%s', '%s', '%s');"
             type-code
             type-code
             (get-in types/all [type-code :name :fi])
             (get-in types/all [type-code :name :se])
             (get-in types/all [type-code :description :fi])
             (get-in types/all [type-code :description :se])
             (get-in types/all [type-code :sub-category])
             (get-in types/all [type-code :geometry-type])
             (-> types/all
                 (get-in [type-code :name :fi])
                 csk/->snake_case
                 (str/replace "ä" "a")
                 (str/replace "ö" "o")
                 (str/replace #"[^a-zA-Z]" "")
                 (->> (str "lipas_" type-code "_")))
             (get-in types/all [type-code :name :en])
             (get-in types/all [type-code :description :en]))))

  (def new-props (set/difference
                  (set (keys prop-types/all))
                  (set (keys prop-types-old/all))))

  new-props

  (require '[camel-snake-kebab.core :as csk])
  (def legacy-mapping (into {} (for [p new-props]
                                 [(csk/->camelCaseKeyword p) p])))

  (keys legacy-mapping)

  (def legacy-mapping-reverse (set/map-invert legacy-mapping))

  (require '[lipas.data.prop-types :as prop-types])

  (def data-types
    "Legacy lipas supports only these. Others will be treated as strings"
    {"boolean" "boolean"
     "numeric" "numberic"
     "string"  "string"})

  (doseq [[legacy-prop-k prop-k] legacy-mapping]
    (println
     (format "INSERT INTO public.ominaisuustyypit(
        nimi_fi, tietotyyppi, kuvaus_fi, nimi_se, kuvaus_se, ui_nimi_fi, nimi_en, kuvaus_en, handle)
        VALUES ('%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s');"
             (csk/->snake_case (get-in prop-types/all [prop-k :name :fi]))
             (get data-types (get-in prop-types/all [prop-k :data-type]) "string")
             (get-in prop-types/all [prop-k :description :fi])
             (get-in prop-types/all [prop-k :name :se])
             (get-in prop-types/all [prop-k :description :se])
             (get-in prop-types/all [prop-k :name :fi])
             (get-in prop-types/all [prop-k :name :en])
             (get-in prop-types/all [prop-k :description :en])
             (name legacy-prop-k))))

  (doseq [p             new-props
          [type-code m] types/all]
    (when (contains? (set (keys (:props m))) p)
      (println (str "-- Type " type-code))
      (println (str "-- prop " p))
      (println
       (format
        "INSERT INTO public.tyypinominaisuus(
        liikuntapaikkatyyppi_id, ominaisuustyyppi_id, prioriteetti)
        VALUES (%s, %s, %s);"
        (format "(select id from liikuntapaikkatyyppi where tyyppikoodi = %s)"
                type-code)
        (format "(select id from ominaisuustyypit where handle = '%s')"
                (name (legacy-mapping-reverse p)))
        (get-in types/all [type-code :props p :priority])))))

  (require '[malli.provider :as mp])
  (require '[lipas.data.types :as types])
  (require '[lipas.backend.core :as core])

  (def all-sites (atom {}))

  (doseq [type-code (keys types/all)]
    (let [sites (core/get-sports-sites-by-type-code (db) type-code)]
      (swap! all-sites (fn [m]
                         (reduce (fn [res site] (assoc res (:lipas-id site) site))
                                 m
                                 sites)))))

  (mp/provide (vals @all-sites))
  (def schema *1)

  (require '[lipas.data.sports-sites :as ss])
  (require '[malli.core :as m])

  (m/schema ss/base-schema)

  (m/validate ss/base-schema (-> @all-sites first second))

  (m/schema [:string {:min 1 :max 200}])
  (m/schema [:string {:min 1 :max 2048}])

  (require '[lipas.data.prop-types :as prop-types])

  (m/schema [:set (into [:enum] (keys (:opts (:surface-material prop-types/all))))])

  (require '[malli.dev])
  (malli.dev/start!)

  (require '[malli.error :as me])

  (def results
    (for [[lipas-id site] @all-sites]
      [lipas-id (me/humanize (m/explain ss/base-schema site))]))

  (->> results
       (filter #(some? (second %))))

  (:activities (@all-sites 613971))

  (count *1)
  (@all-sites 613811)

  (require '[malli.json-schema :as mj])
  (mj/transform ss/base-schema)

  (require '[lipas.data.types-new :as types-new])
  (require '[lipas.data.types-old :as types-old])

  (require '[clojure.set :as set])

  (def new-codes (set/difference
                  (set (keys types-new/all))
                  (set (keys types-old/all))))

  (-> types-new/all
      (select-keys new-codes)
      (->> (map (juxt first (comp :fi :name second)))
           (sort-by first)))

  (def merged-codes (set/difference
                     (set (keys types-old/active))
                     (set (keys types-new/active))))

  (-> types-old/all
      (select-keys merged-codes)
      (->> (map (juxt first (comp :fi :name second)))
           (sort-by first)))

  (def merged-to [101 103 106 203 4320 4510 4510])
  (-> types-new/all
      (select-keys merged-to)
      (->> (map (juxt first (comp :fi :name second)))
           (sort-by first)))

  (require '[lipas.data.prop-types-new :as prop-types-new])
  (require '[lipas.data.prop-types-old :as prop-types-old])

  (def new-prop-types (set/difference
                       (set (keys prop-types-new/all))
                       (set (keys prop-types-old/all))))

  (require '[lipas.data.help :as help-data])
  (require '[lipas.backend.core :as core])
  (core/save-help-data (db) help-data/sections)
  *1

  (require '[malli.core :as m])
  (require '[malli.error :as me])
  (require '[lipas.schema.help :as help-schema])

  (me/humanize (m/explain help-schema/HelpData (core/get-help-data (db))))

  (def help-dada (core/get-help-data (db)))

  (m/validate help-schema/HelpData help-dada)

  (require '[lipas.schema.help :as help-schema])

  (def help-dada-v2
    (-> (core/get-help-data (db)) help-schema/transform-old-to-new-format))

  (core/save-help-data (db) help-dada-v2)

  (lipas.backend.core/process-elevation-queue! (db) (search))
  (reindex-search!)
  (require '[lipas.backend.core])
  (def wat (lipas.backend.core/get-sports-site (db) 520086))
  (tap> wat)

  (reindex-search!)
  (lipas.backend.core/get-sports-site2 (search) 666)

  (def wat2 (lipas.backend.core/get-sports-site (db) 600385))
  (require '[lipas.backend.elevation :as ele])
  (def wat2g (-> wat2 :location :geometries ele/enrich-elevation))
  (def wat2-fixed (assoc-in wat2 [:location :geometries] wat2g))

  (lipas.backend.core/upsert-sports-site!* (db) (get-robot-user) wat2-fixed)
  (lipas.backend.core/index! (search) wat2-fixed))

;; Population grids
(comment
  (require '[lipas.backend.analysis.common :as ac])
  (def path-1km "/Users/tipo/lipas/aineistot/vaestoruutu_1km_2024/vaestoruutu_1km_2024.csv")

  (ac/seed-population-1km-grid-from-csv! (search) path-1km)

  (def path-250m "/Users/tipo/lipas/aineistot/vaestoruutu_250m_2024/vaestoruutu_250m_2024_kp.csv")

  (ac/seed-population-250m-grid-from-csv! (search) path-250m)

  ;; 2025-11-01 19:50 backup prodista
  (reindex-search!)
  (require '[lipas.backend.analysis.diversity :as ad])
  (ad/seed-new-grid-from-csv! (search) path-250m)

  (reindex-lois!)

  (require '[lipas.wfs.core :as wfs])
  (wfs/drop-legacy-mat-views! (db))
  (wfs/create-legacy-mat-views! (db))
  (wfs/refresh-all! (db)))
