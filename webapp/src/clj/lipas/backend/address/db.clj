(ns lipas.backend.address.db
  "Storage for the reverse-geocoding postal caches: `postal_code` and
  `postal_street_segment` (Posti PCF/BAF), `paavo_area` (Tilastokeskus postal
  code area polygons) and `postal_data_source` (which run of each source is
  currently loaded).

  None of this data is authored in LIPAS, so writes come in exactly one shape:
  `replace-*!` empties the table and reloads it from a freshly parsed file,
  inside a single transaction together with the `postal_data_source` row. A
  reader therefore never sees a half-imported table, and a failed import
  leaves the previous data in place.

  Reads are the lookups the reverse-geocode endpoint needs: the segments of
  one street in one municipality (fed to
  `lipas.backend.address.resolve/resolve-postal-codes`), the postal code row
  behind a code, and the Paavo area containing a point.

  Requiring `lipas.backend.db.utils` is load-bearing beyond `->pgobject`: it
  installs the `next.jdbc` protocol extension that turns jsonb columns back
  into Clojure maps, which is how `min_bound`/`max_bound` come out in the
  shape `resolve` expects."
  (:require
    [clojure.string :as str]
    [lipas.backend.address.posti :as posti]
    [lipas.backend.db.utils :as db-utils]
    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as rs])
  (:import
    (java.time LocalDate)))

(def ^:private query-opts
  {:builder-fn rs/as-unqualified-kebab-maps})

(def ^:private rows-per-statement
  "Rows folded into one multi-row INSERT. Postgres allows 65535 bind
  parameters per statement and the widest row here has 11, so 1000 leaves
  room to spare while cutting BAF's ~400k round trips to ~400."
  1000)

(defn- ->local-date
  [s]
  (cond
    (nil? s) nil
    (instance? LocalDate s) s
    :else (LocalDate/parse (str s))))

(defn- insert-rows!
  "Inserts `rows` with multi-row INSERT statements. `insert-sql` is everything
  up to and including the column list, `values-sql` the parenthesized
  placeholder group for one row, and `->params` turns a row into that group's
  positional parameters. Returns the row count.

  Deliberately not `execute-batch!`: PGJDBC sends every batch entry as its own
  round trip unless the connection sets reWriteBatchedInserts. Folding the
  rows into the statement text is that rewrite, done here — measured at 82 s
  against 119 s for BAF's 396k rows on a local database."
  [tx insert-sql values-sql ->params rows]
  (doseq [batch (partition-all rows-per-statement rows)]
    (let [sql (str insert-sql " VALUES "
                   (str/join ", " (repeat (count batch) values-sql)))]
      (jdbc/execute-one! tx (into [sql] (mapcat ->params) batch))))
  (count rows))

;;; Freshness bookkeeping ;;;

