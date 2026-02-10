(ns lipas.backend.handler
  (:require [clojure.java.io :as io]
            [lipas.backend.analysis.heatmap :as heatmap]
            [lipas.backend.api.v1.routes :as v1]
            [lipas.backend.api.v2 :as v2]
            [lipas.backend.bulk-operations.handler :as bulk-ops-handler]
            [lipas.backend.core :as core]
            [lipas.backend.jwt :as jwt]
            [lipas.backend.middleware :as mw]
            [lipas.backend.org :as org]
            [lipas.backend.ptv.handler :as ptv-handler]
            [lipas.jobs.handler :as jobs-handler]
            [lipas.roles :as roles]
            [lipas.schema.diversity :as diversity-schema]
            [lipas.schema.feedback :as feedback-schema]
            [lipas.schema.handler :as handler-schema]
            [lipas.schema.help :as help-schema]
            [lipas.schema.lois :as loi-schema]
            [lipas.schema.org :as org-schema]
            [lipas.schema.reminders :as reminders-schema]
            [lipas.schema.sports-sites :as sports-site-schema]
            [lipas.schema.sports-sites.types :as types-schema]
            [lipas.schema.users :as users-schema]
            [lipas.utils :as utils]
            [malli.core :as malli]
            [malli.error :as me]
            [muuntaja.core :as m]
            [reitit.coercion.malli]
            [reitit.ring :as ring]
            [reitit.ring.coercion :as coercion]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.multipart :as multipart]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [ring.middleware.params :as params]
            [ring.util.io :as ring-io]
            [taoensso.timbre :as log]))

(defn exception-handler
  ([status type]
   (exception-handler status type false))
  ([status type print-stack?]
   (fn [^Exception e _request]
     (when print-stack?
       (log/error e))
     (-> {:status status
          :body {:message (.getMessage e)
                 :type type}}
         mw/add-cors-headers))))

(def exception-handlers
  {:username-conflict (exception-handler 409 :username-conflict)
   :email-conflict (exception-handler 409 :email-conflict)
   :no-permission (exception-handler 403 :no-permission)
   :user-not-found (exception-handler 404 :user-not-found)
   :email-not-found (exception-handler 404 :email-not-found)
   :reminder-not-found (exception-handler 404 :reminder-not-found)
   :invalid-payload (exception-handler 400 :invalid-payload)

   :qbits.spandex/response-exception (exception-handler 500 :internal-server-error :print-stack)

   ;; Return 500 and print stack trace for exceptions that are not
   ;; specifically handled
   ::exception/default (exception-handler 500 :internal-server-error :print-stack)

   :reitit.coercion/response-coercion
   (let [handler (:reitit.coercion/response-coercion exception/default-handlers)]
     (fn [e x]
       (log/errorf e "Response coercion error")
       (handler e x)))

   :reitit.coercion/request-coercion
   (let [default-handler (:reitit.coercion/request-coercion exception/default-handlers)]
     (fn [e request]
       (log/errorf e "Request coercion error")
       (if (clojure.string/starts-with? (:uri request) "/v1")
         ;; Legacy API format: {"errors":{"fieldName":["error message"]}}
         ;; Use malli.error/humanize to get human-readable messages
         (let [{:keys [schema value errors]} (ex-data e)
               humanized (me/humanize {:schema schema :value value :errors errors})
               ;; humanized is a map like {:pageSize ["should be a positive int"]}
               ;; Convert to match production format where values are vectors of strings
               formatted (reduce-kv
                          (fn [acc k v]
                            (assoc acc k (if (sequential? v) v [v])))
                          {}
                          humanized)]
           {:status 400
            :body {:errors formatted}})
         ;; Default format for other APIs
         (default-handler e request))))})

(def exceptions-mw
  (exception/create-exception-middleware
   (merge
    exception/default-handlers
    exception-handlers)))

