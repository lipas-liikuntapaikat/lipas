(ns lipas.search-indexer
  (:require
   [clojure.core.async :as async]
   [clojure.data.csv :as csv]
   [clojure.string :as str]
   [clojure.walk :as walk]
   [lipas.backend.analysis.diversity :as diversity]
   [lipas.backend.config :as config]
   [lipas.backend.core :as core]
   [lipas.backend.search :as search]
   [lipas.backend.system :as backend]
   [lipas.data.cities :as cities]
   [lipas.data.types :as types]
   [lipas.utils :as utils]
   [qbits.spandex :as es]
   [taoensso.timbre :as log]))

(def cities (utils/index-by :city-code cities/all))
(def types types/all)

(defn- wait-all [futures]
  (log/info "Waiting for indexing requests to get processed...")
  (doseq [f futures]
    (log/info (deref f))))

(defn- wait-one
  [future]
  (log/info "Waiting for indexing request to get processed...")
  (doto (deref future)
    (log/info)))

(defn- print-results
  [coll]
  (log/info "Total indexing results:" (apply merge-with + coll)))

(defn index-search!
  ([db client idx-name types]
   (index-search! db client idx-name types []))
  ([db client idx-name types results]
   (let [type-code (first types)]
     (log/info "Starting to re-index type" type-code)
     (if type-code
       (->> type-code
            (core/get-sports-sites-by-type-code db)
            (map core/enrich)
            (search/->bulk idx-name :lipas-id)
            (search/bulk-index! client)
            (wait-one)
            (conj results)
            (recur db client idx-name (rest types)))
       (print-results results)))))

(defn enrich-for-analytics
  [users {:keys [id document author_id status created_at]}]
  (-> document
      core/enrich
      (assoc :id id
             :doc-status status
             :author (users author_id)
             :created-at created_at)))

(defn get-users [db]
  (->> (core/get-users db)
       (map #(select-keys % [:id :email :permissions :status]))
       (utils/index-by :id)))

(defn index-analytics!
  ([db client idx-name types users]
   (index-analytics! db client idx-name types users []))
  ([db client idx-name types users results]
   (let [type-code  (first types)
         query-opts {:raw? true :revs "all"}]
     (log/info "Starting to re-index type" type-code)
     (if type-code
       (->> (core/get-sports-sites-by-type-code db type-code query-opts)
            (map (partial enrich-for-analytics users))
            (search/->bulk idx-name :id)
            (search/bulk-index! client)
            (wait-one)
            (conj results)
            (recur db client idx-name (rest types) users))
       (print-results results)))))

(defn index-analytics2!
  [db client idx-name types users]
  (let [query-opts {:raw? true :revs "all"}]
    (doseq [type-code types]
      (log/info "Starting to re-index type" type-code)
      (->> (core/get-sports-sites-by-type-code db type-code query-opts)
           (map (partial enrich-for-analytics users))
           (search/->bulk idx-name :id)
           (search/bulk-index! client)
           deref))))

(defn read-csv->maps* [path]
  (->> path
       slurp
       csv/read-csv
       utils/csv-data->maps))

(defn read-csv->maps [path]
  (->> path
       read-csv->maps*
       (map walk/keywordize-keys)))

(def dist-km 2)

(def lol-atom (atom []))

(defn index-diversity!
  [search csv-path index-name]
  (log/info "Starting to index diversity")

  (log/info "Creating index" index-name)
  #_(search/create-index! search index-name diversity/mappings)
  (log/info "Index created!")

  (log/info "Parsing grid data")
  (let [grid-data (read-csv->maps csv-path)
        on-error  prn

        {:keys [input-ch output-ch]}
        (es/bulk-chan search {:flush-threshold         100
                              :flush-interval          5000
                              :max-concurrent-requests 3})]

    (log/info "Grid data parsed. Starting to calculate and index")

    (doseq [batch (partition-all 300 grid-data)]
      (log/info "Processing new batch of 300")
      (doseq [grid batch]
        (->> (diversity/process-grid-item search dist-km grid on-error)
             (search/wrap-es-bulk index-name nil :grd_id)
             (async/put! input-ch)))

      (log/info "Waiting for batch indexing to finish...")
      (async/<!! output-ch)
      (async/<!! output-ch)
      (async/<!! output-ch))

    (log/info "Diversity indexing DONE!")))

(defn main [system db {:keys [indices client]} mode]
  (let [idx-name (str mode "-" (search/gen-idx-name))
        mappings (:sports-sites search/mappings)
        types    (keys types/all)
        alias    (case mode
                   "search"    (get-in indices [:sports-site :search])
                   "analytics" (get-in indices [:sports-site :analytics]))]
        (log/info "Starting to re-index types" types)
        (search/create-index! client idx-name mappings)
        (log/info "Created index" idx-name)
        (log/info "Starting to index data...")

        (case mode
          "search"    (index-search! db client idx-name types)
          "analytics" (let [users (get-users db)]
                        (index-analytics2! db client idx-name types users))          )

        (log/info "Indexing data done!")
        (log/info "Swapping alias" alias "to point to index" idx-name)
        (let [old-idxs (search/swap-alias! client {:new-idx idx-name :alias alias})]
          (doseq [idx old-idxs]
            (log/info "Deleting old index" idx)
            (search/delete-index! client idx)))
        (log/info "All done!")))

(defn -main [& args]
  (let [mode   (case (first args)
                 "--analytics" "analytics"
                 "--diversity" "diversity"
                 "search")
        config (select-keys config/default-config [:db :search])
        system (backend/start-system! config)
        db     (:db system)
        search (:search system)]
    (try
      (if (= "diversity" mode)
        (let [csv-path (second args)
              idx-name (str "diversity-" (search/gen-idx-name))]
          (index-diversity! (:client search) csv-path idx-name))
        (main system db search mode))
      (catch Exception ex
        (log/error ex "Something terrible happened while indexing" mode "!"))
      (finally
        (log/info "Stopping system...")
        (backend/stop-system! system)
        (log/info "System stopped. Shutting down...")
        (shutdown-agents)
        (System/exit 0)))))

(comment
  (-main)
  (-main "--analytics")
  (def config (select-keys config/default-config [:db :search]))
  (def system (backend/start-system! config))
  (def db (:db system))
  (get-user-data db)
  (def search (:search system))

  (def csv-path "/Users/tipo/lipas/aineistot/vaestoruutu_250m/vaestoruutu_250m_2020_kp.csv")

  (index-diversity! search csv-path "poni705")
  (count @lol-atom)
  (second @lol-atom)

  (def dada (core/get-sports-sites-by-type-code db 4401))
  (first dada)

  (core/enrich (-> dada first))

  (main system db search "search")
  (def user (core/get-user db "import@lipas.fi"))
  (search/delete-index! search "2018-*")
  (let [idx-name (search/gen-idx-name)
        mappings (:sports_sites search/mappings)]
    (search/create-index! search "test" (:sports-sites search/mappings))
    (search/delete-index! search "test")
    (time (-main)) ;; "Elapsed time: 74175.059697 msecs"
    (search/search search {:idx-name      "sports_sites_current"
                           :search-string "kissa*"})))
