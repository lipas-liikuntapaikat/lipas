(ns lipas.backend.ptv.handler-test
  (:require [cheshire.core]
            [clojure.test :refer [deftest is use-fixtures testing]]
            [lipas.backend.core :as core]
            [lipas.backend.jwt :as jwt]
            [lipas.test-utils :refer [<-json ->json app db search] :as tu]
            [ring.mock.request :as mock]))

;;; Fixtures ;;;

(use-fixtures :once (fn [f]
                      (tu/init-db!)
                      (f)))

(use-fixtures :each (fn [f]
                      (tu/prune-db!)
                      (tu/prune-es!)
                      (f)))

;;; Helper Functions ;;;

(defn- create-test-site-with-ptv
  "Creates a test sports site with basic PTV data for testing"
  [user]
  (let [site (-> (tu/gen-sports-site)
                 (assoc :status "active")
                 (assoc :author user)
                 (assoc-in [:type :type-code] 1210) ; yleisurheilukenttä
                 (assoc-in [:location :postal-code] "00100")
                 (assoc-in [:location :city :city-code] 91)
                 (assoc :ptv {:org-id "test-org-id"
                              :sync-enabled true
                              :summary {:fi "Test summary"
                                        :se "Test summary"
                                        :en "Test summary"}
                              :description {:fi "Test description"
                                            :se "Test description"
                                            :en "Test description"}
                              :service-channel-ids []
                              :service-ids []}))
        _ (core/upsert-sports-site!* db user site)]
    site))

(defn- gen-ptv-audit-user
  "Generates a user with PTV audit privileges"
  []
  (let [user (-> (tu/gen-user {:db? false :admin? false})
                 (assoc :permissions {:roles [{:role :ptv-auditor}]}))]
    (core/add-user! db user)
    (core/get-user db (:email user))))

(defn- gen-admin-user
  "Generates an admin user who can perform PTV audits"
  []
  (tu/gen-user {:db? true :admin? true}))

(defn- safe-parse-json
  "Safely parses JSON response body, handling nil responses"
  [response]
  (when-let [body (:body response)]
    (cond
      (string? body) (cheshire.core/parse-string body true)
      (map? body) body ; Already parsed
      (instance? java.io.InputStream body)
      (let [body-str (slurp body)]
        (when-not (empty? body-str)
          (cheshire.core/parse-string body-str true)))
      :else body)))

;;; Tests ;;;

(deftest save-ptv-audit-success-test
  (testing "Successfully saves PTV audit with valid data"
    (let [admin-user (gen-admin-user)
          ptv-auditor (gen-ptv-audit-user)
          site (create-test-site-with-ptv admin-user)
          lipas-id (:lipas-id site)
          token (jwt/create-token ptv-auditor)
          audit-data {:status "approved"
                      :feedback "Site information looks good"}

          resp (app (-> (mock/request :post "/api/actions/save-ptv-audit")
                        (mock/content-type "application/json")
                        (mock/body (->json {:lipas-id lipas-id
                                            :audit {:summary audit-data
                                                    :description audit-data}}))
                        (tu/token-header token)))

          body (safe-parse-json resp)]

      (is (= 200 (:status resp)))
      (is (some? body))
      (is (contains? body :timestamp))
      (is (contains? body :auditor-id))
      (is (= (str (:id ptv-auditor)) (:auditor-id body)))
      (is (= audit-data (get-in body [:summary])))
      (is (= audit-data (get-in body [:description]))))))

