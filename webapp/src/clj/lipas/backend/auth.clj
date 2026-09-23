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

(def enrich-org-roles
  "See `lipas.backend.org/enrich-org-roles`. Kept as an alias because this is
  the name every existing caller uses."
  org/enrich-org-roles)

(defn active?
  "Whether this account may still hold a session.

  `:status` is `[:enum \"active\" \"archived\"]`. Nothing used to read it during
  authentication, which made both ways of setting it inert: an admin
  deactivating an account via /actions/update-user-status changed nothing, and
  a GDPR-archived user kept logging in and renewing tokens indefinitely. Every
  path that MINTS a token checks this now.

  This is the MINTING side. Tokens already in the wild are handled by
  `lipas.backend.token-revocation`, which `lipas.backend.middleware/auth`
  applies to every authenticated request: archiving an account moves its
  `account.tokens_valid_from`, and every token issued before that stops being
  accepted."
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
