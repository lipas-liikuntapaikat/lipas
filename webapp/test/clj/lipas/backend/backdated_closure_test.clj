(ns lipas.backend.backdated-closure-test
  "A permanent closure dated into the past is carried forward over the
  revisions dated after it (db/rebase-onto-closure!), instead of rewriting
  them to incorrect-data like the removed invalidate-since! did."
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [lipas.backend.core :as core]
            [lipas.backend.db.db :as db]
            [lipas.backend.db.sports-site :as sports-site-db]
            [lipas.test-utils :as tu]))

(defonce test-system (atom nil))

(let [{:keys [once each]} (tu/db-only-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn test-db [] (:lipas/db @test-system))

(def ^:private closed db/closed-status)

(defn- site
  [lipas-id event-date status & {:as extra}]
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

(defn- year-end [year] (format "%d-12-31T23:59:59.999Z" year))

(defn- rows
  "Every revision row of `lipas-id` in insertion order."
  [lipas-id]
  (jdbc/query (test-db)
              ["SELECT id, status, event_date, created_at, document
                FROM sports_site WHERE lipas_id = ?
                ORDER BY created_at, event_date"
               lipas-id]))

(defn- current [lipas-id] (core/get-sports-site (test-db) lipas-id))

(defn- statuses-by-year
  "Status of the revision that wins in each year (sports_site_by_year)."
  [lipas-id]
  (->> (db/get-sports-site-history (test-db) lipas-id)
       (map (juxt #(parse-long (subs (:event-date %) 0 4)) :status))
       (into (sorted-map))))

(defn- current-row-count [lipas-id]
  (-> (jdbc/query (test-db) ["SELECT count(*) AS n FROM sports_site_current WHERE lipas_id = ?" lipas-id])
      first :n))

(deftest backdated-closure-carries-over-later-revisions
  (let [user (tu/gen-admin-user :db-component (test-db))
        id 9970001
        upsert! #(db/upsert-sports-site! (test-db) user %)]
    (upsert! (site id "2015-03-01T10:00:00.000Z" "active"))
    (upsert! (site id "2020-08-12T10:00:00.000Z" "active" :name "Nimi 2020"))
    ;; a later revision a person marked incorrect-data: the closure still wins
    (upsert! (site id "2022-05-01T10:00:00.000Z" "incorrect-data" :name "Nimi 2022"))
    (upsert! (site id "2023-06-01T10:00:00.000Z" "out-of-service-temporarily"
                   :name "Nimi 2023" :comment "remontti"))
    (let [before (rows id)
          resp (upsert! (site id (year-end 2018) closed :name "Nimi 2023" :decommission-year 2018))
          after (rows id)
          cur (current id)]

      (testing "the site is closed, with the newest revision's content"
        (is (= closed (:status cur)))
        (is (= 2018 (:decommission-year cur)))
        (is (= "Nimi 2023" (:name cur)))
        (is (= "remontti" (:comment cur)))
        (is (= "2023-06-01T10:00:00.000Z" (:event-date cur)))
        (is (= 1 (current-row-count id))))

      (testing "the save returns the new current revision"
        (is (= cur (into {} resp))))

      (testing "originals are untouched"
        (is (= before (take (count before) after))))

      (testing "the closure plus one copy per later revision"
        (is (= (+ (count before) 1 3) (count after))))

      (testing "every year from the closure on reads as closed"
        (is (= {2015 "active" 2018 closed 2020 closed 2022 closed 2023 closed}
               (statuses-by-year id)))))))

(deftest one-copy-per-event-date
  (let [user (tu/gen-admin-user :db-component (test-db))
        robot (tu/gen-admin-user :db-component (test-db))
        id 9970002]
    (db/upsert-sports-site! (test-db) user (site id "2015-03-01T10:00:00.000Z" "active"))
    (db/upsert-sports-site! (test-db) user (site id "2020-01-01T10:00:00.000Z" "active" :name "A"))
    ;; the elevation job's copy: same event_date, written later
    (db/upsert-sports-site! (test-db) robot (site id "2020-01-01T10:00:00.000Z" "active" :name "A + korkeus"))
    (let [n (count (rows id))]
      (db/upsert-sports-site! (test-db) user (site id (year-end 2018) closed :decommission-year 2018))
      (is (= (+ n 2) (count (rows id))) "closure + one copy")
      (is (= ["A + korkeus" closed] ((juxt :name :status) (current id))))
      (is (= 1 (current-row-count id))))))

(deftest copy-lands-on-the-rows-event-date
  ;; Some revisions' documents disagree with their event_date column (prod).
  ;; The copy must land on the column's date, or the original keeps winning.
  (let [user (tu/gen-admin-user :db-component (test-db))
        id 9970006]
    (db/upsert-sports-site! (test-db) user (site id "2015-03-01T10:00:00.000Z" "active"))
    (->> (assoc (sports-site-db/marshall (site id "2024-11-25T10:00:00.000Z" "active" :name "Myöhempi") user)
                :event_date "2024-12-17T10:00:00.000Z")
         (sports-site-db/insert-sports-site-rev! (test-db)))
    (db/upsert-sports-site! (test-db) user (site id (year-end 2018) closed :decommission-year 2018))
    (let [cur (current id)]
      (is (= [closed "Myöhempi"] ((juxt :status :name) cur)))
      (is (= "2024-12-17T10:00:00Z" (:event-date cur))))))

(deftest closure-dated-now-adds-one-revision
  (let [user (tu/gen-admin-user :db-component (test-db))
        id 9970003]
    (db/upsert-sports-site! (test-db) user (site id "2015-03-01T10:00:00.000Z" "active"))
    (let [n (count (rows id))
          resp (db/upsert-sports-site! (test-db) user (site id "2025-02-01T10:00:00.000Z" closed
                                                            :decommission-year 2025))]
      (is (= (inc n) (count (rows id))))
      (is (= "2025-02-01T10:00:00.000Z" (:event-date resp)))
      (is (= closed (:status (current id)))))))

(deftest draft-closure-is-not-carried-over
  (let [user (tu/gen-admin-user :db-component (test-db))
        id 9970004]
    (db/upsert-sports-site! (test-db) user (site id "2015-03-01T10:00:00.000Z" "active"))
    (db/upsert-sports-site! (test-db) user (site id "2020-03-01T10:00:00.000Z" "active"))
    (let [n (count (rows id))]
      (db/upsert-sports-site! (test-db) user (site id (year-end 2018) closed :decommission-year 2018) true)
      (is (= (inc n) (count (rows id))) "only the draft row")
      (is (= "active" (:status (current id)))))))

(deftest decommission-year-rules
  (let [admin (tu/gen-admin-user :db-component (test-db))
        id 9970005]
    (db/upsert-sports-site! (test-db) admin (site id "2015-03-01T10:00:00.000Z" "active"))

    (testing "a permanent closure without its year is rejected"
      (let [ex (try (core/upsert-sports-site! (test-db) admin (site id "2020-03-01T10:00:00.000Z" closed))
                    nil
                    (catch clojure.lang.ExceptionInfo e e))]
        (is (= :decommission-year-required (:type (ex-data ex))))
        (is (= "active" (:status (current id))))))

    (testing "a closure keeps its year"
      (core/upsert-sports-site! (test-db) admin (site id "2020-03-01T10:00:00.000Z" closed :decommission-year 2020))
      (is (= 2020 (:decommission-year (current id)))))

    (testing "any other status drops it"
      (core/upsert-sports-site! (test-db) admin (site id "2021-03-01T10:00:00.000Z" "active" :decommission-year 2020))
      (is (not (contains? (current id) :decommission-year))))))

(deftest decommission-year-of-an-event-date
  (testing "the year picker's Dec 31 marker counts in UTC"
    (is (= 2018 (db/event-date->decommission-year "2018-12-31T23:59:59.999Z"))))
  (testing "a real moment counts in Finnish time"
    (is (= 2026 (db/event-date->decommission-year "2025-12-31T22:30:00.000Z")))
    (is (= 2025 (db/event-date->decommission-year "2025-06-01T10:00:00.000Z"))))
  (testing "timestamps from the database"
    (is (= 2016 (db/event-date->decommission-year
                  (java.sql.Timestamp/from (java.time.Instant/parse "2016-12-31T23:59:59.999Z")))))))

;;; LOIs ;;;

(defn- loi-rows [loi-id]
  (jdbc/query (test-db) ["SELECT * FROM loi WHERE id = ?::uuid" (str loi-id)]))

(deftest backdated-loi-closure-carries-over
  (let [user (tu/gen-admin-user :db-component (test-db))
        loi (tu/gen-loi!)
        id (:id loi)
        upsert! #(db/upsert-loi! (test-db) user (merge loi %))]
    (upsert! {:event-date "2015-03-01T10:00:00.000Z" :status "active"})
    (upsert! {:event-date "2020-03-01T10:00:00.000Z" :status "active"})
    ;; same event_date twice: loi_current must still return one row
    (upsert! {:event-date "2021-03-01T10:00:00.000Z" :status "active" :comment "eka"})
    (upsert! {:event-date "2021-03-01T10:00:00.000Z" :status "active" :comment "toka"})
    (let [n (count (loi-rows id))]
      (upsert! {:event-date (year-end 2018) :status closed :decommission-year 2018})
      (is (= (+ n 1 2) (count (loi-rows id))) "closure + one copy per later event_date")
      (let [currents (db/get-lois-by-ids (test-db) [id])]
        (is (= 1 (count currents)))
        (is (= [closed 2018 "toka" "2021-03-01T10:00:00.000Z"]
               ((juxt :status :decommission-year :comment :event-date) (first currents))))))))

;;; Property ;;;

(def ^:private lipas-ids (atom 9971000))

(def ^:private gen-revision
  (gen/hash-map
    :year (gen/choose 2005 2025)
    ;; few distinct dates per year, so same-event_date revisions occur
    :month (gen/elements [3 9])
    :status (gen/elements ["active" "out-of-service-temporarily" "incorrect-data" "planned" closed])
    :name (gen/elements ["A" "B" "C"])))

(defn- revision->site [id {:keys [year month status name]}]
  (cond-> (site id (format "%d-%02d-01T10:00:00.000Z" year month) status :name name)
    (= closed status) (assoc :decommission-year year)))

(defspec backdated-closure-property 25
  (prop/for-all [history (gen/vector gen-revision 1 6)
                 closure-year (gen/choose 2003 2025)]
                (let [user (tu/gen-admin-user :db-component (test-db))
                      id (swap! lipas-ids inc)
                      _ (doseq [rev history]
                          (db/upsert-sports-site! (test-db) user (revision->site id rev)))
                      closure-date (year-end closure-year)
                      before (rows id)
                      by-year-before (statuses-by-year id)
                      later-dates (->> before
                                       (map #(.toInstant ^java.sql.Timestamp (:event_date %)))
                                       (filter #(.isAfter ^java.time.Instant % (java.time.Instant/parse closure-date)))
                                       distinct)
                      _ (db/upsert-sports-site! (test-db) user (site id closure-date closed
                                                                     :decommission-year closure-year))
                      after (rows id)
                      cur (current id)]
                  (and
        ;; the closure took, with its year
                    (= closed (:status cur))
                    (= closure-year (:decommission-year cur))
                    (= 1 (current-row-count id))
        ;; nothing existing changed; the closure + one copy per later date added
                    (= before (take (count before) after))
                    (= (+ (count before) 1 (count later-dates)) (count after))
        ;; history before the closure year reads as before, from it on as closed
                    (= (into (sorted-map) (filter (fn [[y _]] (< y closure-year)) by-year-before))
                       (into (sorted-map) (filter (fn [[y _]] (< y closure-year)) (statuses-by-year id))))
                    (every? (fn [[y st]] (or (< y closure-year) (= closed st)))
                            (statuses-by-year id))))))
