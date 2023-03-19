(ns lipas.maintenance
  (:require
   [clojure.edn :as edn]
   [clojure.spec.alpha :as s]
   [lipas.backend.config :as config]
   [lipas.backend.core :as core]
   [lipas.backend.db.db :as db]
   [lipas.backend.system :as backend]
   [lipas.data.cities :as cities]
   [lipas.backend.search :as search]
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

;; `year`-`city` is used as primary key
(defn index-city-finance-data!
  "Re-indexes documents from db table 'city' into ES."
  [{:keys [db search]}]
  (let [es-index "city_stats"]
    (log/info "Starting to index city finance data to" es-index)
    (->> (core/get-cities db :no-cache)
         ->city-finance-entries
         (search/->bulk es-index :id)
         (search/bulk-index! search)
         deref)
    (log/info "All done!")))

(defn- ->subsidy-entry [m]
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
  (let [es-index "subsidies"]
    (log/info "Deleting index" es-index)
    (search/delete-index! search es-index)
    (log/info "Deleted" es-index)
    (log/info "Starting to index subsidies data to" es-index)
    (->> (core/get-subsidies db)
         (map ->subsidy-entry)
         (search/->bulk es-index :id)
         (search/bulk-index! search)
         deref)
    (log/info "All done!")))

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
  (let [config (select-keys config/default-config [:db :search])
        system (backend/start-system! config)
        db     (:db system)
        search (:search system)
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

  )
