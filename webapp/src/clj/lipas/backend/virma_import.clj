(ns lipas.backend.virma-import
  "Utility namespace for importing VIRMA CSV data into LIPAS sports facility format.

   The VIRMA CSV contains outdoor recreation locations that can be mapped to either:
   1. LIPAS sports site types (by Finnish name in 'lipas_type_name' column)
   2. LOI (Location of Interest) types (by string identifier in 'lipas_loi_type' column)

   The coordinates in the CSV are in ETRS-TM35FIN (EPSG:3067) projection
   and need to be converted to WGS84 (EPSG:4326) for LIPAS."
  (:require
   [clojure.data.csv :as csv]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [cheshire.core :as json]
   [geo.jts :as jts]
   [lipas.data.types :as types]
   [lipas.data.loi :as loi]
   [malli.core :as m]
   [malli.error :as me]
   [lipas.schema.sports-sites :as sports-site-schema]
   [lipas.schema.lois :as loi-schema]))

;; Coordinate transformation constants
(def tm35fin-srid 3067)
(def wgs84-srid 4326)

(defn tm35fin->wgs84
  "Convert ETRS-TM35FIN (EPSG:3067) coordinates to WGS84 (EPSG:4326).
   Input: [easting northing] in meters
   Output: [longitude latitude] in degrees"
  [[easting northing]]
  (let [transformed (jts/transform-geom
                     (jts/point northing easting) ; JTS uses lat/lon order internally
                     tm35fin-srid
                     wgs84-srid)]
    [(.getX transformed) (.getY transformed)]))

;; Owner mapping: VIRMA ownerclass -> LIPAS owner
(def owner-mapping
  {"Valtio"        "state"
   "Kunta"         "city"
   "Yksityinen"    "other"
   "Yhdistys"      "registered-association"
   "Yhteinen alue" "other"
   "Seurakunta"    "other"
   "Yritys"        "company-ltd"})

;; City code mapping for abolished municipalities
;; Maps old city codes to their current (merged) equivalents
;; Source: https://stat.fi/fi/luokitukset/kunta/
(def abolished-city-code-mapping
  {573 445   ; Parainen (-2008) -> Parainen (new)
   480 445   ; Nauvo -> Parainen
   503 445   ; Velkua -> Parainen
   533 445   ; Korppoo -> Parainen
   561 445   ; Houtskari -> Parainen
   601 445   ; Iniö -> Parainen
   })

(defn virma-owner->lipas-owner
  "Map VIRMA owner class to LIPAS owner."
  [virma-owner]
  (get owner-mapping virma-owner "unknown"))

(defn normalize-city-code
  "Normalize city code - maps abolished city codes to their current equivalents."
  [code]
  (get abolished-city-code-mapping code code))

;; Admin mapping is derived from owner (best effort guess)
(defn owner->admin
  "Derive admin from owner (best effort mapping)."
  [owner]
  (case owner
    "state"                  "state"
    "city"                   "city-sports"
    "registered-association" "private-association"
    "company-ltd"            "private-company"
    "foundation"             "private-foundation"
    "unknown"))

;; LIPAS type code mapping by Finnish name
(def type-name-to-code
  "Map Finnish type names to LIPAS type codes."
  (->> types/all
       (map (fn [[code {:keys [name]}]]
              [(get name :fi) code]))
       (into {})))

;; LOI type mapping
(def loi-type-set
  "Set of valid LOI type identifiers."
  (set (keys loi/types)))

;; LOI type to category mapping
(def loi-type->category
  "Map LOI type value to its category key."
  (into {}
        (for [[cat-k cat-v] loi/categories
              [_type-k type-v] (:types cat-v)]
          [(:value type-v) cat-k])))

(defn find-type-code-by-name
  "Find LIPAS type code by Finnish name (case-insensitive partial match)."
  [name-fi]
  (when (and name-fi (not (str/blank? name-fi)))
    (let [search (str/lower-case (str/trim name-fi))]
      (some (fn [[type-name code]]
              (when (and type-name
                         (str/includes? (str/lower-case type-name) search))
                code))
            type-name-to-code))))

