(ns lipas.backend.middleware
  (:require
    [buddy.auth :refer [authenticated?]]
    [buddy.auth.middleware :refer [wrap-authentication]]
    [lipas.backend.auth :as auth]
    [lipas.backend.token-revocation :as revocation]
    [lipas.roles :as roles]
    [ring.util.http-response :as resp]))

(defn wrap-db
  "Puts the database component into the request as `:lipas/db`.

  `auth` below needs a database for the token-revocation check and, being plain
  ring middleware mounted per route, has no way of its own to reach the system
  ctx. Installed FIRST in the global middleware chain in
  `lipas.backend.handler/create-app`, which closes over ctx — so every route
  gets it, and `auth` can rely on it being there (it fails closed if it is not).

  Handlers should keep taking `db` from the closure; this key exists for
  middleware."
  [db]
  (fn [handler]
    (fn [request]
      (handler (assoc request :lipas/db db)))))

(def ^:private token-authenticated?-key
  "Set on requests whose identity came from a JWT, by `token-auth` below.

  `auth` needs to tell a token identity from a basic-auth one: only tokens can
  be revoked, and an `:iat`-less identity means opposite things in the two
  cases. From a token it means \"minted before revocation shipped\" and must be
  rejected; from basic auth it is simply the stored user map, and rejecting it
  would lock a user out of password login permanently the moment an admin
  touched their roles."
  ::token-authenticated?)

(defn auth
  "Middleware used in routes that require authentication. If request is not
   authenticated a 401 not authorized response will be returned.

   This is also where per-user token revocation is enforced (finding M7), and it
   is the reason the check lives here rather than on individual routes: every
   gated route in the app passes through this one function —
   `privilege-middleware` calls it internally, and the routes that authenticate
   manually list it in their own `:middleware`. One check here covers all of
   them, and a route added later is covered by construction.

   The rejection is byte-identical to the unauthenticated one on purpose: a
   caller holding a revoked token learns no more than that it does not work,
   which is all an expired token tells them too. See
   `lipas.backend.token-revocation` for the semantics and the fail-closed
   choice."
  [handler]
  (fn [request]
    (cond
      (= :options (:request-method request))
      (handler request)

      (not (authenticated? request))
      (resp/unauthorized {:error "Not authorized"})

      (and (get request token-authenticated?-key)
           (revocation/revoked? (:lipas/db request) (:identity request)))
      (resp/unauthorized {:error "Not authorized"})

      :else
      (handler request))))

(defn basic-auth [db]
  (fn [handler]
    (wrap-authentication handler (auth/basic-auth-backend db))))

(def allow-methods "GET, PUT, PATCH, POST, DELETE, OPTIONS")
(def allow-headers "Authorization, Content-Type")

(defn add-cors-headers [resp]
  (-> resp
      (assoc-in [:headers "Access-Control-Allow-Origin"] "*")
      (assoc-in [:headers "Access-Control-Allow-Methods"] allow-methods)
      (assoc-in [:headers "Access-Control-Allow-Headers"] allow-headers)))

(def cors-middleware
  "Cross-origin Resource Sharing (CORS) middleware. Allow requests from all
   origins, all http methods and Authorization and Content-Type headers."
  {:name ::cors-middleware
   :compile
   (fn [route-data _opts]
     (when (:cors route-data)
       (fn [next-handler]
         (fn [request]
           (let [response (if (= :options (:request-method request))
                            {:status 200}
                            (next-handler request))]
             (add-cors-headers response))))))})

(defn token-auth
  "Middleware used on routes requiring token authentication.

  Marks the request so `auth` (which always runs beneath this one, both in
  route `:middleware` vectors and in `privilege-middleware`) knows the identity
  came from a token and is therefore subject to revocation."
  [handler]
  (-> (fn [request]
        (handler (assoc request token-authenticated?-key true)))
      (wrap-authentication auth/token-backend)))

(def privilege-middleware
  {:name ::require-privilege
   :compile
   ;; Use:
   ;; :required-privilege :users/manage
   ;; :required-privilege [{:type-code ::roles/any} :site/create-dit]
   ;; :required-privilege [(fn [req] {:type-code ...}) :site/create-dit]
   ;; :required-privilege custom-auth-fn ; Function that takes request and returns boolean
   ;; Last case can be used to retreive the role-context values from request parameters (like path-params)
   (fn [route-data _opts]
     (if-let [required-privilege (:require-privilege route-data)]
       (let [[role-context privilege] (if (vector? required-privilege)
                                        required-privilege
                                        [nil required-privilege])]
         (fn [next-handler]
           (-> (fn [req]
                 (let [role-context (if (fn? role-context)
                                      (role-context req)
                                      role-context)
                       authorized? (if (fn? privilege)
                                     ;; If privilege is a function, call it with the request
                                     (privilege req)
                                     ;; Otherwise use the standard privilege check
                                     (roles/check-privilege (:identity req) role-context privilege))]
                   (if authorized?
                     (next-handler req)
                     (resp/forbidden {:error "Missing privilege"}))))
               (auth)
               (token-auth))))
       {}))})
