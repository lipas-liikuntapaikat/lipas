(ns auth-service.route-functions.auth.get-auth-credentials
  (:require [auth-service.general-functions.user.create-token :refer [create-token]]
            [auth-service.query-defs :as query]
            [ring.util.http-response :as respond]))

(defn auth-credentials-response
  "Route requires basic authentication and will generate a new
   refresh-token."
  [request]
  (let [user          (:identity request)
        refresh-token (str (java.util.UUID/randomUUID))]
    (query/update-registered-user-refresh-token! {:refresh_token refresh-token :id (:id user)})
    (respond/ok {:id            (:id user)
                 :username      (:username user)
                 :permissions   (:permissions user)
                 :token         (create-token user)
                 :refreshToken  refresh-token})))
