(ns lipas.backend.api.v2
  (:require [clojure.string :as str]
            [lipas.backend.core :as core]
            [lipas.schema.sports-sites :as sports-sites-schema]
            [reitit.coercion.malli]
            [reitit.openapi :as openapi]
            [reitit.swagger-ui :as swagger-ui]
            [ring.util.http-response :as resp]))

(defn routes [{:keys [db search] :as ctx}]
  (let [ui-handler (swagger-ui/create-swagger-ui-handler
                     {:url "/api-v2/openapi.json"})]
    ["/api-v2"
     {:openapi {:id :api-v2}
      :coercion reitit.coercion.malli/coercion}

     ["/sports-sites"
      {:get {:handler (fn [req]
                        (let [{:keys [type-codes city-codes]} (:query (:parameters req))
                              query {:from 0
                                     :size 100
                                     :track_total_hits 50000
                                     :_source {:excludes ["search-meta.*"]}
                                     :query {:bool {:must (cond-> []
                                                            type-codes (conj {:terms {:type.type-code type-codes}})
                                                            city-codes (conj {:terms {:location.city.city-code city-codes}}))}}}
                              x  (core/search search query)]
                          {:status 200
                           :body {:items (-> x
                                             :body
                                             :hits
                                             :hits
                                             ;; Why is there results with empty _source?
                                             (->> (keep :_source))
                                             )}}))
             :parameters {:query [:map
                                  [:city-codes
                                   {:optional true
                                    :decode/string (fn [s] (str/split s #","))
                                    #_#_
                                    :description (->> cities/by-city-code
                                                      (sort-by key)
                                                      (map (fn [[code x]]
                                                             (str "* " code " - " (:fi (:name x)) "")))
                                                      (str/join "\n")
                                                      (str "City-codes:\n"))}
                                   #'sports-sites-schema/city-codes]
                                  [:type-codes
                                   {:optional true
                                    :decode/string (fn [s] (str/split s #","))
                                    #_#_
                                    :description (->> types/all
                                                      (sort-by key)
                                                      (map (fn [[code x]]
                                                             (str "* " code " - " (:fi (:name x)) "")))
                                                      (str/join "\n")
                                                      (str "Type-codes:\n"))}
                                   #'sports-sites-schema/type-codes]]}
             :responses {200 {:body [:map
                                     [:items [:vector
                                              {:title "SportSites"}
                                              sports-sites-schema/sports-site]]]}}}}]
     ["/lois"
      {:get {:handler (fn [_]
                        (resp/ok))}}]

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
