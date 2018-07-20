(ns lipas.backend.handler
  (:require [compojure.api.sweet :refer [api context GET POST OPTIONS]]
            [lipas.backend.auth :as auth]
            [lipas.backend.core :as core]
            [lipas.backend.middleware :as mw]
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
    {:coercion :spec
     :exceptions
     {:handlers exception-handlers}}

    (OPTIONS "/api/*" []
      :middleware [mw/cors]
      (ok  {}))

    (GET "/api/" [] (resource-response "index.html" {:root "public"}))

    (GET "/api/health" [] (ok {:status "OK"}))

    ;;; Sports-sites ;;;

    (POST "/api/sports-sites" req
      :middleware [mw/token-auth mw/cors mw/auth]
      (let [user        (:identity req)
            sports-site (:body-params req)]
        (created "/fixme" (core/upsert-sports-site! db user sports-site))))

    (GET "/api/sports-sites/:lipas-id/history" req
      :path-params [lipas-id :- int?]
      :middleware [mw/cors]
      (ok (core/get-sports-site-history db lipas-id)))

    (GET "/api/sports-sites/type/:type-code" req
      :path-params [type-code :- int?]
      :middleware [mw/cors]
      (ok (core/get-sports-sites-by-type-code db type-code)))

    ;;; User ;;;

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
