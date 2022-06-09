(ns lipas.backend.analysis
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [lipas.backend.gis :as gis]
   [lipas.backend.osrm :as osrm]
   [lipas.backend.search :as search]
   [lipas.data.types :as types]
   [lipas.utils :as utils]))

;;; Saavutattevuustyökalu ;;;

(def population-index "vaestoruutu_1km_2019_kp")
(def population-index-high-def "vaestoruutu_250m_2020_kp")
(def population-high-def-threshold-km 10)

(def schools-index "schools")
(def sports-sites-index "sports_sites_current")

(defn- build-query [geom distance-km]
  {:size 10000
   :query
   {:bool
    {:must
     {:match_all {}}
     :filter
     {:geo_shape
      {:coords
       {:shape    (if (= "Point" (-> geom :geometry :type))
                    {:type        "circle"
                     :coordinates (-> geom :geometry :coordinates)
                     :radius      (str distance-km "km")}
                    (:geometry geom))
        :relation "within"}}}}}})

(defn get-es-data*
  [idx-name search fcoll distance-km]
  (let [geom (-> fcoll :features first)]
    (-> (search/search search idx-name (build-query geom distance-km))
        :body
        :hits)))

(defn get-population-data
  [search fcoll distance-km]
  (prn fcoll)
  (prn distance-km)
  (let [idx (if (> distance-km population-high-def-threshold-km)
              population-index
              population-index-high-def)]
    (get-es-data* idx search fcoll distance-km)))

(defn get-school-data
  [search fcoll distance-km]
  (get-es-data* schools-index search fcoll distance-km))

