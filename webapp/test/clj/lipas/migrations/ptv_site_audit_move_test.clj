(ns lipas.migrations.ptv-site-audit-move-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lipas.backend.core :as core]
            [lipas.backend.db.ptv-site-audit :as ptv-site-audit-db]
            [lipas.backend.ptv.audit :as ptv-audit]
            [lipas.migrations.ptv-site-audit-move :as sut]
            [lipas.test-utils :as tu]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(defonce test-system (atom nil))

(let [{:keys [once each]} (tu/db-only-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn test-db [] (:lipas/db @test-system))

(def t0 "2026-05-14T15:34:16.385461Z") ; last sync = content revision
(def t1 "2026-07-13T15:32:26.624179Z") ; first audit save
(def t2 "2026-08-31T09:53:13.130678Z") ; second audit save

(defn- audit [ts status]
  {:timestamp ts
   :auditor-id "not-a-uuid" ; pre-move audits stored the id as a string
   :summary {:status status :feedback "" :audited-content {:fi "Tiivistelmä"}}})

(defn- seed-site!
  "A synced site (event-date = last-sync) followed by two audit-only
   revisions, exactly as save-ptv-audit used to write them."
  [user lipas-id]
  (let [base {:lipas-id lipas-id
              :status "active"
              :event-date t0
              :name (str "Halli " lipas-id)
              :owner "city" :admin "city-sports"
              :type {:type-code 1210}
              :location {:city {:city-code 91}
                         :address "Katu 1" :postal-code "00100" :postal-office "Helsinki"
                         :geometries {:type "FeatureCollection"
                                      :features [{:type "Feature"
                                                  :geometry {:type "Point"
                                                             :coordinates [24.9 60.1]}}]}}
              :ptv {:org-id "ptv-org" :sync-enabled true :last-sync t0
                    :summary {:fi "Tiivistelmä"} :description {:fi "Kuvaus"}
                    :service-ids [] :service-channel-ids ["c1"]}}]
    (core/upsert-sports-site!* (test-db) user base)
    (core/upsert-sports-site!* (test-db) user (-> base
                                                  (assoc :event-date t1)
                                                  (assoc-in [:ptv :audit] (audit t1 "changes-requested"))))
    (core/upsert-sports-site!* (test-db) user (-> base
                                                  (assoc :event-date t2)
                                                  (assoc-in [:ptv :audit] (audit t2 "approved"))))))

(defn- current-row [lipas-id]
  (jdbc/execute-one! (test-db)
                     ["SELECT event_date, document->>'event-date' AS doc_event_date,
                              (document->'ptv'->'audit') IS NOT NULL AS has_audit
                       FROM sports_site_current WHERE lipas_id = ?" lipas-id]
                     {:builder-fn rs/as-unqualified-maps}))

(defn- audit-rows [lipas-id]
  (jdbc/execute! (test-db)
                 ["SELECT event_date, site_revision_id FROM ptv_site_audit WHERE lipas_id = ? ORDER BY event_date" lipas-id]
                 {:builder-fn rs/as-unqualified-maps}))

(defn- revision-id
  "Row id of the site's revision with the given document event-date."
  [lipas-id event-date]
  (:id (jdbc/execute-one! (test-db)
                          ["SELECT id FROM sports_site WHERE lipas_id = ? AND document->>'event-date' = ?"
                           lipas-id event-date]
                          {:builder-fn rs/as-unqualified-maps})))

(deftest ptv-site-audit-move-test
  (let [user (tu/gen-admin-user :db-component (test-db))
        lipas-id 9990101]
    (seed-site! user lipas-id)
    ;; a site whose revision carries an audit but whose event-date is a
    ;; content edit (audit carried over by a sync) is not audit-only
    (core/upsert-sports-site!* (test-db) user
                               {:lipas-id 9990102 :status "active" :event-date t2
                                :name "Synkattu halli" :owner "city" :admin "city-sports"
                                :type {:type-code 1210}
                                :location {:city {:city-code 91}
                                           :address "Katu 2" :postal-code "00100" :postal-office "Helsinki"
                                           :geometries {:type "FeatureCollection"
                                                        :features [{:type "Feature"
                                                                    :geometry {:type "Point"
                                                                               :coordinates [24.9 60.1]}}]}}
                                :ptv {:org-id "ptv-org" :sync-enabled true :last-sync t2
                                      :summary {:fi "S"} :description {:fi "D"}
                                      :service-ids [] :service-channel-ids ["c2"]
                                      :audit (audit t1 "approved")}})

    (testing "before: the audit-only revision is current and dates the site at the audit"
      (let [row (current-row lipas-id)]
        (is (= t2 (:doc_event_date row)))
        (is (:has_audit row))))

    (testing "dry run lists what will happen"
      (let [{:keys [audits repairs]} (sut/compute-plan (test-db))]
        (is (= #{[lipas-id t1] [lipas-id t2] [9990102 t1]}
               (set (map (juxt :lipas_id #(str (.toInstant ^java.sql.Timestamp (:event_date %)))) audits))))
        (is (= [[lipas-id t1 t0] [lipas-id t2 t0]]
               (mapv (juxt :lipas_id
                           #(str (.toInstant ^java.sql.Timestamp (:event_date %)))
                           :content_event_date_str)
                     repairs))
            "both audit-only revisions go back to the content revision's date; the synced site is not a repair")))

    (sut/migrate-up {:db (test-db)})

    (testing "audits copied, latest one current, string auditor-id tolerated"
      (is (= 2 (count (audit-rows lipas-id))))
      (is (= 1 (count (audit-rows 9990102))))
      ;; both verdicts were given on the content revision (t0), not on the
      ;; audit-only revisions that carried them
      (is (= [(revision-id lipas-id t0) (revision-id lipas-id t0)]
             (map :site_revision_id (audit-rows lipas-id))))
      ;; the synced site: the audit (t1) predates its only revision (t2) -> unresolvable
      (is (= [nil] (map :site_revision_id (audit-rows 9990102))))
      (is (= (audit t2 "approved") (ptv-site-audit-db/get-current (test-db) lipas-id)))
      (is (= (audit t1 "approved") (ptv-site-audit-db/get-current (test-db) 9990102))))

    (testing "audit-only revisions are re-dated to the content revision, column and document alike"
      (let [row (current-row lipas-id)]
        (is (= t0 (:doc_event_date row)))
        (is (= t0 (str (.toInstant ^java.sql.Timestamp (:event_date row)))))
        ;; the audit revision itself is still the current one (view tie-break on created_at)
        (is (:has_audit row))))

    (testing "the synced site's revision is left alone"
      (is (= t2 (:doc_event_date (current-row 9990102)))))

    (testing "readers no longer see the in-document audit; the read-side join serves the moved one"
      (is (nil? (get-in (core/get-sports-site (test-db) lipas-id) [:ptv :audit])))
      (is (= t0 (:event-date (core/get-sports-site (test-db) lipas-id))))
      (is (nil? (get-in (core/enrich (core/get-sports-site (test-db) lipas-id)) [:ptv :audit]))
          "not indexed")
      (is (= [{:lipas-id lipas-id :audit (audit t2 "approved")}]
             (ptv-audit/site-audits (test-db) [lipas-id]))))

    (testing "idempotent"
      (let [{:keys [audits repairs]} (sut/compute-plan (test-db))]
        (is (empty? audits))
        (is (empty? repairs)))
      (sut/migrate-up {:db (test-db)})
      (is (= 2 (count (audit-rows lipas-id))))
      (is (= t0 (:doc_event_date (current-row lipas-id)))))))
