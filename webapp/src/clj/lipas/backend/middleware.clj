(ns lipas.backend.middleware
  (:require
   [buddy.auth :refer [authenticated?]]
   [buddy.auth.middleware :refer [wrap-authentication]]
   [lipas.backend.auth :as auth]
   [lipas.roles :as roles]
   [ring.util.http-response :as resp]))

(defn auth
  "Middleware used in routes that require authentication. If request is not
   authenticated a 401 not authorized response will be returned"
  [handler]
  (fn [request]
    (if (authenticated? request)
      (handler request)
      (resp/unauthorized {:error "Not authorized"}))))

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
  "Middleware used on routes requiring token authentication"
  [handler]
  (wrap-authentication handler auth/token-backend))

(def privilege-middleware
  {:name ::require-privilege
   :compile
   ;; Use:
   ;; :required-privilege :users/manage
   ;; :required-privilege [{:type-code ::roles/any} :site/create-dit]
   ;; :required-privilege [(fn [req] {:type-code ...}) :site/create-dit]
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
                                      role-context)]
                   (if (roles/check-privilege (:identity req) role-context privilege)
                     (next-handler req)
                     (resp/forbidden {:error "Missing privilege"}))))
               (auth)
               (token-auth))))
       {}))})
