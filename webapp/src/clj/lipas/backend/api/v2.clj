(ns lipas.backend.api.v2
  (:require [lipas.schema.sports-sites :as sports-sites-schema]
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
      {:get {:handler (fn [_]
                        (resp/ok))
             :responses {200 {:body [:vector
                                     {:title "SportSites"}
                                     sports-sites-schema/sports-site]}}}}]
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
