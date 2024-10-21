(ns lipas.backend.routes.routes-v1
  (:require [clojure.set :as set]
            [lipas.data.types-old :as old]
            [lipas.schema.core-legacy]
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
                  {:status     200
                   :parameters {:query :lipas.api.get-sports-site/query-params}
                   :body       (->> (vals old/all)
                                    (map #(->
                                           %
                                           (select-keys [:type-code :name :description :geometry-type :sub-category])
                                           (set/rename-keys {:type-code :typeCode :geometry-type :geometryType :sub-category :subCategory})
                                           (update :name :fi)
                                           (update :description :fi))))})}}]])