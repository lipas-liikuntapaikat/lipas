(ns lipas.migrations.uuid-feature-ids-test
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.set :as set]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [lipas.backend.core :as core]
            [lipas.backend.db.db :as db]
            [lipas.backend.db.sports-site :as sports-site-db]
            [lipas.migrations.uuid-feature-ids :as sut]
            [lipas.test-utils :as tu]
            [next.jdbc :as next-jdbc]))

(defonce test-system (atom nil))

(let [{:keys [once each]} (tu/db-only-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn test-db [] (:lipas/db @test-system))

;;; Pure renaming ;;;

(def ^:private gen-feature-id
  "A feature id the way they occur: a UUID, one of the two legacy forms
  (made unique by `i`), or none."
  (gen/frequency [[4 (gen/fmap str gen/uuid)]
                  [2 (gen/return :legacy-lipas-id-n)]
                  [1 (gen/return :legacy-temp)]
                  [2 (gen/return nil)]]))

(def ^:private gen-doc
  (gen/let [ids (gen/vector gen-feature-id 1 8)
            route-picks (gen/vector (gen/tuple (gen/vector (gen/choose 0 9))
                                               gen/boolean)
                                    0 3)]
    (let [ids (map-indexed (fn [i id]
                             (case id
                               :legacy-lipas-id-n (str "9970999-" i)
                               :legacy-temp (str "temp" i)
                               id))
                           ids)
          features (mapv (fn [i id]
                           (cond-> {:type "Feature"
                                    :geometry {:type "LineString"
                                               :coordinates [[25.0 (+ 60.0 i)] [25.1 (+ 60.0 i)]]}
                                    :properties {:name (str "osa " i)}}
                             id (assoc :id id)))
                         (range)
                         ids)
          ;; Indexes past the last feature make dangling fids
          ->fid (fn [i] (or (some-> (get features i) :id)
                            (str "dangling-" i)))
          routes (mapv (fn [[picks with-geometries?]]
                         (cond-> {:id (str (random-uuid))
                                  :fids (mapv ->fid picks)}
                           with-geometries?
                           (assoc :geometries {:type "FeatureCollection"
                                               :features (vec (keep #(get features %) picks))})))
                       route-picks)]
      {:lipas-id 9970999
       :name "Reitti"
       :location {:geometries {:type "FeatureCollection" :features features}}
       :activities {:outdoor-recreation-routes {:routes routes}}})))

(defn- feature-index
  "fid -> index of the site feature it points to."
  [doc]
  (into {} (keep-indexed (fn [i f] (when-let [id (:id f)] [id i])))
        (-> doc :location :geometries :features)))

(defn- resolved
  "For each route, which site feature each fid points to (or the dangling
  fid itself)."
  [doc]
  (let [idx (feature-index doc)]
    (for [route (-> doc :activities :outdoor-recreation-routes :routes)]
      (mapv #(get idx % [:dangling %]) (:fids route)))))

(defspec rename-gives-uuids-and-keeps-every-reference 200
  (prop/for-all [doc gen-doc]
                (let [rename (sut/rename-plan doc)
                      copy (sut/rename-feature-ids doc rename)
                      before (-> doc :location :geometries :features)
                      after (-> copy :location :geometries :features)]
                  (and (every? sut/uuid-id? (vals rename))
                       (= (count rename) (count (set (vals rename))))
                       (empty? (sut/legacy-ids copy))
                       (empty? (sut/check-copy doc rename copy))
           ;; fids point at the same features as before; dangling ones stay
                       (= (resolved doc) (resolved copy))
           ;; UUIDs and missing ids are untouched
                       (every? true? (map (fn [b a]
                                            (or (not (contains? rename (:id b)))
                                                (= (rename (:id b)) (:id a))))
                                          before after))
                       (= (map :id (remove #(contains? rename (:id %)) before))
                          (map :id (remove #(contains? (set (vals rename)) (:id %)) after)))))))

(deftest check-copy-test
  (let [doc {:location {:geometries {:type "FeatureCollection"
                                     :features [{:type "Feature" :id "123-0"}]}}
             :activities {:cycling {:routes [{:id "r" :fids ["123-0"]}]}}}
        rename {"123-0" "2b0e5f3c-6a51-4f0b-9d3e-0c3f6d1e7a10"}]
    (testing "a clean rename"
      (is (= [] (sut/check-copy doc rename (sut/rename-feature-ids doc rename)))))
    (testing "an old id somewhere the rename doesn't reach"
      (let [doc (assoc doc :comment "123-0")]
        (is (re-find #"outside features"
                     (first (sut/check-copy doc rename (sut/rename-feature-ids doc rename)))))))
    (testing "an old id as a map key"
      (let [doc (assoc doc :properties {(keyword "123-0") 1})]
        (is (seq (sut/check-copy doc rename (sut/rename-feature-ids doc rename))))))
    (testing "anything else changed"
      (is (some #(re-find #"renaming the ids back" %)
                (sut/check-copy doc rename (-> (sut/rename-feature-ids doc rename)
                                               (assoc :name "muuttunut"))))))))

;;; Migration ;;;

(def ^:private uuid-a "2b0e5f3c-6a51-4f0b-9d3e-0c3f6d1e7a10")

(defn- route-site
  "A route site with features of `ids` (nil = no id) and `activities`."
  [lipas-id event-date ids & {:as extra}]
  (-> (tu/make-route-site lipas-id
                          :type-code 4405
                          :event-date event-date
                          :segments (for [i (range (count ids))]
                                      [[25.0 (+ 60.0 (* 0.01 i))] [25.01 (+ 60.0 (* 0.01 i))]]))
      (update-in [:location :geometries :features]
                 (fn [fs] (mapv (fn [f id] (cond-> f id (assoc :id id))) fs ids)))
      (merge extra)))

(defn- revisions [lipas-id]
  (-> (jdbc/query (test-db) ["SELECT count(*) AS n FROM sports_site WHERE lipas_id = ?" lipas-id])
      first :n))

(defn- current [lipas-id] (core/get-sports-site (test-db) lipas-id))

(defn- current-row [lipas-id]
  (first (jdbc/query (test-db) ["SELECT event_date, author_id FROM sports_site_current WHERE lipas_id = ?"
                                lipas-id])))

(defn- feature-ids [doc] (mapv :id (-> doc :location :geometries :features)))

(deftest uuid-feature-ids-test
  (let [person (tu/gen-admin-user :db-component (test-db))
        _ (core/add-user! (test-db) {:email "robot@lipas.fi" :username "robot@lipas.fi"})
        robot (core/get-user! (test-db) "robot@lipas.fi")
        legacy 9970001
        clean 9970002
        disagreeing 9970003
        closed-site (route-site legacy "2021-06-01T10:00:00.000Z"
                                ["9970001-0" "temp12" uuid-a nil]
                                :status "out-of-service-permanently"
                                :decommission-year 2021
                                :activities
                                {:outdoor-recreation-routes
                                 {:routes [{:id "6f1c1c7e-3c1b-4c55-9a43-5d1f7b3e2c01"
                                            :fids ["9970001-0" uuid-a "temp12" "9970001-7"]}
                                           {:id "7f1c1c7e-3c1b-4c55-9a43-5d1f7b3e2c02"
                                            :fids ["9970001-0"]
                                            :geometries {:type "FeatureCollection"
                                                         :features [{:type "Feature"
                                                                     :id "9970001-0"
                                                                     :geometry {:type "LineString"
                                                                                :coordinates [[25.0 60.0] [25.01 60.0]]}}]}}]}})]
    (db/upsert-sports-site! (test-db) person (route-site legacy "2019-01-01T10:00:00.000Z" ["9970001-0"]))
    (db/upsert-sports-site! (test-db) person closed-site)
    (db/upsert-sports-site! (test-db) person (route-site clean "2022-01-01T10:00:00.000Z" [uuid-a nil]))
    ;; Seen in prod: a document whose :event-date disagrees with its column.
    ;; The copy must land on the column's date to become current.
    (->> (-> (sports-site-db/marshall (route-site disagreeing "2020-05-05T10:00:00.000Z" ["temp1"]) person)
             (assoc :event_date "2020-05-06T10:00:00.000Z"))
         (sports-site-db/insert-sports-site-rev! (test-db)))

    (testing "dry run"
      (is (= [{:lipas-id legacy :status "out-of-service-permanently"
               :ids ["9970001-0" "temp12"] :features 2 :fids 3 :route-geometries 1}
              {:lipas-id disagreeing :status "active"
               :ids ["temp1"] :features 1 :fids 0 :route-geometries 0}]
             (map #(dissoc % :name) (sut/compute-plan (test-db))))))

    (let [before (current legacy)
          revs {legacy (revisions legacy) clean (revisions clean) disagreeing (revisions disagreeing)}
          disagreeing-date (:event_date (current-row disagreeing))]
      (sut/migrate-up {:db (test-db)})

      (let [after (current legacy)
            [new0 new1 kept none] (feature-ids after)
            rename {"9970001-0" new0 "temp12" new1}
            [route1 route2] (-> after :activities :outdoor-recreation-routes :routes)]

        (testing "legacy ids get new distinct UUIDs, the rest stay"
          (is (sut/uuid-id? new0))
          (is (sut/uuid-id? new1))
          (is (not= new0 new1))
          (is (= uuid-a kept))
          (is (nil? none)))

        (testing "routes refer to the same features; a dangling fid stays"
          (is (= [new0 uuid-a new1 "9970001-7"] (:fids route1)))
          (is (= [new0] (:fids route2)))
          (is (= [new0] (map :id (-> route2 :geometries :features)))))

        (testing "nothing else changes"
          (is (= before (sut/rename-feature-ids after (set/map-invert rename))))
          (is (= ["out-of-service-permanently" 2021 "2021-06-01T10:00:00.000Z"]
                 ((juxt :status :decommission-year :event-date) after))))

        (testing "one robot copy, at the current revision's event_date"
          (is (= (inc (revs legacy)) (revisions legacy)))
          (is (= (:id robot) (:author_id (current-row legacy))))
          (is (= (java.time.Instant/parse "2021-06-01T10:00:00.000Z")
                 (.toInstant ^java.sql.Timestamp (:event_date (current-row legacy)))))))

      (testing "a site with only UUIDs is left alone"
        (is (= (revs clean) (revisions clean))))

      (testing "a document disagreeing with its event_date column is copied to the column's date"
        (is (= (inc (revs disagreeing)) (revisions disagreeing)))
        (is (sut/uuid-id? (first (feature-ids (current disagreeing)))))
        (is (= disagreeing-date (:event_date (current-row disagreeing)))))

      (testing "idempotent"
        (is (empty? (sut/compute-plan (test-db))))
        (let [counts (into {} (map (juxt identity revisions)) (keys revs))]
          (sut/migrate-up {:db (test-db)})
          (is (= counts (into {} (map (juxt identity revisions)) (keys revs)))))))))

(deftest one-transaction-under-migratus
  ;; Migratus hands the migration its transaction's connection as :conn.
  ;; Every write must stay inside it: here the transaction rolls back, so
  ;; nothing may persist.
  (let [person (tu/gen-admin-user :db-component (test-db))
        _ (core/add-user! (test-db) {:email "robot@lipas.fi" :username "robot@lipas.fi"})
        id 9970101]
    (db/upsert-sports-site! (test-db) person (route-site id "2021-06-01T10:00:00.000Z" ["temp1" "temp2"]))
    (let [n (revisions id)]
      (next-jdbc/with-transaction [tx (test-db) {:rollback-only true}]
        (sut/migrate-up {:conn tx :db (test-db)})
        (is (every? sut/uuid-id?
                    (feature-ids (core/get-sports-site {:connection tx :level 1 :rollback (atom false)} id)))
            "repaired inside the transaction"))
      (is (= n (revisions id)) "nothing persisted after the rollback")
      (is (= ["temp1" "temp2"] (feature-ids (current id)))))))

(deftest unrewritable-reference-fails-the-migration
  (let [person (tu/gen-admin-user :db-component (test-db))
        _ (core/add-user! (test-db) {:email "robot@lipas.fi" :username "robot@lipas.fi"})
        ok 9970201
        bad 9970202]
    (db/upsert-sports-site! (test-db) person (route-site ok "2021-06-01T10:00:00.000Z" ["temp1"]))
    (db/upsert-sports-site! (test-db) person (route-site bad "2021-06-01T10:00:00.000Z" ["temp1"]
                                                         :comment "temp1"))
    (let [counts [(revisions ok) (revisions bad)]]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Can't give the features of 9970202"
                            (next-jdbc/with-transaction [tx (test-db)]
                              (sut/migrate-up {:conn tx :db (test-db)}))))
      (is (= counts [(revisions ok) (revisions bad)])
          "the site fixed before the failure rolled back too"))))
