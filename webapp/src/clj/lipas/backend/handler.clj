(ns lipas.backend.handler
  (:require
   [clojure.java.io :as io]
   [clojure.spec.alpha :as s]
   [lipas.backend.core :as core]
   [lipas.backend.jwt :as jwt]
   [lipas.backend.middleware :as mw]
   [lipas.schema.core]
   [lipas.utils :as utils]
   [muuntaja.core :as m]
   [reitit.coercion.spec]
   [reitit.ring :as ring]
   [reitit.ring.coercion :as coercion]
   [reitit.ring.middleware.exception :as exception]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.swagger :as swagger]
   [reitit.swagger-ui :as swagger-ui]
   [ring.middleware.params :as params]
   [ring.util.io :as ring-io]))

(defn exception-handler [status type]
  (fn [^Exception e _request]
    (-> {:status status
         :body   {:message (.getMessage e)
                  :type    type}}
        mw/add-cors-headers)))

(def exception-handlers
  {:username-conflict  (exception-handler 409 :username-conflict)
   :email-conflict     (exception-handler 409 :email-conflict)
   :no-permission      (exception-handler 403 :no-permission)
   :user-not-found     (exception-handler 404 :user-not-found)
   :email-not-found    (exception-handler 404 :email-not-found)
   :reminder-not-found (exception-handler 404 :reminder-not-found)

   :qbits.spandex/response-exception (exception-handler 500 :internal-server-error)})

(def exceptions-mw
  (exception/create-exception-middleware
   (merge
    exception/default-handlers
    exception-handlers
    ;;Prints all stack traces
    {::exception/wrap
     (fn [handler e request]
       (prn (ex-data e))
       (.printStackTrace e)
       (handler e request))}
    )))

