(ns lipas.migrations.ptv-site-audit-snapshot-backfill-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lipas.backend.core :as core]
            [lipas.backend.db.ptv-site-audit :as ptv-site-audit-db]
            [lipas.backend.ptv.audit :as ptv-audit]
            [lipas.data.ptv :as ptv-data]
            [lipas.migrations.ptv-site-audit-move :as move]
            [lipas.migrations.ptv-site-audit-snapshot-backfill :as sut]
            [lipas.test-utils :as tu]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(defonce test-system (atom nil))

(let [{:keys [once each]} (tu/db-only-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn test-db [] (:lipas/db @test-system))

(def t0 "2026-04-29T10:15:50.940056Z") ; synced content revision the auditor saw
(def t1 "2026-05-06T08:51:05.280253Z") ; legacy audit save (audit-only revision)
(def t2 "2026-05-06T09:25:57.218705Z") ; next sync: the audit drops out of the document
(def t3 "2026-09-23T11:56:52.004604Z") ; the municipality's fix

(def summary {:fi "Tiivistelmä" :se "Sammanfattning"})
(def description {:fi "Alkuperäinen kuvaus." :se "Ursprunglig beskrivning."})
(def fixed-description {:fi "Korjattu kuvaus." :se "Ursprunglig beskrivning."})

(defn- legacy-audit
  "As save-ptv-audit wrote it before the whose-move workflow: no
   :audited-content on any verdict."
  []
  {:timestamp t1
   :auditor-id "8f66c074-f76e-476f-8aef-08415a0a6fe9"
   :summary {:status "approved" :feedback ""}
   :description {:status "changes-requested" :feedback "Testimuutospyyntö."}})

(defn- site [lipas-id event-date ptv]
  {:lipas-id lipas-id
   :status "active"
   :event-date event-date
   :name (str "Kenttä " lipas-id)
   :owner "city" :admin "city-sports"
   :type {:type-code 1520}
   :location {:city {:city-code 678}
              :address "Katu 1" :postal-code "92100" :postal-office "Raahe"
              :geometries {:type "FeatureCollection"
                           :features [{:type "Feature"
                                       :geometry {:type "Point"
                                                  :coordinates [24.5 64.7]}}]}}
   :ptv (merge {:org-id "ptv-org" :sync-enabled true :last-sync event-date
                :service-ids [] :service-channel-ids ["c1"]}
               ptv)})

(defn- seed!
  "Content revision t0 with `audited-texts`, then the legacy audit-only
   revision t1. With `later-texts`, a sync at t2 that drops the audit from
   the document (what happened to Haapajoen jääkiekkokenttä) and the fix at
   t3 with those texts."
  [user lipas-id audited-texts later-texts]
  (let [base (site lipas-id t0 audited-texts)]
    (core/upsert-sports-site!* (test-db) user base)
    (core/upsert-sports-site!* (test-db) user (-> base
                                                  (assoc :event-date t1)
                                                  (assoc-in [:ptv :audit] (legacy-audit))))
    (when later-texts
      (core/upsert-sports-site!* (test-db) user (site lipas-id t2 audited-texts))
      (core/upsert-sports-site!* (test-db) user (site lipas-id t3 later-texts)))))

(defn- display-status
  "The listing's triangle/check, derived as the frontend does: the current
   site joined with its current audit."
  [lipas-id]
  (let [site (core/get-sports-site (test-db) lipas-id)
        audit (ptv-site-audit-db/get-current (test-db) lipas-id)]
    (ptv-data/determine-audit-status (assoc-in site [:ptv :audit] audit))))

(defn- field-states [lipas-id]
  (let [site (core/get-sports-site (test-db) lipas-id)]
    (ptv-data/audit-field-states (ptv-site-audit-db/get-current (test-db) lipas-id)
                                 (ptv-data/site-audit-fields site))))

(defn- audit-rows [lipas-id]
  (jdbc/execute! (test-db)
                 ["SELECT event_date, site_revision_id, auditor_id, document
                   FROM ptv_site_audit WHERE lipas_id = ? ORDER BY created_at" lipas-id]
                 {:builder-fn rs/as-unqualified-maps}))

(defn- revision-id [lipas-id event-date]
  (:id (jdbc/execute-one! (test-db)
                          ["SELECT id FROM sports_site WHERE lipas_id = ? AND document->>'event-date' = ?"
                           lipas-id event-date]
                          {:builder-fn rs/as-unqualified-maps})))

(deftest ptv-site-audit-snapshot-backfill-test
  (let [user (tu/gen-admin-user :db-component (test-db))
        ;; audited, audit lost from the document, description fixed since
        fixed-id 9990201
        ;; audited, nothing edited: the request is still open
        open-id 9990202
        ;; the audited revision had no description; one was written since
        empty-id 9990203
        ;; an audit whose revision could not be resolved by the move
        orphan-id 9990204]
    (seed! user fixed-id {:summary summary :description description}
           {:summary summary :description fixed-description})
    (seed! user open-id {:summary summary :description description} nil)
    (seed! user empty-id {:summary summary}
           {:summary summary :description fixed-description})
    (core/upsert-sports-site!* (test-db) user (site orphan-id t3 {:summary summary :description description}))
    (move/migrate-up {:db (test-db)})
    ;; the move could not resolve a revision seen before this audit
    (ptv-site-audit-db/insert-audit! (test-db) {:lipas-id orphan-id :event-date t1 :document (legacy-audit)})

    (testing "before: the move reproduces the stuck change request"
      (is (nil? (get-in (core/get-sports-site (test-db) fixed-id) [:ptv :audit]))
          "the audit had dropped out of the current document")
      (is (= fixed-description (get-in (core/get-sports-site (test-db) fixed-id) [:ptv :description])))
      (is (= (revision-id fixed-id t0) (:site_revision_id (first (audit-rows fixed-id)))))
      (is (= {:summary :approved :description :changes-requested} (field-states fixed-id))
          "edited since, still open: the bug")
      (is (= :changes-requested (display-status fixed-id)))
      (is (= :changes-requested (display-status empty-id))))

    (testing "dry run"
      (let [{:keys [backfills unresolvable]} (sut/compute-plan (test-db))]
        (is (= #{fixed-id open-id empty-id} (set (map :lipas_id backfills))))
        (is (every? #(= [:description] (:fields %)) backfills))
        (is (= [orphan-id] (map :lipas_id unresolvable)))))

    (sut/migrate-up {:db (test-db)})

    (testing "the fixed site: the request reads as answered"
      (is (= description (get-in (ptv-site-audit-db/get-current (test-db) fixed-id)
                                 [:description :audited-content]))
          "snapshot = the audited revision's text, every language")
      (is (= {:summary :approved :description :fixed} (field-states fixed-id)))
      (is (= :approved (display-status fixed-id)))
      (is (= :done (ptv-data/audit-bucket (ptv-site-audit-db/get-current (test-db) fixed-id)
                                          (ptv-data/site-audit-fields (core/get-sports-site (test-db) fixed-id))))))

    (testing "only the change request is anchored; the approval stays grandfathered"
      (let [current (ptv-site-audit-db/get-current (test-db) fixed-id)]
        (is (= {:status "approved" :feedback ""} (:summary current)))
        (is (= (dissoc (legacy-audit) :description) (dissoc current :description)))
        (is (= "Testimuutospyyntö." (get-in current [:description :feedback])))))

    (testing "append-only: the original row is kept, the copy keeps date, auditor and revision"
      (let [[original copy :as rows] (audit-rows fixed-id)]
        (is (= 2 (count rows)))
        (is (= (legacy-audit) (:document original)))
        (is (= (map #(select-keys % [:event_date :site_revision_id :auditor_id]) [original original])
               (map #(select-keys % [:event_date :site_revision_id :auditor_id]) [original copy])))
        (is (= [{:lipas-id fixed-id :audit (:document copy)}]
               (ptv-audit/site-audits (test-db) [fixed-id]))
            "the copy is what the frontend is served")))

    (testing "an unanswered request stays open"
      (is (= description (get-in (ptv-site-audit-db/get-current (test-db) open-id)
                                 [:description :audited-content])))
      (is (= :changes-requested (display-status open-id))))

    (testing "the audited revision had no text: any text written since is a fix"
      (is (= {} (get-in (ptv-site-audit-db/get-current (test-db) empty-id)
                        [:description :audited-content])))
      (is (= :approved (display-status empty-id))))

    (testing "no audited revision: left as is"
      (is (= 1 (count (audit-rows orphan-id))))
      (is (= :changes-requested (display-status orphan-id))))

    (testing "a later re-audit keeps the fix (the re-anchoring bug this also closes)"
      (let [persisted (ptv-site-audit-db/get-current (test-db) fixed-id)
            site (core/get-sports-site (test-db) fixed-id)
            anchored (ptv-data/anchor-audit-snapshots
                       persisted persisted
                       {:summary (get-in site [:ptv :summary])
                        :description (get-in site [:ptv :description])})]
        (is (= description (get-in anchored [:description :audited-content])))))

    (testing "idempotent"
      (let [{:keys [backfills]} (sut/compute-plan (test-db))]
        (is (empty? backfills)))
      (sut/migrate-up {:db (test-db)})
      (is (= 2 (count (audit-rows fixed-id)))))))
