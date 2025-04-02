(ns lipas-api.search.cli
  (:require [qbits.spandex :as es]
            [qbits.spandex.utils :as es-utils]
            [clojure.core.async :as async]
            [clojure.string]))

(defn create-cli
  [{:keys [hosts user password]}]
  (es/client {:hosts       hosts
              :http-client {:basic-auth {:user     user
                                         :password password}}}))

(def date-format "yyyy-MM-dd HH:mm:ss.SSS")

(def search-mappings-legacy
  {:mappings {:sportsplace {:properties {:location     {:type "geo_point"}
                                         :lastModified {:type   "date"
                                                        :format date-format}}}}})

(def search-mappings
  {:mappings {:properties   {:location {:type "geo_point"}}
              :lastModified {:type   "date"
                             :format "yyyy-MM-dd HH:mm:ss.SSS"}}})


(def sports-places-mappings-legacy
  {:settings
   {:max_result_window 50000}
   :mappings
   {:sports_place
    {:properties {:location.coordinates.wgs84 {:type "geo_point"}
                  :lastModified               {:type   "date"
                                               :format date-format}}}}})

(def sports-places-mappings
  {:settings
   {:max_result_window 50000}
   :mappings
   {:properties {:location.coordinates.wgs84 {:type "geo_point"}
                 :location.geom-coll         {:type "geo_shape"}
                 :lastModified               {:type   "date"
                                              :format date-format}}}})

(def indices {:sportsplaces     search-mappings
              :sports_places    sports-places-mappings
              :test             sports-places-mappings})

(defn temp-idx-name
  "Returns temporary index name generated from current timestamp.
  Example: \"2017-08-13T14:44:42.761\""
  []
  (-> (java.time.LocalDateTime/now)
      str
      (clojure.string/lower-case)
      (clojure.string/replace #"[:|.]" "-")))

(defn create-index
  [client index mappings]
  (es/request client {:method :put
                      :url    (es-utils/url [index])
                      :body   mappings}))

(defn delete-index
  [client index]
  (es/request client {:method :delete
                      :url    (es-utils/url [index])}))

(defn index
  [client {:keys [index type id data sync?]
           :or   {index "sports_places" type "_doc"}}]
  (es/request client {:method       :put
                      :url          (es-utils/url [index type id])
                      :body         data
                      :query-string (when sync? {:refresh "wait_for"})}))

(defn delete
  [client {:keys [index type id sync?]
           :or   {index "sports_places" type "_doc"}}]
  (es/request client {:method       :delete
                      :url          (es-utils/url [index type id])
                      :query-string (when sync? {:refresh "wait_for"})}))

(defn get
  [client {:keys [index type id] :or {index "sports_places"
                                      type "_doc"}}]
  (es/request client {:method :get
                      :url    (es-utils/url [index type id])}))

(defn bulk-index
  ([client data]
   (let [{:keys [input-ch output-ch]}
         (es/bulk-chan client {:flush-threshold         100
                               :flush-interval          5000
                               :max-concurrent-requests 3})]
     (async/put! input-ch data)
     (future (loop [] (async/<!! output-ch))))))


(defn current-idxs
  "Returns a coll containing current index(es) pointing to alias."
  [client {:keys [alias]}]
  (let [res (es/request client {:method :get
                                :url (es-utils/url ["*" "_alias" alias])
                                :exception-handler (constantly nil)})]
    (not-empty (keys (:body res)))))

(defn swap-alias
  "Swaps alias to point to new-idx. Possible existing aliases will be removed."
  [client {:keys [new-idx alias] :or {alias "sports_places"}}]
  (let [old-idxs (current-idxs client {:alias alias})
        actions  (-> (map #(hash-map :remove {:index % :alias alias}) old-idxs)
                     (conj {:add {:index new-idx :alias alias}}))]
    (es/request client {:method :post
                        :url    (es-utils/url [:_aliases])
                        :body   {:actions actions}})
    old-idxs))

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
  (es/request client {:method :get
                      :url    (es-utils/url [:sports_places :_search])
                      :body
                      {:query            (resolve-query params)
                       :track_total_hits true
                       :size             (:limit params)
                       :from             (* (:offset params) (:limit params))}}))

(defn more?
  "Returns true if result set was limited considering
  page-size and requested page, otherwise false."
  [results page-size page]
  (let [total (-> results :hits :total :value)
        n     (count (-> results :hits :hits))]
    (< (+ (* page page-size) n) total)))

(def partial? "Alias for `more?`" more?)
