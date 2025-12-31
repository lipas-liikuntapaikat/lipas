(ns lipas.backend.search
  (:require
   [clojure.core.async :as async]
   [clojure.string :as str]
   [lipas.data.prop-types :as prop-types]
   [qbits.spandex :as es]
   [qbits.spandex.utils :as es-utils]))

(def es-type "_doc") ; See https://bit.ly/2wslBqY

(def legacy-date-format "yyyy-MM-dd HH:mm:ss.SSS")

(defn create-cli
  [{:keys [hosts user password]}]
  (es/client {:hosts       hosts
              :http-client {:basic-auth {:user     user
                                         :password password}}}))

;; ES 8.x auto-detects float arrays as dense_vector, breaking GeoJSON coordinates.
;; We explicitly disable indexing for raw GeoJSON structures since we use
;; search-meta.location.geometries (geo_shape) for spatial queries.
(def geojson-coordinates-mapping
  "Mapping to prevent ES 8.x from inferring dense_vector for GeoJSON coordinates"
  {:properties
   {:features
    {:properties
     {:geometry
      {:properties
       {:coordinates {:enabled false}}}}}}})

(def mappings
  {:sports-sites
   {:settings
    {:max_result_window 60000
     :index {:mapping {:total_fields {:limit 2000}}}}
    :mappings
    {;; Disable automatic date detection to prevent ES 8.x from inferring
     ;; date types from random test data strings
     :date_detection false
     ;; Dynamic templates to handle ES 8.x stricter type inference
     ;; Forces all integers to be doubles to avoid long vs float conflicts
     :dynamic_templates
     [{:integers_as_doubles
       {:match_mapping_type "long"
        :mapping {:type "double"}}}]
     :properties
     {;; Explicit date mapping since date_detection is disabled
      :event-date {:type "date"}
      :search-meta.location.wgs84-point
      {:type "geo_point"}
      :search-meta.location.wgs84-center
      {:type "geo_point"}
      :search-meta.location.wgs84-end
      {:type "geo_point"}
      :search-meta.location.geometries
      {:type "geo_shape"}
      ;; Prevent ES 8.x dense_vector inference for GeoJSON
      :location.geometries geojson-coordinates-mapping
      :search-meta.location.simple-geoms geojson-coordinates-mapping
      ;; All activity types with routes that have geometries
      :activities.cycling.routes.geometries geojson-coordinates-mapping
      :activities.paddling.routes.geometries geojson-coordinates-mapping
      :activities.fishing.routes.geometries geojson-coordinates-mapping
      :activities.outdoor-recreation-routes.routes.geometries geojson-coordinates-mapping
      :activities.outdoor-recreation-areas.routes.geometries geojson-coordinates-mapping
      :activities.outdoor-recreation-facilities.routes.geometries geojson-coordinates-mapping}}}
   :lois
   {:settings
    {:max_result_window 50000}
    :mappings
    {:date_detection false
     :properties
     {:event-date {:type "date"}
      :search-meta.location.wgs84-point
      {:type "geo_point"}
      :search-meta.location.geometries
      {:type "geo_shape"}}}}
   :legacy-sports-site
   {:mappings
    {:properties
     {:location.coordinates.wgs84 {:type "geo_point"}
      :location.geom-coll         {:type "geo_shape"}
      :lastModified               {:type   "date"
                                   :format legacy-date-format}}}}})

(defn- prop-type->es-mapping
  "Converts a property type definition to an Elasticsearch field mapping."
  [data-type]
  (case data-type
    "numeric"   {:type "double"}
    "boolean"   {:type "boolean"}
    "string"    {:type "text" :fields {:keyword {:type "keyword"}}}
    "enum"      {:type "keyword"}
    "enum-coll" {:type "keyword"}
    ;; fallback
    {:type "keyword"}))

(defn- generate-property-mappings
  "Generates ES mappings for all property fields from prop-types/all."
  []
  (reduce-kv
   (fn [acc prop-key prop-def]
     (let [field-name (keyword (str "properties." (name prop-key)))
           data-type (:data-type prop-def)
           mapping (prop-type->es-mapping data-type)]
       (assoc acc field-name mapping)))
   {}
   prop-types/all))

