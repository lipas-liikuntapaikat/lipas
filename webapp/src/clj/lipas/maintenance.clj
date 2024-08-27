(ns lipas.maintenance
  (:require
   [cheshire.core :as json]
   [clojure.data.csv :as csv]
   [clojure.edn :as edn]
   [clojure.java.jdbc :as jdbc]
   [clojure.set :as set]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [clojure.walk :as walk]
   [lipas.backend.config :as config]
   [lipas.backend.core :as core]
   [lipas.backend.db.db :as db]
   [lipas.backend.search :as search]
   [lipas.backend.system :as backend]
   [lipas.data.cities :as cities]
   [lipas.data.owners :as owners]
   [lipas.schema.core]
   [lipas.utils :as utils]
   [taoensso.timbre :as log])
  (:import java.lang.Math))

(def all-cities
  (merge
   cities/by-city-code
   cities/abolished-by-city-code))

(defn upsert-all!
  ([db user sports-sites]
   (upsert-all! db user :lipas/sports-site sports-sites))
  ([db user spec sports-sites]
   (if (empty? sports-sites)
     (log/info "Collection contains 0 sports sites. Nothing to do!")
     (if (utils/all-valid? spec sports-sites)
       (do
         (log/info "Inserting sports sites" (mapv :lipas-id sports-sites))
         (db/upsert-sports-sites! db user sports-sites)
         (log/info "Done inserting data!"))
       (log/error "Invalid data, check messages in STDOUT.")))))

