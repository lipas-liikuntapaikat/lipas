(ns lipas.backend.bulk-operations-test
  "Bulk contact update is now an ORG operation: CQRS POST actions
  /actions/org-sites-for-bulk and /actions/mass-update-org-sites,
  gated by :site/create-edit for the org (admits admin + org-editor members).
  The candidate/authorized set is the org's editable sites
  (owner-org-id = org OR edit-grants contains org)."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lipas.backend.core :as core]
            [lipas.backend.jwt :as jwt]
            [lipas.test-utils :as test-utils]
            [lipas.utils :as utils]
            [ring.mock.request :as mock]))

(defonce test-system (atom nil))

(let [{:keys [once each]} (test-utils/full-system-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn test-app [] (:lipas/app @test-system))
(defn test-db [] (:lipas/db @test-system))
(defn test-search [] (:lipas/search @test-system))

;;; Helpers ;;;

(defn- org-editor-user
  "A user whose JWT carries the org-editor role for `org-id` (as projected from
  org membership at login)."
  [org-id]
  (test-utils/gen-user {:db? true
                        :db-component (test-db)
                        :permissions {:roles [{:role "org-editor" :org-id [(str org-id)]}]}}))

(defn- mk-site!
  [lipas-id city-code type-code extra]
  (let [admin (test-utils/gen-admin-user :db-component (test-db))
        site (merge (-> (test-utils/gen-sports-site)
                        (assoc :lipas-id lipas-id
                               :event-date (utils/timestamp)
                               :status "active"
                               :email "old@x.fi"
                               :phone-number "+358401111111"
                               :www "old.x.fi"
                               :reservations-link "old-book.x.fi")
                        (assoc-in [:type :type-code] type-code)
                        (assoc-in [:location :city :city-code] city-code))
                    extra)]
    (core/upsert-sports-site!* (test-db) admin site)
    (core/index! (test-search) site true)
    site))

(defn- seed-org-sites!
  "owned1/owned2 owned by org; granted owned by another org but edit-granted to
  org; unrelated belongs to nobody. Returns their lipas-ids."
  [org-id]
  (let [other (str (java.util.UUID/randomUUID))]
    {:owned1    (:lipas-id (mk-site! 9995001 91 1110 {:owner-org-id (str org-id)}))
     :owned2    (:lipas-id (mk-site! 9995002 49 1120 {:owner-org-id (str org-id)}))
     :granted   (:lipas-id (mk-site! 9995003 837 1130 {:owner-org-id other :edit-grants [(str org-id)]}))
     :unrelated (:lipas-id (mk-site! 9995004 91 1110 {}))}))

;;; Tests ;;;

(deftest list-org-editable-sites-test
  (testing "org-editor lists the org's editable sites (owned ∪ granted), not others"
    (let [org-id (java.util.UUID/randomUUID)
          {:keys [owned1 owned2 granted unrelated]} (seed-org-sites! org-id)
          token (jwt/create-token (org-editor-user org-id))
          resp ((test-app) (-> (mock/request :post "/api/actions/org-sites-for-bulk")
                               (mock/content-type "application/json")
                               (mock/body (test-utils/->json {:org-id org-id}))
                               (test-utils/token-header token)))
          body (test-utils/safe-parse-json resp)
          ids  (set (map :lipas-id body))]
      (is (= 200 (:status resp)))
      (is (contains? ids owned1))
      (is (contains? ids owned2))
      (is (contains? ids granted) "granted (cross-org) site is in the editable set")
      (is (not (contains? ids unrelated)) "unrelated site is excluded")
      (is (true? (:owned? (first (filter #(= owned1 (:lipas-id %)) body)))) "owned site flagged owned?")
      (is (false? (:owned? (first (filter #(= granted (:lipas-id %)) body)))) "granted site not owned?"))))

(deftest mass-update-org-editor-success-test
  (testing "org-editor can mass-update owned + granted sites"
    (let [org-id (java.util.UUID/randomUUID)
          {:keys [owned1 granted]} (seed-org-sites! org-id)
          token (jwt/create-token (org-editor-user org-id))
          payload {:org-id org-id :lipas-ids [owned1 granted] :updates {:email "new@org.fi"}}
          resp ((test-app) (-> (mock/request :post "/api/actions/mass-update-org-sites")
                               (mock/content-type "application/json")
                               (mock/body (test-utils/->json payload))
                               (test-utils/token-header token)))
          body (test-utils/safe-parse-json resp)]
      (is (= 200 (:status resp)))
      (is (= 2 (:total-updated body)))
      (is (= "new@org.fi" (:email (core/get-sports-site2 (test-search) owned1 :none)))))))

(deftest mass-update-rejects-foreign-sites-test
  (testing "an org-editor cannot update a site outside the org's editable set"
    (let [org-id (java.util.UUID/randomUUID)
          {:keys [owned1 unrelated]} (seed-org-sites! org-id)
          token (jwt/create-token (org-editor-user org-id))
          payload {:org-id org-id :lipas-ids [owned1 unrelated] :updates {:email "x@x.fi"}}
          resp ((test-app) (-> (mock/request :post "/api/actions/mass-update-org-sites")
                               (mock/content-type "application/json")
                               (mock/body (test-utils/->json payload))
                               (test-utils/token-header token)))]
      (is (= 500 (:status resp)) "unauthorized lipas-ids are rejected"))))

(deftest non-editor-denied-test
  (testing "a user without org-editor for the org is denied (403) on both endpoints"
    (let [org-id (java.util.UUID/randomUUID)
          {:keys [owned1]} (seed-org-sites! org-id)
          token (jwt/create-token (test-utils/gen-regular-user :db-component (test-db)))
          get-resp ((test-app) (-> (mock/request :post "/api/actions/org-sites-for-bulk")
                                   (mock/content-type "application/json")
                                   (mock/body (test-utils/->json {:org-id org-id}))
                                   (test-utils/token-header token)))
          post-resp ((test-app) (-> (mock/request :post "/api/actions/mass-update-org-sites")
                                    (mock/content-type "application/json")
                                    (mock/body (test-utils/->json {:org-id org-id :lipas-ids [owned1] :updates {:email "x@x.fi"}}))
                                    (test-utils/token-header token)))]
      (is (= 403 (:status get-resp)))
      (is (= 403 (:status post-resp))))))

(deftest org-editor-of-other-org-denied-test
  (testing "an org-editor of a DIFFERENT org cannot bulk-edit this org's sites"
    (let [org-id   (java.util.UUID/randomUUID)
          other-id (java.util.UUID/randomUUID)
          {:keys [owned1]} (seed-org-sites! org-id)
          token (jwt/create-token (org-editor-user other-id))
          resp ((test-app) (-> (mock/request :post "/api/actions/org-sites-for-bulk")
                               (mock/content-type "application/json")
                               (mock/body (test-utils/->json {:org-id org-id}))
                               (test-utils/token-header token)))]
      (is (= 403 (:status resp))))))

(deftest admin-can-mass-update-test
  (testing "lipas-admin can mass-update any org's sites"
    (let [org-id (java.util.UUID/randomUUID)
          {:keys [owned1]} (seed-org-sites! org-id)
          token (jwt/create-token (test-utils/gen-admin-user :db-component (test-db)))
          payload {:org-id org-id :lipas-ids [owned1] :updates {:email "admin@org.fi"}}
          resp ((test-app) (-> (mock/request :post "/api/actions/mass-update-org-sites")
                               (mock/content-type "application/json")
                               (mock/body (test-utils/->json payload))
                               (test-utils/token-header token)))
          body (test-utils/safe-parse-json resp)]
      (is (= 200 (:status resp)))
      (is (= 1 (:total-updated body))))))

(deftest auth-required-test
  (testing "endpoints require authentication"
    (let [org-id (java.util.UUID/randomUUID)]
      (is (= 401 (:status ((test-app) (-> (mock/request :post "/api/actions/org-sites-for-bulk")
                                          (mock/content-type "application/json")
                                          (mock/body (test-utils/->json {:org-id org-id}))))))))))

(deftest invalid-payload-test
  (testing "invalid email is rejected"
    (let [org-id (java.util.UUID/randomUUID)
          {:keys [owned1]} (seed-org-sites! org-id)
          token (jwt/create-token (test-utils/gen-admin-user :db-component (test-db)))
          payload {:org-id org-id :lipas-ids [owned1] :updates {:email "not-an-email"}}
          resp ((test-app) (-> (mock/request :post "/api/actions/mass-update-org-sites")
                               (mock/content-type "application/json")
                               (mock/body (test-utils/->json payload))
                               (test-utils/token-header token)))]
      (is (= 400 (:status resp))))))

(comment
  (clojure.test/run-test-var #'list-org-editable-sites-test)
  (clojure.test/run-test-var #'mass-update-org-editor-success-test)
  (clojure.test/run-test-var #'non-editor-denied-test))
