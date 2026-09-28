(ns lipas.migrations.clean-legacy-admin-owner-values-test
  (:require [cheshire.core :as json]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [lipas.backend.core :as core]
            [lipas.data.admins :as admins]
            [lipas.data.owners :as owners]
            [lipas.migrations.clean-legacy-admin-owner-values :as sut]
            [lipas.schema.sports-sites :as sports-sites]
            [lipas.test-utils :as tu]
            [malli.core :as m]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(defonce test-system (atom nil))

(let [{:keys [once each]} (tu/db-only-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn test-db [] (:lipas/db @test-system))

(def t0 "2025-12-30T09:47:06.758Z") ; inside the bad window
(def t1 "2026-03-01T10:00:00.000Z") ; a later, clean edit

(defn- site [lipas-id event-date admin owner]
  {:lipas-id lipas-id
   :status "active"
   :event-date event-date
   :name (str "Kalastuskohde " lipas-id)
   :admin admin
   :owner owner
   :type {:type-code 201}
   :location {:city {:city-code 272}
              :address "Isokarintie"
              :postal-code "67900"
              :postal-office "Kokkola"
              :geometries {:type "FeatureCollection"
                           :features [{:type "Feature"
                                       :geometry {:type "Point"
                                                  :coordinates [23.03 63.83]}}]}}})

(defn- revisions
  "Every stored revision for a lipas-id, oldest first."
  [lipas-id]
  (jdbc/execute! (test-db)
                 ["SELECT document->>'event-date' AS event_date,
                          document->>'admin'      AS admin,
                          document->>'owner'      AS owner
                   FROM sports_site WHERE lipas_id = ?
                   ORDER BY event_date" lipas-id]
                 {:builder-fn rs/as-unqualified-maps}))

(deftest clean-legacy-admin-owner-values-test
  (let [user (tu/gen-admin-user :db-component (test-db))
        ;; both fields legacy - 8 of the 11 production sites
        both-id 9990301
        ;; only :admin legacy, owner was picked properly - the other 3
        admin-only-id 9990302
        ;; never touched by the bad window
        clean-id 9990303
        ;; legacy revision, then a clean edit on top: the old revision is
        ;; still served by the history endpoint and must be fixed too
        edited-id 9990304]
    (core/upsert-sports-site!* (test-db) user (site both-id t0 "no-information" "no-information"))
    (core/upsert-sports-site!* (test-db) user (site admin-only-id t0 "no-information" "city"))
    (core/upsert-sports-site!* (test-db) user (site clean-id t0 "city-sports" "city"))
    (core/upsert-sports-site!* (test-db) user (site edited-id t0 "no-information" "no-information"))
    (core/upsert-sports-site!* (test-db) user (site edited-id t1 "city-sports" "city"))

    (testing "before: the legacy value is stored and fails the schema"
      (is (= "no-information" (:admin (core/get-sports-site (test-db) both-id))))
      (is (not (m/validate sports-sites/admin
                           (:admin (core/get-sports-site (test-db) both-id))))))

    (testing "dry run finds every affected revision, including superseded ones"
      (let [plan (sut/compute-plan (test-db))
            by-id (group-by :lipas-id plan)]
        (is (= #{both-id admin-only-id edited-id} (set (keys by-id))))
        (is (= 3 (count plan))
            "one revision each, and only the stale revision of the edited site")
        (is (= [[:admin :owner]] (map :fields (by-id both-id))))
        (is (= [[:admin]] (map :fields (by-id admin-only-id))))
        (is (= [[:admin :owner]] (map :fields (by-id edited-id))))))

    (sut/migrate-up {:db (test-db)})

    (testing "both fields cleaned"
      (let [s (core/get-sports-site (test-db) both-id)]
        (is (= "unknown" (:admin s)))
        (is (= "unknown" (:owner s)))
        (is (m/validate sports-sites/admin (:admin s)))
        (is (m/validate sports-sites/owner (:owner s)))))

    (testing "a valid owner is left alone"
      (let [s (core/get-sports-site (test-db) admin-only-id)]
        (is (= "unknown" (:admin s)))
        (is (= "city" (:owner s)))))

    (testing "an unaffected site is untouched"
      (let [s (core/get-sports-site (test-db) clean-id)]
        (is (= "city-sports" (:admin s)))
        (is (= "city" (:owner s)))))

    (testing "superseded revisions are fixed in place, and no revision is added"
      (let [revs (revisions edited-id)]
        (is (= 2 (count revs)) "in-place update, not an appended fix revision")
        (is (= [{:event_date t0 :admin "unknown" :owner "unknown"}
                {:event_date t1 :admin "city-sports" :owner "city"}]
               (map #(select-keys % [:event_date :admin :owner]) revs)))))

    (testing "nothing anywhere still holds the legacy value"
      (is (empty? (sut/compute-plan (test-db)))))

    (testing "idempotent"
      (sut/migrate-up {:db (test-db)})
      (is (empty? (sut/compute-plan (test-db))))
      (is (= 2 (count (revisions edited-id))))
      (is (= "unknown" (:admin (core/get-sports-site (test-db) both-id)))))))

;; ---------------------------------------------------------------------------
;; Containment
;; ---------------------------------------------------------------------------

(defn- all-rows
  "Every sports_site row, every column, as comparable values. `document` is
  compared as canonical jsonb text so any change anywhere in it shows up."
  []
  (->> (jdbc/execute!
         (test-db)
         ["SELECT id::text        AS id,
                  created_at::text AS created_at,
                  event_date::text AS event_date,
                  author_id::text  AS author_id,
                  lipas_id, status, type_code, city_code,
                  document::text   AS doc
           FROM sports_site ORDER BY id"]
         {:builder-fn rs/as-unqualified-maps})
       (map (juxt :id identity))
       (into {})))

(defn- doc-diff
  "Top-level keys whose value differs between two document JSON strings."
  [before after]
  (let [a (json/parse-string before true)
        b (json/parse-string after true)]
    (->> (into (set (keys a)) (keys b))
         (remove #(= (get a %) (get b %)))
         set)))

(deftest touches-only-the-broken-rows-test
  (let [user (tu/gen-admin-user :db-component (test-db))
        ;; one site per valid :admin value, owners cycled through the valid set,
        ;; so every legitimate combination is represented and must survive
        valid-sites (map-indexed
                      (fn [i [admin owner]]
                        (site (+ 9990400 i) t0 admin owner))
                      (map vector
                           (keys admins/all)
                           (cycle (keys owners/all))))
        ;; a site that never recorded either field at all
        absent-id 9990450
        ;; the two shapes actually seen in production
        both-id 9990451
        admin-only-id 9990452]
    (doseq [s valid-sites]
      (core/upsert-sports-site!* (test-db) user s))
    (core/upsert-sports-site!* (test-db) user
                               (dissoc (site absent-id t0 nil nil) :admin :owner))
    (core/upsert-sports-site!* (test-db) user (site both-id t0 "no-information" "no-information"))
    (core/upsert-sports-site!* (test-db) user (site admin-only-id t0 "no-information" "city"))

    (let [before (all-rows)
          affected (set (map :id (jdbc/execute!
                                   (test-db)
                                   ["SELECT id::text AS id FROM sports_site
                                     WHERE document->>'admin' = 'no-information'
                                        OR document->>'owner' = 'no-information'"]
                                   {:builder-fn rs/as-unqualified-maps})))
          _ (sut/migrate-up {:db (test-db)})
          after (all-rows)]

      (testing "only the two seeded bad rows were in scope"
        (is (= 2 (count affected)))
        ;; guards against this test quietly going vacuous: one row per valid
        ;; :admin value, plus the no-fields site, plus the two bad ones
        (is (= (+ (count admins/all) 3) (count before)))
        (is (= 12 (- (count before) (count affected)))))

      (testing "no row is added or removed"
        (is (= (set (keys before)) (set (keys after)))))

      (testing "every row that did not hold the legacy value is byte-identical"
        (let [untouched (remove affected (keys before))]
          (is (= (count (keys before)) (+ (count untouched) 2)))
          (is (= (select-keys before untouched)
                 (select-keys after untouched)))))

      (testing "a site with no :admin or :owner at all is left alone"
        (let [id (some (fn [[id r]] (when (= absent-id (:lipas_id r)) id)) before)]
          (is (some? id))
          (is (= (get before id) (get after id)))
          (is (not (contains? (json/parse-string (:doc (get after id)) true) :admin)))))

      (testing "on the bad rows, nothing outside :admin/:owner moved"
        (doseq [id affected
                :let [b (get before id) a (get after id)]]
          (is (= (dissoc b :doc) (dissoc a :doc))
              "created_at, event_date, author_id, status, type_code, city_code all stay put")
          (is (contains? #{#{:admin :owner} #{:admin}} (doc-diff (:doc b) (:doc a)))
              "only the fields that held the legacy value changed")))

      (testing "and they changed to exactly the replacement value"
        (is (= #{"unknown"}
               (set (map #(:admin (json/parse-string (:doc (get after %)) true)) affected))))))))
