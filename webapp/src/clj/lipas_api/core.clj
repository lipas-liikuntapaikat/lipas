(ns lipas-api.core
  (:require
            [lipas-api.search.cli :as es]
            [lipas-api.sports-places :refer [filter-and-format
                                             format-sports-place-es]]
            [lipas-api.util :refer [only-non-nil-recur] :as util]))

(defn fetch-sports-places-es
  "Fetches list of sports-places from ElasticSearch backend."
  [search locale params fields]
  (let [data (:body (es/fetch-sports-places (:client search) params))
        places (map :_source (-> data :hits :hits))
        fields (conj fields :sportsPlaceId)
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
