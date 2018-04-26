(ns auth-service.routes.user
  (:require [auth-service.middleware.cors :refer [cors-mw]]
            [auth-service.middleware.token-auth :refer [token-auth-mw]]
            [auth-service.middleware.authenticated :refer [authenticated-mw]]
            [auth-service.route-functions.user.create-user :refer [create-user-response]]
            [auth-service.route-functions.user.delete-user :refer [delete-user-response]]
            [auth-service.route-functions.user.modify-user :refer [modify-user-response]]
            [schema.core :as s]
            [compojure.api.sweet :refer :all]))


(def user-routes
  (context "/api/v1/user" []
           :tags ["User"]

    (POST "/"           {:as request}
           :return      {:username String}
           :middleware  [cors-mw]
           :body        [user {:username String
                               :email String
                               :password String
                               (s/optional-key :user-data) s/Any}]
           :summary     "Create a new user with provided username, email and password."
           (create-user-response user))

    (DELETE "/:id"          {:as request}
             :path-params   [id :- s/Uuid]
             :return        {:message String}
             :header-params [authorization :- String]
             :middleware    [token-auth-mw cors-mw authenticated-mw]
             :summary       "Deletes the specified user. Requires token to have `admin` auth or self ID."
             :description   "Authorization header expects the following format 'Token {token}'"
             (delete-user-response request id))

    (PATCH  "/:id"          {:as request}
             :path-params   [id :- s/Uuid]
             :body-params   [{username :- String ""} {password :- String ""} {email :- String ""}]
             :header-params [authorization :- String]
             :return        {:id s/Uuid :email String :username String}
             :middleware    [token-auth-mw cors-mw authenticated-mw]
             :summary       "Update some or all fields of a specified user. Requires token to have `admin` auth or self ID."
             :description   "Authorization header expects the following format 'Token {token}'"
             (modify-user-response request id username password email))))
