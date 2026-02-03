(ns lipas.backend.analysis.diversity
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.set :as set]
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
    (let [g1 (-> pop-entry
                 :_source
                 :coords
                 gis/wkt-point->coords
                 (->> (apply gis/->point)))]
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

(def osrm-site-timeout-ms
  "Timeout for OSRM request per site (60 seconds).
  Conservative to handle complex routes with multiple destinations."
  60000)

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

(defn- resolve-min
  [coll]
  (let [min* (partial apply min)]
    (->> coll (remove nil?) min*)))

(defn apply-mins [m]
  (reduce-kv
   (fn [res k v]
     (assoc res k
            (some-> v
                    (update :distances #(-> % first not-empty (some-> resolve-min)))
                    (update :durations #(-> % first not-empty (some-> resolve-min)))
                    (dissoc :code)
                    (set/rename-keys {:distances :distance-m
                                      :durations :duration-s}))))
   {}
   m))

(defn process-sites-chunk
  "Process a chunk of sites to control memory usage.
  Uses timeout-protected deref to prevent stuck jobs."
  [sites coords on-error]
  (mapv (fn [site]
          (let [dests (resolve-dests site on-error)]
            (if (empty? dests)
              {:id (:_id site)
               :type-code (-> site :_source :type :type-code)
               :status (-> site :_source :status)
               :osrm nil}
              ;; Create futures for just this site
              (let [futures (mapv (fn [p]
                                    [p (future
                                         (osrm/get-data
                                          {:profile p
                                           :sources [(str/join "," coords)]
                                           :destinations dests}))])
                                  [:car :bicycle :foot])
                    ;; Collect results with timeout to prevent stuck jobs
                    osrm-results (reduce (fn [res [p f]]
                                           (let [result (deref f osrm-site-timeout-ms ::timeout)]
                                             (cond
                                               (= result ::timeout)
                                               (do
                                                 (future-cancel f)
                                                 (log/warn "OSRM request timed out for profile" p
                                                           "site" (:_id site))
                                                 res)

                                               (nil? result)
                                               res

                                               :else
                                               (assoc res p result))))
                                         {}
                                         futures)]
                {:id (:_id site)
                 :type-code (-> site :_source :type :type-code)
                 :status (-> site :_source :status)
                 :osrm (when (seq osrm-results)
                         (apply-mins osrm-results))}))))
        sites))

(defn process-grid-item
  [search dist-km m on-error & {:keys [site-chunk-size] :or {site-chunk-size 20}}]
  (let [coords (-> m :WKT gis/wkt-point->coords)
        point-fcoll (gis/->fcoll
                     [(gis/->feature {:type "Point"
                                      :coordinates coords})])
        site-data (common/get-sports-site-data
                   search
                   point-fcoll
                   dist-km
                   all-type-codes
                   statuses)
        sites (:hits site-data)
        ;; Process sites in chunks to control memory usage
        site-chunks (partition-all site-chunk-size sites)
        results (atom [])]

    ;; Process each chunk and immediately release futures
    (doseq [chunk site-chunks]
      (let [chunk-results (process-sites-chunk chunk coords on-error)]
        (swap! results into chunk-results)))

    (assoc m :sports-sites @results)))

(defn recalc-grid!
  "Main entry point for recalculating diversity grid.
   Optimized with batch processing to control memory usage."
  ([{:keys [indices client] :as search} fcoll]
   (recalc-grid! search fcoll {}))
  ([{:keys [indices client] :as search} fcoll {:keys [batch-size gc-between-batches? site-chunk-size]
                                               :or {batch-size 20
                                                    gc-between-batches? true
                                                    site-chunk-size 20}}]
   (let [idx-name (get-in indices [:analysis :diversity])
         on-error (fn [e] (log/debug e "Error processing site"))
         buffer-dist-km 2
         buffer-geom (gis/calc-buffer fcoll (* buffer-dist-km 1000))
         buffer-fcoll (gis/->fcoll [(gis/->feature buffer-geom)])
         grid-items (fetch-grid search buffer-fcoll buffer-dist-km)
         total-items (count grid-items)
         batch-count (int (Math/ceil (/ total-items batch-size)))]

     (log/info (format "Processing %d grid items in %d batches of %d (site chunk size: %d)"
                       total-items batch-count batch-size site-chunk-size))

     ;; Process in batches to control memory usage
     (doseq [[batch-idx batch] (map-indexed vector (partition-all batch-size grid-items))]
       (let [batch-num (inc batch-idx)
             start-time (System/currentTimeMillis)]

         (log/info (format "Processing batch %d/%d" batch-num batch-count))

         (try
           ;; Process batch with site chunking
           (let [processed (->> batch
                                (map :_source)
                                (mapv #(process-grid-item search buffer-dist-km % on-error
                                                          :site-chunk-size site-chunk-size))
                                (search/->bulk idx-name :grd_id))]
             (search/bulk-index-sync! client processed))

           (catch Exception e
             (log/error e "Failed to process batch" batch-num)))

         ;; Optional GC between batches
         (when (and gc-between-batches? (< batch-num batch-count))
           (System/gc)
           (Thread/sleep 100))

         (log/debug (format "Batch %d completed in %.1f seconds"
                            batch-num (/ (- (System/currentTimeMillis) start-time) 1000.0)))))

     (log/info "All batches completed"))))

(defn seed-new-grid-from-csv!
  [{:keys [indices client] :as search} csv-path]
  (let [idx-name (str "diversity-" (search/gen-idx-name))
        on-error #(log/error %)
        batch-size 100
        dist-km 2]

    (with-open [rdr (io/reader csv-path)]
      (log/info "Creating index" idx-name)
      (search/create-index! client idx-name mappings)

      (log/info "Starting to process" csv-path)
      (doseq [part (->> (csv/read-csv rdr)
                        utils/csv-data->maps
                        (map walk/keywordize-keys)
                        (partition-all batch-size)
                        (doall))]

        (let [ms (reduce (fn [res m]
                           (conj res (process-grid-item search dist-km m on-error :site-chunk-size 20)))
                         []
                         part)]

          (log/info "Writing batch of" batch-size "to" idx-name)
          (->> ms
               (search/->bulk idx-name :grd_id)
               (search/bulk-index-sync! client))
          (log/info "Writing batch DONE"))))))
