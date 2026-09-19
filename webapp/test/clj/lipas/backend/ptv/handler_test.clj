(ns lipas.backend.ptv.handler-test
  (:require [cheshire.core]
            [clojure.test :refer [deftest is use-fixtures testing]]
            [lipas.backend.core :as core]
            [lipas.backend.db.ptv-service :as ptv-service-db]
            [lipas.backend.db.ptv-service-audit :as ptv-service-audit-db]
            [lipas.backend.db.ptv-site-audit :as ptv-site-audit-db]
            [lipas.backend.jwt :as jwt]
            [lipas.backend.org :as backend-org]
            [lipas.backend.ptv.core :as ptv-core]
            [lipas.backend.ptv.integration :as ptv-integration]
            [lipas.data.ptv :as ptv-data]
            [lipas.data.types :as types]
            [lipas.test-utils :as tu]
            [lipas.utils :as utils]
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

(defn- fetch-site-audits
  "/actions/fetch-ptv-site-audits as the UI calls it: lipas-id -> audit."
  [token lipas-ids]
  (let [resp (test-app (-> (mock/request :post "/api/actions/fetch-ptv-site-audits")
                           (mock/content-type "application/json")
                           (mock/body (tu/->json {:lipas-ids lipas-ids}))
                           (tu/token-header token)))]
    (is (= 200 (:status resp)))
    (into {} (map (juxt :lipas-id :audit)) (tu/safe-parse-json resp))))

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
                    :service-ids []}}
        saved (core/upsert-sports-site!* (test-db) user site)]
    (core/index! (test-search) saved :sync)
    saved))

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
  (testing "PTV audit is stored in ptv_site_audit and joined into the indexed site"
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

          stored (ptv-site-audit-db/get-current (test-db) lipas-id)
          db-site (core/get-sports-site (test-db) lipas-id)
          history (core/get-sports-site-history (test-db) lipas-id)
          es-site (core/get-sports-site2 (test-search) lipas-id)
          served (fetch-site-audits token [lipas-id])]

      (is (= audit-data (:summary stored)))
      (is (some? (:timestamp stored)))
      (is (= (str (:id ptv-auditor)) (:auditor-id stored)))
      ;; provenance: the audit points at the revision it was given on
      (is (= (:id (meta db-site))
             (:site-revision-id (first (ptv-site-audit-db/get-history (test-db) lipas-id)))))

      ;; the site document is untouched: no revision, no event-date change
      (is (= 1 (count history)))
      (is (= (:event-date site) (:event-date db-site)))
      (is (nil? (get-in db-site [:ptv :audit])))

      ;; ...nor the index; the UI fetches audits apart and merges them in
      (is (nil? (get-in es-site [:ptv :audit])))
      (is (= (:event-date site) (:event-date es-site)))
      (is (= stored (get served lipas-id))))))

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
          stored (ptv-site-audit-db/get-current (test-db) lipas-id)
          served (fetch-site-audits token [lipas-id])]

      (is (= 200 (:status resp)))
      (is (= second-audit (:summary body)))
      ;; append-only table, latest row wins everywhere
      (is (= second-audit (:summary stored)))
      (is (= second-audit (get-in served [lipas-id :summary])))
      (is (= 2 (count (ptv-site-audit-db/get-history (test-db) lipas-id)))))))

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

