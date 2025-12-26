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

;;; Fixtures ;;;

(let [{:keys [once each]} (test-utils/full-system-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

;;; Helper Functions ;;;

(defn test-db [] (:lipas/db @test-system))
(defn test-app [req] ((:lipas/app @test-system) req))

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
              :ptv-data {:org-id nil
                         :city-codes [91]
                         :owners ["city"]
                         :supported-languages ["fi" "se"]}}

        org2 {:id (java.util.UUID/randomUUID)
              :name (str "Espoo Athletics " timestamp)
              :data {:primary-contact {:phone "+358509876543"
                                       :email "info@espooathletics.fi"
                                       :website nil
                                       :reservations-link nil}}
              :ptv-data {:org-id nil
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
    (let [admin-user (test-utils/gen-admin-user :db-component (test-db))
          token (jwt/create-token admin-user)
          timestamp (System/currentTimeMillis)
          org-data {:name (str "Test Organization " timestamp)
                    :data {:primary-contact {:phone "+358401234567"
                                             :email "test@example.com"
                                             :website "https://test.org"
                                             :reservations-link "https://booking.test.org"}}
                    :ptv-data {:org-id nil
                               :city-codes [91]
                               :owners ["city"]
                               :supported-languages ["fi"]}}
          resp (test-app (-> (mock/request :post "/api/orgs")
                             (mock/content-type "application/json")
                             (mock/body (test-utils/->json org-data))
                             (test-utils/token-header token)))
          body (test-utils/safe-parse-json resp)]
      (is (= 200 (:status resp)))
      (is (some? body))
      (is (some? (:id body)))
      (is (= (str "Test Organization " timestamp) (:name body)))
      (is (= "test@example.com" (get-in body [:data :primary-contact :email])))
      (is (= [91] (get-in body [:ptv-data :city-codes]))))))

(deftest create-org-invalid-data-test
  (testing "Fails gracefully when creating organization with invalid data"
    (let [admin-user (test-utils/gen-admin-user :db-component (test-db))
          token (jwt/create-token admin-user)
          invalid-org {:name "" ; Invalid: empty name
                       :data {:primary-contact {:email "invalid-email"}}} ; Invalid: bad email
          resp (test-app (-> (mock/request :post "/api/orgs")
                             (mock/content-type "application/json")
                             (mock/body (test-utils/->json invalid-org))
                             (test-utils/token-header token)))]
      (is (= 400 (:status resp))))))

(deftest add-user-by-email-success-test
  (testing "Successfully adds existing user to organization by email"
    (let [admin-user (test-utils/gen-admin-user :db-component (test-db))
          target-user (test-utils/gen-regular-user :db-component (test-db))
          [org1 _] (create-test-orgs)
          org-id (:id org1)
          token (jwt/create-token admin-user)
          email (:email target-user)
          user-data {:email email :role "org-user"}
          resp (test-app (-> (mock/request :post (str "/api/orgs/" org-id "/add-user-by-email"))
                             (mock/content-type "application/json")
                             (mock/body (test-utils/->json user-data))
                             (test-utils/token-header token)))
          body (test-utils/safe-parse-json resp)]
      (is (= 200 (:status resp)))
      (is (:success? body))
      (is (contains? body :message)))))

(deftest add-user-by-email-user-not-found-test
  (testing "Returns helpful error when user email doesn't exist (GDPR-compliant)"
    (let [admin-user (test-utils/gen-admin-user :db-component (test-db))
          [org1 _] (create-test-orgs)
          org-id (:id org1)
          token (jwt/create-token admin-user)
          nonexistent-email "nonexistent@example.com"
          user-data {:email nonexistent-email :role "org-user"}
          resp (test-app (-> (mock/request :post (str "/api/orgs/" org-id "/add-user-by-email"))
                             (mock/content-type "application/json")
                             (mock/body (test-utils/->json user-data))
                             (test-utils/token-header token)))
          body (test-utils/safe-parse-json resp)]
      (is (= 400 (:status resp)))
      (is (false? (:success? body)))
      (is (string? (:message body)))
      (is (.contains (:message body) "No user found"))
      (is (.contains (:message body) "register an account")))))

(deftest add-user-by-email-privilege-test
  (testing "Requires org admin or LIPAS admin privilege"
    (let [regular-user (test-utils/gen-regular-user :db-component (test-db))
          [org1 _] (create-test-orgs)
          org-id (:id org1)
          token (jwt/create-token regular-user)
          user-data {:email "test@example.com" :role "org-user"}
          resp (test-app (-> (mock/request :post (str "/api/orgs/" org-id "/add-user-by-email"))
                             (mock/content-type "application/json")
                             (mock/body (test-utils/->json user-data))
                             (test-utils/token-header token)))]
      (is (= 403 (:status resp))))))

(deftest lipas-admin-can-view-org-users-test
  (testing "LIPAS admin can view users of any organization"
    (let [[org1 _] (create-test-orgs)
          org-id (:id org1)
          ;; LIPAS admin - not a member of this org
          admin-user (test-utils/gen-admin-user :db-component (test-db))
          token (jwt/create-token admin-user)
          resp (test-app (-> (mock/request :get (str "/api/orgs/" org-id "/users"))
                             (test-utils/token-header token)))]
      (is (= 200 (:status resp)) "LIPAS admin should be able to view org users"))))

(deftest get-org-users-test
  (testing "Successfully retrieves organization users"
    (let [target-user (test-utils/gen-regular-user :db-component (test-db))
          [org1 _] (create-test-orgs)
          org-id (:id org1)
          admin-user (test-utils/gen-org-admin-user org-id :db-component (test-db))
          token (jwt/create-token admin-user)

          ;; First add a user to the org
          _ (backend-org/update-org-users! (test-db) org-id
                                           [{:user-id (:id target-user)
                                             :change "add"
                                             :role "org-user"}])

          resp (test-app (-> (mock/request :get (str "/api/orgs/" org-id "/users"))
                             (test-utils/token-header token)))
          body (test-utils/safe-parse-json resp)]
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
          org-a-admin (test-utils/gen-org-admin-user org-a-id :db-component (test-db))
          org-a-admin-token (jwt/create-token org-a-admin)

          ;; Create a target user to add
          target-user (test-utils/gen-regular-user :db-component (test-db))
          email (:email target-user)
          user-data {:email email :role "org-user"}

          ;; Try to add user to org B (should fail)
          resp-org-b (test-app (-> (mock/request :post (str "/api/orgs/" org-b-id "/add-user-by-email"))
                                   (mock/content-type "application/json")
                                   (mock/body (test-utils/->json user-data))
                                   (test-utils/token-header org-a-admin-token)))

          ;; Try to add user to org A (should succeed)
          resp-org-a (test-app (-> (mock/request :post (str "/api/orgs/" org-a-id "/add-user-by-email"))
                                   (mock/content-type "application/json")
                                   (mock/body (test-utils/->json user-data))
                                   (test-utils/token-header org-a-admin-token)))

          body-org-a (test-utils/safe-parse-json resp-org-a)]

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
          org-a-admin (test-utils/gen-org-admin-user org-a-id :db-component (test-db))
          org-a-admin-token (jwt/create-token org-a-admin)

          ;; Create a regular user
          target-user (test-utils/gen-regular-user :db-component (test-db))

          ;; Try to update users in org B (should fail)
          resp-org-b (test-app (-> (mock/request :post (str "/api/orgs/" org-b-id "/users"))
                                   (mock/content-type "application/json")
                                   (mock/body (test-utils/->json {:changes [{:user-id (:id target-user)
                                                                             :change "add"
                                                                             :role "org-user"}]}))
                                   (test-utils/token-header org-a-admin-token)))

          ;; Try to update users in org A (should succeed)
          resp-org-a (test-app (-> (mock/request :post (str "/api/orgs/" org-a-id "/users"))
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
          org-a-admin (test-utils/gen-org-admin-user org-a-id :db-component (test-db))
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
          resp-org-b (test-app (-> (mock/request :put (str "/api/orgs/" org-b-id))
                                   (mock/content-type "application/json")
                                   (mock/body (test-utils/->json updated-data))
                                   (test-utils/token-header org-a-admin-token)))

          ;; Try to update org A (should succeed)
          resp-org-a (test-app (-> (mock/request :put (str "/api/orgs/" org-a-id))
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
          org-a-admin (test-utils/gen-org-admin-user org-a-id :db-component (test-db))
          org-a-admin-token (jwt/create-token org-a-admin)

          ;; Try to view users in org B (should fail)
          resp-org-b (test-app (-> (mock/request :get (str "/api/orgs/" org-b-id "/users"))
                                   (test-utils/token-header org-a-admin-token)))

          ;; Try to view users in org A (should succeed)
          resp-org-a (test-app (-> (mock/request :get (str "/api/orgs/" org-a-id "/users"))
                                   (test-utils/token-header org-a-admin-token)))]

      ;; Assert org admin cannot view users in org B
      (is (= 403 (:status resp-org-b)) "Org admin should not be able to view users in another org")

      ;; Assert org admin can view users in org A
      (is (= 200 (:status resp-org-a)) "Org admin should be able to view users in their own org"))))

(deftest update-org-ptv-config-test
  (testing "LIPAS admin can update organization PTV configuration"
    (let [[org-a _org-b] (create-test-orgs)
          org-id (:id org-a)
          admin-user (test-utils/gen-admin-user :db-component (test-db))
          admin-token (jwt/create-token admin-user)

          ptv-config {:org-id #uuid "92374b0f-7d3c-4017-858e-666ee3ca2761"
                      :prod-org-id #uuid "d0a60c4c-89ff-4c09-a948-a2ecca780105"
                      :test-credentials {:username "API15@testi.fi"
                                         :password "APIinterfaceUser15-1015*"}
                      :city-codes [564]
                      :owners ["city" "city-main-owner"]
                      :supported-languages ["fi" "se" "en"]
                      :sync-enabled true}

          resp (test-app (-> (mock/request :put (str "/api/orgs/" org-id "/ptv-config"))
                             (mock/content-type "application/json")
                             (mock/body (test-utils/->json ptv-config))
                             (test-utils/token-header admin-token)))]

      (is (= 200 (:status resp)) "Admin should be able to update PTV config")
      ;; Verify the config was saved
      (let [all-orgs (backend-org/all-orgs (test-db))
            updated-org (->> all-orgs
                             (filter (fn [org] (= org-id (:id org))))
                             first)
            saved-config (:ptv-data updated-org)]
        (is (= (str (:org-id ptv-config)) (:org-id saved-config)))
        (is (= (str (:prod-org-id ptv-config)) (:prod-org-id saved-config)))
        (is (= (:test-credentials ptv-config) (:test-credentials saved-config)))
        (is (= (:city-codes ptv-config) (:city-codes saved-config)))
        (is (= (:owners ptv-config) (:owners saved-config)))
        (is (= (:supported-languages ptv-config) (:supported-languages saved-config)))
        (is (= (:sync-enabled ptv-config) (:sync-enabled saved-config))))))

  (testing "Non-admin cannot update organization PTV configuration"
    (let [[org-a _org-b] (create-test-orgs)
          org-id (:id org-a)
          org-admin (test-utils/gen-org-admin-user org-id :db-component (test-db))
          org-admin-token (jwt/create-token org-admin)
          regular-user (test-utils/gen-regular-user :db-component (test-db))
          regular-token (jwt/create-token regular-user)

          ptv-config {:org-id #uuid "7b83257d-06ad-4e3b-985d-16a5c9d3fced"
                      :city-codes [91]
                      :owners ["city"]
                      :supported-languages ["fi"]
                      :sync-enabled false}

          ;; Try with org admin
          resp-org-admin (test-app (-> (mock/request :put (str "/api/orgs/" org-id "/ptv-config"))
                                       (mock/content-type "application/json")
                                       (mock/body (test-utils/->json ptv-config))
                                       (test-utils/token-header org-admin-token)))

          ;; Try with regular user
          resp-regular (test-app (-> (mock/request :put (str "/api/orgs/" org-id "/ptv-config"))
                                     (mock/content-type "application/json")
                                     (mock/body (test-utils/->json ptv-config))
                                     (test-utils/token-header regular-token)))]

      (is (= 403 (:status resp-org-admin)) "Org admin should not be able to update PTV config")
      (is (= 403 (:status resp-regular)) "Regular user should not be able to update PTV config")))

  (testing "Invalid PTV config data is rejected"
    (let [[org-a _org-b] (create-test-orgs)
          org-id (:id org-a)
          admin-user (test-utils/gen-admin-user :db-component (test-db))
          admin-token (jwt/create-token admin-user)

          ;; Invalid config - missing required fields
          invalid-config {:org-id "not-a-uuid" ; Should be UUID
                          :city-codes ["not-a-number"] ; Should be numbers
                          :owners ["invalid-owner-type"] ; Invalid enum value
                          :supported-languages ["xx"] ; Invalid language code
                          :sync-enabled "not-a-boolean"} ; Should be boolean

          resp (test-app (-> (mock/request :put (str "/api/orgs/" org-id "/ptv-config"))
                             (mock/content-type "application/json")
                             (mock/body (test-utils/->json invalid-config))
                             (test-utils/token-header admin-token)))]

      (is (= 400 (:status resp)) "Invalid config should be rejected"))))

(deftest current-user-orgs-test
  (testing "Admin users see all organizations"
    (let [[org-a org-b] (create-test-orgs)
          admin-user (test-utils/gen-admin-user :db-component (test-db))
          admin-token (jwt/create-token admin-user)
          resp (test-app (-> (mock/request :get "/api/current-user-orgs")
                             (test-utils/token-header admin-token)))
          body (test-utils/safe-parse-json resp)]
      (is (= 200 (:status resp)))
      (is (coll? body))
      (is (>= (count body) 2) "Admin should see all organizations")
      ;; Verify both test orgs are in the response (compare string UUIDs)
      (is (some #(= (str (:id org-a)) (str (:id %))) body))
      (is (some #(= (str (:id org-b)) (str (:id %))) body))))

  (testing "PTV auditors see all organizations"
    (let [[org-a org-b] (create-test-orgs)
          ;; Create user with ptv-auditor role (not namespaced keyword)
          auditor-user (test-utils/gen-user {:db? true
                                             :db-component (test-db)
                                             :admin? false
                                             :permissions {:roles [{:role :ptv-auditor}]}})
          auditor-token (jwt/create-token auditor-user)
          resp (test-app (-> (mock/request :get "/api/current-user-orgs")
                             (test-utils/token-header auditor-token)))
          body (test-utils/safe-parse-json resp)]
      (is (= 200 (:status resp)))
      (is (coll? body))
      (is (>= (count body) 2) "Auditor should see all organizations")
      ;; Verify both test orgs are in the response (compare string UUIDs)
      (is (some #(= (str (:id org-a)) (str (:id %))) body))
      (is (some #(= (str (:id org-b)) (str (:id %))) body))))

  (testing "Regular users see only their assigned organizations"
    (let [[org-a org-b] (create-test-orgs)
          org-a-id (:id org-a)
          ;; Create user that's a member of org-a only
          org-member (test-utils/gen-org-admin-user org-a-id :db-component (test-db))
          member-token (jwt/create-token org-member)
          resp (test-app (-> (mock/request :get "/api/current-user-orgs")
                             (test-utils/token-header member-token)))
          body (test-utils/safe-parse-json resp)]
      (is (= 200 (:status resp)))
      (is (coll? body))
      ;; Should only see org-a, not org-b (compare string UUIDs)
      (is (some #(= (str org-a-id) (str (:id %))) body) "Should see org-a")
      (is (not (some #(= (str (:id org-b)) (str (:id %))) body)) "Should NOT see org-b")))

  (testing "Users with no org memberships see empty list"
    (let [_orgs (create-test-orgs)
          regular-user (test-utils/gen-regular-user :db-component (test-db))
          regular-token (jwt/create-token regular-user)
          resp (test-app (-> (mock/request :get "/api/current-user-orgs")
                             (test-utils/token-header regular-token)))
          body (test-utils/safe-parse-json resp)]
      (is (= 200 (:status resp)))
      (is (coll? body))
      (is (empty? body) "Regular user with no org memberships should see empty list"))))

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
  (clojure.test/run-test-var #'create-org-success-test)
  (clojure.test/run-test-var #'update-org-ptv-config-test))
