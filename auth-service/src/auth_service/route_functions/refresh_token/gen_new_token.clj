(ns auth-service.route-functions.refresh-token.gen-new-token
  (:require [auth-service.general-functions.user.create-token :refer [create-token]]
            [auth-service.query-defs :as query]
            [ring.util.http-response :as respond]))

(defn create-new-tokens [user]
  (let [new-refresh-token (str (java.util.UUID/randomUUID))]
    (query/update-registered-user-refresh-token! {:refresh_token new-refresh-token :id (:id user)})
    {:token (create-token user) :refreshToken new-refresh-token}))

(defn gen-new-token-response [refresh-token]
  (let [user (query/get-registered-user-details-by-refresh-token {:refresh_token refresh-token})]
    (if (empty? user)
      (respond/bad-request {:error "Bad Request"})
      (respond/ok          (create-new-tokens user)))))
