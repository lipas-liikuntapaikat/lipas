(ns lipas.backend.auth
  (:require [buddy.auth.backends :refer [jws]]
            [buddy.auth.backends.httpbasic :refer [http-basic-backend]]
            [buddy.hashers :as hashers]
            [buddy.sign.jwt :as jwt]
            [environ.core :refer [env]]
            [lipas.backend.core :as core]))

(defn create-token [user]
  (let [payload (-> user
                    (select-keys [:id :email :username :user-data :permissions])
                    (assoc :exp (.plusSeconds (java.time.Instant/now) 1800)))]
    (jwt/sign payload (env :auth-key) {:alg :hs512})))

(defn basic-auth
  [db request {:keys [username password]}]
  (let [user (core/get-user db username)]
    (if (and user (hashers/check password (:password user)))
      (-> user
          (dissoc :password)
          (assoc :token (create-token user)))
      false)))

(defn basic-auth-backend
  [db]
  (http-basic-backend {:authfn (partial basic-auth db)}))

(def token-backend
  (jws {:secret (env :auth-key) :options {:alg :hs512}}))
