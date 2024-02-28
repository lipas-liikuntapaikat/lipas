(ns lipas.backend.handler-test
  (:require [cheshire.core :as j]
            [clojure.java.jdbc :as jdbc]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.string :as str]
            [clojure.test :refer [deftest is use-fixtures] :as t]
            [cognitect.transit :as transit]
            [dk.ative.docjure.spreadsheet :as excel]
            [lipas.backend.analysis.diversity :as diversity]
            [lipas.backend.config :as config]
            [lipas.backend.core :as core]
            [lipas.backend.email :as email]
            [lipas.backend.jwt :as jwt]
            [lipas.backend.search :as search]
            [lipas.backend.system :as system]
            [lipas.data.loi :as loi]
            [lipas.schema.core]
            [lipas.seed :as seed]
            [lipas.test-utils :as tu]
            [lipas.utils :as utils]
            [migratus.core :as migratus]
            [ring.mock.request :as mock])
  (:import [java.io ByteArrayOutputStream]
           java.util.Base64))

;;; Setup ;;;

(def <-json #(j/parse-string (slurp %) true))
(def ->json j/generate-string)

(defn ->transit [x]
  (let [out (ByteArrayOutputStream. 4096)
        writer (transit/writer out :json)]
    (transit/write writer x)
    (.toString out)))

(defn <-transit [in]
  (let [reader (transit/reader in :json)]
    (transit/read reader)))

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

(defn- test-suffix [s] (str s "_test"))

(def config (-> config/default-config
                (select-keys [:db :app :search :mailchimp :aws])
                (assoc-in [:app :emailer] (email/->TestEmailer))
                (update-in [:db :dbname] test-suffix)
                (assoc-in [:db :dev] true) ;; No connection pool
                (update-in [:search :indices :sports-site :search] test-suffix)
                (update-in [:search :indices :sports-site :analytics] test-suffix)
                (update-in [:search :indices :report :subsidies] test-suffix)
                (update-in [:search :indices :report :city-stats] test-suffix)
                (update-in [:search :indices :analysis :schools] test-suffix)
                (update-in [:search :indices :analysis :population] test-suffix)
                (update-in [:search :indices :analysis :population-high-def] test-suffix)
                (update-in [:search :indices :analysis :diversity] test-suffix)
                (update-in [:search :indices :lois :search] test-suffix)))

(defn init-db! []
  (let [migratus-opts {:store         :database
                       :migration-dir "migrations"
                       :db            (:db config)}]
    (try
      (jdbc/db-do-commands (-> config :db (assoc :dbname ""))
                           false
                           [(str "CREATE DATABASE " (-> config :db :dbname))])
      (catch Exception e
        (when-not (= "ERROR: database \"lipas_test\" already exists"
                     (-> e .getCause .getMessage))
          (throw e))))

    (jdbc/db-do-commands (:db config)
                           false
                           [(str "CREATE EXTENSION IF NOT EXISTS postgis")
                            (str "CREATE EXTENSION IF NOT EXISTS postgis_topology")
                            (str "CREATE EXTENSION IF NOT EXISTS citext")
                            (str "CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"")])

    (migratus/init migratus-opts)
    (migratus/migrate migratus-opts)))

(def tables
  ["account"
   "analysis_queue"
   "city"
   "elevation_queue"
   "email_out_queue"
   "integration_log"
   "integration_out_queue"
   "reminder"
   "sports_site"
   "subsidy"])

(defn prune-db! []
  (jdbc/execute! (:db config) [(str "TRUNCATE "
                                    (str/join "," tables)
                                    " RESTART IDENTITY CASCADE")]))

(def system (system/start-system! config))

(def db (:db system))
(def app (:app system))
(def search (:search system))

(defn prune-es! []
  (let [client   (:client search)
        mappings {(-> search :indices :sports-site :search) (:sports-sites search/mappings)
                  (-> search :indices :analysis :diversity) diversity/mappings
                  (-> search :indices :lois :search) (:lois search/mappings)}]

    (doseq [idx-name (-> search :indices vals (->> (mapcat vals)))]
      (try
        (search/delete-index! client idx-name)
        (catch Exception ex
          (when (not= "index_not_found_exception"
                      (-> ex ex-data :body :error :root_cause first :type))
            (throw ex))))
      (when-let [mapping (mappings idx-name)]
        (search/create-index! client idx-name mapping)))))

(comment
  (init-db!)
  (prune-es!)
  (prune-db!)
  (ex-data *e)
  )

(defn gen-user
  ([]
   (gen-user {:db? false :admin? false :status "active"}))
  ([{:keys [db? admin? status]
     :or   {admin? false status "active"}}]
   (let [user (-> (gen/generate (s/gen :lipas/user))
                  (assoc :password (str (gensym)) :status status)
                  (assoc-in [:permissions :admin?] admin?))]
     (if db?
       (do
         (core/add-user! db user)
         (assoc user :id (:id (core/get-user db (:email user)))))
       user))))

(defn gen-loi! []
  (-> (gen/generate (s/gen :lipas.loi/document))
      (assoc :status "active")
      (assoc :id (str (java.util.UUID/randomUUID)))))

;;; Fixtures ;;;

(use-fixtures :once (fn [f] (init-db!) (f)))
(use-fixtures :each (fn [f] (prune-db!) (prune-es!) (f)))

;;; The tests ;;;

;;; Location of interests ;;;

(deftest search-loi-by-type
  (let [loi-type "fishing-pier"
        loi-category "outdoor-recreation-facilities"
        loi  (-> (gen-loi!)
                 (assoc :loi-type loi-type)
                 (assoc :loi-category loi-category))
        _    (core/index-loi! search loi :sync)
        resp (app (-> (mock/request :get (str "/api/lois/type/" "fishing-pier"))
                      (mock/content-type "application/json")))
        response-loi (first (<-json (:body resp)))]
    (is (= loi-type (:loi-type response-loi)))))



(deftest search-loi-by-invalid-type
  (let [bad-request-response (app (-> (mock/request :get (str "/api/lois/type/kekkosen-ulkoilu"))
                                      (mock/content-type "application/json")))]
    ;; can we test that the exception is thrown?
    ;; we'd like to clean the output, now it floods it with an error message
    (is (= 400 (:status bad-request-response)))))

(deftest search-loi-by-category
  (let [loi-category "outdoor-recreation-facilities"
        loi  (-> (gen-loi!)
                 (assoc :loi-category loi-category))
        _    (core/index-loi! search loi :sync)
        resp (app (-> (mock/request :get (str "/api/lois/category/" loi-category))
                      (mock/content-type "application/json")))
        response-loi (first (<-json (:body resp)))]
    (is (= loi-category (:loi-category response-loi)))))

(deftest get-loi-by-id
  (let [{:keys [id] :as loi} (gen-loi!)
        _    (core/index-loi! search loi :sync)
        resp (app (-> (mock/request :get (str "/api/lois/" id))
                      (mock/content-type "application/json")))
        response-loi (<-json (:body resp))]
    (is (= loi response-loi))))

(deftest search-loi-by-invalid-category
  (let [loi-category "kekkonen-666-category"
        loi  (-> (gen-loi!)
                 (assoc :loi-category loi-category))
        _    (core/index-loi! search loi :sync)
        response (app (-> (mock/request :get (str "/api/lois/category/" loi-category))
                      (mock/content-type "application/json")))]
    (is (= 400 (:status response)))))

(deftest search-loi-by-status
  (let [geometry {:features [{:geometry {:coordinates [25.974759578704834 67.703125]
                                         :type "Point"}
                              :type "Feature"}]
                  :type "FeatureCollection"}
        lois-with-every-status [{:event-date "1901-02-13T12:40:08.957Z"
                                 :geometries geometry
                                 :id (java.util.UUID/randomUUID)
                                 :loi-category "outdoor-recreation-facilities"
                                 :loi-type "mooring-ring"
                                 :status "planning"}
                                {:event-date "1994-12-17T07:45:51.186Z"
                                 :geometries geometry
                                 :id (java.util.UUID/randomUUID)
                                 :loi-category "outdoor-recreation-facilities"
                                 :loi-type "canopy"
                                 :status "planned"}
                                {:event-date "1997-07-26T13:43:03.959Z"
                                 :geometries geometry
                                 :id (java.util.UUID/randomUUID)
                                 :loi-category "outdoor-recreation-facilities"
                                 :loi-type "dog-swimming-area"
                                 :status "active"}
                                {:event-date "2015-12-24T16:31:49.045Z"
                                 :geometries geometry
                                 :id (java.util.UUID/randomUUID)
                                 :loi-category "outdoor-recreation-facilities"
                                 :loi-type "historical-structure"
                                 :status "out-of-service-temporarily"}
                                {:event-date "1900-07-01T15:50:31.924Z"
                                 :geometries geometry
                                 :id (java.util.UUID/randomUUID)
                                 :loi-category "outdoor-recreation-facilities"
                                 :loi-type "canopy"
                                 :status "out-of-service-permanently"}
                                {:event-date "1900-07-01T15:50:31.924Z"
                                 :geometries geometry
                                 :id (java.util.UUID/randomUUID)
                                 :loi-category "outdoor-recreation-facilities"
                                 :loi-type "parking-spot"
                                 :status "incorrect-data"}]
        _         (doseq [loi lois-with-every-status] (core/index-loi! search loi :sync))
        responses (mapv #(app (-> (mock/request :get (str "/api/lois/status/" %))
                                  (mock/content-type "application/json")))
                        (keys loi/statuses))
        bodies (mapv (comp first <-json :body) responses)]
    (is (= "planning" (:status (nth bodies 0))))
    (is (= "planned"  (:status (nth bodies 1))))
    (is (= "active" (:status (nth bodies 2))))
    (is (= "out-of-service-temporarily" (:status (nth bodies 3))))
    (is (= "out-of-service-permanently" (:status (nth bodies 4))))
    (is (= "incorrect-data" (:status (nth bodies 5))))))


(deftest search-lois-by-location
  (let [loi-a {:event-date "1901-02-13T12:40:08.957Z"
               :geometries {:features
                            [{:geometry
                              {:coordinates [25 80.0002]
                               :type "Point"}
                              :type "Feature"}]
                            :type "FeatureCollection"}
               :id "fff0ec1a-d12c-4145-bb56-729396ad90ff"
               :loi-category "outdoor-recreation-facilities"
               :loi-type "mooring-ring"
               :status "active"}
        loi-b {:event-date "1901-02-13T12:40:08.957Z"
               :geometries {:features
                            [{:geometry
                              {:coordinates [25 80]
                               :type "Point"}
                              :type "Feature"}]
                            :type "FeatureCollection"}
               :id "a4c29b32-73bd-465f-ace1-06d50a1838ce"
               :loi-category "outdoor-recreation-facilities"
               :loi-type "mooring-ring"
               :status "active"}
        body-params {:location {:lon 25.0
                                :lat 68.0
                                :distance 100}
                     :loi-statuses ["active"]}]
    (core/index-loi! search loi-a :sync)
    (core/index-loi! search loi-b :sync)
    (let [response (<-json (:body
                            (app (-> (mock/request :post (str "/api/actions/search-lois"))
                                     (mock/content-type "application/json")
                                     (mock/body (->json body-params))))))
          sorted-lois (sort-by :_score > response)]
      (is (> (:_score (first sorted-lois))
             (:_score (second sorted-lois)))))))

(comment
  (t/run-test search-loi-by-status)
  (t/run-test search-lois-by-location)
  )

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
                                           :login-url "https://localhost"
                                           :variant   "lipas"}))
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
        site       (-> (tu/gen-sports-site)
                       (assoc :event-date event-date)
                       (assoc :status "active")
                       (assoc :lipas-id 123))
        _          (core/upsert-sports-site!* db user site draft?)

        perms {:admin? true}
        token (jwt/create-token admin)
        resp  (app (-> (mock/request :post "/api/actions/update-user-permissions")
                       (mock/content-type "application/json")
                       (mock/body (->json {:id          (:id user)
                                           :permissions perms
                                           :login-url   "https://localhost"}))
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
                       (mock/body (-> user
                                      (select-keys [:id :permissions])
                                      (assoc :login-url "https://localhost")
                                      ->json))
                       (token-header token)))]
    (is (= 403 (:status resp)))))

