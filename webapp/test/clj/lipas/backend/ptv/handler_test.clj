(ns lipas.backend.ptv.handler-test
  (:require [cheshire.core]
            [clojure.test :refer [deftest is use-fixtures testing]]
            [lipas.backend.core :as core]
            [lipas.backend.jwt :as jwt]
            [lipas.test-utils :as tu]
            [ring.mock.request :as mock]))

(defonce test-system (atom nil))

;;; Fixtures ;;;

(let [{:keys [once each]} (tu/full-system-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn test-db [] (:lipas/db @test-system))
(defn test-search [] (:lipas/search @test-system))
(defn test-app [req] ((:lipas/app @test-system) req))

;;; Helper Functions ;;;

(defn- create-test-site-with-ptv
  "Creates a stable test sports site (yleisurheilukenttä) with basic PTV data for testing"
  [user]
  (let [site {:status "active"
              :event-date "2025-01-01T00:00:00.000Z"
              :name "Test Athletics Training Area"
              :owner "city"
              :admin "city-sports"
              :type {:type-code 1210} ; yleisurheilukenttä
              :location {:city {:city-code 91} ; Helsinki
                         :address "Test Street 1"
                         :postal-code "00100"
                         :postal-office "Helsinki"
                         :geometries {:type "FeatureCollection"
                                      :features [{:type "Feature"
                                                  :geometry {:type "Point"
                                                             :coordinates [24.9384 60.1695]}}]}}
              :ptv {:org-id "test-org-id"
                    :sync-enabled true
                    :summary {:fi "Test summary"
                              :se "Test summary"
                              :en "Test summary"}
                    :description {:fi "Test description"
                                  :se "Test description"
                                  :en "Test description"}
                    :service-channel-ids []
                    :service-ids []}}]
    (core/upsert-sports-site!* (test-db) user site)))

;;; Tests ;;;

(deftest save-ptv-audit-success-test
  (testing "Successfully saves PTV audit with valid data"
    (let [admin-user (tu/gen-admin-user :db-component (test-db))
          ptv-auditor (tu/gen-ptv-auditor :db-component (test-db))
          site (create-test-site-with-ptv admin-user)
          lipas-id (:lipas-id site)
          token (jwt/create-token ptv-auditor)
          audit-data {:status "approved"
                      :feedback "Site information looks good"}

          resp (test-app (-> (mock/request :post "/api/actions/save-ptv-audit")
                             (mock/content-type "application/json")
                             (mock/body (tu/->json {:lipas-id lipas-id
                                                    :audit {:summary audit-data
                                                            :description audit-data}}))
                             (tu/token-header token)))

          body (tu/safe-parse-json resp)]

      (is (= 200 (:status resp)))
      (is (some? body))
      (is (contains? body :timestamp))
      (is (contains? body :auditor-id))
      (is (= (str (:id ptv-auditor)) (:auditor-id body)))
      (is (= audit-data (get-in body [:summary])))
      (is (= audit-data (get-in body [:description]))))))

(deftest save-ptv-audit-updates-site-test
  (testing "PTV audit is properly saved to sports site and indexed"
    (let [admin-user (tu/gen-admin-user :db-component (test-db))
          ptv-auditor (tu/gen-ptv-auditor :db-component (test-db))
          site (create-test-site-with-ptv admin-user)
          lipas-id (:lipas-id site)
          token (jwt/create-token ptv-auditor)
          audit-data {:status "approved"
                      :feedback "Comprehensive review completed"}

          _ (test-app (-> (mock/request :post "/api/actions/save-ptv-audit")
                          (mock/content-type "application/json")
                          (mock/body (tu/->json {:lipas-id lipas-id
                                                 :audit {:summary audit-data}}))
                          (tu/token-header token)))

          ;; Verify the site was updated in database
          updated-site (core/get-sports-site (test-db) lipas-id)

          ;; Verify the site was updated in Elasticsearch
          es-site (core/get-sports-site2 (test-search) lipas-id)]

      (is (contains? updated-site :ptv))
      (is (contains? (:ptv updated-site) :audit))
      (is (= audit-data (get-in updated-site [:ptv :audit :summary])))
      (is (some? (get-in updated-site [:ptv :audit :timestamp])))
      (is (= (str (:id ptv-auditor)) (get-in updated-site [:ptv :audit :auditor-id])))

      ;; Verify Elasticsearch is in sync
      (is (= (get-in updated-site [:ptv :audit])
             (get-in es-site [:ptv :audit]))))))

(deftest save-ptv-audit-requires-privilege-test
  (testing "Endpoint requires :ptv/audit privilege"
    (let [regular-user (tu/gen-user {:db? true :db-component (test-db) :admin? false})
          admin-user (tu/gen-admin-user :db-component (test-db))
          site (create-test-site-with-ptv admin-user)
          lipas-id (:lipas-id site)
          token (jwt/create-token regular-user)
          audit-data {:status "approved" :feedback "Test"}

          resp (test-app (-> (mock/request :post "/api/actions/save-ptv-audit")
                             (mock/content-type "application/json")
                             (mock/body (tu/->json {:lipas-id lipas-id
                                                    :audit {:summary audit-data}}))
                             (tu/token-header token)))]

      (is (= 403 (:status resp))))))

(deftest save-ptv-audit-nonexistent-site-test
  (testing "Returns 404 for non-existent site"
    (let [ptv-auditor (tu/gen-ptv-auditor :db-component (test-db))
          token (jwt/create-token ptv-auditor)
          nonexistent-id 999999
          audit-data {:status "approved" :feedback "Test"}

          resp (test-app (-> (mock/request :post "/api/actions/save-ptv-audit")
                             (mock/content-type "application/json")
                             (mock/body (tu/->json {:lipas-id nonexistent-id
                                                    :audit {:summary audit-data}}))
                             (tu/token-header token)))]
      (is (= 404 (:status resp))))))

(deftest save-ptv-audit-empty-audit-data-test
  (testing "Handles empty audit data gracefully"
    (let [admin-user (tu/gen-admin-user :db-component (test-db))
          ptv-auditor (tu/gen-ptv-auditor :db-component (test-db))
          site (create-test-site-with-ptv admin-user)
          lipas-id (:lipas-id site)
          token (jwt/create-token ptv-auditor)

          resp (test-app (-> (mock/request :post "/api/actions/save-ptv-audit")
                             (mock/content-type "application/json")
                             (mock/body (tu/->json {:lipas-id lipas-id
                                                    :audit {}}))
                             (tu/token-header token)))]
      (is (= 200 (:status resp))))))

(deftest save-ptv-audit-schema-compliant-test
  (testing "Saves audit data that complies with closed schema"
    (let [admin-user (tu/gen-admin-user :db-component (test-db))
          ptv-auditor (tu/gen-ptv-auditor :db-component (test-db))
          site (create-test-site-with-ptv admin-user)
          lipas-id (:lipas-id site)
          token (jwt/create-token ptv-auditor)
          valid-summary {:status "changes-requested"
                         :feedback "Summary field needs improvement - missing Finnish translation and contact details are outdated"}
          valid-description {:status "approved"
                             :feedback "Description is comprehensive and accurate"}

          resp (test-app (-> (mock/request :post "/api/actions/save-ptv-audit")
                             (mock/content-type "application/json")
                             (mock/body (tu/->json {:lipas-id lipas-id
                                                    :audit {:summary valid-summary
                                                            :description valid-description}}))
                             (tu/token-header token)))

          body (tu/safe-parse-json resp)]

      (is (= 200 (:status resp)))
      (is (= valid-summary (:summary body)))
      (is (= valid-description (:description body)))
      (is (contains? body :timestamp))
      (is (contains? body :auditor-id)))))

(deftest save-ptv-audit-invalid-extra-fields-test
  (testing "Strips extra fields from audit data due to closed schema"
    (let [admin-user (tu/gen-admin-user :db-component (test-db))
          ptv-auditor (tu/gen-ptv-auditor :db-component (test-db))
          site (create-test-site-with-ptv admin-user)
          lipas-id (:lipas-id site)
          token (jwt/create-token ptv-auditor)
          audit-with-extra {:status "changes-requested"
                            :feedback "Valid feedback"
                            :extra-field "This should be stripped"} ; Extra field not in schema

          resp (test-app (-> (mock/request :post "/api/actions/save-ptv-audit")
                             (mock/content-type "application/json")
                             (mock/body (tu/->json {:lipas-id lipas-id
                                                    :audit {:summary audit-with-extra}}))
                             (tu/token-header token)))

          body (tu/safe-parse-json resp)]

      ;; Should succeed but strip the extra field
      (is (= 200 (:status resp)))
      (is (some? body))

      ;; Valid fields should be preserved
      (is (= "changes-requested" (get-in body [:summary :status])))
      (is (= "Valid feedback" (get-in body [:summary :feedback])))

      ;; Extra field should be stripped
      (is (not (contains? (:summary body) :extra-field))))))

(deftest save-ptv-audit-invalid-status-test
  (testing "Rejects audit data with invalid status values"
    (let [admin-user (tu/gen-admin-user :db-component (test-db))
          ptv-auditor (tu/gen-ptv-auditor :db-component (test-db))
          site (create-test-site-with-ptv admin-user)
          lipas-id (:lipas-id site)
          token (jwt/create-token ptv-auditor)
          invalid-audit {:status "invalid-status" ; Not in enum
                         :feedback "Valid feedback"}

          resp (test-app (-> (mock/request :post "/api/actions/save-ptv-audit")
                             (mock/content-type "application/json")
                             (mock/body (tu/->json {:lipas-id lipas-id
                                                    :audit {:summary invalid-audit}}))
                             (tu/token-header token)))]

      (is (= 400 (:status resp))))))

(deftest save-ptv-audit-feedback-too-long-test
  (testing "Rejects audit data with feedback exceeding max length"
    (let [admin-user (tu/gen-admin-user :db-component (test-db))
          ptv-auditor (tu/gen-ptv-auditor :db-component (test-db))
          site (create-test-site-with-ptv admin-user)
          lipas-id (:lipas-id site)
          token (jwt/create-token ptv-auditor)
          invalid-audit {:status "approved"
                         :feedback (apply str (repeat 1001 "x"))} ; Too long (>1000 chars)

          resp (test-app (-> (mock/request :post "/api/actions/save-ptv-audit")
                             (mock/content-type "application/json")
                             (mock/body (tu/->json {:lipas-id lipas-id
                                                    :audit {:summary invalid-audit}}))
                             (tu/token-header token)))]

      (is (= 400 (:status resp))))))

(deftest save-ptv-audit-overwrites-previous-test
  (testing "New audit data overwrites previous audit data"
    (let [admin-user (tu/gen-admin-user :db-component (test-db))
          ptv-auditor (tu/gen-ptv-auditor :db-component (test-db))
          site (create-test-site-with-ptv admin-user)
          lipas-id (:lipas-id site)
          token (jwt/create-token ptv-auditor)
          first-audit {:status "approved" :feedback "First review"}

          ;; Save first audit
          _ (test-app (-> (mock/request :post "/api/actions/save-ptv-audit")
                          (mock/content-type "application/json")
                          (mock/body (tu/->json {:lipas-id lipas-id
                                                 :audit {:summary first-audit}}))
                          (tu/token-header token)))

          second-audit {:status "changes-requested" :feedback "Second review"}

          ;; Save second audit
          resp (test-app (-> (mock/request :post "/api/actions/save-ptv-audit")
                             (mock/content-type "application/json")
                             (mock/body (tu/->json {:lipas-id lipas-id
                                                    :audit {:summary second-audit}}))
                             (tu/token-header token)))

          body (tu/safe-parse-json resp)
          updated-site (core/get-sports-site (test-db) lipas-id)]

      (is (= 200 (:status resp)))
      (is (= second-audit (:summary body)))
      (is (= second-audit (get-in updated-site [:ptv :audit :summary])))
      ;; Verify first audit is completely replaced
      (is (not= first-audit (get-in updated-site [:ptv :audit :summary]))))))

(deftest save-ptv-audit-invalid-lipas-id-test
  (testing "Validates lipas-id parameter"
    (let [ptv-auditor (tu/gen-ptv-auditor :db-component (test-db))
          token (jwt/create-token ptv-auditor)
          audit-data {:status "approved" :feedback "Test"}

          resp (test-app (-> (mock/request :post "/api/actions/save-ptv-audit")
                             (mock/content-type "application/json")
                             (mock/body (tu/->json {:lipas-id "not-a-number" ; Invalid
                                                    :audit {:summary audit-data}}))
                             (tu/token-header token)))]

      (is (= 400 (:status resp))))))

(deftest save-ptv-audit-invalid-audit-schema-test
  (testing "Validates audit parameter against schema"
    (let [admin-user (tu/gen-admin-user :db-component (test-db))
          ptv-auditor (tu/gen-ptv-auditor :db-component (test-db))
          site (create-test-site-with-ptv admin-user)
          lipas-id (:lipas-id site)
          token (jwt/create-token ptv-auditor)

          resp (test-app (-> (mock/request :post "/api/actions/save-ptv-audit")
                             (mock/content-type "application/json")
                             (mock/body (tu/->json {:lipas-id lipas-id
                                                    :audit "not-a-map"})) ; Invalid
                             (tu/token-header token)))]

      (is (= 400 (:status resp))))))

(deftest save-ptv-audit-no-auth-token-test
  (testing "Requires authentication token"
    (let [admin-user (tu/gen-admin-user :db-component (test-db))
          site (create-test-site-with-ptv admin-user)
          lipas-id (:lipas-id site)
          audit-data {:status "approved" :feedback "Test"}

          resp (test-app (-> (mock/request :post "/api/actions/save-ptv-audit")
                             (mock/content-type "application/json")
                             (mock/body (tu/->json {:lipas-id lipas-id
                                                    :audit {:summary audit-data}}))))]

      (is (= 401 (:status resp))))))

(deftest save-ptv-audit-updates-event-date-test
  (testing "Updates the sports site event-date when audit is saved"
    (let [admin-user (tu/gen-admin-user :db-component (test-db))
          ptv-auditor (tu/gen-ptv-auditor :db-component (test-db))
          site (create-test-site-with-ptv admin-user)
          lipas-id (:lipas-id site)
          original-date (:event-date site)
          token (jwt/create-token ptv-auditor)
          audit-data {:status "approved" :feedback "Event date test"}

          ;; Add small delay to ensure timestamp difference
          _ (Thread/sleep 100)

          _ (test-app (-> (mock/request :post "/api/actions/save-ptv-audit")
                          (mock/content-type "application/json")
                          (mock/body (tu/->json {:lipas-id lipas-id
                                                 :audit {:summary audit-data}}))
                          (tu/token-header token)))

          updated-site (core/get-sports-site (test-db) lipas-id)]

      (is (not= original-date (:event-date updated-site))))))

;;; PTV Audit Access Tests ;;;

(deftest ptv-audit-can-access-get-ptv-integration-candidates-test
  (testing "Users with :ptv/audit privilege can access get-ptv-integration-candidates"
    (let [ptv-auditor (tu/gen-ptv-auditor :db-component (test-db))
          token (jwt/create-token ptv-auditor)

          resp (test-app (-> (mock/request :post "/api/actions/get-ptv-integration-candidates")
                             (mock/content-type "application/json")
                             (mock/body (tu/->json {:city-codes [91]
                                                    :owners ["private" "municipal" "other"]}))
                             (tu/token-header token)))]

      (is (not= 403 (:status resp))
          "PTV auditor should be able to access integration candidates endpoint"))))

(deftest ptv-audit-can-access-fetch-ptv-org-test
  (testing "Users with :ptv/audit privilege can access fetch-ptv-org"
    (let [ptv-auditor (tu/gen-ptv-auditor :db-component (test-db))
          token (jwt/create-token ptv-auditor)

          resp (test-app (-> (mock/request :post "/api/actions/fetch-ptv-org")
                             (mock/content-type "application/json")
                             (mock/body (tu/->json {:org-id "test-org-id"}))
                             (tu/token-header token)))]

      (is (not= 403 (:status resp))
          "PTV auditor should be able to access fetch PTV org endpoint"))))

(deftest ptv-audit-can-access-fetch-ptv-services-test
  (testing "Users with :ptv/audit privilege can access fetch-ptv-services"
    (let [ptv-auditor (tu/gen-ptv-auditor :db-component (test-db))
          token (jwt/create-token ptv-auditor)

          resp (test-app (-> (mock/request :post "/api/actions/fetch-ptv-services")
                             (mock/content-type "application/json")
                             (mock/body (tu/->json {:org-id "test-org-id"}))
                             (tu/token-header token)))]

      (is (not= 403 (:status resp))
          "PTV auditor should be able to access fetch PTV services endpoint"))))

(deftest ptv-audit-can-access-fetch-ptv-service-channels-test
  (testing "Users with :ptv/audit privilege can access fetch-ptv-service-channels"
    (let [ptv-auditor (tu/gen-ptv-auditor :db-component (test-db))
          token (jwt/create-token ptv-auditor)

          resp (test-app (-> (mock/request :post "/api/actions/fetch-ptv-service-channels")
                             (mock/content-type "application/json")
                             (mock/body (tu/->json {:org-id "test-org-id"}))
                             (tu/token-header token)))]

      (is (not= 403 (:status resp))
          "PTV auditor should be able to access fetch PTV service channels endpoint"))))

(deftest ptv-audit-can-access-fetch-ptv-service-collections-test
  (testing "Users with :ptv/audit privilege can access fetch-ptv-service-collections"
    (let [ptv-auditor (tu/gen-ptv-auditor :db-component (test-db))
          token (jwt/create-token ptv-auditor)

          resp (test-app (-> (mock/request :post "/api/actions/fetch-ptv-service-collections")
                             (mock/content-type "application/json")
                             (mock/body (tu/->json {:org-id "test-org-id"}))
                             (tu/token-header token)))]

      (is (not= 403 (:status resp))
          "PTV auditor should be able to access fetch PTV service collections endpoint"))))

(deftest regular-user-cannot-access-ptv-endpoints-test
  (testing "Regular users without PTV privileges cannot access PTV endpoints"
    (let [regular-user (tu/gen-user {:db? true :db-component (test-db) :admin? false})
          token (jwt/create-token regular-user)

          endpoints ["/api/actions/get-ptv-integration-candidates"
                     "/api/actions/fetch-ptv-org"
                     "/api/actions/fetch-ptv-services"
                     "/api/actions/fetch-ptv-service-channels"
                     "/api/actions/fetch-ptv-service-collections"]

          test-body (tu/->json {:org-id "test-org-id"
                                :city-codes [91]
                                :owners ["private"]})]

      (doseq [endpoint endpoints]
        (let [resp (test-app (-> (mock/request :post endpoint)
                                 (mock/content-type "application/json")
                                 (mock/body test-body)
                                 (tu/token-header token)))]
          (is (= 403 (:status resp))
              (str "Regular user should not be able to access " endpoint)))))))

(deftest ptv-audit-cannot-modify-ptv-data-test
  (testing "Users with :ptv/audit privilege cannot modify PTV data (write operations)"
    (let [ptv-auditor (tu/gen-ptv-auditor :db-component (test-db))
          admin-user (tu/gen-admin-user :db-component (test-db))
          site (create-test-site-with-ptv admin-user)
          token (jwt/create-token ptv-auditor)

          ;; Test save-ptv-service (should require :ptv/manage)
          save-service-resp (test-app (-> (mock/request :post "/api/actions/save-ptv-service")
                                          (mock/content-type "application/json")
                                          (mock/body (tu/->json {:org-id "test-org-id"
                                                                 :city-codes [91]
                                                                 :source-id "test-source"
                                                                 :sub-category-id 1210
                                                                 :languages ["fi"]
                                                                 :summary {:fi "Test"}
                                                                 :description {:fi "Test"}}))
                                          (tu/token-header token)))

          ;; Test save-ptv-service-location (should require :ptv/manage)
          ;; Provide complete schema-compliant data
          save-location-resp (test-app (-> (mock/request :post "/api/actions/save-ptv-service-location")
                                           (mock/content-type "application/json")
                                           (mock/body (tu/->json {:lipas-id (:lipas-id site)
                                                                  :org-id "test-org-id"
                                                                  :ptv {:org-id "test-org-id"
                                                                        :sync-enabled true
                                                                        :service-channel-ids []
                                                                        :service-ids []
                                                                        :summary {:fi "Test summary"}
                                                                        :description {:fi "Test description"}}}))
                                           (tu/token-header token)))

          ;; Test save-ptv-meta (should require :ptv/manage)
          ;; Provide complete spec-compliant data
          save-meta-resp (test-app (-> (mock/request :post "/api/actions/save-ptv-meta")
                                       (mock/content-type "application/json")
                                       (mock/body (tu/->json {(:lipas-id site) {:org-id "test-org-id"
                                                                                :sync-enabled true
                                                                                :service-channel-ids []
                                                                                :service-ids []
                                                                                :summary {:fi "Test summary"}
                                                                                :description {:fi "Test description"}}}))
                                       (tu/token-header token)))]

      (is (= 403 (:status save-service-resp))
          "PTV auditor should not be able to save PTV services")
      (is (= 403 (:status save-location-resp))
          "PTV auditor should not be able to save PTV service locations")
      (is (= 403 (:status save-meta-resp))
          "PTV auditor should not be able to save PTV meta data"))))

;;; PTV service-channel double-link detection ;;;

;; Realistic PTV organisation + service-location channel UUIDs (PTV uses UUIDs
;; for both). Two of the seeded sites share `rink-channel`, which is the
;; double-link scenario.
(def ^:private ptv-org-id "7fdd7f84-e52a-4c17-a59a-d7c2a3095ed5")
(def ^:private rink-channel "a1b2c3d4-0001-4abc-9def-000000000001")
(def ^:private pool-channel "a1b2c3d4-0002-4abc-9def-000000000002")

(defn- seed-ptv-site!
  "Seeds a realistic, PTV-synced sports-site bound to `channel-ids`."
  [user {:keys [name type-code city-code address postal-code postal-office coords channel-ids]}]
  (core/upsert-sports-site!*
   (test-db) user
   {:status "active"
    :event-date "2025-01-01T00:00:00.000Z"
    :name name
    :owner "city"
    :admin "city-sports"
    :type {:type-code type-code}
    :location {:city {:city-code city-code}
               :address address
               :postal-code postal-code
               :postal-office postal-office
               :geometries {:type "FeatureCollection"
                            :features [{:type "Feature"
                                        :geometry {:type "Point"
                                                   :coordinates coords}}]}}
    :ptv {:org-id ptv-org-id
          :sync-enabled true
          :last-sync "2025-01-02T00:00:00.000Z"
          :summary {:fi "Kuvaus" :se "Beskrivning" :en "Summary"}
          :description {:fi "Pidempi kuvaus" :se "Beskrivning" :en "Description"}
          :service-ids []
          :service-channel-ids channel-ids}}))

(defn- check-link
  "Calls the check endpoint as `token` and returns parsed body."
  [token lipas-id service-channel-id]
  (-> (test-app (-> (mock/request :post "/api/actions/check-ptv-service-channel-link")
                    (mock/content-type "application/json")
                    (mock/body (tu/->json {:lipas-id lipas-id
                                           :service-channel-id service-channel-id}))
                    (tu/token-header token)))))

(defn- seed-double-link-scenario!
  "Seeds Oulunkylän tekojää + Helsingin jäähalli (both on `rink-channel`) and
  Yrjönkadun uimahalli (on `pool-channel`). Returns their lipas-ids."
  [user]
  {:rink-a (:lipas-id (seed-ptv-site! user {:name "Oulunkylän tekojää"
                                            :type-code 2510 :city-code 91
                                            :address "Käärmetie 8" :postal-code "00640"
                                            :postal-office "Helsinki" :coords [24.9612 60.2289]
                                            :channel-ids [rink-channel]}))
   :rink-b (:lipas-id (seed-ptv-site! user {:name "Helsingin jäähalli"
                                            :type-code 2520 :city-code 91
                                            :address "Nordenskiöldinkatu 11-13" :postal-code "00250"
                                            :postal-office "Helsinki" :coords [24.9226 60.1872]
                                            :channel-ids [rink-channel]}))
   :pool (:lipas-id (seed-ptv-site! user {:name "Yrjönkadun uimahalli"
                                          :type-code 3110 :city-code 91
                                          :address "Yrjönkatu 21 b" :postal-code "00100"
                                          :postal-office "Helsinki" :coords [24.9384 60.1677]
                                          :channel-ids [pool-channel]}))})

(deftest check-service-channel-link-no-conflict-test
  (testing "Channel held only by the queried site returns no other sites"
    (let [admin (tu/gen-admin-user :db-component (test-db))
          token (jwt/create-token admin)
          {:keys [pool]} (seed-double-link-scenario! admin)
          resp (check-link token pool pool-channel)
          body (tu/safe-parse-json resp)]
      (is (= 200 (:status resp)))
      (is (= pool-channel (:service-channel-id body)))
      (is (= [] (:other-sites body))))))

(deftest check-service-channel-link-single-sibling-test
  (testing "A channel shared with one other site returns that sibling, excluding self"
    (let [admin (tu/gen-admin-user :db-component (test-db))
          token (jwt/create-token admin)
          {:keys [rink-a rink-b]} (seed-double-link-scenario! admin)
          body (tu/safe-parse-json (check-link token rink-a rink-channel))
          others (:other-sites body)]
      (is (= 1 (count others)))
      (is (= rink-b (-> others first :lipas-id)))
      (is (= "Helsingin jäähalli" (-> others first :name)))
      ;; self is excluded
      (is (not (contains? (set (map :lipas-id others)) rink-a))))))

(deftest check-service-channel-link-multiple-siblings-test
  (testing "A channel shared with several other sites returns all of them"
    (let [admin (tu/gen-admin-user :db-component (test-db))
          token (jwt/create-token admin)
          {:keys [rink-a rink-b]} (seed-double-link-scenario! admin)
          ;; a third site joins the same rink channel
          rink-c (:lipas-id (seed-ptv-site! admin {:name "Pirkkolan jäähalli"
                                                   :type-code 2520 :city-code 91
                                                   :address "Pirkkolan metsätie 4" :postal-code "00630"
                                                   :postal-office "Helsinki" :coords [24.9046 60.2389]
                                                   :channel-ids [rink-channel]}))
          body (tu/safe-parse-json (check-link token rink-a rink-channel))
          other-ids (set (map :lipas-id (:other-sites body)))]
      (is (= #{rink-b rink-c} other-ids))
      (is (not (contains? other-ids rink-a))))))

(deftest check-service-channel-link-unknown-channel-test
  (testing "An unused channel id returns no sites"
    (let [admin (tu/gen-admin-user :db-component (test-db))
          token (jwt/create-token admin)
          {:keys [rink-a]} (seed-double-link-scenario! admin)
          body (tu/safe-parse-json (check-link token rink-a "ffffffff-ffff-ffff-ffff-ffffffffffff"))]
      (is (= [] (:other-sites body))))))

(deftest check-service-channel-link-requires-ptv-access-test
  (testing "Regular users without PTV privileges get 403"
    (let [admin (tu/gen-admin-user :db-component (test-db))
          regular (tu/gen-user {:db? true :db-component (test-db) :admin? false})
          token (jwt/create-token regular)
          {:keys [rink-a]} (seed-double-link-scenario! admin)
          resp (check-link token rink-a rink-channel)]
      (is (= 403 (:status resp))))))

;;; Double-link enforcement at the persistence boundary ;;;

(deftest save-ptv-meta-rejects-double-link-test
  (testing "Saving meta that binds a site to a channel another site owns is rejected with 409"
    (let [admin (tu/gen-admin-user :db-component (test-db))
          token (jwt/create-token admin)
          ;; rink-b already owns rink-channel
          {:keys [rink-b]} (seed-double-link-scenario! admin)
          ;; an unrelated site tries to grab the same channel
          intruder (:lipas-id (seed-ptv-site! admin {:name "Malmin jäähalli"
                                                     :type-code 2520 :city-code 91
                                                     :address "Pekanraitti 4" :postal-code "00700"
                                                     :postal-office "Helsinki" :coords [25.0103 60.2503]
                                                     :channel-ids []}))
          resp (test-app (-> (mock/request :post "/api/actions/save-ptv-meta")
                             (mock/content-type "application/json")
                             (mock/body (tu/->json {intruder {:org-id ptv-org-id
                                                              :sync-enabled true
                                                              :service-ids []
                                                              :service-channel-ids [rink-channel]
                                                              :summary {:fi "Test summary"}
                                                              :description {:fi "Test description"}}}))
                             (tu/token-header token)))
          body (tu/safe-parse-json resp)]
      (is (= 409 (:status resp)))
      (is (= "double-link" (:type body)))
      (is (= rink-channel (:service-channel-id body)))
      (is (contains? (set (map :lipas-id (:other-sites body))) rink-b)))))

(deftest save-ptv-meta-allows-own-channel-test
  (testing "Re-saving meta for the site that already owns the channel is allowed (self excluded)"
    (let [admin (tu/gen-admin-user :db-component (test-db))
          token (jwt/create-token admin)
          {:keys [pool]} (seed-double-link-scenario! admin)
          resp (test-app (-> (mock/request :post "/api/actions/save-ptv-meta")
                             (mock/content-type "application/json")
                             (mock/body (tu/->json {pool {:org-id ptv-org-id
                                                          :sync-enabled true
                                                          :service-ids []
                                                          :service-channel-ids [pool-channel]
                                                          :summary {:fi "Test summary"}
                                                          :description {:fi "Test description"}}}))
                             (tu/token-header token)))]
      (is (= 200 (:status resp))))))

(deftest save-ptv-service-location-rejects-double-link-test
  (testing "Syncing a service-location to a channel another site owns is rejected with 409 (before any PTV call)"
    (let [admin (tu/gen-admin-user :db-component (test-db))
          token (jwt/create-token admin)
          {:keys [rink-b]} (seed-double-link-scenario! admin)
          intruder (:lipas-id (seed-ptv-site! admin {:name "Kontulan jäähalli"
                                                     :type-code 2520 :city-code 91
                                                     :address "Kontulankaari 11" :postal-code "00940"
                                                     :postal-office "Helsinki" :coords [25.0732 60.2419]
                                                     :channel-ids []}))
          resp (test-app (-> (mock/request :post "/api/actions/save-ptv-service-location")
                             (mock/content-type "application/json")
                             (mock/body (tu/->json {:lipas-id intruder
                                                    :org-id ptv-org-id
                                                    :ptv {:org-id ptv-org-id
                                                          :sync-enabled true
                                                          :service-ids []
                                                          :service-channel-ids [rink-channel]
                                                          :summary {:fi "Test summary"}
                                                          :description {:fi "Test description"}}}))
                             (tu/token-header token)))
          body (tu/safe-parse-json resp)]
      (is (= 409 (:status resp)))
      (is (= "double-link" (:type body)))
      (is (contains? (set (map :lipas-id (:other-sites body))) rink-b)))))

(comment
  (clojure.test/run-tests *ns*)
  (clojure.test/run-test-var #'save-ptv-audit-success-test)
  (clojure.test/run-test-var #'save-ptv-audit-no-auth-token-test)
  (clojure.test/run-test-var #'save-ptv-audit-invalid-extra-fields-test)
  (clojure.test/run-test-var #'save-ptv-audit-invalid-audit-schema-test)
  (clojure.test/run-test-var #'save-ptv-audit-empty-audit-data-test)
  ;; New audit access tests
  (clojure.test/run-test-var #'ptv-audit-can-access-get-ptv-integration-candidates-test)
  (clojure.test/run-test-var #'ptv-audit-can-access-fetch-ptv-org-test)
  (clojure.test/run-test-var #'ptv-audit-can-access-fetch-ptv-services-test)
  (clojure.test/run-test-var #'ptv-audit-can-access-fetch-ptv-service-channels-test)
  (clojure.test/run-test-var #'ptv-audit-can-access-fetch-ptv-service-collections-test)
  (clojure.test/run-test-var #'regular-user-cannot-access-ptv-endpoints-test)
  (clojure.test/run-test-var #'ptv-audit-cannot-modify-ptv-data-test))
