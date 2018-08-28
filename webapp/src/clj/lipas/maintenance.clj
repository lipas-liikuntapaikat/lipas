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
   (if (utils/all-valid? spec sports-sites)
     (if (empty? sports-sites)
         (log/info "Collection contains 0 sports sites. Nothing to do!")
         (do
           (log/info "Starting to put" (count sports-sites) "records into db...")
           (db/upsert-sports-sites! db user sports-sites)
           (log/info "Done inserting data!")))
     (log/error "Invalid data, check messages in STDOUT."))))

(defn filter-valid-in [path spec coll]
  (->> coll
       (filter (comp some? #(get-in % path)))
       (remove (comp (partial s/valid? spec) #(get-in % path)))))

(defn fix-filtering-methods!
  "Updates [:water-treatment :filtering-method] to match spec."
  [db user]
  (log/info "Starting to fix swimming pool (3110 3130) filtering methods..")
  (let [data1   (core/get-sports-sites-by-type-code db 3110 {:revs "latest"})
        data2   (core/get-sports-sites-by-type-code db 3130 {:revs "latest"})
        path    [:water-treatment :filtering-methods]
        allowed (-> swimming-pools/filtering-methods keys set)
        spec    :lipas.swimming-pool.water-treatment/filtering-methods]
    (->> (concat data1 data2)
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

(defn print-usage! []
  (println)
  (println "Usage: lein run -m lipas.maintenance :task-name")
  (println)
  (println "Available tasks:")
  (doseq [s [:fix-filtering-methods
             :fix-building-materials]]
    (println s)))

(defn -main [& args]
  (let [config (select-keys backend/default-config [:db])
        system (backend/start-system! config)
        db     (:db system)
        user   (core/get-user db "import@lipas.fi")
        task   (-> args first edn/read-string)]
    (try
      (case task
        :fix-filtering-methods  (fix-filtering-methods! db user)
        :fix-building-materials (fix-building-materials! db user)
        (print-usage!))
      (finally (backend/stop-system! system)))))
