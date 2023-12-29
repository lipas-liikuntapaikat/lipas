(ns lipas.backend.analysis.diversity
  (:require
   [clojure.data.csv :as csv]
   [clojure.java.io :as io]
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.walk :as walk]
   [taoensso.timbre :as log]
   [lipas.backend.analysis.common :as common]
   [lipas.backend.gis :as gis]
   [lipas.backend.osrm :as osrm]
   [lipas.backend.search :as search]
   [lipas.data.types :as types]
   [lipas.utils :as utils]))

;; process:

;; - input1 desired area (point + radius OR polygon)
;; - input2 "max distance" for a relevant sports sites
;; - input3 categorisation for sports sites (map from cat => type-codes)
;;
;; - in parallel
;;   - get population grid (area)
;;   - get sports sites (area + buffer, max-distance..?)
;; - for each grid calculate "sport site diversity" index
;; - euclidean distance (on JVM based on coords)
;; - route distance with OSRM
;; - calculate weighted diversity index (by population) for the whole area
;; - return as GeoJSON

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
        :let      []]
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
                                          {:id         (:_id site)
                                           :type-code  (-> site
                                                                  :_source
                                                                  :type
                                                                  :type-code)
                                           :distance-m distance}))
                                      site-data)))))

(defn- append-route-distances [pop-data site-data]
  (let [res (osrm/get-distances-and-travel-times
             {:profiles     #{:foot}
              :sources      (->> pop-data
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
                 {:id         (:_id site)
                  :status     (-> site :_source :status)
                  :type-code  (-> site :_source :type :type-code)
                  :distance-m (get-in res [:foot :distances pop-idx site-idx])
                  :duration-s (get-in res [:foot :durations pop-idx site-idx])})
               site-data)))
     pop-data)))

(def bool->num {true 1 false 0})

