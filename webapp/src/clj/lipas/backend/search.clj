(ns lipas.backend.search
  (:require [qbits.spandex :as es]
            [qbits.spandex.utils :as es-utils]
            [clojure.core.async :as async]))

(def es-type "_doc") ; See https://bit.ly/2wslBqY

(defn create-cli
  [{:keys [hosts user password]}]
  (es/client {:hosts       hosts
              :http-client {:basic-auth {:user     user
                                         :password password}}}))

(def mappings
  {:sports-sites
   {:settings
    {:max_result_window 50000}
    :mappings
    {:_doc
     {:properties
      {:location.geometries.features
       {:type "nested"
        :properties
        {:geometry
         {:type             "geo_shape"
          :ignore_malformed true}
         :type {:type "keyword"}}}
       :search-meta.location.wgs84-point
       {:type "geo_point"}}}}}})

(defn gen-idx-name
  "Returns index name generated from current timestamp that is
  a valid ElasticSearch alias. Example: \"2017-08-13t14-44-42-761\""
  []
  (-> (java.time.LocalDateTime/now)
      str
      (clojure.string/lower-case)
      (clojure.string/replace #"[:|.]" "-")))

(defn create-index!
  [client index mappings]
  (es/request client {:method :put
                      :url    (es-utils/url [index])
                      :body   mappings}))

(defn delete-index!
  [client index]
  (es/request client {:method :delete
                      :url    (es-utils/url [index])}))

(defn index!
  ([client idx-name id-fn data]
   (index! client idx-name id-fn data false))
  ([client idx-name id-fn data sync?]
   (es/request client {:method       :put
                       :url          (es-utils/url [idx-name es-type (id-fn data)])
                       :body         data
                       :query-string (when sync? {:refresh "wait_for"})})))

(defn delete!
  [client idx-name id]
  (es/request client {:method :delete
                      :url    (es-utils/url [idx-name es-type id])}))

(defn bulk-index!
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

(defn swap-alias!
  "Swaps alias to point to new-idx. Possible existing aliases will be removed."
  [client {:keys [new-idx alias] :or {alias "sports_sites"}}]
  (let [old-idxs (current-idxs client {:alias alias})
        actions  (-> (map #(hash-map :remove {:index % :alias alias}) old-idxs)
                     (conj {:add {:index new-idx :alias alias}}))]
    (es/request client {:method :post
                        :url    (es-utils/url [:_aliases])
                        :body   {:actions actions}})
    old-idxs))

(defn search
  [client idx-name params]
  (es/request client {:method :get
                      :url    (es-utils/url [idx-name :_doc :_search])
                      :body   params}))

(defn scroll
  [client idx-name params]
  (es/scroll-chan client {:method :get
                          :url    (es-utils/url [idx-name :_doc :_search])
                          :body   params}))

(defn more?
  "Returns true if result set was limited considering
  page-size and requested page, otherwise false."
  [results page-size page]
  (let [total (-> results :hits :total)
        n     (count (-> results :hits :hits))]
    (< (+ (* page page-size) n) total)))

(def partial? "Alias for `more?`" more?)

(defn wrap-es-bulk
  [es-index es-type id-fn entry]
  [{:index {:_index es-index
            :_type  es-type
            :_id    (id-fn entry)}}
   entry])

(defn ->bulk [es-index id-fn data]
  (reduce into (map (partial wrap-es-bulk es-index es-type id-fn) data)))