(deftest save-ptv-audit-updates-site-test
  (testing "PTV audit is properly saved to sports site and indexed"
    (let [admin-user (gen-admin-user)
          ptv-auditor (gen-ptv-audit-user)
          site (create-test-site-with-ptv admin-user)
          lipas-id (:lipas-id site)
          token (jwt/create-token ptv-auditor)
          audit-data {:status "approved"
                      :feedback "Comprehensive review completed"}

          _ (app (-> (mock/request :post "/api/actions/save-ptv-audit")
                     (mock/content-type "application/json")
                     (mock/body (->json {:lipas-id lipas-id
                                         :audit {:summary audit-data}}))
                     (tu/token-header token)))

          ;; Verify the site was updated in database
          updated-site (core/get-sports-site db lipas-id)

          ;; Verify the site was updated in Elasticsearch
          es-site (core/get-sports-site2 search lipas-id)]

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
    (let [regular-user (tu/gen-user {:db? true :admin? false})
          admin-user (gen-admin-user)
          site (create-test-site-with-ptv admin-user)
          lipas-id (:lipas-id site)
          token (jwt/create-token regular-user)
          audit-data {:status "approved" :feedback "Test"}

          resp (app (-> (mock/request :post "/api/actions/save-ptv-audit")
                        (mock/content-type "application/json")
                        (mock/body (->json {:lipas-id lipas-id
                                            :audit {:summary audit-data}}))
                        (tu/token-header token)))]

      (is (= 403 (:status resp))))))

(deftest save-ptv-audit-nonexistent-site-test
  (testing "Returns 404 for non-existent site"
    (let [ptv-auditor (gen-ptv-audit-user)
          token (jwt/create-token ptv-auditor)
          nonexistent-id 999999
          audit-data {:status "approved" :feedback "Test"}

          resp (app (-> (mock/request :post "/api/actions/save-ptv-audit")
                        (mock/content-type "application/json")
                        (mock/body (->json {:lipas-id nonexistent-id
                                            :audit {:summary audit-data}}))
                        (tu/token-header token)))]
      (is (= 404 (:status resp))))))

(deftest save-ptv-audit-empty-audit-data-test
  (testing "Handles empty audit data gracefully"
    (let [admin-user (gen-admin-user)
          ptv-auditor (gen-ptv-audit-user)
          site (create-test-site-with-ptv admin-user)
          lipas-id (:lipas-id site)
          token (jwt/create-token ptv-auditor)

          resp (app (-> (mock/request :post "/api/actions/save-ptv-audit")
                        (mock/content-type "application/json")
                        (mock/body (->json {:lipas-id lipas-id
                                            :audit {}}))
                        (tu/token-header token)))]
      (is (= 200 (:status resp))))))

(deftest save-ptv-audit-schema-compliant-test
  (testing "Saves audit data that complies with closed schema"
    (let [admin-user (gen-admin-user)
          ptv-auditor (gen-ptv-audit-user)
          site (create-test-site-with-ptv admin-user)
          lipas-id (:lipas-id site)
          token (jwt/create-token ptv-auditor)
          valid-summary {:status "changes-requested"
                         :feedback "Summary field needs improvement - missing Finnish translation and contact details are outdated"}
          valid-description {:status "approved"
                             :feedback "Description is comprehensive and accurate"}

          resp (app (-> (mock/request :post "/api/actions/save-ptv-audit")
                        (mock/content-type "application/json")
                        (mock/body (->json {:lipas-id lipas-id
                                            :audit {:summary valid-summary
                                                    :description valid-description}}))
                        (tu/token-header token)))

          body (safe-parse-json resp)]

      (is (= 200 (:status resp)))
      (is (= valid-summary (:summary body)))
      (is (= valid-description (:description body)))
      (is (contains? body :timestamp))
      (is (contains? body :auditor-id)))))

(deftest save-ptv-audit-invalid-extra-fields-test
  (testing "Strips extra fields from audit data due to closed schema"
    (let [admin-user (gen-admin-user)
          ptv-auditor (gen-ptv-audit-user)
          site (create-test-site-with-ptv admin-user)
          lipas-id (:lipas-id site)
          token (jwt/create-token ptv-auditor)
          audit-with-extra {:status "changes-requested"
                            :feedback "Valid feedback"
                            :extra-field "This should be stripped"} ; Extra field not in schema

          resp (app (-> (mock/request :post "/api/actions/save-ptv-audit")
                        (mock/content-type "application/json")
                        (mock/body (->json {:lipas-id lipas-id
                                            :audit {:summary audit-with-extra}}))
                        (tu/token-header token)))

          body (safe-parse-json resp)]

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
    (let [admin-user (gen-admin-user)
          ptv-auditor (gen-ptv-audit-user)
          site (create-test-site-with-ptv admin-user)
          lipas-id (:lipas-id site)
          token (jwt/create-token ptv-auditor)
          invalid-audit {:status "invalid-status" ; Not in enum
                         :feedback "Valid feedback"}

          resp (app (-> (mock/request :post "/api/actions/save-ptv-audit")
                        (mock/content-type "application/json")
                        (mock/body (->json {:lipas-id lipas-id
                                            :audit {:summary invalid-audit}}))
                        (tu/token-header token)))]

      (is (= 400 (:status resp))))))

(deftest save-ptv-audit-feedback-too-long-test
  (testing "Rejects audit data with feedback exceeding max length"
    (let [admin-user (gen-admin-user)
          ptv-auditor (gen-ptv-audit-user)
          site (create-test-site-with-ptv admin-user)
          lipas-id (:lipas-id site)
          token (jwt/create-token ptv-auditor)
          invalid-audit {:status "approved"
                         :feedback (apply str (repeat 1001 "x"))} ; Too long (>1000 chars)

          resp (app (-> (mock/request :post "/api/actions/save-ptv-audit")
                        (mock/content-type "application/json")
                        (mock/body (->json {:lipas-id lipas-id
                                            :audit {:summary invalid-audit}}))
                        (tu/token-header token)))]

      (is (= 400 (:status resp))))))

(deftest save-ptv-audit-overwrites-previous-test
  (testing "New audit data overwrites previous audit data"
    (let [admin-user (gen-admin-user)
          ptv-auditor (gen-ptv-audit-user)
          site (create-test-site-with-ptv admin-user)
          lipas-id (:lipas-id site)
          token (jwt/create-token ptv-auditor)
          first-audit {:status "approved" :feedback "First review"}

          ;; Save first audit
          _ (app (-> (mock/request :post "/api/actions/save-ptv-audit")
                     (mock/content-type "application/json")
                     (mock/body (->json {:lipas-id lipas-id
                                         :audit {:summary first-audit}}))
                     (tu/token-header token)))

          second-audit {:status "changes-requested" :feedback "Second review"}

          ;; Save second audit
          resp (app (-> (mock/request :post "/api/actions/save-ptv-audit")
                        (mock/content-type "application/json")
                        (mock/body (->json {:lipas-id lipas-id
                                            :audit {:summary second-audit}}))
                        (tu/token-header token)))

          body (safe-parse-json resp)
          updated-site (core/get-sports-site db lipas-id)]

      (is (= 200 (:status resp)))
      (is (= second-audit (:summary body)))
      (is (= second-audit (get-in updated-site [:ptv :audit :summary])))
      ;; Verify first audit is completely replaced
      (is (not= first-audit (get-in updated-site [:ptv :audit :summary]))))))

(deftest save-ptv-audit-invalid-lipas-id-test
  (testing "Validates lipas-id parameter"
    (let [ptv-auditor (gen-ptv-audit-user)
          token (jwt/create-token ptv-auditor)
          audit-data {:status "approved" :feedback "Test"}

          resp (app (-> (mock/request :post "/api/actions/save-ptv-audit")
                        (mock/content-type "application/json")
                        (mock/body (->json {:lipas-id "not-a-number" ; Invalid
                                            :audit {:summary audit-data}}))
                        (tu/token-header token)))]

      (is (= 400 (:status resp))))))

(deftest save-ptv-audit-invalid-audit-schema-test
  (testing "Validates audit parameter against schema"
    (let [admin-user (gen-admin-user)
          ptv-auditor (gen-ptv-audit-user)
          site (create-test-site-with-ptv admin-user)
          lipas-id (:lipas-id site)
          token (jwt/create-token ptv-auditor)

          resp (app (-> (mock/request :post "/api/actions/save-ptv-audit")
                        (mock/content-type "application/json")
                        (mock/body (->json {:lipas-id lipas-id
                                            :audit "not-a-map"})) ; Invalid
                        (tu/token-header token)))]

      (is (= 400 (:status resp))))))

(deftest save-ptv-audit-no-auth-token-test
  (testing "Requires authentication token"
    (let [admin-user (gen-admin-user)
          site (create-test-site-with-ptv admin-user)
          lipas-id (:lipas-id site)
          audit-data {:status "approved" :feedback "Test"}

          resp (app (-> (mock/request :post "/api/actions/save-ptv-audit")
                        (mock/content-type "application/json")
                        (mock/body (->json {:lipas-id lipas-id
                                            :audit {:summary audit-data}}))))]

      (is (= 401 (:status resp))))))

(deftest save-ptv-audit-updates-event-date-test
  (testing "Updates the sports site event-date when audit is saved"
    (let [admin-user (gen-admin-user)
          ptv-auditor (gen-ptv-audit-user)
          site (create-test-site-with-ptv admin-user)
          lipas-id (:lipas-id site)
          original-date (:event-date site)
          token (jwt/create-token ptv-auditor)
          audit-data {:status "approved" :feedback "Event date test"}

          ;; Add small delay to ensure timestamp difference
          _ (Thread/sleep 100)

          _ (app (-> (mock/request :post "/api/actions/save-ptv-audit")
                     (mock/content-type "application/json")
                     (mock/body (->json {:lipas-id lipas-id
                                         :audit {:summary audit-data}}))
                     (tu/token-header token)))

          updated-site (core/get-sports-site db lipas-id)]

      (is (not= original-date (:event-date updated-site))))))

(comment
  (clojure.test/run-tests *ns*)
  (clojure.test/run-test-var #'save-ptv-audit-success-test)
  (clojure.test/run-test-var #'save-ptv-audit-no-auth-token-test)
  (clojure.test/run-test-var #'save-ptv-audit-invalid-extra-fields-test)
  (clojure.test/run-test-var #'save-ptv-audit-invalid-audit-schema-test)
  (clojure.test/run-test-var #'save-ptv-audit-empty-audit-data-test)
  )
