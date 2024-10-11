(ns lipas.backend.auth
  (:require
   [buddy.auth.backends :refer [jws]]
   [buddy.auth.backends.httpbasic :refer [http-basic-backend]]
   [buddy.hashers :as hashers]
   [environ.core :refer [env]]
   [lipas.backend.core :as core]
   [lipas.backend.jwt :as jwt]
   [lipas.roles :as roles]))

(defn basic-auth
  [db request {:keys [username password]}]
  (let [user (core/get-user db username)]
    (if (and user (hashers/check password (:password user)))
      (-> user
          (dissoc :password)
          (assoc :token (jwt/create-token user)))
      false)))

(defn basic-auth-backend
  [db]
  (http-basic-backend {:authfn (partial basic-auth db)}))

(def token-backend
  (jws {:secret (env :auth-key)
        :authfn (fn [token-data]
                  ;; unmarshall the permissions/roles to use keywords and sets
                  (if (:permissions token-data)
                    (update-in token-data [:permissions :roles] roles/conform-roles)
                    token-data))
        :options {:alg :hs512}}))