(defn create-app
  [{:keys [db emailer search mailchimp aws ptv] :as ctx}]
  (ring/ring-handler
   (ring/router

    [["/favicon.ico"
      {:get
       {:no-doc true
        :handler
        (fn [_]
          {:status 200
           :headers {"Content-Type" "image/x-icon"}
           :body (io/input-stream (io/resource "public/favicon.ico"))})}}]

     ["/index.html"
      {:get
       {:no-doc true
        :handler
        (fn [_]
          {:status 200
           :headers {"Content-Type" "text/html"}
           :body (io/input-stream (io/resource "public/index.html"))})}}]

     ["/api"
      {:cors true
       :no-doc true}

      ["/swagger.json"
       {:get
        {:no-doc true
         :swagger {:info {:title "Lipas-API v2"}
                   :securityDefinitions
                   {:token-auth
                    {:type "apiKey"
                     :in "header"
                     :name "Authorization"}}}
         :handler (swagger/create-swagger-handler)}}]

      ["/health"
       {:get
        {:no-doc true
         :handler
         (fn [_]
           {:status 200
            :body {:status "OK"}})}}]

      ["/sports-sites"
       {:post
        {:no-doc false
         :middleware [mw/token-auth mw/auth]
           ;; NOTE: privilege checked in the core code
         :responses {201 {:body sports-site-schema/new-or-existing-sports-site}
                     400 {:body [:map {:closed false}]}}
         :parameters
         {:query [:map [:draft {:optional true} :boolean]]
          :body #'sports-site-schema/new-or-existing-sports-site}
         :handler
         (fn [{:keys [body-params identity] :as req}]
           (let [spec sports-site-schema/new-or-existing-sports-site
                 draft? (-> req :parameters :query :draft utils/->bool)
                 valid? (malli/validate spec body-params)]
             (if valid?
               {:status 201
                :body (core/save-sports-site! db search ptv identity body-params draft?)}
               {:status 400
                :body (malli/explain spec body-params)})))}}]

      ["/sports-sites/:lipas-id"
       {:get
        {:no-doc false
         :parameters {:path {:lipas-id int?}
                      :query [:map [:lang {:optional true} [:enum "fi" "en" "se" "all"]]]}
         ;; Use compatibility schema to coerce type-codes and
         ;; city-codes as Long's. The default malli json transformer
         ;; turns them into strings (V2 API model).
         :responses {200 {:body sports-site-schema/sports-site-compat}
                     404 {:body [:map {:closed false}]}}
         :handler
         (fn [req]
           (let [lipas-id (-> req :parameters :path :lipas-id)
                 locale (or (-> req :parameters :query :lang keyword)
                            :none)]
             (if-let [res (core/get-sports-site2 search lipas-id locale)]
               {:status 200 :body res}
               {:status 404 :body {:message "Not found"}})))}}]

      ["/sports-sites/history/:lipas-id"
       {:get
        {:no-doc false
         :parameters {:path {:lipas-id int?}}
         :responses {200 {:body [:sequential sports-site-schema/sports-site-compat]}}
         :handler
         (fn [{{{:keys [lipas-id]} :path} :parameters}]
           {:status 200
            :body (core/get-sports-site-history db lipas-id)})}}]

      ["/sports-sites/type/:type-code"
       {:get
        {:no-doc false
         :responses {200 {:body [:sequential [:map {:closed false}]]}}
         :parameters
         {:path {:type-code #'types-schema/type-code-with-legacy}
          :query handler-schema/sports-sites-query-params}
         :handler
         (fn [{:keys [parameters]}]
           (let [type-code (-> parameters :path :type-code)
                 #_#_revs (or (-> parameters :query :revs)
                              "latest")
                 locale (or (-> parameters :query :lang keyword)
                            :none)]
             {:status 200
              :body (core/get-sports-sites-by-type-code db
                                                        type-code
                                                        {#_#_:revs revs
                                                         :locale locale})}))}}]

      ["/lois"
       {:get
        {:no-doc false
         :responses {200 {:body loi-schema/loi-documents}}
         :parameters {}
         :handler
         (fn []
           (let [query {:size 10000 :query {:match_all {}}}]
             {:status 200
              :body (core/search-lois search query)}))}}]

      ["/lois/:loi-id"
       {:get
        {:no-doc false
         :responses {200 {:body loi-schema/loi-document}}
         :parameters {:path {:loi-id #'loi-schema/loi-id}}
         :handler
         (fn [{:keys [parameters]}]
           {:status 200
            :body (core/get-loi search (get-in parameters [:path :loi-id]))})}}]

      ["/lois/type/:loi-type"
       {:get
        {:no-doc false
         :responses {200 {:body loi-schema/loi-documents}}
         :parameters
         {:path {:loi-type #'loi-schema/loi-type}
          :query handler-schema/sports-sites-query-params}
         :handler
         (fn [{:keys [parameters]}]
           (let [loi-type (-> parameters :path :loi-type)
                 query {:size 10000 :query {:term {:loi-type.keyword loi-type}}}]
             {:status 200
              :body (core/search-lois search query)}))}}]

      ["/lois/category/:loi-category"
       {:get
        {:no-doc false
         :responses {200 {:body loi-schema/loi-documents}}
         :parameters
         {:path {:loi-category #'loi-schema/loi-category}
          :query handler-schema/sports-sites-query-params}
         :handler
         (fn [{:keys [parameters]}]
           (let [loi-category (-> parameters :path :loi-category)
                 query {:size 10000 :query {:term {:loi-category.keyword loi-category}}}]
             {:status 200
              :body (core/search-lois search query)}))}}]

      ["/lois/status/:status"
       {:get
        {:no-doc false
         :responses {200 {:body loi-schema/loi-documents}}
         :parameters
         {:path {:status #'loi-schema/loi-status}
          :query handler-schema/sports-sites-query-params}
         :handler
         (fn [{:keys [parameters]}]
           (let [loi-status (-> parameters :path :status)
                 query {:size 10000 :query {:term {:status.keyword loi-status}}}]
             {:status 200
              :body (core/search-lois search query)}))}}]

      ["/users"
       {:get
        {:no-doc true
         :require-privilege :users/manage
         :handler
         (fn [_]
           {:status 200
            :body (core/get-users db)})}}]

        ;; FIXME: Where should this be?
      ["/current-user-orgs"
       {:get
        {:no-doc false
           ;; Doesn't require privileges, no :org/member just means no orgs.
         :require-privilege nil
           ;; Need to mount the auth manually when no :require-privilege enabled
         :middleware [mw/token-auth mw/auth]
         :handler (fn [req]
                    (let [user (:identity req)]
                      {:status 200
                       :body (cond
                                 ;; Admins see all organizations
                               (roles/check-role user :admin)
                               (org/all-orgs db)

                                 ;; PTV auditors see all organizations (they need to audit any org)
                               (roles/check-privilege user {} :ptv/audit)
                               (org/all-orgs db)

                                 ;; Regular users see only their assigned organizations
                               :else
                               (org/user-orgs db (parse-uuid (:id user))))}))}}]

      ["/orgs"
       {:no-doc false}
       [""
        {;; Only admin users
         :require-privilege :org/admin
         :get
         {:handler
          (fn [_]
            {:status 200
             :body (org/all-orgs db)})}
         :post
         {:parameters {:body org-schema/new-org}
          :handler
          (fn [req]
            {:status 200
             :body (org/create-org db (-> req :parameters :body))})}}]
       ["/:org-id"
        {:parameters {:path [:map
                             [:org-id org-schema/org-id]]}}
        [""
         {;; Only org-admins can update org details
          :require-privilege [(fn [req] {:org-id (str (-> req :parameters :path :org-id))}) :org/manage]
          :put
          {:parameters {:body org-schema/org}
           :handler (fn [req]
                      (org/update-org! db
                                       (-> req :parameters :path :org-id)
                                       (-> req :parameters :body))
                      {:status 200
                       :body {}})}}]
        ["/users"
         {:get
          {;; Both org-admins and org-members can view users
           :require-privilege (fn [req]
                                (let [user (:identity req)]
                                  ;; Allow if user is admin OR has org/member privilege
                                  (or (roles/check-role user :admin)
                                      (roles/check-privilege user
                                                             {:org-id (str (-> req :parameters :path :org-id))}
                                                             :org/member))))
           :handler (fn [req]
                      {:status 200
                       :body (org/get-org-users db (-> req :parameters :path :org-id))})}
          :post
          {;; Only org-admins can modify users
           :require-privilege [(fn [req] {:org-id (str (-> req :parameters :path :org-id))}) :org/manage]
           :parameters {:body org-schema/user-updates}
           :handler (fn [req]
                      (org/update-org-users! db
                                             (-> req :parameters :path :org-id)
                                             (-> req :parameters :body :changes))
                      {:status 200
                       :body {}})}}]
        ["/add-user-by-email"
         {;; Only org-admins can add users
          :require-privilege [(fn [req] {:org-id (str (-> req :parameters :path :org-id))}) :org/manage]
          :post
          {:parameters {:body [:map
                               [:email :string]
                               [:role [:enum "org-admin" "org-user"]]]}
           :handler (fn [req]
                      (let [org-id (-> req :parameters :path :org-id)
                            email (-> req :parameters :body :email)
                            role (-> req :parameters :body :role)
                            result (org/add-org-user-by-email! db org-id email role)]
                        {:status (if (:success? result) 200 400)
                         :body result}))}}]
        ["/ptv-config"
         {;; Only LIPAS admins can configure PTV settings
          :require-privilege :users/manage
          :put
          {:parameters {:body org-schema/ptv-config-update}
           :handler (fn [req]
                      (let [org-id (-> req :parameters :path :org-id)
                            ptv-config (-> req :parameters :body)]
                        (org/update-org-ptv-config! db org-id ptv-config)
                        {:status 200
                         :body {:message "PTV configuration updated successfully"}}))}}]]]

      ["/actions/gdpr-remove-user"
       {:post
        {:no-doc true
         :require-privilege :users/manage
         :handler
         (fn [{:keys [body-params]}]
           (let [{:keys [id] :as user} (core/get-user! db (or (:id body-params)
                                                              (:username body-params)
                                                              (:email body-params)))]
             (core/gdpr-remove-user! db user)
             {:status 200
              :body (core/get-user db (str id))}))}}]

      ["/actions/search"
       {:post
        {:no-doc false
         :handler
         (fn [{:keys [body-params]}]
           (core/search search body-params))}}]

      ["/actions/find-fields"
       {:post
        {:no-doc false
         :parameters {:body handler-schema/find-fields-payload}
         :responses {200 {:body [:sequential [:map {:closed false}]]}}
         :handler
         (fn [{:keys [body-params]}]
           {:status 200
            :body (core/search-fields search body-params)})}}]

      ["/actions/register"
       {:post
        {:no-doc true
         :handler
         (fn [req]
           (let [user (-> req
                          :body-params
                          (dissoc :permissions))
                 _ (core/register! db emailer user)]
             {:status 201
              :body {:status "OK"}}))}}]

      ["/actions/login"
       {:post
        {:no-doc true
         :middleware [(mw/basic-auth db) mw/auth]
         :handler
         (fn [{:keys [identity]}]
           (core/login! db identity)
           {:status 200 :body identity})}}]

      ["/actions/refresh-login"
       {:get
        {:no-doc true
         :middleware [mw/token-auth mw/auth]
         :handler
         (fn [{:keys [identity]}]
           (let [user (core/get-user! db (-> identity :id))]
             {:status 200
              :body (merge (dissoc user :password)
                           {:token (jwt/create-token user)})}))}}]

      ["/actions/request-password-reset"
       {:post
        {:no-doc true
         :parameters {:body {:email string?}}
         :handler
         (fn [{:keys [body-params]}]
           (let [_ (core/send-password-reset-link! db emailer body-params)]
             {:status 200
              :body {:status "OK"}}))}}]

      ["/actions/reset-password"
       {:post
        {:no-doc true
         :middleware [mw/token-auth mw/auth]
         :parameters {:body {:password string?}}
         :handler
         (fn [req]
           (let [user (-> req :identity)
                 pass (-> req :parameters :body :password)
                 _ (core/reset-password! db user pass)]
             {:status 200
              :body {:status "OK"}}))}}]

      ["/actions/update-user-permissions"
       {:post
        {:no-doc true
         :require-privilege :users/manage
         :parameters
         {:body
          {:id string?
           :login-url handler-schema/magic-link-login-url
           :permissions users-schema/permissions-schema}}
         :handler
         (fn [req]
           (let [params (-> req :parameters :body)
                 _ (core/update-user-permissions! db emailer params)]
             {:status 200
              :body {:status "OK"}}))}}]

      ["/actions/update-user-status"
       {:post
        {:no-doc true
         :require-privilege :users/manage
         :parameters
         {:body
          {:id string?
           :status users-schema/user-status}}
         :handler
         (fn [req]
           {:status 200
            :body (core/update-user-status! db (-> req :parameters :body))})}}]

      ["/actions/update-user-data"
       {:post
        {:no-doc true
         :middleware [mw/token-auth mw/auth]
         :parameters
         {:body users-schema/user-data-schema}
         :handler
         (fn [req]
           (let [user-data (-> req :parameters :body)
                 user (:identity req)]
             {:status 200
              :body (core/update-user-data! db user user-data)}))}}]

      ["/actions/order-magic-link"
       {:post
        {:no-doc true
         :parameters
         {:body
          {:email users-schema/email-schema
           :login-url handler-schema/magic-link-login-url
           :variant handler-schema/email-variant}}
         :handler
         (fn [req]
           (let [email (-> req :parameters :body :email)
                 variant (-> req :parameters :body :variant keyword)
                 user (core/get-user! db email)
                 url (-> req :parameters :body :login-url)
                 _ (core/send-magic-link! db emailer {:user user
                                                      :login-url url
                                                      :variant variant})]
             {:status 200 :body {:status "OK"}}))}}]

      ["/actions/send-magic-link"
       {:post
        {:no-doc true
         :require-privilege :users/manage
         :parameters
         {:body
          {:login-url string?
           :variant handler-schema/email-variant
           :user users-schema/new-user-schema}}
         :handler
         (fn [req]
           (let [user (-> req :parameters :body :user)
                 variant (-> req :parameters :body :variant keyword)
                 user (or (core/get-user db (:email user))
                          (do (core/add-user! db user)
                              (core/get-user db (:email user))))
                 url (-> req :parameters :body :login-url)
                 params {:user user :variant variant :login-url url}
                 _ (core/send-magic-link! db emailer params)]
             {:status 200 :body {:status "OK"}}))}}]

      ["/actions/add-reminder"
       {:post
        {:no-doc true
         :middleware [mw/token-auth mw/auth]
         :parameters
         {:body reminders-schema/new-reminder}
         :handler
         (fn [{:keys [identity parameters]}]
           (let [reminder (:body parameters)]
             {:status 200
              :body (core/add-reminder! db identity reminder)}))}}]

      ["/actions/update-reminder-status"
       {:post
        {:no-doc true
         :middleware [mw/token-auth mw/auth]
         :parameters
         {:body
          {:id uuid?
           :status reminders-schema/reminder-status}}
         :handler
         (fn [{:keys [identity parameters]}]
           (let [params (:body parameters)]
             {:status 200
              :body (core/update-reminder-status! db identity params)}))}}]

      ["/actions/get-upcoming-reminders"
       {:post
        {:no-doc true
         :middleware [mw/token-auth mw/auth]
         :handler
         (fn [{:keys [identity]}]
           {:status 200
            :body (core/get-users-pending-reminders! db identity)})}}]

      ["/actions/create-energy-report"
       {:post
        {:no-doc true
         :parameters
         {:body handler-schema/energy-report-req}
         :handler
         (fn [{:keys [parameters]}]
           (let [type-code (-> parameters :body :type-code)
                 year (-> parameters :body :year)]
             {:status 200
              :body (core/energy-report db type-code year)}))}}]

      ["/actions/create-sports-sites-report"
       {:post
        {:no-doc false
         :parameters
         {:body handler-schema/sports-site-report-req}
         :handler
         (fn [{:keys [parameters]}]
           (let [query (-> parameters :body :search-query)
                 fields (-> parameters :body :fields)
                 locale (-> parameters :body :locale)
                 format* (or (-> parameters :body :format) "xlsx")]
             {:status 200
              :headers (condp = format*
                         "xlsx" {"Content-Type" (-> utils/content-type :xlsx)
                                 "Content-Disposition" "inline; filename=\"lipas.xlsx\""}
                         "geojson" {"Content-Type" "application/json"
                                    "Content-Disposition" "inline; filename=\"lipas.geojson\""}
                         "csv" {"Content-Type" "text/csv"
                                "Content-Disposition" "inline; filename=\"lipas.csv\""})
              :body
              (ring-io/piped-input-stream
               (fn [out]
                 (condp = format*
                   "xlsx" (core/sports-sites-report-excel search query fields locale out)
                   "geojson" (core/sports-sites-report-geojson search query fields locale out)
                   "csv" (core/sports-sites-report-csv search query fields locale out))))}))}}]

      ["/actions/create-data-model-report"
       {:post
        {:no-doc false
         :parameters {:body {}}
         :handler
         (fn [{:keys [_parameters]}]
           {:status 200
            :headers {"Content-Type" (-> utils/content-type :xlsx)
                      "Content-Disposition" "inline; filename=\"lipas_tietomalli.xlsx\""}
            :body (ring-io/piped-input-stream (fn [out] (core/data-model-report out)))})}}]

      ;; Old simple db version
      ["/actions/create-finance-report"
       {:post
        {:no-doc true
         :parameters
         {:body handler-schema/finance-report-req}
         :handler
         (fn [{:keys [parameters]}]
           (let [params (:body parameters)]
             {:status 200
              :body (core/finance-report db params)}))}}]

      ;; New version that uses ES backend
      ["/actions/query-finance-report"
       {:post
        {:no-doc true
         :parameters
         {:body [:map {:closed false}]}
         :handler
         (fn [{:keys [parameters]}]
           (let [params (:body parameters)]
             {:status 200
              :body (core/query-finance-report search params)}))}}]

      ;; Subsidies
      ["/actions/query-subsidies"
       {:post
        {:no-doc true
         :parameters
         {:body [:map {:closed false}]}
         :handler
         (fn [{:keys [parameters]}]
           (let [params (:body parameters)]
             {:status 200
              :body (core/query-subsidies search params)}))}}]

      ["/actions/calculate-stats"
       {:post
        {:no-doc true
         :parameters {:body handler-schema/calculate-stats-payload}
         :handler
         (fn [{:keys [body-params]}]
           {:status 200
            :body (core/calculate-stats db search body-params)})}}]

      ;; Accessibility
      ["/actions/get-accessibility-statements"
       {:post
        {:no-doc true
         :parameters {:lipas-id int?}
         :handler
         (fn [{:keys [body-params]}]
           (let [lipas-id (-> body-params :lipas-id)]
             {:status 200
              :body (core/get-accessibility-statements lipas-id)}))}}]

      ["/actions/get-accessibility-app-url"
       {:post
        {:no-doc true
         :middleware [mw/token-auth mw/auth]
         :parameters {:lipas-id int?}
         :handler
         (fn [{:keys [body-params identity]}]
           (let [lipas-id (-> body-params :lipas-id)]
             {:status 200
              :body (core/get-accessibility-app-url db identity lipas-id)}))}}]

      ;;; Analysis ;;;

      ;; Search Schools
      ["/actions/search-schools"
       {:post
        {:no-doc true
         :handler
         (fn [{:keys [body-params]}]
           (core/search-schools search body-params))}}]

      ;; Search population
      ["/actions/search-population"
       {:post
        {:no-doc true
         :handler
         (fn [{:keys [body-params]}]
           (core/search-population search body-params))}}]

      ;; Calc distances and travel-times
      ["/actions/calc-distances-and-travel-times"
       {:post
        {:no-doc true
         :handler
         (fn [{:keys [body-params]}]
           {:status 200
            :body (core/calc-distances-and-travel-times search body-params)})}}]

      ;; Create analysis report
      ["/actions/create-analysis-report"
       {:post
        {:no-doc true
         :handler
         (fn [{:keys [body-params]}]
           {:status 200
            :headers {"Content-Type" (-> utils/content-type :xlsx)
                      "Content-Disposition" "inline; filename=\"lipas.xlsx\""}
            :body
            (ring-io/piped-input-stream
             (fn [out]
               (core/create-analysis-report body-params out)))})}}]

      ;; Get newsletter
      ["/actions/get-newsletter"
       {:post
        {:no-doc false
         :handler
         (fn [_]
           {:status 200
            :body (core/get-newsletter mailchimp)})}}]

      ;; Subscribe newsletter
      ["/actions/subscribe-newsletter"
       {:post
        {:no-doc false
         :parameters
         {:body
          {:email users-schema/email-schema}}
         :handler
         (fn [{:keys [body-params]}]
           {:status 200
            :body (core/subscribe-newsletter mailchimp body-params)})}}]

      ;; Calculate diversity indices
      ["/actions/calc-diversity-indices"
       {:post
        {:no-doc true
         :parameters {:body [:map {:closed false}]}
         :handler
         (fn [{:keys [parameters]}]
           (let [body (:body parameters)]
             (if (malli/validate diversity-schema/diversity-indices-req body)
               {:status 200
                :body (core/calc-diversity-indices search body)}
               {:status 400
                :body {:error (malli/explain diversity-schema/diversity-indices-req body)}})))}}]

      ;; Send feedback
      ["/actions/send-feedback"
       {:post
        {:no-doc false
         :parameters
         {:body feedback-schema/feedback-payload}
         :handler
         (fn [{:keys [body-params]}]
           (core/send-feedback! emailer body-params)
           {:status 200
            :body {:status "OK"}})}}]

      ;; Check sports-site name
      ["/actions/check-sports-site-name"
       {:post
        {:no-doc true
         :parameters
         {:body handler-schema/check-sports-site-name-payload}
         :handler
         (fn [{:keys [body-params]}]
           {:status 200
            :body (core/check-sports-site-name search body-params)})}}]

      ["/actions/create-upload-url"
       {:post
        {:no-doc false
         ;; TODO: role, :activity/edit?
         :middleware [mw/token-auth mw/auth]
         :parameters
         {:body handler-schema/create-upload-url-payload}
         :handler
         (fn [{:keys [body-params identity]}]
           {:status 200
            :body (core/presign-upload-url aws (assoc body-params :user identity))})}}]

      ["/actions/upload-utp-image"
       {:post
        {:no-doc false
         ;; TODO: role, :activity/edit?
         :middleware [multipart/multipart-middleware mw/token-auth mw/auth]
         :parameters {:multipart {:file [:map
                                         [:filename :string]
                                         [:content-type :string]
                                         [:tempfile :any]
                                         [:size :int]]}}
         :handler
         (fn [{:keys [parameters multipart-params identity]}]
           (let [params {:lipas-id (get multipart-params "lipas-id")
                         :filename (-> parameters :multipart :file :filename)
                         :data (-> parameters :multipart :file :tempfile)
                         :user identity}]
             {:status 200
              :body (core/upload-utp-image! params)}))}}]

      ["/actions/save-loi"
       {:post
        {:no-doc false
         :require-privilege [{:type-code ::roles/any
                              :city-code ::roles/any
                              :activity ::roles/any}
                             :loi/create-edit]
         :parameters
         {:body loi-schema/loi-document}
         :handler
         (fn [{:keys [body-params identity]}]
           {:status 200
            :body (core/upsert-loi! db search identity body-params)})}}]

      ["/actions/search-lois"
       {:post
        {:no-doc false
         ;; TODO: Tests don't use auth for this endpoint now
                                        ; :require-privilege [{:type-code ::roles/any
                                        ;                      :city-code ::roles/any
                                        ;                      :activity ::roles/any}
                                        ;                     :loi/view]
         :parameters {:body handler-schema/search-lois-payload}
         :handler
         (fn [{:keys [body-params]}]
           {:status 200
            :body (core/search-lois-with-params search body-params)})}}]

      ["/actions/save-help-data"
       {:post
        {:no-doc true
         :require-privilege :help/manage
         :parameters {:body help-schema/HelpData}
         :handler
         (fn [{:keys [body-params]}]
           {:status 200
            :body (core/save-help-data db body-params)})}}]

      ["/actions/get-help-data"
       {:post
        {:no-doc true
         :responses {200 {:body help-schema/HelpData}}
         :handler
         (fn [_]
           {:status 200
            :body (core/get-help-data db)})}}]

      ;; Heatmap analysis
      ["/actions/create-heatmap"
       {:post
        {:no-doc false
         #_#_:require-privilege :analysis-tool/experimental
         :parameters {:body heatmap/HeatmapParams}
         :responses {200 {:body heatmap/CreateHeatmapResponse}}
         :handler
         (fn [req]
           (let [params (:body-params req)
                 data (heatmap/create ctx params)]
             {:status 200
              :body {:data data
                     :metadata {:dimension (:dimension params)
                                :weight-by (:weight-by params)
                                :total-features (count data)}}}))}}]

      ["/actions/get-heatmap-facets"
       {:post
        {:no-doc false
         #_#_:require-privilege :analysis-tool/experimental
         :parameters {:body heatmap/FacetParams}
         :responses {200 {:body heatmap/GetHeatmapFacetsResponse}}
         :handler
         (fn [req]
           (let [params (:body-params req)]
             {:status 200
              :body (heatmap/get-facets ctx params)}))}}]

      (bulk-ops-handler/routes ctx)
      (ptv-handler/routes ctx)
      (jobs-handler/routes ctx)]

     (v1/routes ctx)
     (v2/routes ctx)]

    {:data
     {:coercion reitit.coercion.malli/coercion
      :muuntaja m/instance
      :middleware [;; query-params & form-params
                   params/wrap-params
                   ;; content-negotiation
                   muuntaja/format-negotiate-middleware
                   ;; encoding response body
                   muuntaja/format-response-middleware
                   ;; add cors headers and respond to OPTIONS requests,
                   ;; - before privilege check, so options request is handled first.
                   ;; - before exception handling, so error responses also get cors headers.
                   mw/cors-middleware
                   ;; exception handling
                   exceptions-mw
                   ;; decoding request body
                   muuntaja/format-request-middleware
                   ;; coercing response bodys
                   coercion/coerce-response-middleware
                   ;; coercing request parameters
                   coercion/coerce-request-middleware
                   ;; privilege check based on route-data,
                   ;; also enables token-auth and auth checks
                   ;; per route.
                   mw/privilege-middleware]}})
   (ring/routes
    (swagger-ui/create-swagger-ui-handler {:path "/api/swagger-ui" :url "/api/swagger.json"})
    (ring/create-default-handler))))
