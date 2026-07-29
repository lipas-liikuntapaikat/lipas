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

(defn active?
  "Whether this account may still hold a session.

  `:status` is `[:enum \"active\" \"archived\"]`. Nothing used to read it during
  authentication, which made both ways of setting it inert: an admin
  deactivating an account via /actions/update-user-status changed nothing, and
  a GDPR-archived user kept logging in and renewing tokens indefinitely. Every
  path that MINTS a token checks this now.

  A token already in the wild still works until it expires — revoking those
  needs per-request state we don't keep (tracked separately). Blocking issuance
  bounds the damage to one token lifetime."
  [user]
  (= "active" (:status user)))

(defn basic-auth
  [db _request {:keys [username password]}]
  (let [user (core/get-user db username)]
    ;; Status is checked AFTER the password so an archived account can't be
    ;; distinguished from a wrong password by response timing.
    (if (and user (hashers/check password (:password user)) (active? user))
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
