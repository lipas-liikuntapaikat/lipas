(ns lipas.backend.org-test
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [integrant.core :as ig]
            [lipas.backend.config :as config]
            [lipas.backend.core :as core]
            [lipas.backend.jwt :as jwt]
            [lipas.backend.org :as backend-org]
            [lipas.backend.org-takeover :as org-takeover]
            [lipas.roles :as roles]
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
          resp (test-app (-> (mock/request :post "/api/actions/create-org")
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
          resp (test-app (-> (mock/request :post "/api/actions/create-org")
                             (mock/content-type "application/json")
                             (mock/body (test-utils/->json invalid-org))
                             (test-utils/token-header token)))]
      (is (= 400 (:status resp))))))

(deftest lipas-admin-can-view-org-users-test
  (testing "LIPAS admin can view users of any organization"
    (let [[org1 _] (create-test-orgs)
          org-id (:id org1)
          ;; LIPAS admin - not a member of this org
          admin-user (test-utils/gen-admin-user :db-component (test-db))
          token (jwt/create-token admin-user)
          resp (test-app (-> (mock/request :post "/api/actions/get-org-members")
                             (mock/content-type "application/json")
                             (mock/body (test-utils/->json {:org-id org-id}))
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

          resp (test-app (-> (mock/request :post "/api/actions/get-org-members")
                             (mock/content-type "application/json")
                             (mock/body (test-utils/->json {:org-id org-id}))
                             (test-utils/token-header token)))
          body (test-utils/safe-parse-json resp)]
      (is (= 200 (:status resp)))
      (is (coll? body))

      ;; Membership now lives in the org document: get-org-users returns member
      ;; accounts augmented with their :roles (not account roles). A user added
      ;; via the legacy "org-user" path is a plain member (:roles []).
      (is (some (fn [user]
                  (and (= (str (:id target-user)) (str (:id user)))
                       (= [] (:roles user))))
                body)
          "The added user should be returned as a plain member of this organization"))))

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

          ;; Try to update org B (should fail) — org identity travels in :id
          resp-org-b (test-app (-> (mock/request :post "/api/actions/update-org")
                                   (mock/content-type "application/json")
                                   (mock/body (test-utils/->json updated-data))
                                   (test-utils/token-header org-a-admin-token)))

          ;; Try to update org A (should succeed)
          resp-org-a (test-app (-> (mock/request :post "/api/actions/update-org")
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
          resp-org-b (test-app (-> (mock/request :post "/api/actions/get-org-members")
                                   (mock/content-type "application/json")
                                   (mock/body (test-utils/->json {:org-id org-b-id}))
                                   (test-utils/token-header org-a-admin-token)))

          ;; Try to view users in org A (should succeed)
          resp-org-a (test-app (-> (mock/request :post "/api/actions/get-org-members")
                                   (mock/content-type "application/json")
                                   (mock/body (test-utils/->json {:org-id org-a-id}))
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

          resp (test-app (-> (mock/request :post "/api/actions/update-org-ptv-config")
                             (mock/content-type "application/json")
                             (mock/body (test-utils/->json {:org-id org-id :ptv-config ptv-config}))
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
          resp-org-admin (test-app (-> (mock/request :post "/api/actions/update-org-ptv-config")
                                       (mock/content-type "application/json")
                                       (mock/body (test-utils/->json {:org-id org-id :ptv-config ptv-config}))
                                       (test-utils/token-header org-admin-token)))

          ;; Try with regular user
          resp-regular (test-app (-> (mock/request :post "/api/actions/update-org-ptv-config")
                                     (mock/content-type "application/json")
                                     (mock/body (test-utils/->json {:org-id org-id :ptv-config ptv-config}))
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

          resp (test-app (-> (mock/request :post "/api/actions/update-org-ptv-config")
                             (mock/content-type "application/json")
                             (mock/body (test-utils/->json {:org-id org-id :ptv-config invalid-config}))
                             (test-utils/token-header admin-token)))]

      (is (= 400 (:status resp)) "Invalid config should be rejected"))))

(deftest current-user-orgs-test
  (testing "Admin users see all organizations"
    (let [[org-a org-b] (create-test-orgs)
          admin-user (test-utils/gen-admin-user :db-component (test-db))
          admin-token (jwt/create-token admin-user)
          resp (test-app (-> (mock/request :post "/api/actions/get-current-user-orgs")
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
          resp (test-app (-> (mock/request :post "/api/actions/get-current-user-orgs")
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
          resp (test-app (-> (mock/request :post "/api/actions/get-current-user-orgs")
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
          resp (test-app (-> (mock/request :post "/api/actions/get-current-user-orgs")
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

(deftest catalog-validation-test
  (testing "A valid catalog is stored"
    (let [org-id (catalog-org! editor+ptv-catalog)]
      (is (contains? (:role-templates (backend-org/get-org (test-db) org-id)) :editor))))

  (testing "An unknown role name is rejected (no revision written)"
    (let [org (first (create-test-orgs))
          ex  (try (backend-org/update-catalog! (test-db) (:id org)
                                                {:bad {:roles [{:role "super-admin"}]}} nil)
                   :no-throw
                   (catch Exception e (:type (ex-data e))))]
      (is (= :invalid-catalog ex))
      (is (not (contains? (:role-templates (backend-org/get-org (test-db) (:id org))) :bad))
          "The bogus catalog must not have been persisted")))

  (testing "A structurally malformed catalog is rejected"
    (let [org (first (create-test-orgs))
          ex  (try (backend-org/update-catalog! (test-db) (:id org)
                                                {:bad {:roles 5}} nil)
                   :no-throw
                   (catch Exception e (:type (ex-data e))))]
      (is (= :invalid-catalog ex))))

  (testing "A context-scoped role missing its required key is rejected (would project org-wide)"
    (let [org (first (create-test-orgs))
          ex  (try (backend-org/update-catalog! (test-db) (:id org)
                                                ;; ptv-manager requires :city-code; omitting it
                                                ;; would grant PTV management everywhere
                                                {:ptv {:roles [{:role "ptv-manager"}]}} nil)
                   :no-throw
                   (catch Exception e (:type (ex-data e))))]
      (is (= :invalid-catalog ex))))

  (testing "A present-but-empty context key is allowed (matches nothing; admin fills it later)"
    (let [org-id (catalog-org! {:ptv {:roles [{:role "ptv-manager" :city-code []}]}})]
      (is (contains? (:role-templates (backend-org/get-org (test-db) org-id)) :ptv)))))

;;; --- The ceiling is lipas-admin-only (authorization, HTTP edge) -------------
;;; These guard against accidentally opening a path for an org to elevate its
;;; own permission ceiling (the role-template catalog) or take-over ceiling
;;; (`:type`/`:ownership`). They hit the real routes through `test-app`, unlike
;;; the function-level catalog tests above which call `update-catalog!` directly.

(deftest only-lipas-admin-can-set-catalog-test
  (let [[org-a _]       (create-test-orgs)
        org-id          (:id org-a)
        admin-token     (jwt/create-token (test-utils/gen-admin-user :db-component (test-db)))
        org-admin-token (jwt/create-token (test-utils/gen-org-admin-user org-id :db-component (test-db)))
        regular-token   (jwt/create-token (test-utils/gen-regular-user :db-component (test-db)))
        catalog         {:editor {:label "Muokkaaja" :roles [{:role "org-editor"}]}}
        post            (fn [token]
                          ;; transit, like the FE — the catalog is a :map-of :keyword body
                          (test-app (-> (mock/request :post "/api/actions/update-org-role-templates")
                                        (mock/content-type "application/transit+json")
                                        (mock/header "Accept" "application/transit+json")
                                        (mock/body (test-utils/->transit {:org-id org-id :role-templates catalog}))
                                        (test-utils/token-header token))))]
    (testing "LIPAS admin can set the catalog (the ceiling)"
      (is (= 200 (:status (post admin-token))))
      (is (contains? (:role-templates (backend-org/get-org (test-db) org-id)) :editor)
          "Catalog set by the lipas-admin must persist"))

    (testing "Org admin cannot set the catalog"
      (is (= 403 (:status (post org-admin-token)))))

    (testing "Regular user cannot set the catalog"
      (is (= 403 (:status (post regular-token)))))))

(deftest check-is-existing-user-endpoint-test
  ;; The invite-form existence probe is org-scoped on purpose: you may only ask
  ;; within an org you administer, so it can't be used to harvest valid emails
  ;; or enumerate the user directory. It returns ONLY a boolean.
  (let [[org-a org-b]   (create-test-orgs)
        org-id          (:id org-a)
        target          (test-utils/gen-regular-user :db-component (test-db)) ; a known account
        known-email     (:email target)
        unknown-email   "nobody-zz@example.invalid"
        admin-token     (jwt/create-token (test-utils/gen-admin-user :db-component (test-db)))
        org-admin-token (jwt/create-token (test-utils/gen-org-admin-user org-id :db-component (test-db)))
        other-org-admin (jwt/create-token (test-utils/gen-org-admin-user (:id org-b) :db-component (test-db)))
        regular-token   (jwt/create-token (test-utils/gen-regular-user :db-component (test-db)))
        check           (fn [token email]
                          (test-app (-> (mock/request :post "/api/actions/check-is-existing-user")
                                        (mock/content-type "application/json")
                                        (mock/header "Accept" "application/json")
                                        (mock/body (test-utils/->json {:org-id org-id :email email}))
                                        (test-utils/token-header token))))]
    (testing "LIPAS admin gets an accurate boolean"
      (let [hit       (check admin-token known-email)
            hit-body  (test-utils/<-json (:body hit)) ; body is a single-use stream — decode once
            miss      (check admin-token unknown-email)
            miss-body (test-utils/<-json (:body miss))]
        (is (= 200 (:status hit)))
        (is (true? (:exists hit-body)))
        (is (= 200 (:status miss)))
        (is (false? (:exists miss-body)) "Unknown email must report not-existing")
        (is (= #{:exists} (set (keys hit-body)))
            "Response must leak nothing beyond the boolean")))

    (testing "Org admin may probe within their own org"
      (let [resp (check org-admin-token known-email)]
        (is (= 200 (:status resp)))
        (is (true? (:exists (test-utils/<-json (:body resp)))))))

    (testing "An admin of a DIFFERENT org cannot probe this org (no cross-org harvesting)"
      (is (= 403 (:status (check other-org-admin known-email)))))

    (testing "A non-member cannot probe at all"
      (is (= 403 (:status (check regular-token known-email)))))))

(deftest org-admin-cannot-change-type-or-ownership-test
  (testing "Org admin's :type/:ownership edits are ignored; lipas-admin's persist"
    (let [[org-a _]       (create-test-orgs)
          org-id          (:id org-a)
          ;; A lipas-admin first sets a real ownership rule + type — the ceiling.
          admin-token     (jwt/create-token (test-utils/gen-admin-user :db-component (test-db)))
          org-admin-token (jwt/create-token (test-utils/gen-org-admin-user org-id :db-component (test-db)))
          update-org      (fn [token body]
                            (test-app (-> (mock/request :post "/api/actions/update-org")
                                          (mock/content-type "application/json")
                                          (mock/body (test-utils/->json body))
                                          (test-utils/token-header token))))
          base            {:id org-id :name "Helsinki"}
          _               (update-org admin-token (assoc base
                                                         :type "city"
                                                         :ownership {:city-codes [91] :owners ["city"]}))
          before          (backend-org/get-org (test-db) org-id)

          ;; Org admin now tries to widen the ceiling while renaming the org.
          resp-org-admin  (update-org org-admin-token
                                      {:id org-id
                                       :name "Org-admin renamed this"
                                       :type "state"
                                       :ownership {:city-codes [999] :owners ["state"]}})
          after-org-admin (backend-org/get-org (test-db) org-id)]

      ;; The request succeeds and the allowed field (name) is written...
      (is (= 200 (:status resp-org-admin)))
      (is (= "Org-admin renamed this" (:name after-org-admin)))
      ;; ...but the ceiling fields are unchanged — the attempted values did NOT take.
      (is (= (:type before) (:type after-org-admin))
          "Org admin must not be able to change the org type")
      (is (not= "state" (:type after-org-admin)))
      (is (= (:ownership before) (:ownership after-org-admin))
          "Org admin must not be able to widen the ownership rule")
      (is (not= [999] (:city-codes (:ownership after-org-admin))))

      ;; A lipas-admin, by contrast, may change them.
      (let [resp-admin  (update-org admin-token {:id org-id
                                                 :name "Helsinki"
                                                 :type "state"
                                                 :ownership {:city-codes [49] :owners ["state"]}})
            after-admin (backend-org/get-org (test-db) org-id)]
        (is (= 200 (:status resp-admin)))
        (is (= "state" (:type after-admin)))
        (is (= [49] (:city-codes (:ownership after-admin))))
        (is (= ["state"] (:owners (:ownership after-admin))))))))

(deftest org-admin-invite-catalog-bound-test
  (testing "An assignment within the catalog is accepted"
    (let [org-id (catalog-org! editor+ptv-catalog)
          user   (test-utils/gen-regular-user :db-component (test-db))
          _      (core/invite-org-member! (test-db) (test-utils/create-test-emailer) org-id
                                          {:email (:email user) :roles ["editor"]}
                                          nil "http://login")
          member (->> (backend-org/get-org-users (test-db) org-id)
                      (filter #(= (str (:id user)) (str (:id %)))) first)]
      (is (= ["editor"] (:roles member)))))

  (testing "An out-of-catalog template is rejected"
    (let [org-id (catalog-org! editor+ptv-catalog)
          user   (test-utils/gen-regular-user :db-component (test-db))
          ex     (try (core/invite-org-member! (test-db) (test-utils/create-test-emailer) org-id
                                               {:email (:email user) :roles ["editor" "not-in-catalog"]}
                                               nil "http://login")
                      :no-throw
                      (catch Exception e (:type (ex-data e))))]
      (is (= :roles-outside-catalog ex))
      (is (empty? (backend-org/get-org-users (test-db) org-id))
          "No member should have been added when the assignment is rejected")))

  (testing "Even a forged out-of-catalog assignment yields no derived role (structural ceiling)"
    (let [org-id (catalog-org! editor+ptv-catalog)
          ;; Project an assignment that names a template NOT in the catalog.
          roles  (backend-org/derive-org-roles
                   "u1"
                   [{:id org-id
                     :role-templates editor+ptv-catalog
                     :members [{:user-id "u1" :roles ["forged" "editor"]}]}])
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
          resp    (test-app (-> (mock/request :post "/api/actions/invite-org-member")
                                (mock/json-body {:org-id org-b
                                                 :email (:email invitee)
                                                 :roles ["editor"]
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
                                           {:email (:email user) :roles ["editor"]}
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
                                           {:email email :roles ["editor"]}
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
                             (filter #(= (str (:id account)) (str (:id %)))) first :roles))))))

;;; --- Unified :roles model: baseline, reserved admin, ceiling, endpoint -------

(defn- privileged?
  "Does a member with `member-roles` (the document `:roles` list) hold
  `privilege` in `org-id`'s context once their roles are projected & conformed?"
  [org-id catalog member-roles privilege]
  (let [derived (backend-org/derive-org-roles
                  "u1" [{:id org-id :role-templates catalog
                         :members [{:user-id "u1" :roles member-roles}]}])
        user    {:permissions {:roles (roles/conform-roles derived)}}]
    (roles/check-privilege user {:org-id #{(str org-id)}} privilege)))

(deftest membership-baseline-test
  (testing "Any member — even with :roles [] — gets the :org/member baseline, not :org/manage"
    (let [org-id (catalog-org! editor+ptv-catalog)]
      (is (privileged? org-id editor+ptv-catalog [] :org/member)
          "An empty role list still views the org (membership ⟺ :org/member)")
      (is (not (privileged? org-id editor+ptv-catalog [] :org/manage))
          "A plain member cannot manage the org"))))

(deftest reserved-admin-grants-manage-test
  (testing "The reserved \"admin\" role projects org-admin (:org/manage + :org/member)"
    (let [org-id (catalog-org! editor+ptv-catalog)]
      (is (privileged? org-id editor+ptv-catalog ["admin"] :org/manage))
      (is (privileged? org-id editor+ptv-catalog ["admin"] :org/member)))))

(deftest roles-ceiling-test
  (testing "A role key in neither the reserved set nor the catalog expands to nothing"
    (let [org-id  (catalog-org! editor+ptv-catalog)
          derived (backend-org/derive-org-roles
                    "u1" [{:id org-id :role-templates editor+ptv-catalog
                           :members [{:user-id "u1" :roles ["bogus"]}]}])
          role-ks (set (map :role derived))]
      ;; only the baseline survives; "bogus" vanishes
      (is (= #{"org-user"} role-ks)))))

(deftest catalog-edit-cannot-strip-admin-test
  (testing "Wiping every data role from the catalog never removes a member's reserved :admin"
    (let [org-id  (catalog-org! editor+ptv-catalog)
          member  (test-utils/gen-regular-user :db-component (test-db))
          _       (backend-org/add-member! (test-db) org-id (:id member)
                                           {:roles ["admin" "editor"]} nil)
          ;; lipas-admin wholesale-replaces the catalog with an empty one
          _       (backend-org/update-catalog! (test-db) org-id {} nil)
          derived (backend-org/derive-user-org-roles (test-db) (:id member))
          role-ks (set (map :role derived))]
      (is (contains? role-ks "org-admin") "admin survives a catalog wipe")
      (is (not (contains? role-ks "org-editor")) "the data role (editor) is gone with the catalog")
      (is (contains? role-ks "org-user") "baseline still present"))))

(deftest set-org-member-roles-endpoint-test
  (testing "set-org-member-roles replaces a member's whole role list (org-admin only)"
    (let [org-id  (catalog-org! editor+ptv-catalog)
          admin   (test-utils/gen-org-admin-user org-id :db-component (test-db))
          token   (jwt/create-token admin)
          member  (test-utils/gen-regular-user :db-component (test-db))
          _       (backend-org/add-member! (test-db) org-id (:id member) {:roles ["editor"]} nil)
          call    (fn [roles]
                    (test-app (-> (mock/request :post "/api/actions/set-org-member-roles")
                                  (mock/json-body {:org-id org-id :user-id (:id member) :roles roles})
                                  (test-utils/token-header token))))
          roles-of (fn [] (->> (backend-org/get-org-users (test-db) org-id)
                               (filter #(= (str (:id member)) (str (:id %)))) first :roles))]
      (is (= 200 (:status (call ["admin" "ptv"]))))
      (is (= #{"admin" "ptv"} (set (roles-of))) "roles replaced wholesale")
      (testing "an out-of-catalog role is rejected (400) and leaves roles untouched"
        (is (= 400 (:status (call ["editor" "not-in-catalog"]))))
        (is (= #{"admin" "ptv"} (set (roles-of))))))))

(deftest invite-plain-member-and-first-admin-test
  (testing "Invite with :roles [] makes a plain member (only :org/member)"
    (let [org-id (catalog-org! editor+ptv-catalog)
          user   (test-utils/gen-regular-user :db-component (test-db))
          _      (core/invite-org-member! (test-db) (test-utils/create-test-emailer) org-id
                                          {:email (:email user) :roles []}
                                          nil "http://login")
          derived (backend-org/derive-user-org-roles (test-db) (:id user))]
      (is (= #{"org-user"} (set (map :role derived))))))
  (testing "Invite with :roles [\"admin\"] creates a first admin in one step"
    (let [org-id (catalog-org! editor+ptv-catalog)
          user   (test-utils/gen-regular-user :db-component (test-db))
          _      (core/invite-org-member! (test-db) (test-utils/create-test-emailer) org-id
                                          {:email (:email user) :roles ["admin"]}
                                          nil "http://login")
          derived (backend-org/derive-user-org-roles (test-db) (:id user))]
      (is (contains? (set (map :role derived)) "org-admin")))))

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
      (is (= "approved" (:status (org-takeover/get-request (test-db) (:id req)))))
      ;; re-approving the same (already-approved) request is rejected, not re-run
      (is (= :invalid-takeover-state
             (try (org-takeover/approve! (test-db) (test-search) (:id req) admin)
                  :no-throw
                  (catch Exception e (:type (ex-data e)))))
          "A decided request cannot be approved again")
      ;; ...and denying a decided request is likewise rejected
      (is (= :invalid-takeover-state
             (try (org-takeover/deny! (test-db) (:id req) admin)
                  :no-throw
                  (catch Exception e (:type (ex-data e)))))))))

(deftest reclaim-org-sites-endpoint-test
  (testing "LIPAS admin reclaims matching sites in one step (no approval queue)"
    (let [admin   (test-utils/gen-admin-user :db-component (test-db))
          token   (jwt/create-token admin)
          org     (first (create-test-orgs))
          org-id  (:id org)
          _       (backend-org/update-org! (test-db) org-id
                                           (assoc (backend-org/get-org (test-db) org-id)
                                                  :type "city"
                                                  :ownership {:city-codes [91] :owners ["city"]})
                                           nil)
          match-id 9995001
          mk-site (fn [lid owner]
                    (-> (test-utils/gen-sports-site)
                        (assoc :event-date (utils/timestamp) :status "active" :lipas-id lid :owner owner)
                        (assoc-in [:location :city :city-code] 91)))
          _       (core/upsert-sports-site!* (test-db) admin (mk-site match-id "city"))
          resp    (test-app (-> (mock/request :post "/api/actions/reclaim-org-sites")
                                (mock/json-body {:org-id org-id})
                                (test-utils/token-header token)))
          body    (test-utils/safe-parse-json resp)
          claimed (core/get-sports-site (test-db) match-id)]
      (is (= 200 (:status resp)))
      (is (= "approved" (:status body)))
      (is (pos? (:sites-claimed body)) "At least the matching site is claimed")
      (is (= (str org-id) (str (:owner-org-id claimed))) "Matching site is owned by the org")))

  (testing "A non-lipas-admin cannot reclaim (403)"
    (let [org     (first (create-test-orgs))
          org-id  (:id org)
          ;; org-admin has :org/manage but not :org/admin (lipas-admin)
          oa      (test-utils/gen-org-admin-user org-id :db-component (test-db))
          token   (jwt/create-token oa)
          resp    (test-app (-> (mock/request :post "/api/actions/reclaim-org-sites")
                                (mock/json-body {:org-id org-id})
                                (test-utils/token-header token)))]
      (is (= 403 (:status resp)) "Direct reclaim is lipas-admin only"))))

(deftest reclaim-idempotent-test
  (testing "Re-running a reclaim only claims not-yet-owned sites (no redundant revisions)"
    (let [admin    (test-utils/gen-admin-user :db-component (test-db))
          org      (first (create-test-orgs))
          org-id   (:id org)
          _        (backend-org/update-org! (test-db) org-id
                                            (assoc (backend-org/get-org (test-db) org-id)
                                                   :type "city"
                                                   :ownership {:city-codes [91] :owners ["city"]})
                                            nil)
          lid      9996001
          _        (core/upsert-sports-site!* (test-db) admin
                                              (-> (test-utils/gen-sports-site)
                                                  (assoc :event-date (utils/timestamp) :status "active"
                                                         :lipas-id lid :owner "city")
                                                  (assoc-in [:location :city :city-code] 91)))
          rev-count (fn [] (:n (jdbc/execute-one! (test-db)
                                                  ["SELECT count(*) AS n FROM sports_site WHERE lipas_id=?" lid]
                                                  {:builder-fn rs/as-unqualified-kebab-maps})))
          ;; first reclaim
          r1       (org-takeover/reclaim-now! (test-db) (test-search) org-id admin)
          revs-after-1 (rev-count)
          ;; second reclaim — nothing left to claim
          preview2 (org-takeover/preview (test-db) org-id)
          r2       (org-takeover/reclaim-now! (test-db) (test-search) org-id admin)
          revs-after-2 (rev-count)]
      (is (pos? (:sites-claimed r1)) "First run claims the matching site")
      (is (zero? (:count preview2)) "Preview shows nothing left to claim after a full claim")
      (is (empty? (:sites preview2)))
      (is (zero? (:sites-claimed r2)) "Second run claims nothing (already owned)")
      (is (= revs-after-1 revs-after-2)
          "No new revision is written for the already-claimed site on re-run")
      (is (= (str org-id) (str (:owner-org-id (core/get-sports-site (test-db) lid))))
          "Site remains owned by the org"))))

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

(deftest org-history-resolves-member-emails-test
  (testing "Member user-ids in the audit diff resolve to emails, not raw UUIDs"
    (let [org-id   (catalog-org! editor+ptv-catalog)
          member   (test-utils/gen-regular-user :db-component (test-db))
          _        (backend-org/add-member! (test-db) org-id (:id member)
                                            {:roles ["editor"]} nil)
          add-line (->> (backend-org/get-history (test-db) org-id)
                        (mapcat :changes)
                        (filter #(re-find #"Jäsen lisätty" %))
                        first)]
      (is (some? add-line) "An 'added member' line exists")
      (is (clojure.string/includes? add-line (:email member))
          "The added-member line shows the email")
      (is (not (clojure.string/includes? add-line (str (:id member))))
          "The raw user-id UUID must not appear"))))

(deftest org-history-authz-test
  (testing "History endpoint is admin-only (lipas-admin or org-admin), not members"
    (let [org-id      (catalog-org! editor+ptv-catalog)
          admin-token (jwt/create-token (test-utils/gen-admin-user :db-component (test-db)))
          oadmin-tok  (jwt/create-token (test-utils/gen-org-admin-user org-id :db-component (test-db)))
          member-tok  (jwt/create-token (test-utils/gen-org-user org-id :db-component (test-db)))
          regular-tok (jwt/create-token (test-utils/gen-regular-user :db-component (test-db)))
          call        (fn [token]
                        (test-app (-> (mock/request :post "/api/actions/get-org-history")
                                      (mock/content-type "application/json")
                                      (mock/body (test-utils/->json {:org-id org-id}))
                                      (test-utils/token-header token))))]
      (is (= 200 (:status (call admin-token))) "LIPAS admin may see history")
      (is (= 200 (:status (call oadmin-tok))) "Org admin may see their org's history")
      (is (= 403 (:status (call member-tok))) "Plain member may not see history")
      (is (= 403 (:status (call regular-tok))) "Non-member may not see history"))))

;;; --- Org instructions (org-admin writes localized member guidance) ----------

(deftest org-instructions-test
  (testing "Instructions round-trip and survive a details update without wiping members"
    (let [org-id (catalog-org! editor+ptv-catalog)
          member (test-utils/gen-regular-user :db-component (test-db))
          _      (backend-org/add-member! (test-db) org-id (:id member)
                                          {:roles ["editor"]} nil)
          _      (backend-org/update-org! (test-db) org-id
                                          (assoc (backend-org/get-org (test-db) org-id)
                                                 :instructions {:fi "Ohje" :en "Guide" :se "Anvisning"})
                                          nil)
          o      (backend-org/get-org (test-db) org-id)]
      (is (= {:fi "Ohje" :en "Guide" :se "Anvisning"} (:instructions o)))
      (is (= 1 (count (:members o))) "Members preserved through a details update"))))

(deftest org-admin-can-set-instructions-test
  (testing "An org-admin may set instructions via the update-org route (not pinned like type/ownership)"
    (let [org-id (catalog-org! editor+ptv-catalog)
          oadmin (jwt/create-token (test-utils/gen-org-admin-user org-id :db-component (test-db)))
          resp   (test-app (-> (mock/request :post "/api/actions/update-org")
                               (mock/content-type "application/json")
                               (mock/body (test-utils/->json
                                            (assoc (backend-org/get-org (test-db) org-id)
                                                   :instructions {:fi "Jäsenohje"})))
                               (test-utils/token-header oadmin)))]
      (is (= 200 (:status resp)))
      (is (= "Jäsenohje" (-> (backend-org/get-org (test-db) org-id) :instructions :fi))))))

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

(deftest org-owned-site-counts-test
  (testing "owned-site counts: one ES terms agg, keyed by org-id, granted sites excluded"
    (let [admin      (test-utils/gen-admin-user :db-component (test-db))
          [org-a org-b] (create-test-orgs)
          org-a-id   (:id org-a)
          org-b-id   (:id org-b)
          mk         (fn [lid extra]
                       (-> (test-utils/gen-sports-site)
                           (assoc :event-date (utils/timestamp) :status "active" :lipas-id lid)
                           (merge extra)))
          sites      [(mk 9993001 {:owner-org-id (str org-a-id)})
                      (mk 9993002 {:owner-org-id (str org-a-id)})
                      ;; merely granted to org-a — NOT owned, must not be counted
                      (mk 9993003 {:owner-org-id (str (java.util.UUID/randomUUID))
                                   :edit-grants  [(str org-a-id)]})]]
      (doseq [s sites]
        (core/upsert-sports-site!* (test-db) admin s)
        (core/index! (test-search) s true))
      (let [counts (core/org-owned-site-counts (test-search))]
        (is (= 2 (get counts (str org-a-id))) "org-a owns exactly its two owned sites (grant excluded)")
        (is (nil? (get counts (str org-b-id))) "org-b owns nothing → absent from the agg buckets")
        (testing "matches org-sites :total (single source of truth)"
          (is (= (:total (core/org-sites (test-search) org-a-id "owned"))
                 (get counts (str org-a-id)))))))))

(deftest current-user-orgs-site-count-test
  (testing "get-current-user-orgs annotates each org with :site-count (0 when none owned)"
    (let [admin      (test-utils/gen-admin-user :db-component (test-db))
          admin-tok  (jwt/create-token admin)
          [org-a org-b] (create-test-orgs)
          org-a-id   (:id org-a)
          mk         (fn [lid extra]
                       (-> (test-utils/gen-sports-site)
                           (assoc :event-date (utils/timestamp) :status "active" :lipas-id lid)
                           (merge extra)))
          sites      [(mk 9994001 {:owner-org-id (str org-a-id)})
                      (mk 9994002 {:owner-org-id (str org-a-id)})]]
      (doseq [s sites]
        (core/upsert-sports-site!* (test-db) admin s)
        (core/index! (test-search) s true))
      (let [resp (test-app (-> (mock/request :post "/api/actions/get-current-user-orgs")
                               (test-utils/token-header admin-tok)))
            body (test-utils/safe-parse-json resp)
            by-id (into {} (map (juxt #(str (:id %)) identity)) body)]
        (is (= 200 (:status resp)))
        (is (= 2 (:site-count (get by-id (str org-a-id)))) "org-a reports its two owned sites")
        (is (= 0 (:site-count (get by-id (str (:id org-b)))))
            "org-b owns nothing → :site-count defaults to 0, never nil")))))

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

(deftest site-editors-legacy-users-test
  (testing "Legacy direct-permission users are found; admins excluded; result == naive scan"
    (let [admin  (test-utils/gen-admin-user :db-component (test-db))
          city   837
          lid    9992050
          site   (-> (test-utils/gen-sports-site)
                     (assoc :event-date (utils/timestamp) :status "active" :lipas-id lid)
                     (assoc-in [:location :city :city-code] city)
                     (assoc-in [:type :type-code] 1530))
          _      (core/upsert-sports-site!* (test-db) admin site)
          editor (test-utils/gen-city-manager-user city :db-component (test-db)) ; direct edit role for the city
          other  (test-utils/gen-city-manager-user 91 :db-component (test-db))   ; different city
          admin2 (test-utils/gen-admin-user :db-component (test-db))             ; admin (must be excluded)
          emails (set (map :email (:legacy-users (core/site-editors (test-db) lid))))
          ;; exhaustive oracle: scan every active user with the exact matcher
          rc     (roles/site-roles-context (core/get-sports-site (test-db) lid))
          naive  (->> (core/get-users (test-db))
                      (filter (fn [u] (and (= "active" (:status u))
                                           (not (roles/check-role u :admin))
                                           (roles/check-privilege u rc :site/create-edit))))
                      (map :email) set)]
      (is (contains? emails (:email editor)) "Direct city-manager for the site's city is listed")
      (is (not (contains? emails (:email other))) "User scoped to a different city is not listed")
      (is (not (contains? emails (:email admin2))) "Admins are excluded (they can edit everything)")
      (is (= emails naive) "Candidate pre-filter matches the exhaustive naive scan exactly"))))

;;; Generalized ownership/claim rule (city / owners / type-codes / activities) ;;;

(deftest matching-lipas-ids-axes-test
  (testing "matching-lipas-ids supports each axis and AND-combines them"
    (let [admin (test-utils/gen-admin-user :db-component (test-db))
          mk    (fn [lid {:keys [city type owner activities]}]
                  (cond-> (-> (test-utils/gen-sports-site)
                              (assoc :event-date (utils/timestamp) :status "active"
                                     :lipas-id lid :owner owner)
                              (assoc-in [:location :city :city-code] city)
                              (assoc-in [:type :type-code] type))
                    activities (assoc :activities activities)))
          ;; cycling route (4411) in city 91, owner city, with cycling activity
          a 9993001
          ;; ice stadium (2520) in city 91, owner state
          b 9993002
          ;; cycling route (4412) in city 49, owner city
          c 9993003]
      (core/upsert-sports-site!* (test-db) admin (mk a {:city 91 :type 4411 :owner "city" :activities {:cycling {}}}))
      (core/upsert-sports-site!* (test-db) admin (mk b {:city 91 :type 2520 :owner "state"}))
      (core/upsert-sports-site!* (test-db) admin (mk c {:city 49 :type 4412 :owner "city"}))
      (let [ids (fn [rule] (set (org-takeover/matching-lipas-ids (test-db) rule)))]
        (is (= #{a b} (set/intersection #{a b c} (ids {:city-codes [91]})))
            "city axis: both city-91 sites")
        (is (contains? (ids {:type-codes [4411 4412]}) a) "type axis includes cycling route a")
        (is (contains? (ids {:type-codes [4411 4412]}) c) "type axis includes cycling route c")
        (is (not (contains? (ids {:type-codes [4411 4412]}) b)) "type axis excludes ice stadium")
        (is (contains? (ids {:activities ["cycling"]}) a) "activity axis includes the cycling site")
        (is (not (contains? (ids {:activities ["cycling"]}) c)) "activity axis excludes route without activity")
        (is (= #{a} (set/intersection #{a b c} (ids {:type-codes [4411 4412] :city-codes [91]})))
            "AND of type+city narrows to a only")
        (is (nil? (org-takeover/matching-lipas-ids (test-db) {})) "empty rule matches nothing")))))

(deftest takeover-preview-test
  (testing "preview reports count, owner-enum + org name, and the full site list"
    (let [admin   (test-utils/gen-admin-user :db-component (test-db))
          org     (first (create-test-orgs))
          org-id  (:id org)
          _       (backend-org/update-org! (test-db) org-id
                                           (assoc (backend-org/get-org (test-db) org-id)
                                                  :type "sports-federation"
                                                  :ownership {:type-codes [4411]})
                                           nil)
          lid     9994001
          _       (core/upsert-sports-site!* (test-db) admin
                                             (-> (test-utils/gen-sports-site)
                                                 (assoc :event-date (utils/timestamp) :status "active"
                                                        :lipas-id lid :owner "city")
                                                 (assoc-in [:type :type-code] 4411)))
          p       (org-takeover/preview (test-db) org-id)]
      (is (pos? (:count p)) "matches at least the seeded cycling route")
      (is (= "registered-association" (:owner-enum p))
          "owner-enum derives from the org type (sports-federation)")
      (is (= (:name org) (:owner-org-name p)) "preview carries the target org name")
      (is (= (str org-id) (:owner-org-id p)))
      (is (some #(= lid (:lipas-id %)) (:sites p)) "site list includes the seeded site")
      (is (= "city" (:current-owner (first (filter #(= lid (:lipas-id %)) (:sites p)))))
          "list shows the site's current owner enum before relabel"))))

;;; --- Site ownership assignment authz (POST /sports-sites) -------------------
;;; The save route gates the org-ownership dimension in middleware
;;; (`owner-org-assignment-authorized?`): a claimed :owner-org-id must name an
;;; org the actor holds :site/create-edit on. Site-level edit perms are still
;;; enforced in core; here we isolate the ownership-claim boundary.

(defn- editor-of-org!
  "Make a user an org-editor of `org-id` (member assigned the :editor template)
  and return a token carrying the derived roles, exactly as login bakes them."
  [org-id]
  (let [user (test-utils/gen-regular-user :db-component (test-db))]
    (backend-org/add-member! (test-db) org-id (:id user)
                             {:roles ["editor"]} nil)
    (jwt/create-token
      (assoc-in user [:permissions :roles]
                (backend-org/derive-user-org-roles (test-db) (:id user))))))

(defn- post-site [token site]
  (test-app (-> (mock/request :post "/api/sports-sites")
                (mock/content-type "application/json")
                (mock/body (test-utils/->json site))
                (test-utils/token-header token))))

(deftest owner-org-assignment-authz-test
  (let [org-id   (catalog-org! editor+ptv-catalog)        ; org the user can edit
        other    (:id (first (create-test-orgs)))         ; an org the user can't
        token    (editor-of-org! org-id)
        new-site (fn [owner-org-id]
                   ;; When claiming an org, set :owner to the org-type-locked
                   ;; enum, mirroring the FE (else check-owner-lock! would 400).
                   (let [owner (some-> owner-org-id
                                       (->> (backend-org/get-org (test-db)))
                                       :type core/org-type->owner)]
                     (cond-> (-> (test-utils/gen-sports-site)
                                 (assoc :status "active")
                                 (dissoc :lipas-id))
                       owner-org-id (assoc :owner-org-id (str owner-org-id))
                       owner        (assoc :owner owner))))]

    (testing "An org-editor may create a site owned by their own org"
      (is (= 201 (:status (post-site token (new-site org-id))))))

    (testing "Claiming a DIFFERENT org the actor has no rights to is forbidden"
      (is (= 403 (:status (post-site token (new-site other))))))

    (testing "A user with no org rights cannot claim any org"
      (let [regular (jwt/create-token (test-utils/gen-regular-user :db-component (test-db)))]
        (is (= 403 (:status (post-site regular (new-site org-id)))))))))

(defn- seed-site!
  "Persist an existing site with `attrs` via the trusted path (bypasses the
  authz under test), and return its lipas-id."
  [attrs]
  (let [admin (test-utils/gen-admin-user :db-component (test-db))
        resp  (core/upsert-sports-site!* (test-db) admin
                                         (merge (assoc (test-utils/gen-sports-site)
                                                       :status "active")
                                                attrs))]
    (:lipas-id resp)))

(defn- owner-enum-for [org-id]
  (core/org-type->owner (:type (backend-org/get-org (test-db) org-id))))

(deftest site-save-ownership-grant-rule-test
  ;; The business rule (core/ownership-change-authorized? +
  ;; edit-grant-change-authorized?) is enforced on the generic save path against
  ;; the STORED site — not the submitted body.
  (let [org-x   (catalog-org! editor+ptv-catalog)
        org-y   (:id (first (create-test-orgs)))
        editor  (editor-of-org! org-x)                 ; token: org-editor of X only
        foreign (seed-site! {})                        ; legacy site editor can't touch
        owned   (seed-site! {:owner-org-id (str org-x) :owner (owner-enum-for org-x)})
        fetch   (fn [id] (core/get-sports-site (test-db) id))]

    (testing "injecting :edit-grants cannot self-authorize editing a foreign site"
      (let [resp  (post-site editor (assoc (fetch foreign) :edit-grants [(str org-x)]))
            after (fetch foreign)]
        (is (= 403 (:status resp)) "escalation attempt must be forbidden")
        (is (empty? (:edit-grants after)) "no grant was persisted")))

    (testing "an org-editor may edit content of their own org's site"
      (is (= 201 (:status (post-site editor (fetch owned))))))

    (testing "an org-editor may NOT add an edit-grant (not an org admin)"
      (let [resp  (post-site editor (assoc (fetch owned) :edit-grants [(str org-y)]))
            after (fetch owned)]
        (is (= 403 (:status resp)))
        (is (not (contains? (set (map str (:edit-grants after))) (str org-y)))
            "grant not persisted")))

    (testing "an org-editor may NOT change ownership of an existing site"
      (is (= 403 (:status (post-site editor (assoc (fetch owned)
                                                   :owner-org-id (str org-y)))))))))

(deftest grant-revoke-edit-authz-test
  ;; #19: revoke must be as restricted as grant — only the owning org's admin (or
  ;; a LIPAS admin). Enforced in core so both endpoints share the rule.
  (let [org-b   (catalog-org! editor+ptv-catalog)
        org-a   (:id (first (create-test-orgs)))
        site-id (seed-site! {:owner-org-id (str org-b) :owner (owner-enum-for org-b)})
        ;; conform roles exactly as the JWT backend does before they reach core
        conform (fn [u] (update-in u [:permissions :roles] roles/conform-roles))
        b-admin (conform (test-utils/gen-org-admin-user org-b :db-component (test-db)))
        a-admin (conform (test-utils/gen-org-admin-user org-a :db-component (test-db)))
        grants  #(set (map str (:edit-grants (core/get-sports-site (test-db) site-id))))]
    (testing "an admin of a non-owning org can neither grant nor revoke"
      (is (thrown-with-msg? Exception #"Not authorized"
                            (core/grant-site-edit! (test-db) (test-search) a-admin site-id org-a nil)))
      (is (thrown-with-msg? Exception #"Not authorized"
                            (core/revoke-site-edit! (test-db) (test-search) a-admin site-id org-a nil))))
    (testing "the owning org's admin may grant, then revoke"
      (core/grant-site-edit! (test-db) (test-search) b-admin site-id org-a (str org-b))
      (is (contains? (grants) (str org-a)) "grant added by owner admin")
      (core/revoke-site-edit! (test-db) (test-search) b-admin site-id org-a (str org-b))
      (is (not (contains? (grants) (str org-a))) "grant revoked by owner admin"))))

(comment
  (clojure.test/run-test-var #'owner-org-assignment-authz-test)
  (clojure.test/run-test-var #'cross-org-view-users-test)
  (clojure.test/run-test-var #'cross-org-update-org-data-test)
  (clojure.test/run-test-var #'get-org-users-test)
  (clojure.test/run-test-var #'create-org-invalid-data-test)
  (clojure.test/run-test-var #'create-org-success-test)
  (clojure.test/run-test-var #'update-org-ptv-config-test))
