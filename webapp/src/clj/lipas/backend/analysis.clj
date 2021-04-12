(ns lipas.backend.analysis
  (:require
   [clojure.string :as str]
   [clojure.set :as set]
   [lipas.backend.search :as search]
   [lipas.utils :as utils]
   [lipas.backend.gis :as gis]
   [lipas.backend.osrm :as osrm]))

(def population-index "vaestoruutu_1km_2019_kp")
(def schools-index "schools")
(def sports-sites-index "sports_sites_current")

(defn- build-query [geom distance-km]
  {:size 1000
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
  (get-es-data* population-index search fcoll distance-km))

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
  (let [destinations (if (gis/point? search-fcoll)
                       [(-> search-fcoll :features first :geometry :coordinates
                            (->> (str/join ",")))]
                       (-> search-fcoll
                           gis/->single-linestring-coords
                           (->> (map #(str/join "," %)))))

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

(defn- combine
  [sports-site-data sports-site-distances sports-site-travel-times
   pop-data pop-distances pop-travel-times
   school-data school-distances school-travel-times
   profiles]
  {:population
   (->> pop-data
        :hits
        (map :_source)
        (map
         (fn [{:keys [id_nro] :as m}]
           (-> m
               (select-keys [:id_nro :ika_65_ :ika_15_64 :ika_0_14 :kunta
                             :naiset :miehet :vaesto :coords])
               (update :ika_65_ utils/->int)
               (update :ika_15_64 utils/->int)
               (update :ika_0_14 utils/->int)
               (update :kunta utils/->int)
               (update :naiset utils/->int)
               (update :miehet utils/->int)
               (update :vaesto utils/->int)
               (assoc :route
                      (into {}
                            (for [p profiles]
                              [p (get-in pop-travel-times [p id_nro])])))
               (assoc-in [:route :direct :distance-m]
                         (double
                          (Math/round
                           (get-in pop-distances [id_nro :distance-m]))))))))
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
                           (get-in sports-site-distances [id :distance-m]))))))))})

(defn calc-distances-and-travel-times
  [search {:keys [search-fcoll buffer-fcoll distance-km profiles type-codes]}]
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
             profiles)))

(comment

  )