(defn parse-geom-wkt
  "Parse POINT geometry from WKT string.
   Input: 'POINT (278939 6765988)' or 'POINT (278939.0000000001 6765988)'
   Output: [easting northing] or nil"
  [geom-str]
  (when (and geom-str (str/starts-with? geom-str "POINT"))
    (let [matches (re-find #"POINT\s*\(\s*([-\d.]+)\s+([-\d.]+)\s*\)" geom-str)]
      (when matches
        [(Double/parseDouble (nth matches 1))
         (Double/parseDouble (nth matches 2))]))))

(defn parse-properties
  "Parse properties JSON string into a map.
   Converts boolean string values to keywords that match LIPAS property names."
  [props-str]
  (when (and props-str (not (str/blank? props-str)) (not= "{}" props-str))
    (try
      (let [parsed (json/decode props-str true)]
        ;; Convert keys like "toilet?" to :toilet?
        (into {}
              (for [[k v] parsed
                    :when (some? v)]
                [(keyword (name k)) v])))
      (catch Exception _
        nil))))

(defn normalize-postal-code
  "Ensure postal code is 5 digits with leading zeros."
  [zip]
  (when (and zip (not (str/blank? zip)))
    (let [cleaned (str/replace (str/trim zip) #"[^\d]" "")]
      (when (seq cleaned)
        (format "%05d" (Integer/parseInt cleaned))))))

(defn parse-csv-row
  "Parse a single CSV row into a map with column keys."
  [headers row]
  (zipmap (map keyword headers) row))

(defn determine-import-type
  "Determine whether this row should become a sports-site or LOI.
   Returns :sports-site, :loi, or :unknown"
  [{:keys [lipas_type_name lipas_loi_type]}]
  (cond
    ;; Has a known LOI type
    (and lipas_loi_type
         (not (str/blank? lipas_loi_type))
         (contains? loi-type-set lipas_loi_type))
    :loi

    ;; Has a LIPAS type name that we can map
    (and lipas_type_name
         (not (str/blank? lipas_type_name))
         (find-type-code-by-name lipas_type_name))
    :sports-site

    ;; Default - try as LOI if has any loi_type
    (and lipas_loi_type (not (str/blank? lipas_loi_type)))
    :loi

    :else
    :unknown))

(defn csv-row->sports-site
  "Convert a parsed CSV row to a LIPAS sports site document.
   Returns nil if the row cannot be converted to a valid sports site.

   Note: The :status field here refers to the sports site's operational status
   (active, planning, etc.), not the document status (draft/published).
   Document status is set separately when saving to the database."
  [{:keys [geom id name_fi name_en name_se address zip municipali
           info_fi info_se info_en
           www_fi www_se www_en telephone email
           ownerclass x_eureffin y_eureffin
           munici_nro lipas_type_name lipas_type_code
           lipas_loi_type properties]
    :as row}]
  (let [;; Parse coordinates
        coords-from-wkt (parse-geom-wkt geom)
        coords-from-xy (when (and x_eureffin y_eureffin)
                         (try
                           [(Double/parseDouble x_eureffin)
                            (Double/parseDouble y_eureffin)]
                           (catch Exception _ nil)))
        tm35-coords (or coords-from-wkt coords-from-xy)

        ;; Transform to WGS84
        wgs84-coords (when tm35-coords (tm35fin->wgs84 tm35-coords))

        ;; Find type code
        type-code (or (when (and lipas_type_code (re-matches #"\d+" lipas_type_code))
                        (let [code (Integer/parseInt lipas_type_code)]
                          (when (contains? types/all code) code)))
                      (find-type-code-by-name lipas_type_name))

        ;; Parse city code and normalize abolished codes to current equivalents
        city-code (when (and munici_nro (re-matches #"\d+" munici_nro))
                    (-> munici_nro Integer/parseInt normalize-city-code))

        ;; Owner and admin
        owner (virma-owner->lipas-owner ownerclass)
        admin (owner->admin owner)

        ;; Postal code
        postal-code (normalize-postal-code zip)

        ;; Properties
        props (parse-properties properties)]

    (when (and wgs84-coords type-code city-code name_fi)
      (let [site {;; :status is the operational status of the sports facility
                  ;; Use "planning"
                  :status "planning"
                  :event-date (str (java.time.Instant/now))
                  :name name_fi
                  :owner owner
                  :admin admin
                  :type {:type-code type-code}
                  :location {:city {:city-code city-code}
                             ;; Address is required and must be non-empty
                             :address (if (and address (not (str/blank? address)))
                                        address
                                        "Osoitteeton") ; "No address" placeholder
                             :postal-code (or postal-code "00000")
                             :geometries {:type "FeatureCollection"
                                          :features [{:type "Feature"
                                                      :geometry {:type "Point"
                                                                 :coordinates wgs84-coords}}]}}}]
        ;; Add optional fields
        (cond-> site
          (and name_se (not (str/blank? name_se)))
          (assoc-in [:name-localized :se] name_se)

          (and name_en (not (str/blank? name_en)))
          (assoc-in [:name-localized :en] name_en)

          (and info_fi (not (str/blank? info_fi)))
          (assoc :comment info_fi)

          (and www_fi (not (str/blank? www_fi)))
          (assoc :www www_fi)

          (and email (not (str/blank? email)))
          (assoc :email email)

          (and telephone (not (str/blank? telephone)))
          (assoc :phone-number telephone)

          (seq props)
          (assoc :properties props))))))

(defn csv-row->loi
  "Convert a parsed CSV row to a LOI (Location of Interest) document.
   LOIs have a different schema than sports sites, requiring:
   - id (UUID)
   - event-date (timestamp)
   - geometries (GeoJSON)
   - status (operational status)
   - loi-category (category key)
   - loi-type (type value)
   - Plus optional props (name, description, etc.)"
  [{:keys [geom id name_fi name_en name_se address zip municipali
           info_fi info_se info_en
           www_fi telephone email
           ownerclass x_eureffin y_eureffin
           munici_nro lipas_loi_type]
    :as row}]
  (let [;; Parse coordinates
        coords-from-wkt (parse-geom-wkt geom)
        coords-from-xy (when (and x_eureffin y_eureffin)
                         (try
                           [(Double/parseDouble x_eureffin)
                            (Double/parseDouble y_eureffin)]
                           (catch Exception _ nil)))
        tm35-coords (or coords-from-wkt coords-from-xy)

        ;; Transform to WGS84
        wgs84-coords (when tm35-coords (tm35fin->wgs84 tm35-coords))

        ;; Lookup LOI category from type
        loi-category (get loi-type->category lipas_loi_type)]

    ;; Only create LOI if we have valid coordinates, type, and category
    (when (and wgs84-coords
               lipas_loi_type
               (not (str/blank? lipas_loi_type))
               loi-category)
      (let [loi-doc {:id (str (java.util.UUID/randomUUID))
                     :event-date (str (java.time.Instant/now))
                     :status "planning" ; LOI operational status
                     :loi-category loi-category
                     :loi-type lipas_loi_type
                     :geometries {:type "FeatureCollection"
                                  :features [{:type "Feature"
                                              :geometry {:type "Point"
                                                         :coordinates wgs84-coords}}]}}]
        ;; Add optional props (name, description)
        (cond-> loi-doc
          (and name_fi (not (str/blank? name_fi)))
          (assoc :name {:fi name_fi})

          (and name_se (not (str/blank? name_se)))
          (assoc-in [:name :se] name_se)

          (and name_en (not (str/blank? name_en)))
          (assoc-in [:name :en] name_en)

          (and info_fi (not (str/blank? info_fi)))
          (assoc :description {:fi info_fi})

          (and info_se (not (str/blank? info_se)))
          (assoc-in [:description :se] info_se)

          (and info_en (not (str/blank? info_en)))
          (assoc-in [:description :en] info_en))))))

(defn read-virma-csv
  "Read and parse VIRMA CSV file.
   Returns a sequence of parsed row maps."
  [file-path]
  (with-open [reader (io/reader file-path)]
    (let [data (csv/read-csv reader)
          headers (first data)
          rows (rest data)]
      (doall (map #(parse-csv-row headers %) rows)))))

(defn import-virma-csv
  "Import VIRMA CSV file and convert to LIPAS documents.
   Returns a map with:
   - :sports-sites - vector of valid sports site documents
   - :lois - vector of valid LOI documents
   - :skipped - vector of rows that couldn't be converted
   - :stats - summary statistics"
  [file-path]
  (let [rows (read-virma-csv file-path)
        classified (group-by determine-import-type rows)

        sports-sites (->> (get classified :sports-site [])
                          (map csv-row->sports-site)
                          (filter some?)
                          vec)

        lois (->> (concat (get classified :loi [])
                          (get classified :unknown []))
                  (map csv-row->loi)
                  (filter some?)
                  vec)

        skipped-sports (- (count (get classified :sports-site []))
                          (count sports-sites))
        skipped-loi (- (count (concat (get classified :loi [])
                                      (get classified :unknown [])))
                       (count lois))]

    {:sports-sites sports-sites
     :lois lois
     :stats {:total-rows (count rows)
             :sports-sites-created (count sports-sites)
             :lois-created (count lois)
             :sports-sites-skipped skipped-sports
             :lois-skipped skipped-loi
             :unknown-type (count (get classified :unknown []))}}))

(defn validate-sports-site
  "Validate a sports site document against the malli schema.
   Uses new-sports-site schema since these are new imports without lipas-id.
   Returns {:valid? true/false :errors [...] :site site}"
  [site]
  (let [;; Use new-sports-site schema which doesn't require lipas-id
        schema sports-site-schema/new-sports-site
        explanation (m/explain schema site)]
    {:valid? (nil? explanation)
     :errors (when explanation (me/humanize explanation))
     :site site}))

(defn validate-all-sports-sites
  "Validate all sports sites and return validation summary."
  [sports-sites]
  (let [results (map validate-sports-site sports-sites)
        valid (filter :valid? results)
        invalid (remove :valid? results)]
    {:valid-count (count valid)
     :invalid-count (count invalid)
     :valid-sites (map :site valid)
     :invalid-sites invalid}))

(defn validate-loi
  "Validate a LOI document against the malli schema.
   Returns {:valid? true/false :errors [...] :loi loi}"
  [loi-doc]
  (let [schema loi-schema/loi
        explanation (m/explain schema loi-doc)]
    {:valid? (nil? explanation)
     :errors (when explanation (me/humanize explanation))
     :loi loi-doc}))

(defn validate-all-lois
  "Validate all LOIs and return validation summary."
  [lois]
  (let [results (map validate-loi lois)
        valid (filter :valid? results)
        invalid (remove :valid? results)]
    {:valid-count (count valid)
     :invalid-count (count invalid)
     :valid-lois (map :loi valid)
     :invalid-lois invalid}))

(comment
  ;; Example usage:

  ;; Import the CSV file
  (def result (import-virma-csv "/tmp/virma_lipas_export.csv"))

  ;; Check statistics
  (:stats result)

  ;; Look at first few sports sites
  (map :name (take 100 (:sports-sites result)))

  ;; Look at first few LOIs
  (take 3 (:lois result))
  (take 3 (:sports-sites result))

  ;; Validate all sports sites
  (def sports-validation (validate-all-sports-sites (:sports-sites result)))
  (:valid-count sports-validation)
  (:invalid-count sports-validation)

  ;; See validation errors for invalid sports sites
  (take 3 (:invalid-sites sports-validation))

  ;; Validate all LOIs
  (def loi-validation (validate-all-lois (:lois result)))
  (:valid-count loi-validation)
  (:invalid-count loi-validation)

  ;; See validation errors for invalid LOIs
  (take 3 (:invalid-lois loi-validation))

  ;; Test coordinate transformation
  (tm35fin->wgs84 [278939.0 6765988.0])
  ;; Should return approximately [23.xxx 60.xxx]

  ;; Test type code lookup
  (find-type-code-by-name "Opastuspiste") ; => 207
  (find-type-code-by-name "Uimapaikka")   ; => 3230

  ;; Check LOI types
  (keys loi/types)

  (require '[lipas.backend.db.db :as db])
  (require '[lipas.backend.system :as system])
  (require '[lipas.backend.config :as config])
  (require '[lipas.backend.core :as core])

  (def db (:lipas/db (system/start-system! (select-keys config/system-config [:lipas/db]))))
  (def robot (core/get-user db "robot@lipas.fi"))

  (do
    (println "Saving" (count (:sports-sites result)) "sports sites")
    (doseq [m (:sports-sites result)]
      (println "Saving" (:name m))
      (db/upsert-sports-site! db robot m false))
    (println "All Sports sites saved!"))

  (do
    (println "Saving" (count (:lois result)) "lois")
    (doseq [m (:lois result)]
      (println "Saving" (:fi (:name m)))
      (db/upsert-loi! db robot m))
    (println "All Lois saved!"))

  )
