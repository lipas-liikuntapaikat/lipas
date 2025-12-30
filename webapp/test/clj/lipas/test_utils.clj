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

(declare gen-user prune-es!)

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

;; ========================================
;; Sports Site Factories
;; ========================================
;;
;; These factories create valid sports sites for testing.
;; All generated sites conform to the malli sports-site schema.
;;
;; Naming convention:
;; - make-*-site: Creates a sports site with specific geometry type
;; - Type codes determine which geometry type is valid
;;
;; Geometry types by type code:
;; - Point: Most indoor facilities and fields (1120, 2510, 3110, etc.)
;; - LineString: Routes (4401-4452 - hiking, skiing, cycling routes)
;; - Polygon: Areas (101, 103, 1110, 1650 - parks, recreation areas, golf)

(defn- current-timestamp []
  (str (java.time.Instant/now)))

(defn make-point-site
  "Creates a valid Point geometry sports site.

   Example type codes: 1120 (sports field), 2510 (ice stadium),
   3110 (swimming pool), 1530 (rink), 4810 (shooting range)

   Options:
   - :type-code        - Type code (default: 1120)
   - :city-code        - City code as string or int (default: \"91\" Helsinki)
   - :name             - Site name (default: \"Test Sports Place\")
   - :status           - Site status (default: \"active\")
   - :admin            - Admin entity (default: \"city-technical-services\")
   - :owner            - Owner entity (default: \"city\")
   - :event-date       - Event date (default: current timestamp)
   - :construction-year - Construction year (optional)
   - :properties       - Type-specific properties map (optional)
   - :coordinates      - [lon lat] coordinates (default: [25.0 60.2])"
  [lipas-id & {:keys [type-code city-code name status admin owner
                      event-date construction-year properties coordinates]
               :or {type-code 1120
                    city-code "91"
                    name "Test Sports Place"
                    status "active"
                    admin "city-technical-services"
                    owner "city"
                    coordinates [25.0 60.2]}}]
  (cond-> {:lipas-id lipas-id
           :name name
           :status status
           :admin admin
           :owner owner
           :event-date (or event-date (current-timestamp))
           :type {:type-code type-code}
           :location {:city {:city-code (str city-code)}
                      :address "Testikatu 1"
                      :postal-code "00100"
                      :postal-office "Helsinki"
                      :geometries {:type "FeatureCollection"
                                   :features [{:type "Feature"
                                               :geometry {:type "Point"
                                                          :coordinates coordinates}}]}}}
    construction-year (assoc :construction-year construction-year)
    properties (assoc :properties properties)))

(defn make-route-site
  "Creates a valid LineString geometry sports site (route).

   Example type codes: 4401 (jogging track), 4402 (ski track),
   4405 (hiking route), 4411 (MTB route), 4451 (canoe route)

   Options:
   - :type-code        - Type code (default: 4401 jogging track)
   - :city-code        - City code as string or int (default: \"91\" Helsinki)
   - :name             - Site name (default: \"Test Route\")
   - :status           - Site status (default: \"active\")
   - :admin            - Admin entity (default: \"city-technical-services\")
   - :owner            - Owner entity (default: \"city\")
   - :event-date       - Event date (default: current timestamp)
   - :construction-year - Construction year (optional)
   - :properties       - Type-specific properties map (optional)
   - :segments         - Vector of coordinate vectors for multi-segment routes
                         Each segment is [[lon lat] [lon lat] ...]
                         (default: single segment route)"
  [lipas-id & {:keys [type-code city-code name status admin owner
                      event-date construction-year properties segments]
               :or {type-code 4401
                    city-code "91"
                    name "Test Route"
                    status "active"
                    admin "city-technical-services"
                    owner "city"}}]
  (let [default-segment [[25.0 60.2] [25.01 60.21] [25.02 60.22] [25.03 60.23]]
        route-segments (or segments [default-segment])
        features (mapv (fn [coords]
                         {:type "Feature"
                          :geometry {:type "LineString"
                                     :coordinates coords}
                          :properties {}})
                       route-segments)]
    (cond-> {:lipas-id lipas-id
             :name name
             :status status
             :admin admin
             :owner owner
             :event-date (or event-date (current-timestamp))
             :type {:type-code type-code}
             :location {:city {:city-code (str city-code)}
                        :address "Reittikatu 1"
                        :postal-code "00100"
                        :postal-office "Helsinki"
                        :geometries {:type "FeatureCollection"
                                     :features features}}}
      construction-year (assoc :construction-year construction-year)
      properties (assoc :properties properties))))

(defn make-area-site
  "Creates a valid Polygon geometry sports site (area).

   Example type codes: 101 (neighbourhood park), 103 (outdoor area),
   1110 (sports park), 1650 (golf course), 4510 (orienteering area)

   Options:
   - :type-code        - Type code (default: 103 outdoor area)
   - :city-code        - City code as string or int (default: \"91\" Helsinki)
   - :name             - Site name (default: \"Test Area\")
   - :status           - Site status (default: \"active\")
   - :admin            - Admin entity (default: \"city-technical-services\")
   - :owner            - Owner entity (default: \"city\")
   - :event-date       - Event date (default: current timestamp)
   - :construction-year - Construction year (optional)
   - :properties       - Type-specific properties map (optional)
   - :polygons         - Vector of polygon ring vectors for multi-polygon areas
                         Each polygon is [[[lon lat] [lon lat] ... [first point]]]
                         (default: single polygon area)"
  [lipas-id & {:keys [type-code city-code name status admin owner
                      event-date construction-year properties polygons]
               :or {type-code 103
                    city-code "91"
                    name "Test Area"
                    status "active"
                    admin "city-technical-services"
                    owner "city"}}]
  (let [;; Default polygon - a simple rectangle (must close: first = last)
        default-polygon [[[25.0 60.2]
                          [25.01 60.2]
                          [25.01 60.21]
                          [25.0 60.21]
                          [25.0 60.2]]]
        area-polygons (or polygons [default-polygon])
        features (mapv (fn [rings]
                         {:type "Feature"
                          :geometry {:type "Polygon"
                                     :coordinates rings}})
                       area-polygons)]
    (cond-> {:lipas-id lipas-id
             :name name
             :status status
             :admin admin
             :owner owner
             :event-date (or event-date (current-timestamp))
             :type {:type-code type-code}
             :location {:city {:city-code (str city-code)}
                        :address "Puistotie 1"
                        :postal-code "00100"
                        :postal-office "Helsinki"
                        :geometries {:type "FeatureCollection"
                                     :features features}}}
      construction-year (assoc :construction-year construction-year)
      properties (assoc :properties properties))))

;; ========================================
;; Type-Specific Site Factories
;; ========================================

(defn make-swimming-pool-site
  "Creates a swimming pool site (type 3110) with pool-specific properties."
  [lipas-id & {:keys [city-code name pool-length-m pool-tracks-count
                      pool-width-m pool-min-depth-m pool-max-depth-m]
               :or {city-code "91"
                    name "Test Swimming Pool"
                    pool-length-m 25
                    pool-tracks-count 6}}]
  (make-point-site lipas-id
                   :type-code 3110
                   :city-code city-code
                   :name name
                   :properties (cond-> {:pool-length-m pool-length-m
                                        :pool-tracks-count pool-tracks-count}
                                 pool-width-m (assoc :pool-width-m pool-width-m)
                                 pool-min-depth-m (assoc :pool-min-depth-m pool-min-depth-m)
                                 pool-max-depth-m (assoc :pool-max-depth-m pool-max-depth-m))))

(defn make-ice-stadium-site
  "Creates an ice stadium site (type 2510) with ice-specific properties."
  [lipas-id & {:keys [city-code name ice-rinks-count field-length-m field-width-m]
               :or {city-code "91"
                    name "Test Ice Stadium"
                    ice-rinks-count 1
                    field-length-m 60
                    field-width-m 30}}]
  (make-point-site lipas-id
                   :type-code 2510
                   :city-code city-code
                   :name name
                   :properties {:ice-rinks-count ice-rinks-count
                                :field-length-m field-length-m
                                :field-width-m field-width-m}))

(defn make-ski-track-site
  "Creates a ski track site (type 4402) with route-specific properties."
  [lipas-id & {:keys [city-code name route-length-km lit-route-length-km
                      surface-material skiing-technique segments]
               :or {city-code "91"
                    name "Test Ski Track"
                    route-length-km 5.0
                    lit-route-length-km 3.0
                    surface-material ["natural-surface"]
                    skiing-technique "classic-and-free-style"}}]
  (make-route-site lipas-id
                   :type-code 4402
                   :city-code city-code
                   :name name
                   :segments segments
                   :properties {:route-length-km route-length-km
                                :lit-route-length-km lit-route-length-km
                                :surface-material surface-material
                                :skiing-technique skiing-technique}))

(defn make-hiking-route-site
  "Creates a hiking route site (type 4405) with route-specific properties."
  [lipas-id & {:keys [city-code name route-length-km surface-material segments]
               :or {city-code "91"
                    name "Test Hiking Route"
                    route-length-km 8.5
                    surface-material ["natural-surface"]}}]
  (make-route-site lipas-id
                   :type-code 4405
                   :city-code city-code
                   :name name
                   :segments segments
                   :properties {:route-length-km route-length-km
                                :surface-material surface-material}))

(defn make-golf-course-site
  "Creates a golf course site (type 1650) with golf-specific properties."
  [lipas-id & {:keys [city-code name holes-count range? polygons]
               :or {city-code "91"
                    name "Test Golf Course"
                    holes-count 18
                    range? true}}]
  (make-area-site lipas-id
                  :type-code 1650
                  :city-code city-code
                  :name name
                  :polygons polygons
                  :properties {:holes-count holes-count
                               :range? range?}))

(defn make-sports-park-site
  "Creates a sports park site (type 1110) with area-specific properties."
  [lipas-id & {:keys [city-code name area-km2 playground? ligthing? polygons]
               :or {city-code "91"
                    name "Test Sports Park"
                    playground? true
                    ligthing? true}}]
  (make-area-site lipas-id
                  :type-code 1110
                  :city-code city-code
                  :name name
                  :polygons polygons
                  :properties (cond-> {:playground? playground?
                                       :ligthing? ligthing?}
                                area-km2 (assoc :area-km2 area-km2))))

(defn make-outdoor-recreation-area-site
  "Creates an outdoor/recreation area site (type 103) with area properties."
  [lipas-id & {:keys [city-code name area-km2 free-use? polygons]
               :or {city-code "91"
                    name "Test Recreation Area"
                    free-use? true}}]
  (make-area-site lipas-id
                  :type-code 103
                  :city-code city-code
                  :name name
                  :polygons polygons
                  :properties (cond-> {:free-use? free-use?}
                                area-km2 (assoc :area-km2 area-km2))))

;; ========================================
;; Multi-Segment Geometry Helpers
;; ========================================

(defn make-multi-segment-route
  "Creates a route with multiple distinct segments.
   Useful for testing routes that span multiple trail sections."
  [lipas-id segment-count & {:keys [type-code city-code name properties]
                             :or {type-code 4405
                                  city-code "91"
                                  name "Multi-Segment Route"}}]
  (let [segments (for [i (range segment-count)]
                   (let [base-lon (+ 25.0 (* i 0.05))
                         base-lat (+ 60.2 (* i 0.02))]
                     [[base-lon base-lat]
                      [(+ base-lon 0.01) (+ base-lat 0.01)]
                      [(+ base-lon 0.02) (+ base-lat 0.015)]
                      [(+ base-lon 0.03) (+ base-lat 0.02)]]))]
    (make-route-site lipas-id
                     :type-code type-code
                     :city-code city-code
                     :name name
                     :segments (vec segments)
                     :properties properties)))

(defn make-multi-polygon-area
  "Creates an area with multiple distinct polygon features.
   Useful for testing areas that span multiple separate regions."
  [lipas-id polygon-count & {:keys [type-code city-code name properties]
                             :or {type-code 103
                                  city-code "91"
                                  name "Multi-Polygon Area"}}]
  (let [polygons (for [i (range polygon-count)]
                   (let [base-lon (+ 25.0 (* i 0.1))
                         base-lat (+ 60.2 (* i 0.05))]
                     ;; Each polygon is a closed ring
                     [[[base-lon base-lat]
                       [(+ base-lon 0.02) base-lat]
                       [(+ base-lon 0.02) (+ base-lat 0.02)]
                       [base-lon (+ base-lat 0.02)]
                       [base-lon base-lat]]]))]
    (make-area-site lipas-id
                    :type-code type-code
                    :city-code city-code
                    :name name
                    :polygons (vec polygons)
                    :properties properties)))

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

     ;; Install PostGIS legacy function shims (st_force_2d -> st_force2d, etc.)
     ;; PostGIS 3.x renamed these functions without underscores
     (jdbc/db-do-commands db-config
                          false
                          ["CREATE OR REPLACE FUNCTION public.st_force_2d(geom public.geometry)
                            RETURNS public.geometry AS $$
                            BEGIN
                              RETURN public.st_force2d(geom);
                            END;
                            $$ LANGUAGE plpgsql IMMUTABLE STRICT PARALLEL SAFE
                            SET search_path = public"
                           "CREATE OR REPLACE FUNCTION public.st_force_3d(geom public.geometry)
                            RETURNS public.geometry AS $$
                            BEGIN
                              RETURN public.st_force3d(geom);
                            END;
                            $$ LANGUAGE plpgsql IMMUTABLE STRICT PARALLEL SAFE
                            SET search_path = public"])

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
   "city"
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

;; ==========================================================================
;; DEPRECATED: Global vars removed - use full-system-fixture pattern instead
;; ==========================================================================
;;
;; The global db/app/search vars have been removed because:
;; 1. They caused StackOverflow on REPL reload (malli schema #'var references become stale)
;; 2. They created implicit shared state between tests
;; 3. They required @deref ceremony at every call site
;;
;; Instead, use the full-system-fixture pattern:
;;
;;   (defonce test-system (atom nil))
;;   (let [{:keys [once each]} (test-utils/full-system-fixture test-system)]
;;     (use-fixtures :once once)
;;     (use-fixtures :each each))
;;
;;   (defn test-db [] (:lipas/db @test-system))
;;   (defn test-app [] (:lipas/app @test-system))
;;   (defn test-search [] (:lipas/search @test-system))
;;
;; See lipas.backend.org-test for a complete example.

(defn prune-es!
  "Prune all Elasticsearch test indices and recreate them with proper mappings.
   Requires the search component as an argument."
  [search]
  (let [client (:client search)
        ;; Use programmatically generated explicit mapping for sports-site search index
        mappings {(-> search :indices :sports-site :search) (search/generate-explicit-mapping)
                  (-> search :indices :legacy-sports-site :search) (:legacy-sports-site search/mappings)
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
  ;; prune-es! now requires search component: (prune-es! search)
  ;; prune-db! can still be called without args if using test config
  (prune-db!)
  (ex-data *e))

(defn gen-user
  "Generate a test user with optional persistence to database.

   Options:
   - :db?          - If true, save user to database (requires :db-component)
   - :admin?       - If true, add admin role
   - :status       - User status (default: \"active\")
   - :permissions  - Custom permissions map to merge
   - :db-component - Required when db? is true. The database component to use."
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
       (do
         (when-not db-component
           (throw (ex-info "gen-user with db? true requires :db-component option. Use (gen-user {:db? true :db-component (test-db)})"
                           {:opts {:db? db? :admin? admin?}})))
         (core/add-user! db-component user)
         (assoc user :id (:id (core/get-user db-component (:email user)))))
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
  "Generate a user with admin permissions, optionally saved to DB.
   When db? is true (default), requires :db-component option."
  [& {:keys [db? db-component] :or {db? true} :as opts}]
  (gen-user (merge opts {:db? db? :admin? true :db-component db-component})))

(defn gen-regular-user
  "Generate a regular user with default permissions.
   When db? is true (default), requires :db-component option."
  [& {:keys [db? db-component] :or {db? true} :as opts}]
  (gen-user (merge opts {:db? db? :admin? false :db-component db-component})))

(defn gen-city-manager-user
  "Generate a city manager user for specific city.
   When db? is true (default), requires :db-component option."
  [city-code & {:keys [db? db-component] :or {db? true} :as opts}]
  (gen-user (merge opts {:db? db?
                         :db-component db-component
                         :permissions {:roles [{:role "city-manager"
                                                :city-code [city-code]}]}})))

(defn gen-site-manager-user
  "Generate a site manager user for specific sports site.
   When db? is true (default), requires :db-component option."
  [lipas-id & {:keys [db? db-component] :or {db? true} :as opts}]
  (gen-user (merge opts {:db? db?
                         :db-component db-component
                         :permissions {:roles [{:role "site-manager"
                                                :lipas-id [lipas-id]}]}})))

(defn gen-org-admin-user
  "Generate an organization admin for specific org.
   When db? is true (default), requires :db-component option."
  [org-id & {:keys [db? db-component] :or {db? true} :as opts}]
  (gen-user (merge opts {:db? db?
                         :db-component db-component
                         :permissions {:roles [{:role "org-admin"
                                                :org-id [(str org-id)]}]}})))

(defn gen-ptv-auditor
  "Generate a PTV auditor user.
   When db? is true (default), requires :db-component option."
  [& {:keys [db? db-component] :or {db? true} :as opts}]
  (gen-user (merge opts {:db? db?
                         :db-component db-component
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
  [search]
  {:once (fn [f] (init-db!) (f))
   :each (fn [f] (prune-db!) (prune-es! search) (f))})

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
