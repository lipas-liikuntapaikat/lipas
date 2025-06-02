(ns lipas-api.core
  (:require
   [lipas-api.search.cli :as es]
   [lipas-api.sports-places :refer [filter-and-format
                                    format-sports-place-es]]
   [lipas-api.util :refer [only-non-nil-recur] :as util]
   [qbits.spandex :as elastic]
   [qbits.spandex.utils :as es-utils]))

(defn fetch-sports-places-es
  "Fetches list of sports-places from ElasticSearch backend."
  [search locale params fields]
  (let [data (:body (es/fetch-sports-places (:client search) params))
        places (map :_source (-> data :hits :hits))
        ;; Only add sportsPlaceId to fields if specific fields are requested
        ;; When fields is empty, we want all fields (handled in filter-and-format)
        fields (if (empty? fields)
                 fields ; Keep empty to get all fields
                 (conj fields :sportsPlaceId)) ; Add sportsPlaceId when specific fields requested
        partial? (es/more? data (:limit params) (:offset params))]
    {:partial? partial?
     :total (-> data :hits :total :value)
     :results (mapv (comp only-non-nil-recur
                          (partial filter-and-format locale fields)) places)}))

(defn fetch-sports-place-es
  "Fetches single sports-place from search engine index."
  [search locale sports-place-id]
  (try
    (-> (es/es-get search sports-place-id)
        :body
        :_source
        (format-sports-place-es locale)
        only-non-nil-recur)
    (catch Exception ex (println ex))))

(defn fetch-deleted-sports-places-es
  "Fetches list of deleted sports-places from ElasticSearch backend.
   Returns sports places that were previously published but are now in non-published status
   after the given timestamp."
  [search since-timestamp]
  (let [client (:client search)
        response (elastic/request client
                                  {:method :get
                                   :url (es-utils/url [:legacy_sports_sites_current :_search])
                                   :body {:query {:bool {:must [{:range {:lastModified {:gte since-timestamp}}}]
                                                         :must_not [{:term {:status "published"}}]}}
                                          :sort [{:lastModified {:order "desc"}}]
                                          :_source ["sportsPlaceId" "lastModified"]}})
        hits (-> response :body :hits :hits)]
    (map (fn [hit]
           (let [source (:_source hit)]
             {:sportsPlaceId (:sportsPlaceId source)
              :deletedAt (str (:lastModified source))}))
         hits)))
