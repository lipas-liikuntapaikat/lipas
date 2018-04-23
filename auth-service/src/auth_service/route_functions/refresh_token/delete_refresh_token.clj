(ns auth-service.route-functions.refresh-token.delete-refresh-token
  (:require [auth-service.query-defs :as query]
            [ring.util.http-response :as respond]))

(defn remove-refresh-token-response [refresh-token]
  (let [null-refresh-token (query/null-refresh-token! {:refresh_token refresh-token})]
    (if (zero? null-refresh-token)
      (respond/not-found  {:error "The refresh token does not exist"})
      (respond/ok         {:message "Refresh token successfully deleted"}))))