(defn- calc-indices
  [pop-data categories
   {:keys [max-distance-m statuses site->distance-fn site->type-code-fn site->status-fn
           sports-sites-fn]
    :or   {statuses           #{true}
           sports-sites-fn    :sports-sites
           site->type-code-fn :type-code
           site->distance-fn  :distance-m
           site->status-fn    (constantly true)}
    :as   opts}]
  (map
   (fn [pop-entry]
     (let [cats (reduce
                 (fn [m {:keys [type-codes name factor]
                         :or   {factor 1}}]
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
              :or   {coords-fn (comp gis/wkt-point->coords :coords)}}]
   {:type "FeatureCollection"
    :features
    (map
     (fn [pop-entry]
       (let [coords      (-> pop-entry :_source coords-fn)
             coords-3067 (gis/wgs84->tm35fin-no-wrap coords)]
         {:type "Feature"
          :geometry
          {:type        "Point"
           :coordinates coords}
          :properties
          (merge
           {:id               (-> pop-entry :_source :id_nro)
            :grid_id          (-> pop-entry :_source :grd_id)
            :epsg3067         coords-3067
            #_#_:sports-sites (map :_id (:sports-sites pop-entry))
            #_#_:envelope_wgs84   (gis/epsg3067-point->wgs84-envelope coords-3067 125)
            :diversity_idx    (:diversity-index pop-entry)
            #_#_:age_0_14     (-> pop-entry :_source :ika_0_14 utils/->int anonymize)
            #_#_:age_15_64    (-> pop-entry :_source :ika_15_64 utils/->int anonymize)
            #_#_:age_65_      (-> pop-entry :_source :ika_65_ utils/->int anonymize)
            :population       (-> pop-entry :_source :vaesto utils/->int common/anonymize)}
           (:categories pop-entry))}))
     pop-data)}))

(defn prepare-categories [categories]
  (map #(update % :type-codes set) categories))

(defn calc-aggs [pop-entries]
  (let [sum-field   (fn [k]
                    (->> pop-entries
                         (map (comp (partial max 0)
                                    (fnil utils/->int 0)
                                    k
                                    :_source))
                         (apply +)))
        idxs        (map :diversity-index pop-entries)
        total-pop   (sum-field :vaesto)
        age-0-14    (sum-field :ika_0_14)
        age-15-64   (sum-field :ika_15_64)
        age-65-     (sum-field :ika_65_)
        anonymized (- total-pop (+ age-0-14 age-15-64 age-65-
                                    ))]
    {:diversity-idx-mean       (some-> idxs utils/mean double)
     :diversity-idx-median     (some-> idxs utils/median double)
     :diversity-idx-mode       (utils/mode idxs)
     :population               total-pop
     :anonymized-count         anonymized
     :population-age-0-14      age-0-14
     :population-age-15-64     age-15-64
     :population-age-65-       age-65-
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
    :or   {max-distance-m 800 analysis-radius-km 5}
    :as   opts}]

  (let [categories (prepare-categories categories)
        type-codes (into #{} (mapcat :type-codes) categories)
        buff-geom  (gis/calc-buffer analysis-area-fcoll max-distance-m)
        buff-fcoll (gis/->fcoll [(gis/->feature buff-geom)])
        buff-dist  (double (+ analysis-radius-km (/ max-distance-m 1000)))

        pop-data  (future
                    (common/get-population-data search analysis-area-fcoll analysis-radius-km))
        site-data (future
                    (common/get-sports-site-data search buff-fcoll buff-dist type-codes statuses))

        pop-data-with-distances (condp = distance-mode
                                  "euclid" (append-euclid-distances (:hits @pop-data)
                                                                    (:hits @site-data))
                                  "route"  (append-route-distances (:hits @pop-data)
                                                                   (:hits @site-data))
                                  (append-route-distances (:hits @pop-data) (:hits @site-data)))
        pop-data-with-indices   (calc-indices pop-data-with-distances categories opts)]

    {:grid (->grid-geojson pop-data-with-indices)
     :aggs (calc-aggs pop-data-with-indices)
     #_#_:sports-sites
     {:type "FeatureCollection"
      :features
      (->> @site-data
           :hits
           (mapcat
            (fn [m]
              (let [props {:id        (:_id m)
                           :name      (-> m :_source :name)
                           :type-code (-> m :_source :type :type-code)}]
                (map
                 (fn [f] (assoc f :properties props))
                 (-> m :_source :search-meta :location :simple-geoms :features))))))}}))

;;; Pre-calculated impl ;;;

(defn fetch-grid
  [{:keys [indices client]} fcoll analysis-radius-km]
  (let [idx-name (get-in indices [:analysis :diversity])
        geom     (-> fcoll :features first)
        query    {:size 10000
                  :query
                  {:bool
                   {:filter
                    {:geo_shape
                     {:WKT
                      {:shape    (if (= "Point" (-> geom :geometry :type))
                                   {:type        "circle"
                                    :coordinates (-> geom :geometry :coordinates)
                                    :radius      (str analysis-radius-km "km")}
                                   (:geometry geom))
                       :relation "intersects"}}}}}}]
    (->> (search/search client idx-name query)
         :body
         :hits
         :hits)))

(defn calc-diversity-indices-2
  [search
   {:keys [analysis-area-fcoll categories max-distance-m analysis-radius-km distance-mode]
    :or   {max-distance-m 800 analysis-radius-km 5}
    :as   opts}]
  (let [categories (prepare-categories categories)
        #_#_buff-geom  (gis/calc-buffer analysis-area-fcoll max-distance-m)
        #_#_buff-fcoll (gis/->fcoll [(gis/->feature buff-geom)])
        buff-dist  (double (+ analysis-radius-km (/ max-distance-m 1000)))
        statuses   #{"active" "out-of-service-temporarily"}

        pop-data-with-distances (fetch-grid search analysis-area-fcoll buff-dist)
        pop-data-with-indices   (calc-indices pop-data-with-distances categories
                                              (assoc opts
                                                     :statuses statuses
                                                     :sports-sites-fn   (comp :sports-sites
                                                                              :_source)
                                                     :site->status-fn :status
                                                     :site->distance-fn (comp :distance-m
                                                                              :foot
                                                                              :osrm)))]

    {:grid (->grid-geojson pop-data-with-indices {:coords-fn (comp gis/wkt-point->coords :WKT)})
     :aggs (calc-aggs pop-data-with-indices)}))

(def all-type-codes (keys types/all))

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

(defn process-grid-item
  [search dist-km m on-error]
  (let [coords      (-> m :WKT gis/wkt-point->coords)
        point-fcoll (gis/->fcoll
                     [(gis/->feature {:type        "Point"
                                      :coordinates coords})])
        site-data   (common/get-sports-site-data
                     search
                     point-fcoll
                     dist-km
                     all-type-codes
                     statuses)]
    (assoc m :sports-sites
           (map (fn [site]
                  {:id        (:_id site)
                   :type-code (-> site :_source :type :type-code)
                   :status    (-> site :_source :status)
                   :osrm      (-> (osrm/get-distances-and-travel-times
                                   {:profiles     [:car :bicycle :foot]
                                    :sources      [(str/join "," coords)]
                                    :destinations (resolve-dests site on-error)})
                                  (some-> apply-mins))})
                (:hits site-data)))))

(defn recalc-grid!
  [{:keys [indices client] :as search} fcoll]
  (let [idx-name       (get-in indices [:analysis :diversity])
        on-error       prn
        buffer-dist-km 2
        buffer-geom    (gis/calc-buffer fcoll (* buffer-dist-km 1000))
        buffer-fcoll   (gis/->fcoll [(gis/->feature buffer-geom)])
        grid-items     (fetch-grid search buffer-fcoll buffer-dist-km)]
    (->> grid-items
         (map :_source)
         (map #(process-grid-item search buffer-dist-km % on-error))
         (search/->bulk idx-name :grd_id)
         (search/bulk-index-sync! client))))

(defn seed-new-grid-from-csv!
  [{:keys [indices client] :as search} csv-path]
  (let [idx-name   (str "diversity-" (search/gen-idx-name))
        on-error   #(log/error %)
        batch-size 100
        dist-km    2]

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
                           (conj res (process-grid-item search dist-km m on-error)))
                         []
                         part)]

          (log/info "Writing batch of" batch-size "to" idx-name)
          (->> ms
               (search/->bulk idx-name :grd_id)
               (search/bulk-index-sync! client))
          (log/info "Writing batch DONE"))))))

(comment
  (require '[lipas.backend.search])
  (require '[lipas.backend.config :as config])
  (require '[lipas.data.types :as types])
  config/default-config
  (def search (lipas.backend.search/create-cli (:search config/default-config)))
  (def point1
    {:type "Feature" :geometry {:type "Point" :coordinates [25.1 62.0]}})
  (def point1-fcoll {:type "FeatureCollection" :features [point1]})
  (def distance-km 5)
  (def pop-data (common/get-population-data search point1-fcoll distance-km))
  (->> pop-data :hits (map :_source) (map :vaesto) distinct)

  (def point-type-codes (->> types/all
                             (filter (comp #{"Point"} :geometry-type second))
                             (map first)))

  (def route-type-codes (->> types/all
                             (filter (comp #{"LineString"} :geometry-type second))
                             (map first)))

  route-type-codes

  (def point2 (assoc-in point1 [:geometry :coordinates] [25.7473 62.2426]))
  (def point2-fcoll (assoc point1-fcoll :features [point2]))

  (def site-data (common/get-sports-site-data search point1-fcoll distance-km point-type-codes default-statuses))
  site-data

  (def site-data2
    (common/get-sports-site-data search
                                 point2-fcoll
                                 distance-km
                                 route-type-codes
                                 common/default-statuses))

  site-data2

  (def point3 (assoc-in point1 [:geometry :coordinates] [25.759742853 62.236192345]))
  (def point3-fcoll (assoc point1-fcoll :features [point3]))

  (def pop-data3 (get-population-data search point3-fcoll 2))
  pop-data3

  (def site-data3 (time (get-sports-site-data search point3-fcoll 2 point-type-codes)))
  site-data3

  (time (osrm/get-distances-and-travel-times
         {:profiles     #{:foot}
          :sources      (->> pop-data3 :hits
                             (map (comp gis/wkt-point->coords :coords :_source))
                             (map #(str/join "," %)))
          :destinations (->> site-data3 :hits
                             (map (comp :simple-geoms :location :search-meta :_source))
                             (mapcat gis/->coord-pair-strs))}))

  (def pop-data4 (get-population-data search point3-fcoll 4))
  pop-data3

  (def site-data4 (time (get-sports-site-data search point3-fcoll 4 point-type-codes)))
  site-data4

  (time (osrm/get-distances-and-travel-times
         {:profiles     #{:foot}
          :sources      (->> pop-data4 :hits
                             (map (comp gis/wkt-point->coords :coords :_source))
                             (map #(str/join "," %)))
          :destinations (->> site-data4 :hits
                             (map (comp :simple-geoms :location :search-meta :_source))
                             (mapcat gis/->coord-pair-strs))}))

  (gis/->coord-pair-strs point1-fcoll)

  (time
   (osrm/get-distances-and-travel-times
    {:profiles     #{:foot}
     :sources      (->> pop-data :hits
                        (map (comp gis/wkt-point->coords :coords :_source))
                        (map #(str/join "," %)))
     :destinations (->> site-data
                        :hits
                        (map (comp :simple-geoms :location :search-meta :_source))
                        (mapcat gis/->coord-pair-strs))}))

  point-type-codes

  (def categoriez
    [{:name       "koira"
      :factor     1
      :type-codes [1530 1520 2320 6130 1395 6210 1370 1360 2360 5310 1560]}
     {:name       "kana"
      :factor     1
      :type-codes [205 2150 2210 7000 6220 4720 1330 206 4830 1180 204 4610 2610 2110 3120 2330 2280 6140]}
     {:name       "heppa"
      :factor     1
      :type-codes [2140 4220 2230 1350 4840 1510 5350 2520 4710 304 4820 1170 2350 2340 2120 5160 1550 3230 5130 5110 3240 4240 2270 4210]}
     {:name       "mursu"
      :factor     1
      :type-codes [301 4630 4810 1540 5320 3210 4640 1150 2310 5210 2380 201 1220 1140 6110 1120 1390 5340 302 6120 1310 202 1620 2250 2530 2130 3220 5330 4230 4320 3130 3110 203 4620 5360 2290 2260 1160 1210 5140 4310 207 1130 5120 4110 5370 2240 2510 1640 1380 5150 1630 2295 2370 1340 1610 2220 1320]}])


  (def res1 (calc-diversity-indices search point2-fcoll categoriez
                                    {:max-distance-m 800 :analysis-radius-km 0.5}))

  (def res2 (calc-diversity-indices search point2-fcoll categoriez
                                    {:max-distance-m 800 :analysis-radius-km 2}))

  (def res3 (time (doall
                   (calc-diversity-indices search point3-fcoll categoriez
                                           {:max-distance-m 800 :analysis-radius-km 2}))))''

  (def res4 (time (doall
                   (calc-diversity-indices search point3-fcoll categoriez
                                           {:max-distance-m 800 :analysis-radius-km 10}))))

  (def params
    {:analysis-area-fcoll point2-fcoll
     :categories          categoriez
     :max-distance-m      800 :analysis-radius-km 2
     :distance-mode       "route"})

  (time
   (doall (calc-diversity-indices search params)))

  (def route-categoriez
    [{:name       "routes"
      :factor     1
      :type-codes route-type-codes}])

  (def route-params
    {:analysis-area-fcoll point2-fcoll
     :categories          route-categoriez
     :max-distance-m      800
     :analysis-radius-km  2
     :distance-mode       "route"})

  (calc-diversity-indices search route-params)

  (def params {:analysis-area-fcoll point2-fcoll
               :categories          route-categoriez
               :max-distance-m      800
               :analysis-radius-km  5})


  (time (calc-diversity-indices-2 search params))
  (prepare-categories categoriez)
  (time (+ 1 1))

  )