(def ^:private upsert-source-sql
  "INSERT INTO postal_data_source (kind, run_date, imported_at)
   VALUES (?, ?, CURRENT_TIMESTAMP)
   ON CONFLICT (kind) DO UPDATE
   SET run_date = EXCLUDED.run_date, imported_at = EXCLUDED.imported_at")

(defn- set-data-source!
  [tx kind run-date]
  (jdbc/execute-one! tx [upsert-source-sql kind (->local-date run-date)]))

(defn get-data-source
  "What run of `kind` (\"pcf\", \"baf\" or \"paavo\") is currently loaded, as
  `{:kind .. :run-date \"yyyy-mm-dd\" :imported-at ..}`, or nil when the source
  has never been imported."
  [db kind]
  (some-> (jdbc/execute-one!
            db
            ["SELECT kind, run_date, imported_at FROM postal_data_source WHERE kind = ?" kind]
            query-opts)
          (update :run-date str)))

;;; Postal codes (PCF) ;;;

(def ^:private postal-code-insert-sql
  "INSERT INTO postal_code
     (code, name_fi, name_sv, type, municipality_code, municipality_name_fi,
      municipality_name_sv, region_code, region_name_fi, region_name_sv,
      valid_from)")

(def ^:private postal-code-values-sql
  "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")

(defn- postal-code-params
  [{:keys [postal-code name-fi name-sv type municipality-code
           municipality-name-fi municipality-name-sv region-code
           region-name-fi region-name-sv valid-from]}]
  [postal-code name-fi name-sv (name type) municipality-code
   municipality-name-fi municipality-name-sv region-code region-name-fi
   region-name-sv (->local-date valid-from)])

(defn replace-postal-codes!
  "Replaces `postal_code` with `records` (PCF records as produced by
  `lipas.backend.address.posti/parse-pcf-file`) and records `run-date` as the
  loaded 'pcf' run. Returns the number of rows written."
  [db records run-date]
  (jdbc/with-transaction [tx db]
    (jdbc/execute-one! tx ["DELETE FROM postal_code"])
    (let [n (insert-rows! tx postal-code-insert-sql postal-code-values-sql
                          postal-code-params records)]
      (set-data-source! tx "pcf" run-date)
      n)))

(defn get-postal-code
  "The `postal_code` row for `code`, or nil."
  [db code]
  (jdbc/execute-one! db ["SELECT * FROM postal_code WHERE code = ?" code] query-opts))

;;; Street segments (BAF) ;;;

(defn street-segment?
  "True when a BAF record actually carries a street address. Side code '0'
  rows (~10 % of BAF, e.g. PO-box-only codes) leave the street name and
  building numbers blank and only link a postal code to a municipality —
  there is no segment to match against, so they are not stored."
  [{:keys [street-name-fi]}]
  (some? street-name-fi))

(def ^:private segment-insert-sql
  "INSERT INTO postal_street_segment
     (street_key, street_key_sv, name_fi, name_sv, municipality_code,
      postal_code, side, min_bound, max_bound)")

(def ^:private segment-values-sql
  "(?, ?, ?, ?, ?, ?, ?, ?, ?)")

(defn- segment-params
  [{:keys [street-name-fi street-name-sv municipality-code postal-code side
           min-bound max-bound]}]
  [(posti/name-key street-name-fi)
   (some-> street-name-sv posti/name-key)
   street-name-fi
   street-name-sv
   municipality-code
   postal-code
   (some-> side name)
   (some-> min-bound db-utils/->pgobject)
   (some-> max-bound db-utils/->pgobject)])

(defn replace-street-segments!
  "Replaces `postal_street_segment` with the addressed rows of `records` (BAF
  records as produced by `lipas.backend.address.posti/parse-baf-file`) and
  records `run-date` as the loaded 'baf' run. Rows without a street address
  are dropped (see `street-segment?`). Returns the number of rows written."
  [db records run-date]
  (let [segments (filterv street-segment? records)]
    (jdbc/with-transaction [tx db]
      (jdbc/execute-one! tx ["DELETE FROM postal_street_segment"])
      (let [n (insert-rows! tx segment-insert-sql segment-values-sql
                            segment-params segments)]
        (set-data-source! tx "baf" run-date)
        n))))

(def ^:private segments-sql
  "SELECT postal_code, name_fi, name_sv, side, min_bound, max_bound
   FROM postal_street_segment
   WHERE municipality_code = ? AND (street_key = ? OR street_key_sv = ?)")

(defn get-street-segments
  "Segments of the street `street-key` (a normalized name key, see
  `lipas.backend.address.posti/name-key`) in municipality `municipality-code`.
  Matches the Finnish and the Swedish key, so a Swedish street name finds the
  same street.

  Returns rows in the shape `lipas.backend.address.resolve` consumes:
  `:side` a keyword, `:min-bound`/`:max-bound` maps."
  [db street-key municipality-code]
  (->> (jdbc/execute! db [segments-sql municipality-code street-key street-key] query-opts)
       (mapv (fn [segment] (update segment :side #(some-> % keyword))))))

;;; Paavo areas ;;;

(def ^:private paavo-insert-sql
  "INSERT INTO paavo_area (postal_code, name_fi, name_sv, municipality_code, year, geom)")

(def ^:private paavo-values-sql
  "(?, ?, ?, ?, ?, ST_SetSRID(ST_GeomFromGeoJSON(?), 4326))")

(defn- paavo-params
  [year {:keys [properties geometry]}]
  [(:posti_alue properties)
   (:nimi properties)
   (:namn properties)
   (:kunta properties)
   (int year)
   (db-utils/->json geometry)])

(defn replace-paavo-areas!
  "Replaces `paavo_area` with GeoJSON `features` of the Paavo `postialue`
  layer (properties `posti_alue`, `nimi`, `namn`, `kunta`) and records
  `year` — the layer's `vuosi` — as both the row year and the loaded 'paavo'
  run. Returns the number of rows written."
  [db features year]
  (jdbc/with-transaction [tx db]
    (jdbc/execute-one! tx ["DELETE FROM paavo_area"])
    (let [n (insert-rows! tx paavo-insert-sql paavo-values-sql
                          (partial paavo-params year) features)]
      (set-data-source! tx "paavo" (LocalDate/of (int year) 1 1))
      n)))

(defn get-paavo-year
  "The year of the currently loaded Paavo areas, or nil when the table is
  empty. Both answers mean 'import' to the refresh job."
  [db]
  (:year (jdbc/execute-one! db ["SELECT max(year) AS year FROM paavo_area"] query-opts)))

(def ^:private paavo-at-point-sql
  "SELECT postal_code, name_fi, name_sv, municipality_code, year
   FROM paavo_area
   WHERE ST_Contains(geom, ST_SetSRID(ST_MakePoint(?, ?), 4326))
   LIMIT 1")

(defn get-paavo-area
  "The Paavo postal code area containing WGS84 point (`lon`, `lat`), or nil
  when the point is outside every polygon. The geometry column is left out of
  the result — callers want the attributes."
  [db lon lat]
  (jdbc/execute-one! db [paavo-at-point-sql (double lon) (double lat)] query-opts))
