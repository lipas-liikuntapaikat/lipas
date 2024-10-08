(ns lipas.backend.handler
  (:require
   [lipas.backend.middleware :as mw]
   [lipas.roles :as roles]
   [lipas.backend.routes.routes-v1 :as routes-v1]
   [lipas.backend.routes.routes-v2 :as routes-v2]
   [lipas.schema.core]
   [muuntaja.core :as m]
   [reitit.coercion.spec]
   [reitit.ring :as ring]
   [reitit.ring.coercion :as coercion]
   [reitit.ring.middleware.exception :as exception]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.swagger-ui :as swagger-ui]
   [ring.middleware.params :as params]
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
   ::exception/default (exception-handler 500 :internal-server-error :print-stack)})

(def exceptions-mw
  (exception/create-exception-middleware
   (merge
    exception/default-handlers
    exception-handlers)))

(defn create-app
  [{:keys [db emailer search mailchimp aws]}]
  (ring/ring-handler
   (ring/router
    (conj
     (routes-v2/routes db emailer search mailchimp aws)
     routes-v1/routes)
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