(ns lipas.backend.analysis.diversity
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [lipas.backend.analysis.common :as common]
            [lipas.backend.gis :as gis]
            [lipas.backend.osrm :as osrm]
            [lipas.backend.search :as search]
            [lipas.data.types :as types]
            [lipas.utils :as utils]
            [taoensso.timbre :as log]))

(def mappings
  {:mappings
   {:properties
    {:WKT {:type "geo_point"}
     :sports-sites {:type "nested"}}}})

(defn create-index!
  [{:keys [indices client]}]
  (let [idx-name (get-in indices [:analysis :diversity])]
    (search/create-index! client idx-name mappings)))

(def statuses
  "Relevant sports site statuses for diversity index calculation."
  #{"active" "out-of-service-temporarily"})

(defn- append-euclid-distances [pop-data site-data]
  (for [pop-entry pop-data
        :let []]
    (let [[lon lat] (-> pop-entry :_source :coords gis/wkt-point->coords)
          g1 (gis/->point lat lon)]
      (assoc pop-entry :sports-sites (map
                                      (fn [site]
                                        (let [g2 (-> site
                                                     :_source
                                                     :search-meta
                                                     :location
                                                     :simple-geoms
                                                     gis/->flat-coords
                                                     not-empty
                                                     (some->
                                                      gis/->jts-multi-point
                                                      (gis/nearest-points g1)
                                                      first))

                                              distance (gis/distance-point g1 g2)]
                                          {:id (:_id site)
                                           :type-code (-> site
                                                          :_source
                                                          :type
                                                          :type-code)
                                           :distance-m distance}))
                                      site-data)))))

(defn- append-route-distances [pop-data site-data]
  (let [res (osrm/get-distances-and-travel-times
             {:profiles #{:foot}
              :sources (->> pop-data
                            (map (comp gis/wkt-point->coords :coords :_source))
                            (map #(str/join "," %)))
              :destinations (->> site-data
                                 (map (comp :simple-geoms :location :search-meta :_source))
                                 (mapcat gis/->coord-pair-strs))})]
    (map-indexed
     (fn [pop-idx pop-entry]
       (assoc pop-entry :sports-sites
              (map-indexed
               (fn [site-idx site]
                 {:id (:_id site)
                  :status (-> site :_source :status)
                  :type-code (-> site :_source :type :type-code)
                  :distance-m (get-in res [:foot :distances pop-idx site-idx])
                  :duration-s (get-in res [:foot :durations pop-idx site-idx])})
               site-data)))
     pop-data)))

(def bool->num {true 1 false 0})

(defn- calc-indices
  [pop-data categories
   {:keys [max-distance-m statuses site->distance-fn site->type-code-fn site->status-fn
           sports-sites-fn]
    :or {statuses #{true}
         sports-sites-fn :sports-sites
         site->type-code-fn :type-code
         site->distance-fn :distance-m
         site->status-fn (constantly true)}
    :as _opts}]
  (map
   (fn [pop-entry]
     (let [cats (reduce
                 (fn [m {:keys [type-codes name factor]
                         :or {factor 1}}]
                   (assoc m name
                          (->> pop-entry
                               sports-sites-fn
                               (filter
                                (fn [site]
                                  (and
                                   (statuses (site->status-fn site))
                                   (type-codes (site->type-code-fn site))
                                   (> max-distance-m (or (site->distance-fn site)
                                                         max-distance-m)))))
                               first
                               some?
                               bool->num
                               ;; Occurrence in category contributes
                               ;; to diversity index with 0 or 1 *
                               ;; factor
                               (* factor))))
                 {}
                 categories)]
       (-> pop-entry
           (assoc :categories cats)
           (assoc :diversity-index (->> cats vals (apply +))))))
   pop-data))

(defn- ->grid-geojson
  ([pop-data] (->grid-geojson pop-data {}))
  ([pop-data {:keys [coords-fn]
              :or {coords-fn (comp gis/wkt-point->coords :coords)}}]
   {:type "FeatureCollection"
    :features
    (map
     (fn [pop-entry]
       (let [coords (-> pop-entry :_source coords-fn)
             coords-3067 (gis/wgs84->tm35fin-no-wrap coords)]
         {:type "Feature"
          :geometry
          {:type "Point"
           :coordinates coords}
          :properties
          (merge
           {:id (-> pop-entry :_source :id_nro)
            :grid_id (-> pop-entry :_source :grd_id)
            :epsg3067 coords-3067
            :diversity_idx (:diversity-index pop-entry)
            :population (-> pop-entry :_source :vaesto utils/->int common/anonymize)}
           (:categories pop-entry))}))
     pop-data)}))

(defn prepare-categories [categories]
  (map #(update % :type-codes set) categories))

(defn calc-aggs [pop-entries]
  (let [sum-field (fn [k]
                    (->> pop-entries
                         (map (comp (partial max 0)
                                    (fnil utils/->int 0)
                                    k
                                    :_source))
                         (apply +)))
        idxs (map :diversity-index pop-entries)
        total-pop (sum-field :vaesto)
        age-0-14 (sum-field :ika_0_14)
        age-15-64 (sum-field :ika_15_64)
        age-65- (sum-field :ika_65_)
        anonymized (- total-pop (+ age-0-14 age-15-64 age-65-))]
    {:diversity-idx-mean (some-> idxs utils/mean double)
     :diversity-idx-median (some-> idxs utils/median double)
     :diversity-idx-mode (utils/mode idxs)
     :population total-pop
     :anonymized-count anonymized
     :population-age-0-14 age-0-14
     :population-age-15-64 age-15-64
     :population-age-65- age-65-
     :population-weighted-mean (when (pos? total-pop)
                                 (double
                                  (/ (->> pop-entries
                                          (map (fn [m]
                                                 (* (-> m :_source :vaesto utils/->int)
                                                    (:diversity-index m))))
                                          (apply +))
                                     total-pop)))}))

(defn calc-diversity-indices
  [search
   {:keys [analysis-area-fcoll categories max-distance-m analysis-radius-km distance-mode]
    :or {max-distance-m 800 analysis-radius-km 5}
    :as opts}]
  (let [categories (prepare-categories categories)
        type-codes (into #{} (mapcat :type-codes) categories)
        buff-geom (gis/calc-buffer analysis-area-fcoll max-distance-m)
        buff-fcoll (gis/->fcoll [(gis/->feature buff-geom)])
        buff-dist (double (+ analysis-radius-km (/ max-distance-m 1000)))

        pop-data (future
                   (common/get-population-data search analysis-area-fcoll analysis-radius-km))
        site-data (future
                    (common/get-sports-site-data search buff-fcoll buff-dist type-codes statuses))

        pop-data-with-distances (condp = distance-mode
                                  "euclid" (append-euclid-distances (:hits @pop-data)
                                                                    (:hits @site-data))
                                  "route" (append-route-distances (:hits @pop-data)
                                                                  (:hits @site-data))
                                  (append-route-distances (:hits @pop-data) (:hits @site-data)))
        pop-data-with-indices (calc-indices pop-data-with-distances categories opts)]

    {:grid (->grid-geojson pop-data-with-indices)
     :aggs (calc-aggs pop-data-with-indices)}))

;;; Pre-calculated impl ;;;

(defn fetch-grid
  [{:keys [indices client]} fcoll analysis-radius-km]
  (let [idx-name (get-in indices [:analysis :diversity])
        geom (-> fcoll :features first)
        query {:size 10000
               :query
               {:bool
                {:filter
                 {:geo_shape
                  {:WKT
                   {:shape (if (= "Point" (-> geom :geometry :type))
                             {:type "circle"
                              :coordinates (-> geom :geometry :coordinates)
                              :radius (str analysis-radius-km "km")}
                             (:geometry geom))
                    :relation "intersects"}}}}}}]
    (->> (search/search client idx-name query)
         :body
         :hits
         :hits)))

(defn calc-diversity-indices-2
  [search
   {:keys [analysis-area-fcoll categories max-distance-m analysis-radius-km _distance-mode]
    :or {max-distance-m 800 analysis-radius-km 5}
    :as opts}]
  (let [categories (prepare-categories categories)
        buff-dist (double (+ analysis-radius-km (/ max-distance-m 1000)))
        statuses #{"active" "out-of-service-temporarily"}

        pop-data-with-distances (fetch-grid search analysis-area-fcoll buff-dist)
        pop-data-with-indices (calc-indices pop-data-with-distances categories
                                            (assoc opts
                                                   :statuses statuses
                                                   :sports-sites-fn (comp :sports-sites
                                                                          :_source)
                                                   :site->status-fn :status
                                                   :site->distance-fn (comp :distance-m
                                                                            :foot
                                                                            :osrm)))]

    {:grid (->grid-geojson pop-data-with-indices {:coords-fn (comp gis/wkt-point->coords :WKT)})
     :aggs (calc-aggs pop-data-with-indices)}))

(def all-type-codes (keys types/all))

(def cell-radius-m
  "Radius around each grid cell centroid within which sports sites
  contribute to the cell's precomputed distances."
  2000)

(def assignment-margin-m
  "Extra slack in the cell<->site assignment. Compensates for
  simple-geoms being a simplification (~111m tolerance) of the exact
  geometries the per-cell ES radius query used to match against."
  250)

(def tile-size-m
  "Cells are grouped into square TM35FIN tiles; each tile shares one
  multi-source OSRM table request per profile."
  2000)

(def max-table-locations
  "Max sources + destinations per OSRM table request. Deployment runs
  osrm-routed with --max-table-size 3000; staying well under keeps
  request URLs and response matrices moderate."
  1500)

(def osrm-table-timeout-ms
  "Timeout per OSRM table request (60 seconds)."
  60000)

(def osrm-profiles [:car :bicycle :foot])

(defn resolve-dests [site on-error]
  (try
    (-> site
        :_source
        :search-meta
        :location
        :simple-geoms
        gis/->coord-pair-strs)
    (catch Exception e
      (on-error {:site site :error e})
      [])))

(defn prepare-site-entry
  "Precompute OSRM destination strings and a metric (TM35FIN) JTS
  geometry for a site. Returns nil when the site has no usable
  geometry (it could never contribute a distance to the index)."
  [site on-error]
  (let [dests (into [] (distinct) (resolve-dests site on-error))
        geom (when (seq dests)
               (try
                 (-> site :_source :search-meta :location :simple-geoms
                     gis/fcoll->tm35fin-geom)
                 (catch Exception e
                   (on-error {:site site :error e})
                   nil)))]
    (when geom
      {:id (:_id site)
       :type-code (-> site :_source :type :type-code)
       :status (-> site :_source :status)
       :dests dests
       :geom-3067 geom})))

(defn assign-sites
  "For each cell (a TM35FIN JTS point), indices of site entries whose
  simple-geoms geometry lies within radius-m. This mirrors the old
  per-cell ES radius query against the exact geometries, to within the
  simple-geoms simplification tolerance (covered by
  assignment-margin-m). Returns a vector of index vectors aligned with
  cell-points."
  [cell-points site-entries radius-m]
  (mapv (fn [point]
          (persistent!
           (reduce
            (fn [acc i]
              (if (gis/within-distance? (:geom-3067 (nth site-entries i))
                                        radius-m
                                        point)
                (conj! acc i)
                acc))
            (transient [])
            (range (count site-entries)))))
        cell-points))

(defn index-dests
  "Assign one OSRM table column per distinct destination string across
  the given site entries. Returns [dests site-idx->cols]."
  [site-entries site-idxs]
  (loop [[site-idx & more] (seq site-idxs)
         dest->col {}
         dests []
         site->cols {}]
    (if (nil? site-idx)
      [dests site->cols]
      (let [[dest->col dests cols]
            (reduce (fn [[dest->col dests cols] dest]
                      (if-let [col (dest->col dest)]
                        [dest->col dests (conj cols col)]
                        [(assoc dest->col dest (count dests))
                         (conj dests dest)
                         (conj cols (count dests))]))
                    [dest->col dests []]
                    (:dests (nth site-entries site-idx)))]
        (recur more dest->col dests (assoc site->cols site-idx cols))))))

(defn- fetch-table!
  "One OSRM table request per profile in parallel for sources x dests.
  Failed profiles are retried once; still-failing profiles are omitted
  from the result map."
  [sources dests]
  (let [fetch (fn [profiles]
                (-> {:profiles profiles
                     :sources sources
                     :destinations dests
                     :cache? false
                     :timeout-ms osrm-table-timeout-ms}
                    osrm/get-distances-and-travel-times
                    (update-vals #(select-keys % [:distances :durations]))
                    (->> (into {} (filter (fn [[_ v]]
                                            (and (:distances v)
                                                 (:durations v))))))))
        first-pass (fetch osrm-profiles)
        missing (into [] (remove first-pass) osrm-profiles)]
    (if (seq missing)
      (merge first-pass (fetch missing))
      first-pass)))

(defn merge-table-chunks
  "Merge per-profile table results of consecutive destination chunks by
  concatenating matrix columns. chunk-sizes gives each chunk's column
  count and n-sources the row count, so a chunk whose request failed
  for a profile is nil-filled and its column indices reported under
  :failed-cols - sites touching those columns then omit the profile
  instead of risking a wrong (partial) minimum."
  [chunk-results chunk-sizes n-sources]
  (let [offsets (vec (reductions + 0 chunk-sizes))
        nil-rows (fn [n-cols]
                   (vec (repeat n-sources (vec (repeat n-cols nil)))))]
    (reduce
     (fn [acc p]
       (let [per-chunk (mapv #(get % p) chunk-results)]
         (if (every? nil? per-chunk)
           acc
           (let [failed-cols (into (sorted-set)
                                   (mapcat (fn [i]
                                             (when (nil? (nth per-chunk i))
                                               (range (nth offsets i)
                                                      (+ (nth offsets i)
                                                         (nth chunk-sizes i))))))
                                   (range (count per-chunk)))
                 concat-rows (fn [k]
                               (apply mapv (fn [& rows] (into [] cat rows))
                                      (map-indexed
                                       (fn [i r]
                                         (if r (k r) (nil-rows (nth chunk-sizes i))))
                                       per-chunk)))]
             (-> acc
                 (assoc-in [:tables p] {:distances (concat-rows :distances)
                                        :durations (concat-rows :durations)})
                 (cond-> (seq failed-cols)
                   (assoc-in [:failed-cols p] failed-cols)))))))
     {:tables {} :failed-cols {}}
     osrm-profiles)))

(defn- fetch-tables!
  "Table matrices for sources x dests, chunking destinations so each
  request stays within max-table-locations. Returns
  {:tables {profile ..} :failed-cols {profile #{col ..}}}."
  [sources dests]
  (let [chunk-size (max 1 (- max-table-locations (count sources)))
        chunks (mapv vec (partition-all chunk-size dests))]
    (merge-table-chunks (mapv #(fetch-table! sources %) chunks)
                        (mapv count chunks)
                        (count sources))))

(defn min-finite [xs]
  (when-let [vs (seq (remove nil? xs))]
    (reduce min vs)))

(defn site-osrm-mins
  "Per-profile minimum distance/duration for one cell (matrix row) and
  one site (its matrix columns). Min distance and min duration are
  taken independently, like the old per-site implementation did. A
  profile whose failed columns overlap the site's columns is omitted
  for this site (same footprint as the old per-site request failure)."
  [tables failed-cols row-idx cols]
  (not-empty
   (reduce-kv
    (fn [res p {:keys [distances durations]}]
      (if (some (or (get failed-cols p) #{}) cols)
        res
        (let [drow (nth distances row-idx)
              trow (nth durations row-idx)]
          (assoc res p {:distance-m (min-finite (map #(nth drow %) cols))
                        :duration-s (min-finite (map #(nth trow %) cols))}))))
    {}
    tables)))

(defn- tile-key [[e n]]
  [(quot (long e) tile-size-m) (quot (long n) tile-size-m)])

(defn- process-tile!
  "Compute distances for one tile of cells and bulk index the resulting
  grid docs. Returns the number of OSRM requests made."
  [client idx-name site-entries tile-cells]
  (let [routed (filterv (comp seq :site-idxs) tile-cells)
        unrouted (remove (comp seq :site-idxs) tile-cells)
        sources (mapv (fn [{:keys [coord]}] (str/join "," coord)) routed)
        [dests site->cols] (index-dests site-entries
                                        (into [] (distinct) (mapcat :site-idxs routed)))
        {:keys [tables failed-cols]} (when (seq sources)
                                       (fetch-tables! sources dests))
        chunk-size (max 1 (- max-table-locations (count sources)))
        n-requests (if (seq sources)
                     (* (count osrm-profiles)
                        (long (Math/ceil (/ (count dests) (double chunk-size)))))
                     0)
        ->site-result (fn [row-idx site-idx]
                        (let [entry (nth site-entries site-idx)]
                          {:id (:id entry)
                           :type-code (:type-code entry)
                           :status (:status entry)
                           :osrm (when (seq tables)
                                   (site-osrm-mins tables failed-cols row-idx
                                                   (site->cols site-idx)))}))
        docs (concat
              (map-indexed
               (fn [row-idx {:keys [cell site-idxs]}]
                 (assoc cell :sports-sites (mapv #(->site-result row-idx %) site-idxs)))
               routed)
              (map (fn [{:keys [cell]}] (assoc cell :sports-sites []))
                   unrouted))]
    (->> docs
         (search/->bulk idx-name :grd_id)
         (search/bulk-index-sync! client))
    n-requests))

(defn process-cells!
  "Compute sports-site OSRM distances for grid cells and bulk index the
  resulting diversity grid docs into idx-name.

  Fetches all candidate sports sites with one ES query over
  site-area-fcoll (which must cover every cell buffered by
  cell-radius-m), assigns sites to cells with a euclidean prefilter and
  issues chunked multi-source OSRM table requests per tile of cells --
  instead of one request per site per profile per cell like the old
  implementation. Returns summary stats."
  [{:keys [client] :as search} idx-name cells site-area-fcoll on-error]
  (let [cells (vec cells)
        cell-coords (mapv (comp gis/wkt-point->coords :WKT) cells)
        cells-3067 (mapv gis/wgs84->tm35fin-no-wrap cell-coords)
        cell-points (mapv gis/tm35fin-point cells-3067)
        site-hits (:hits (common/get-sports-site-data-scrolled
                          search site-area-fcoll (/ cell-radius-m 1000)
                          all-type-codes statuses))
        site-entries (into [] (keep #(prepare-site-entry % on-error)) site-hits)
        assignments (assign-sites cell-points site-entries
                                  (+ cell-radius-m assignment-margin-m))
        tiles (group-by #(tile-key (nth cells-3067 %)) (range (count cells)))
        osrm-requests (volatile! 0)]
    (log/info (format "Processing %d cells / %d sites in %d tiles"
                      (count cells) (count site-entries) (count tiles)))
    (doseq [[tk idxs] tiles]
      (try
        (let [tile-cells (mapv (fn [i]
                                 {:cell (nth cells i)
                                  :coord (nth cell-coords i)
                                  :site-idxs (nth assignments i)})
                               idxs)]
          (vswap! osrm-requests + (process-tile! client idx-name site-entries tile-cells)))
        (catch Exception e
          (log/error e "Failed to process tile" tk))))
    {:cells (count cells)
     :sites (count site-entries)
     :tiles (count tiles)
     :osrm-requests @osrm-requests}))

(defn recalc-grid!
  "Main entry point for recalculating the precomputed diversity grid
  around fcoll (2 km buffer)."
  ([search fcoll]
   (recalc-grid! search fcoll {}))
  ([{:keys [indices] :as search} fcoll
    {:keys [on-error]
     :or {on-error (fn [m] (log/debug (:error m) "Error processing site"))}}]
   (let [idx-name (get-in indices [:analysis :diversity])
         buffer-dist-km 2
         buffer-fcoll (-> fcoll
                          (gis/calc-buffer (* buffer-dist-km 1000))
                          gis/->feature
                          vector
                          gis/->fcoll)
         grid-items (fetch-grid search buffer-fcoll buffer-dist-km)
         ;; Covers every fetched cell buffered by cell-radius-m
         site-area-fcoll (-> fcoll
                             (gis/calc-buffer (+ (* buffer-dist-km 1000) cell-radius-m))
                             gis/->feature
                             vector
                             gis/->fcoll)
         start-ms (System/currentTimeMillis)
         stats (process-cells! search idx-name (mapv :_source grid-items)
                               site-area-fcoll on-error)]
     (log/info (format "Diversity grid recalculated in %.1fs: %s"
                       (/ (- (System/currentTimeMillis) start-ms) 1000.0)
                       (pr-str stats)))
     stats)))

(defn seed-new-grid-from-csv!
  [{:keys [client] :as search} csv-path]
  (let [idx-name (str "diversity-" (search/gen-idx-name))
        on-error (fn [m] (log/error (:error m) "Error processing site"))
        batch-size 500]

    (with-open [rdr (io/reader csv-path)]
      (log/info "Creating index" idx-name)
      (search/create-index! client idx-name mappings)

      (log/info "Starting to process" csv-path)
      (doseq [part (->> (csv/read-csv rdr)
                        utils/csv-data->maps
                        (map walk/keywordize-keys)
                        (partition-all batch-size))]
        (let [coords (mapv (comp gis/wkt-point->coords :WKT) part)
              area-fcoll (-> {:type "MultiPoint" :coordinates coords}
                             gis/->feature
                             vector
                             gis/->fcoll
                             (gis/calc-buffer cell-radius-m)
                             gis/->feature
                             vector
                             gis/->fcoll)
              stats (process-cells! search idx-name (vec part) area-fcoll on-error)]
          (log/info "Batch done:" (pr-str stats)))))))
