(ns legacy-api.search
  (:require [qbits.spandex :as es]
            [qbits.spandex.utils :as es-utils]
            [clojure.string :as str]
            [taoensso.timbre :as log]))

(defn sanitize-search-string
  "Sanitizes search string to prevent injection attacks and malformed queries."
  [s]
  (when s
    (-> s
        str/trim
        ;; Remove potentially dangerous characters
        (str/replace #"[<>\"']" "")
        ;; Remove excessive whitespace
        (str/replace #"\s+" " ")
        ;; Ensure we have content after sanitization
        (as-> clean (when (and (seq clean) (> (count clean) 0)) clean)))))

(defn validate-geo-params
  "Validates geographic filter parameters.
  Expected structure: {:distance \"1000m\" :field :location.coordinates.wgs84 :point {:lon 24.9 :lat 60.1}}"
  [geo-params]
  (when geo-params
    (let [{:keys [distance point field]} geo-params]
      (when-not (and distance point field)
        (throw (ex-info "Invalid geo parameters: missing distance, field, or point"
                        {:type :invalid-input
                         :geo-params geo-params})))

      (when-not (or (string? distance) (and (number? distance) (pos? distance)))
        (throw (ex-info "Invalid geo distance: must be a positive number or string with unit"
                        {:type :invalid-input
                         :distance distance})))

      (let [{:keys [lon lat]} point]
        (when-not (and (number? lon) (number? lat))
          (throw (ex-info "Invalid geo coordinates: lat/lon must be numbers"
                          {:type :invalid-input
                           :point point})))

        (when-not (and (<= -180 lon 180) (<= -90 lat 90))
          (throw (ex-info "Invalid geo coordinates: lat/lon out of valid range"
                          {:type :invalid-input
                           :point point}))))
      geo-params)))

(defn by-id
  [client index-name id]
  (try
    (when-not id
      (throw (ex-info "Missing required parameter: id"
                      {:type :invalid-input
                       :parameter :id})))

    (log/debug "Fetching sports place by ID" {:id id :index index-name})
    (es/request client {:method :get
                        :url (es-utils/url [index-name :_doc id])})

    (catch clojure.lang.ExceptionInfo ex
      (let [error-data (ex-data ex)
            status (:status error-data)]
        (if (= :invalid-input (:type error-data))
          (throw ex)
          (do
            (log/error ex "Elasticsearch error fetching by ID" {:id id :status status})
            (throw (ex-info "Failed to fetch sports place by ID"
                            {:type :search-error
                             :id id
                             :status status}
                            ex))))))

    (catch Exception ex
      (log/error ex "Unexpected error fetching sports place by ID" {:id id})
      (throw (ex-info "Unexpected error fetching sports place by ID"
                      {:type :unexpected-error
                       :id id}
                      ex)))))

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
  "Creates geo_distance filter for ES.
  Input: {:distance \"1000m\" :field :location.coordinates.wgs84 :point {:lon 24.9 :lat 60.1}}
  Output: {:geo_distance {:distance \"1000m\" :location.coordinates.wgs84 {:lon 24.9 :lat 60.1}}}"
  [geo-params]
  (when geo-params
    (let [{:keys [distance field point]} (validate-geo-params geo-params)]
      (log/debug "Creating geo filter" {:distance distance :field field :point point})
      {:geo_distance {:distance distance
                      field point}})))

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
  "Adds search string to query using multi_match with name field boosting.
  Production behavior: results with search term in 'name' field rank highest."
  [params query-map]
  (if-let [qs (sanitize-search-string (:search-string params))]
    (do
      (log/debug "Adding search string to query" {:search-string-length (count qs)})
      ;; Use multi_match with field boosting for better relevance
      ;; name^10 means matches in name field are 10x more important
      ;; type.name^5 for type name matches
      ;; marketingName^3 for marketing name
      (assoc-in query-map [:bool :must]
                [{:multi_match {:query qs
                                :fields ["name^10"
                                         "type.name^5"
                                         "marketingName^3"
                                         "location.address^2"
                                         "location.city.name^2"
                                         "*"]
                                :type "best_fields"
                                :operator "and"}}]))
    query-map))

(defn resolve-query
  [params]
  (if-let [query (->> {}
                      (append-filters params)
                      (append-search-string params)
                      not-empty)]
    query
    {:match_all {}}))

(defn fetch-sports-places
  [client index-name params]
  (try
    (log/debug "Executing sports places search"
               {:params (dissoc params :search-string) :index index-name}) ; Don't log search string

    (let [query (resolve-query params)
          ;; Production behavior:
          ;; - When searchString is provided: use relevance scoring (no explicit sort)
          ;; - When no searchString: sort by sportsPlaceId ascending
          has-search? (some? (sanitize-search-string (:search-string params)))
          sort-config (when-not has-search?
                        [{:sportsPlaceId {:order "asc"
                                          :unmapped_type "long"}}])
          body (cond-> {:query query
                        :track_total_hits true
                        :size (:limit params)
                        :from (* (:offset params) (:limit params))}
                 sort-config (assoc :sort sort-config))
          response (es/request client {:method :get
                                       :url (es-utils/url [index-name :_search])
                                       :body body})]
      (log/debug "Sports places search query executed successfully"
                 {:total-hits (-> response :body :hits :total :value)
                  :returned-hits (count (-> response :body :hits :hits))
                  :has-search? has-search?})
      response)

    (catch clojure.lang.ExceptionInfo ex
      (let [error-data (ex-data ex)
            status (:status error-data)]
        (if (= :invalid-input (:type error-data))
          (throw ex) ; Re-throw validation errors as-is
          (do
            (log/error ex "Elasticsearch error in fetch-sports-places"
                       {:params (dissoc params :search-string) :status status})
            (throw (ex-info "Failed to fetch sports places"
                            {:type :search-error
                             :params (dissoc params :search-string)
                             :status status}
                            ex))))))

    (catch Exception ex
      (log/error ex "Unexpected error in fetch-sports-places"
                 {:params (dissoc params :search-string)})
      (throw (ex-info "Unexpected error in fetch-sports-places"
                      {:type :unexpected-error
                       :params (dissoc params :search-string)}
                      ex)))))

(defn more?
  "Returns true if result set was limited considering
  page-size and requested page, otherwise false."
  [results page-size page]
  (let [total (-> results :hits :total :value)
        n (count (-> results :hits :hits))]
    (< (+ (* page page-size) n) total)))
