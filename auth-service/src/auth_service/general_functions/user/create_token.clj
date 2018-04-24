(ns auth-service.general-functions.user.create-token
   (:require [environ.core :refer [env]]
             [clj-time.core :as time]
             [buddy.sign.jwt :as jwt]))

(defn create-token
  "Create a signed json web token. The token contents are; username, email, id,
   permissions and token expiration time. Tokens are valid for 15 minutes."
  [user]
  (let [user    (-> user
                    (update :username str)
                    (update :email str)
                    (assoc     :exp (time/plus (time/now) (time/seconds 900))))
        payload (-> user
                    (select-keys [:permissions
                                  :permission_data
                                  :username
                                  :email
                                  :id
                                  :exp]))]
    (jwt/sign payload (env :auth-key) {:alg :hs512})))
