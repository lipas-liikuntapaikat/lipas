(ns lipas.backend.analysis.heatmap
  (:require [lipas.backend.search :as search]
            [lipas.schema.common :as common]
            [lipas.schema.sports-sites :as sports-sites]
            [lipas.schema.sports-sites.types :as types]
            [malli.core :as m]
            [malli.error :as me]
            [taoensso.timbre :as log]))

;; Malli schemas for validation
(def Bbox
  [:map
   [:min-x :double]
   [:max-x :double]
   [:min-y :double]
   [:max-y :double]])

(def HeatmapParams
  [:map
   [:zoom number?]
   [:bbox Bbox]
   [:dimension [:enum :density :area :capacity :type-distribution
                :year-round :lighting :activities]]
   [:weight-by {:optional true}
    [:enum :count :area-m2 :capacity :route-length-km]]
   [:precision {:optional true} number?]
   [:filters {:optional true}
    [:map
     [:type-codes {:optional true} [:vector :int]]
     [:year-range {:optional true} [:tuple :int :int]]
     [:year-round-only {:optional true} :boolean]
     [:status-codes {:optional true} [:set :string]]
     [:city-codes {:optional true} [:vector :int]]
     [:admins {:optional true} [:vector :string]]
     [:owners {:optional true} [:vector :string]]
     [:surface-materials {:optional true} [:vector :string]]
     [:retkikartta? {:optional true} :boolean]
     [:harrastuspassi? {:optional true} :boolean]
     [:school-use? {:optional true} :boolean]]]])

(def FacetParams
  [:map
   [:bbox Bbox]
   [:filters {:optional true}
    [:map
     [:type-codes {:optional true} [:vector :int]]
     [:status-codes {:optional true} [:set :string]]]]])

;; Response schemas

(def GeoJSONPoint
  [:map
   [:type [:= "Point"]]
   [:coordinates [:tuple :double :double]]])

(def HeatmapFeatureProperties
  [:map
   [:weight :double]
   [:grid_key :string]
   [:doc_count :int]
   [:normalized-weight :double]])

