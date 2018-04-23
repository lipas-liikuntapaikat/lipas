(ns auth-service.handler
  (:require [compojure.api.sweet :refer [api]]
            [auth-service.routes.user :refer :all]
            [auth-service.routes.preflight :refer :all]
            [auth-service.routes.permission :refer :all]
            [auth-service.routes.refresh-token :refer :all]
            [auth-service.routes.auth :refer :all]
            [auth-service.routes.password :refer :all]
            [auth-service.middleware.basic-auth :refer [basic-auth-mw]]
            [auth-service.middleware.token-auth :refer [token-auth-mw]]
            [auth-service.middleware.cors :refer [cors-mw]]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(def app
  (api
    {:swagger
     {:ui   "/api-docs"
      :spec "/swagger.json"
      :data {:info {:title "authenticated-refresh-for-real"
                    :version "0.0.1"}
             :tags [{:name "Preflight"     :description "Return successful response for all preflight requests"}
                    {:name "User"          :description "Create, delete and update user details"}
                    {:name "Permission"    :description "Add and remove permissions tied to specific users"}
                    {:name "Refresh-Token" :description "Get and delete refresh-tokens"}
                    {:name "Auth"          :description "Get auth information for a user"}
                    {:name "Password"      :description "Request and confirm password resets"}]}}}
    preflight-route
    user-routes
    permission-routes
    refresh-token-routes
    auth-routes
    password-routes))
