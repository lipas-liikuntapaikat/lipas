(ns lipas.maintenance
  (:require [clojure.data.csv :as csv]
            [clojure.edn :as edn]
            [clojure.spec.alpha :as s]
            [clojure.string :as string]
            [lipas.backend.core :as core]
            [lipas.backend.db.db :as db]
            [lipas.backend.system :as backend]
            [lipas.data.materials :as materials]
            [lipas.data.swimming-pools :as swimming-pools]
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

(defn fix-filtering-methods!
  "Updates [:water-treatment :filtering-method] to match spec."
  [db user & args]
  (log/info "Starting to fix swimming pool (3110 3130) filtering methods..")
  (let [data-3110 (core/get-sports-sites-by-type-code db 3110 {:revs "yearly"})
        data-3130 (core/get-sports-sites-by-type-code db 3130 {:revs "yearly"})
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
  [db user & args]
  (log/info "Starting to fix swimming pool (3110 3130) building materials..")
  (let [data-3110 (core/get-sports-sites-by-type-code db 3110 {:revs "yearly"})
        data-3130 (core/get-sports-sites-by-type-code db 3130 {:revs "yearly"})
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
  [db user & args]
  (log/info "Starting to fix swimming pool (3110 3130) ceiling structures..")
  (let [data-3110 (core/get-sports-sites-by-type-code db 3110 {:revs "yearly"})
        data-3130 (core/get-sports-sites-by-type-code db 3130 {:revs "yearly"})
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
  [db user & args]
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

(defn heat-source->heat-sources!
  "Creates new field [:building :heat-sources [...]] and migrates values
  from old field [:building :heat-source]. Old field is removed."
  [db user & args]
  (log/info "Starting to fix swimming pool (3110 3130) heat sources..")
  (let [data-3110 (core/get-sports-sites-by-type-code db 3110 {:revs "yearly"})
        data-3130 (core/get-sports-sites-by-type-code db 3130 {:revs "yearly"})]
    (->> (concat data-3110 data-3130)
         (filter (comp some? :heat-source :building))
         (map #(assoc-in % [:building :heat-sources] [(-> % :building :heat-source)]))
         (map #(update-in % [:building] dissoc :heat-source))
         (upsert-all! db user :lipas.sports-site/swimming-pool))))

(def months
  {"01" :jan
   "02" :feb
   "03" :mar
   "04" :apr
   "05" :may
   "06" :jun
   "07" :jul
   "08" :aug
   "09" :sep
   "10" :oct
   "11" :nov
   "12" :dec})

(def path-parts
  {"LÄMPÖ"        {:root :energy-consumption-monthly :leaf :heat-mwh}
   "SÄHKÖ"        {:root :energy-consumption-monthly :leaf :electricity-mwh}
   "VESI"         {:root :energy-consumption-monthly :leaf :water-m3}
   "KÄYTTÖTUNNIT" {:root :energy-consumption-monthly :leaf :operating-hours}
   "KATSOJAT"     {:root :visitors-monthly :leaf :spectators-count}
   "KÄYTTÄJÄT"    {:root :visitors-monthly :leaf :total-count}})

(defn- append-monthly-entries [year new-rev entries]
  (reduce (fn [rev entry]
            (let [type  (get entry "Laji")
                  v     (-> entry
                            (get "Kulutus")
                            utils/->int
                            (as-> $ (case type ; KWh->MWh
                                      ("LÄMPÖ" "SÄHKÖ") (double (/ $ 1000))
                                      $)))
                  month (get months (-> entry
                                        (get "Kuukausi")
                                        (string/split #"/")
                                        second))
                  parts (get path-parts type)
                  path  [(:root parts) month (:leaf parts)]]
              (assoc-in rev path v)))
          new-rev
          entries))

(defn- yearly-sum [k m]
  (let [entries (map k m)]
    ;; Sum only if there's data for all months
    (when (= 12 (count (filter some? entries)))
      (-> (reduce utils/+safe (map #(* 1000 %) entries))
          (/ 1000)
          (+ 0.5) ; rounding fix
          int)))) ; flooring

(defn- calculate-totals [rev]
  (let [monthly-e-vals (-> rev :energy-consumption-monthly vals)
        monthly-v-vals (-> rev :visitors-monthly vals)

        electricity-mwh  (yearly-sum :electricity-mwh monthly-e-vals)
        heat-mwh         (yearly-sum :heat-mwh monthly-e-vals)
        water-m3         (yearly-sum :water-m3 monthly-e-vals)
        operating-hours  (yearly-sum :operating-hours monthly-e-vals)
        spectators-count (yearly-sum :spectators-count monthly-v-vals)
        total-count      (yearly-sum :total-count monthly-v-vals)]

    (cond-> rev
      (some? electricity-mwh)  (assoc-in [:energy-consumption :electricity-mwh] electricity-mwh)
      (some? heat-mwh)         (assoc-in [:energy-consumption :heat-mwh] heat-mwh)
      (some? water-m3)         (assoc-in [:energy-consumption :water-m3] water-m3)
      (some? operating-hours)  (assoc-in [:energy-consumption :operating-hours] operating-hours)
      (some? spectators-count) (assoc-in [:visitors :spectators-count] spectators-count)
      (some? total-count)      (assoc-in [:visitors :total-count] total-count))))

(defn- make-revs [lipas-revs monthly-entries]
  (let [revs-by-year    (utils/index-by (comp #(subs % 0 4) :event-date) lipas-revs)
        entries-by-year (group-by (comp
                                   first ;; yyyy/MM/dd
                                   #(string/split % #"/")
                                   #(get % "Kuukausi"))
                                  monthly-entries)]
    (for [[year entries] entries-by-year
          :let           [rev (or (get revs-by-year year)
                                  (-> lipas-revs
                                      first
                                      (dissoc :energy-consumption
                                              :energy-consumption-monthly
                                              :visitors
                                              :visitors-monthly)))
                          event-date (str year "-12-31T23:59:59.999Z")
                          new-rev (assoc rev :event-date event-date)]]
      (->> entries
           (append-monthly-entries year new-rev)
           calculate-totals))))

(defn append-monthly-readings!
  "Appends monthly readings from csv to corresponding ice stadiums (2510
  2520) revisions under fields :energy-consumption-monthly
  and :visitors-monthly and calculates corresponding yearly
  consumptions under :energy-consumption and :visitors."
  [db user & args]
  (when-not (first args)
    (throw (ex-info "Please provide csv-url as argument!" {})))
  (let [csv-url  (first args)
        csv-data (-> csv-url
                     slurp
                     csv/read-csv
                     utils/csv-data->maps)

        monthly-data (group-by (comp utils/->int #(get % "Lipas-ID")) csv-data)

        lipas-ids (into #{}
                        (map (comp utils/->int #(get % "Lipas-ID")) csv-data))

        data-2510 (core/get-sports-sites-by-type-code db 2510 {:revs "yearly"})
        data-2520 (core/get-sports-sites-by-type-code db 2520 {:revs "yearly"})

        lipas-data (->> (concat data-2510 data-2520)
                        (filter (comp lipas-ids :lipas-id))
                        (group-by :lipas-id))

        revs (for [lipas-id lipas-ids
                   :let     [lipas-revs (get lipas-data lipas-id)
                             monthly-entries (get monthly-data lipas-id)]]
               (make-revs lipas-revs monthly-entries))]
    (->> revs
         flatten
         (upsert-all! db user :lipas.sports-site/ice-stadium))))

(def tasks
  {:fix-filtering-methods     fix-filtering-methods!
   :fix-building-materials    fix-building-materials!
   :fix-ceiling-structures    fix-ceiling-structures!
   :fix-pool-types            fix-pool-types!
   :heat-source->heat-sources heat-source->heat-sources!
   :append-monthly-readings   append-monthly-readings!})

(defn print-usage! []
  (println "\nUsage: lein run -m lipas.maintenance :task-name\n")
  (println "Available tasks:")
  (doseq [task (keys tasks)]
    (println task)))

(defn run-task! [task-fn args]
  (let [config   (select-keys backend/default-config [:db])
        system   (backend/start-system! config)
        db       (:db system)
        user     (core/get-user db "import@lipas.fi")]
    (try
      (apply task-fn (into [db user] args))
      (finally (backend/stop-system! system)))))

(defn -main [& args]
  (let [task-key (-> args first edn/read-string)
        args     (rest args)]
    (if-let [task (get tasks task-key)]
      (run-task! task args)
      (print-usage!))))

(comment
  (-main ":fix-kissa")
  (-main ":fix-pool-types")
  (-main ":fix-ceiling-structures")
  (-main ":heat-source->heat-sources")
  (-main ":append-monthly-readings" (str "/Users/vaotjuha/Dropbox/Public/"
                                         "2018-10-04-jaahallien-kk-kulutukset.csv")))