(deftest update-user-data-test
  (let [user       (gen-user {:db? true})
        token      (jwt/create-token user :terse? true)
        user-data  (gen/generate (s/gen :lipas.user/user-data))
        resp       (app (-> (mock/request :post "/api/actions/update-user-data")
                            (mock/content-type "application/json")
                            (mock/body (->json user-data))
                            (token-header token)))
        user-data2 (-> resp :body <-json)]
    (is (= 200 (:status resp)))
    (is (= user-data user-data2))))

(deftest update-user-status-test
  (let [admin (gen-user {:db? true :admin? true})
        user  (gen-user {:db? true :status "active"})
        token (jwt/create-token admin)
        resp  (app (-> (mock/request :post "/api/actions/update-user-status")
                       (mock/content-type "application/json")
                       (mock/body (->json {:id (:id user) :status "archived"}))
                       (token-header token)))
        user2 (-> resp :body <-json)]
    (is (= 200 (:status resp)))
    (is (= "archived" (:status user2)))))

(deftest update-user-status-requires-admin-test
  (let [admin (gen-user {:db? true :admin? false})
        user  (gen-user {:db? true :status "active"})
        token (jwt/create-token admin)
        resp  (app (-> (mock/request :post "/api/actions/update-user-status")
                       (mock/content-type "application/json")
                       (mock/body (->json {:id (:id user) :status "archived"}))
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
                                           :login-url "https://localhost"
                                           :variant   "lipas"}))
                       (token-header token)))]
    (is (= 200 (:status resp)))))

