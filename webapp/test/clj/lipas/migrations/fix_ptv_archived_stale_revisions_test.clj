(ns lipas.migrations.fix-ptv-archived-stale-revisions-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lipas.backend.core :as core]
            [lipas.backend.db.db :as db]
            [lipas.migrations.fix-ptv-archived-stale-revisions :as sut]
            [lipas.test-utils :as tu]))

(defonce test-system (atom nil))

(let [{:keys [once each]} (tu/db-only-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn test-db [] (:lipas/db @test-system))

(def ^:private published-ptv
  {:sync-enabled true :org-id "ptv-org" :publishing-status "Published"
   :last-sync "2026-01-01T10:00:00.000Z" :source-id "lipas-src"
   :service-channel-ids ["channel-1"] :service-ids ["service-1"]
   :summary {:fi "Lyhyt kuvaus"} :description {:fi "Pidempi kuvaus"}})

(defn- site [lipas-id event-date status ptv]
  {:lipas-id lipas-id
   :status status
   :event-date event-date
   :name (str "Kenttä " lipas-id)
   :owner "city"
   :admin "city-sports"
   :type {:type-code 1340}
   :location {:city {:city-code 678}
              :address "Testitie 1"
              :postal-code "92100"
              :postal-office "Raahe"
              :geometries {:type "FeatureCollection"
                           :features [{:type "Feature"
                                       :geometry {:type "Point"
                                                  :coordinates [24.48 64.68]}}]}}
   :ptv ptv})

(defn- close-with-fast-browser-clock!
  "Replays prod: the user's browser-dated revision (pre-archive :ptv) is
  saved first, then the sync's server-dated archive revision, which is
  OLDER, so the backdating rule re-marks the user's revision incorrect-data."
  [user lipas-id archived-at stale-at]
  (db/upsert-sports-site! (test-db) user (site lipas-id "2025-06-01T10:00:00.000Z" "active" published-ptv))
  (db/upsert-sports-site! (test-db) user (site lipas-id stale-at "out-of-service-permanently" published-ptv))
  (db/upsert-sports-site! (test-db) user (site lipas-id archived-at "out-of-service-permanently"
                                               (-> published-ptv
                                                   (assoc :publishing-status "Deleted" :last-sync archived-at)
                                                   (dissoc :source-id :service-channel-ids)))))

(defn- current [lipas-id] (core/get-sports-site (test-db) lipas-id))

(deftest fix-ptv-archived-stale-revisions-test
  (let [user (tu/gen-admin-user :db-component (test-db))
        _robot (core/add-user! (test-db) {:email "robot@lipas.fi" :username "robot@lipas.fi"})
        affected 9990401
        genuine 9990402
        far-apart 9990403]
    (close-with-fast-browser-clock! user affected "2026-01-13T06:06:39.000Z" "2026-01-13T06:06:43.000Z")
    ;; marked incorrect-data on purpose, never archived: not ours to touch
    (db/upsert-sports-site! (test-db) user (site genuine "2026-02-01T10:00:00.000Z" "active" published-ptv))
    (db/upsert-sports-site! (test-db) user (site genuine "2026-02-02T10:00:00.000Z" "incorrect-data" published-ptv))
    ;; same shape, but the archive is hours before: not the clock-skew signature
    (close-with-fast-browser-clock! user far-apart "2026-03-31T01:00:00.000Z" "2026-03-31T04:23:55.000Z")

    (testing "the replay reproduces prod"
      (is (= ["incorrect-data" "Published"]
             ((juxt :status (comp :publishing-status :ptv)) (current affected)))))

    (testing "only the signature matches"
      (is (= [affected] (map :lipas-id (sut/compute-plan (test-db))))))

    (sut/migrate-up {:db (test-db)})

    (testing "the archive result is current again, with the status the user chose"
      (let [fixed (current affected)]
        (is (= "out-of-service-permanently" (:status fixed)))
        (is (= "Deleted" (get-in fixed [:ptv :publishing-status])))
        (is (= "2026-01-13T06:06:39.000Z" (get-in fixed [:ptv :last-sync])))
        (is (empty? (get-in fixed [:ptv :service-channel-ids])))))

    (testing "others are untouched"
      (is (= "incorrect-data" (:status (current genuine))))
      (is (= "incorrect-data" (:status (current far-apart)))))

    (testing "idempotent"
      (is (empty? (sut/compute-plan (test-db)))))))
