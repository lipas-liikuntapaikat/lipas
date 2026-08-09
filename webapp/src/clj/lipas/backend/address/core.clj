(ns lipas.backend.address.core
  "Keeping the postal caches current: discovering, downloading, parsing and
  importing Posti's PCF/BAF files and Tilastokeskus' Paavo postal code areas.
  These are the work fns behind the \"fetch-postal-data\" and
  \"fetch-paavo-areas\" jobs.

  Fetching is deliberately separated from importing — `import-pcf!`,
  `import-baf!` and `replace-paavo-areas!` take bytes and parsed data, and the
  `refresh-*!` entry points take the fetching fns as overridable options — so
  tests exercise the whole import path without touching the network.

  Both refresh fns are cheap no-ops when the published data is not newer than
  what `postal_data_source` records, which is what lets the scheduler run them
  on a plain fixed interval.

  A line Posti's format doesn't explain is an error, never a dropped row: the
  import throws with the offending line numbers and the job dead-letters. The
  alternative — importing the parseable remainder — would quietly lose
  addresses and answer reverse-geocode queries with stale or missing postal
  codes, which nobody would notice."
  (:require
    [clj-http.client :as client]
    [clojure.string :as str]
    [lipas.backend.address.db :as address-db]
    [lipas.backend.address.posti :as posti]
    [taoensso.timbre :as log])
  (:import
    (java.net URI)))

(def webpcode-url
  "Posti's open postal data listing. It links both zipped and plain .dat
  files; we take the plain ones under unzip/."
  "https://www.posti.fi/webpcode/")

(def paavo-wfs-url "https://geo.stat.fi/geoserver/postialue/wfs")

(def paavo-layer
  "Postal code areas extended over sea. LIPAS has plenty of water-adjacent
  sites, and the land-only layer leaves them in no polygon at all."
  "postialue:pno_meri")

(def ^:private http-opts
  {:socket-timeout 120000
   :connection-timeout 10000})

;;; Posti PCF/BAF ;;;

(defn- ->iso-date
  [yyyymmdd]
  (str (subs yyyymmdd 0 4) "-" (subs yyyymmdd 4 6) "-" (subs yyyymmdd 6 8)))

(defn latest-posti-dat
  "The newest `unzip/<kind>_yyyymmdd.dat` link on the webpcode listing `html`,
  as `{:url .. :run-date \"yyyy-mm-dd\"}`. `kind` is \"PCF\" or \"BAF\".

  Posti keeps several runs on the page and the file name is the only place
  the run date appears, so the date in the name is both the sort key and the
  freshness marker. Throws when the page has no such link — a silently empty
  result would look like 'nothing new to import' forever."
  [html kind]
  (let [pattern (re-pattern (str "href=\"([^\"]*unzip/" kind "_(\\d{8})\\.dat)\""))
        best    (->> (re-seq pattern html)
                     (sort-by (fn [[_ _ date]] date))
                     last)]
    (if-let [[_ href date] best]
      {:url      (str (.resolve (URI. webpcode-url) href))
       :run-date (->iso-date date)}
      (throw (ex-info (str "No unzip/" kind "_*.dat link on " webpcode-url)
                      {:kind kind :url webpcode-url})))))

(defn fetch-posti-listing
  "The webpcode listing page as HTML."
  []
  (:body (client/get webpcode-url http-opts)))

(defn download-dat
  "Downloads a .dat file as raw bytes. The files are ISO-8859-1 and the
  parser decodes them, so nothing here guesses at a charset."
  [url]
  (:body (client/get url (assoc http-opts :as :byte-array))))

(defn- records!
  "The records of a parse result, throwing when any line failed to parse.
  Only the first few offending lines travel with the exception; the point is
  to show a human what changed, not to serialize the whole file into the
  dead-letter row."
  [kind {:keys [records errors]}]
  (when (seq errors)
    (throw (ex-info (str kind ": " (count errors) " unparseable line(s), first at line "
                         (:line-number (first errors)))
                    {:kind        kind
                     :error-count (count errors)
                     :errors      (vec (take 5 errors))})))
  records)

(defn import-pcf!
  "Parses PCF `source` (anything `io/input-stream` accepts — a byte array, a
  file, a resource) and replaces `postal_code` with its contents. Returns the
  number of rows written."
  [db source run-date]
  (let [records (records! "PCF" (posti/parse-pcf-file source))]
    (address-db/replace-postal-codes! db records run-date)))