(deftest order-magic-link-test
  (let [user  (gen-user {:db? true})
        resp  (app (-> (mock/request :post "/api/actions/order-magic-link")
                       (mock/content-type "application/json")
                       (mock/body (->json {:email     (:email user)
                                           :login-url "https://localhost"
                                           :variant   "lipas"}))))]
    (is (= 200 (:status resp)))))

(deftest order-magic-link-login-url-whitelist-test
  (let [resp  (app (-> (mock/request :post "/api/actions/order-magic-link")
                       (mock/content-type "application/json")
                       (mock/body (->json {:email     (:email "kissa@koira.fi")
                                           :login-url "https://bad-attacker.fi"
                                           :variant   "lipas"}))))]
    (is (= 400 (:status resp)))))

(deftest upsert-sports-site-draft-test
  (let [user  (gen-user {:db? true})
        token (jwt/create-token user)
        site  (-> (tu/gen-sports-site)
                  (assoc :status "active")
                  (dissoc :lipas-id))
        resp  (app (-> (mock/request :post "/api/sports-sites?draft=true")
                       (mock/content-type "application/json")
                       (mock/body (->json site))
                       (token-header token)))]
    (is (= 201 (:status resp)))))

(deftest upsert-invalid-sports-site-test
  (let [user  (gen-user {:db? true})
        token (jwt/create-token user)
        site  (-> (tu/gen-sports-site)
                  (assoc :status "kebab")
                  (dissoc :lipas-id))
        resp  (app (-> (mock/request :post "/api/sports-sites?draft=true")
                       (mock/content-type "application/json")
                       (mock/body (->json site))
                       (token-header token)))]
    (is (= 400 (:status resp)))))

