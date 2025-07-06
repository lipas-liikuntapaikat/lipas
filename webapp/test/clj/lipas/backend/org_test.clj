(ns lipas.backend.org-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [integrant.core :as ig]
            [lipas.backend.config :as config]
            [lipas.backend.jwt :as jwt]
            [lipas.backend.org :as backend-org]
            [lipas.test-utils :as test-utils]
            [ring.mock.request :as mock]))

;;; Test system setup ;;;

(defonce test-system (atom nil))

(defn setup-test-system! []
  ;; Ensure database is properly initialized
  (test-utils/ensure-test-database!)
  ;; Initialize test system using test config (with _test database suffix)
  (reset! test-system
          (ig/init (select-keys (config/->system-config test-utils/config) [:lipas/db]))))

(defn teardown-test-system! []
  (when @test-system
    (ig/halt! @test-system)
    (reset! test-system nil)))

;;; Fixtures ;;;

(use-fixtures :once
  (fn [f]
    (setup-test-system!)
    (f)
    (teardown-test-system!)))

(use-fixtures :each
  (fn [f]
    ;; Clean all tables before each test
    (test-utils/prune-db! test-utils/db)
    (f)))

;;; Test System ;;;

;;; Helper Functions ;;;

(defn test-db []
  ;; Use test-utils db instead of test-system
  test-utils/db)

(defn gen-admin-user []
  (test-utils/gen-user {:db? true
                        :admin? true
                        :permissions {:roles []}})) ; Start with clean roles, admin? flag will add admin role

(defn gen-org-admin-user [org-id]
  (test-utils/gen-user {:db? true
                        :permissions {:roles [{:role "org-admin" :org-id [(str org-id)]}]}}))

(defn gen-regular-user []
  (test-utils/gen-user {:db? true
                        :permissions {:roles [{:role "default"}]}})) ; Start with clean roles, admin? flag will add admin role

(defn safe-parse-json [resp]
  (try
    (test-utils/<-json (:body resp))
    (catch Exception _
      nil)))

(defn- create-test-orgs
  "Creates multiple test orgs for testing"
  []
  (let [timestamp (System/currentTimeMillis)
        org1 {:id (java.util.UUID/randomUUID)
              :name (str "Helsinki Sports " timestamp)
              :data {:primary-contact {:phone "+358401234567"
                                       :email "helsinki@sports.fi"
                                       :website "https://helsinkisports.fi"
                                       :reservations-link "https://booking.helsinkisports.fi"}}
              :ptv-data {:ptv-org-id nil
                         :city-codes [91]
                         :owners ["city"]
                         :supported-languages ["fi" "se"]}}

        org2 {:id (java.util.UUID/randomUUID)
              :name (str "Espoo Athletics " timestamp)
              :data {:primary-contact {:phone "+358509876543"
                                       :email "info@espooathletics.fi"
                                       :website nil
                                       :reservations-link nil}}
              :ptv-data {:ptv-org-id nil
                         :city-codes [49]
                         :owners ["city-main-owner"]
                         :supported-languages ["fi"]}}

        orgs [org1 org2]]

    ;; Save all orgs to database
    (doseq [org orgs]
      (backend-org/create-org (test-db) org))

    orgs))

;;; Tests ;;;

(deftest create-org-success-test
  (testing "Successfully creates organization with valid data"
    (let [admin-user (gen-admin-user)
          token (jwt/create-token admin-user)
          timestamp (System/currentTimeMillis)
          org-data {:name (str "Test Organization " timestamp)
                    :data {:primary-contact {:phone "+358401234567"
                                             :email "test@example.com"
                                             :website "https://test.org"
                                             :reservations-link "https://booking.test.org"}}
                    :ptv-data {:ptv-org-id nil
                               :city-codes [91]
                               :owners ["city"]
                               :supported-languages ["fi"]}}
          resp (test-utils/app (-> (mock/request :post "/api/orgs")
                                   (mock/content-type "application/json")
                                   (mock/body (test-utils/->json org-data))
                                   (test-utils/token-header token)))
          body (safe-parse-json resp)]
      (is (= 200 (:status resp)))
      (is (some? body))
      (is (some? (:id body)))
      (is (= (str "Test Organization " timestamp) (:name body)))
      (is (= "test@example.com" (get-in body [:data :primary-contact :email])))
      (is (= [91] (get-in body [:ptv-data :city-codes]))))))

(deftest create-org-invalid-data-test
  (testing "Fails gracefully when creating organization with invalid data"
    (let [admin-user (gen-admin-user)
          token (jwt/create-token admin-user)
          invalid-org {:name "" ; Invalid: empty name
                       :data {:primary-contact {:email "invalid-email"}}} ; Invalid: bad email
          resp (test-utils/app (-> (mock/request :post "/api/orgs")
                                   (mock/content-type "application/json")
                                   (mock/body (test-utils/->json invalid-org))
                                   (test-utils/token-header token)))]
      (is (= 400 (:status resp))))))

