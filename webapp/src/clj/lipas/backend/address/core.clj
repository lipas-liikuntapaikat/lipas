(ns lipas.backend.address.core
  "Two things live here: keeping the postal caches current, and answering
  \"what is at this point\" from them.

  == Reverse geocoding ==

  `reverse-geocode` merges three sources that each know something the others
  do not: Pelias knows which buildings are near the point and how far away,
  Posti's BAF knows which postal code a street number really belongs to, and
  Paavo's polygons know which postal area covers the wilderness between
  addresses. `build-result` is the merge itself, a pure fn over injected
  lookups (see `summary` for the rules and why they are ordered the way they
  are).

  == Keeping the caches current ==

  Discovering, downloading, parsing and importing Posti's PCF/BAF files and
  Tilastokeskus' Paavo postal code areas. These are the work fns behind the
  \"fetch-postal-data\" and \"fetch-paavo-areas\" jobs.

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
    [lipas.backend.address.resolve :as resolve]
    [lipas.data.cities :as cities]
    [taoensso.timbre :as log])
  (:import
    (java.net URI)))

;;; Reverse geocoding ;;;

(def exact-match-max-distance-m
  "How far a BAF-exact address may be from the clicked point and still speak
  for it. Beyond this the address is a neighbour, not the location: the
  Nuuksio case has an exact 02820 address 1.17 km away and another
  municipality's 1.19 km away, and picking either would be a coin toss
  dressed up as an answer. The Paavo polygon, which actually contains the
  point, wins there."
  300)

(def max-addresses
  "Addresses returned to the client. Pelias is asked for more so that the
  summary can look past phantom addresses, but the panel only ever shows a
  handful."
  5)

(def ^:private municipality-code-by-name
  "Every active municipality's Finnish, Swedish and English name folded
  through `posti/name-key`, mapped to its zero-padded kuntanumero — the same
  code space Posti and Paavo use.

  This is how Pelias' `localadmin` (a plain string, in either national
  language) becomes something we can query BAF with. Verified collision-free
  across all three name sets."
  (delay
    (into {}
          (for [{:keys [name city-code]} cities/active
                municipality-name (vals name)
                :when municipality-name]
            [(posti/name-key municipality-name) (format "%03d" city-code)]))))

(defn- ->municipality
  "`{:code \"049\" :name {:fi \"Espoo\" :sv \"Esbo\"}}` for a kuntanumero, or
  nil. Abolished municipalities are unknown to `cities/by-city-code`; the code
  is still returned, without names, since Posti's data outlives some merges."
  [code]
  (when code
    (let [{:keys [name]} (cities/by-city-code (parse-long code))]
      (cond-> {:code code}
        name (assoc :name {:fi (:fi name) :sv (:se name)})))))

(defn- ->postal-office
  [postal-code-row]
  (when postal-code-row
    {:fi (:name-fi postal-code-row) :sv (:name-sv postal-code-row)}))

(defn- ->region
  [postal-code-row]
  (when postal-code-row
    {:fi (:region-name-fi postal-code-row) :sv (:region-name-sv postal-code-row)}))