(defn get-sports-site-data
  [search fcoll distance-km type-codes]
  (let [geom  (-> fcoll :features first)
        query (-> (build-query geom distance-km)
                  (assoc :_source [:name
                                   :type.type-code
                                   :search-meta.location.simple-geoms])
                  (update-in [:query :bool :filter :geo_shape] set/rename-keys
                             {:coords :search-meta.location.geometries})
                  (cond->
                      (not-empty type-codes) (assoc-in [:query :bool :must]
                                                       {:terms
                                                        {:type.type-code type-codes}})))

        g1    (gis/->jts-geom fcoll)]
    (-> (search/search search sports-sites-index query)
        :body
        :hits
        (update :hits #(map
                       (fn [hit]
                         (let [closest (-> hit
                                           :_source
                                           :search-meta
                                           :location
                                           :simple-geoms
                                           gis/->flat-coords
                                           not-empty
                                           (some->
                                            gis/->jts-multi-point
                                            (gis/nearest-points g1)
                                            first
                                            gis/->wkt))]
                           (when closest
                             (assoc-in hit [:_source :coords] closest)))
                         ) %))
        (update :hits #(remove nil? %)))))

(defn calc-distances
  [source-fcoll populations]
  (let [geom-jts (-> source-fcoll gis/->jts-geom gis/transform-crs)]
    (->> populations
         (reduce-kv
          (fn [res id {:keys [coords] :as m}]
            (let [g (gis/wgs84wkt->tm35fin-geom coords)]
              (assoc res id (assoc m :distance-m (gis/shortest-distance geom-jts g)))))
          {}))))

(defn calc-travel-times
  [search-fcoll populations profiles id-fn]
  (let [destinations (gis/->coord-pair-strs search-fcoll)

        sources (->> populations                     
                     (map (comp gis/wkt-point->coords :coords :_source))
                     (map #(str/join "," %)))

        result (if (and (seq sources) (seq destinations))
                 (osrm/get-distances-and-travel-times
                    {:profiles     profiles
                     :sources      sources
                     :destinations destinations})
                   [])]

    (reduce-kv
     (fn [m k {:keys [distances durations]}]
       (assoc m k
              (into {}
                    (map-indexed
                     (fn [idx pop]
                       [(id-fn pop)
                        {:distance-m  (apply min (nth distances idx))
                         :duration-s  (apply min (nth durations idx))}]))
                    populations)))
     {}
     result)))

(defn resolve-zone
  [zones m profile metric]
  (if (= :travel-time metric)

    ;; travel time
    (when-let [time-s (-> m :route profile :duration-s)]
      (let [time-min (/ time-s 60)]
        (->> zones
             :travel-time
             (some
              (fn [zone]
                (when (>= (:max zone) time-min (:min zone))
                  (:id zone)))))))

    ;; distance
    (let [distance-km (-> m :route profile :distance-m (/ 1000))]
      (->> zones
           :distance
           (some
            (fn [zone]
              (when (>= (:max zone) distance-km (:min zone))
                (:id zone))))))))

(def anonymity-threshold 10)

(defn anonymize [n]
  (when (and (some? n) (>= n anonymity-threshold)) n))

(defn- anonymize-all [profiles zones m]
  (reduce
   (fn [m ks]
     (update-in m ks anonymize))
   m
   (for [k1 [:distance :travel-time]
         k2 profiles
         k3 (map :id ((if (= :distance k1) :distance :travel-time) zones))
         k4 [:ika_65_ :ika_15_64 :ika_0_14 :vaesto]]
     [k1 k2 k3 k4])))

(defn- resolve-zones
  [zones profiles pop-data pop-distances pop-travel-times]
  (->> pop-data
       :hits
       (map :_source)

        ;; Combine distance and travel-time calculations to demographics
       (map
        (fn [{:keys [id_nro] :as m}]
          (-> m
              (select-keys [:id_nro :ika_65_ :ika_15_64 :ika_0_14 :kunta
                            #_:naiset #_:miehet :vaesto :coords])
              (update :ika_65_ utils/->int)
              (update :ika_15_64 utils/->int)
              (update :ika_0_14 utils/->int)
              (update :kunta utils/->int)
              #_(update :naiset utils/->int)
              #_(update :miehet utils/->int)
              (update :vaesto utils/->int)
              (assoc :route
                     (into {}
                           (for [p profiles]
                             [p (get-in pop-travel-times [p id_nro])])))
              (assoc-in [:route :direct :distance-m]
                        (double
                         (Math/round
                          (get-in pop-distances [id_nro :distance-m])))))))

        ;; Resolve zones
       (map
        (fn [m]
          (reduce
           (fn [m1 profile]
             (let [d-zone  (resolve-zone zones m1 profile :distance)
                   tt-zone (resolve-zone zones m1 profile :travel-time)]
               (cond-> m1
                 d-zone  (assoc-in [:zone profile :distance] d-zone)
                 tt-zone (assoc-in [:zone profile :travel-time] tt-zone))))
           m
           (conj profiles :direct))))))

(defn- combine
  [sports-site-data sports-site-distances sports-site-travel-times
   pop-data pop-distances pop-travel-times
   school-data school-distances school-travel-times
   profiles zones]

  (let [pop-data-with-zones (resolve-zones zones
                                           profiles
                                           pop-data
                                           pop-distances
                                           pop-travel-times)]

    {:population
     (->> pop-data-with-zones
          (map
           (fn [{:keys [id_nro] :as m}]
             ;; TODO Figure out if it's OK to expose grid id's or not
             (-> m
                 (select-keys [:vaesto :coords :zone])
                 (update :vaesto anonymize)))))

     :population-stats
     (->> pop-data-with-zones
          ;; Sum by metric, profile, zone and demography
          (reduce
           (fn [res m]
             (reduce
              (fn [res2 profile]
                (let [d-zone  (get-in m [:zone profile :distance])
                      tt-zone (get-in m [:zone profile :travel-time])]
                  (cond-> res2

                    d-zone
                    (->
                     (update-in [:distance profile d-zone :ika_65_] #(+ (or % 0) (:ika_65_ m)))
                     (update-in [:distance profile d-zone :ika_15_64] #(+ (or % 0) (:ika_15_64 m)))
                     (update-in [:distance profile d-zone :ika_0_14] #(+ (or % 0) (:ika_0_14 m)))
                     (update-in [:distance profile d-zone :vaesto] #(+ (or % 0) (:vaesto m))))

                    tt-zone
                    (->
                     (update-in [:travel-time profile tt-zone :ika_65_] #(+ (or % 0) (:ika_65_ m)))
                     (update-in [:travel-time profile tt-zone :ika_15_64] #(+ (or % 0) (:ika_15_64 m)))
                     (update-in [:travel-time profile tt-zone :ika_0_14] #(+ (or % 0) (:ika_0_14 m)))
                     (update-in [:travel-time profile tt-zone :vaesto] #(+ (or % 0) (:vaesto m)))))))
              res
              (conj profiles :direct)))
           {})

        ;; Anonymize possibly "too small" population values
          (anonymize-all profiles zones))

     :schools
     (->> school-data
          :hits
          (map (juxt :_id :_source))
          (map
           (fn [[id m]]
             (-> m
                 (assoc :route
                        (into {}
                              (for [p profiles]
                                [p (get-in school-travel-times [p id])])))
                 (assoc-in [:route :direct :distance-m]
                           (double
                            (Math/round
                             (get-in school-distances [id :distance-m]))))))))

     :sports-sites
     (->> sports-site-data
          :hits
          (map (juxt :_id :_source))
          (map
           (fn [[id m]]
             (-> m
                 (select-keys [:type :name])
                 (assoc :route
                        (into {}
                              (for [p profiles]
                                [p (get-in sports-site-travel-times [p id])])))
                 (assoc-in [:route :direct :distance-m]
                           (double
                            (Math/round
                             (get-in sports-site-distances [id :distance-m]))))))))}))

(defn calc-distances-and-travel-times
  [search {:keys [search-fcoll buffer-fcoll distance-km profiles type-codes zones]}]
  (let [pop-data (future (get-population-data search buffer-fcoll distance-km))

        school-data (future (get-school-data search buffer-fcoll distance-km))

        sports-site-data (future (get-sports-site-data search
                                                       buffer-fcoll
                                                       distance-km
                                                       type-codes))

        profiles [:car :foot :bicycle]

        pop-distances (future
                        (calc-distances
                         search-fcoll
                         (->> @pop-data
                              :hits
                              (reduce
                               (fn [res m]
                                 (let [id     (-> m :_source :id_nro)
                                       coords (-> m :_source :coords)]
                                   (assoc res id {:coords coords})))
                               {}))))

        school-distances (future
                           (calc-distances
                            search-fcoll
                            (->> @school-data
                                 :hits
                                 (reduce
                                  (fn [res m]
                                    (let [id     (-> m :_id)
                                          coords (-> m :_source :coords)]
                                      (assoc res id {:coords coords})))
                                  {}))))

        sports-site-distances (future
                                (calc-distances
                                 search-fcoll
                                 (->> @sports-site-data
                                      :hits
                                      (reduce
                                       (fn [res m]
                                         (let [id     (-> m :_id)
                                               coords (-> m :_source :coords)]
                                           (assoc res id {:coords coords})))
                                       {}))))

        pop-travel-times (future
                           (calc-travel-times search-fcoll
                                              (:hits @pop-data)
                                              profiles
                                              (comp :id_nro :_source)))

        school-travel-times (future
                              (calc-travel-times search-fcoll
                                                 (:hits @school-data)
                                                 profiles
                                                 :_id))

        sports-site-travel-times (future
                                   (calc-travel-times search-fcoll
                                                      (:hits @sports-site-data)
                                                      profiles
                                                      :_id))]

    (combine @sports-site-data
             @sports-site-distances
             @sports-site-travel-times
             @pop-data
             @pop-distances
             @pop-travel-times
             @school-data
             @school-distances
             @school-travel-times
             profiles
             zones)))

;;; Report generation ;;;;

(def categories
  {:travel-time
   {:car     "Travel time by car"
    :bicycle "Travel time by bicycle"
    :foot    "Travel time by foot"}
   :distance
   {:car     "Distance by car"
    :bicycle "Disntace by bicycle"
    :foot    "Distance by foot"}})

(def population-fields
  {:ika_65_   "Age 65-"
   :ika_15_64 "Age 15-64"
   :ika_0_14  "Age 0-14"
   :vaesto    "People total"})

(def school-fields
  {:vaka        "Varhaiskasvatusyksikkö"
   :lukiot      "Lukiot"
   :peruskoulut "Peruskoulut"
   :perus+lukio "Perus- ja lukioasteen koulut"
   :erityis     "Peruskouluasteen erityiskoulut"})

(def school-fields-invert (set/map-invert school-fields))

(def sports-site-fields
  (into {}
        (map (juxt first (comp :fi :name second)))
        types/all))

(def sports-site-to-sub-cat-fields
  (into {}
        (map (juxt first (comp :fi :name types/sub-categories :sub-category second)))
        types/all))

(def sports-site-to-main-cat-fields
  (into {}
        (map (juxt first (comp :fi :name types/main-categories :main-category second)))
        types/all))

(def units
  {:travel-time "min"
   :distance    "km"})

(defn make-zone-name [zone suffix]
  (str (:min zone) "-" (:max zone) suffix))

(defn- create-sheet
  [data zones metric fields]
  (let [m-zones (metric zones)]

    (into [] cat
          (for [[profile p-name] (get categories metric)
                :let [by-zone (->> data
                                   (map
                                    (fn [m]
                                      (assoc m :zone (resolve-zone zones m profile metric))))
                                   (group-by :zone))]]
            (into [[p-name ""]] cat
                  (for [zone m-zones]
                    (into [[(make-zone-name zone (units metric)) ""]]
                          (for [[k f-name] fields]
                            [f-name (->> (:id zone)
                                         by-zone
                                         (map #(get % k))
                                         (reduce +))]))))))))

(defn create-population-sheet
  [data metric]
  (let [zones (-> data :zones metric (->> (utils/index-by :id)))
        stats (-> data :population :stats metric)]
    (into [] cat
          (for [[profile zone] stats]
            (into [[(get-in categories [metric profile])]] cat
                  (for [[zkey zpop] zone]
                    (into [[(make-zone-name (get zones zkey) (units metric)) ""]]
                          (for [[popk popv] zpop]
                            [(popk population-fields) popv]))))))))

(defn create-schools-sheet
  [data metric]
  (create-sheet  (->> data
                      :schools
                      :data
                      (map
                       (fn [m]
                         (let [k (school-fields-invert (:type m))
                               ks (set/difference (set (keys school-fields)) #{k})]
                           (apply assoc (into [m k 1] (interleave ks (repeat 0))))))))
                 (:zones data)
                 metric
                 school-fields))

(defn create-sports-sites-sheet
  [data metric]
  (create-sheet  (->> data
                      :sports-sites
                      :data
                      (map
                       (fn [m]
                         (let [k (-> m :type :type-code)
                               ks (set/difference (set (keys sports-site-fields)) #{k})]
                           (apply assoc (into [m k 1] (interleave ks (repeat 0))))))))
                 (:zones data)
                 metric
                 sports-site-fields))

(defn create-sports-sites-sheet-main-cat
  [data metric]
  (create-sheet  (->> data
                      :sports-sites
                      :data
                      (map
                       (fn [m]
                         (let [k  (-> m :type :type-code sports-site-to-main-cat-fields)
                               ks (set/difference
                                   (set (vals sports-site-to-main-cat-fields))
                                   #{k})]
                           (apply assoc (into [m k 1] (interleave ks (repeat 0))))))))
                 (:zones data)
                 metric
                 sports-site-to-main-cat-fields))

(defn create-report [data]
  ["Population travel-time" (create-population-sheet data :travel-time)
   "Population distance" (create-population-sheet data :distance)
   "Schools travel-time" (create-schools-sheet data :travel-time)
   "Schools distance" (create-schools-sheet data :distance)
   "Sports facility distance (all types)" (create-sports-sites-sheet data :distance)
   "Sports facility travel-time (all types)" (create-sports-sites-sheet data :travel-time)])

(comment
  (def data (read-string (slurp "/Users/tipo/kana.edn")))
  (-> data :zones :travel-time)

  (create-population-sheet data :travel-time)
  (create-population-sheet data :distance)
  (create-schools-sheet data :travel-time)
  (create-schools-sheet data :distance)
  (create-sports-sites-sheet data :travel-time)
  (create-sports-sites-sheet-main-cat data :travel-time)

  (require '[clojure.java.io :as io])


  (require '[cognitect.transit :as transit])
  
  (import [java.io ByteArrayInputStream ByteArrayOutputStream])  
  
  (def in (io/input-stream "/tmp/cc.transit"))
  (def reader (transit/reader in :json))
  
  (spit "/tmp/cc-output.edn" (transit/read reader))
  )

;; Monipuolisuustyökalu 

;; process:

;; - input1 desired area (point + radius OR polygon)
;; - input2 "max distance" for a relevant sports site
;; - input3 categorisation for sports sites (map from cat => type-codes)
;; 
;; - in parallel
;;   - get population grid (area)
;;   - get sports sites (area + buffer, max-distance..?)
;; - for each grid calculate "sport site" index (possibly weighted by population)
;; - euclidean distance (on JVM based on coords)
;; - route distance with OSRM
;; - return as GeoJSON

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
                                                     :features
                                                     first
                                                     :geometry
                                                     :coordinates
                                                     (->> (apply gis/->point)))
                                              
                                              distance (gis/distance-point g1 g2)]
                                          {:id                (:_id site)
                                           :type-code         (-> site
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
                  :type-code  (-> site :_source :type :type-code)
                  :distance-m (get-in res [:foot :distances pop-idx site-idx])
                  :duration-s (get-in res [:foot :durations pop-idx site-idx])})
               site-data)))
     pop-data)))

(defn- calc-indices
  [pop-data categories
   {:keys [max-distance-m population-weight-factor analysis] :as opts}]  
  (map
   (fn [pop-entry]
     (let [cats (reduce
                 (fn [m {:keys [type-codes name]}]                   
                   (assoc m name
                          (->> pop-entry
                               :sports-sites
                               (filter
                                (fn [site]
                                  (and (type-codes (:type-code site))
                                       (> max-distance-m (:distance-m site)))))
                               count)))
                 {}
                 categories)]
       (-> pop-entry
           (assoc :categories cats)
           (assoc :diversity-index (->> cats vals (map (partial min 1)) (apply +))))))
   pop-data))

(defn- ->diversity-geojson [pop-data]
  {:type     "FeatureCollection"
   :features (map
              (fn [pop-entry]
                {:type "Feature"
                 :geometry
                 {:type        "Point"
                  :coordinates (-> pop-entry :_source :coords gis/wkt-point->coords)}
                 :properties
                 (merge
                  {:id            (-> pop-entry :_source :id_nro)
                   :grid_id       (-> pop-entry :_source :grd_id)
                   :diversity_idx (:diversity-index pop-entry)
                   #_#_:age_0_14      (-> pop-entry :_source :ika_0_14 utils/->int anonymize)
                   #_#_:age_15_64     (-> pop-entry :_source :ika_15_64 utils/->int anonymize)
                   #_#_:age_65_       (-> pop-entry :_source :ika_65_ utils/->int anonymize)
                   #_#_:population    (-> pop-entry :_source :vaesto)}
                  (:categories pop-entry))})
              pop-data)})

(defn prepare-categories [categories]
  (map #(update % :type-codes set) categories))

(defn calc-diversity-indices
  [search
   {:keys [analysis-area-fcoll categories max-distance-m analysis-radius-km distance-mode]
    :or   {max-distance-m 800 analysis-radius-km 5}
    :as   opts}]
  
  (let [categories (prepare-categories categories)
        
        pop-data  (future (get-population-data search analysis-area-fcoll analysis-radius-km))
        site-data (future (get-sports-site-data search
                                                analysis-area-fcoll
                                                analysis-radius-km
                                                (into #{}
                                                      (mapcat :type-codes)
                                                      categories)))
        
        with-distances (condp = distance-mode
                         "euclid" (append-euclid-distances (:hits @pop-data) (:hits @site-data))
                         "route"  (append-route-distances (:hits @pop-data) (:hits @site-data))
                         (append-route-distances (:hits @pop-data) (:hits @site-data)))
        with-indices   (calc-indices with-distances categories opts)]    
    (->diversity-geojson with-indices)))

(comment

  (->> pop-data
       :hits
       (reduce
        (fn [res m]
          (let [id     (-> m :_source :id_nro)
                coords (-> m :_source :coords)]
            (assoc res id {:coords coords})))
        {}))
  
  (require '[lipas.backend.search])
  (require '[lipas.backend.config :as config])
  (require '[lipas.data.types :as types])
  config/default-config
  (def search (lipas.backend.search/create-cli (:search config/default-config)))
  (def point1
    {:type "Feature" :geometry {:type "Point" :coordinates [25.1 62.0]}})
  (def point1-fcoll {:type "FeatureCollection" :features [point1]})
  (def distance-km 5)
  (def pop-data (get-population-data search point1-fcoll distance-km))
  pop-data
  
  (def point-type-codes (->> types/all
                             (filter (comp #{"Point"} :geometry-type second))
                             (map first)))

  (def point2 (assoc-in point1 [:geometry :coordinates] [25.7473 62.2426]))
  (def point2-fcoll (assoc point1-fcoll :features [point2]))
  
  (def site-data (get-sports-site-data search point1-fcoll distance-km point-type-codes))
  site-data

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

  res3


  (def res4 (time (doall
                   (calc-diversity-indices search point3-fcoll categoriez
                                           {:max-distance-m 800 :analysis-radius-km 10}))))

  res4

  (def params
    {:analysis-area-fcoll point3-fcoll
     :categories          categoriez
     :max-distance-m      800 :analysis-radius-km 2
     :distance-mode       "route"})
  
  (time
   (doall (calc-diversity-indices search params)))

  (def temp *1)

  temp

  (require '[jsonista.core :as json2])

  (json2/write-value-as-string temp)
  
  (require '[clojure.spec.alpha :as spec])
  (spec/valid? :lipas.api.diversity-indices/req params)

  (spit "/Users/tipo/Desktop/diversity-req.json" (json/encode params))
  
  res3

  (keys res3)
  (-> res3 :features count)
  
  (-> pop-data :hits count)
  (->> pop-data3 :hits (map (comp :coords :_source)))
  
  (require '[cheshire.core :as json])
  (spit "/Users/tipo/Desktop/diversity-05km.geojson" (json/encode res1))
  (spit "/Users/tipo/Desktop/diversity-2km.geojson" (json/encode res2))
  (spit "/Users/tipo/Desktop/diversity-2km-lutakko.geojson" (json/encode res3))

  (->> site-data3
       :hits 
       (map (fn [s]
              (-> s
                  :_source
                  :search-meta
                  :location
                  :simple-geoms
                  :features
                  first
                  :geometry
                  :coordinates)))
       (map count)
       distinct)
  
  (time (append-euclid-distances (:hits pop-data) (:hits site-data)))

  (str/join ";" (repeat 5 "800"))
  
  )



;;;; DEMO ;;;;

(comment
  (def my-categories
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


  (def search (lipas.backend.search/create-cli (:search config/default-config)))

  (def point {:type     "Feature"
              :geometry {:type        "Point"
                         :coordinates [25.759742853 62.236192345]}})
  
  (def point-fcoll {:type "FeatureCollection" :features [point]})  

  (def params
    {:analysis-area-fcoll point-fcoll
     :categories          my-categories
     :max-distance-m      800
     :analysis-radius-km  10
     :distance-mode       "euclid"})

  ;; liikuntapaikat radius + max-distance

  ;; Postinumeroalueet...?
  
  (require '[lipas.backend.search])
  (require '[lipas.backend.config :as config])
  
  (time )
  (->>
   (calc-diversity-indices search params)
   :features
   (map (comp :diversity_idx :properties)))
  
  )