(deftest add-user-by-email-success-test
  (testing "Successfully adds existing user to organization by email"
    (let [admin-user (gen-admin-user)
          target-user (gen-regular-user)
          [org1 _] (create-test-orgs)
          org-id (:id org1)
          token (jwt/create-token admin-user)
          email (:email target-user)
          user-data {:email email :role "org-user"}
          resp (test-utils/app (-> (mock/request :post (str "/api/orgs/" org-id "/add-user-by-email"))
                                   (mock/content-type "application/json")
                                   (mock/body (test-utils/->json user-data))
                                   (test-utils/token-header token)))
          body (safe-parse-json resp)]
      (is (= 200 (:status resp)))
      (is (:success? body))
      (is (contains? body :message)))))

(deftest add-user-by-email-user-not-found-test
  (testing "Returns helpful error when user email doesn't exist (GDPR-compliant)"
    (let [admin-user (gen-admin-user)
          [org1 _] (create-test-orgs)
          org-id (:id org1)
          token (jwt/create-token admin-user)
          nonexistent-email "nonexistent@example.com"
          user-data {:email nonexistent-email :role "org-user"}
          resp (test-utils/app (-> (mock/request :post (str "/api/orgs/" org-id "/add-user-by-email"))
                                   (mock/content-type "application/json")
                                   (mock/body (test-utils/->json user-data))
                                   (test-utils/token-header token)))
          body (safe-parse-json resp)]
      (is (= 400 (:status resp)))
      (is (false? (:success? body)))
      (is (string? (:message body)))
      (is (.contains (:message body) "No user found"))
      (is (.contains (:message body) "register an account")))))

(deftest add-user-by-email-privilege-test
  (testing "Requires org admin or LIPAS admin privilege"
    (let [regular-user (gen-regular-user)
          [org1 _] (create-test-orgs)
          org-id (:id org1)
          token (jwt/create-token regular-user)
          user-data {:email "test@example.com" :role "org-user"}
          resp (test-utils/app (-> (mock/request :post (str "/api/orgs/" org-id "/add-user-by-email"))
                                   (mock/content-type "application/json")
                                   (mock/body (test-utils/->json user-data))
                                   (test-utils/token-header token)))]
      (is (= 403 (:status resp))))))

(deftest get-org-users-test
  (testing "Successfully retrieves organization users"
    (let [target-user (gen-regular-user)
          [org1 _] (create-test-orgs)
          org-id (:id org1)
          admin-user (gen-org-admin-user org-id)
          token (jwt/create-token admin-user)

          ;; First add a user to the org
          _ (backend-org/update-org-users! (test-db) org-id
                                           [{:user-id (:id target-user)
                                             :change "add"
                                             :role "org-user"}])

          resp (test-utils/app (-> (mock/request :get (str "/api/orgs/" org-id "/users"))
                                   (test-utils/token-header token)))
          body (safe-parse-json resp)]
      (is (= 200 (:status resp)))
      (is (coll? body))

      ;; Verify we get back users who belong to this org
      (is (some (fn [user]
                  (some (fn [role]
                          (and (= "org-user" (:role role))
                               (when (:org-id role)
                                 (.contains (:org-id role) (str org-id)))))
                        (get-in user [:permissions :roles])))
                body)
          "At least one user should have org-user role for this specific organization"))))

(deftest cross-org-add-user-by-email-test
  (testing "Org admin of org A cannot add users to org B"
    ;; Setup: Create two organizations
    (let [[org-a org-b] (create-test-orgs)
          org-a-id (:id org-a)
          org-b-id (:id org-b)

          ;; Create org admin for org A only
          org-a-admin (gen-org-admin-user org-a-id)
          org-a-admin-token (jwt/create-token org-a-admin)

          ;; Create a target user to add
          target-user (gen-regular-user)
          email (:email target-user)
          user-data {:email email :role "org-user"}

          ;; Try to add user to org B (should fail)
          resp-org-b (test-utils/app (-> (mock/request :post (str "/api/orgs/" org-b-id "/add-user-by-email"))
                                         (mock/content-type "application/json")
                                         (mock/body (test-utils/->json user-data))
                                         (test-utils/token-header org-a-admin-token)))

          ;; Try to add user to org A (should succeed)
          resp-org-a (test-utils/app (-> (mock/request :post (str "/api/orgs/" org-a-id "/add-user-by-email"))
                                         (mock/content-type "application/json")
                                         (mock/body (test-utils/->json user-data))
                                         (test-utils/token-header org-a-admin-token)))

          body-org-a (safe-parse-json resp-org-a)]

      ;; Assert org admin cannot add to org B
      (is (= 403 (:status resp-org-b)) "Org admin should not be able to add users to another org")

      ;; Assert org admin can add to org A
      (is (= 200 (:status resp-org-a)) "Org admin should be able to add users to their own org")
      (is (:success? body-org-a)))))

