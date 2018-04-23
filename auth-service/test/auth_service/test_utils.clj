(ns auth-service.test-utils
  (:require [cheshire.core :as ch]
            [ring.mock.request :as mock]
            [auth-service.handler :refer [app]]
            [auth-service.query-defs :as query]
            [buddy.core.codecs :as codecs]
            [buddy.core.codecs.base64 :as b64]))

(def str->base64 (comp codecs/bytes->str b64/encode))

(defn parse-body [body]
  (ch/parse-string (slurp body) true))

(defn basic-auth-header
  [request original]
  (mock/header request "Authorization" (str "Basic " (str->base64 original))))

(defn token-auth-header
  [request token]
  (mock/header request "Authorization" (str "Token " token)))

(defn get-user-token [username-and-password]
  (let [initial-response (app (-> (mock/request :get "/api/v1/auth")
                                  (basic-auth-header username-and-password)))
        initial-body     (parse-body (:body initial-response))]
    (:token initial-body)))

(defn get-token-auth-header-for-user [request username-and-password]
  (token-auth-header request (get-user-token username-and-password)))

(defn get-permissions-for-user [id]
  (:permissions (query/get-permissions-for-userid {:userid id})))

(defn get-id-for-user [username]
  (:id (query/get-registered-user-by-username {:username username})))

(defn add-permission-for-username [username permission]
  (let [user-id (:id (query/get-registered-user-by-username {:username username}))]
    (query/insert-permission-for-user! {:userid user-id :permission permission})))

(defn add-users []
  (let [user-1 {:email "j@man.com" :username "JarrodCTaylor" :password "pass"}
        user-2 {:email "e@man.com" :username "Everyman"      :password "pass"}]
    (app (-> (mock/request :post "/api/v1/user")
             (mock/content-type "application/json")
             (mock/body (ch/generate-string user-1))))
    (app (-> (mock/request :post "/api/v1/user")
             (mock/content-type "application/json")
             (mock/body (ch/generate-string user-2))))))