(defn generate-explicit-mapping
  "Generates explicit Elasticsearch mapping for sports_sites_current index.

  Uses strict dynamic mode to prevent index bloat from nested activity structures.
  Programmatically generates mappings for all 181 property fields from prop-types.

  Returns a map with :settings and :mappings keys suitable for create-index!."
  []
  (let [;; Core fields that ARE QUERIED
        core-fields
        {:lipas-id {:type "integer"}
         :status {:type "keyword"}  ; queried by V2 API filter
         :event-date {:type "date"}  ; might be used for sorting
         :type.type-code {:type "integer"}  ; queried by V2 API filter
         :construction-year {:type "integer"}  ; might be used for range queries
         :admin {:type "keyword"}  ; queried by V2 API filter
         :owner {:type "keyword"}  ; queried by V2 API filter
         :name {:type "text" :fields {:keyword {:type "keyword"}}}  ; full-text search
         :marketing-name {:type "text" :fields {:keyword {:type "keyword"}}}
         :comment {:type "text" :fields {:keyword {:type "keyword"}}}
         ;; Location fields that ARE QUERIED
         :location.city.city-code {:type "integer"}  ; queried by V2 API filter
         :location.city.neighborhood {:type "text" :fields {:keyword {:type "keyword"}}}
         :location.address {:type "text" :fields {:keyword {:type "keyword"}}}  ; might be searched
         :location.postal-code {:type "keyword"}
         :location.postal-office {:type "text" :fields {:keyword {:type "keyword"}}}}

        ;; Geographic fields
        geo-fields
        {:search-meta.location.wgs84-point {:type "geo_point"}
         :search-meta.location.wgs84-center {:type "geo_point"}
         :search-meta.location.wgs84-end {:type "geo_point"}
         :search-meta.location.geometries {:type "geo_shape"}}

        ;; Search-meta enrichment fields (multilingual and computed)
        search-meta-fields
        {:search-meta.name {:type "keyword"}
         :search-meta.admin.name.fi {:type "keyword"}
         :search-meta.admin.name.se {:type "keyword"}
         :search-meta.admin.name.en {:type "keyword"}
         :search-meta.owner.name.fi {:type "keyword"}
         :search-meta.owner.name.se {:type "keyword"}
         :search-meta.owner.name.en {:type "keyword"}
         :search-meta.location.city.name.fi {:type "keyword"}
         :search-meta.location.city.name.se {:type "keyword"}
         :search-meta.location.city.name.en {:type "keyword"}
         :search-meta.location.province.name.fi {:type "keyword"}
         :search-meta.location.province.name.se {:type "keyword"}
         :search-meta.location.province.name.en {:type "keyword"}
         :search-meta.location.avi-area.name.fi {:type "keyword"}
         :search-meta.location.avi-area.name.se {:type "keyword"}
         :search-meta.location.avi-area.name.en {:type "keyword"}
         :search-meta.type.name.fi {:type "keyword"}
         :search-meta.type.name.se {:type "keyword"}
         :search-meta.type.name.en {:type "keyword"}
         ;; Tags are multilingual arrays
         :search-meta.type.tags.fi {:type "keyword"}
         :search-meta.type.tags.se {:type "keyword"}
         :search-meta.type.tags.en {:type "keyword"}
         :search-meta.type.main-category.name.fi {:type "keyword"}
         :search-meta.type.main-category.name.se {:type "keyword"}
         :search-meta.type.main-category.name.en {:type "keyword"}
         :search-meta.type.sub-category.name.fi {:type "keyword"}
         :search-meta.type.sub-category.name.se {:type "keyword"}
         :search-meta.type.sub-category.name.en {:type "keyword"}
         :search-meta.fields.field-types {:type "keyword"}
         :search-meta.audits.latest-audit-date {:type "date"}
         ;; NEW: Activity keys array for filtering
         :search-meta.activities {:type "keyword"}}

        ;; Disabled fields - stored in _source but not indexed (display-only)
        disabled-fields
        {:activities {:enabled false}  ; indexed via search-meta.activities instead
         :location.geometries {:enabled false}  ; indexed via search-meta.location.geometries instead
         :search-meta.location.simple-geoms {:enabled false}
         ;; Display-only fields (never queried, only retrieved)
         :phone-number {:enabled false}
         :email {:enabled false}
         :www {:enabled false}
         :reservations-link {:enabled false}
         :renovation-years {:enabled false}
         :name-localized {:enabled false}
         :fields {:enabled false}
         :locker-rooms {:enabled false}
         :circumstances {:enabled false}
         :audits {:enabled false}
         :ptv {:enabled false}}

        ;; Programmatically generated property mappings
        property-mappings (generate-property-mappings)

        ;; Combine all mappings
        all-properties (merge core-fields
                              geo-fields
                              search-meta-fields
                              property-mappings
                              disabled-fields)]

    {:settings
     {:max_result_window 60000
      :index {:mapping {:total_fields {:limit 300}}}}
     :mappings
     {:dynamic "strict"
      :date_detection false
      :properties all-properties}}))

