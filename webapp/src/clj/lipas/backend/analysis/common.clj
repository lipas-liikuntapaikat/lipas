(ns lipas.backend.analysis.common
  (:require
   [clojure.set :as set]
   [lipas.backend.search :as search]))

(def population-high-def-threshold-km 10)
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
        :relation "intersects"}}}}}})

(defn get-sports-site-data
  ([search fcoll distance-km type-codes]
   (get-sports-site-data search fcoll distance-km type-codes default-statuses))
  ([{:keys [indices client]} fcoll distance-km type-codes statuses]
   (let [idx-name (get-in indices [:sports-sites :search])
         geom     (-> fcoll :features first)
         query    (-> (build-query geom distance-km)
                   (assoc :_source [:name
                                    :status
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
     (-> (search/search client idx-name query)
         :body
         :hits))))

(defn get-es-data*
  [idx-name client fcoll distance-km]
  (let [geom (-> fcoll :features first)]
    (-> (search/search client idx-name (build-query geom distance-km))
        :body
        :hits)))

(defn get-population-data
  [{:keys [indices client]} fcoll distance-km]
  (let [idx (if (> distance-km population-high-def-threshold-km)
              (get-in indices [:analysis :population])
              (get-in indices [:analysis :population-high-def]))]
    (get-es-data* idx client fcoll distance-km)))

(defn get-school-data
  [{:keys [indices client]} fcoll distance-km]
  (let [idx-name (get-in indices [:analysis :schools])]
    (get-es-data* idx-name client fcoll distance-km)))

(def anonymity-threshold 10)

(defn anonymize [n]
  (when (and (some? n) (>= n anonymity-threshold)) n))
