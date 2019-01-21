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

(defn migrate-from-old-lipas! [db search user lipas-ids]
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

(defn migrate-changed-since! [db search user since]
  (let [changed (old-lipas-api/query-changed (old-lipas/UTC->last-modified since))

        ;; Get last modification dates from db for all update
        ;; candidates.
        timestamps (->> (db/get-last-modified db (map :sportsPlaceId changed))
                        (utils/index-by :lipas_id))

        updates (->>
                 ;; Filter entries where newer modifications
                 ;; exists in this system.
                 (utils/filter-newer (utils/index-by :sportsPlaceId changed)
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

        ignores     (set/difference (set changed) (set updates))
        sites       (->> updates
                         (map (partial old-lipas/->sports-site))
                         (map utils/clean))
        valid-sites (filter (partial utils/validate-noisy :lipas/sports-site) sites)
        invalid     (set/difference (set sites) (set valid-sites))
        idx-name    "sports_sites_current"]

    (db/upsert-sports-sites! db user valid-sites)

    (->> valid-sites
         (search/->bulk idx-name :lipas-id)
         (search/bulk-index! search))

    {:total   (count changed)
     :updated (map :lipas-id valid-sites)
     :failed  (map :lipas-id invalid)
     :ignored (map :sportsPlaceId ignores)
     ;; Timestamp from where we will query next time.
     :latest  (->> changed
                   (map :lastModified)
                   sort
                   last
                   old-lipas/last-modified->UTC)}))

(defn migrate-users! [db fpath]
  (let [users (-> fpath slurp read-string)]
    (doseq [user  users]
      (try
        (core/add-user! db user)
        (log/info "Added user" (:email user))
        (catch Exception e
          (log/error "Adding user" (:email user) "failed!" e))))))

(defn -main [& args]
  (let [source              (first args)
        config              (select-keys config/default-config [:db])
        {:keys [db search]} (backend/start-system! config)
        user                (core/get-user db "import@lipas.fi")]
    (case source
      "--old-lipas" (migrate-from-old-lipas! db search user (rest args))
      "--es-dump"   (migrate-from-es-dump! db user
                                           (first (rest args))
                                           (second (rest args)))
      "--users"     (migrate-users! db (second args))
      (log/error "Please provide --es-dump dump-path err-path or
      --old-lipas 123 234 ..."))))

(comment
  (-main "--lipas" "529736")
  (def fpath "/Users/vaotjuha/lipas/data_migration/2018-10-19-data.json")
  (def d (slurp "/Users/vaotjuha/lipas/data_migration/2018-10-19-data.json"))
  (ns-unmap *ns* 'd)
  (with-open [rdr (clojure.java.io/reader fpath)]
    (doseq [line  (line-seq rdr)
            :let  [m (:_source (json/decode line true))
                   d (es-dump->sports-site m)]
            :when (not (#{2510 2520 3110 3130} (-> m :type :typeCode)))]
      (when-not (spec/valid? :lipas/sports-site d)
        (spec/explain :lipas/sports-site d))))

  (def config (select-keys config/default-config [:db :search]))
  (def system (backend/start-system! config))
  (def db (:db system))
  (def search (:search system))
  (def user (core/get-user db "import@lipas.fi"))

  (migrate-from-old-lipas! db search user [72348])

  (migrate-users db "/Users/vaotjuha/lipas/data_migration/2019-01-09-lipas-users-filtered.edn")

  (def sites (db/get-sports-sites-modified-since db "2018-12-14T00:00:00.000"))
  (->> sites
       (map ->old-lipas-sports-site))

  (migrate-from-es-dump! db user fpath "/tmp/invalid/")

  (->> (query-changed "2018-12-14 00:00:00.000" [] 1)
       (remove (comp #{3110 3130 2510 2520} :typeCode :type)))

  (db/get-last-modified db [74538])

  (query [] "http://lipas.cc.jyu.fi/api/sports-places"
         {:modifiedAfter "2018-12-14 00:00:00.000"
          :pageSize      100
          :fields        ["lastModified"]}))
