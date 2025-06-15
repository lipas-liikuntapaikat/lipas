(ns lipas.test-utils
  (:require [cheshire.core :as j]
            [clojure.java.jdbc :as jdbc]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.string :as str]
            [cognitect.transit :as transit]
            [lipas.backend.analysis.diversity :as diversity]
            [lipas.backend.config :as config]
            [lipas.backend.core :as core]
            [lipas.backend.email :as email]
            [lipas.backend.search :as search]
            [lipas.backend.system :as sy]
            [lipas.schema.core]
            [lipas.utils :as utils]
            [migratus.core :as migratus]
            [ring.mock.request :as mock])
  (:import [java.io ByteArrayOutputStream]
           java.util.Base64))

(defn gen-sports-site
  []
  (try
    ;; FIXME :ptv generators produce difficult values
    (-> (s/gen :lipas/sports-site)
        gen/generate
        (dissoc :ptv))
    (catch Throwable _t (gen-sports-site))))

(def <-json
  (fn [response-body]
    (cond
      (string? response-body)
      (if (empty? response-body)
        nil
        (j/parse-string response-body true))

      (instance? java.io.InputStream response-body)
      (j/parse-string (slurp response-body) true)

      :else
      (j/parse-string (slurp response-body) true))))
(def ->json j/generate-string)

(defn ->transit [x]
  (let [out (ByteArrayOutputStream. 4096)
        writer (transit/writer out :json)]
    (transit/write writer x)
    (.toString out)))

(defn <-transit [in]
  (let [reader (transit/reader in :json)]
    (transit/read reader)))

(defn ->base64
  "Encodes a string as base64."
  [s]
  (.encodeToString (Base64/getEncoder) (.getBytes s)))

(defn auth-header
  "Adds Authorization header to the request
  with base64 encoded \"Basic user:pass\" value."
  [req user passwd]
  (mock/header req "Authorization" (str "Basic " (->base64 (str user ":" passwd)))))

(defn token-header
  [req token]
  (mock/header req "Authorization" (str "Token " token)))

(defn- test-suffix [s] (str s "_test"))

(def config (-> config/default-config
                (select-keys [:db :app :search :mailchimp :aws :ptv])
                (assoc-in [:app :emailer] (email/->TestEmailer))
                (update-in [:db :dbname] test-suffix)
                (assoc-in [:db :dev] true) ;; No connection pool
                (update-in [:search :indices :sports-site :search] test-suffix)
                (update-in [:search :indices :sports-site :analytics] test-suffix)
                (update-in [:search :indices :report :subsidies] test-suffix)
                (update-in [:search :indices :report :city-stats] test-suffix)
                (update-in [:search :indices :analysis :schools] test-suffix)
                (update-in [:search :indices :analysis :population] test-suffix)
                (update-in [:search :indices :analysis :population-high-def] test-suffix)
                (update-in [:search :indices :analysis :diversity] test-suffix)
                (update-in [:search :indices :lois :search] test-suffix)))

