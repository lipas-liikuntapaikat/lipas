(ns auth-service.routes.auth
  (:require [auth-service.middleware.basic-auth :refer [basic-auth-mw]]
            [auth-service.middleware.authenticated :refer [authenticated-mw]]
            [auth-service.middleware.cors :refer [cors-mw]]
            [auth-service.route-functions.auth.get-auth-credentials :refer [auth-credentials-response]]
            [schema.core :as s]
            [compojure.api.sweet :refer :all]))

(def auth-routes
  (context "/api/v1/auth" []

     (GET "/"             {:as request}
           :tags          ["Auth"]
           :return        {:id s/Uuid
                           :username String
                           :permissions String
                           :permissionData s/Any
                           :token String
                           :refreshToken String}
           :header-params [authorization :- String]
           :middleware    [basic-auth-mw cors-mw authenticated-mw]
           :summary       "Returns auth info given a username and password in the '`Authorization`' header."
           :description   "Authorization header expects '`Basic username:password`' where `username:password`
                           is base64 encoded. To adhere to basic auth standards we have to use a field called
                           `username` however we will accept a valid username or email as a value for this key."
           (auth-credentials-response request))))