(def HeatmapFeature
  [:map
   [:type [:= "Feature"]]
   [:geometry #'GeoJSONPoint]
   [:properties #'HeatmapFeatureProperties]])

(def HeatmapMetadata
  [:map
   [:dimension [:enum :density :area :capacity :type-distribution
                :year-round :lighting :activities]]
   [:weight-by [:maybe [:enum :count :area-m2 :capacity :route-length-km]]]
   [:total-features :int]])

(def CreateHeatmapResponse
  [:map
   [:data [:sequential #'HeatmapFeature]]
   [:metadata #'HeatmapMetadata]])

(def TypeCodeFacetValue
  [:map
   [:value #'types/active-type-code]
   [:count :int]])

(def OwnerFacetValue
  [:map
   [:value #'sports-sites/owner]
   [:count :int]])

(def AdminFacetValue
  [:map
   [:value #'sports-sites/admin]
   [:count :int]])

(def StatusFacetValue
  [:map
   [:value #'common/status]
   [:count :int]])

(def YearRange
  [:map
   [:min :int]
   [:max :int]])

(def GetHeatmapFacetsResponse
  [:map
   [:type-codes [:sequential #'TypeCodeFacetValue]]
   [:owners [:sequential #'OwnerFacetValue]]
   [:admins [:sequential #'AdminFacetValue]]
   [:year-range #'YearRange]
   [:statuses [:sequential #'StatusFacetValue]]])

(defn zoom->precision
  "Convert zoom level to appropriate geohash precision for aggregation"
  [zoom]
  (cond
    (<= zoom 5) 4
    (<= zoom 7) 5
    (<= zoom 9) 6
    (<= zoom 11) 7
    (<= zoom 13) 8
    (<= zoom 15) 8
    :else 8))

(defn build-filters
  "Build Elasticsearch filters from bbox and filter parameters"
  [{:keys [min-x max-x min-y max-y]} filters]
  (let [geo-filter {:geo_bounding_box
                    {:search-meta.location.wgs84-point
                     {:top_left {:lat max-y :lon min-x}
                      :bottom_right {:lat min-y :lon max-x}}}}

        status-filter (when-let [statuses (:status-codes filters)]
                        {:terms {:status.keyword (vec statuses)}})

        type-filter (when-let [types (:type-codes filters)]
                      {:terms {:type.type-code types}})

        city-filter (when-let [cities (:city-codes filters)]
                      {:terms {:location.city.city-code cities}})

        year-filter (when-let [[min-year max-year] (:year-range filters)]
                      {:range {:construction-year {:gte min-year :lte max-year}}})

        admins-filter (when-let [admins (:admins filters)]
                        {:terms {:admin.keyword admins}})

        owners-filter (when-let [owners (:owners filters)]
                        {:terms {:owner.keyword owners}})

        materials-filter (when-let [materials (:surface-materials filters)]
                           {:terms {:properties.surface-material.keyword materials}})

        year-round-filter (when (:year-round-only filters)
                            {:term {:properties.may-be-shown-in-excursion-map-fi? true}})

        retkikartta-filter (when (:retkikartta? filters)
                             {:term {:properties.may-be-shown-in-excursion-map-fi? true}})

        harrastuspassi-filter (when (:harrastuspassi? filters)
                                {:term {:properties.may-be-shown-in-harrastuspassi-fi? true}})

        school-filter (when (:school-use? filters)
                        {:term {:properties.school-use? true}})]

    (vec (remove nil? [geo-filter status-filter type-filter city-filter
                       year-filter admins-filter owners-filter materials-filter
                       year-round-filter retkikartta-filter harrastuspassi-filter
                       school-filter]))))

(defn build-dimension-aggs
  "Build aggregation based on dimension and weight"
  [dimension weight-by]
  (let [base-agg (case (or weight-by :count)
                   :count {}
                   :area-m2 {:sum {:field "properties.area-m2"}}
                   :route-length-km {:sum {:field "properties.route-length-km"}}
                   ;; Default case for unknown weight-by values
                   {})]

    (case dimension
      :density base-agg

      :area {:area_sum {:sum {:field "properties.area-m2"}}}

      :type-distribution {:types {:terms {:field "type.type-code" :size 200}}}

      :year-round {:year_round_count {:filter {:term {:properties.year-round-use? true}}}}

      :lighting {:lighting_count {:filter {:term {:properties.lighting? true}}}}

      :activities {:activities {:terms {:field "search-meta.activities.keyword" :size 10}}}

      ;; Default to count-based aggregation
      base-agg)))

(defn build-es-query
  "Build the complete Elasticsearch query for heatmap data"
  [{:keys [zoom bbox dimension weight-by filters precision]}]
  (let [geohash-precision (or precision (zoom->precision zoom))]
    {:size 0
     :query {:bool {:filter (build-filters bbox filters)}}
     :aggs {:grid
            {:geohash_grid {:field "search-meta.location.wgs84-point"
                            :precision geohash-precision
                            #_#_:size (case geohash-precision
                                        (1 2 3 4) 10000
                                        (5 6 7) 50000
                                        (8 9 10) 100000
                                        10000)}
             :aggs (merge
                    {:centroid {:geo_centroid {:field "search-meta.location.wgs84-point"}}}
                    (build-dimension-aggs dimension weight-by))}}}))

(defn normalize-weights
  "Scale weights to 0-1 range using min-max normalization.
   Handles edge cases like all zeros, single values, and negative weights."
  [weights]
  (when (seq weights)
    (let [min-weight (apply min weights)
          max-weight (apply max weights)
          range (- max-weight min-weight)]
      (if (zero? range)
        ;; All weights are the same - return uniform distribution
        (repeat (count weights) 0.5)
        ;; Standard min-max normalization
        (map (fn [weight] (double (/ (- weight min-weight) range))) weights)))))

(defn scale-feature-weights
  "Apply weight normalization to a collection of GeoJSON features"
  [features]
  (if (seq features)
    (let [weights (map #(get-in % [:properties :weight]) features)
          normalized-weights (normalize-weights weights)]
      (map (fn [feature normalized-weight]
             (assoc-in feature [:properties :normalized-weight] normalized-weight))
           features
           normalized-weights))
    features))

(defn transform-bucket
  "Transform ES aggregation bucket to GeoJSON feature"
  [bucket dimension _weight-by]
  (let [weight (case dimension
                 :density (get bucket :doc_count 0)
                 :area (get-in bucket [:area_sum :value] 0)
                 :capacity (get-in bucket [:capacity_sum :value] 0)
                 :year-round (get-in bucket [:year_round_count :doc_count] 0)
                 :lighting (get-in bucket [:lighting_count :doc_count] 0)
                 :type-distribution (get bucket :doc_count 0)
                 :activities (get bucket :doc_count 0)
                 (get bucket :doc_count 0))

        centroid (get-in bucket [:centroid :location])
        lon (get centroid :lon)
        lat (get centroid :lat)]

    {:type "Feature"
     :geometry {:type "Point"
                :coordinates [lon lat]}
     :properties (merge
                  {:weight weight
                   :grid_key (:key bucket)
                   :doc_count (:doc_count bucket)}
                  ;; Add dimension-specific properties
                  (case dimension
                    :type-distribution {:types (get-in bucket [:types :buckets] [])}
                    :activities {:activities (get-in bucket [:activities :buckets] [])}
                    {}))}))

(defn transform-to-features
  "Transform ES aggregation results to GeoJSON features with normalized weights"
  [es-result dimension weight-by]
  (let [buckets (get-in es-result [:body :aggregations :grid :buckets] [])
        features (mapv #(transform-bucket % dimension weight-by) buckets)]
    (scale-feature-weights features)))

(defn create
  "Business function for creating heatmap data"
  [{:keys [search]} params]
  (let [es-query (build-es-query params)
        _ (log/debug "ES Query:" es-query)
        result (search/search (:client search)
                              (get-in search [:indices :sports-site :search])
                              es-query)
        bucket-count (count (get-in result [:body :aggregations :grid :buckets] []))
        total-docs (get-in result [:body :aggregations :grid :sum_other_doc_count] 0)]
    (log/info (str "Precision " (:precision params)
                   " returned " bucket-count " buckets"
                   " (truncated: " total-docs " docs in other buckets)"))
    (transform-to-features result (:dimension params) (:weight-by params))))

(defn build-facets-query
  "Build ES query for getting available facet values"
  [{:keys [bbox filters]}]
  {:size 0
   :query {:bool {:filter (build-filters bbox (dissoc filters :type-codes :owners :admins))}}
   :aggs {:type-codes {:terms {:field "type.type-code" :size 200}}
          :owners {:terms {:field "owner.keyword" :size 100}}
          :admins {:terms {:field "admin.keyword" :size 100}}
          :year-range {:stats {:field "construction-year"}}
          :statuses {:terms {:field "status.keyword" :size 10}}}})

(defn transform-facets
  "Transform ES facet results to UI-friendly format"
  [es-result]
  (let [aggs (get-in es-result [:body :aggregations])]
    {:type-codes (mapv (fn [bucket]
                         {:value (:key bucket)
                          :count (:doc_count bucket)})
                       (get-in aggs [:type-codes :buckets] []))
     :owners (mapv (fn [bucket]
                     {:value (:key bucket)
                      :count (:doc_count bucket)})
                   (get-in aggs [:owners :buckets] []))
     :admins (mapv (fn [bucket]
                     {:value (:key bucket)
                      :count (:doc_count bucket)})
                   (get-in aggs [:admins :buckets] []))
     :year-range {:min (or (some-> (get-in aggs [:year-range :min]) int) 1900)
                  :max (or (some-> (get-in aggs [:year-range :max]) int) 2024)}
     :statuses (mapv (fn [bucket]
                       {:value (:key bucket)
                        :count (:doc_count bucket)})
                     (get-in aggs [:statuses :buckets] []))}))

(defn get-facets
  "Business function for getting available facet values based on current view"
  [{:keys [search]} params]
  (let [es-query (build-facets-query params)
        result (search/search (:client search)
                              (get-in search [:indices :sports-site :search])
                              es-query)]
    (transform-facets result)))
