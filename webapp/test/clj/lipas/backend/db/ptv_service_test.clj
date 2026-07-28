(ns lipas.backend.db.ptv-service-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lipas.backend.db.ptv-service :as ptv-service-db]
            [lipas.backend.org :as backend-org]
            [lipas.test-utils :as tu]))

(defonce test-system (atom nil))

;;; Fixtures ;;;

(let [{:keys [once each]} (tu/full-system-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn test-db [] (:lipas/db @test-system))

;;; Helpers ;;;

(def ^:private ptv-org-id "7fdd7f84-e52a-4c17-a59a-d7c2a3095ed5")

(defn- seed-org! []
  (let [org {:id (java.util.UUID/randomUUID)
             :name (str "PTV Service Test Org " (System/currentTimeMillis) "-" (rand-int 100000))
             :data {}
             :ptv-data {:org-id ptv-org-id
                        :city-codes [889]
                        :owners ["city"]
                        :supported-languages ["fi"]}}]
    (backend-org/create-org (test-db) org)
    org))

(defn- rev
  [org-id source-id event-date & [doc-extra]]
  {:org-id (:id org-id)
   :source-id source-id
   :service-id nil
   :author-id nil
   :event-date event-date
   :document (merge {:source-id source-id
                     :summary {:fi "Tiivistelmä"}
                     :description {:fi "Kuvaus"}}
                    doc-extra)})

;;; Tests ;;;

(deftest insert-and-get-current-roundtrip-test
  (testing "Inserted document round-trips through the current view with keywordized keys"
    (let [org (seed-org!)
          source-id (str "lipas-" ptv-org-id "-1300")
          svc-id (java.util.UUID/randomUUID)
          _ (ptv-service-db/insert-service-rev!
              (test-db)
              {:org-id (:id org)
               :source-id source-id
               :service-id svc-id
               :author-id nil
               :event-date "2026-01-01T00:00:00.000Z"
               :document {:source-id source-id
                          :name {:fi "Pallokentät"}
                          :summary {:fi "Tiivistelmä"}
                          :description {:fi "Kuvaus"}
                          :audit {:summary {:status "approved" :feedback "ok"}}}})
          current (ptv-service-db/get-current (test-db) (:id org) source-id)]
      (is (some? current))
      (is (= "active" (:status current)))
      (is (= svc-id (:service-id current)))
      (is (= {:fi "Pallokentät"} (get-in current [:document :name])))
      (is (= "approved" (get-in current [:document :audit :summary :status])))
      (is (= current (ptv-service-db/get-current-by-service-id (test-db) (:id org) svc-id))))))

(deftest current-view-returns-latest-revision-test
  (testing "ptv_service_current returns the latest revision per lineage; history keeps all"
    (let [org (seed-org!)
          source-id (str "lipas-" ptv-org-id "-2100")
          _ (ptv-service-db/insert-service-rev!
              (test-db) (rev org source-id "2026-01-01T00:00:00.000Z" {:summary {:fi "eka"}}))
          _ (ptv-service-db/insert-service-rev!
              (test-db) (rev org source-id "2026-01-02T00:00:00.000Z" {:summary {:fi "toka"}}))
          current (ptv-service-db/get-current (test-db) (:id org) source-id)
          history (ptv-service-db/get-history (test-db) (:id org) source-id)]
      (is (= {:fi "toka"} (get-in current [:document :summary])))
      (is (= 2 (count history)))
      (is (= 1 (count (ptv-service-db/get-current-by-org (test-db) (:id org))))))))

(deftest lineages-are-independent-test
  (testing "Distinct source-ids and orgs form independent lineages"
    (let [org-a (seed-org!)
          org-b (seed-org!)
          source-1 (str "lipas-" ptv-org-id "-1300")
          source-2 (str "lipas-" ptv-org-id "-2100")
          _ (ptv-service-db/insert-service-rev!
              (test-db) (rev org-a source-1 "2026-01-01T00:00:00.000Z"))
          _ (ptv-service-db/insert-service-rev!
              (test-db) (rev org-a source-2 "2026-01-01T00:00:00.000Z"))
          _ (ptv-service-db/insert-service-rev!
              (test-db) (rev org-b source-1 "2026-01-01T00:00:00.000Z"))]
      (is (= 2 (count (ptv-service-db/get-current-by-org (test-db) (:id org-a)))))
      (is (= 1 (count (ptv-service-db/get-current-by-org (test-db) (:id org-b)))))
      (is (= 1 (count (ptv-service-db/get-history (test-db) (:id org-b) source-1)))))))

(deftest required-columns-test
  ;; org_id has intentionally NO foreign key (feat/org-management turns org
  ;; into an append-only revision table whose current view can't be an FK
  ;; target), but it must always be present.
  (testing "Insert without org-id violates NOT NULL and throws"
    (is (thrown? Exception
                 (ptv-service-db/insert-service-rev!
                   (test-db)
                   {:org-id nil
                    :source-id "lipas-x-1"
                    :service-id nil
                    :author-id nil
                    :event-date "2026-01-01T00:00:00.000Z"
                    :document {:source-id "lipas-x-1"}}))))

  (testing "Insert with unknown author-id violates the account FK and throws"
    (let [org (seed-org!)]
      (is (thrown? Exception
                   (ptv-service-db/insert-service-rev!
                     (test-db)
                     {:org-id (:id org)
                      :source-id "lipas-x-1"
                      :service-id nil
                      :author-id (java.util.UUID/randomUUID)
                      :event-date "2026-01-01T00:00:00.000Z"
                      :document {:source-id "lipas-x-1"}}))))))
