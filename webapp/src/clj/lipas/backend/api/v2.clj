(ns lipas.backend.api.v2
  (:require [clojure.string :as str]
            [lipas.backend.core :as core]
            [lipas.schema.common :as common-schema]
            [lipas.schema.lois :as lois-schema]
            [lipas.schema.sports-sites :as sports-sites-schema]
            [lipas.schema.sports-sites.activities :as activities-schema]
            [reitit.coercion.malli]
            [reitit.openapi :as openapi]
            [reitit.swagger-ui :as swagger-ui]
            [ring.util.http-response :as resp]))

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
  "Coerces singular and repeating params into a collection. Singular
  query param comes in as scalar, while multiple params come in as
  vector. Handles also possibly comma-separated vals."
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
    :or {page-size 10}}]
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
    :or {page-size 10}}]
  {:from (* (dec page) page-size)
   :size page-size
   :track_total_hits 50000
   #_#_:_source {:includes sports-site-keys}
   :query {:bool
           {:must (cond-> []
                    statuses (conj {:terms {:status.keyword statuses}})
                    types (conj {:terms {:loi-type.keyword types}})
                    categories (conj {:terms {:loi-category.keyword categories}}))}}})

(defn routes [{:keys [db search] :as ctx}]
  (let [ui-handler (swagger-ui/create-swagger-ui-handler
                     {:url "/api-v2/openapi.json"})]
    ["/api-v2"
     {:openapi {:id :api-v2}
      ;; The regular handle is still using swagger-spec, so :openapi :id doesn't hide
      ;; these routes from that.
      :swagger {:id :hide-from-default}
      :coercion reitit.coercion.malli/coercion}

     ["/sports-sites"
      {:tags ["sports-sites"]
       :get
       {:handler
        (fn [req]
          (tap> (:parameters req))
          (let [params (:query (:parameters req))
                query (->sports-sites-query params)
                _ (tap> query)
                results (core/search search query)]
            (tap> results)
            {:status 200
             :body
             {:items (-> results :body :hits :hits (->> (keep :_source)))
              :pagination (let [total-items (-> results :body :hits :total :value)]
                            (->pagination (assoc params :total-items total-items)))}}))

        :parameters {:query
                     [:map

                      [:page {:optional true}
                       [:int {:min 1 :json-schema/default 1}]]

                      [:page-size {:optional true}
                       [:int {:min 1 :max 100 :json-schema/default 10}]]

                      [:statuses
                       {:optional true
                        :decode/string decode-heisenparam
                        :json-schema/default #{"active" "out-of-service-temporarily"}}
                       #'common-schema/statuses]

                      [:city-codes
                       {:optional true
                        :decode/string decode-heisenparam
                        :description (-> sports-sites-schema/city-codes
                                         second
                                         :description)}
                       #'sports-sites-schema/city-codes]

                      [:type-codes
                       {:optional true
                        :decode/string decode-heisenparam
                        :description (-> sports-sites-schema/type-codes
                                         second
                                         :description)}
                       #'sports-sites-schema/type-codes]

                      [:admins
                       {:optional true
                        :decode/string decode-heisenparam
                        :description (-> sports-sites-schema/admins
                                         second
                                         :description)}
                       #'sports-sites-schema/admins]

                      [:owners
                       {:optional true
                        :decode/string decode-heisenparam}
                       #'sports-sites-schema/owners]

                      [:activities
                       {:optional true
                        :description (-> activities-schema/activities
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

     ["/lois"
      {:tags ["locations-of-interest"]
       :get {:handler (fn [req]
                        (tap> (:parameters req))
                        (let [params (:query (:parameters req))
                              query (->lois-query params)
                              _ (tap> query)
                              results (core/search-lois* search query)]
            (tap> results)
            {:status 200
             :body
             {:items (-> results :body :hits :hits (->> (keep :_source)))
              :pagination (let [total-items (-> results :body :hits :total :value)]
                            (->pagination (assoc params :total-items total-items)))}}))

             :parameters {:query
                          [:map

                           [:page {:optional true}
                            [:int {:min 1 :json-schema/default 1}]]

                           [:page-size {:optional true}
                            [:int {:min 1 :max 100 :json-schema/default 10}]]

                           [:statuses
                            {:optional true
                             :decode/string decode-heisenparam
                             :json-schema/default #{"active" "out-of-service-temporarily"}}
                            #'common-schema/statuses]

                           [:categories
                            {:optional true
                             :decode/string decode-heisenparam}
                            #'lois-schema/loi-categories]

                           [:types
                            {:optional true
                             :decode/string decode-heisenparam}
                            #'lois-schema/loi-types]]}

             :responses {200 {:body [:map
                                [:items
                                 [:vector {:title "LocationsOfInterest"}
                                  #'lois-schema/loi]]
                                [:pagination {:title "Pagination"}
                                 pagination-schema]]}}}}]

     ["/openapi.json"
      {:get
       {:no-doc  true
        :swagger {:info {:title "Lipas-API v2"}}
        :handler (openapi/create-openapi-handler)}}]

     ["/swagger-ui"
      {:get {:no-doc true
             :handler ui-handler}}]
     ["/swagger-ui/*"
      {:get {:no-doc true
             :handler ui-handler}}]]))
