(ns lipas.backend.handler
  (:require [compojure.api.sweet :refer [api context GET POST OPTIONS]]
            [lipas.backend.middleware :as mw]
            [lipas.backend.core :as core]
            [ring.util.http-response :refer [ok created conflict]]
            [ring.util.response :refer [resource-response]]))

(defn exception-handler [resp-fn type]
  (fn [^Exception e data request]
    (resp-fn {:message (.getMessage e)
              :type type})))

(def exception-handlers
  {:username-conflict (exception-handler conflict :username-conflict)
   :email-conflict    (exception-handler conflict :email-conflict)})

(defn create-app [{:keys [db]}]
  (api
    {:exceptions
     {:handlers exception-handlers}}

    (OPTIONS "/api/*" []
      :middleware [mw/cors]
      (ok  {}))

    (GET "/api/" [] (resource-response "index.html" {:root "public"}))

    (GET "/api/health" [] (ok {:status "OK"}))

    (POST "/api/actions/register" req
      :middleware [mw/cors]
      (let [_ (core/add-user db (:body-params req))]
        (created "/fixme" {:status "OK"})))

    (POST "/api/actions/login" req
      :middleware [(mw/basic-auth db) mw/cors mw/auth]
      (ok (:identity req)))))
