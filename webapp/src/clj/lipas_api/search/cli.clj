(ns lipas-api.search.cli
  (:require [qbits.spandex :as es]
            [qbits.spandex.utils :as es-utils]
            [clojure.string]))

(defn es-get
  [client id]
  (es/request client {:method :get
                      :url    (es-utils/url [:legacy_sports_sites_current :_doc id])}))

(defn create-excursion-map-filter [excursion-map?]
  (when excursion-map?
    {:terms {:properties.mayBeShownInExcursionMapFi [true]}}))

(defn create-harrastuspassi-filter [harrastuspassi?]
  (when harrastuspassi?
    {:terms {:properties.mayBeShownInHarrastuspassiFi [true]}}))

(defn maybe-truncate [s]
  (if (> (count s) 23)
    (subs s 0 23)
    s))

(defn create-modified-after-filter
  [timestamp]
  (when timestamp {:range {:lastModified {:gt (maybe-truncate timestamp)}}}))

(defn create-geo-filter
  "Creates geo_distance filter:
  :geo_distance {:distance ... }
                 :point {:lon ... :lat ... }}"
  [geo-params]
  (when geo-params {:geo_distance geo-params}))

(defn create-filter
  [k coll]
  (when (seq coll) {:terms {k coll}}))

(defn create-filters
  [{:keys [city-codes type-codes close-to modified-after excursion-map? harrastuspassi?]}]
  (not-empty (remove nil? [(create-filter :location.city.cityCode city-codes)
                           (create-filter :type.typeCode type-codes)
                           (create-excursion-map-filter excursion-map?)
                           (create-harrastuspassi-filter harrastuspassi?)
                           (create-geo-filter close-to)
                           (create-modified-after-filter modified-after)])))

(defn append-filters
  [params query-map]
  (if-let [filters (create-filters params)]
    (assoc-in query-map [:bool] {:filter filters})
    query-map))

(defn append-search-string
  [params query-map]
  (if-let [qs (:search-string params)]
    (assoc-in query-map [:bool :must] [{:query_string {:query qs}}])
    query-map))

(defn resolve-query
  [params]
  (if-let [query (->> {}
                      (append-filters params)
                      (append-search-string params)
                      not-empty)]
    query
    {:match_all        {}}))

(defn fetch-sports-places
  [client params]
  (let [response (es/request client {:method :get
                                     :url    (es-utils/url [:legacy_sports_sites_current :_search])
                                     :body
                                     {:query            (resolve-query params)
                                      :track_total_hits true
                                      :size             (:limit params)
                                      :from             (* (:offset params) (:limit params))}})]
    response))

(defn more?
  "Returns true if result set was limited considering
  page-size and requested page, otherwise false."
  [results page-size page]
  (let [total (-> results :hits :total :value)
        n     (count (-> results :hits :hits))]
    (< (+ (* page page-size) n) total)))

(def partial? "Alias for `more?`" more?)