(defn filter-valid-in [path spec coll]
  (->> coll
       (filter (comp some? #(get-in % path)))
       (remove (comp (partial s/valid? spec) #(get-in % path)))))

(defn- calc-per-capita [m population]
  (reduce-kv
   (fn [m k v]
     (let [v (when (and v population) (/ (* 1000 v) population))]
       (assoc m k v)))
   {}
   m))

(defn- ->city-finance-entries [data]
  (reduce
   (fn [res {:keys [city-code stats]}]
     (into res
           (for [[year data] stats
                 :let        [youth (-> data :services :youth-services)
                              sports (-> data :services :sports-services)
                              city (all-cities city-code)
                              province (cities/provinces (:province-id city))
                              avi (cities/avi-areas (:avi-id city))
                              popl (:population data)]]
             (merge
              (select-keys city [:province-id :avi-id])
              {:id         (str city-code "-" year)
               :date       (str year "-01-01")
               :city-code  city-code
               :year       year
               :population popl
               :search_meta
               {:city-name     (:name city)
                :province-name (:name province)
                :avi-name      (:name avi)}}
              (utils/->prefix-map youth "youth-services-")
              (utils/->prefix-map sports "sports-services-")
              (-> youth
                  (calc-per-capita popl)
                  (utils/->prefix-map "youth-services-pc-"))
              (-> sports
                  (calc-per-capita popl)
                  (utils/->prefix-map "sports-services-pc-"))))))
   []
   data))

(defn- ->subsidy-es-entry [m]
  (-> m
      (assoc
       :timestamp (str (:year m) "-01-01")
       :province-id (-> m :city-code all-cities :province-id)
       :avi-id (-> m :city-code all-cities :avi-id))
      (cond->
          (->> m :type-codes (remove nil?) empty?) (assoc :type-codes [-1]))
      (dissoc :city-name)))

;; There are no sensible primary keys in the data so we purge and
;; rewrite the index each time.
(defn index-subsidies!
  "Deletes current index and creates new one with data from db-table
  'subsidy'."
  [{:keys [db search]}]
  (let [{:keys [indices client]} search
        es-index                 (get-in indices [:report :subsidies])]
    (log/info "Deleting index" es-index)
    (search/delete-index! client es-index)
    (log/info "Deleted" es-index)
    (log/info "Starting to index subsidies data to" es-index)
    (->> (core/get-subsidies db)
         (map ->subsidy-es-entry)
         (search/->bulk es-index :id)
         (search/bulk-index! client)
         deref)
    (log/info "All done!")))

(def owner-lookup (utils/index-by (comp :fi second) first owners/all))
(def city-lookup (utils/index-by (comp :fi :name) :city-code cities/all))

(def subsidy-csv-headers
    {"Saajataho, luokiteltu (Lipas-luokitus omistaja)" :owner
     "Liikuntapaikan nimi"                             :target
     "Avustuksen saaja"                                :receiver-name
     "Kunta myöntövuonna"                              :city-name
     "Myöntövuosi"                                     :year
     "Tyyppikoodi (numero)"                            :type-codes
     "Avustuksen myöntäjä"                             :issuer
     "Lipas-ID"                                        :lipas-ids
     "Myönnetty avustus tuhatta e"                     :amount
     "Avustuksen selite"                               :description})

(defn ->subsidy-db-entry [m]
    (-> m
        (assoc :city-code (-> m :city-name city-lookup))
        (update :type-codes #(-> % (str/split #";") (->> (mapv utils/->int))))
        (update :year utils/->int)
        (update :lipas-ids #(-> % (str/split #";") (->> (mapv utils/->int) (remove nil?))))
        (update :owner owner-lookup)
        (update :issuer #(condp = %
                           "ELY" "AVI"
                           "OPM" "OKM"
                           %))
        (update :amount utils/->number)))

(def valid-subsidy-keys #{:description :amount :lipas-ids :city-name :receiver-name
  :type-codes :city-code :year :issuer :target :owner})

(defn add-subsidies-from-csv!
  [{:keys [db search] :as system} csv-path]

  (log/info "Reading subsidies from csv" csv-path)

  (let [ms (->> csv-path
                slurp
                csv/read-csv
                utils/csv-data->maps
                (map #(set/rename-keys % subsidy-csv-headers))
                (map ->subsidy-db-entry))]

    ;; TODO add malli schema or spec for entries
    (log/info "Validating" (count ms) "subsidy entries")
    ;; Validate that all columns names were matched
    ;; Note: :target (sports-site name) is not available in all sheets

    #_(println (set/difference (into #{} (mapcat keys) ms) valid-keys))

    (assert (= valid-subsidy-keys (into #{} (mapcat keys) ms)))

    (log/info "Writing subsidies to database")
    (jdbc/with-db-transaction [tx db]
      (doseq [m ms]
        (db/add-subsidy! tx m)))

    (log/info "Indexing to Elasticsearch")
    (index-subsidies! system)))

;; `year`-`city` is used as primary key
(defn index-city-finance-data!
  "Re-indexes documents from db table 'city' into ES."
  [{:keys [db search]}]
  (let [{:keys [indices client]} search
        es-index                 (get-in indices [:report :city-stats])]
    (log/info "Starting to index city finance data to" es-index)
    (->> (core/get-cities db :no-cache)
         ->city-finance-entries
         (search/->bulk es-index :id)
         (search/bulk-index! client)
         deref)
    (log/info "All done!")))

(defn ->number-div-by-1000 [x]
  (when-let [n (utils/->number x)]
    (/ n 1000)))

(defn ->city-stats-map
  [csv-data year]
  (reduce
     (fn [res m]
       (let [city-code (-> m :kunta_nimi city-lookup)]
         (-> res
             (assoc-in [city-code year :population] (or (-> m :asukasluku utils/->number) 0))
             (assoc-in [city-code year :services "youth-services"]
                       {:investments          (-> m :nuor_investoinnit ->number-div-by-1000)
                        :net-costs            (-> m :nuor_nettokustannukset ->number-div-by-1000)
                        :subsidies            (-> m :nuor_avustukset ->number-div-by-1000)
                        :operating-expenses   (-> m :nuor_kayttokustannukset ->number-div-by-1000)
                        :operating-incomes    (-> m :nuor_kayttotuotot ->number-div-by-1000)
                        ;; New names since 2021 Numbers are not
                        ;; probably comparatible with earlier ones and
                        ;; thus new keys
                        :operational-expenses (-> m :nuor_toimintakulut ->number-div-by-1000)
                        :operational-income   (-> m :nuor_toimintatuotot ->number-div-by-1000)
                        :surplus              (-> m :nuor_tilikauden_ylijaama ->number-div-by-1000)
                        :deficit              (-> m :nuor_tilikauden_alijaama ->number-div-by-1000)})
             (assoc-in [city-code year :services "sports-services"]
                       {:investments          (-> m :liik_investoinnit ->number-div-by-1000)
                        :net-costs            (-> m :liik_nettokustannukset ->number-div-by-1000)
                        :subsidies            (-> m :liik_avustukset ->number-div-by-1000)
                        :operating-expenses   (-> m :liik_kayttokustannukset ->number-div-by-1000)
                        :operating-incomes    (-> m :liik_kayttotuotot ->number-div-by-1000)
                        ;; New names since 2021 Numbers are not
                        ;; probably comparatible with earlier ones and
                        ;; thus new keys
                        :operational-expenses (-> m :liik_toimintakulut ->number-div-by-1000)
                        :operational-income   (-> m :liik_toimintatuotot ->number-div-by-1000)
                        :surplus              (-> m :liik_tilikauden_ylijaama ->number-div-by-1000)
                        :deficit              (-> m :liik_tilikauden_alijaama ->number-div-by-1000)}))))
     {}
     csv-data))

(def city-finance-csv-headers
    ["kunta_nimi"
     "asukasluku"
     "liik_investoinnit"
     #_"liik_kayttokustannukset" ; Until 2021
     #_"liik_kayttotuotot"       ; Until 2021
     #_"liik_nettokustannukset"  ; Until 2021
     "liik_avustukset"
     "liik_toimintakulut"        ; Since 2022
     "liik_toimintatuotot"       ; Since 2022
     "liik_tilikauden_ylijaama"  ; Since 2022
     "liik_tilikauden_alijaama"  ; Since 2022
     "nuor_investoinnit"
     #_"nuor_kayttokustannukset" ; Until 2021
     #_"nuor_kayttotuotot"       ; Until 2021
     #_"nuor_nettokustannukset"  ; Until 2021
     "nuor_avustukset"
     "nuor_toimintakulut"        ; Since 2022
     "nuor_toimintatuotot"       ; Since 2022
     "nuor_tilikauden_ylijaama"  ; Since 2022
     "nuor_tilikauden_alijaama"  ; Since 2022
     ])

(defn ->city-finance-sql-update
  [city-code year m]
    (let [entry (json/encode m)]
      (format
       (str
        "UPDATE city "
        "SET stats = jsonb_set(stats, '{%s}', '%s') "
        "WHERE city_code = %s;")
       year entry city-code)))

(defn add-city-stats-from-csv!
  [{:keys [db _search] :as system} csv-path year]
  (log/info "Reading city finance entries for year" year "from" csv-path)
  (let [csv-data  (->> csv-path
                       slurp
                       csv/read-csv
                       utils/csv-data->maps
                       (map walk/keywordize-keys))
        stats-map (->city-stats-map csv-data year)]

    ;; Validate that all columns names were matched
    ;; TODO add malli schema or spec for entries
    (log/info "Validating" (count csv-data) "city finance entries")
    (assert (= (into #{} (map keyword) city-finance-csv-headers)
               (into #{} (mapcat keys) csv-data)))


    (log/info "Storing city finance entries into the db")
    (jdbc/with-db-transaction [tx db]
      (doseq [[city-code m] stats-map
              [year stats]  m]
        (let [sql (->city-finance-sql-update city-code year stats)]
          (jdbc/execute! tx [sql]))))

    (log/info "Re-indexing city finance data to elasticsearch")
    (index-city-finance-data! system)))

(def get-city-name (comp :fi :name all-cities))

(defn newer [a b]
  (if (.isAfter (java.time.Instant/parse a) (java.time.Instant/parse b))
    a
    b))

;; https://fi.wikipedia.org/wiki/Kuntaliitos_Suomessa
(defn merge-cities
  [{:keys [db user]} from-city-code to-city-code event-date]
  (let [from-city-code (utils/->int from-city-code)
        to-city-code   (utils/->int to-city-code)
        event-date     (or event-date (utils/timestamp))
        from-name      (get-city-name from-city-code)
        to-name        (get-city-name to-city-code)]
    (log/info "Merging" from-name from-city-code "->" to-name to-city-code)
    (->> from-city-code
         (db/get-sports-sites-by-city-code db)
         (map (fn [m]
                (-> m
                    (assoc-in [:location :city :city-code] to-city-code)
                    (update :event-date #(newer % event-date)))))
         (upsert-all! db user))
    (log/info "All done!")))

(def tasks
  {:index-city-finance-data index-city-finance-data!
   :index-subsidies         index-subsidies!
   :merge-cities            merge-cities})

(defn print-usage! []
  (println "\nUsage: lein run -m lipas.maintenance :task-name\n")
  (println "Available tasks:")
  (doseq [task (keys tasks)]
    (println task)))

(defn run-task! [task-fn args]
  (let [config (select-keys config/system-config [:lipas/db :lipas/search])
        system (backend/start-system! config)
        db     (:lipas/db system)
        search (:lipas/search system)
        user   (core/get-user db "import@lipas.fi")
        args'  {:db db :search search :user user}]
    (try
      (apply task-fn (into [args'] args))
      (catch Exception e (log/error e))
      (finally
        (backend/stop-system! system)
        (shutdown-agents)
        (System/exit 0)))))

(defn -main [& args]
  (let [task-key (-> args first edn/read-string)
        args     (rest args)]
    (if-let [task (get tasks task-key)]
      (run-task! task args)
      (print-usage!))))

(comment
  (-main ":index-city-finance-data")
  (-main ":index-subsidies")
  ;; Valtimo -> Nurmes
  (-main ":merge-cities" "911" "541" "2020-01-01T00:00:00.000Z")

  (def config (select-keys config/default-config [:db :search]))
  (def system (backend/start-system! config))
  (index-subsidies! system)
  (index-city-finance-data! system)
  (def city-data (core/get-cities (:db system) :no-cache))

  (utils/->prefix-map
   (calc-per-capita
    {:net-costs 17.0,
     :subsidies 4.0,
     :investments 0.0,
     :operating-incomes 0.0,
     :operating-expenses 17.0}
    2500)
   "kissa-komodo-")

  (-> city-data
      ->city-finance-entries
      (->> (filter #(= 2021 (:year %)))))

  (def aki (filter #(= 992 (:city-code %)) city-data))
  (def kair (first aki))

  (->city-finance-entries [(update kair :stats select-keys [2021])])

  (add-subsidies-from-csv! system "/usr/src/app/avustukset_2023.csv")


  )

(comment
  (def csv-path "/Users/tipo/lipas/taloustiedot/taloustiedot_2022.csv")
  (add-city-stats-from-csv! system csv-path 2022)
  (->> csv-path
       slurp
       csv/read-csv
       utils/csv-data->maps
       (mapcat keys)
       (into #{})
       #_(map walk/keywordize-keys)
       #_(map :kunta_nimi)
       #_(map city-lookup)
       (set/difference (set city-finance-csv-headers))))
