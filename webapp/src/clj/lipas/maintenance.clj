(ns lipas.maintenance
  (:require [lipas.backend.system :as backend]
            [lipas.backend.core :as core]
            [lipas.schema.core]
            [lipas.utils :as utils]
            [lipas.backend.db.db :as db]
            [clojure.string :as string]
            [clojure.edn :as edn]
            [lipas.data.swimming-pools :as swimming-pools]
            [lipas.data.materials :as materials]
            [clojure.spec.alpha :as s]
            [taoensso.timbre :as log]))

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

(defn fix-filtering-methods!
  "Updates [:water-treatment :filtering-method] to match spec."
  [db user]
  (log/info "Starting to fix swimming pool (3110 3130) filtering methods..")
  (let [data-3110 (core/get-sports-sites-by-type-code db 3110 {:revs "latest"})
        data-3130 (core/get-sports-sites-by-type-code db 3130 {:revs "latest"})
        path      [:water-treatment :filtering-methods]
        allowed   (-> swimming-pools/filtering-methods keys set)
        spec      :lipas.swimming-pool.water-treatment/filtering-methods]
    (->> (concat data-3110 data-3130)
         (filter-valid-in path spec)
         (map #(update-in % path (partial filter allowed)))
         (map utils/clean)
         (upsert-all! db user :lipas.sports-site/swimming-pool))))

(defn fix-building-materials!
  "Updates [:building :main-construction-materials] to contain only
  allowed values."
  [db user]
  (log/info "Starting to fix swimming pool (3110 3130) building materials..")
  (let [data-3110 (core/get-sports-sites-by-type-code db 3110 {:revs "latest"})
        data-3130 (core/get-sports-sites-by-type-code db 3130 {:revs "latest"})
        path      [:building :main-construction-materials]
        allowed   (-> materials/building-materials keys set)
        spec      :lipas.building/main-construction-materials]
    (->> (concat data-3110 data-3130)
         (filter-valid-in path spec)
         (map #(update-in % path (partial filter allowed)))
         (map utils/clean)
         (upsert-all! db user :lipas.sports-site/swimming-pool))))

(defn fix-ceiling-structures!
  "Updates [:building :ceiling-structures] to contain only
  allowed values."
  [db user]
  (log/info "Starting to fix swimming pool (3110 3130) ceiling structures..")
  (let [data-3110 (core/get-sports-sites-by-type-code db 3110 {:revs "latest"})
        data-3130 (core/get-sports-sites-by-type-code db 3130 {:revs "latest"})
        path      [:building :ceiling-structures]
        allowed   (-> materials/ceiling-structures keys set)
        spec      :lipas.building/ceiling-structures]
    (->> (concat data-3110 data-3130)
         (filter-valid-in path spec)
         (map #(update-in % path (partial filter allowed)))
         (map utils/clean)
         (upsert-all! db user :lipas.sports-site/swimming-pool))))

(defn fix-pool-type [spec pool]
  (if (s/valid? spec pool)
    pool
    (-> pool
        (as-> $ (if (= "outdoor-pool" (:type $))
                  (assoc $ :outdoor-pool? true)
                  $))
        (dissoc :type))))

(defn fix-pool-types!
  "Updates {:pools [{:pool-type \"some-type\"} {...}]} entries to
  contain only allowed values."
  [db user]
  (log/info "Starting to fix swimming pool (3110 3130) pool types..")
  (let [data-3110  (core/get-sports-sites-by-type-code db 3110 {:revs "latest"})
        data-3130  (core/get-sports-sites-by-type-code db 3130 {:revs "latest"})
        pools-spec :lipas.swimming-pool/pools
        pool-spec  :lipas.swimming-pool/pool]
    (->> (concat data-3110 data-3130)
         (filter-valid-in [:pools] pools-spec)
         (map (fn [s]
                (update s :pools (fn [pools]
                                   (map (partial fix-pool-type pool-spec) pools)))))
         (map utils/clean)
         (upsert-all! db user :lipas.sports-site/swimming-pool))))

(def tasks
  {:fix-filtering-methods  fix-filtering-methods!
   :fix-building-materials fix-building-materials!
   :fix-ceiling-structures fix-ceiling-structures!
   :fix-pool-types         fix-pool-types!})

(defn print-usage! []
  (println "\nUsage: lein run -m lipas.maintenance :task-name\n")
  (println "Available tasks:")
  (doseq [task (keys tasks)]
    (println task)))

(defn run-task! [task-fn]
  (let [config   (select-keys backend/default-config [:db])
        system   (backend/start-system! config)
        db       (:db system)
        user     (core/get-user db "import@lipas.fi")]
    (try
      (apply task-fn [db user]) ; TODO maybe pass a map instead?
      (finally (backend/stop-system! system)))))

(defn -main [& args]
  (let [task-key (-> args first edn/read-string)]
    (if-let [task (get tasks task-key)]
      (run-task! task)
      (print-usage!))))

(comment
  (-main ":fix-kissa")
  (-main ":fix-pool-types")
  (-main ":fix-ceiling-structures"))
