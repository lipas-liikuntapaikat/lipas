(ns lipas.backend.handler
  (:require [compojure.api.sweet :refer [api context GET POST OPTIONS]]
            [lipas.backend.middleware :as mw]
            [lipas.backend.core :as core]
            [lipas.backend.auth :as auth]
            [ring.util.http-response :refer [ok created conflict forbidden]]
            [ring.util.response :refer [resource-response]]))

(defn exception-handler [resp-fn type]
  (fn [^Exception e data request]
    (resp-fn {:message (.getMessage e)
              :type type})))

(def exception-handlers
  {:username-conflict (exception-handler conflict :username-conflict)
   :email-conflict    (exception-handler conflict :email-conflict)
   :no-permission     (exception-handler forbidden :no-permission)})

(defn create-app [{:keys [db]}]
  (api
    {:exceptions
     {:handlers exception-handlers}}

    (OPTIONS "/api/*" []
      :middleware [mw/cors]
      (ok  {}))

    (GET "/api/" [] (resource-response "index.html" {:root "public"}))

    (GET "/api/health" [] (ok {:status "OK"}))

    (POST "/api/sports-sites" req
      :middleware [mw/token-auth mw/cors mw/auth]
      (let [user        (:identity req)
            sports-site (:body-params req)]
        (created "/fixme" (core/upsert-sports-site! db user sports-site))))

    (POST "/api/actions/register" req
      :middleware [mw/cors]
      (let [_ (core/add-user! db (:body-params req))]
        (created "/fixme" {:status "OK"})))

    (POST "/api/actions/login" req
      :middleware [(mw/basic-auth db) mw/cors mw/auth]
      (ok (:identity req)))

    (GET "/api/actions/refresh-login" req
      :middleware [mw/token-auth mw/cors mw/auth]
      (ok {:token (auth/create-token (:identity req))}))))
