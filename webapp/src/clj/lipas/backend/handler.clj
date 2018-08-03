(ns lipas.backend.handler
  (:require [compojure.api.sweet :refer [api context GET POST OPTIONS undocumented]]
            [lipas.backend.jwt :as jwt]
            [lipas.backend.core :as core]
            [lipas.backend.middleware :as mw]
            [compojure.route :as route]
            [ring.util.http-response :as resp]))

(defn exception-handler [resp-fn type]
  (fn [^Exception e data request]
    (let [payload {:message (.getMessage e) :type type}]
      (-> payload
          resp-fn
          mw/add-cors-headers))))

(def exception-handlers
  {:username-conflict (exception-handler resp/conflict :username-conflict)
   :email-conflict    (exception-handler resp/conflict :email-conflict)
   :no-permission     (exception-handler resp/forbidden :no-permission)
   :user-not-found    (exception-handler resp/not-found :user-not-found)
   :email-not-found   (exception-handler resp/not-found :email-not-found)})

(defn create-app [{:keys [db]}]
  (api
    {:coercion :spec
     :exceptions
     {:handlers exception-handlers}}

    (context "/api" []
      :middleware [mw/cors]

      (OPTIONS "/*" []
        (resp/ok))

      (GET "/health" [] (resp/ok {:status "OK"}))

      ;;; Sports-sites ;;;

      (POST "/sports-sites" req
        :middleware [mw/token-auth mw/auth]
        (let [user        (:identity req)
              sports-site (:body-params req)]
          (resp/created "/fixme" (core/upsert-sports-site! db user sports-site))))

      (GET "/sports-sites/:lipas-id/history" req
        :path-params [lipas-id :- int?]
        (resp/ok (core/get-sports-site-history db lipas-id)))

      (GET "/sports-sites/type/:type-code" req
        :path-params [type-code :- int?]
        (resp/ok (core/get-sports-sites-by-type-code db type-code)))

      ;;; User ;;;

      (POST "/actions/register" req
        (let [_ (core/add-user! db (:body-params req))]
          (resp/created "/fixme" {:status "OK"})))

      (POST "/actions/login" req
        :middleware [(mw/basic-auth db) mw/auth]
        (resp/ok (:identity req)))

      (GET "/actions/refresh-login" req
        :middleware [mw/token-auth mw/auth]
        (resp/ok (merge
                  (:identity req)
                  {:token (jwt/create-token (:identity req))})))

      (POST "/actions/request-password-reset" req
        :body-params [email :- string?]
        (let [_ (core/send-password-reset-link! db (:body-params req))]
          (resp/ok {:status "OK"})))

      (POST "/actions/reset-password" req
        :middleware [mw/token-auth mw/auth]
        :body-params [password :- string?]
        (let [_ (core/reset-password! db (:identity req) password)]
          (resp/ok {:status "OK"}))))

    (undocumented
     (route/resources "/")
     (route/not-found "404 not found"))))
