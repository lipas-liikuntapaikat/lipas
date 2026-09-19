(ns lipas.migrations.ptv-service-audit-move-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lipas.backend.db.ptv-service :as ptv-service-db]
            [lipas.backend.db.ptv-service-audit :as ptv-service-audit-db]
            [lipas.migrations.ptv-service-audit-move :as sut]
            [lipas.test-utils :as tu]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(defonce test-system (atom nil))

(let [{:keys [once each]} (tu/db-only-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn test-db [] (:lipas/db @test-system))

(def t0 "2026-05-14T15:34:16.385461Z") ; content revision (sync)
(def t1 "2026-07-13T15:32:26.624179Z") ; first audit save
(def t2 "2026-08-31T09:53:13.130678Z") ; second audit save (a content sync carried it on afterwards)
(def t3 "2026-09-01T10:00:00.000000Z") ; content sync carrying the t2 audit

(defn- audit [ts status]
  {:timestamp ts
   :auditor-id "not-a-uuid" ; pre-move audits stored the id as a string
   :summary {:status status :feedback "" :audited-content {:fi "Tiivistelmä"}}})

(defn- rev!
  [org-id source-id svc-id event-date doc]
  (ptv-service-db/insert-service-rev!
    (test-db)
    {:org-id org-id :source-id source-id :service-id svc-id
     :status "active" :author-id nil :event-date event-date :document doc}))

(defn- audit-rows [org-id source-id]
  (jdbc/execute! (test-db)
                 ["SELECT event_date, service_id, service_revision_id
                   FROM ptv_service_audit WHERE org_id = ? AND source_id = ? ORDER BY event_date"
                  org-id source-id]
                 {:builder-fn rs/as-unqualified-maps}))

(deftest ptv-service-audit-move-test
  (let [org-id (java.util.UUID/randomUUID)
        svc-id (java.util.UUID/randomUUID)
        source-id "lipas-org-1300"
        base {:source-id source-id :service-id (str svc-id)
              :summary {:fi "Tiivistelmä"} :description {:fi "Kuvaus"} :last-sync t0}
        ;; the lineage exactly as the old code wrote it: content, audit
        ;; revision, audit revision, content sync carrying the last audit
        content (rev! org-id source-id svc-id t0 base)
        _ (rev! org-id source-id svc-id t1 (assoc base :audit (audit t1 "changes-requested")))
        _ (rev! org-id source-id svc-id t2 (assoc base :audit (audit t2 "approved")))
        _ (rev! org-id source-id svc-id t3 (assoc base :last-sync t3 :audit (audit t2 "approved")))
        ;; a lineage whose first revision was created by the audit save itself
        lazy-source "lipas-org-2100"
        lazy-svc (java.util.UUID/randomUUID)
        lazy-rev (rev! org-id lazy-source lazy-svc t1
                       {:source-id lazy-source :summary {:fi "S"} :audit (audit t1 "approved")})]

    (testing "dry run: one row per distinct audit, anchored to the revision the auditor saw"
      (let [{:keys [audits]} (sut/compute-plan (test-db))
            by-key (into {} (map (juxt (juxt :source_id #(str (.toInstant ^java.sql.Timestamp (:event_date %))))
                                       :service_revision_id))
                         audits)]
        (is (= 3 (count audits)))
        (is (= (:id content) (get by-key [source-id t1])))
        (is (= (:id content) (get by-key [source-id t2]))
            "the carried copy (t3) doesn't count as a separate audit")
        (is (= (:id lazy-rev) (get by-key [lazy-source t1]))
            "no earlier content revision -> the audit's own initial revision")))

    (sut/migrate-up {:db (test-db)})

    (testing "audits copied; latest current; service id and string auditor-id handled"
      (is (= 2 (count (audit-rows org-id source-id))))
      (is (= [svc-id svc-id] (map :service_id (audit-rows org-id source-id))))
      (is (= (audit t2 "approved") (ptv-service-audit-db/get-current (test-db) org-id source-id)))
      (is (= (audit t1 "approved") (ptv-service-audit-db/get-current (test-db) org-id lazy-source))))

    (testing "ptv_service readers no longer see the in-document audit"
      (is (nil? (get-in (ptv-service-db/get-current (test-db) org-id source-id) [:document :audit])))
      (is (every? #(nil? (get-in % [:document :audit]))
                  (ptv-service-db/get-history (test-db) org-id source-id))))

    (testing "idempotent"
      (is (empty? (:audits (sut/compute-plan (test-db)))))
      (sut/migrate-up {:db (test-db)})
      (is (= 2 (count (audit-rows org-id source-id)))))))