;; Enhanced database initialization with migration status checking
(defn init-db!
  "Initialize test database with all extensions and current migrations.
   Optionally accepts a custom db config."
  ([]
   (init-db! (:db config)))
  ([db-config]
   (let [migratus-opts {:store :database
                        :migration-dir "migrations"
                        :db db-config}]
     ;; Create database if it doesn't exist
     (try
       (jdbc/db-do-commands (-> db-config (assoc :dbname ""))
                            false
                            [(str "CREATE DATABASE " (:dbname db-config))])
       (catch Exception e
         (when-not (str/includes? (-> e .getCause .getMessage str/lower-case)
                                  "already exists")
           (throw e))))

     ;; Install extensions
     (jdbc/db-do-commands db-config
                          false
                          [(str "CREATE EXTENSION IF NOT EXISTS postgis")
                           (str "CREATE EXTENSION IF NOT EXISTS postgis_topology")
                           (str "CREATE EXTENSION IF NOT EXISTS citext")
                           (str "CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"")])

     ;; Initialize and run migrations
     (try
       (migratus/init migratus-opts)
       (let [pending (migratus/pending-list migratus-opts)]
         (when (seq pending)
           (println "Running migrations:" (map :id pending)))
         (migratus/migrate migratus-opts)
         {:status :migrated :pending-count (count pending)})
       (catch Exception e
         (println "Migration failed:" (.getMessage e))
         (throw e))))))

(def tables
  ["account"
   "analysis_queue"
   "city"
   "elevation_queue"
   "email_out_queue"
   "integration_log"
   "integration_out_queue"
   "jobs"
   "reminder"
   "sports_site"
   "subsidy"
   "loi"])

;; For all other tests except the legacy WFS compatibility layer
;; Enhanced database utilities that accept db parameter
(defn prune-db!
  "Truncate all tables. Optionally accepts a custom db connection."
  ([]
   (prune-db! (:db config)))
  ([db-connection]
   (jdbc/execute! db-connection [(str "TRUNCATE "
                                      (str/join "," tables)
                                      " RESTART IDENTITY CASCADE")])))

(defn check-migration-status
  "Check if migrations are up to date by attempting migration (idempotent)."
  ([]
   (check-migration-status (:db config)))
  ([db-config]
   (let [migratus-opts {:store :database
                        :migration-dir "migrations"
                        :db db-config}]
     (try
       ;; This is idempotent - won't re-run completed migrations
       (migratus/migrate migratus-opts)
       {:up-to-date? true :status :checked}
       (catch Exception ex
         {:up-to-date? false :error (str ex)})))))

(defn ensure-test-database!
  "Comprehensive test database setup: create, migrate, and verify.
   Returns database connection details."
  ([]
   (ensure-test-database! (:db config)))
  ([db-config]
   (println "Setting up test database...")
   (let [init-result (init-db! db-config)
         migration-status (check-migration-status db-config)]
     (when-not (:up-to-date? migration-status)
       (throw (ex-info "Database migrations not up to date after init!"
                       migration-status)))

     ;; Verify that required tables actually exist
     (try
       ;; First check if we can connect to the database
       (let [connection-test (jdbc/query db-config ["SELECT 1"])]
         (println "✓ Database connection successful"))

       ;; Then check for the account table specifically
       (let [table-check (jdbc/query db-config
                                     ["SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'account'"])]
         (if (empty? table-check)
           (throw (ex-info "Account table not found in information_schema" {:db-config (dissoc db-config :password)}))
           (println "✓ Account table exists in schema")))

       ;; Finally, try to query the account table
       (jdbc/query db-config ["SELECT 1 FROM account LIMIT 1"])
       (println "✓ Database setup successful - account table verified")
       (catch Exception e
         (println "✗ Database verification failed:" (.getMessage e))
         (println "Database config (without password):" (dissoc db-config :password))
         (println "Init result:" init-result)
         (println "Migration status:" migration-status)
         ;; Additional debugging - list all tables
         (try
           (let [all-tables (jdbc/query db-config
                                        ["SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name"])]
             (println "Available tables:" (mapv :table_name all-tables)))
           (catch Exception debug-e
             (println "Could not list tables:" (.getMessage debug-e))))
         (throw (ex-info "Required tables not found after migration - database setup incomplete!"
                         {:error (.getMessage e)
                          :init-result init-result
                          :migration-status migration-status
                          :db-config (dissoc db-config :password)}))))

     {:db-config db-config
      :migrations-applied (:status init-result)
      :status :ready})))

(def wfs-up-ddl
  (slurp (io/resource "migrations/20250209182407-legacy-wfs.up.sql")))

;; For the legacy WFS compatibility layer tests
(defn prune-wfs-schema! []
  (jdbc/execute! (:db config) [(str "DROP SCHEMA IF EXISTS wfs CASCADE;"
                                    wfs-up-ddl)]))

;; Likely all test system components are stateless, so maybe ok to start without ever halting this.
;; But take steps to also stop the system before starting it again on each reload of this ns.
(defonce system nil)

(alter-var-root #'system (fn [x]
                           (when x
                             (sy/stop-system! x))
                           (sy/start-system! (config/->system-config config))))

;; These need to be redefined after each alter-var-root.
(def db (:lipas/db system))
(def app (:lipas/app system))
(def search (:lipas/search system))

(defn prune-es! []
  (let [client (:client search)
        mappings {(-> search :indices :sports-site :search) (:sports-sites search/mappings)
                  (-> search :indices :analysis :diversity) diversity/mappings
                  (-> search :indices :lois :search) (:lois search/mappings)}]

    (doseq [idx-name (-> search :indices vals (->> (mapcat vals)))]
      (try
        (search/delete-index! client idx-name)
        (catch Exception ex
          (when (not= "index_not_found_exception"
                      (-> ex ex-data :body :error :root_cause first :type))
            (throw ex))))
      (when-let [mapping (mappings idx-name)]
        (search/create-index! client idx-name mapping)))))

(comment
  (init-db!)
  (prune-es!)
  (prune-db!)
  (ex-data *e))

(defn gen-user
  ([]
   (gen-user {:db? false :admin? false :status "active"}))
  ([{:keys [db? admin? status]
     :or {admin? false status "active"}}]
   (let [user (-> (gen/generate (s/gen :lipas/user))
                  (assoc :password (str (gensym)) :status status)
                  ;; Ensure :permissions is a map always, generate doesn't always add the key because it it is optional in
                  ;; the :lipas/user spec but required e.g. update-user-permissions endpoint.
                  (update :permissions (fn [permissions]
                                         (cond-> (or permissions {})
                                           ;; generated result might include admin role, remove if the flag is false
                                           (not admin?) (update :roles (fn [roles]
                                                                         (into [] (remove (fn [x] (= :admin (:role x))) roles))))
                                           admin? (update :roles (fnil conj []) {:role :admin})))))]
     (if db?
       (do
         (core/add-user! db user)
         (assoc user :id (:id (core/get-user db (:email user)))))
       user))))

(defn gen-loi! []
  (-> (gen/generate (s/gen :lipas.loi/document))
      (assoc :status "active")
      (assoc :id (str (java.util.UUID/randomUUID)))))

(comment
  (every? #(s/valid? :lipas/sports-site %) (repeatedly 100 gen-sports-site)))
