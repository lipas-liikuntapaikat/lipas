(ns lipas.backend.routes.routes-v1
  (:require [reitit.ring :as ring]
            [reitit.swagger :as swagger]))


(def routes ["/v1/api"
             ["/swagger.json"
              {:get
               {:no-doc  true
                :swagger {:id ::legacy
                          :info {:title "Lipas-API (legacy) v1"}
                          :securityDefinitions
                          {:token-auth
                           {:type "apiKey"
                            :in   "header"
                            :name "Authorization"}}}
                :handler (swagger/create-swagger-handler)}}]
             ["/sports-place-types"
              {:swagger {:id ::legacy}
               :get
               {:handler
                (fn [_]
                  {:status 200
                   :body   {:status "Sports place types here"}})}}]])
