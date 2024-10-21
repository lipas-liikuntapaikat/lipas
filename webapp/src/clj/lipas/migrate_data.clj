(ns lipas.migrate-data
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.pprint :as pprint]
   [clojure.set :as set]
   [clojure.spec.alpha :as spec]
   [lipas.backend.config :as config]
   [lipas.backend.core :as core]
   [lipas.backend.db.db :as db]
   [lipas.backend.search :as search]
   [lipas.backend.system :as backend]
   [lipas.integration.old-lipas.api :as old-lipas-api]
   [lipas.integration.old-lipas.transform :as old-lipas]
   [lipas.schema.core]
   [lipas.utils :as utils]
   [taoensso.timbre :as log]))

;; Lipas-data is fetched from JSON REST-API

(defn migrate-from-old-lipas!
  "Fetches sites one by one from old Lipas and upserts to db without any
  timestamp checks."
  [db search user lipas-ids]
  (log/info "Starting to migrate sports-sites" lipas-ids)
  (doseq [lipas-id lipas-ids]
    (let [data (->> lipas-id
                    old-lipas-api/get
                    old-lipas/->sports-site
                    utils/clean)
          spec :lipas/sports-site]
      (if (spec/valid? spec data)
        (do
          (->> (db/upsert-sports-site! db user data)
               (core/index! search))
          (log/info "Successfully migrated" lipas-id))
        (do
          (spec/explain spec data)
          (log/error "Failed to migrate lipas-id" lipas-id))))))

(defn save-invalid! [m path]
  (with-open [w (clojure.java.io/writer (str path (:lipas-id m)))]
    (binding [*out* w]
      (pprint/write (spec/explain :lipas/sports-site m))
      (pprint/write m))))

(defn migrate-from-es-dump! [db user fpath err-path]
  (io/make-parents (str err-path "foo"))
  (with-open [rdr (clojure.java.io/reader fpath)]
    (doseq [l     (line-seq rdr)
            :let  [m (:_source (json/decode l true))]
            :when (not (#{2510 2520 3110 3130} (-> m :type :typeCode)))]
      (let [data (old-lipas/es-dump->sports-site m)]
        (if (utils/validate-noisy :lipas/sports-site data)
          (db/upsert-sports-site! db user data)
          ;;(throw (ex-info "Invalid site" {:data data}))
          (save-invalid! data err-path))))))

(defn- migrate-changed!
  "Migrates modifications from data if they're newer than data in db."
  [db {:keys [indices client] :as _search} user data]
  (let [;; Get last modification dates from db for all update
        ;; candidates.
        timestamps (->> (db/get-last-modified db (map :sportsPlaceId data))
                        (utils/index-by :lipas_id))

        updates (->>
                 ;; Filter entries where newer modifications
                 ;; exists in this system.
                 (utils/filter-newer (utils/index-by :sportsPlaceId data)
                                     #(-> % :lastModified old-lipas/last-modified->UTC)
                                     timestamps
                                     #(when-let [ts (:created_at %)]
                                        (-> ts .toInstant str)))

                 vals

                 ;; Ignore swimming pools and ice stadiums
                 ;; because this system has richer data model
                 ;; for them and we can't integrate data in a
                 ;; sensible way.
                 (remove (comp #{3110 3130 2510 2520} :typeCode :type)))

        ignores     (set/difference (set data) (set updates))
        sites       (->> updates
                         (map (partial old-lipas/->sports-site))
                         (map utils/clean))
        valid-sites (filter (partial utils/validate-noisy :lipas/sports-site) sites)
        invalid     (set/difference (set sites) (set valid-sites))
        idx-name    (get-in indices [:sports-site :search])]

    (db/upsert-sports-sites! db user valid-sites)

    (->> valid-sites
         (map core/enrich)
         (search/->bulk idx-name :lipas-id)
         (search/bulk-index! client))

    {:total   (count data)
     :updated (map :lipas-id valid-sites)
     :failed  (map :lipas-id invalid)
     :ignored (map :sportsPlaceId ignores)
     ;; Timestamp from where we will query next time.
     :latest  (->> data
                   (map :lastModified)
                   sort
                   last
                   old-lipas/last-modified->UTC)}))

(defn migrate-changed-since! [db search user since]
  (let [data (old-lipas-api/query-changed (old-lipas/UTC->last-modified since))]
    (migrate-changed! db search user data)))

(defn migrate-changed-by-ids! [db search user ids]
  (let [data (old-lipas-api/query-all-data ids)]
    (migrate-changed! db search user data)))

(defn migrate-changed-by-ids-edn [db search user fpath]
  (log/info "Starting to migrate changed data by lipas ids from " fpath)
  (let [ids (->> fpath slurp read-string (map (comp utils/->int :lipas-id)))]
    (->> (migrate-changed-by-ids! db search user ids)
         log/info)))

(defn migrate-users! [db fpath]
  (let [users (-> fpath slurp read-string)]
    (doseq [user  users]
      (try
        (core/add-user! db user)
        (log/info "Added user" (:email user))
        (catch Exception e
          (log/error "Adding user" (:email user) "failed!" e))))))

(defn migrate-city-data! [db fpath]
  (let [city-stats (-> fpath slurp read-string)]
    (doseq [[city-code stats] city-stats]
      (try
        (db/add-city! db {:city-code city-code :stats stats})
        (log/info "City data added for" city-code)
        (catch Exception e
          (log/error "Adding stats for" city-code "failed!" e))))))

(defn -main [& args]
  (let [source              (first args)
        config              (select-keys config/default-config [:db :search])
        {:keys [db search]} (backend/start-system! config)
        user                (core/get-user db "import@lipas.fi")]

    (log/info "Starting to run" source "with args" (rest args))
    (log/info
     (case source
       "--old-lipas"       (migrate-from-old-lipas! db search user (rest args))
       "--old-lipas-edn"   (migrate-changed-by-ids-edn db search user (second args))
       "--old-lipas-since" (migrate-changed-since! db search user (second args))
       "--es-dump"         (migrate-from-es-dump! db user
                                                  (first (rest args))
                                                  (second (rest args)))
       "--users"           (migrate-users! db (second args))
       "--city-data"       (migrate-city-data! db (second args))
       (log/error "Please provide --es-dump dump-path err-path or
      --old-lipas 123 234 ...")))))

(comment
  (def config (select-keys config/default-config [:db :search]))
  (def system (backend/start-system! config))
  (def db (:db system))
  (def search (:search system))
  (def user (core/get-user db "import@lipas.fi"))
  (-main "--old-lipas-since" "2019-01-01T00:00:00.000Z")
  (-main "--city-data" "/Users/vaotjuha/lipas/raportit/city_stats5.edn")
  (-main "--old-lipas" "527986")
  (-main "--old-lipas-edn" "/Users/vaotjuha/lipas/data_migration/missing-ids.edn"))
