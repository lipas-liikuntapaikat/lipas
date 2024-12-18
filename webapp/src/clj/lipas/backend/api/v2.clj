(ns lipas.backend.api.v2
  (:require [reitit.coercion.malli]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [ring.util.http-response :as resp]
            [lipas.schema.sports-sites :as sports-sites-schema]))

(defn routes [{:keys [db search] :as ctx}]
  (let [ui-handler (swagger-ui/create-swagger-ui-handler
                     {:url "/api-v2/swagger.json"})]
    ["/api-v2"
     {:swagger {:id :api-v2}
      :coercion reitit.coercion.malli/coercion}

     ["/sports-sites"
      {:get {:handler (fn [_]
                        (resp/ok))
             :responses {200 {:body [:vector sports-sites-schema/base-schema]}}}}]
     ["/lois"
      {:get {:handler (fn [_]
                        (resp/ok))}}]

     ["/swagger.json"
      {:get
       {:no-doc  true
        :swagger {:info {:title "Lipas-API v2"}
                  :securityDefinitions
                  {:token-auth
                   {:type "apiKey"
                    :in   "header"
                    :name "Authorization"}}}
        :handler (swagger/create-swagger-handler)}}]

     ["/swagger-ui"
      {:get {:no-doc true
             :handler ui-handler}}]
     ["/swagger-ui/*"
      {:get {:no-doc true
             :handler ui-handler}}]]))
