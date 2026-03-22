(ns lipas.backend.virma-import-routes
  "Import VIRMA routes CSV data into LIPAS sports site format.

   The routes CSV contains outdoor recreation routes (hiking, cycling, paddling, etc.)
   with MULTILINESTRING geometries in ETRS-TM35FIN (EPSG:3067).

   Each MULTILINESTRING is split into individual LineString features in a
   FeatureCollection, which is the format LIPAS uses for route-type sports sites.

   Fields NOT mapped (dropped from CSV):
   - subregion, region, subreg_nro, region_nro (LIPAS derives these from city)
   - shapeestim (shape estimation metadata)
   - timestamp (VIRMA edit timestamp, not relevant for LIPAS)
   - ski_route (all False in this dataset)
   - chall_clas (free text challenge classification, too unstructured)
   - accessibil (dropped for types without accessibility-info prop: 4402, 4411, 4412, 4451)
   - upkeepclas (maintenance class, no LIPAS equivalent)
   - upkeepinfo (maintenance info, no LIPAS equivalent)
   - pyhiinvaellus? property (pilgrimage flag, no LIPAS equivalent)"
  (:require
    [clojure.data.csv :as csv]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [cheshire.core :as json]
    [geo.jts :as jts]
    [lipas.backend.db.db :as db]
    [lipas.data.cities :as cities]
    [lipas.data.types :as types]
    [malli.core :as m]
    [malli.error :as me]
    [lipas.schema.sports-sites :as sports-site-schema])
  (:import
    [org.locationtech.jts.io WKTReader]))

;; --- Coordinate transformation ---

(def tm35fin-srid 3067)
(def wgs84-srid 4326)

(defn parse-multilinestring-wkt
  "Parse MULTILINESTRING WKT and transform from EPSG:3067 to WGS84.
   Returns a vector of LineString coordinate arrays, each suitable for
   a GeoJSON LineString geometry, or nil if parsing fails.

   Input:  'MULTILINESTRING ((x1 y1, x2 y2), (x3 y3, x4 y4))'
   Output: [[[lon1 lat1] [lon2 lat2]] [[lon3 lat3] [lon4 lat4]]]"
  [wkt-str]
  (when (and wkt-str (str/starts-with? wkt-str "MULTILINESTRING"))
    (try
      (let [reader (WKTReader.)
            geom (.read reader wkt-str)
            _ (.setSRID geom tm35fin-srid)
            transformed (jts/transform-geom geom tm35fin-srid wgs84-srid)
            n (.getNumGeometries transformed)]
        (mapv (fn [i]
                (let [ls (.getGeometryN transformed i)
                      coords (.getCoordinates ls)]
                  (mapv (fn [c] [(.getX c) (.getY c)]) coords)))
              (range n)))
      (catch Exception e
        (println "Failed to parse WKT:" (.getMessage e))
        nil))))

(defn linestrings->feature-collection
  "Convert vector of LineString coordinate arrays to GeoJSON FeatureCollection."
  [linestrings]
  {:type "FeatureCollection"
   :features (mapv (fn [coords]
                     {:type "Feature"
                      :geometry {:type "LineString"
                                 :coordinates coords}})
                   linestrings)})

;; --- Owner/admin resolution ---

(def upkeeper->owner
  "Map known upkeeper strings to LIPAS owner values.
   Key patterns are matched case-insensitively."
  {"metsähallitus" "state"
   "kunta / liikuntatoimi" "city"
   "kunta / muu" "city"})

