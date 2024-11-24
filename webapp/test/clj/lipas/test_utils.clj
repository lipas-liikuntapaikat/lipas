(ns lipas.test-utils
  (:require [cheshire.core :as j]
            [clojure.java.jdbc :as jdbc]
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

(def <-json #(j/parse-string (slurp %) true))
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

(defn init-db! []
  (let [migratus-opts {:store         :database
                       :migration-dir "migrations"
                       :db            (:db config)}]
    (try
      (jdbc/db-do-commands (-> config :db (assoc :dbname ""))
                           false
                           [(str "CREATE DATABASE " (-> config :db :dbname))])
      (catch Exception e
        (when-not (= "ERROR: database \"lipas_test\" already exists"
                     (-> e .getCause .getMessage))
          (throw e))))

    (jdbc/db-do-commands (:db config)
                           false
                           [(str "CREATE EXTENSION IF NOT EXISTS postgis")
                            (str "CREATE EXTENSION IF NOT EXISTS postgis_topology")
                            (str "CREATE EXTENSION IF NOT EXISTS citext")
                            (str "CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"")])

    (migratus/init migratus-opts)
    (migratus/migrate migratus-opts)))

(def tables
  ["account"
   "analysis_queue"
   "city"
   "elevation_queue"
   "email_out_queue"
   "integration_log"
   "integration_out_queue"
   "reminder"
   "sports_site"
   "subsidy"])

(defn prune-db! []
  (jdbc/execute! (:db config) [(str "TRUNCATE "
                                    (str/join "," tables)
                                    " RESTART IDENTITY CASCADE")]))

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
  (let [client   (:client search)
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
     :or   {admin? false status "active"}}]
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
  (every? #(s/valid? :lipas/sports-site %) (repeatedly 100 gen-sports-site))
  )