(defn- split-name
  "Splits a Pelias `name` ('Haukkaranta 16') into street and building number.
  Only a fallback: the structured `street`/`housenumber` properties are what
  we read when Pelias provides them, which for the address layer is nearly
  always."
  [nm]
  (or (when-let [[_ street number] (some->> nm (re-find #"^(.*?)\s+(\d.*)$"))]
        [street number])
      [nm nil]))

(defn- choose-code
  "The postal code to report for an address among the ones its street resolves
  to. Pelias' own code breaks a tie: when BAF says the street number could be
  in either of two codes and Pelias' nearest record already claims one of
  them, the two sources agreeing is the better guess than alphabetical order."
  [codes pelias-postal-code]
  (or (first (filter #(= pelias-postal-code (:postal-code %)) codes))
      (first codes)))

(defn- ->address
  "One Pelias address feature's `properties` turned into a response address,
  with the Posti verdict on it attached.

  `:posti` is nil when BAF has never heard of the street in that municipality
  — a real and common case (OSM's 'Valklammentie 105, Vihti' is not a Posti
  address at all), and the honest way to say so."
  [{:keys [segments-fn postal-code-fn]}
   {:keys [street housenumber label localadmin postalcode distance] pelias-name :name}]
  (let [[fallback-street fallback-number] (split-name pelias-name)
        street (or street fallback-street)
        building (or housenumber fallback-number)
        municipality-code (get @municipality-code-by-name
                               (some-> localadmin posti/name-key))
        {:keys [number letter]} (resolve/parse-building-number building)
        segments (when (and street municipality-code)
                   (segments-fn (posti/name-key street) municipality-code))
        codes (when (seq segments)
                (resolve/resolve-postal-codes segments number letter))
        chosen (choose-code codes postalcode)
        segment (first (filter #(= (:postal-code chosen) (:postal-code %)) segments))]
    {:street {:fi (or (:name-fi segment) street)
              :sv (:name-sv segment)}
     :number building
     :label label
     ;; Pelias reports distance in kilometres, the UI speaks metres.
     ;; 1000.0 (not 1000): an exactly-matched point comes back with an
     ;; integer distance, and Math/round has no Long overload.
     :distance-m (some-> distance (* 1000.0) Math/round)
     :municipality (->municipality municipality-code)
     :pelias-postal-code postalcode
     :posti (when chosen
              {:postal-code (:postal-code chosen)
               :postal-office (->postal-office (postal-code-fn (:postal-code chosen)))
               ;; A street with no building number resolves to every code on
               ;; the street; that is a street-level answer, never an exact one.
               :exact (boolean (and number (:exact? chosen)))})}))

(defn- ->area
  [{:keys [postal-code-fn]} paavo]
  (when paavo
    {:postal-code (:postal-code paavo)
     :name {:fi (:name-fi paavo) :sv (:name-sv paavo)}
     :postal-office (->postal-office (postal-code-fn (:postal-code paavo)))
     :municipality (->municipality (:municipality-code paavo))}))

(defn- summary
  "What the UI leads with, per the agreed rules:

  1. A = the closest address BAF matched exactly.
  2. B = the Paavo area containing the point.
  3. The postal code is A when A is within `exact-match-max-distance-m`, else
     B, else A, else the raw code Pelias inherited from the nearest address,
     else nothing. Each step is a step down in trustworthiness, and
     `:postal-code-source` says which one answered.
  4. `:alternative-postal-code` is the other of A and B when they disagree —
     the caveat line the panel shows. Nil when they agree, which is the
     common case and worth not nagging about.
  5. The municipality comes from the Paavo polygon when there is one:
     containing the point beats a string match on Pelias' `localadmin`.
  6. The region comes from Posti's `postal_code` row for whichever code won."
  [{:keys [postal-code-fn]} addresses area]
  (let [nearest (first addresses)
        a (first (filter #(get-in % [:posti :exact]) addresses))
        a-code (get-in a [:posti :postal-code])
        b-code (:postal-code area)
        [code source] (cond
                        (and a (<= (:distance-m a) exact-match-max-distance-m))
                        [a-code :posti]

                        b-code [b-code :paavo]
                        a-code [a-code :posti]
                        (:pelias-postal-code nearest) [(:pelias-postal-code nearest) :pelias]
                        :else [nil nil])
        row (when code (postal-code-fn code))
        street (get-in nearest [:street :fi])]
    {:postal-code code
     :postal-code-source source
     :postal-office (->postal-office row)
     :municipality (or (:municipality area) (:municipality a))
     :region (->region row)
     :address (when street (str/trim (str street " " (:number nearest))))
     :address-distance-m (:distance-m nearest)
     :alternative-postal-code (when (and a-code b-code (not= a-code b-code))
                                (if (= :posti source) b-code a-code))}))

(defn build-result
  "The reverse-geocode response for point (`lat`, `lon`). Pure: every lookup
  arrives as data or as an injected fn, which is what lets the summary rules
  be tested without a database or a network.

  - `pelias` — `{:status :ok|:error :features [<pelias properties> ...]}`
  - `paavo` — the `paavo_area` row containing the point, or nil
  - `segments-fn` — `(fn [street-key municipality-code] -> segments)`
  - `postal-code-fn` — `(fn [code] -> postal_code row or nil)`

  `:sources` reports how each half fared, so the UI can be honest about a
  degraded answer rather than silently showing less."
  [{:keys [lat lon pelias paavo] :as ctx}]
  (let [addresses (->> (:features pelias)
                       (mapv (partial ->address ctx))
                       (sort-by #(or (:distance-m %) Long/MAX_VALUE))
                       (take max-addresses)
                       vec)
        area (->area ctx paavo)]
    {:point {:lat lat :lon lon}
     :area area
     :addresses addresses
     :summary (assoc (summary ctx addresses area)
                     :sources
                     {:pelias (if (= :error (:status pelias))
                                :error
                                (if (seq addresses) :ok :empty))
                      :paavo (if area :ok :empty)})}))

(defn reverse-geocode
  "Address, postal code and postitoimipaikka for WGS84 point (`lat`, `lon`).

  `pelias-fn` is `(fn [lat lon] -> [<pelias properties> ...])`. When it throws
  — timeout, expired subscription key, Digitransit outage — the answer
  degrades to the database half alone rather than failing: Paavo still knows
  which postal area the point is in, and that is most of what the panel
  shows.

  `postal_code` rows are looked up repeatedly (an address, its area, the
  summary) and there are at most a handful of distinct codes per request, so
  the lookup is memoized for the duration of one call."
  [db pelias-fn lat lon]
  (let [pelias (try
                 {:status :ok :features (pelias-fn lat lon)}
                 (catch Exception e
                   (log/warn e "Pelias reverse geocoding failed" {:lat lat :lon lon})
                   {:status :error :features []}))]
    (build-result
      {:lat lat
       :lon lon
       :pelias pelias
       :paavo (address-db/get-paavo-area db lon lat)
       :segments-fn (fn [street-key municipality-code]
                      (address-db/get-street-segments db street-key municipality-code))
       :postal-code-fn (memoize (fn [code] (address-db/get-postal-code db code)))})))

;;; Posti data files ;;;

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