(defn resolve-owner
  "Resolve LIPAS owner from VIRMA upkeeper free text.
   Strategy: exact match on known upkeepers, then pattern-based heuristics."
  [upkeeper]
  (if (or (nil? upkeeper) (= "NULL" upkeeper)
          (str/blank? upkeeper) (= "ei tietoa ylläpitäjästä" upkeeper))
    "unknown"
    (let [lower (str/lower-case upkeeper)]
      (or
       ;; Exact match
        (get upkeeper->owner lower)
       ;; Pattern-based
        (cond
          (str/includes? lower "kaupunki")      "city"
          (str/includes? lower "kunta")         "city"
          (re-find #"yhdistys|seura|lippukunta" lower) "registered-association"
          (re-find #"\boy\b|oy$|ab$|resort"    lower) "company-ltd"
          (str/includes? lower "seurakunta")    "other"
          :else                                 "unknown")))))

(defn owner->admin
  "Derive admin from owner (best effort)."
  [owner]
  (case owner
    "state"                  "state"
    "city"                   "city-sports"
    "registered-association" "private-association"
    "company-ltd"            "private-company"
    "unknown"))

;; --- City code handling ---

(def abolished-city-code-mapping
  "Maps old city codes to their current (merged) equivalents."
  {573 445   ; Parainen (-2008) -> Parainen (new)
   480 445   ; Nauvo -> Parainen
   503 445   ; Velkua -> Parainen
   533 445   ; Korppoo -> Parainen
   561 445   ; Houtskari -> Parainen
   601 445}) ; Iniö -> Parainen

(def city-name->code
  "Map Finnish city name to city code (active cities only)."
  (->> cities/all
       (filter #(= "active" (:status %)))
       (map (fn [c] [(get-in c [:name :fi]) (:city-code c)]))
       (into {})))

(defn parse-first-city-code
  "Parse the first city code from a potentially comma-separated list.
   Maps abolished codes to current equivalents.
   Falls back to municipality name lookup when code is 0 (state-owned)
   or missing. Returns nil if no valid code found."
  [munici-nro municipali]
  (let [code-from-nro (when (and munici-nro (not= "NULL" munici-nro) (not (str/blank? munici-nro)))
                        (let [first-code (-> munici-nro (str/split #",") first str/trim)]
                          (when (re-matches #"\d+" first-code)
                            (let [code (Integer/parseInt first-code)]
                              (get abolished-city-code-mapping code code)))))]
    (if (and code-from-nro (pos? code-from-nro))
      code-from-nro
      ;; Fallback: resolve from municipality name
      (when (and municipali (not= "NULL" municipali) (not (str/blank? municipali)))
        (let [first-name (-> municipali (str/split #",") first str/trim)]
          (get city-name->code first-name))))))

;; --- Properties mapping ---

(def types-with-accessibility-info
  "Type codes that have the :accessibility-info property."
  #{4401 4403 4404 4405})

(def types-with-toilet
  "Type codes that have the :toilet? property."
  #{4401 4402 4403 4405 4411 4412})

(defn- build-accessibility-info
  "Concatenate accessibile? classification and accessibil free text.
   Only for types that support the :accessibility-info property."
  [accessibile-val accessibil-str]
  (let [parts (cond-> []
                (and accessibile-val (not (str/blank? accessibile-val)))
                (conj accessibile-val)

                (and accessibil-str (not= "NULL" accessibil-str) (not (str/blank? accessibil-str)))
                (conj accessibil-str))]
    (when (seq parts)
      (str/join ". " parts))))

(defn parse-properties
  "Parse VIRMA properties JSON and map to LIPAS property keys.
   Also incorporates length_m and accessibil from the CSV row."
  [props-str length-m-str accessibil-str type-code]
  (let [props (when (and props-str
                         (not (str/blank? props-str))
                         (not= "{}" props-str)
                         (not= "NULL" props-str))
                (try (json/decode props-str true) (catch Exception _ nil)))
        accessibility (when (contains? types-with-accessibility-info type-code)
                        (build-accessibility-info (:accessibile? props) accessibil-str))
        result {}]
    (cond-> result
      ;; toilet? - only for types that have the prop
      (and (some? (:toilet? props))
           (contains? types-with-toilet type-code))
      (assoc :toilet? (:toilet? props))

      ;; length_m -> route-length-km
      (and length-m-str (not= "NULL" length-m-str) (not (str/blank? length-m-str)))
      (assoc :route-length-km
             (try (/ (Double/parseDouble length-m-str) 1000.0)
                  (catch Exception _ nil)))

      ;; Combined accessibility info
      accessibility
      (assoc :accessibility-info accessibility))))

;; --- Row conversion ---

(defn csv-row->sports-site
  "Convert a parsed CSV row to a LIPAS sports site document.
   Returns {:site <doc> :dropped <map>} or nil if row can't be converted.
   The :dropped map records what information was not carried over."
  [{:keys [geom gid name_fi name_en name_se
           info_fi info_se info_en
           www_fi telephone email
           upkeeper munici_nro municipali
           lipas_type_code lipas_type_name
           length_m properties
           chall_clas accessibil upkeepclas upkeepinfo
           shapeestim timestamp ski_route
           subregion region subreg_nro region_nro]
    :as row}]
  (let [linestrings (parse-multilinestring-wkt geom)
        type-code   (when (and lipas_type_code (re-matches #"\d+" lipas_type_code))
                      (let [code (Integer/parseInt lipas_type_code)]
                        (when (contains? types/all code) code)))
        city-code   (parse-first-city-code munici_nro municipali)
        owner       (resolve-owner upkeeper)
        admin       (owner->admin owner)]

    (when (and linestrings (seq linestrings) type-code city-code name_fi
               (not= "NULL" name_fi) (not (str/blank? name_fi)))
      (let [props    (parse-properties properties length_m accessibil type-code)
            truncate (fn [s max-len] (if (> (count s) max-len) (subs s 0 max-len) s))
            site  (cond->
                    {:event-date (str timestamp "T00:00:00Z")
                     :status     "planning"
                     :name       (truncate name_fi 100)
                     :owner      owner
                     :admin      admin
                     :type       {:type-code type-code}
                     :location   {:city       {:city-code city-code}
                                  :address    "Osoitteeton"
                                  :postal-code "00000"
                                  :geometries (linestrings->feature-collection linestrings)}}

                    (seq props)
                    (assoc :properties (into {} (filter (comp some? val) props)))

                    (and name_se (not= "NULL" name_se) (not (str/blank? name_se)))
                    (assoc-in [:name-localized :se] (truncate name_se 100))

                    (and name_en (not= "NULL" name_en) (not (str/blank? name_en)))
                    (assoc-in [:name-localized :en] (truncate name_en 100))

                    (and www_fi (not= "NULL" www_fi) (not (str/blank? www_fi)))
                    (assoc :www www_fi)

                    (and email (not= "NULL" email) (not (str/blank? email)))
                    (assoc :email email)

                    (and telephone (not= "NULL" telephone) (not (str/blank? telephone)))
                    (assoc :phone-number telephone))

            ;; Build comment from info_fi + useful extra fields
            pyhiinvaellus? (some-> properties
                                   (#(when (and % (not= "{}" %) (not= "NULL" %))
                                       (try (json/decode % true) (catch Exception _ nil))))
                                   :pyhiinvaellus?)
            other-municipalities (when (and municipali (str/includes? municipali ","))
                                   (str/join ", " (rest (str/split municipali #",\s*"))))
            comment-parts (cond-> []
                            (and info_fi (not= "NULL" info_fi) (not (str/blank? info_fi)))
                            (conj info_fi)

                            (and chall_clas (not= "NULL" chall_clas) (not (str/blank? chall_clas)))
                            (conj (str "Haastavuus: " chall_clas))

                            (true? pyhiinvaellus?)
                            (conj "Pyhiinvaellusreitti")

                            other-municipalities
                            (conj (str "Reitti kulkee myös: " other-municipalities))

                            (and upkeepinfo (not= "NULL" upkeepinfo) (not (str/blank? upkeepinfo)))
                            (conj (str "Ylläpitotiedot: " upkeepinfo)))
            comment (when (seq comment-parts)
                      (str/join "\n" comment-parts))
            site (cond-> site
                   comment
                   (assoc :comment comment))

            ;; Record what we're still dropping
            dropped (cond-> {}
                      ;; accessibil dropped only for types without the prop
                      (and accessibil (not= "NULL" accessibil)
                           (not (contains? types-with-accessibility-info type-code)))
                      (assoc :accessibil accessibil)

                      (and upkeepclas (not= "NULL" upkeepclas))
                      (assoc :upkeepclas upkeepclas)

                      (and shapeestim (not= "NULL" shapeestim))
                      (assoc :shapeestim shapeestim))]

        {:site site :gid gid :dropped dropped}))))

;; --- CSV reading and import ---

(defn read-csv [file-path]
  (with-open [reader (io/reader file-path)]
    (let [data    (csv/read-csv reader)
          headers (mapv keyword (first data))
          rows    (rest data)]
      (doall (map #(zipmap headers %) rows)))))

(defn import-csv
  "Import VIRMA routes CSV file and convert to LIPAS sports site documents.
   Returns a map with:
   - :sites      - vector of {:site <doc> :gid <virma-id> :dropped <map>}
   - :skipped    - vector of {:row <original> :reason <string>}
   - :stats      - summary statistics
   - :drop-summary - aggregate of what information was dropped"
  [file-path]
  (let [rows   (read-csv file-path)
        results (map (fn [row]
                       (if-let [result (csv-row->sports-site row)]
                         {:ok result}
                         {:skip {:row row
                                 :reason (cond
                                           (not (parse-multilinestring-wkt (:geom row)))
                                           "invalid geometry"

                                           (nil? (parse-first-city-code (:munici_nro row) (:municipali row)))
                                           "no valid city code"

                                           (or (nil? (:name_fi row))
                                               (= "NULL" (:name_fi row)))
                                           "no name"

                                           :else "no valid type code")}}))
                     rows)

        sites   (vec (keep :ok results))
        skipped (vec (keep :skip results))

        ;; Aggregate dropped fields
        drop-counts (reduce (fn [acc {:keys [dropped]}]
                              (reduce-kv (fn [m k _] (update m k (fnil inc 0)))
                                         acc dropped))
                            {} (map :ok (filter :ok results)))]

    {:sites   sites
     :skipped skipped
     :stats   {:total-rows     (count rows)
               :sites-created  (count sites)
               :sites-skipped  (count skipped)
               :skip-reasons   (frequencies (map #(get-in % [:skip :reason]) (filter :skip results)))}
     :drop-summary drop-counts}))

(defn validate-site
  "Validate a sports site document against the malli schema."
  [site]
  (let [explanation (m/explain sports-site-schema/new-sports-site site)]
    {:valid? (nil? explanation)
     :errors (when explanation (me/humanize explanation))
     :site   site}))

(defn validate-all
  "Validate all sites and return summary."
  [sites]
  (let [results (map #(validate-site (:site %)) sites)
        valid   (filter :valid? results)
        invalid (remove :valid? results)]
    {:valid-count   (count valid)
     :invalid-count (count invalid)
     :invalid-sites (vec invalid)}))

(defn save-sites!
  "Save all sites to database and return gid->lipas-id mapping.
   The mapping can be returned to VIRMA for cross-referencing."
  [db user sites]
  (let [mapping (atom [])]
    (doseq [{:keys [site gid]} sites]
      (let [result (db/upsert-sports-site! db user site false)
            lipas-id (:lipas-id result)]
        (swap! mapping conj {:gid gid :lipas-id lipas-id})
        (println (str "Saved gid=" gid " -> lipas-id=" lipas-id " (" (:name site) ")"))))
    @mapping))

(defn export-gid-mapping
  "Export gid->lipas-id mapping as CSV for VIRMA."
  [mapping file-path]
  (with-open [writer (io/writer file-path)]
    (csv/write-csv writer
                   (into [["gid" "lipas_id"]]
                         (map (fn [{:keys [gid lipas-id]}]
                                [gid (str lipas-id)])
                              mapping))))
  (println (str "Mapping exported to " file-path " (" (count mapping) " rows)")))

(comment
  ;; === REPL workflow ===

  ;; 1. Import and check stats
  (def result (import-csv "/Users/tipo/lipas/aineistot/2026/virma_lipas__routes_export.csv"))
  (:stats result)
  (:drop-summary result)

  ;; 2. Check skipped rows
  (:skipped result)

  ;; 3. Validate against schema
  (def validation (validate-all (:sites result)))
  (:valid-count validation)
  (:invalid-count validation)
  (take 3 (:invalid-sites validation))

  ;; 4. Inspect sample sites
  (first (:sites result))
  (-> result :sites first :site :location :geometries :features count)

  ;; 5. Check owner distribution
  (frequencies (map #(get-in % [:site :owner]) (:sites result)))

  ;; 6. Save to database
  (require '[lipas.backend.core :as core])
  (require '[lipas.backend.system :as system])

  (def db (:lipas/db @system/current-system))
  (def robot (core/get-user db "robot@lipas.fi"))

  (def gid-mapping (save-sites! db robot (:sites result)))

  ;; 7. Export mapping for VIRMA
  (export-gid-mapping gid-mapping "/tmp/virma_routes_gid_lipas_mapping.csv")

  ;; 8. Reindex Elasticsearch so new sites appear in search
  (require '[lipas.search-indexer :as indexer])
  (def search (:lipas/search @system/current-system))
  (indexer/main db search "search"))