(deftest cross-org-update-users-test
  (testing "Org admin of org A cannot update users in org B"
    ;; Setup: Create two organizations
    (let [[org-a org-b] (create-test-orgs)
          org-a-id (:id org-a)
          org-b-id (:id org-b)

          ;; Create org admin for org A only
          org-a-admin (gen-org-admin-user org-a-id)
          org-a-admin-token (jwt/create-token org-a-admin)

          ;; Create a regular user
          target-user (gen-regular-user)

          ;; Try to update users in org B (should fail)
          resp-org-b (test-utils/app (-> (mock/request :post (str "/api/orgs/" org-b-id "/users"))
                                         (mock/content-type "application/json")
                                         (mock/body (test-utils/->json {:changes [{:user-id (:id target-user)
                                                                                   :change "add"
                                                                                   :role "org-user"}]}))
                                         (test-utils/token-header org-a-admin-token)))

          ;; Try to update users in org A (should succeed)
          resp-org-a (test-utils/app (-> (mock/request :post (str "/api/orgs/" org-a-id "/users"))
                                         (mock/content-type "application/json")
                                         (mock/body (test-utils/->json {:changes [{:user-id (:id target-user)
                                                                                   :change "add"
                                                                                   :role "org-user"}]}))
                                         (test-utils/token-header org-a-admin-token)))]

      ;; Assert org admin cannot update users in org B
      (is (= 403 (:status resp-org-b)) "Org admin should not be able to update users in another org")

      ;; Assert org admin can update users in org A
      (is (= 200 (:status resp-org-a)) "Org admin should be able to update users in their own org"))))

(deftest cross-org-update-org-data-test
  (testing "Org admin of org A cannot update org B's information"
    ;; Setup: Create two organizations
    (let [[org-a org-b] (create-test-orgs)
          org-a-id (:id org-a)
          org-b-id (:id org-b)

          ;; Create org admin for org A only
          org-a-admin (gen-org-admin-user org-a-id)
          org-a-admin-token (jwt/create-token org-a-admin)

          ;; Updated org data with all required fields
          updated-data {:id org-b-id
                        :name "Updated Organization Name"
                        :data {:primary-contact {:phone "+358401234567"
                                                 :email "updated@example.com"
                                                 :website "https://updated.com"
                                                 :reservations-link "https://booking.updated.com"}}
                        :ptv-data {:city-codes [49]
                                   :owners ["city"]
                                   :supported-languages ["fi"]}}

          ;; Try to update org B (should fail)
          resp-org-b (test-utils/app (-> (mock/request :put (str "/api/orgs/" org-b-id))
                                         (mock/content-type "application/json")
                                         (mock/body (test-utils/->json updated-data))
                                         (test-utils/token-header org-a-admin-token)))

          ;; Try to update org A (should succeed)
          resp-org-a (test-utils/app (-> (mock/request :put (str "/api/orgs/" org-a-id))
                                         (mock/content-type "application/json")
                                         (mock/body (test-utils/->json (assoc updated-data :id org-a-id)))
                                         (test-utils/token-header org-a-admin-token)))]

      ;; Assert org admin cannot update org B
      (is (= 403 (:status resp-org-b)) "Org admin should not be able to update another org's data")

      ;; Assert org admin can update org A
      (is (= 200 (:status resp-org-a)) "Org admin should be able to update their own org's data"))))

(deftest cross-org-view-users-test
  (testing "Org admin of org A cannot view users of org B"
    ;; Setup: Create two organizations
    (let [[org-a org-b] (create-test-orgs)
          org-a-id (:id org-a)
          org-b-id (:id org-b)

          ;; Create org admin for org A only
          org-a-admin (gen-org-admin-user org-a-id)
          org-a-admin-token (jwt/create-token org-a-admin)

          ;; Try to view users in org B (should fail)
          resp-org-b (test-utils/app (-> (mock/request :get (str "/api/orgs/" org-b-id "/users"))
                                         (test-utils/token-header org-a-admin-token)))

          ;; Try to view users in org A (should succeed)
          resp-org-a (test-utils/app (-> (mock/request :get (str "/api/orgs/" org-a-id "/users"))
                                         (test-utils/token-header org-a-admin-token)))]

      ;; Assert org admin cannot view users in org B
      (is (= 403 (:status resp-org-b)) "Org admin should not be able to view users in another org")

      ;; Assert org admin can view users in org A
      (is (= 200 (:status resp-org-a)) "Org admin should be able to view users in their own org"))))

(comment
  ;; Cross org tests are currently failing
  (clojure.test/run-test-var #'cross-org-view-users-test)
  (clojure.test/run-test-var #'cross-org-update-org-data-test)
  (clojure.test/run-test-var #'cross-org-update-users-test)
  (clojure.test/run-test-var #'cross-org-add-user-by-email-test)

  (clojure.test/run-test-var #'get-org-users-test)
  (clojure.test/run-test-var #'add-user-by-email-privilege-test)
  (clojure.test/run-test-var #'add-user-by-email-user-not-found-test)
  (clojure.test/run-test-var #'add-user-by-email-success-test)
  (clojure.test/run-test-var #'create-org-invalid-data-test)
  (clojure.test/run-test-var #'create-org-success-test))
