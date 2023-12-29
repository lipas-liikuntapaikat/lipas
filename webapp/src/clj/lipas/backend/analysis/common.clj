(ns lipas.backend.analysis.common
  (:require
   [clojure.data.csv :as csv]
   [clojure.java.io :as io]
   [clojure.set :as set]
   [clojure.walk :as walk]
   [lipas.backend.search :as search]
   [lipas.utils :as utils]
   [taoensso.timbre :as log]))

(def population-high-def-threshold-km 10)
(def default-statuses #{"planning" "planned" "active" "out-of-service-temporarily"})

(def mappings
  {:population-grid
   {:mappings
    {:properties
     {:coords {:type "geo_point"}}}}})

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
   (let [idx-name (get-in indices [:sports-site :search])
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

#_(defn coerce-population-grid-int
  [s]
  (if (#{"-1"} s) nil (utils/->int s)))

(defn coerce-population-grid-item
  [m]
  (-> m
      #_(update :ykoord utils/->int)
      #_(update :xkoord utils/->int)
      #_(update :ika_65_ coerce-population-grid-int)
      #_(update :ika_15_64 coerce-population-grid-int)
      #_(update :ika_0_14 coerce-population-grid-int)
      #_(update :vuosi utils/->int)
      #_(update :vaesto coerce-population-grid-int)
      (assoc :coords (:WKT m))
      (dissoc :WKT)))

(defn *seed-population-grid-from-csv!
  [{:keys [client]} csv-path idx-name idx-alias batch-size]
  (assert idx-name "Index name is required")
  (assert batch-size "Batch size is required")

  (with-open [rdr (io/reader csv-path)]
    (log/info "Creating index" idx-name)
    (search/create-index! client idx-name (:population-grid mappings))

    (log/info "Starting to process" csv-path)
    (doseq [part (->> (csv/read-csv rdr)
                      utils/csv-data->maps
                      (map walk/keywordize-keys)
                      (map coerce-population-grid-item)
                      (partition-all batch-size))]

      (log/info "Writing batch of" batch-size "to" idx-name)
      (->> part
           (search/->bulk idx-name :grd_id)
           (search/bulk-index-sync! client))))

  (log/info "Swapping alias" idx-alias "to point to" idx-name)
  (search/swap-alias! client {:new-idx idx-name :alias idx-alias})

  (log/info "All done!"))

(defn seed-population-1km-grid-from-csv!
  [{:keys [indices] :as search} csv-path]
  (let [idx-name   (str "population-1km-" (search/gen-idx-name))
        idx-alias  (get-in indices [:analysis :population])
        batch-size 100]
    (*seed-population-grid-from-csv! search csv-path idx-name idx-alias batch-size)))

(defn seed-population-250m-grid-from-csv!
  [{:keys [indices] :as search} csv-path]
  (let [idx-name   (str "population-250m-" (search/gen-idx-name))
        idx-alias  (get-in indices [:analysis :population-high-def])
        batch-size 100]
    (*seed-population-grid-from-csv! search csv-path idx-name idx-alias batch-size)))