(deftest upsert-sports-site-no-permissions-test
  (let [user  (gen-user)
        _     (as-> user $
                (dissoc $ :permissions)
                (core/add-user! db $))
        user  (core/get-user db (:email user))
        token (jwt/create-token user)
        site  (-> (tu/gen-sports-site)
                  (assoc :status "active"))
        resp  (app (-> (mock/request :post "/api/sports-sites")
                       (mock/content-type "application/json")
                       (mock/body (->json site))
                       (token-header token)))]
    (is (= 403 (:status resp)))))

(deftest get-sports-sites-by-type-code-test
  (let [user (gen-user {:db? true :admin? true})
        site (-> (tu/gen-sports-site)
                 (assoc-in [:type :type-code] 3110)
                 (assoc :status "active"))
        _    (core/upsert-sports-site!* db user site)
        resp (app (-> (mock/request :get "/api/sports-sites/type/3110")
                      (mock/content-type "application/json")))
        body (<-json (:body resp))]
    (is (= 200 (:status resp)))
    (is (s/valid? :lipas/sports-sites body))))

(deftest get-sports-sites-by-type-code-localized-test
  (let [user (gen-user {:db? true :admin? true})
        site (-> (tu/gen-sports-site)
                 (assoc-in [:type :type-code] 3110)
                 (assoc-in [:admin] "state")
                 (assoc :status "active"))
        _    (core/upsert-sports-site!* db user site)
        resp (app (-> (mock/request :get "/api/sports-sites/type/3110?lang=fi")
                      (mock/content-type "application/json")))
        body (<-json (:body resp))]
    (is (= 200 (:status resp)))
    ;; Note returned entities are not valid according to specs!
    (is (= "Valtio" (->> body
                         (filter #(= (:lipas-id site) (:lipas-id %)))
                         first
                         :admin)))))

(deftest get-sports-site-test
  (let [user     (gen-user {:db? true :admin? true})
        rev1     (-> (tu/gen-sports-site)
                     (assoc :status "active"))
        _        (core/upsert-sports-site!* db user rev1)
        lipas-id (:lipas-id rev1)
        resp     (app (-> (mock/request :get (str "/api/sports-sites/" lipas-id))
                          (mock/content-type "application/json")))
        body     (<-json (:body resp))]
    (is (= 200 (:status resp)))
    (is (s/valid? :lipas/sports-site body))))

(deftest get-non-existing-sports-site-test
  (let [lipas-id 9999999999
        resp     (app (-> (mock/request :get (str "/api/sports-sites/" lipas-id))
                          (mock/content-type "application/json")))]
    (is (= 404 (:status resp)))))

(deftest get-sports-site-history-test
  (let [user     (gen-user {:db? true :admin? true})
        rev1     (-> (tu/gen-sports-site)
                     (assoc :status "active"))
        rev2     (-> rev1
                     (assoc :event-date (gen/generate (s/gen :lipas/timestamp)))
                     (assoc :name "Kissalan kuulahalli"))
        _        (core/upsert-sports-site!* db user rev1)
        _        (core/upsert-sports-site!* db user rev2)
        lipas-id (:lipas-id rev1)
        resp     (app (-> (mock/request :get (str "/api/sports-sites/history/"
                                                  lipas-id))
                          (mock/content-type "application/json")))
        body     (<-json (:body resp))]
    (is (= 200 (:status resp)))
    (is (s/valid? :lipas/sports-sites body))))

(deftest search-test
  (let [site     (tu/gen-sports-site)
        lipas-id (:lipas-id site)
        name     (:name site)
        _        (core/index! search site :sync)
        resp     (app (-> (mock/request :post "/api/actions/search")
                          (mock/content-type "application/json")
                          (mock/body (->json {:query
                                              {:bool
                                               {:must
                                                [{:query_string
                                                  {:query name}}]}}}))))
        body     (<-json (:body resp))
        sites    (map :_source (-> body :hits :hits))]
    (is (= 200 (:status resp)))
    (is (some? (first (filter (comp #{lipas-id} :lipas-id) sites))))
    (is (s/valid? :lipas/sports-sites sites))))

(deftest sports-sites-report-test
  (let [site     (tu/gen-sports-site)
        _        (core/index! search site :sync)
        path     "/api/actions/create-sports-sites-report"
        resp     (app (-> (mock/request :post path)
                          (mock/content-type "application/json")
                          (mock/body (->json
                                      {:search-query
                                       {:query
                                        {:bool
                                         {:must
                                          [{:query_string
                                            {:query "*"}}]}}}
                                       :fields ["lipas-id"
                                                "name"
                                                "location.city.city-code"]
                                       :locale :fi}))))
        body     (:body resp)
        wb       (excel/load-workbook body)
        header-1 (excel/read-cell
                  (->> wb
                       (excel/select-sheet "lipas")
                       (excel/select-cell "A1")))]
    (is (= 200 (:status resp)))
    (is (= "Lipas-id" header-1))))

(deftest finance-report-test
  (let [_    (seed/seed-city-data! db)
        path "/api/actions/create-finance-report"
        resp (app (-> (mock/request :post path)
                      (mock/content-type "application/json")
                      (mock/body (->json {:city-codes [275 972]}))))
        body (-> resp :body <-json)]
    (is (= 200 (:status resp)))
    (is (contains? body :country-averages))
    (is (= '(:275 :972) (-> body :data-points keys)))))

(deftest calculate-stats-test
  (let [_    (seed/seed-city-data! db)
        user (gen-user {:db? true :admin? true})
        site (-> (tu/gen-sports-site)
                 (assoc :status "active")
                 (assoc-in [:location :city :city-code] 275)
                 (assoc-in [:properties :area-m2] 100))
        _    (core/upsert-sports-site!* db user site)
        _    (core/index! search site :sync)
        path "/api/actions/calculate-stats"
        resp (app (-> (mock/request :post path)
                      (mock/content-type "application/transit+json")
                      (mock/header "Accept" "application/transit+json")
                      (mock/body (->transit {:city-codes [275 972]}))))
        body (-> resp :body <-transit)]
    (is (= 200 (:status resp)))
    (is (number? (get-in body [275 :area-m2-pc])))))

(deftest create-energy-report-test
  (let [resp (app (-> (mock/request :post "/api/actions/create-energy-report")
                      (mock/content-type "application/json")
                      (mock/body (->json {:type-code 3110 :year 2017}))))]
    (is (= 200 (:status resp)))))

(deftest add-reminder-test
  (let [user     (gen-user {:db? true})
        token    (jwt/create-token user :terse? true)
        reminder (gen/generate (s/gen :lipas/new-reminder))
        resp     (app (-> (mock/request :post "/api/actions/add-reminder")
                          (mock/content-type "application/json")
                          (mock/body (->json reminder))
                          (token-header token)))
        body     (-> resp :body <-json)]
    (is (= 200 (:status resp)))
    (is (= "pending" (:status body)))))

(deftest update-reminder-status-test
  (let [user     (gen-user {:db? true})
        token    (jwt/create-token user :terse? true)
        reminder (gen/generate (s/gen :lipas/new-reminder))
        resp1    (app (-> (mock/request :post "/api/actions/add-reminder")
                          (mock/content-type "application/json")
                          (mock/body (->json reminder))
                          (token-header token)))
        id       (-> resp1 :body <-json :id)
        resp2    (app (-> (mock/request :post "/api/actions/update-reminder-status")
                          (mock/content-type "application/json")
                          (mock/body (->json {:id id :status "canceled"}))
                          (token-header token)))]
    (is (= 200 (:status resp1)))
    (is (= 200 (:status resp2)))))

(deftest search-order-test
  (doseq [site-name ["\"Bantis\" beachvolleykenttä 2"
                     "Antis"
                     "bantis beachvolleykenttä (1)"
                     "!antis"]]
    (core/index! search (-> (tu/gen-sports-site) (assoc :name site-name)) :sync))
  (let [response (-> (mock/request :post "/api/actions/search")
                     (mock/content-type "application/json")
                     (mock/body (->json {:query
                                         {:bool
                                          {:must
                                           [{:query_string
                                             {:query "*"}}]}}
                                         :sort
                                         [{:search-meta.name.keyword
                                           {:order :asc}}]}))
                     app)
        actual-sites    (-> response
                            (:body)
                            (<-json)
                            (as-> body (map :_source (-> body :hits :hits)))
                            (as-> site (map :name site)))
        expected-site-names ["!antis"
                             "Antis"
                             "bantis beachvolleykenttä (1)"
                             "\"Bantis\" beachvolleykenttä 2"]]
    (is (= actual-sites expected-site-names))))

(comment
  (t/run-tests *ns*)
  (t/run-test search-order-test)
  (t/run-test search-loi-by-status)
  (t/run-test get-loi-by-id)
  )