(deftest audit-save-keeps-site-in-sync-test
  ;; The DVV report behind the ptv_site_audit move: an audit save used to
  ;; append a site revision with a new :event-date, which the PTV views
  ;; compare against :last-sync — so every audited site read "out of date".
  (testing "A synced site still reads :ok in the PTV candidates listing after an audit"
    (let [admin (tu/gen-admin-user :db-component (test-db))
          auditor (tu/gen-ptv-auditor :db-component (test-db))
          synced-at "2026-05-14T15:34:16.385461Z"
          site (core/upsert-sports-site!*
                 (test-db) admin
                 (-> (create-test-site-with-ptv admin)
                     (assoc :event-date synced-at)
                     (update :ptv assoc :last-sync synced-at :service-channel-ids ["chan-1"])))
          lipas-id (:lipas-id site)
          _ (core/index! (test-search) site :sync (core/index-context (test-db)))
          candidates (fn []
                       (let [resp (test-app (-> (mock/request :post "/api/actions/get-ptv-integration-candidates")
                                                (mock/content-type "application/json")
                                                (mock/body (tu/->json {:city-codes [91] :owners ["city"]}))
                                                (tu/token-header (jwt/create-token auditor))))
                             body (tu/safe-parse-json resp)]
                         (is (= 200 (:status resp)))
                         (some #(when (= lipas-id (:lipas-id %)) %) body)))
          sync-status (fn [candidate]
                        (:sync-status (ptv-data/sports-site->ptv-input
                                        {:types types/all :org-id "test-org-id" :org-langs ["fi"]}
                                        {} {} candidate)))
          before (candidates)
          _ (test-app (-> (mock/request :post "/api/actions/save-ptv-audit")
                          (mock/content-type "application/json")
                          (mock/body (tu/->json {:lipas-id lipas-id
                                                 :audit {:summary {:status "approved" :feedback ""}}}))
                          (tu/token-header (jwt/create-token auditor))))
          after (candidates)]
      (is (= :ok (sync-status before)))
      (is (= :ok (sync-status after)))
      (is (= synced-at (:event-date after)))
      (is (nil? (get-in after [:ptv :audit])) "audits are a separate fetch")
      (is (= "approved" (get-in (fetch-site-audits (jwt/create-token auditor) [lipas-id])
                                [lipas-id :summary :status]))
          "the UI merges this into the listing it derives its status symbols from"))))

(deftest save-ptv-audit-keeps-event-date-test
  ;; An audit is information about the site, not a content change: the
  ;; PTV views compare :event-date with :last-sync to tell whether a site
  ;; is in sync, so an audit save moving :event-date read as "out of date"
  ;; on every audited site.
  (testing "Does not touch the sports site event-date when audit is saved"
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

          updated-site (core/get-sports-site (test-db) lipas-id)
          es-site (core/get-sports-site2 (test-search) lipas-id)]

      (is (= original-date (:event-date updated-site)))
      (is (= original-date (:event-date es-site))))))

;;; PTV Audit Access Tests ;;;

;; The UI reads the audit off the indexed site ([:ptv :audit]) and
;; round-trips the whole document back on save, so the generic site save
;; must drop it: the audit lives in ptv_site_audit, never in a revision.
(deftest generic-site-save-drops-audit-test
  (let [admin (tu/gen-admin-user :db-component (test-db))
        auditor (tu/gen-ptv-auditor :db-component (test-db))
        site (create-test-site-with-ptv admin)
        lipas-id (:lipas-id site)
        save! (fn [site]
                (core/upsert-sports-site! (test-db) admin
                                          (assoc site :event-date (utils/timestamp)))
                (core/get-sports-site (test-db) lipas-id))
        forged {:timestamp "2026-01-01T00:00:00.000Z"
                :auditor-id "me"
                :summary {:status "approved" :feedback ""}}]

    (testing "a submitted audit never lands in the document"
      (is (nil? (get-in (save! (assoc-in site [:ptv :audit] forged)) [:ptv :audit]))))

    (let [audit (ptv-core/save-ptv-audit
                  (test-db) auditor
                  {:lipas-id lipas-id
                   :audit {:summary {:status "changes-requested"
                                     :feedback "Tarkenna"
                                     :audited-content {:fi "Test summary"}}}})
          ;; what the UI holds: the site from the index with the separately
          ;; fetched audit merged in
          client-copy (-> (core/get-sports-site2 (test-search) lipas-id)
                          (assoc-in [:ptv :audit] audit))]

      (testing "round-tripping the cached site saves its content and leaves the audit where it is"
        (let [after (save! (-> client-copy
                               (dissoc :search-meta)
                               (assoc :name "Renamed")
                               (assoc-in [:ptv :summary :fi] "Fixed summary")))]
          (is (nil? (get-in after [:ptv :audit])))
          (is (= "Renamed" (:name after)))
          (is (= "Fixed summary" (get-in after [:ptv :summary :fi])))
          (is (= audit (ptv-site-audit-db/get-current (test-db) lipas-id)))))

      (testing "a forged audit in the body cannot replace the stored one"
        (save! (assoc-in site [:ptv :audit] forged))
        (is (= audit (ptv-site-audit-db/get-current (test-db) lipas-id)))))))

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

(deftest save-ptv-meta-preserves-lifecycle-keys-test
  (testing "save-ptv-meta turns sync off without wiping server-owned lifecycle keys"
    ;; Contract the Liikuntapaikat-tab toggle relies on: turning the switch off
    ;; persists sync-enabled=false via save-ptv-meta (no PTV call) AND keeps the
    ;; channel link plus the lifecycle keys (:source-id, :publishing-status,
    ;; :previous-type-code) the sync path owns. A blanket :ptv replace used to
    ;; wipe those, breaking is-sent-to-ptv? and the reversible-archive flow.
    (let [admin (tu/gen-admin-user :db-component (test-db))
          token (jwt/create-token admin)
          channel "b0000000-0000-4000-8000-000000000abc"
          source-id (str "lipas-" ptv-org-id "-700001-2026-01-01T00-00-00.000Z")
          texts {:summary {:fi "Kesäkäyttöinen urheilukenttä."}
                 :description {:fi "Limingan keskustan urheilukenttä."}}
          ;; a pre-move revision still carrying its audit in the document
          legacy-audit {:timestamp "2026-01-03T00:00:00.000Z"
                        :auditor-id "auditor-1"
                        :summary {:status "changes-requested"
                                  :feedback "Tarkenna tekstiä"
                                  :audited-content {:fi "Kesäkäyttöinen urheilukenttä."}}}
          ;; A published, synced site carrying the full lifecycle meta.
          site (core/upsert-sports-site!*
                 (test-db) admin
                 {:status "active"
                  :event-date "2026-01-02T00:00:00.000Z"
                  :name "Limingan urheilukenttä"
                  :owner "city" :admin "city-sports"
                  :type {:type-code 1210}
                  :location {:city {:city-code 425}
                             :address "Kentäntie 1" :postal-code "91900" :postal-office "Liminka"
                             :geometries {:type "FeatureCollection"
                                          :features [{:type "Feature"
                                                      :geometry {:type "Point" :coordinates [25.41 64.81]}}]}}
                  :ptv (merge texts
                              {:org-id ptv-org-id
                               :sync-enabled true
                               :last-sync "2026-01-02T00:00:00.000Z"
                               :source-id source-id
                               :publishing-status "Published"
                               :previous-type-code 1210
                               :service-ids []
                               :service-channel-ids [channel]
                               :audit legacy-audit})})
          lipas-id (:lipas-id site)
          resp (test-app (-> (mock/request :post "/api/actions/save-ptv-meta")
                             (mock/content-type "application/json")
                             (mock/body (tu/->json {lipas-id (merge texts
                                                                    {:org-id ptv-org-id
                                                                     :sync-enabled false
                                                                     :service-ids []
                                                                     :service-channel-ids [channel]})}))
                             (tu/token-header token)))
          after (:ptv (core/get-sports-site (test-db) lipas-id))]
      (is (= 200 (:status resp)))
      ;; editable key updated
      (is (false? (:sync-enabled after)))
      ;; server-owned lifecycle keys preserved (not wiped)
      (is (= source-id (:source-id after)))
      (is (= "Published" (:publishing-status after)))
      (is (= 1210 (:previous-type-code after)))
      ;; channel link preserved (frozen in PTV, not unlinked)
      (is (= [channel] (:service-channel-ids after)))
      ;; audits live in ptv_site_audit now; a legacy in-document copy is not rebuilt
      (is (nil? (:audit after))))))

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

;;; PTV Service audits (ptv_service_audit table, anchored to ptv_service revisions) ;;;

(def ^:private svc-ptv-org-id "8f1c2a3b-1111-4abc-9def-000000000010")

(defn- seed-service-org!
  "Seeds a LIPAS org with PTV config; returns the org map (with :id)."
  []
  (let [org {:id (java.util.UUID/randomUUID)
             :name (str "PTV Service Audit Org " (System/currentTimeMillis) "-" (rand-int 100000))
             :data {}
             :ptv-data {:org-id svc-ptv-org-id
                        :city-codes [91]
                        :owners ["city"]
                        :supported-languages ["fi"]}}]
    (backend-org/create-org (test-db) org)
    org))

(defn- seed-service-rev!
  [org {:keys [source-id service-id summary]}]
  (ptv-service-db/insert-service-rev!
    (test-db)
    {:org-id (:id org)
     :source-id source-id
     :service-id service-id
     :author-id nil
     :event-date "2026-01-01T00:00:00.000Z"
     :document {:source-id source-id
                :service-id service-id
                :name {:fi "Pallokentät"}
                :summary (or summary {:fi "Tiivistelmä"})
                :description {:fi "Kuvaus"}
                :user-instruction {:fi "Toimintaohje"}}}))

(defn- post-service-audit
  [token body]
  (test-app (-> (mock/request :post "/api/actions/save-ptv-service-audit")
                (mock/content-type "application/json")
                (mock/body (tu/->json body))
                (tu/token-header token))))

(deftest save-ptv-service-audit-existing-revision-test
  (testing "Saves audit onto the latest existing ptv_service revision"
    (let [org (seed-service-org!)
          auditor (tu/gen-ptv-auditor :db-component (test-db))
          token (jwt/create-token auditor)
          svc-id (str (java.util.UUID/randomUUID))
          source-id (str "lipas-" svc-ptv-org-id "-1300")
          rev (seed-service-rev! org {:source-id source-id :service-id svc-id})
          audit-data {:status "approved" :feedback "Hyvä kuvaus"}
          resp (post-service-audit token {:org-id (str (:id org))
                                          :service-id svc-id
                                          :source-id source-id
                                          :audit {:summary audit-data
                                                  :description audit-data
                                                  :user-instruction audit-data}})
          body (tu/safe-parse-json resp)
          stored (first (ptv-service-audit-db/get-history (test-db) (:id org) source-id))
          current (ptv-service-db/get-current (test-db) (:id org) source-id)]
      (is (= 200 (:status resp)))
      (is (contains? body :timestamp))
      (is (= (str (:id auditor)) (:auditor-id body)))
      (is (= audit-data (:summary body)))
      (is (= audit-data (:user-instruction body)))
      ;; Audit stored in ptv_service_audit, anchored to the revision it was given on
      (is (= body (:document stored)))
      (is (= (:id rev) (:service-revision-id stored)))
      (is (= (parse-uuid svc-id) (:service-id stored)))
      ;; The ptv_service lineage is untouched: no audit revision appended
      (is (= 1 (count (ptv-service-db/get-history (test-db) (:id org) source-id))))
      (is (nil? (get-in current [:document :audit]))))))

(deftest save-ptv-service-audit-lazy-initial-revision-test
  (testing "Creates the initial revision from a live PTV fetch when none exists"
    (let [org (seed-service-org!)
          auditor (tu/gen-ptv-auditor :db-component (test-db))
          token (jwt/create-token auditor)
          svc-id (str (java.util.UUID/randomUUID))
          source-id (str "lipas-" svc-ptv-org-id "-2100")
          audit-data {:status "changes-requested" :feedback "Tarkenna kuvausta"}
          resp (with-redefs [ptv-integration/get-service
                             (fn [_ _ _]
                               {:id svc-id
                                :sourceId source-id
                                :serviceNames [{:type "Name" :language "fi" :value "Kuntosalit"}]
                                :serviceDescriptions [{:type "Summary" :language "fi" :value "Tiivistelmä"}
                                                      {:type "Description" :language "fi" :value "Kuvaus"}]
                                :languages ["fi"]
                                :publishingStatus "Published"})]
                 (post-service-audit token {:org-id (str (:id org))
                                            :service-id svc-id
                                            :audit {:summary audit-data}}))
          body (tu/safe-parse-json resp)
          current (ptv-service-db/get-current (test-db) (:id org) source-id)
          stored (first (ptv-service-audit-db/get-history (test-db) (:id org) source-id))]
      (is (= 200 (:status resp)))
      (is (= audit-data (:summary body)))
      (is (some? current))
      ;; Document extracted from the live PTV entity
      (is (= {:fi "Kuntosalit"} (get-in current [:document :name])))
      (is (= {:fi "Tiivistelmä"} (get-in current [:document :summary])))
      ;; ...written by the same path as every other revision
      (is (some? (get-in current [:document :last-sync])))
      ;; ...and the audit anchored to that initial revision
      (is (= "changes-requested" (get-in stored [:document :summary :status])))
      (is (= (:id current) (:service-revision-id stored))))))

(deftest save-ptv-service-audit-scoping-test
  (testing "An empty audit marks the service as part of the audit sample"
    (let [org (seed-service-org!)
          auditor (tu/gen-ptv-auditor :db-component (test-db))
          token (jwt/create-token auditor)
          svc-id (str (java.util.UUID/randomUUID))
          source-id (str "lipas-" svc-ptv-org-id "-1300")
          _ (seed-service-rev! org {:source-id source-id :service-id svc-id})
          resp (post-service-audit token {:org-id (str (:id org))
                                          :service-id svc-id
                                          :audit {}})
          body (tu/safe-parse-json resp)
          stored (ptv-service-audit-db/get-current (test-db) (:id org) source-id)]
      (is (= 200 (:status resp)))
      (is (contains? body :timestamp))
      (is (= (str (:id auditor)) (:auditor-id body)))
      ;; No verdicts, just the scope stamp
      (is (nil? (:summary body)))
      (is (some? (:timestamp stored)))
      (is (nil? (:summary stored))))))

(deftest save-ptv-service-audit-snapshot-test
  (testing "Per-field :audited-content snapshots round-trip"
    (let [org (seed-service-org!)
          auditor (tu/gen-ptv-auditor :db-component (test-db))
          token (jwt/create-token auditor)
          svc-id (str (java.util.UUID/randomUUID))
          source-id (str "lipas-" svc-ptv-org-id "-1300")
          _ (seed-service-rev! org {:source-id source-id :service-id svc-id})
          audit {:summary {:status "approved"
                           :feedback ""
                           :audited-content {:fi "Tiivistelmä"}}}
          resp (post-service-audit token {:org-id (str (:id org))
                                          :service-id svc-id
                                          :audit audit})
          body (tu/safe-parse-json resp)
          stored (ptv-service-audit-db/get-current (test-db) (:id org) source-id)]
      (is (= 200 (:status resp)))
      (is (= {:fi "Tiivistelmä"} (get-in body [:summary :audited-content])))
      (is (= {:fi "Tiivistelmä"}
             (get-in stored [:summary :audited-content]))))))

(deftest save-ptv-service-audit-not-found-test
  (testing "Returns 404 when the org is unknown"
    (let [auditor (tu/gen-ptv-auditor :db-component (test-db))
          token (jwt/create-token auditor)
          resp (post-service-audit token {:org-id (str (java.util.UUID/randomUUID))
                                          :service-id (str (java.util.UUID/randomUUID))
                                          :audit {:summary {:status "approved" :feedback ""}}})]
      (is (= 404 (:status resp)))))

  (testing "Returns 404 when the service has no sourceId (native, never adopted)"
    (let [org (seed-service-org!)
          auditor (tu/gen-ptv-auditor :db-component (test-db))
          token (jwt/create-token auditor)
          resp (with-redefs [ptv-integration/get-service (fn [_ _ _] {})]
                 (post-service-audit token {:org-id (str (:id org))
                                            :service-id (str (java.util.UUID/randomUUID))
                                            :audit {:summary {:status "approved" :feedback ""}}}))]
      (is (= 404 (:status resp))))))

(deftest save-ptv-service-audit-privilege-test
  (testing "Requires :ptv/audit privilege"
    (let [org (seed-service-org!)
          regular-user (tu/gen-user {:db? true :db-component (test-db) :admin? false})
          token (jwt/create-token regular-user)
          resp (post-service-audit token {:org-id (str (:id org))
                                          :service-id (str (java.util.UUID/randomUUID))
                                          :audit {:summary {:status "approved" :feedback ""}}})]
      (is (= 403 (:status resp)))))

  (testing "Requires authentication"
    (let [resp (test-app (-> (mock/request :post "/api/actions/save-ptv-service-audit")
                             (mock/content-type "application/json")
                             (mock/body (tu/->json {:org-id (str (java.util.UUID/randomUUID))
                                                    :service-id (str (java.util.UUID/randomUUID))
                                                    :audit {}}))))]
      (is (= 401 (:status resp))))))

(deftest save-ptv-service-audit-validation-test
  (testing "Rejects invalid status and overlong feedback"
    (let [org (seed-service-org!)
          auditor (tu/gen-ptv-auditor :db-component (test-db))
          token (jwt/create-token auditor)
          bad-status (post-service-audit token {:org-id (str (:id org))
                                                :service-id (str (java.util.UUID/randomUUID))
                                                :audit {:summary {:status "lgtm" :feedback ""}}})
          too-long (post-service-audit token {:org-id (str (:id org))
                                              :service-id (str (java.util.UUID/randomUUID))
                                              :audit {:summary {:status "approved"
                                                                :feedback (apply str (repeat 1001 "x"))}}})]
      (is (= 400 (:status bad-status)))
      (is (= 400 (:status too-long))))))

(deftest save-ptv-service-audit-last-write-wins-test
  (testing "Concurrent/subsequent audits: latest wins in current view, history keeps all"
    (let [org (seed-service-org!)
          auditor (tu/gen-ptv-auditor :db-component (test-db))
          token (jwt/create-token auditor)
          svc-id (str (java.util.UUID/randomUUID))
          source-id (str "lipas-" svc-ptv-org-id "-1300")
          _ (seed-service-rev! org {:source-id source-id :service-id svc-id})
          _ (post-service-audit token {:org-id (str (:id org))
                                       :service-id svc-id
                                       :audit {:summary {:status "approved" :feedback "Eka"}}})
          _ (Thread/sleep 1100) ;; utils/timestamp has second granularity in event ordering
          _ (post-service-audit token {:org-id (str (:id org))
                                       :service-id svc-id
                                       :audit {:summary {:status "changes-requested" :feedback "Toka"}}})
          stored (ptv-service-audit-db/get-current (test-db) (:id org) source-id)]
      (is (= "changes-requested" (get-in stored [:summary :status])))
      (is (= "Toka" (get-in stored [:summary :feedback])))
      (is (= 2 (count (ptv-service-audit-db/get-history (test-db) (:id org) source-id))))
      ;; audits never append ptv_service revisions
      (is (= 1 (count (ptv-service-db/get-history (test-db) (:id org) source-id)))))))

(deftest fetch-ptv-service-audits-test
  (testing "Returns the org's current service audits to auditors — nothing for unaudited services"
    (let [org (seed-service-org!)
          auditor (tu/gen-ptv-auditor :db-component (test-db))
          token (jwt/create-token auditor)
          svc-id (str (java.util.UUID/randomUUID))
          source-id (str "lipas-" svc-ptv-org-id "-1300")
          _ (seed-service-rev! org {:source-id source-id :service-id svc-id})
          resp (test-app (-> (mock/request :post "/api/actions/fetch-ptv-service-audits")
                             (mock/content-type "application/json")
                             (mock/body (tu/->json {:org-id (str (:id org))}))
                             (tu/token-header token)))]
      (is (= 200 (:status resp)))
      (is (= [] (tu/safe-parse-json resp)))))

  (testing "The current audit per service, keyed for the UI's merge"
    (let [org (seed-service-org!)
          auditor (tu/gen-ptv-auditor :db-component (test-db))
          token (jwt/create-token auditor)
          svc-id (str (java.util.UUID/randomUUID))
          source-id (str "lipas-" svc-ptv-org-id "-1300")
          _ (seed-service-rev! org {:source-id source-id :service-id svc-id})
          audit (tu/safe-parse-json
                  (post-service-audit token {:org-id (str (:id org))
                                             :service-id svc-id
                                             :audit {:summary {:status "approved" :feedback "Hyvä"}}}))
          resp (test-app (-> (mock/request :post "/api/actions/fetch-ptv-service-audits")
                             (mock/content-type "application/json")
                             (mock/body (tu/->json {:org-id (str (:id org))}))
                             (tu/token-header token)))
          body (tu/safe-parse-json resp)]
      (is (= 200 (:status resp)))
      (is (= [{:service-id svc-id :source-id source-id :audit audit}] body))))

  (testing "An audit row without a service id is served under the lineage's current one"
    (let [org (seed-service-org!)
          auditor (tu/gen-ptv-auditor :db-component (test-db))
          token (jwt/create-token auditor)
          svc-id (str (java.util.UUID/randomUUID))
          source-id (str "lipas-" svc-ptv-org-id "-1300")
          rev (seed-service-rev! org {:source-id source-id :service-id svc-id})
          audit {:timestamp "2026-07-01T00:00:00.000Z" :auditor-id (str (:id auditor))
                 :summary {:status "approved" :feedback "" :audited-content {:fi "Tiivistelmä"}}}
          _ (ptv-service-audit-db/insert-audit! (test-db)
                                                {:org-id (:id org)
                                                 :source-id source-id
                                                 :service-id nil ; audited before the PTV UUID was recorded
                                                 :service-revision-id (:id rev)
                                                 :auditor-id (:id auditor)
                                                 :event-date (:timestamp audit)
                                                 :document audit})
          resp (test-app (-> (mock/request :post "/api/actions/fetch-ptv-service-audits")
                             (mock/content-type "application/json")
                             (mock/body (tu/->json {:org-id (str (:id org))}))
                             (tu/token-header token)))]
      (is (= 200 (:status resp)))
      (is (= [{:service-id svc-id :source-id source-id :audit audit}]
             (tu/safe-parse-json resp)))))

  (testing "Regular users get 403"
    (let [org (seed-service-org!)
          regular-user (tu/gen-user {:db? true :db-component (test-db) :admin? false})
          token (jwt/create-token regular-user)
          resp (test-app (-> (mock/request :post "/api/actions/fetch-ptv-service-audits")
                             (mock/content-type "application/json")
                             (mock/body (tu/->json {:org-id (str (:id org))}))
                             (tu/token-header token)))]
      (is (= 403 (:status resp))))))

(deftest save-ptv-service-persists-revision-test
  (testing "Successful save-ptv-service shadows a revision into ptv_service"
    (let [org (seed-service-org!)
          admin (tu/gen-admin-user :db-component (test-db))
          token (jwt/create-token admin)
          svc-id (str (java.util.UUID/randomUUID))
          source-id (str "lipas-" svc-ptv-org-id "-1300")
          resp (with-redefs [ptv-integration/update-service
                             (fn [_ _ data] (assoc data :id svc-id))]
                 (test-app (-> (mock/request :post "/api/actions/save-ptv-service")
                               (mock/content-type "application/json")
                               (mock/body (tu/->json {:org-id svc-ptv-org-id
                                                      :city-codes [91]
                                                      :source-id source-id
                                                      :sub-category-id 1300
                                                      :languages ["fi"]
                                                      :summary {:fi "Tiivistelmä"}
                                                      :description {:fi "Kuvaus"}}))
                               (tu/token-header token))))
          current (ptv-service-db/get-current (test-db) (:id org) source-id)]
      (is (= 200 (:status resp)))
      (is (some? current))
      (is (= svc-id (str (:service-id current))))
      (is (= {:fi "Tiivistelmä"} (get-in current [:document :summary])))
      (is (some? (get-in current [:document :last-sync]))))))

(deftest save-ptv-service-keeps-audit-test
  (testing "A content sync via save-ptv-service leaves the audit (stored apart) intact"
    (let [org (seed-service-org!)
          admin (tu/gen-admin-user :db-component (test-db))
          auditor (tu/gen-ptv-auditor :db-component (test-db))
          svc-id (str (java.util.UUID/randomUUID))
          source-id (str "lipas-" svc-ptv-org-id "-1300")
          _ (seed-service-rev! org {:source-id source-id :service-id svc-id})
          _ (post-service-audit (jwt/create-token auditor)
                                {:org-id (str (:id org))
                                 :service-id svc-id
                                 :audit {:summary {:status "changes-requested" :feedback "Korjaa"}}})
          _ (Thread/sleep 1100)
          _ (with-redefs [ptv-integration/update-service
                          (fn [_ _ data] (assoc data :id svc-id))]
              (test-app (-> (mock/request :post "/api/actions/save-ptv-service")
                            (mock/content-type "application/json")
                            (mock/body (tu/->json {:org-id svc-ptv-org-id
                                                   :city-codes [91]
                                                   :source-id source-id
                                                   :sub-category-id 1300
                                                   :languages ["fi"]
                                                   :summary {:fi "Uusi tiivistelmä"}
                                                   :description {:fi "Uusi kuvaus"}}))
                            (tu/token-header (jwt/create-token admin)))))
          current (ptv-service-db/get-current (test-db) (:id org) source-id)
          stored (ptv-service-audit-db/get-current (test-db) (:id org) source-id)
          served (ptv-core/get-ptv-service-audits (test-db) (:id org))]
      ;; Content updated by the sync, no audit in the document...
      (is (= {:fi "Uusi tiivistelmä"} (get-in current [:document :summary])))
      (is (nil? (get-in current [:document :audit])))
      ;; ...the audit survives in its own table and is still served
      (is (= "changes-requested" (get-in stored [:summary :status])))
      (is (= "Korjaa" (get-in stored [:summary :feedback])))
      (is (= [stored] (map :audit served))))))

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
