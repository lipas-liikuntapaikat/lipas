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
     (when (and v population)
       (assoc m k (/ (* 1000 v) population))))
   {}
   m))

(defn- ->entries [data]
  (->> data
       (reduce
        (fn [res {:keys [city-code stats]}]
          (into res
                (for [[year data] stats
                      :let        [youth (-> data :services :youth-services)
                                   sports (-> data :services :sports-services)
                                   city (cities/by-city-code city-code)
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
        [])))

(defn index-city-finance-data! [{:keys [db search]}]
  (let [es-index "city_stats"]
    (log/info "Starting to index city finance data to" es-index)
    (->> (core/get-cities db)
         ->entries
         (search/->bulk es-index :id)
         (search/bulk-index! search)
         deref)
    (log/info "All done!")))

(defn- ->subsidy-entry [m]
  (-> m
      (assoc
       :timestamp (str (:year m) "-01-01")
       :province-id (-> m :city-code cities/by-city-code :province-id)
       :avi-id (-> m :city-code cities/by-city-code :avi-id))
      (cond->
          (->> m :type-codes (remove nil?) empty?) (assoc :type-codes [-1]))
      (dissoc :city-name)))

(defn index-subsidies! [{:keys [db search]}]
  (let [es-index "subsidies"]
    (log/info "Starting to index subsidies data to" es-index)
    (->> (core/get-subsidies db)
         (map ->subsidy-entry)
         (search/->bulk es-index :id)
         (search/bulk-index! search)
         deref)
    (log/info "All done!")))

(def tasks
  {:index-city-finance-data index-city-finance-data!
   :index-subsidies         index-subsidies!})

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
      (finally (backend/stop-system! system)))))

(defn -main [& args]
  (let [task-key (-> args first edn/read-string)
        args     (rest args)]
    (if-let [task (get tasks task-key)]
      (run-task! task args)
      (print-usage!))))

(comment
  (-main ":index-city-finance-data")
  (-main ":index-subsidies"))
