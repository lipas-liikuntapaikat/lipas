(ns lipas.backend.api.v1.core
  (:require
   [clojure.core.async :as async]
   [lipas.backend.api.v1.search :as es]
   [lipas.backend.api.v1.sports-place :refer [filter-and-format
                                              format-sports-place-es]]
   [lipas.backend.api.v1.transform :as transform]
   [lipas.backend.api.v1.util :refer [only-non-nil-recur] :as util]
   [lipas.backend.search :as search]
   [taoensso.timbre :as log]))

(defn fetch-sports-places-es
  "Fetches list of sports-places from ElasticSearch backend."
  [search locale params fields]
  (try
    (log/debug "Fetching sports places"
               {:locale locale
                :params (dissoc params :search-string) ; Don't log potentially sensitive search data
                :field-count (count fields)})

    (let [start-time (System/currentTimeMillis)
          index-name (get-in search [:indices :legacy-sports-site :search])
          data (:body (es/fetch-sports-places (:client search) index-name params))
          places (map :_source (-> data :hits :hits))
          ;; Production behavior: when no fields specified, return only sportsPlaceId
          ;; When fields are specified, always include sportsPlaceId
          fields (if (empty? fields)
                   [:sportsPlaceId] ; Default to only sportsPlaceId (production behavior)
                   (conj fields :sportsPlaceId)) ; Add sportsPlaceId when specific fields requested
          partial? (es/more? data (:limit params) (:offset params))
          duration (- (System/currentTimeMillis) start-time)
          result {:partial? partial?
                  :total (-> data :hits :total :value)
                  :results (mapv (comp only-non-nil-recur
                                       (partial filter-and-format locale fields)) places)}]

      (log/debug "Sports places search completed"
                 {:duration-ms duration
                  :result-count (count (:results result))
                  :total (:total result)
                  :partial? partial?})
      result)

    (catch clojure.lang.ExceptionInfo ex
      (let [error-data (ex-data ex)
            status (:status error-data)]
        (log/error ex "Elasticsearch error in sports places search"
                   {:params (dissoc params :search-string)
                    :status status
                    :error-data error-data})
        (throw (ex-info "Sports places search failed"
                        {:type :search-error
                         :params (dissoc params :search-string)
                         :status status}
                        ex))))

    (catch Exception ex
      (log/error ex "Unexpected error in sports places search"
                 {:params (dissoc params :search-string)})
      (throw (ex-info "Unexpected error in sports places search"
                      {:type :unexpected-error
                       :params (dissoc params :search-string)}
                      ex)))))

(defn fetch-sports-place-es
  "Fetches single sports-place from search engine index."
  [search locale sports-place-id]
  (try
    (let [index-name (get-in search [:indices :legacy-sports-site :search])]
      (when-let [response (es/by-id (:client search) index-name sports-place-id)]
        (when-let [source (-> response :body :_source)]
          (-> source
              (format-sports-place-es locale)
              only-non-nil-recur))))
    (catch clojure.lang.ExceptionInfo ex
      (let [status (-> ex ex-data :status)]
        (cond
          (= 404 status)
          (do
            (log/debug "Sports place not found" {:id sports-place-id})
            nil)

          :else
          (do
            (log/error ex "Elasticsearch error fetching sports place"
                       {:id sports-place-id :status status})
            (throw (ex-info "Failed to fetch sports place"
                            {:type :search-error
                             :sports-place-id sports-place-id
                             :status status}
                            ex))))))
    (catch Exception ex
      (log/error ex "Unexpected error fetching sports place"
                 {:id sports-place-id})
      (throw (ex-info "Unexpected error fetching sports place"
                      {:type :unexpected-error
                       :sports-place-id sports-place-id}
                      ex)))))

(defn format-timestamp-for-es
  "Converts timestamp from 'yyyy-MM-dd HH:mm:ss.SSS' format to ISO 8601 format for Elasticsearch."
  [timestamp-str]
  (when timestamp-str
    (try
      (let [formatter (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss.SSS")
            local-dt (java.time.LocalDateTime/parse timestamp-str formatter)
            instant (.toInstant local-dt java.time.ZoneOffset/UTC)]
        (.toString instant))
      (catch Exception e
        (log/warn "Failed to parse timestamp, using as-is" {:timestamp timestamp-str :error (.getMessage e)})
        timestamp-str))))

(defn fetch-deleted-sports-places-es
  "Fetches list of deleted sports-places from ElasticSearch backend using scroll API.
   Returns sports places that were previously published but are now in non-published status
   after the given timestamp."
  [search-component since-timestamp]
  (try
    (log/debug "Fetching deleted sports places" {:since since-timestamp})

    (when-not since-timestamp
      (throw (ex-info "Missing required parameter: since-timestamp"
                      {:type :invalid-input
                       :parameter :since-timestamp})))

    (let [client (:client search-component)
          idx-name (get-in search-component [:indices :sports-site :search])
          formatted-timestamp (format-timestamp-for-es since-timestamp)
          ;; Use scroll API for large result sets (production has 484K+ deleted places)
          ;; Only fetch the two fields we need to minimize data transfer
          scroll-chan (search/scroll client idx-name
                                     {:query {:bool {:must [{:range {:event-date {:gte formatted-timestamp}}}
                                                            {:terms {:status.keyword ["out-of-service-permanently" "incorrect-data"]}}]}}
                                      :sort [{:event-date {:order "desc"}}]
                                      :_source {:includes ["lipas-id" "event-date"]}})
          ;; Collect all pages from scroll
          result (loop [results []]
                   (if-let [page (async/<!! scroll-chan)]
                     (let [hits (-> page :body :hits :hits)
                           transformed (map (fn [hit]
                                              (let [source (:_source hit)]
                                                {:sportsPlaceId (:lipas-id source)
                                                 :deletedAt (transform/UTC->last-modified (:event-date source))}))
                                            hits)]
                       (recur (into results transformed)))
                     results))]

      (log/debug "Deleted sports places search completed"
                 {:count (count result) :since since-timestamp})
      result)

    (catch clojure.lang.ExceptionInfo ex
      (let [error-data (ex-data ex)
            status (:status error-data)]
        (if (= :invalid-input (:type error-data))
          (throw ex) ; Re-throw validation errors as-is
          (do
            (log/error ex "Elasticsearch error fetching deleted sports places"
                       {:since since-timestamp :status status})
            (throw (ex-info "Failed to fetch deleted sports places"
                            {:type :search-error
                             :since since-timestamp
                             :status status}
                            ex))))))

    (catch Exception ex
      (log/error ex "Unexpected error fetching deleted sports places"
                 {:since since-timestamp})
      (throw (ex-info "Unexpected error fetching deleted sports places"
                      {:type :unexpected-error
                       :since since-timestamp}
                      ex)))))
