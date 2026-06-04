(ns lipas.backend.org-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [integrant.core :as ig]
            [lipas.backend.config :as config]
            [lipas.backend.core :as core]
            [lipas.backend.jwt :as jwt]
            [lipas.backend.org :as backend-org]
            [lipas.backend.org-takeover :as org-takeover]
            [lipas.test-utils :as test-utils]
            [lipas.utils :as utils]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [ring.mock.request :as mock]))

;;; Test system setup ;;;

(defonce test-system (atom nil))

;;; Fixtures ;;;

(let [{:keys [once each]} (test-utils/full-system-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

;;; Helper Functions ;;;

(defn test-db [] (:lipas/db @test-system))
(defn test-search [] (:lipas/search @test-system))
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

      ;; Membership now lives in the org document: get-org-users returns member
      ;; accounts augmented with their :org-role / :templates (not account roles).
      (is (some (fn [user]
                  (and (= (str (:id target-user)) (str (:id user)))
                       (= "member" (:org-role user))))
                body)
          "The added user should be returned as a 'member' of this organization"))))

(deftest remove-user-from-org-test
  (testing "update-org-users! 'remove' actually drops the matching role entry"
    (let [target-user (test-utils/gen-regular-user :db-component (test-db))
          [org1 _] (create-test-orgs)
          org-id (:id org1)
          db (test-db)

          _ (backend-org/update-org-users! db org-id
                                           [{:user-id (:id target-user)
                                             :change "add"
                                             :role "org-user"}])
          users-after-add (backend-org/get-org-users db org-id)

          _ (backend-org/update-org-users! db org-id
                                           [{:user-id (:id target-user)
                                             :change "remove"
                                             :role "org-user"}])
          users-after-remove (backend-org/get-org-users db org-id)

          target-id (:id target-user)
          present? (fn [users]
                     (some #(= target-id (:id %)) users))]
      (is (present? users-after-add)
          "Target user should appear in org members after add")
      (is (not (present? users-after-remove))
          "Target user should be gone from org members after remove"))))

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

;;; Org-admin invite: permission-bound safety tests (org-management §3.5/§10) ;;;
;;; These guarantee an org-admin can only ever hand out authority the
;;; lipas-admin defined in the catalog, scoped to their own org.

(defn- catalog-org!
  "Create an org with a role-template catalog and return its id."
  [catalog]
  (let [org (first (create-test-orgs))]
    (backend-org/update-catalog! (test-db) (:id org) catalog nil)
    (:id org)))

(def ^:private editor+ptv-catalog
  {:editor {:label "Muokkaaja" :roles [{:role "org-editor"}]}
   :ptv    {:label "PTV"       :roles [{:role "ptv-manager" :city-code [91]}]}})

(deftest org-admin-invite-catalog-bound-test
  (testing "An assignment within the catalog is accepted"
    (let [org-id (catalog-org! editor+ptv-catalog)
          user   (test-utils/gen-regular-user :db-component (test-db))
          _      (core/invite-org-member! (test-db) (test-utils/create-test-emailer) org-id
                                          {:email (:email user) :org-role "member" :templates ["editor"]}
                                          nil "http://login")
          member (->> (backend-org/get-org-users (test-db) org-id)
                      (filter #(= (str (:id user)) (str (:id %)))) first)]
      (is (= "member" (:org-role member)))
      (is (= ["editor"] (:templates member)))))

  (testing "An out-of-catalog template is rejected"
    (let [org-id (catalog-org! editor+ptv-catalog)
          user   (test-utils/gen-regular-user :db-component (test-db))
          ex     (try (core/invite-org-member! (test-db) (test-utils/create-test-emailer) org-id
                                               {:email (:email user) :templates ["editor" "not-in-catalog"]}
                                               nil "http://login")
                      :no-throw
                      (catch Exception e (:type (ex-data e))))]
      (is (= :templates-outside-catalog ex))
      (is (empty? (backend-org/get-org-users (test-db) org-id))
          "No member should have been added when the assignment is rejected")))

  (testing "Even a forged out-of-catalog assignment yields no derived role (structural ceiling)"
    (let [org-id (catalog-org! editor+ptv-catalog)
          ;; Project an assignment that names a template NOT in the catalog.
          roles  (backend-org/derive-org-roles
                  "u1"
                  [{:id org-id
                    :role-templates editor+ptv-catalog
                    :members [{:user-id "u1" :org-role "member" :templates ["forged" "editor"]}]}])
          role-ks (set (map :role roles))]
      ;; "editor" expands; "forged" vanishes. No :users/manage or other escalation.
      (is (contains? role-ks "org-editor"))
      (is (contains? role-ks "org-user"))
      (is (not-any? #{"admin" "users-manage" "forged"} role-ks)))))

(deftest org-admin-cannot-invite-into-other-org-test
  (testing "An org-admin of org A cannot invite into org B (endpoint authz)"
    (let [org-a   (catalog-org! editor+ptv-catalog)
          org-b   (catalog-org! editor+ptv-catalog)
          a-admin (test-utils/gen-org-admin-user org-a :db-component (test-db))
          token   (jwt/create-token a-admin)
          invitee (test-utils/gen-regular-user :db-component (test-db))
          resp    (test-app (-> (mock/request :post (str "/api/orgs/" org-b "/invite"))
                                (mock/json-body {:email (:email invitee)
                                                 :org-role "member"
                                                 :templates ["editor"]
                                                 :login-url "https://localhost/login"})
                                (test-utils/token-header token)))]
      (is (= 403 (:status resp)) "Cross-org invite must be forbidden")
      (is (empty? (backend-org/get-org-users (test-db) org-b))
          "No member added to the foreign org"))))

(deftest invited-member-derived-roles-exact-test
  (testing "A member's derived roles are exactly their catalog templates — no escalation, no foreign org"
    (let [org-id  (catalog-org! editor+ptv-catalog)
          other   (catalog-org! editor+ptv-catalog)
          user    (test-utils/gen-regular-user :db-component (test-db))
          _       (core/invite-org-member! (test-db) (test-utils/create-test-emailer) org-id
                                           {:email (:email user) :org-role "member" :templates ["editor"]}
                                           nil "http://login")
          derived (backend-org/derive-user-org-roles (test-db) (:id user))
          role-ks (set (map :role derived))
          org-ids (set (mapcat #(map str (:org-id %)) (filter :org-id derived)))]
      (is (= #{"org-user" "org-editor"} role-ks)
          "Exactly the membership role + the editor template role")
      (is (not (contains? role-ks "users-manage")))
      (is (= #{(str org-id)} org-ids) "All org-scoped roles are scoped to this org only")
      (is (not (contains? org-ids (str other))) "No roles leak to a foreign org"))))

(deftest org-admin-invite-new-account-test
  (testing "Inviting an unknown email creates an active account and sends the invitation email"
    (let [org-id  (catalog-org! editor+ptv-catalog)
          emailer (test-utils/create-test-emailer)
          email   (str "invitee-" (System/currentTimeMillis) "@example.com")
          result  (core/invite-org-member! (test-db) emailer org-id
                                           {:email email :org-role "member" :templates ["editor"]}
                                           nil "http://localhost/login")
          account (core/get-user (test-db) email)
          sent    @(:sent-emails emailer)]
      (is (:new-account? result))
      (is (some? account) "A new account is created for the invitee")
      (is (= "active" (:status account)))
      (is (= 1 (count sent)) "Exactly one invitation email is sent")
      (is (= email (:to (first sent))))
      ;; the new account is a member with the assigned template
      (is (= ["editor"] (->> (backend-org/get-org-users (test-db) org-id)
                             (filter #(= (str (:id account)) (str (:id %)))) first :templates))))))

(deftest org-takeover-approve-test
  (testing "Approving a take-over claims the matching sites via append-only revisions"
    (let [admin   (test-utils/gen-admin-user :db-component (test-db))
          org     (first (create-test-orgs))
          org-id  (:id org)
          ;; org owns city 91, ownertype "city"
          _       (backend-org/update-org! (test-db) org-id
                                           (assoc (backend-org/get-org (test-db) org-id)
                                                  :type "city"
                                                  :ownership {:city-codes [91] :owners ["city"]})
                                           nil)
          ;; a matching site (city 91, owner "city") and a non-matching one (owner "state")
          match-id    9990001
          nomatch-id  9990002
          mk-site (fn [lid owner]
                    (-> (test-utils/gen-sports-site)
                        (assoc :event-date (utils/timestamp) :status "active" :lipas-id lid :owner owner)
                        (assoc-in [:location :city :city-code] 91)))
          _ (core/upsert-sports-site!* (test-db) admin (mk-site match-id "city"))
          _ (core/upsert-sports-site!* (test-db) admin (mk-site nomatch-id "state"))
          ;; request + approve
          req     (org-takeover/create-request! (test-db) org-id (:id admin))
          result  (org-takeover/approve! (test-db) (test-search) (:id req) admin)
          claimed (core/get-sports-site (test-db) match-id)
          untouched (core/get-sports-site (test-db) nomatch-id)]
      (is (= "approved" (:status result)))
      (is (pos? (:sites-claimed result)))
      (is (= (str org-id) (str (:owner-org-id claimed))) "Matching site is now owned by the org")
      (is (= "city" (:owner claimed)) "Owner enum locked to the org type's owner")
      ;; acting_org_id is persisted on the revision (audit), read from the table
      ;; directly (it is intentionally not surfaced via the current-snapshot view)
      (is (= (str org-id)
             (str (:acting-org-id
                   (jdbc/execute-one! (test-db)
                                      ["SELECT acting_org_id FROM sports_site
                                        WHERE lipas_id=? ORDER BY event_date DESC, created_at DESC LIMIT 1"
                                       match-id]
                                      {:builder-fn rs/as-unqualified-kebab-maps}))))
          "Revision records the acting org on behalf of which the edit was made")
      (is (nil? (:owner-org-id untouched)) "Non-matching site (owner 'state') is untouched")
      ;; request is marked decided
      (is (= "approved" (:status (org-takeover/get-request (test-db) (:id req))))))))

;;; Phase C — dashboard read endpoints (org-sites, site-editors, history) ;;;

(deftest org-history-test
  (testing "get-history returns append-only revisions with computed diffs"
    (let [org     (first (create-test-orgs))
          org-id  (:id org)
          _       (backend-org/update-org! (test-db) org-id
                                           (assoc (backend-org/get-org (test-db) org-id)
                                                  :name "Renamed Org")
                                           nil)
          history (backend-org/get-history (test-db) org-id)]
      (is (>= (count history) 2) "Create + rename ⇒ at least two revisions")
      (is (= "Renamed Org" (:name (backend-org/get-org (test-db) org-id))))
      (is (some (fn [rev] (some #(re-find #"Nimi" %) (:changes rev))) history)
          "A revision records the name change")
      (is (some (fn [rev] (some #(re-find #"luotu" %) (:changes rev))) history)
          "The first revision is recorded as creation"))))

(deftest org-sites-test
  (testing "org-sites returns owned vs editable sites via ES term filters"
    (let [admin        (test-utils/gen-admin-user :db-component (test-db))
          org-id       (:id (first (create-test-orgs)))
          owned-id     9991001
          granted-id   9991002
          unrelated-id 9991003
          mk           (fn [lid extra]
                         (-> (test-utils/gen-sports-site)
                             (assoc :event-date (utils/timestamp) :status "active" :lipas-id lid)
                             (merge extra)))
          sites        [(mk owned-id {:owner-org-id (str org-id)})
                        (mk granted-id {:owner-org-id (str (java.util.UUID/randomUUID))
                                        :edit-grants  [(str org-id)]})
                        (mk unrelated-id {})]]
      (doseq [s sites]
        (core/upsert-sports-site!* (test-db) admin s)
        (core/index! (test-search) s true))
      (let [owned-ids    (set (map :lipas-id (:sites (core/org-sites (test-search) org-id "owned"))))
            editable-ids (set (map :lipas-id (:sites (core/org-sites (test-search) org-id "editable"))))]
        (is (contains? owned-ids owned-id) "Owned filter returns the owned site")
        (is (not (contains? owned-ids granted-id)) "Owned filter excludes the merely-granted site")
        (is (contains? editable-ids owned-id) "Editable includes owned")
        (is (contains? editable-ids granted-id) "Editable includes granted")
        (is (not (contains? editable-ids unrelated-id)) "Unrelated site excluded from both")))))

(deftest site-editors-test
  (testing "site-editors composes owner + grantee + activity-editor orgs"
    (let [admin                (test-utils/gen-admin-user :db-component (test-db))
          [owner-org grantee-org] (create-test-orgs)
          owner-id             (:id owner-org)
          grantee-id           (:id grantee-org)
          _                    (backend-org/update-catalog!
                                (test-db) grantee-id
                                {:melonta {:label "Melonta"
                                           :roles [{:role "activities-manager" :activity ["melonta"]}]}}
                                nil)
          lid                  9992001
          site                 (-> (test-utils/gen-sports-site)
                                   (assoc :event-date (utils/timestamp) :status "active" :lipas-id lid
                                          :owner-org-id (str owner-id)
                                          :edit-grants  [(str grantee-id)]
                                          :activities   {:melonta {}}))
          _                    (core/upsert-sports-site!* (test-db) admin site)
          editors              (core/site-editors (test-db) lid)]
      (is (= (str owner-id) (:id (:owner-org editors))) "Owner org resolved")
      (is (contains? (set (map :id (:grantee-orgs editors))) (str grantee-id)) "Grantee org listed")
      (is (contains? (set (map :id (:activity-editor-orgs editors))) (str grantee-id))
          "Org whose catalog grants the site's activity (melonta) is listed"))))

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
