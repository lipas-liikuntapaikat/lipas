(ns lipas.backend.api.v1.legacy-index-sync-test
  "Tests for legacy Elasticsearch index synchronization.

   These tests verify that:
   1. When a sports site is saved via save-sports-site!, it's also indexed to the legacy ES index
   2. When a sports site becomes inactive (out-of-service-permanently, incorrect-data),
      it's removed from the legacy ES index
   3. Draft saves do NOT sync to the legacy index

   This is critical for keeping the legacy API in sync with the database."
  (:require
    [clojure.test :refer [deftest is testing use-fixtures]]
    [lipas.backend.core :as core]
    [lipas.backend.search :as search]
    [lipas.test-utils :as test-utils]
    [ring.mock.request :as mock]))

;;; Test system setup ;;;

(defonce test-system (atom nil))

(defn test-app [] (:lipas/app @test-system))
(defn test-db [] (:lipas/db @test-system))
(defn test-search [] (:lipas/search @test-system))
(defn test-ptv [] (:lipas/ptv @test-system))

(let [{:keys [once each]} (test-utils/full-system-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

;;; Helper Functions ;;;

(defn get-legacy-doc
  "Fetch a document directly from the legacy ES index."
  [lipas-id]
  (let [{:keys [client indices]} (test-search)
        idx-name (get-in indices [:legacy-sports-site :search])]
    (try
      (-> (search/fetch-document client idx-name lipas-id)
          :body
          :_source)
      (catch Exception _e
        nil))))

(defn legacy-doc-exists?
  "Check if a document exists in the legacy ES index."
  [lipas-id]
  (some? (get-legacy-doc lipas-id)))

(defn query-legacy-api
  "Queries the legacy API and returns parsed response."
  [path]
  (let [resp ((test-app) (mock/request :get path))
        body (test-utils/<-json (:body resp))]
    {:status (:status resp)
     :headers (:headers resp)
     :body (if (sequential? body) (vec body) body)}))

(defn create-site-in-db!
  "Creates a sports site directly in the database (bypasses save-sports-site! flow).
   Used to set up test data before testing the save-sports-site! behavior."
  [site]
  (let [admin (test-utils/gen-admin-user :db-component (test-db))]
    (core/upsert-sports-site!* (test-db) admin site)))

(defn save-site!
  "Saves a sports site via the full save-sports-site! flow.
   This is what we're testing - it should sync to both ES indices."
  ([site]
   (save-site! site false))
  ([site draft?]
   (let [admin (test-utils/gen-admin-user :db-component (test-db))]
     (core/save-sports-site! (test-db) (test-search) (test-ptv)
                             admin site draft?))))

;;; Tests for save-sports-site! legacy sync ;;;

(deftest save-sports-site-syncs-to-legacy-index-test
  (testing "save-sports-site! indexes new site to legacy ES index"
    ;; For new sites, pass nil lipas-id - system generates one
    (let [site (-> (test-utils/make-point-site nil
                                               :name "Legacy Sync Test Site"
                                               :type-code 1120
                                               :city-code "91")
                   (dissoc :lipas-id))
          saved-site (save-site! site)]

      (Thread/sleep 200) ; Give ES time to index

      (is (some? (:lipas-id saved-site))
          "Saved site should have a generated lipas-id")

      (is (legacy-doc-exists? (:lipas-id saved-site))
          "Site should exist in legacy ES index after save-sports-site!")

      ;; Verify the legacy data format
      (let [legacy-doc (get-legacy-doc (:lipas-id saved-site))]
        (is (= (:lipas-id saved-site) (:sportsPlaceId legacy-doc))
            "Legacy doc should have sportsPlaceId (camelCase)")
        (is (= 1120 (-> legacy-doc :type :typeCode))
            "Legacy doc should have typeCode in type map")
        (is (= 91 (-> legacy-doc :location :city :cityCode))
            "Legacy doc should have cityCode as integer"))))

  (testing "draft save does NOT sync to legacy index"
    (let [site (-> (test-utils/make-point-site nil
                                               :name "Draft Site"
                                               :type-code 1120)
                   (dissoc :lipas-id))
          saved-site (save-site! site true)] ; draft? = true

      (Thread/sleep 200)

      (is (some? (:lipas-id saved-site))
          "Draft site should still get a lipas-id")

      (is (not (legacy-doc-exists? (:lipas-id saved-site)))
          "Draft site should NOT exist in legacy ES index"))))

(deftest update-sports-site-syncs-to-legacy-index-test
  (testing "updating a sports site updates the legacy ES index"
    ;; First create a site via upsert (bypasses save-sports-site!)
    (let [site (test-utils/make-point-site 2000001
                                           :name "Original Name"
                                           :type-code 1120)
          _ (create-site-in-db! site)]

      ;; Now use save-sports-site! to update - this should sync to legacy index
      (let [updated-site (assoc site :name "Updated Name")
            _ (save-site! updated-site)]
        (Thread/sleep 200)

        ;; Verify the update went to legacy index
        (let [legacy-doc (get-legacy-doc 2000001)]
          (is (some? legacy-doc)
              "Site should exist in legacy index after update")
          ;; Legacy format stores name as locale map when fetched directly from ES
          ;; (API endpoint formats it to string based on lang param)
          (is (= "Updated Name" (or (:fi (:name legacy-doc)) (:name legacy-doc)))
              "Legacy doc should reflect the updated name"))))))

;;; Tests for legacy index deletion on status change ;;;

(deftest inactive-status-removes-from-legacy-index-test
  (testing "changing status to 'out-of-service-permanently' removes from legacy index"
    ;; First create and save as active
    (let [site (test-utils/make-point-site 2000002
                                           :name "Soon To Be Deleted"
                                           :status "active"
                                           :type-code 1120)
          _ (create-site-in-db! site)
          _ (save-site! site)] ; First save to get it into legacy index
      (Thread/sleep 200)

      (is (legacy-doc-exists? 2000002)
          "Active site should exist in legacy index")

      ;; Change status to out-of-service-permanently
      (let [inactive-site (assoc site :status "out-of-service-permanently")]
        (save-site! inactive-site)
        (Thread/sleep 200))

      (is (not (legacy-doc-exists? 2000002))
          "Site with 'out-of-service-permanently' status should be REMOVED from legacy index")))

  (testing "changing status to 'incorrect-data' removes from legacy index"
    (let [site (test-utils/make-point-site 2000003
                                           :name "Incorrect Data Site"
                                           :status "active"
                                           :type-code 1120)
          _ (create-site-in-db! site)
          _ (save-site! site)]
      (Thread/sleep 200)

      (is (legacy-doc-exists? 2000003)
          "Active site should exist in legacy index")

      ;; Change status to incorrect-data
      (let [incorrect-site (assoc site :status "incorrect-data")]
        (save-site! incorrect-site)
        (Thread/sleep 200))

      (is (not (legacy-doc-exists? 2000003))
          "Site with 'incorrect-data' status should be REMOVED from legacy index")))

  (testing "'out-of-service-temporarily' still stays in legacy index"
    (let [site (test-utils/make-point-site 2000004
                                           :name "Temporarily Out Of Service"
                                           :status "active"
                                           :type-code 1120)
          _ (create-site-in-db! site)
          _ (save-site! site)]
      (Thread/sleep 200)

      ;; Change status to out-of-service-temporarily
      (let [temp-closed-site (assoc site :status "out-of-service-temporarily")]
        (save-site! temp-closed-site)
        (Thread/sleep 200))

      (is (legacy-doc-exists? 2000004)
          "Site with 'out-of-service-temporarily' should STAY in legacy index"))))

(deftest legacy-api-reflects-changes-test
  (testing "Legacy API returns updated data after save-sports-site!"
    (let [site (test-utils/make-point-site 2000005
                                           :name "API Visible Site"
                                           :type-code 1120
                                           :city-code "91")
          _ (create-site-in-db! site)
          _ (save-site! site)]
      (Thread/sleep 200)

      ;; Query via legacy API
      (let [{:keys [status body]} (query-legacy-api "/v1/sports-places/2000005")]
        (is (= 200 status))
        (is (= 2000005 (:sportsPlaceId body)))
        (is (= "API Visible Site" (:name body))))))

  (testing "Legacy API returns 404 after site becomes inactive"
    (let [site (test-utils/make-point-site 2000006
                                           :name "Will Be Deleted"
                                           :status "active"
                                           :type-code 1120)
          _ (create-site-in-db! site)
          _ (save-site! site)]
      (Thread/sleep 200)

      ;; Verify it's accessible
      (let [{:keys [status]} (query-legacy-api "/v1/sports-places/2000006")]
        (is (= 200 status)))

      ;; Make inactive
      (let [inactive-site (assoc site :status "out-of-service-permanently")]
        (save-site! inactive-site)
        (Thread/sleep 200))

      ;; Verify it's gone from API
      (let [{:keys [status]} (query-legacy-api "/v1/sports-places/2000006")]
        (is (= 404 status)
            "Inactive site should return 404 from legacy API")))))

(comment
  ;; Run all tests in this namespace
  (clojure.test/run-tests 'lipas.backend.api.v1.legacy-index-sync-test)

  ;; Run specific tests
  (clojure.test/run-test-var #'save-sports-site-syncs-to-legacy-index-test)
  (clojure.test/run-test-var #'update-sports-site-syncs-to-legacy-index-test)
  (clojure.test/run-test-var #'inactive-status-removes-from-legacy-index-test)
  (clojure.test/run-test-var #'legacy-api-reflects-changes-test))