(defn gen-idx-name
  "Returns index name generated from current timestamp that is
  a valid ElasticSearch alias. Example: \"2017-08-13t14-44-42-761\""
  []
  (-> (java.time.LocalDateTime/now)
      str
      (str/lower-case)
      (str/replace #"[:|.]" "-")))

(defn index-exists?
  [client index-name]
  (let [resp (es/request client {:method :head
                                 :url    (es-utils/url [index-name])
                                 :exception-handler (fn [e]
                                                      (if (and (instance? org.elasticsearch.client.ResponseException e)
                                                               (= 404 (-> e
                                                                          (.getResponse)
                                                                          (.getStatusLine)
                                                                          (.getStatusCode))))
                                                        {:status 404}
                                                        (throw e)))})]
    (= 200 (:status resp))))

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
   ;; Return a future to keep consistent with previous impl
   (future
     (let [{:keys [input-ch output-ch]}
           (es/bulk-chan client {:flush-threshold         100
                                 :flush-interval          5000
                                 :max-concurrent-requests 3})]

       (async/put! input-ch data)
       (async/close! input-ch)

       (when-let [[_job resp] (async/<!! output-ch)]
         (async/close! output-ch)
         (->> resp
              :body
              :items
              (map (comp :result second first))
              frequencies))))))

(defn bulk-index-sync!
  ([client data]
   (let [{:keys [input-ch output-ch]}
         (es/bulk-chan client {:url                     "/_bulk?refresh=wait_for"
                               :flush-threshold         100
                               :flush-interval          5000
                               :max-concurrent-requests 3})]

     (async/put! input-ch data)
     (async/close! input-ch)

     (when-let [[_job resp] (async/<!! output-ch)]
       (async/close! output-ch)
       (->> resp
            :body
            :items
            (map (comp :result second first))
            frequencies)))))

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

(defn create-alias!
  [client {:keys [alias idx-name]}]
  (es/request client {:method :post
                      :url    (es-utils/url [:_aliases])
                      :body   {:actions [{:add {:index idx-name :alias alias}}]}}))

(defn search
  [client idx-name params]
  (assert idx-name)
  (es/request client {:method :get
                      :url    (es-utils/url [idx-name :_search])
                      :body   params}))

(defn fetch-document
  [client idx-name doc-id]
  (assert idx-name)
  (assert doc-id)
  (es/request client {:method :get
                      :url    (es-utils/url [idx-name :_doc doc-id])}))

(defn scroll
  [client idx-name params]
  (assert idx-name)
  (es/scroll-chan client {:method :get
                          :url    (es-utils/url [idx-name :_search])
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
  [es-index _es-type id-fn entry]
  [{:index {:_index es-index
            ;;:_type  es-type
            :_id    (id-fn entry)}}
   entry])

(defn ->bulk [es-index id-fn data]
  (reduce into (map (partial wrap-es-bulk es-index es-type id-fn) data)))
