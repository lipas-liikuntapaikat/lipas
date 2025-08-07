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
            [lipas.schema.sports-sites :as sports-site-schema]
            [malli.core :as m]
            [malli.generator :as mg]
            [lipas.utils :as utils]
            [migratus.core :as migratus]
            [ring.mock.request :as mock]
            [integrant.core :as ig]
            [clojure.test :as t])
  (:import [java.io ByteArrayOutputStream]
           java.util.Base64))

(declare gen-user)

#_(defn gen-sports-site
  []
  (try
    ;; FIXME :ptv generators produce difficult values
    (-> (s/gen :lipas/sports-site)
        gen/generate
        (dissoc :ptv))
    (catch Throwable _t (gen-sports-site))))

(defn fix-generated-site [site]
  (cond-> site
    (:renovation-years site) (update :renovation-years (fn [years]
                                                         (->> years
                                                              distinct
                                                              (filter #(>= % 1900))
                                                              vec)))
    (:reservations-link site) (update :reservations-link #(subs % 0 (min (count %) 200)))
    (:www site) (update :www #(subs % 0 (min (count %) 200)))
    (get-in site [:location :postal-office]) (update-in [:location :postal-office] #(subs % 0 (min (count %) 50)))))

(defn gen-sports-site
  []
  (fix-generated-site (mg/generate sports-site-schema/sports-site)))

(defn gen-sports-site-with-type
  [type-code]
  (let [child-schemas (m/children sports-site-schema/sports-site)
        entry (first (filter #(= (first %) type-code) child-schemas))
        schema (last entry)]
    (fix-generated-site (mg/generate schema))))

(defn create-test-sites!
  "Creates test sports sites with optional customizations.

   Options:
   - :count       - Number of sites to create (default: 3)
   - :city-codes  - Vector of city codes to assign
   - :type-codes  - Vector of type codes to assign
   - :save?       - Save to database (default: true)
   - :index?      - Index to Elasticsearch (default: true)
   - :customize-fn - Function to customize each site

   Returns vector of created sites.

   Example:
   (create-test-sites! db search
                       :count 5
                       :city-codes [91 49]
                       :customize-fn #(assoc % :name \"Test Site\"))"
  [db search & {:keys [count city-codes type-codes save? index? customize-fn]
                :or {count 3 save? true index? true}}]
  (let [admin (gen-user {:db? true :admin? true})
        sites (for [i (range count)]
                (let [base-site (if (and type-codes (seq type-codes))
                                  (gen-sports-site-with-type (nth type-codes (mod i (clojure.core/count type-codes))))
                                  (gen-sports-site))
                      site (-> base-site
                               (assoc :lipas-id (inc i))
                               (cond->
                                 city-codes
                                 (assoc-in [:location :city :city-code]
                                           (nth city-codes (mod i (clojure.core/count city-codes))))

                                 customize-fn
                                 customize-fn))]
                  site))]
    (when save?
      (doseq [site sites]
        (core/upsert-sports-site!* db admin site)))
    (when index?
      (doseq [site sites]
        (core/index! search site :sync)))
    sites))

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
                (update-in [:search :indices :lois :search] test-suffix)
                (update-in [:search :indices :legacy-sports-site :search] test-suffix)))

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
   "org"
   "job_metrics"
   "dead_letter_jobs"
   "circuit_breakers"
   "versioned_data"
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

(defn prune-es!
  ([] (prune-es! search))
  ([search]
   (let [client (:client search)
         mappings {(-> search :indices :sports-site :search) (:sports-sites search/mappings)
                   (-> search :indices :legacy-sports-sites :search) (:legacy-sports-sites search/mappings)
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
         (search/create-index! client idx-name mapping))))))

(comment
  (init-db!)
  (prune-es!)
  (prune-db!)
  (ex-data *e))

(defn gen-user
  ([]
   (gen-user {:db? false :admin? false :status "active"}))
  ([{:keys [db? admin? status permissions db-component]
     :or {admin? false status "active"}}]
   (let [user (-> (gen/generate (s/gen :lipas/user))
                  (assoc :password (str (gensym)) :status status)
                  ;; Ensure :permissions is a map always, generate doesn't always add the key because it it is optional in
                  ;; the :lipas/user spec but required e.g. update-user-permissions endpoint.
                  (update :permissions (fn [generated-permissions]
                                         (cond-> (or generated-permissions {})
                                           ;; If custom permissions provided, use them instead of generated ones
                                           permissions (merge permissions)
                                           ;; generated result might include admin role, remove if the flag is false
                                           (not admin?) (update :roles (fn [roles]
                                                                         (into [] (remove (fn [x] (= :admin (:role x))) roles))))
                                           admin? (update :roles (fnil conj []) {:role :admin})))))]
     (if db?
       (let [db-conn (or db-component db)]
         (core/add-user! db-conn user)
         (assoc user :id (:id (core/get-user db-conn (:email user)))))
       user))))

(defn gen-loi! []
  (-> (gen/generate (s/gen :lipas.loi/document))
      (assoc :status "active")
      (assoc :id (str (java.util.UUID/randomUUID)))))

(comment
  (every? #(s/valid? :lipas/sports-site %) (repeatedly 100 gen-sports-site)))

;; ========================================
;; User Generators
;; ========================================

(defn gen-admin-user
  "Generate a user with admin permissions, optionally saved to DB"
  [& {:keys [db?] :or {db? true} :as opts}]
  (gen-user (merge opts {:db? db? :admin? true})))

(defn gen-regular-user
  "Generate a regular user with default permissions"
  [& {:keys [db?] :or {db? true} :as opts}]
  (gen-user (merge opts {:db? db? :admin? false})))

(defn gen-city-manager-user
  "Generate a city manager user for specific city"
  [city-code & {:keys [db?] :or {db? true} :as opts}]
  (gen-user (merge opts {:db? db?
                         :permissions {:roles [{:role "city-manager"
                                                :city-code [city-code]}]}})))

(defn gen-site-manager-user
  "Generate a site manager user for specific sports site"
  [lipas-id & {:keys [db?] :or {db? true} :as opts}]
  (gen-user (merge opts {:db? db?
                         :permissions {:roles [{:role "site-manager"
                                                :lipas-id [lipas-id]}]}})))

(defn gen-org-admin-user
  "Generate an organization admin for specific org"
  [org-id & {:keys [db?] :or {db? true} :as opts}]
  (gen-user (merge opts {:db? db?
                         :permissions {:roles [{:role "org-admin"
                                                :org-id [(str org-id)]}]}})))

(defn gen-ptv-auditor
  "Generate a PTV auditor user"
  [& {:keys [db?] :or {db? true} :as opts}]
  (gen-user (merge opts {:db? db?
                         :permissions {:roles [{:role :ptv-auditor}]}})))

;; ========================================
;; Response Helpers
;; ========================================

(defn safe-parse-json
  "Safely parse JSON response body, returning nil on error"
  [resp]
  (try
    (<-json (:body resp))
    (catch Exception _ nil)))

(defn assert-response
  "Assert response status and optionally validate body"
  [resp expected-status & {:keys [message validator]}]
  (t/is (= expected-status (:status resp)) (or message (str "Response status should be " expected-status)))
  (when validator
    (t/is (validator (safe-parse-json resp)))))

;; ========================================
;; System Setup
;; ========================================

(defn with-test-system!
  "Setup and teardown full Integrant test system.
   Calls f with the system atom as argument.

   Usage:
   (with-test-system!
     (fn [system]
       (let [db (:lipas/db @system)]
         ;; ... tests ...
         )))"
  [f]
  (let [test-system (atom nil)]
    (try
      (ensure-test-database!)
      (reset! test-system
              (ig/init (config/->system-config config)))
      (f test-system)
      (finally
        (when @test-system
          (ig/halt! @test-system))))))

;; ========================================
;; Standard Fixture Patterns
;; ========================================

(defn db-fixture
  "Initializes test database once, prunes between tests.

   For tests that only need database access.

   Usage:
   (let [{:keys [once each]} (test-utils/db-fixture)]
     (use-fixtures :once once)
     (use-fixtures :each each))"
  []
  {:once (fn [f] (init-db!) (f))
   :each (fn [f] (prune-db!) (f))})

(defn db-and-search-fixture
  "Initializes DB and Elasticsearch, prunes both between tests.

   For tests that need both database and search.

   Usage:
   (let [{:keys [once each]} (test-utils/db-and-search-fixture)]
     (use-fixtures :once once)
     (use-fixtures :each each))"
  []
  {:once (fn [f] (init-db!) (f))
   :each (fn [f] (prune-db!) (prune-es!) (f))})

(defn full-system-fixture
  "Starts complete Integrant system, prunes between tests.

   For integration tests that need full system components.
   Returns a map with :system atom and :once/:each fixtures.

   Usage:
   (def test-system (atom nil))
   (let [{:keys [once each]} (test-utils/full-system-fixture test-system)]
     (use-fixtures :once once)
     (use-fixtures :each each))

   ;; Access components:
   (def db (:lipas/db @test-system))"
  [system-atom]
  {:once (fn [f]
           (ensure-test-database!)
           (reset! system-atom (ig/init (config/->system-config config)))
           (try (f)
                (finally
                  (when @system-atom
                    (ig/halt! @system-atom)
                    (reset! system-atom nil)))))
   :each (fn [f]
           (let [db (:lipas/db @system-atom)
                 search (:lipas/search @system-atom)]
             (prune-db! db)
             (when search (prune-es! search))
             (f)))})
