(ns user
  "Utilities for reloaded workflow using `integrant.repl`."
  (:require
   [clojure.tools.namespace.repl]
   [integrant.repl :refer [reset reset-all halt go]]
   [integrant.repl.state]
   [migratus.core :as migratus]))

(integrant.repl/set-prep! (fn []
                            (dissoc @(requiring-resolve 'lipas.backend.config/system-config) :lipas/nrepl)))
#_(clojure.tools.namespace.repl/set-refresh-dirs "/src")

(defn current-config []
  integrant.repl.state/config)

(defn current-system []
  integrant.repl.state/system)

(defn assert-running-system []
  (assert (current-system) "System is not running. Start the system first."))

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

(defn reindex-search!
  []
  ((requiring-resolve 'lipas.search-indexer/main) (db) (search) "search"))

(defn reindex-legacy-search!
  []
  ((requiring-resolve 'lipas.search-indexer/main) (db) (search) "legacy"))

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

(defn run-db-migrations!
  []
  (migratus/migrate {:store :database :db (db)}))

(comment
  (go)
  (reset)
  (reindex-search!)
  (reindex-analytics!)
  (reindex-legacy-search!)


  #_:clj-kondo/ignore
  (lipas.search-indexer/index-legacy-sports-site! (db) (search) "legacy-2025-03-31t15-49-55-720612" 74782)
  #_:clj-kondo/ignore
  (-> (lipas.backend.core/get-sports-site (db) 74782)
      (lipas.integration.old-lipas.transform/->old-lipas-sports-site)
      (assoc :id 74872)
      (lipas-api.sports-places/format-sports-place
       :all
       lipas-api.locations/format-location
       lipas-api.properties/format-props-db))

  (defn index-single [lipasid]
    (-> (lipas.backend.core/get-sports-site (db) 74782)
        (lipas.integration.old-lipas.transform/->old-lipas-sports-site)
        (assoc :id 74782)
        (lipas-api.sports-places/format-sports-place
         :all
         lipas-api.locations/format-location
         lipas-api.properties/format-props-db)
        )
    )





  (index-single 76201)
  (index-single 74782)
  ;;=> {:typeCode nil}
  (transform/->old-lipas-sports-site)
  (assoc :id lipas-id)
  (lipas-sports-places/format-sports-place
   :all
   legacy-sports-places/format-sports-place-db
   legacy-properties/format-props-db)

  (reset-admin-password! "kissa13")
  (reset-password! "valtteri.harmainen@gmail.com" "kissa13")

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
  )
