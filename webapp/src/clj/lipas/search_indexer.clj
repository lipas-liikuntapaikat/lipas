(ns lipas.search-indexer
  (:require [lipas.backend.config :as config]
            [lipas.backend.core :as core]
            [lipas.backend.search :as search]
            [lipas.backend.system :as backend]
            [lipas.data.cities :as cities]
            [lipas.data.types :as types]
            [lipas.utils :as utils]
            [taoensso.timbre :as log]))

(def cities (utils/index-by :city-code cities/all))
(def types types/all)

(defn index-all!
  ([db search user idx-name types]
   (index-all! db search user idx-name types []))
  ([db search user idx-name types futures]
   (let [type-code (first types)]
     (log/info "Starting to re-index type" type-code)
     (if type-code
       (->> type-code
            (core/get-sports-sites-by-type-code db)
            (map core/enrich)
            (search/->bulk idx-name :lipas-id)
            (search/bulk-index! search)
            (conj futures)
            (recur db search user idx-name (rest types)))
       (do
         (log/info "Waiting for indexing requests to get processed...")
         (doseq [f futures]
          (deref f)))))))

(defn -main [& args]
  (let [config (select-keys config/default-config [:db :search])
        system (backend/start-system! config)
        db     (:db system)
        search (:search system)
        user   (core/get-user db "import@lipas.fi")]
    (try
      (let [idx-name (search/gen-idx-name)
            mappings (:sports-sites search/mappings)
            types    (keys types/all)
            alias    "sports_sites_current"]
        (log/info "Starting to re-index types" types)
        (search/create-index! search idx-name mappings)
        (log/info "Created index" idx-name)
        (log/info "Starting to index data...")
        (index-all! db search user idx-name types)
        (log/info "Indexing data done!")
        (log/info "Swapping alias" alias "to point to index" idx-name)
        (let [old-idxs (search/swap-alias! search {:new-idx idx-name :alias alias})]
          (doseq [idx old-idxs]
            (log/info "Deleting old index" idx)
            (search/delete-index! search idx)))
        (log/info "All done!"))
      (finally (backend/stop-system! system)))))

(comment
  (-main)
  (def config (select-keys config/default-config [:db :search]))
  (def system (backend/start-system! config))
  (def db (:db system))
  (def search (:search system))
  (def user (core/get-user db "import@lipas.fi"))
  (search/delete-index! search "2018-*")
  (let [idx-name (search/gen-idx-name)
        mappings (:sports_sites search/mappings)]
    (search/create-index! search "test" (:sports-sites search/mappings))
    (index-all! db search user "test" #{1180})
    (index-all! db search user "test" #{4404})
    (search/delete-index! search "test")
    (time (-main)) ;; "Elapsed time: 74175.059697 msecs"
    (search/search search {:idx-name      "sports_sites_current"
                           :search-string "kissa*"})))
