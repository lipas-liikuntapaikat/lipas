(ns lipas.backend.api.v2
  (:require [clojure.string :as str]
            [lipas.backend.core :as core]
            [lipas.schema.common :as common-schema]
            [lipas.schema.lois :as lois-schema]
            [lipas.schema.sports-sites :as sports-sites-schema]
            [lipas.schema.sports-sites.activities :as activities-schema]
            [lipas.schema.sports-sites.location :as location-schema]
            [lipas.schema.sports-sites.types :as types-schema]
            [reitit.coercion.malli]
            [reitit.openapi :as openapi]
            [reitit.swagger-ui :as swagger-ui]))

(def sports-site-keys
  "Publicly exposed top level keys."
  ["lipas-id"
   "status"
   "event-date"
   "admin"
   "name"
   "marketing-name"
   "name-localized"
   "construction-year"
   "renovation-years"
   "owner"
   "location"
   "properties"
   "www"
   "phone-number"
   "type"
   "activities"
   "comment"
   "circumstances"])

(defn decode-heisenparam
  "Normalizes query parameters into a collection by handling different input formats:

  - Single value: Converts a scalar string into a vector by splitting on commas
  - Multiple values: Preserves existing vector format
  - Empty/nil: Returns the input unchanged

  Examples:
    (decode-heisenparam \"a,b,c\")     ;=> [\"a\" \"b\" \"c\"]
    (decode-heisenparam [\"a\" \"b\"]) ;=> [\"a\" \"b\"]
    (decode-heisenparam \"single\")    ;=> [\"single\"]
    (decode-heisenparam nil)           ;=> nil

  Used for normalizing HTTP query parameters where the same parameter name
  can appear multiple times or contain comma-separated values."
  [x]
  (cond-> x
    (string? x) (str/split #",")))

(def pagination-schema
  [:map
   [:current-page [:int {:min 1}]]
   [:page-size [:int {:min 1}]]
   [:total-items [:int {:min 0}]]
   [:total-pages [:int {:min 0}]]])

(defn ->pagination
  [{:keys [page page-size total-items]
    :or {page 1 page-size 10}}]
  {:current-page page
   :page-size page-size
   :total-items total-items
   :total-pages (-> total-items (/ page-size) Math/ceil int)})

(defn ->sports-sites-query
  [{:keys [page page-size statuses type-codes city-codes admins owners activities]
    :or {page 1
         page-size 10}}]
  {:from (* (dec page) page-size)
   :size page-size
   :track_total_hits 50000
   :_source {:includes sports-site-keys}
   :query {:bool
           {:must (cond-> []
                    statuses (conj {:terms {:status.keyword statuses}})
                    type-codes (conj {:terms {:type.type-code type-codes}})
                    city-codes (conj {:terms {:location.city.city-code city-codes}})
                    admins (conj {:terms {:admin.keyword admins}})
                    owners (conj {:terms {:owner.keyword owners}})
                    activities (into (for [a activities]
                                       {:exists {:field (str "activities." a)}})))}}})

(defn ->lois-query
  [{:keys [page page-size statuses types categories]
    :or {page 1
         page-size 10}}]
  {:from (* (dec page) page-size)
   :size page-size
   :track_total_hits 50000
   :query {:bool
           {:must (cond-> []
                    statuses (conj {:terms {:status.keyword statuses}})
                    types (conj {:terms {:loi-type.keyword types}})
                    categories (conj {:terms {:loi-category.keyword categories}}))}}})

(defn routes [{:keys [db search] :as _ctx}]
  (let [ui-handler (swagger-ui/create-swagger-ui-handler
                    {:url "/v2/openapi.json"})]
    ["/v2"
     {:openapi
      {:id :api-v2

       :info {:title "LIPAS API V2"
              :summary "API for Finnish sports and recreational facility database LIPAS"
              :description "The LIPAS system provides comprehensive data about sports and recreational facilities in Finland. The API is organized into three main sections:

**Sports Sites**
The core entities of LIPAS. Each sports facility is classified using a hierarchical type system, where specific facility types belong to subcategories within seven main categories. Each facility type has its own specific set of properties and a defined geometry type (Point, LineString, or Polygon) that describes its spatial representation.

**Sports Site Categories**
Access to the hierarchical type classification system used for categorizing sports facilities.

**Locations of Interest**
Additional non-facility entities in LIPAS, that complement the sports facility data."

              :contact
              {:name "Support, feature requests and bug reports"
               :url  "https://github.com/lipas-liikuntapaikat/lipas/issues"
               :email "lipasinfo@jyu.fi"}

              :license
              {:name "Creative Commons Attribution 4.0 International"
               :identifier "CC-BY-SA-4.0"
               :url "https://creativecommons.org/licenses/by-sa/4.0/"}}

       ;; These get merged in a wild way
       #_#_:tags [{:name        "Sports Sites"
               :description "The core entities of LIPAS."}
              {:name        "Sports Site Categories"
               :description "Hierarchical categorization of sports facilities"}
              {:name        "Locations of Interest"
               :description "Additional non-facility entities in LIPAS"}]

       }
      ;; The regular handle is still using swagger-spec, so :openapi :id doesn't hide
      ;; these routes from that.
      :swagger  {:id :hide-from-default}
      :coercion reitit.coercion.malli/coercion}

     [""
      {:no-doc true :get {:handler (fn [_] {:status 200 :body {:status "healthy"}})}}]
     ["/"
      {:no-doc true :get {:handler (fn [_] {:status 200 :body {:status "healthy"}})}}]

     ["/sports-site-categories"
      {:tags ["Sports Site Categories"]}

      [""
       {:get
        {:summary "Get all sports site categories"
         :handler
         (fn [_req]
           {:status 200
            :body   (core/get-categories)})

         :responses {200 {:body [:sequential #'types-schema/type]}}}}]

      ["/{type-code}"
       {:get
        {:summary "Get sports site category by type code"
         :handler
         (fn [req]
           (let [type-code (-> req :parameters :path :type-code)]
             {:status 200
              :body   (core/get-category type-code)}))

         :parameters
         {:path [:map [:type-code {:description (-> types-schema/type-code
                                                    second
                                                    :description)}
                       #'types-schema/type-code]]}

         :responses {200 {:body #'types-schema/type}}}}]]

     ["/sports-sites"
      {:tags ["Sports Sites"]}

      [""
       {:get
        {:summary "Get a paginated list of sports sites"
         :handler (fn [req]
                    (tap> (:parameters req))
                    (let [params  (:query (:parameters req))
                          query   (->sports-sites-query params)
                          _       (tap> query)
                          results (core/search search query)]
                      (tap> results)
                      {:status 200
                       :body
                       {:items      (-> results :body :hits :hits (->> (keep :_source)))
                        :pagination (let [total-items (-> results :body :hits :total :value)]
                                      (->pagination (assoc params :total-items total-items)))}}))

         :parameters {:query
                      [:map

                       [:page {:optional true}
                        [:int {:min 1 :json-schema/default 1}]]

                       [:page-size {:optional true}
                        [:int {:min 1 :max 100 :json-schema/default 10}]]

                       [:statuses
                        {:optional            true
                         :decode/string       decode-heisenparam
                         :json-schema/default #{"active" "out-of-service-temporarily"}}
                        #'common-schema/statuses]

                       [:city-codes
                        {:optional      true
                         :decode/string decode-heisenparam
                         :description   (-> location-schema/city-codes
                                          second
                                          :description)}
                        #'location-schema/city-codes]

                       [:type-codes
                        {:optional      true
                         :decode/string decode-heisenparam
                         :description   (-> types-schema/type-codes
                                          second
                                          :description)}
                        #'types-schema/type-codes]

                       [:admins
                        {:optional      true
                         :decode/string decode-heisenparam
                         :description   (-> sports-sites-schema/admins
                                          second
                                          :description)}
                        #'sports-sites-schema/admins]

                       [:owners
                        {:optional      true
                         :decode/string decode-heisenparam}
                        #'sports-sites-schema/owners]

                       [:activities
                        {:optional      true
                         :description   (-> activities-schema/activities
                                          second
                                          :description)
                         :decode/string decode-heisenparam}
                        #'activities-schema/activities]]}

         :responses {200 {:body [:map
                                 [:items
                                  [:vector {:title "SportSites"}
                                   #'sports-sites-schema/sports-site]]
                                 [:pagination {:title "Pagination"}
                                  pagination-schema]]}}}}]

      ["/{lipas-id}"
       {:get
        {:summary "Get sports facility by lipas-id"

         :handler (fn [req]
                    (tap> (:parameters req))
                    (let [lipas-id (-> req :parameters :path :lipas-id)]
                      {:status 200
                       :body   (doto (core/get-sports-site db lipas-id) tap>)}))

         :parameters {:path [:map
                             [:lipas-id
                              {:description "Lipas-id of the sports facility"}
                              #'sports-sites-schema/lipas-id]]}

         :responses {200 {:body #'sports-sites-schema/sports-site}}}}]]

     ["/lois"
      {:tags ["Locations of Interest"]
       }

      [""
       {:get {:summary "Get a paginated list of Locations of Interest"
              :handler (fn [req]
                         (tap> (:parameters req))
                         (let [params  (:query (:parameters req))
                               query   (->lois-query params)
                               _       (tap> query)
                               results (core/search-lois* search query)]
                           (tap> results)
                           {:status 200
                            :body
                            {:items      (-> results :body :hits :hits (->> (keep :_source)))
                             :pagination (let [total-items (-> results :body :hits :total :value)]
                                           (->pagination (assoc params :total-items total-items)))}}))

              :parameters {:query
                           [:map

                            [:page {:optional true}
                             [:int {:min 1 :json-schema/default 1}]]

                            [:page-size {:optional true}
                             [:int {:min 1 :max 100 :json-schema/default 10}]]

                            [:statuses
                             {:optional            true
                              :decode/string       decode-heisenparam
                              :json-schema/default #{"active" "out-of-service-temporarily"}}
                             #'common-schema/statuses]

                            [:categories
                             {:optional      true
                              :decode/string decode-heisenparam}
                             #'lois-schema/loi-categories]

                            [:types
                             {:optional      true
                              :decode/string decode-heisenparam}
                             #'lois-schema/loi-types]]}

              :responses {200 {:body [:map
                                      [:items
                                       [:vector {:title "LocationsOfInterest"}
                                        #'lois-schema/loi]]
                                      [:pagination {:title "Pagination"}
                                       pagination-schema]]}}}}]

      ["/{loi-id}"
       {:get
        {:summary "Get Location of Interest by id"

         :handler (fn [req]
                    (tap> (:parameters req))
                    (let [loi-id (-> req :parameters :path :loi-id)]
                      {:status 200
                       :body   (doto (core/get-loi search loi-id) tap>)}))

         :parameters {:path [:map
                             [:loi-id
                              {:description "UUID v4 of the Location if Interest"}
                              #'lois-schema/loi-id]]}

         :responses {200 {:body #'lois-schema/loi}}}}]]

     ["/openapi.json"
      {:get
       {:no-doc  true
        :swagger {:info {:title "Lipas-API v2"}}
        :handler (openapi/create-openapi-handler)}}]

     ["/swagger-ui"
      {:get {:no-doc  true
             :handler ui-handler}}]
     ["/swagger-ui/*"
      {:get {:no-doc  true
             :handler ui-handler}}]]))
