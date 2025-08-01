(ns legacy-api.routes
  (:require [legacy-api.core :as legacy-core]
            [legacy-api.http :as legacy-http]
            [legacy-api.api :as legacy-api]
            [lipas.schema.legacy :as legacy-schema]
            [lipas.schema.sports-sites.types :as types-schema]
            [reitit.coercion.malli :as malli]
            [reitit.openapi :as openapi]
            [reitit.swagger-ui :as swagger-ui]))

(defn routes [{:keys [search db]}]
  (let [ui-handler (swagger-ui/create-swagger-ui-handler
                    {:url "/rest/api/openapi.json"})]
    ["/rest/api"
     {:openapi
      {:id :api-v1

       :info {:title "LIPAS API V1"
              :summary "API for Finnish sports and recreational facility database LIPAS"
              :description
              "The LIPAS system provides comprehensive data about sports and recreational facilities in Finland. The API is organized into two main sections:

**Sports Places**
The core entities of LIPAS. Each sports facility is classified using a hierarchical type system, where specific facility types belong to subcategories within seven main categories. Each facility type has its own specific set of properties and a defined geometry type (Point, LineString, or Polygon) that describes its spatial representation.

**Sports Site Categories**
Access to the hierarchical type classification system used for categorizing sports facilities."

              :contact
              {:name "Support, feature requests and bug reports"
               :url "https://github.com/lipas-liikuntapaikat/lipas/issues"
               :email "lipasinfo@jyu.fi"}

              :license
              {:name "Creative Commons Attribution 4.0 International"
               :identifier "CC-BY-SA-4.0"
               :url "https://creativecommons.org/licenses/by-sa/4.0/"}}}
           ;; The regular handle is still using swagger-spec, so :openapi :id doesn't hide
           ;; these routes from that.
      :swagger {:id :hide-from-default}
      :coercion malli/coercion}
     ["/sports-places/:sports-place-id"
      {:parameters {:query [:map [:lang {:optional true} #'legacy-schema/lang]]
                    :path [:map [:sports-place-id :int]]}
       :get
       {:tags ["sport-places"]
        :handler
        (fn [req]
          (let [locale (or (-> req :parameters :query :lang keyword) :fi)
                sports-place-id (-> req :parameters :path :sports-place-id)
                resp (legacy-core/fetch-sports-place-es (:client search) locale sports-place-id)]
            {:status 200
             :body resp}))
        :responses {200 {:body legacy-schema/legacy-sports-place}}}}]
     ["/sports-places"
      {:parameters {:query legacy-schema/search-params}
       :get
       {:tags ["sport-places"]
        :handler
        (fn [{:keys [parameters]}]
          (let [{:keys [pageSize page typeCodes cityCodes closeToDistanceKm
                        closeToMatch closeToLon closeToLat modifiedAfter
                        searchString retkikartta harrastuspassi lang] :as qp} (-> parameters :query)

                pageSize (or pageSize 10)
                ;; Ensure typeCodes and cityCodes are always collections
                type-codes (cond (nil? typeCodes) nil
                                 (coll? typeCodes) typeCodes
                                 :else [typeCodes])
                city-codes (cond (nil? cityCodes) nil
                                 (coll? cityCodes) cityCodes
                                 :else [cityCodes])

                params {:limit (or pageSize 10)
                        :offset (dec (or page 1))
                        :type-codes type-codes
                        :city-codes city-codes
                        :close-to (when closeToDistanceKm
                                    {:distance (* closeToDistanceKm 1000)
                                     (if (= closeToMatch :start-point)
                                       :location.coordinates.wgs84
                                       :location.geom-coll)
                                     {:lon closeToLon
                                      :lat closeToLat}})
                        :modified-after modifiedAfter
                        :search-string searchString
                        :excursion-map? retkikartta
                        :harrastuspassi? harrastuspassi}
                fields (let [fields-value (:fields qp)]
                         (cond
                           (nil? fields-value) []
                           (string? fields-value)
                           ;; Handle comma-separated string by splitting
                           (if (re-find #"," fields-value)
                             (mapv clojure.string/trim (clojure.string/split fields-value #","))
                             [fields-value])
                           :else fields-value))
                locale (or (keyword lang) :se)
                resp (legacy-core/fetch-sports-places-es search locale params fields)
                {:keys [partial? total results]} resp]
            (if partial?
              (let [path "/v1/sports-places"
                    links (legacy-http/create-page-links path params (:offset params) (:limit params) total)]
                (legacy-http/linked-partial-content results links))
              {:status 200
               :body (doall results)})))
        :responses {200 {:body [:vector legacy-schema/legacy-sports-place]}}}}]
     ["/deleted-sports-places"
      {:parameters {:query [:map [:since {:optional true
                                          :example "1984-01-01 00:00:00.000"}
                                  [:re legacy-schema/date-re]]]}
       :get
       {:tags ["sport-places"]
        :handler
        (fn [req]
          (let [since-str (or (-> req :parameters :query :since) "1984-01-01 00:00:00.000")
                ;; Use the since string directly for ES query (ES accepts ISO format)
                api-results (legacy-core/fetch-deleted-sports-places-es search since-str)]
            {:status 200
             :body api-results}))
        :responses {200 {:body legacy-schema/deleted-sports-places-response}}}}]
     ["/categories"
      {:tags ["sport-place-types"]
       :parameters {:query [:map [:lang {:optional true} #'legacy-schema/lang]]}
       :get
       {:handler
        (fn [req]
          (let [locale (or (-> req :parameters :query :lang keyword) :fi)]
            {:status 200
             :body (legacy-api/categories locale)}))
        :responses {200 {:body #'legacy-schema/category-response}}}}]
     ["/sports-place-types"
      {:parameters {:query [:map [:lang {:optional true} #'legacy-schema/lang]]}
       :get
       {:tags ["sport-place-types"]
        :handler
        (fn [req]
          (let [locale (or (-> req :parameters :query :lang keyword) :fi)]
            {:status 200
             :body (legacy-api/sports-place-types locale)}))
        :responses {200 {:body #'legacy-schema/sports-place-types-response}}}}]
     ["/sports-place-types/:type-code"
      {:swagger {:id ::legacy}
       :parameters {:query [:map [:lang {:optional true} #'legacy-schema/lang]]
                    :path [:map [:type-code #'types-schema/active-type-code]]}
       :get
       {:tags ["sport-place-types"]
        :handler
        (fn [req]
          (let [locale (or (-> req :parameters :query :lang keyword) :fi)
                type-code (-> req :parameters :path :type-code)]
            {:status 200
             :body (legacy-api/sports-place-by-type-code locale type-code)}))
        :responses {200 {:body #'legacy-schema/sports-places-by-type-response}}}}]
     ["/openapi.json"
      {:get
       {:no-doc true
        :swagger {:info {:title "Lipas-API (legacy) v1"}}
        :tags [{:name "sport-places"
                :description "Sport places"}
               {:name "sport-place-types"
                :description "Sport place types"}]
        :handler (openapi/create-openapi-handler)}}]
     ["/swagger-ui"
      {:get {:no-doc true
             :handler ui-handler}}]
     ["/swagger-ui/*"
      {:get {:no-doc true
             :handler ui-handler}}]]))