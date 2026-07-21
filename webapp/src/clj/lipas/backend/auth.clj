(ns lipas.backend.auth
  (:require
    [buddy.auth.backends :refer [jws]]
    [buddy.auth.backends.httpbasic :refer [http-basic-backend]]
    [buddy.hashers :as hashers]
    [environ.core :refer [env]]
    [lipas.backend.core :as core]
    [lipas.backend.jwt :as jwt]
    [lipas.backend.org :as org]
    [lipas.roles :as roles]))

(defn enrich-org-roles
  "Project the user's org-derived roles and merge them into the user's roles.
  Both existing and derived roles are conformed to the same (keyword/set) shape
  and deduped, so a legacy account org role and its derived twin collapse.
  Derived roles live only in the resulting token — never persisted."
  [db user]
  (update-in user [:permissions :roles]
             (fn [roles]
               (->> (org/derive-user-org-roles db (:id user))
                    (concat roles)
                    roles/conform-roles
                    distinct
                    vec))))

(defn basic-auth
  [db _request {:keys [username password]}]
  (let [user (core/get-user db username)]
    (if (and user (hashers/check password (:password user)))
      (let [user (enrich-org-roles db user)]
        (-> user
            (dissoc :password)
            (update-in [:permissions :roles] roles/conform-roles)
            (assoc :token (jwt/create-token user))))
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
