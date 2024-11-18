(ns lipas.backend.handler
  (:require [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [lipas.backend.api.v2 :as v2]
            [lipas.backend.core :as core]
            [lipas.backend.jwt :as jwt]
            [lipas.backend.middleware :as mw]
            [lipas.backend.ptv.handler :as ptv-handler]
            [lipas.roles :as roles]
            [lipas.schema.core]
            [lipas.utils :as utils]
            [muuntaja.core :as m]
            [reitit.coercion.spec]
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
  (:require [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [lipas.backend.core :as core]
            [lipas.backend.jwt :as jwt]
            [lipas.backend.legacy.api :as legacy.api]
            [lipas.backend.middleware :as mw]
            [lipas.roles :as roles]
            [lipas.schema.core]
            [lipas.utils :as utils]
            [muuntaja.core :as m]
            [reitit.coercion.spec]
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
          :body   {:message (.getMessage e)
                   :type    type}}
         mw/add-cors-headers))))

(def exception-handlers
  {:username-conflict  (exception-handler 409 :username-conflict)
   :email-conflict     (exception-handler 409 :email-conflict)
   :no-permission      (exception-handler 403 :no-permission)
   :user-not-found     (exception-handler 404 :user-not-found)
   :email-not-found    (exception-handler 404 :email-not-found)
   :reminder-not-found (exception-handler 404 :reminder-not-found)
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
   (let [handler (:reitit.coercion/request-coercion exception/default-handlers)]
     (fn [e x]
       (log/errorf e "Request coercion error")
       (handler e x)))})

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

     ["/api"
      {:middleware [mw/cors]
       :no-doc     true
       :options
       {:handler
        (fn [_]
          {:status 200
           :body   {:status "OK"}})}}

      ["/swagger.json"
       {:get
        {:no-doc  true
         :swagger {:info {:title "Lipas-API v2"}
                   :securityDefinitions
                   {:token-auth
                    {:type "apiKey"
                     :in   "header"
                     :name "Authorization"}}}
         :handler (swagger/create-swagger-handler)}}]

      ["/health"
       {:get
        {:no-doc true
         :handler
         (fn [_]
           {:status 200
            :body   {:status "OK"}})}}]

      ["/sports-sites"
       {:post
        {:no-doc     false
         :middleware [mw/token-auth mw/auth]
         ;; NOTE: privilege checked in the core code
         :responses  {201 {:body :lipas/sports-site}
                      400 {:body map?}}
         :parameters
         {:query (s/keys :opt-un [:lipas.api/draft])
          :body  :lipas/new-or-existing-sports-site}
         :handler
         (fn [{:keys [body-params identity] :as req}]
           (let [spec   :lipas/new-or-existing-sports-site
                 draft? (-> req :parameters :query :draft utils/->bool)
                 valid? (s/valid? spec body-params)]
             (if valid?
               {:status 201
                :body   (core/save-sports-site! db search ptv identity body-params draft?)}
               {:status 400
                :body   (s/explain-data spec body-params)})))}}]

      ["/sports-sites/:lipas-id"
       {:get
        {:no-doc     false
         :parameters {:path  {:lipas-id int?}
                      :query :lipas.api.get-sports-site/query-params}
         :responses  {200 {:body :lipas/sports-site}
                      404 {:body map?}}
         :handler
         (fn [req]
           (let [lipas-id (-> req :parameters :path :lipas-id)
                 locale   (or (-> req :parameters :query :lang keyword)
                              :none)]
             (if-let [res (core/get-sports-site db lipas-id locale)]
               {:status 200 :body res}
               {:status 404 :body {:message "Not found"}})))}}]

      ["/sports-sites/history/:lipas-id"
       {:get
        {:no-doc     false
         :parameters {:path {:lipas-id int?}}
         :responses  {200 {:body (s/coll-of :lipas/sports-site)}}
         :handler
         (fn [{{{:keys [lipas-id]} :path} :parameters}]
           {:status 200
            :body   (core/get-sports-site-history db lipas-id)})}}]

      ["/sports-sites/type/:type-code"
       {:get
        {:no-doc    false
         :responses {200 {:body (s/coll-of map?)}}
         :parameters
         {:path  {:type-code :lipas.sports-site.type/type-code}
          :query :lipas.api.get-sports-sites-by-type-code/query-params}
         :handler
         (fn [{:keys [parameters]}]
           (let [type-code (-> parameters :path :type-code)
                 #_#_revs  (or (-> parameters :query :revs)
                               "latest")
                 locale    (or (-> parameters :query :lang keyword)
                               :none)]
             {:status 200
              :body   (core/get-sports-sites-by-type-code db
                                                          type-code
                                                          {#_#_:revs revs
                                                           :locale   locale})}))}}]

      ["/lois"
       {:get
        {:no-doc     false
         :responses  {200 {:body :lipas.loi/documents}}
         :parameters {}
         :handler
         (fn []
           (let [query {:size 10000 :query {:match_all {}}}]
             {:status 200
              :body   (core/search-lois search query)}))}}]

      ["/lois/:loi-id"
       {:get
        {:no-doc     false
         :responses  {200 {:body :lipas.loi/document}}
         :parameters {:path {:loi-id :lipas.loi/id}}
         :handler
         (fn [{:keys [parameters]}]
           {:status 200
            :body   (core/get-loi search (get-in parameters [:path :loi-id]))})}}]

      ["/lois/type/:loi-type"
       {:get
        {:no-doc    false
         :responses {200 {:body :lipas.loi/documents}}
         :parameters
         {:path  {:loi-type :lipas.loi/loi-type}
          :query :lipas.api.get-sports-sites-by-type-code/query-params}
         :handler
         (fn [{:keys [parameters]}]
           (let [loi-type (-> parameters :path :loi-type)
                 query    {:size 10000 :query {:term {:loi-type.keyword loi-type}}}]
             {:status 200
              :body   (core/search-lois search query)}))}}]

      ["/lois/category/:loi-category"
       {:get
        {:no-doc    false
         :responses {200 {:body :lipas.loi/documents}}
         :parameters
         {:path  {:loi-category :lipas.loi/loi-category}
          :query :lipas.api.get-sports-sites-by-type-code/query-params}
         :handler
         (fn [{:keys [parameters]}]
           (let [loi-category (-> parameters :path :loi-category)
                 query        {:size 10000 :query {:term {:loi-category.keyword loi-category}}}]
             {:status 200
              :body   (core/search-lois search query)}))}}]

      ["/lois/status/:status"
       {:get
        {:no-doc    false
         :responses {200 {:body :lipas.loi/documents}}
         :parameters
         {:path  {:status :lipas.loi/status}
          :query :lipas.api.get-sports-sites-by-type-code/query-params}
         :handler
         (fn [{:keys [parameters]}]
           (let [loi-status (-> parameters :path :status)
                 query      {:size 10000 :query {:term {:status.keyword loi-status}}}]
             {:status 200
              :body   (core/search-lois search query)}))}}]

      ["/users"
       {:get
        {:no-doc     true
         :require-privilege :users/manage
         :handler
         (fn [_]
           {:status 200
            :body   (core/get-users db)})}}]

      ["/actions/gdpr-remove-user"
       {:post
        {:no-doc     true
         :require-privilege :users/manage
         :handler
         (fn [{:keys [body-params]}]
           (let [{:keys [id] :as user} (core/get-user! db (or (:id body-params)
                                                              (:username body-params)
                                                              (:email body-params)))]
             (core/gdpr-remove-user! db user)
             {:status 200
              :body   (core/get-user db (str id))}))}}]

      ["/actions/search"
       {:post
        {:no-doc false
         :handler
         (fn [{:keys [body-params]}]
           (core/search search body-params))}}]

      ["/actions/find-fields"
       {:post
        {:no-doc     false
         :parameters {:body :lipas.api.find-fields/payload}
         :responses  {200 {:body (s/coll-of :lipas/sports-site)}}
         :handler
         (fn [{:keys [body-params]}]
           {:status 200
            :body   (core/search-fields search body-params)})}}]

      ["/actions/register"
       {:post
        {:no-doc true
         :handler
         (fn [req]
           (let [user (-> req
                          :body-params
                          (dissoc :permissions))
                 _    (core/register! db emailer user)]
             {:status 201
              :body   {:status "OK"}}))}}]

      ["/actions/login"
       {:post
        {:no-doc     true
         :middleware [(mw/basic-auth db) mw/auth]
         :handler
         (fn [{:keys [identity]}]
           (core/login! db identity)
           {:status 200 :body identity})}}]

      ["/actions/refresh-login"
       {:get
        {:no-doc     true
         :middleware [mw/token-auth mw/auth]
         :handler
         (fn [{:keys [identity]}]
           (let [user (core/get-user! db (-> identity :id))]
             {:status 200
              :body   (merge (dissoc user :password)
                             {:token (jwt/create-token user)})}))}}]

      ["/actions/request-password-reset"
       {:post
        {:no-doc     true
         :parameters {:body {:email string?}}
         :handler
         (fn [{:keys [body-params]}]
           (let [_ (core/send-password-reset-link! db emailer body-params)]
             {:status 200
              :body   {:status "OK"}}))}}]

      ["/actions/reset-password"
       {:post
        {:no-doc     true
         :middleware [mw/token-auth mw/auth]
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
        {:no-doc     true
         :require-privilege :users/manage
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

      ["/actions/update-user-status"
       {:post
        {:no-doc     true
         :require-privilege :users/manage
         :parameters
         {:body
          {:id     string?
           :status :lipas.user/status}}
         :handler
         (fn [req]
           {:status 200
            :body   (core/update-user-status! db (-> req :parameters :body))})}}]

      ["/actions/update-user-data"
       {:post
        {:no-doc     true
         :middleware [mw/token-auth mw/auth]
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
        {:no-doc true
         :parameters
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
        {:no-doc     true
         :require-privilege :users/manage
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
        {:no-doc     true
         :middleware [mw/token-auth mw/auth]
         :parameters
         {:body :lipas/new-reminder}
         :handler
         (fn [{:keys [identity parameters]}]
           (let [reminder (:body parameters)]
             {:status 200
              :body   (core/add-reminder! db identity reminder)}))}}]

      ["/actions/update-reminder-status"
       {:post
        {:no-doc     true
         :middleware [mw/token-auth mw/auth]
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
        {:no-doc     true
         :middleware [mw/token-auth mw/auth]
         :handler
         (fn [{:keys [identity]}]
           {:status 200
            :body   (core/get-users-pending-reminders! db identity)})}}]

      ["/actions/create-energy-report"
       {:post
        {:no-doc true
         :parameters
         {:body :lipas.api.energy-report/req}
         :handler
         (fn [{:keys [parameters]}]
           (let [type-code (-> parameters :body :type-code)
                 year      (-> parameters :body :year)]
             {:status 200
              :body   (core/energy-report db type-code year)}))}}]

      ["/actions/create-sports-sites-report"
       {:post
        {:no-doc false
         :parameters
         {:body :lipas.api.sports-site-report/req}
         :handler
         (fn [{:keys [parameters]}]
           (let [query   (-> parameters :body :search-query)
                 fields  (-> parameters :body :fields)
                 locale  (-> parameters :body :locale)
                 format* (or (-> parameters :body :format) "xlsx")]
             {:status  200
              :headers (condp = format*
                         "xlsx"    {"Content-Type"        (-> utils/content-type :xlsx)
                                    "Content-Disposition" "inline; filename=\"lipas.xlsx\""}
                         "geojson" {"Content-Type"        "application/json"
                                    "Content-Disposition" "inline; filename=\"lipas.geojson\""}
                         "csv"     {"Content-Type"        "text/csv"
                                    "Content-Disposition" "inline; filename=\"lipas.csv\""})
              :body
              (ring-io/piped-input-stream
               (fn [out]
                 (condp = format*
                   "xlsx"    (core/sports-sites-report-excel search query fields locale out)
                   "geojson" (core/sports-sites-report-geojson search query fields locale out)
                   "csv"     (core/sports-sites-report-csv search query fields locale out))))}))}}]

      ;; Old simple db version
      ["/actions/create-finance-report"
       {:post
        {:no-doc true
         :parameters
         {:body :lipas.api.finance-report/req}
         :handler
         (fn [{:keys [parameters]}]
           (let [params (:body parameters)]
             {:status 200
              :body   (core/finance-report db params)}))}}]

      ;; New version that uses ES backend
      ["/actions/query-finance-report"
       {:post
        {:no-doc true
         :parameters
         {:body map?}
         :handler
         (fn [{:keys [parameters]}]
           (let [params (:body parameters)]
             {:status 200
              :body   (core/query-finance-report search params)}))}}]

      ;; Subsidies
      ["/actions/query-subsidies"
       {:post
        {:no-doc true
         :parameters
         {:body map?}
         :handler
         (fn [{:keys [parameters]}]
           (let [params (:body parameters)]
             {:status 200
              :body   (core/query-subsidies search params)}))}}]

      ["/actions/calculate-stats"
       {:post
        {:no-doc     true
         :parameters {:body :lipas.api.calculate-stats/payload}
         :handler
         (fn [{:keys [body-params]}]
           {:status 200
            :body   (core/calculate-stats db search body-params)})}}]

      ;; Accessibility
      ["/actions/get-accessibility-statements"
       {:post
        {:no-doc     true
         :parameters {:lipas-id int?}
         :handler
         (fn [{:keys [body-params]}]
           (let [lipas-id (-> body-params :lipas-id)]
             {:status 200
              :body   (core/get-accessibility-statements lipas-id)}))}}]

      ["/actions/get-accessibility-app-url"
       {:post
        {:no-doc     true
         :middleware [mw/token-auth mw/auth]
         :parameters {:lipas-id int?}
         :handler
         (fn [{:keys [body-params identity]}]
           (let [lipas-id (-> body-params :lipas-id)]
             {:status 200
              :body   (core/get-accessibility-app-url db identity lipas-id)}))}}]

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
            :body   (core/calc-distances-and-travel-times search body-params)})}}]

      ;; Create analysis report
      ["/actions/create-analysis-report"
       {:post
        {:no-doc true
         :handler
         (fn [{:keys [body-params]}]
           {:status  200
            :headers {"Content-Type"        (-> utils/content-type :xlsx)
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
            :body   (core/get-newsletter mailchimp)})}}]

      ;; Subscribe newsletter
      ["/actions/subscribe-newsletter"
       {:post
        {:no-doc false
         :parameters
         {:body
          {:email :lipas/email}}
         :handler
         (fn [{:keys [body-params]}]
           {:status 200
            :body   (core/subscribe-newsletter mailchimp body-params)})}}]

      ;; Calculate diversity indices
      ["/actions/calc-diversity-indices"
       {:post
        {:no-doc     true
         :parameters {:body map?} ;; TODO proper spec
         :handler
         (fn [{:keys [parameters]}]
           (let [body (:body parameters)]
             (if (s/valid? :lipas.api.diversity-indices/req body)
               {:status 200
                :body   (core/calc-diversity-indices search body)}
               {:status 400
                :body   {:error (s/explain-data :lipas.api.diversity-indices/req body)}})))}}]

      ;; Send feedback
      ["/actions/send-feedback"
       {:post
        {:no-doc false
         :parameters
         {:body :lipas.feedback/payload}
         :handler
         (fn [{:keys [body-params]}]
           (core/send-feedback! emailer body-params)
           {:status 200
            :body   {:status "OK"}})}}]

      ;; Check sports-site name
      ["/actions/check-sports-site-name"
       {:post
        {:no-doc true
         :parameters
         {:body :lipas.api.check-sports-site-name/payload}
         :handler
         (fn [{:keys [body-params]}]
           {:status 200
            :body   (core/check-sports-site-name search body-params)})}}]

      ["/actions/create-upload-url"
       {:post
        {:no-doc     false
         ;; TODO: role, :activity/edit?
         :middleware [mw/token-auth mw/auth]
         :parameters
         {:body :lipas.api.create-upload-url/payload}
         :handler
         (fn [{:keys [body-params identity]}]
           {:status 200
            :body   (core/presign-upload-url aws (assoc body-params :user identity))})}}]

      ["/actions/upload-utp-image"
       {:post
        {:no-doc     false
         ;; TODO: role, :activity/edit?
         :middleware [multipart/multipart-middleware mw/token-auth mw/auth]
         :parameters {:multipart {:file multipart/temp-file-part}}
         :handler
         (fn [{:keys [parameters multipart-params identity]}]
           (let [params {:lipas-id (get multipart-params "lipas-id")
                         :filename (-> parameters :multipart :file :filename)
                         :data     (-> parameters :multipart :file :tempfile)
                         :user     identity}]
             {:status 200
              :body   (core/upload-utp-image! params)}))}}]

      ["/actions/save-loi"
       {:post
        {:no-doc     false
         :require-privilege [{:type-code ::roles/any
                              :city-code ::roles/any
                              :activity ::roles/any}
                             :loi/create-edit]
         :parameters
         {:body :lipas.loi/document}
         :handler
         (fn [{:keys [body-params identity]}]
           {:status 200
            :body   (core/upsert-loi! db search identity body-params)})}}]

      ["/actions/search-lois"
       {:post
        {:no-doc         false
         ;; TODO: Tests don't use auth for this endpoint now
         ; :require-privilege [{:type-code ::roles/any
         ;                      :city-code ::roles/any
         ;                      :activity ::roles/any}
         ;                     :loi/view]
         :parameters     {:body :lipas.api.search-lois/payload}
         :handler
         (fn [{:keys [body-params]}]
           {:status 200
            :body   (core/search-lois-with-params search body-params)})}}]

      (ptv-handler/routes ctx)]
     (v2/routes ctx)]
      ;; PTV
      ["/actions/get-ptv-integration-candidates"
       {:post
        {:no-doc     false
         :require-role :ptv/manage
         :parameters {:body map?}
         :handler
         (fn [{:keys [body-params]}]
           {:status 200
            :body   (core/get-ptv-integration-candidates search body-params)})}}]

      ["/actions/generate-ptv-descriptions"
       {:post
        {:no-doc     false
         :require-role :ptv/manage
         :parameters {:body map?}
         :handler
         (fn [{:keys [body-params]}]
           {:status 200
            :body   (core/generate-ptv-descriptions search body-params)})}}]

      ["/actions/generate-ptv-service-descriptions"
       {:post
        {:no-doc     false
         :require-role :ptv/manage
         :parameters {:body map?}
         :handler
         (fn [{:keys [body-params]}]
           {:status 200
            :body   (core/generate-ptv-service-descriptions search body-params)})}}]

      ["/actions/save-ptv-service"
       {:post
        {:no-doc     false
         :require-role :ptv/manage
         :parameters {:body map?}
         :handler
         (fn [{:keys [body-params]}]
           {:status 200
            :body   (core/upsert-ptv-service! body-params)})}}]

      ["/actions/fetch-ptv-services"
       {:post
        {:no-doc     false
         :require-role :ptv/manage
         :parameters {:body map?}
         :handler
         (fn [{:keys [body-params]}]
           {:status 200
            :body   (core/fetch-ptv-services body-params)})}}]

      ["/actions/save-ptv-service-location"
       {:post
        {:no-doc     false
         :require-role :ptv/manage
         :parameters {:body map?}
         :handler
         (fn [{:keys [body-params identity]}]
           {:status 200
            :body   (core/upsert-ptv-service-location! db search identity body-params)})}}]

      ["/actions/save-ptv-meta"
       {:post
        {:no-doc     false
         :require-role :ptv/manage
         :parameters {:body map?}
         :handler
         (fn [{:keys [body-params identity]}]
           {:status 200
            :body   (core/save-ptv-integration-definitions db search identity body-params)})}}]]

     ;; legacy routes
     ["/v1/api"
      ["/swagger.json"
       {:get
        {:no-doc  true
         :swagger {:id ::legacy
                   :info {:title "Lipas-API (legacy) v1"}}
         :handler (swagger/create-swagger-handler)}}]
      ["/sports-place-types"
       {:swagger {:id ::legacy}
        :parameters {:query (s/keys :opt-un [:lipas.api/lang])}
        :get
        {:handler
         (fn [req]
           (let [locale  (or (-> req :parameters :query :lang keyword) :en)]
             {:status     200
              :body       (legacy.api/sports-place-types locale)}))}}]
      ["/sports-place-types/:type-code"
       {:swagger {:id ::legacy}
        :parameters {:query (s/keys :opt-un [:lipas.api/lang])
                     :path {:type-code int?}}
        :get
        {:handler
         (fn [req]
           (let [locale  (or (-> req :parameters :query :lang keyword) :en)
                 type-code (-> req :parameters :path :type-code)]
             {:status     200
              :body       (legacy.api/sports-place-by-type-code locale type-code)}))}}]
      ["/categories"
       {:swagger {:id ::legacy}
        :parameters {:query (s/keys :opt-un [:lipas.api/lang])}
        :get
        {:handler
         (fn [req]
           (let [locale  (or (-> req :parameters :query :lang keyword) :en)]
             {:status     200
              :body       (legacy.api/categories locale)}))}}]]]

    {:data
     {:coercion   reitit.coercion.spec/coercion
      :muuntaja   m/instance
      :middleware [
                   ;; query-params & form-params
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
                   coercion/coerce-request-middleware
                   ;; privilege check based on route-data,
                   ;; also enables token-auth and auth checks
                   ;; per route.
                   mw/privilege-middleware]}})
   (ring/routes
    (swagger-ui/create-swagger-ui-handler {:path "/api/swagger-ui" :url "/api/swagger.json"})
    (swagger-ui/create-swagger-ui-handler {:path "/v1/api/swagger-ui" :url "/v1/api/swagger.json"})
    (ring/create-default-handler))))
