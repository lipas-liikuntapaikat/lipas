(ns lipas.backend.analysis.common
  (:require
   [clojure.set :as set]
   [lipas.backend.search :as search]))

(def population-index "vaestoruutu_1km_2019_kp")
(def population-index-high-def "vaestoruutu_250m_2020_kp")
(def population-high-def-threshold-km 10)

(def schools-index "schools")
(def sports-sites-index "sports_sites_current")
(def default-statuses #{"planning" "planned" "active" "out-of-service-temporarily"})

(defn build-query [geom distance-km]
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

(defn get-sports-site-data
  ([search fcoll distance-km type-codes]
   (get-sports-site-data search fcoll distance-km type-codes default-statuses))
  ([search fcoll distance-km type-codes statuses]
   (let [geom  (-> fcoll :features first)
         query (-> (build-query geom distance-km)
                   (assoc :_source [:name
                                    :type.type-code
                                    :search-meta.location.simple-geoms])
                   (update-in [:query :bool :filter :geo_shape] set/rename-keys
                              {:coords :search-meta.location.geometries})
                   (cond->
                       (or (seq type-codes) (seq statuses)) (assoc-in [:query :bool :must] [])
                       (seq type-codes) (update-in [:query :bool :must] conj
                                                   {:terms {:type.type-code type-codes}})
                       (seq statuses)   (update-in [:query :bool :must] conj
                                                   {:terms {:status statuses}})))]
     (-> (search/search search sports-sites-index query)
         :body
         :hits))))

(defn get-es-data*
  [idx-name search fcoll distance-km]
  (let [geom (-> fcoll :features first)]
    (-> (search/search search idx-name (build-query geom distance-km))
        :body
        :hits)))

(defn get-population-data
  [search fcoll distance-km]
  (let [idx (if (> distance-km population-high-def-threshold-km)
              population-index
              population-index-high-def)]
    (get-es-data* idx search fcoll distance-km)))

(defn get-school-data
  [search fcoll distance-km]
  (get-es-data* schools-index search fcoll distance-km))

(def anonymity-threshold 10)

(defn anonymize [n]
  (when (and (some? n) (>= n anonymity-threshold)) n))