(defn create-app [{:keys [db emailer search]}]
  (ring/ring-handler
   (ring/router

    [["/favicon.ico"
      {:get
       {:no-doc true
        :handler
        (fn [_]
          {:status  200
           :headers {"Content-Type" "image/x-icon"}
           :body    (io/input-stream (io/resource "public/favicon.ico"))})}}]

     ["/index.html"
      {:get
       {:no-doc true
        :handler
        (fn [_]
          {:status  200
           :headers {"Content-Type" "text/html"}
           :body    (io/input-stream (io/resource "public/index.html"))})}}]

     ["/swagger.json"
      {:get
       {:no-doc  true
        :swagger {:info {:title "my-api"}}
        :handler (swagger/create-swagger-handler)}}]

     ["/api"
      {:middleware [mw/cors]
       :no-doc     true
       :options
       {:handler
        (fn [_]
          {:status 200
           :body   {:status "OK"}})}}

      ["/health"
       {:get
        {:handler
         (fn [_]
           {:status 200
            :body   {:status "OK"}})}}]

      ["/sports-sites"
       {:post
        {:no-doc     false
         :middleware [mw/token-auth mw/auth]
         :parameters
         {:query :lipas.api/query-params
          :body  map?}
         :handler
         (fn [{:keys [body-params identity] :as req}]
           (let [spec :lipas/new-or-existing-sports-site
                 draft? (-> req :parameters :query :draft utils/->bool)
                 valid? (s/valid? spec body-params)]
             (if valid?
               {:status 201
                :body   (core/save-sports-site! db search identity body-params draft?)}
               {:status 400
                :body (s/explain-data spec body-params)})))}}]

      ["/sports-sites/:lipas-id"
       {:get
        {:no-doc     false
         :parameters {:path {:lipas-id int?}}
         :handler
         (fn [{{{:keys [lipas-id]} :path} :parameters}]
           (if-let [res (core/get-sports-site db lipas-id)]
             {:status 200 :body res}
             {:status 404 :body {:message "Not found"}}))}}]

      ["/sports-sites/history/:lipas-id"
       {:get
        {:parameters {:path {:lipas-id int?}}
         :handler
         (fn [{{{:keys [lipas-id]} :path} :parameters}]
           {:status 200
            :body   (core/get-sports-site-history db lipas-id)})}}]

      ["/sports-sites/type/:type-code"
       {:get
        {:parameters
         {:path  {:type-code int?}
          :query :lipas.api/query-params}
         :handler
         (fn [{:keys [parameters]}]
           (let [type-code (-> parameters :path :type-code)
                 revs      (or (-> parameters :query :revs)
                               "latest")
                 locale    (or (-> parameters :query :lang keyword)
                               :none)]
             {:status 200
              :body   (core/get-sports-sites-by-type-code db
                                                          type-code
                                                          {:revs   revs
                                                           :locale locale})}))}}]

      ["/users"
       {:get
        {:middleware [mw/token-auth mw/auth mw/admin]
         :handler
         (fn [_]
           {:status 200
            :body   (core/get-users db)})}}]

      ["/actions/search"
       {:post
        {:no-doc false
         :handler
         (fn [{:keys [body-params]}]
           (core/search search body-params))}}]

      ["/actions/register"
       {:post
        {:handler
         (fn [req]
           (let [user (-> req
                          :body-params
                          (dissoc :permissions))
                 _    (core/register! db emailer user)]
             {:status 201
              :body   {:status "OK"}}))}}]

      ["/actions/login"
       {:post
        {:middleware [(mw/basic-auth db) mw/auth]
         :handler
         (fn [{:keys [identity]}]
           (core/login! db identity)
           {:status 200 :body identity})}}]

      ["/actions/refresh-login"
       {:get
        {:middleware [mw/token-auth mw/auth]
         :handler
         (fn [{:keys [identity]}]
           (let [user (core/get-user! db (-> identity :id))]
             {:status 200
              :body   (merge (dissoc user :password)
                             {:token (jwt/create-token user)})}))}}]

      ["/actions/request-password-reset"
       {:post
        {:parameters {:body {:email string?}}
         :handler
         (fn [{:keys [body-params]}]
           (let [_ (core/send-password-reset-link! db emailer body-params)]
             {:status 200
              :body   {:status "OK"}}))}}]

      ["/actions/reset-password"
       {:post
        {:middleware [mw/token-auth mw/auth]
         :parameters {:body {:password string?}}
         :handler
         (fn [req]
           (let [user (-> req :identity)
                 pass (-> req :parameters :body :password)
                 _    (core/reset-password! db user pass)]
             {:status 200
              :body   {:status "OK"}}))}}]

      ["/actions/update-user-permissions"
       {:post
        {:middleware [mw/token-auth mw/auth mw/admin]
         :parameters
         {:body
          {:id          string?
           :login-url   :lipas.magic-link/login-url
           :permissions :lipas.user/permissions}}
         :handler
         (fn [req]
           (let [params (-> req :parameters :body)
                 _      (core/update-user-permissions! db emailer params)]
             {:status 200
              :body   {:status "OK"}}))}}]

      ["/actions/update-user-data"
       {:post
        {:middleware [mw/token-auth mw/auth]
         :parameters
         {:body :lipas.user/user-data}
         :handler
         (fn [req]
           (let [user-data (-> req :parameters :body)
                 user      (:identity req)]
             {:status 200
              :body   (core/update-user-data! db user user-data)}))}}]

      ["/actions/order-magic-link"
       {:post
        {:parameters
         {:body
          {:email     :lipas/email
           :login-url :lipas.magic-link/login-url
           :variant   :lipas.magic-link/email-variant}}
         :handler
         (fn [req]
           (let [email   (-> req :parameters :body :email)
                 variant (-> req :parameters :body :variant keyword)
                 user    (core/get-user! db email)
                 url     (-> req :parameters :body :login-url)
                 _       (core/send-magic-link! db emailer {:user      user
                                                            :login-url url
                                                            :variant   variant})]
             {:status 200 :body {:status "OK"}}))}}]

      ["/actions/send-magic-link"
       {:post
        {:middleware [mw/token-auth mw/auth mw/admin]
         :parameters
         {:body
          {:login-url string?
           :variant   :lipas.magic-link/email-variant
           :user      :lipas/new-user}}
         :handler
         (fn [req]
           (let [user    (-> req :parameters :body :user)
                 variant (-> req :parameters :body :variant keyword)
                 user    (or (core/get-user db (:email user))
                             (do (core/add-user! db user)
                                 (core/get-user db (:email user))))
                 url     (-> req :parameters :body :login-url)
                 params  {:user user :variant variant :login-url url}
                 _       (core/send-magic-link! db emailer params)]
             {:status 200 :body {:status "OK"}}))}}]

      ["/actions/add-reminder"
       {:post
        {:middleware [mw/token-auth mw/auth]
         :parameters
         {:body :lipas/new-reminder}
         :handler
         (fn [{:keys [identity parameters]}]
           (let [reminder (:body parameters)]
             {:status 200
              :body   (core/add-reminder! db identity reminder)}))}}]

      ["/actions/update-reminder-status"
       {:post
        {:middleware [mw/token-auth mw/auth]
         :parameters
         {:body
          {:id     uuid?
           :status :lipas.reminder/status}}
         :handler
         (fn [{:keys [identity parameters]}]
           (let [params (:body parameters)]
             {:status 200
              :body   (core/update-reminder-status! db identity params)}))}}]

      ["/actions/get-upcoming-reminders"
       {:post
        {:middleware [mw/token-auth mw/auth]
         :handler
         (fn [{:keys [identity]}]
           {:status 200
            :body   (core/get-users-pending-reminders! identity)})}}]

      ["/actions/create-energy-report"
       {:post
        {:parameters
         {:body :lipas.api.energy-report/req}
         :handler
         (fn [{:keys [parameters]}]
           (let [type-code (-> parameters :body :type-code)
                 year      (-> parameters :body :year)]
             {:status 200
              :body   (core/energy-report db type-code year)}))}}]

      ["/actions/create-sports-sites-report"
       {:post
        {:parameters
         {:body :lipas.api.sports-site-report/req}
         :handler
         (fn [{:keys [parameters]}]
           (let [query  (-> parameters :body :search-query)
                 fields (-> parameters :body :fields)]
             {:status  200
              :headers {"Content-Type"        (-> utils/content-type :xlsx)
                        "Content-Disposition" "inline; filename=\"lipas.xlsx\""}
              :body
              (ring-io/piped-input-stream
               (fn [out]
                 (core/sports-sites-report search query fields out)))}))}}]

      ;; Old simple db version
      ["/actions/create-finance-report"
       {:post
        {:parameters
         {:body :lipas.api.finance-report/req}
         :handler
         (fn [{:keys [parameters]}]
           (let [params (:body parameters)]
             {:status 200
              :body   (core/finance-report db params)}))}}]

      ;; New version that uses ES backend
      ["/actions/query-finance-report"
       {:post
        {:parameters
         {:body map?}
         :handler
         (fn [{:keys [parameters]}]
           (let [params (:body parameters)]
             {:status 200
              :body   (core/query-finance-report search params)}))}}]

      ;; Subsidies
      ["/actions/query-subsidies"
       {:post
        {:parameters
         {:body map?}
         :handler
         (fn [{:keys [parameters]}]
           (let [params (:body parameters)]
             {:status 200
              :body   (core/query-subsidies search params)}))}}]

      ["/actions/calculate-stats"
       {:post
        {:parameters {}
         :handler
         (fn [{:keys [body-params]}]
           (let [city-codes (-> body-params :city-codes)
                 type-codes (-> body-params :type-codes)
                 grouping   (-> body-params (:grouping "location.city.city-code"))]
             {:status 200
              :body   (core/calculate-stats db search city-codes type-codes grouping)}))}}]]]

    {:data
     {:coercion   reitit.coercion.spec/coercion
      :muuntaja   m/instance
      :middleware [;; query-params & form-params
                   params/wrap-params
                   ;; content-negotiation
                   muuntaja/format-negotiate-middleware
                   ;; encoding response body
                   muuntaja/format-response-middleware
                   ;; exception handling
                   exceptions-mw
                   ;; decoding request body
                   muuntaja/format-request-middleware
                   ;; coercing response bodys
                   coercion/coerce-response-middleware
                   ;; coercing request parameters
                   coercion/coerce-request-middleware]}})
   (ring/routes
    (swagger-ui/create-swagger-ui-handler {:path "/"})
    (ring/create-default-handler))))
