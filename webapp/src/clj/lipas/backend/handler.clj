(ns lipas.backend.handler
  (:require [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [lipas.backend.core :as core]
            [lipas.backend.jwt :as jwt]
            [lipas.backend.middleware :as mw]
            [muuntaja.core :as m]
            [reitit.coercion.spec]
            [reitit.ring :as ring]
            [reitit.ring.coercion :as coercion]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [ring.middleware.params :as params]))

(s/def ::revs #{"latest" "yearly"})
(s/def ::query (s/keys :opt-un [::revs]))

(defn exception-handler [status type]
  (fn [^Exception e request]
    (-> {:status status
         :body   {:message (.getMessage e)
                  :type    type}}
        mw/add-cors-headers)))

(def exception-handlers
  {:username-conflict (exception-handler 409 :username-conflict)
   :email-conflict    (exception-handler 409 :email-conflict)
   :no-permission     (exception-handler 403 :no-permission)
   :user-not-found    (exception-handler 404 :user-not-found)
   :email-not-found   (exception-handler 404 :email-not-found)})

(def exceptions-mw
  (exception/create-exception-middleware
   (merge
    exception/default-handlers
    exception-handlers
    ;; Prints all stack traces
    ;; {::exception/wrap (fn [handler e request]
    ;;                     (.printStackTrace e)
    ;;                     (handler e request))}
    )))

(defn create-app [{:keys [db emailer]}]
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
      {:options
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
        {:middleware [mw/token-auth mw/auth]
         :handler
         (fn [{:keys [body-params identity]}]
           {:status 201
            :body   (core/upsert-sports-site! db identity body-params)})}}]

      ["/sports-sites/history/:lipas-id"
       {:get
        {:parameters {:path {:lipas-id int?}}
         :handler
         (fn [{{{:keys [lipas-id]} :path} :parameters}]
           {:status 200
            :body   (core/get-sports-site-history db lipas-id)})}}]

      ["/sports-sites/type/:type-code"
       {:get
        {:parameters {:path  {:type-code int?}
                      :query ::query}
         :handler
         (fn [{:keys [parameters]}]
           (let [type-code (-> parameters :path :type-code)
                 revs      (or (-> parameters :query :revs)
                               "latest")]
             {:status 200
              :body   (core/get-sports-sites-by-type-code db type-code {:revs revs})}))}}]

      ["/actions/register"
       {:post
        {:handler
         (fn [req]
           (let [user (-> req :body-params)
                 _    (core/add-user! db (-> user
                                             (dissoc :permissions)))]
             {:status 201
              :body   {:status "OK"}}))}}]

      ["/actions/login"
       {:post
        {:middleware [(mw/basic-auth db) mw/auth]
         :handler
         (fn [{:keys [identity]}]
           {:status 200
            :body   identity})}}]

      ["/actions/refresh-login"
       {:get
        {:middleware [mw/token-auth mw/auth]
         :handler
         (fn [{:keys [identity]}]
           {:status 200
            :body   (merge identity
                           {:token (jwt/create-token identity)})})}}]

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
              :body   {:status "OK"}}))}}]]]

    {:data {:coercion   reitit.coercion.spec/coercion
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
