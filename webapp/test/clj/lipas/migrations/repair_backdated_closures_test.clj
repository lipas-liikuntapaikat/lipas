(ns lipas.migrations.repair-backdated-closures-test
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [lipas.backend.core :as core]
            [lipas.backend.db.db :as db]
            [lipas.backend.db.sports-site :as sports-site-db]
            [lipas.migrations.repair-backdated-closures :as sut]
            [lipas.test-utils :as tu]
            [next.jdbc :as next-jdbc]))

(defonce test-system (atom nil))

(let [{:keys [once each]} (tu/db-only-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn test-db [] (:lipas/db @test-system))

(def ^:private closed db/closed-status)

(defn- site [lipas-id event-date status & {:as extra}]
  (merge {:lipas-id lipas-id
          :status status
          :event-date event-date
          :name (str "Kenttä " lipas-id)
          :owner "city"
          :admin "city-sports"
          :type {:type-code 1340}
          :location {:city {:city-code 91}
                     :address "Testitie 1"
                     :postal-code "00100"
                     :postal-office "Helsinki"
                     :geometries {:type "FeatureCollection"
                                  :features [{:type "Feature"
                                              :geometry {:type "Point"
                                                         :coordinates [24.94 60.17]}}]}}}
         extra))

(defn- close-the-old-way!
  "Replays the removed invalidate-since!: every later-dated revision is
  rewritten to incorrect-data in place, then the backdated closure is
  inserted as is (no year, no rebase)."
  [user lipas-id event-date]
  (jdbc/execute! (test-db)
                 ["UPDATE sports_site
                   SET document = jsonb_set(document, '{status}', '\"incorrect-data\"')
                   WHERE lipas_id = ? AND event_date > ?::timestamptz"
                  lipas-id event-date])
  (->> (sports-site-db/marshall (site lipas-id event-date closed) user "published")
       (sports-site-db/insert-sports-site-rev! (test-db))))

(defn- current [lipas-id] (core/get-sports-site (test-db) lipas-id))

(defn- incorrect-data-rows [lipas-id]
  (-> (jdbc/query (test-db) ["SELECT count(*) AS n FROM sports_site
                              WHERE lipas_id = ? AND document->>'status' = 'incorrect-data'"
                             lipas-id])
      first :n))

(deftest repair-backdated-closures-test
  (let [person (tu/gen-admin-user :db-component (test-db))
        _ (core/add-user! (test-db) {:email "robot@lipas.fi" :username "robot@lipas.fi"})
        robot (core/get-user! (test-db) "robot@lipas.fi")
        upsert! (fn [user s] (db/upsert-sports-site! (test-db) user s))
        broken 9960001          ; the closure was the last action
        robot-after 9960002     ; + the elevation job's copy after it
        person-after 9960003    ; + a person's save after it
        plain 9960004           ; closed now, never backdated, no year
        year-marker 9960005     ; closed with the year picker, nothing later
        reactivated 9960006     ; + reactivated by a person after it
        streak 9960007          ; closed, then a closed edit
        reclosed 9960008        ; closure hidden, closed again with this year
        loi (tu/gen-loi!)]
    (doseq [id [broken robot-after person-after reactivated]]
      (upsert! person (site id "2015-03-01T10:00:00.000Z" "active"))
      (upsert! person (site id "2020-08-12T10:00:00.000Z" "active" :name (str "Nimi 2020 " id)))
      (close-the-old-way! person id "2018-12-31T23:59:59.999Z"))
    (upsert! robot (site robot-after "2020-08-12T10:00:00.000Z" "incorrect-data"
                         :name (str "Nimi 2020 " robot-after) :comment "korkeus"))
    (upsert! person (site person-after "2024-01-10T10:00:00.000Z" "incorrect-data"))
    (upsert! person (site reactivated "2024-01-10T10:00:00.000Z" "active"))
    (upsert! person (site reclosed "2015-03-01T10:00:00.000Z" "active"))
    (upsert! person (site reclosed "2020-08-12T10:00:00.000Z" "active"))
    (close-the-old-way! person reclosed "2017-12-31T23:59:59.999Z")
    (upsert! person (site reclosed "2021-02-01T10:00:00.000Z" closed))

    (upsert! person (site plain "2015-03-01T10:00:00.000Z" "active"))
    (upsert! person (site plain "2019-05-01T10:00:00.000Z" closed))
    (upsert! person (site year-marker "2015-03-01T10:00:00.000Z" "active"))
    (upsert! person (site year-marker "2017-12-31T23:59:59.999Z" closed))
    (upsert! person (site streak "2015-03-01T10:00:00.000Z" "active"))
    (upsert! person (site streak "2016-03-01T10:00:00.000Z" closed))
    (upsert! person (site streak "2019-04-01T10:00:00.000Z" closed :comment "edit"))

    (db/upsert-loi! (test-db) person (assoc loi :event-date "2015-03-01T10:00:00.000Z" :status "active"))
    (db/upsert-loi! (test-db) person (assoc loi :event-date "2020-05-01T10:00:00.000Z" :status closed))

    (testing "the replay reproduces prod"
      (is (= "incorrect-data" (:status (current broken))))
      (is (= "incorrect-data" (:status (current robot-after)))))

    (testing "dry run"
      (let [plan (sut/compute-plan (test-db))]
        (is (= [broken robot-after] (map :lipas_id (:rebase plan))))
        (is (= [person-after] (map :lipas_id (:review plan))))
        (is (= {plain 2019 year-marker 2017 streak 2016 reclosed 2017}
               (into {} (map (juxt :key :decommission-year) (:backfill-sites plan)))))
        (is (= {(parse-uuid (:id loi)) 2020}
               (into {} (map (juxt :key :decommission-year) (:backfill-lois plan)))))))

    (let [rewritten-before (incorrect-data-rows broken)]
      (sut/migrate-up {:db (test-db)})

      (testing "rebased closures are current, with the latest content"
        (is (= [closed 2018 (str "Nimi 2020 " broken) "2020-08-12T10:00:00.000Z"]
               ((juxt :status :decommission-year :name :event-date) (current broken))))
        (is (= [closed 2018 "korkeus"]
               ((juxt :status :decommission-year :comment) (current robot-after)))))

      (testing "the rewritten originals stay as the audit trail"
        (is (= rewritten-before (incorrect-data-rows broken))))

      (testing "a person's later save is left alone"
        (is (= "incorrect-data" (:status (current person-after))))
        (is (= "active" (:status (current reactivated))))
        (is (not (contains? (current reactivated) :decommission-year))))

      (testing "closed sites get the year their closure started"
        (is (= 2019 (:decommission-year (current plain))))
        (is (= 2017 (:decommission-year (current year-marker))))
        (is (= [2016 "edit"] ((juxt :decommission-year :comment) (current streak))))
        (testing "the year first chosen, not the re-close's, when the first was hidden"
          (is (= 2017 (:decommission-year (current reclosed))))))

      (testing "closed LOIs too"
        (is (= 2020 (:decommission-year (first (db/get-lois-by-ids (test-db) [(:id loi)]))))))

      (testing "idempotent"
        (let [plan (sut/compute-plan (test-db))]
          (is (empty? (:rebase plan)))
          (is (empty? (:backfill-sites plan)))
          (is (empty? (:backfill-lois plan))))))))

(deftest repair-is-one-transaction-under-migratus
  ;; Migratus hands the migration its transaction's connection as :conn. Every
  ;; write must stay inside it: here the transaction rolls back, so nothing
  ;; may persist (the rebase's inserts once committed on their own).
  (let [person (tu/gen-admin-user :db-component (test-db))
        _ (core/add-user! (test-db) {:email "robot@lipas.fi" :username "robot@lipas.fi"})
        id 9960101
        count-rows #(-> (jdbc/query (test-db) ["SELECT count(*) AS n FROM sports_site WHERE lipas_id = ?" id])
                        first :n)]
    (db/upsert-sports-site! (test-db) person (site id "2015-03-01T10:00:00.000Z" "active"))
    (db/upsert-sports-site! (test-db) person (site id "2020-08-12T10:00:00.000Z" "active"))
    (close-the-old-way! person id "2018-12-31T23:59:59.999Z")
    (let [n (count-rows)]
      (next-jdbc/with-transaction [tx (test-db) {:rollback-only true}]
        (sut/migrate-up {:conn tx :db (test-db)})
        (is (= closed (:status (core/get-sports-site {:connection tx :level 1 :rollback (atom false)} id)))
            "repaired inside the transaction"))
      (is (= n (count-rows)) "nothing persisted after the rollback")
      (is (= "incorrect-data" (:status (current id)))))))

(deftest closure-started-at-test
  (let [ts #(java.sql.Timestamp/from (java.time.Instant/parse %))
        rev (fn [event-date created-at status]
              {:event_date (ts event-date) :created_at (ts created-at) :status status})
        started #(some-> (sut/closure-started-at %) .toInstant str)]
    (testing "the first closed revision of the current closed streak"
      (is (= "2016-03-01T00:00:00Z"
             (started [(rev "2015-01-01T00:00:00Z" "2015-01-01T00:00:00Z" "active")
                       (rev "2016-03-01T00:00:00Z" "2016-03-01T00:00:00Z" closed)
                       (rev "2019-04-01T00:00:00Z" "2019-04-01T00:00:00Z" closed)]))))
    (testing "a reopening starts a new streak"
      (is (= "2022-01-01T00:00:00Z"
             (started [(rev "2016-03-01T00:00:00Z" "2016-03-01T00:00:00Z" closed)
                       (rev "2019-01-01T00:00:00Z" "2019-01-01T00:00:00Z" "active")
                       (rev "2022-01-01T00:00:00Z" "2022-01-01T00:00:00Z" closed)]))))
    (testing "a revision the invalidation rewrote doesn't reopen"
      (is (= "2017-12-31T23:59:59.999Z"
             (started [(rev "2015-01-01T00:00:00Z" "2015-01-01T00:00:00Z" "active")
                       (rev "2020-08-12T00:00:00Z" "2020-08-12T00:00:00Z" "incorrect-data")
                       (rev "2017-12-31T23:59:59.999Z" "2021-01-10T00:00:00Z" closed)
                       (rev "2021-02-01T00:00:00Z" "2021-02-01T00:00:00Z" closed)]))))
    (testing "a person's incorrect-data, not followed by a backdated closure, is open"
      (is (= "2021-02-01T00:00:00Z"
             (started [(rev "2017-12-31T23:59:59.999Z" "2018-01-10T00:00:00Z" closed)
                       (rev "2020-08-12T00:00:00Z" "2020-08-12T00:00:00Z" "incorrect-data")
                       (rev "2021-02-01T00:00:00Z" "2021-02-01T00:00:00Z" closed)]))))
    (testing "at one event_date the newest revision wins"
      (is (= "2019-01-01T00:00:00Z"
             (started [(rev "2016-03-01T00:00:00Z" "2016-03-01T00:00:00Z" closed)
                       (rev "2018-01-01T00:00:00Z" "2018-01-01T00:00:00Z" closed)
                       (rev "2018-01-01T00:00:00Z" "2018-02-01T00:00:00Z" "active")
                       (rev "2019-01-01T00:00:00Z" "2019-01-01T00:00:00Z" closed)]))))))
