(ns lipas.backend.handler-test
  (:require [cheshire.core :as j]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.test :refer [deftest testing is]]
            [lipas.backend.core :as core]
            [lipas.backend.auth :as auth]
            [lipas.backend.system :refer [start-system!]]
            [lipas.schema.core]
            [ring.mock.request :as mock])
  (:import java.util.Base64))

(def <-json #(j/parse-string (slurp %) true))
(def ->json j/generate-string)

(defn ->base64
  "Encodes a string as base64."
  [s]
  (.encodeToString (Base64/getEncoder) (.getBytes s)))

(defn auth-header
  "Adds Authorization header to the request
  with base64 encoded \"Basic user:pass\" value."
  [req user passwd]
  (mock/header req "Authorization" (str "Basic " (->base64 (str user ":" passwd)))))

(defn token-header
  [req token]
  (mock/header req "Authorization" (str "Token " token)))

(def system (start-system!))
(def db (:db system))
(def app (:app system))

(comment (gen/generate (s/gen :lipas/email)))

(defn gen-user
  ([]
   (gen-user {:db? false}))
  ([{:keys [db?]}]
   (let [user (gen/generate (s/gen :lipas/user))]
     (if db?
       (do
         (core/add-user! db user)
         (assoc user :id (:id (core/get-user db (:email user)))))
       user))))

(deftest register-user-test
  (let [user (gen-user)
        resp (app (-> (mock/request :post "/api/actions/register")
                      (mock/content-type "application/json")
                      (mock/body (->json user))))]
    (is (= 201 (:status resp)))))

(deftest register-user-conflict-test
  (let [user (gen-user {:db? true})
        resp (app (-> (mock/request :post "/api/actions/register")
                      (mock/content-type "application/json")
                      (mock/body (->json user))))
        body (<-json (:body resp))]
    (is (= 409 (:status resp)))
    (is (= "username-conflict" (:type body)))))

(deftest login-failure-test
  (let [resp (app (-> (mock/request :post "/api/actions/login")
                      (mock/content-type "application/json")
                      (auth-header "this" "fails")))]
    (is (= (:status resp) 401))))

(deftest login-test
  (let [user (gen-user {:db? true})
        resp (app (-> (mock/request :post "/api/actions/login")
                      (mock/content-type "application/json")
                      (auth-header (:username user) (:password user))))
        body (<-json (:body resp))]
    (is (= 200 (:status resp)))
    (is (= (:email user) (:email body)))))

(deftest upsert-sports-site-draft-test
  (let [user  (gen-user {:db? true})
        token (auth/create-token user)
        site  (-> (gen/generate (s/gen :lipas/sports-site))
                  (assoc :status "draft")
                  (dissoc :lipas-id))
        resp  (app (-> (mock/request :post "/api/sports-sites")
                       (mock/content-type "application/json")
                       (mock/body (->json site))
                       (token-header token)))]
    (is (= 201 (:status resp)))))

(deftest upsert-sports-site-no-permissions-test
  (let [user  (gen-user)
        token (auth/create-token user)
        _     (as-> user $
                (dissoc $ :permissions)
                (core/add-user! db $))
        site  (-> (gen/generate (s/gen :lipas/sports-site))
                  (assoc :status "active"))
        resp  (app (-> (mock/request :post "/api/sports-sites")
                       (mock/content-type "application/json")
                       (mock/body (->json site))
                       (token-header token)))]
    (is (= 403 (:status resp)))))

(deftest get-sports-sites-by-type-code-test
  (let [user (gen-user {:db? true})
        site (-> (gen/generate (s/gen :lipas/sports-site))
                 (assoc-in [:type :type-code] 3110)
                 (assoc :status "draft"))
        _    (core/upsert-sports-site! db user site)
        resp (app (-> (mock/request :get "/api/sports-sites/type/3110")
                      (mock/content-type "application/json")))
        body (<-json (:body resp))]
    (is (= 200 (:status resp)))
    (is (s/valid? :lipas/sports-sites body))))

(deftest get-sports-site-history-test
  (let [user     (gen-user {:db? true})
        rev1     (-> (gen/generate (s/gen :lipas/sports-site))
                     (assoc :status "draft"))
        rev2     (-> rev1
                     (assoc :event-date (gen/generate (s/gen :lipas/timestamp)))
                     (assoc :name "Kissalan kuulahalli"))
        _        (core/upsert-sports-site! db user rev1)
        _        (core/upsert-sports-site! db user rev2)
        lipas-id (:lipas-id rev1)
        resp     (app (-> (mock/request :get (str "/api/sports-sites/"
                                                  lipas-id "/history"))
                          (mock/content-type "application/json")))
        body     (<-json (:body resp))]
    (is (= 200 (:status resp)))
    (is (s/valid? :lipas/sports-sites body))))

(comment (gen/generate (s/gen :lipas/sports-site)))
