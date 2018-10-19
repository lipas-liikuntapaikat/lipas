(ns lipas.backend.handler-test
  (:require [cheshire.core :as j]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.test :refer [deftest testing is] :as t]
            [lipas.backend.config :as config]
            [lipas.backend.core :as core]
            [lipas.backend.email :as email]
            [lipas.backend.jwt :as jwt]
            [lipas.backend.system :as system]
            [lipas.schema.core]
            [lipas.utils :as utils]
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

(def config (-> config/default-config
                (select-keys [:db :app])
                (assoc-in [:app :emailer] (email/->TestEmailer))))
(def system (system/start-system! config))
(def db (:db system))
(def app (:app system))

(defn gen-user
  ([]
   (gen-user {:db? false :admin? false}))
  ([{:keys [db? admin?]
     :or   {admin? false}}]
   (let [user (-> (gen/generate (s/gen :lipas/user))
                  (assoc :password (str (gensym)))
                  (assoc-in [:permissions :admin?] admin?))]
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

(deftest refresh-login-test
  (let [user   (gen-user {:db? true})
        token1 (jwt/create-token user :terse? true)
        _      (Thread/sleep 1000) ; to see effect between timestamps
        resp   (app (-> (mock/request :get "/api/actions/refresh-login")
                        (mock/content-type "application/json")
                        (token-header token1)))
        body   (<-json (:body resp))
        token2 (:token body)

        exp-old (:exp (jwt/unsign token1))
        exp-new (:exp (jwt/unsign token2))]

    (is (= 200 (:status resp)))
    (is (> exp-new exp-old))))

;; TODO how to test side-effects? (sending email)
(deftest request-password-reset-test
  (let [user (gen-user {:db? true})
        resp (app (-> (mock/request :post "/api/actions/request-password-reset")
                      (mock/content-type "application/json")
                      (mock/body (->json (select-keys user [:email])))))]
    (is (= 200 (:status resp)))))

(deftest request-password-reset-email-not-found-test
  (let [resp (app (-> (mock/request :post "/api/actions/request-password-reset")
                      (mock/content-type "application/json")
                      (mock/body (->json {:email "i-will-fail@fail.com"}))))
        body (<-json (:body resp))]
    (is (= 404 (:status resp)))
    (is (= "email-not-found" (:type body)))))

(deftest reset-password-test
  (let [user  (gen-user {:db? true})
        token (jwt/create-token user :terse? true)
        resp  (app (-> (mock/request :post "/api/actions/reset-password")
                       (mock/content-type "application/json")
                       (mock/body (->json {:password "blablaba"}))
                       (token-header token)))]
    (is (= 200 (:status resp)))))

(deftest reset-password-expired-token-test
  (let [user  (gen-user {:db? true})
        token (jwt/create-token user :terse? true :valid-seconds 0)
        _     (Thread/sleep 100) ; make sure token expires
        resp  (app (-> (mock/request :post "/api/actions/reset-password")
                       (mock/content-type "application/json")
                       (mock/body (->json {:password "blablaba"}))
                       (token-header token)))]
    (is (= 401 (:status resp)))))

(deftest send-magic-link-requires-admin-test
  (let [admin (gen-user {:db? true :admin? false})
        user  (-> (gen-user {:db? false})
                  (dissoc :password :id))
        token (jwt/create-token admin)
        resp  (app (-> (mock/request :post "/api/actions/send-magic-link")
                       (mock/content-type "application/json")
                       (mock/body (->json {:user      user
                                           :login-url "http://www.kissa.fi"}))
                       (token-header token)))]
    (is (= 403 (:status resp)))))

(deftest update-user-permissions-test
  ;; Updating permissions has side-effect of publshing drafts the user
  ;; has done earlier to sites where permissions are now being
  ;; granted
  (let [admin (gen-user {:db? true :admin? true})
        user  (gen-user {:db? true})

        ;; Add 'draft' which is expected to get publshed as side-effect
        event-date (utils/timestamp)
        draft?     true
        site       (-> (gen/generate (s/gen :lipas/sports-site))
                       (assoc :event-date event-date)
                       (assoc :status "active")
                       (assoc :lipas-id 123))
        _          (core/upsert-sports-site! db user site draft?)

        perms {:admin? true}
        token (jwt/create-token admin)
        resp  (app (-> (mock/request :post "/api/actions/update-user-permissions")
                       (mock/content-type "application/json")
                       (mock/body (->json {:id          (:id user)
                                           :permissions perms}))
                       (token-header token)))

        site-log (->> (core/get-sports-site-history db 123)
                      (utils/index-by :event-date))]
    (is (= 200 (:status resp)))
    (is (= perms (-> (core/get-user db (:email user))
                     :permissions)))
    (is (= "active" (:status (get site-log event-date))))))

(deftest update-user-permissions-requires-admin-test
  (let [user  (gen-user {:db? true :admin? false})
        token (jwt/create-token user)
        resp  (app (-> (mock/request :post "/api/actions/update-user-permissions")
                       (mock/content-type "application/json")
                       (mock/body (->json (select-keys user [:id :permissions])))
                       (token-header token)))]
    (is (= 403 (:status resp)))))

(deftest send-magic-link-test
  (let [admin (gen-user {:db? true :admin? true})
        user  (-> (gen-user {:db? false})
                  (dissoc :password :id))
        token (jwt/create-token admin)
        resp  (app (-> (mock/request :post "/api/actions/send-magic-link")
                       (mock/content-type "application/json")
                       (mock/body (->json {:user      user
                                           :login-url "http://www.kissa.fi"}))
                       (token-header token)))]
    (is (= 200 (:status resp)))))

(deftest upsert-sports-site-draft-test
  (let [user  (gen-user {:db? true})
        token (jwt/create-token user)
        site  (-> (gen/generate (s/gen :lipas/sports-site))
                  (assoc :status "active")
                  (dissoc :lipas-id))
        resp  (app (-> (mock/request :post "/api/sports-sites?draft=true")
                       (mock/content-type "application/json")
                       (mock/body (->json site))
                       (token-header token)))]
    (is (= 201 (:status resp)))))

(deftest upsert-sports-site-no-permissions-test
  (let [user  (gen-user)
        _     (as-> user $
                (dissoc $ :permissions)
                (core/add-user! db $))
        user  (core/get-user db (:email user))
        token (jwt/create-token user)
        site  (-> (gen/generate (s/gen :lipas/sports-site))
                  (assoc :status "active"))
        resp  (app (-> (mock/request :post "/api/sports-sites")
                       (mock/content-type "application/json")
                       (mock/body (->json site))
                       (token-header token)))]
    (is (= 403 (:status resp)))))

(deftest get-sports-sites-by-type-code-test
  (let [user (gen-user {:db? true :admin? true})
        site (-> (gen/generate (s/gen :lipas/sports-site))
                 (assoc-in [:type :type-code] 3110)
                 (assoc :status "active"))
        _    (core/upsert-sports-site! db user site)
        resp (app (-> (mock/request :get "/api/sports-sites/type/3110")
                      (mock/content-type "application/json")))
        body (<-json (:body resp))]
    (is (= 200 (:status resp)))
    (is (s/valid? :lipas/sports-sites body))))

(deftest get-sports-sites-yearly-by-type-code-test
  (let [user (gen-user {:db? true :admin? true})
        rev1 (-> (gen/generate (s/gen :lipas/sports-site))
                 (assoc-in [:type :type-code] 3110)
                 (assoc :status "active")
                 (assoc :event-date "2018-01-01T00:00:00.000Z"))
        rev2 (assoc rev1 :event-date "2018-02-01T00:00:00.000Z")
        rev3 (assoc rev1 :event-date "2017-01-01T00:00:00.000Z")
        _    (core/upsert-sports-site! db user rev1)
        _    (core/upsert-sports-site! db user rev2)
        _    (core/upsert-sports-site! db user rev3)
        id   (:lipas-id rev1)
        resp (app (-> (mock/request :get "/api/sports-sites/type/3110?revs=yearly")
                      (mock/content-type "application/json")))
        body (<-json (:body resp))]

    (is (= 200 (:status resp)))
    (is (= #{"2018-02-01T00:00:00.000Z" "2017-01-01T00:00:00.000Z"}
           (-> (group-by :lipas-id body)
               (get id)
               (as-> $ (into #{} (map :event-date) $)))))))

(deftest get-sports-sites-by-type-code-localized-test
  (let [user (gen-user {:db? true :admin? true})
        site (-> (gen/generate (s/gen :lipas/sports-site))
                 (assoc-in [:type :type-code] 3110)
                 (assoc-in [:admin] "state")
                 (assoc :status "active"))
        _    (core/upsert-sports-site! db user site)
        resp (app (-> (mock/request :get "/api/sports-sites/type/3110?lang=fi")
                      (mock/content-type "application/json")))
        body (<-json (:body resp))]
    (is (= 200 (:status resp)))
    ;; Note returned entities are not valid according to specs!
    (is (= "Valtio" (->> body
                         (filter #(= (:lipas-id site) (:lipas-id %)))
                         first
                         :admin)))))

(deftest get-sports-site-history-test
  (let [user     (gen-user {:db? true :admin? true})
        rev1     (-> (gen/generate (s/gen :lipas/sports-site))
                     (assoc :status "active"))
        rev2     (-> rev1
                     (assoc :event-date (gen/generate (s/gen :lipas/timestamp)))
                     (assoc :name "Kissalan kuulahalli"))
        _        (core/upsert-sports-site! db user rev1)
        _        (core/upsert-sports-site! db user rev2)
        lipas-id (:lipas-id rev1)
        resp     (app (-> (mock/request :get (str "/api/sports-sites/history/"
                                                  lipas-id))
                          (mock/content-type "application/json")))
        body     (<-json (:body resp))]
    (is (= 200 (:status resp)))
    (is (s/valid? :lipas/sports-sites body))))

(deftest create-energy-report-test
  (let [resp (app (-> (mock/request :post "/api/actions/create-energy-report")
                      (mock/content-type "application/json")
                      (mock/body (->json {:type-code 3110 :year 2017}))))]
    (is (= 200 (:status resp)))))

(comment
  (t/run-tests *ns*))