(defn import-baf!
  "Parses BAF `source` and replaces `postal_street_segment` with its addressed
  rows. Returns the number of rows written (BAF rows without a street address
  are not segments and are not counted)."
  [db source run-date]
  (let [records (records! "BAF" (posti/parse-baf-file source))]
    (address-db/replace-street-segments! db records run-date)))

(defn- refresh-posti-file!
  [db {:keys [kind import! download]} {:keys [url run-date]}]
  (let [stored (:run-date (address-db/get-data-source db (str/lower-case kind)))]
    (if (and stored (not (pos? (compare run-date stored))))
      (do
        (log/info "Postal data already current" {:kind kind :run-date run-date})
        {:status :skipped :run-date run-date :stored stored})
      (do
        (log/info "Importing postal data" {:kind kind :run-date run-date :url url})
        (let [n (import! db (download url) run-date)]
          (log/info "Imported postal data" {:kind kind :run-date run-date :count n})
          {:status :imported :run-date run-date :stored stored :count n})))))

(defn refresh-postal-data!
  "Work fn of the \"fetch-postal-data\" job. Discovers the latest PCF and BAF
  runs and imports each one that is newer than the loaded one. Posti
  publishes PCF daily except Sundays and BAF weekly, so most runs import one
  file or neither.

  Returns `{\"PCF\" {:status :imported|:skipped ..} \"BAF\" {..}}`.

  `listing` and `download` exist for tests; they default to the real HTTP
  calls."
  ([db] (refresh-postal-data! db {}))
  ([db {:keys [listing download]
        :or   {listing fetch-posti-listing download download-dat}}]
   (let [html (listing)]
     (into {}
           (for [{:keys [kind] :as file} [{:kind "PCF" :import! import-pcf! :download download}
                                          {:kind "BAF" :import! import-baf! :download download}]]
             [kind (refresh-posti-file! db file (latest-posti-dat html kind))])))))

;;; Tilastokeskus Paavo ;;;

(defn- paavo-request
  [extra-params]
  (:body (client/get paavo-wfs-url
                     (assoc http-opts
                            :as :json
                            :query-params (merge {"service"      "WFS"
                                                  "version"      "2.0.0"
                                                  "request"      "GetFeature"
                                                  "typeName"     paavo-layer
                                                  "outputFormat" "application/json"
                                                  "srsName"      "EPSG:4326"}
                                                 extra-params)))))

(defn fetch-paavo-year
  "The `vuosi` of the published layer, read from a single feature with its
  geometry left out — a ~300 byte response against the tens of megabytes the
  whole layer weighs. This is what makes a weekly (or every-restart) tick
  cheap: the layer is only downloaded when the year moved."
  []
  (some-> (paavo-request {"count" "1" "propertyName" "vuosi"})
          :features first :properties :vuosi long))

(defn fetch-paavo-geojson
  "The whole Paavo postal code area layer as a parsed GeoJSON
  FeatureCollection in WGS84."
  []
  (paavo-request {}))

(defn refresh-paavo-areas!
  "Work fn of the \"fetch-paavo-areas\" job. Imports the Paavo areas when the
  layer's `vuosi` is newer than the loaded year, or when `paavo_area` is empty
  — the latter is what self-seeds a fresh deployment.

  Returns `{:status :imported|:skipped :year .. :stored .. :count ..}`.

  `fetch-year` and `fetch` exist for tests; they default to the real WFS
  calls."
  ([db] (refresh-paavo-areas! db {}))
  ([db {:keys [fetch-year fetch]
        :or   {fetch-year fetch-paavo-year fetch fetch-paavo-geojson}}]
   (let [stored (address-db/get-paavo-year db)
         year   (fetch-year)]
     (cond
       (nil? year)
       (throw (ex-info "Paavo WFS reported no vuosi"
                       {:url paavo-wfs-url :layer paavo-layer}))

       (and stored (<= year stored))
       (do
         (log/info "Paavo areas already current" {:year year :stored stored})
         {:status :skipped :year year :stored stored})

       :else
       (let [features (:features (fetch))]
         (when (empty? features)
           (throw (ex-info "Paavo WFS returned no features"
                           {:url paavo-wfs-url :layer paavo-layer :year year})))
         (log/info "Importing Paavo areas" {:year year :feature-count (count features)})
         (let [n (address-db/replace-paavo-areas! db features year)]
           (log/info "Imported Paavo areas" {:year year :count n})
           {:status :imported :year year :stored stored :count n}))))))
