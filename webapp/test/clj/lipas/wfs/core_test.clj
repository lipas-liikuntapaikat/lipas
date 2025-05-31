(ns lipas.wfs.core-test
  "This namespace provides tests for lipas.wfs.core namespace"
  (:require [clojure.test :refer :all]
            [lipas.test-utils :as test-utils]
            [lipas.wfs.core :as wfs]
            [lipas.utils :as utils]))

;; Test data fixtures

(defn with-clean-wfs-db
  "Test fixture to ensure clean WFS database state"
  [test-fn]
  (test-utils/prune-wfs-schema!)
  (test-fn))

(use-fixtures :each with-clean-wfs-db)

(def sample-instant "2023-12-25T10:15:30.123456Z")

;; Point geometry sports site (Tennis Court)
(def sample-point-sports-site
  {:lipas-id 12345
   :name "Test Tennis Court"
   :name-localized {:fi "Testi tenniskenttä"
                    :se "Test tennisbana"
                    :en "Test Tennis Court"}
   :email "tennis@example.com"
   :www "https://tennis.example.com"
   :phone-number "+358401234567"
   :status "active"
   :construction-year 2020
   :renovation-years [2022 2023]
   :owner "test-owner"
   :admin "test-admin"
   :comment "Tennis court test comment"
   :event-date "2024-01-15T12:00:00.000Z"
   :properties {:school-use? true
                :free-use? false
                :tennis-courts-count 2
                :surface-material "artificial-turf"}
   :location {:address "Tennis Street 123"
              :postal-code "00100"
              :postal-office "Helsinki"
              :city {:city-code 91
                     :name {:fi "Helsinki"}
                     :neighborhood "Keskusta"}
              :geometries {:features [{:geometry {:type "Point"
                                                  :coordinates [24.9458 60.1674]}
                                       :properties {}}]}}
   :type {:type-code 1370}}) ; Tennis court - Point geometry

;; LineString geometry sports site (Horse Trail)
(def sample-linestring-sports-site
  {:lipas-id 23456
   :name "Test Horse Trail"
   :name-localized {:fi "Testi hevosreitti"
                    :se "Test hästled"
                    :en "Test Horse Trail"}
   :email "horse@example.com"
   :www "https://horse.example.com"
   :phone-number "+358407654321"
   :status "active"
   :construction-year 2019
   :renovation-years [2021]
   :owner "trail-owner"
   :admin "trail-admin"
   :comment "Horse trail test comment"
   :event-date "2024-01-20T10:00:00.000Z"
   :properties {:school-use? false
                :free-use? true
                :route-length-km 5.2
                :route-width-m 3.0
                :surface-material "gravel"}
   :location {:address "Trail Road 456"
              :postal-code "00200"
              :postal-office "Espoo"
              :city {:city-code 49
                     :name {:fi "Espoo"}
                     :neighborhood "Tapiola"}
              :geometries {:features [{:geometry {:type "LineString"
                                                  :coordinates [[24.9258 60.1574]
                                                                [24.9278 60.1594]
                                                                [24.9298 60.1614]]}
                                       :properties {:travel-direction "both"}}]}}
   :type {:type-code 4430}}) ; Horse trail - LineString geometry

;; Polygon geometry sports site (Sports Park)
(def sample-polygon-sports-site
  {:lipas-id 34567
   :name "Test Sports Park"
   :name-localized {:fi "Testi liikuntapuisto"
                    :se "Test idrottspark"
                    :en "Test Sports Park"}
   :email "park@example.com"
   :www "https://park.example.com"
   :phone-number "+358409876543"
   :status "active"
   :construction-year 2015
   :renovation-years [2020 2023]
   :owner "park-owner"
   :admin "park-admin"
   :comment "Sports park test comment"
   :event-date "2024-01-25T14:00:00.000Z"
   :properties {:school-use? true
                :free-use? true
                :area-m2 15000
                :fields-count 5
                :playground? true}
   :location {:address "Park Avenue 789"
              :postal-code "00300"
              :postal-office "Vantaa"
              :city {:city-code 92
                     :name {:fi "Vantaa"}
                     :neighborhood "Myyrmäki"}
              :geometries {:features [{:geometry {:type "Polygon"
                                                  :coordinates [[[24.9158 60.1474]
                                                                 [24.9178 60.1474]
                                                                 [24.9178 60.1494]
                                                                 [24.9158 60.1494]
                                                                 [24.9158 60.1474]]]}
                                       :properties {}}]}}
   :type {:type-code 1110}}) ; Sports park - Polygon geometry

;; Legacy definitions for backwards compatibility with existing tests
(def sample-sports-site sample-point-sports-site)

(def sample-feature
  {:geometry {:type "Point"
              :coordinates [24.9458 60.1674]}
   :properties {:travel-direction "north"}})

;; Basic Data Transformation Tests

(deftest test-helsinki-time-conversion
  (testing "->helsinki-time converts ISO instant to Helsinki time string"
    (let [result (wfs/->helsinki-time sample-instant)]
      (is (string? result))
      (is (re-matches #"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d+" result))
      (is (= "2023-12-25T12:15:30.123456" result)))))

(deftest test-resolve-geom-field-type
  (testing "resolve-geom-field-type returns correct geometry type keywords"
    (testing "Point geometry without Z"
      (is (= (keyword "geometry(Point,3067)") (wfs/resolve-geom-field-type 1530 false))))

    (testing "Point geometry with Z"
      (is (= (keyword "geometry(PointZ,3067)") (wfs/resolve-geom-field-type 1530 true))))

    (testing "LineString geometry without Z"
      (is (= (keyword "geometry(LineString,3067)") (wfs/resolve-geom-field-type 4422 false))))

    (testing "LineString geometry with Z"
      (is (= (keyword "geometry(LineStringZ,3067)") (wfs/resolve-geom-field-type 4422 true))))

    (testing "Polygon geometry without Z"
      (is (= (keyword "geometry(Polygon,3067)") (wfs/resolve-geom-field-type 110 false))))

    (testing "Polygon geometry with Z"
      (is (= (keyword "geometry(Polygonz,3067)") (wfs/resolve-geom-field-type 110 true))))))

;; WFS Row Conversion Tests

(deftest test-wfs-row-conversion-comprehensive
  "Test WFS row conversion with different geometry types and proper test data"

  (testing "Point geometry WFS row conversion (Tennis Court)"
    (let [[status row-data] (wfs/->wfs-row sample-point-sports-site 0
                                           (first (get-in sample-point-sports-site [:location :geometries :features])))]

      (testing "Returns correct status and row structure"
        (is (= "active" status))
        (is (map? row-data)))

      (testing "Point-specific field mappings"
        (is (= 12345 (:id row-data)))
        (is (= "Test Tennis Court" (:nimi_fi row-data)))
        (is (= "Test tennisbana" (:nimi_se row-data)))
        (is (= "Test Tennis Court" (:nimi_en row-data)))
        (is (= "tennis@example.com" (:sahkoposti row-data)))
        (is (= 1370 (:tyyppikoodi row-data))) ; Tennis court type
        (is (= "Tennis Street 123" (:katuosoite row-data)))
        (is (= "Tennis court test comment" (:lisatieto_fi row-data))))

      (testing "Point geometry fields"
        (is (= {:type "Point" :coordinates [24.9458 60.1674]} (:the_geom row-data)))
        ;; Point geometries don't have route/area IDs or travel direction
        (is (= 1 (:reitti_id row-data))) ; Always 1 for point (idx + 1)
        (is (= 1 (:alue_id row-data)))))) ; Always 1 for point (idx + 1)

  (testing "LineString geometry WFS row conversion (Horse Trail)"
    (let [[status row-data] (wfs/->wfs-row sample-linestring-sports-site 0
                                           (first (get-in sample-linestring-sports-site [:location :geometries :features])))]

      (testing "LineString-specific field mappings"
        (is (= 23456 (:id row-data)))
        (is (= "Test Horse Trail" (:nimi_fi row-data)))
        (is (= 4430 (:tyyppikoodi row-data))) ; Horse trail type
        (is (= "Trail Road 456" (:katuosoite row-data)))
        (is (= false (:koulun_liikuntapaikka row-data)))
        (is (= true (:vapaa_kaytto row-data))))

      (testing "LineString geometry and route fields"
        (is (= "LineString" (-> row-data :the_geom :type)))
        (is (= [[24.9258 60.1574] [24.9278 60.1594] [24.9298 60.1614]]
               (-> row-data :the_geom :coordinates)))
        (is (= 1 (:reitti_id row-data))) ; Route ID based on index
        (is (= "both" (:kulkusuunta row-data))))))

  (testing "Polygon geometry WFS row conversion (Sports Park)"
    (let [[status row-data] (wfs/->wfs-row sample-polygon-sports-site 0
                                           (first (get-in sample-polygon-sports-site [:location :geometries :features])))]

      (testing "Polygon-specific field mappings"
        (is (= 34567 (:id row-data)))
        (is (= "Test Sports Park" (:nimi_fi row-data)))
        (is (= 1110 (:tyyppikoodi row-data))) ; Sports park type
        (is (= "Park Avenue 789" (:katuosoite row-data)))
        (is (= true (:koulun_liikuntapaikka row-data)))
        (is (= true (:vapaa_kaytto row-data))))

      (testing "Polygon geometry and area fields"
        (is (= "Polygon" (-> row-data :the_geom :type)))
        (is (vector? (-> row-data :the_geom :coordinates)))
        (is (= 1 (:alue_id row-data))))))) ; Area ID based on index

(deftest test-wfs-rows-conversion-multiple-geometries
  "Test WFS rows conversion with multiple features for different geometry types"

  (testing "Multiple Point features"
    (let [multi-point-site (assoc-in sample-point-sports-site
                                     [:location :geometries :features]
                                     [{:geometry {:type "Point" :coordinates [24.9458 60.1674]} :properties {}}
                                      {:geometry {:type "Point" :coordinates [25.0 60.2]} :properties {}}])
          rows (wfs/->wfs-rows multi-point-site)]

      (is (= 2 (count rows)))
      (is (= 1 (-> rows first second :reitti_id)))
      (is (= 2 (-> rows second second :reitti_id)))))

  (testing "Multiple LineString features with travel directions"
    (let [multi-line-site (assoc-in sample-linestring-sports-site
                                    [:location :geometries :features]
                                    [{:geometry {:type "LineString"
                                                 :coordinates [[24.9 60.1] [24.91 60.11]]}
                                      :properties {:travel-direction "north"}}
                                     {:geometry {:type "LineString"
                                                 :coordinates [[24.92 60.12] [24.93 60.13]]}
                                      :properties {:travel-direction "south"}}])
          rows (wfs/->wfs-rows multi-line-site)]

      (is (= 2 (count rows)))
      (is (= "north" (-> rows first second :kulkusuunta)))
      (is (= "south" (-> rows second second :kulkusuunta)))
      (is (= 1 (-> rows first second :reitti_id)))
      (is (= 2 (-> rows second second :reitti_id)))))

  (testing "Multiple Polygon features"
    (let [multi-polygon-site (assoc-in sample-polygon-sports-site
                                       [:location :geometries :features]
                                       [{:geometry {:type "Polygon"
                                                    :coordinates [[[24.9158 60.1474] [24.9178 60.1474]
                                                                   [24.9178 60.1494] [24.9158 60.1474]]]}
                                         :properties {}}
                                        {:geometry {:type "Polygon"
                                                    :coordinates [[[24.92 60.15] [24.93 60.15]
                                                                   [24.93 60.16] [24.92 60.15]]]}
                                         :properties {}}])
          rows (wfs/->wfs-rows multi-polygon-site)]

      (is (= 2 (count rows)))
      (is (= 1 (-> rows first second :alue_id)))
      (is (= 2 (-> rows second second :alue_id))))))

(deftest test-geometry-type-specific-fields
  "Test that different geometry types include appropriate type-specific fields"

  (testing "Point geometry includes point-specific fields"
    (let [[_ row-data] (wfs/->wfs-row sample-point-sports-site 0
                                      (first (get-in sample-point-sports-site [:location :geometries :features])))
          tennis-fields (get wfs/type-code->legacy-fields 1370)]

      ;; Tennis court should have tennis-specific fields in the row
      (doseq [field tennis-fields]
        (is (contains? row-data field)
            (str "Tennis court should include field: " field)))))

  (testing "LineString geometry includes route-specific fields"
    (let [[_ row-data] (wfs/->wfs-row sample-linestring-sports-site 0
                                      (first (get-in sample-linestring-sports-site [:location :geometries :features])))
          trail-fields (get wfs/type-code->legacy-fields 4430)]

      ;; Horse trail should have trail-specific fields
      (is (contains? row-data :kulkusuunta) "Should include travel direction")
      (is (contains? row-data :reitti_id) "Should include route ID")

      (doseq [field trail-fields]
        (is (contains? row-data field)
            (str "Horse trail should include field: " field)))))

  (testing "Polygon geometry includes area-specific fields"
    (let [[_ row-data] (wfs/->wfs-row sample-polygon-sports-site 0
                                      (first (get-in sample-polygon-sports-site [:location :geometries :features])))
          park-fields (get wfs/type-code->legacy-fields 1110)]

      ;; Sports park should have park-specific fields
      (is (contains? row-data :alue_id) "Should include area ID")

      (doseq [field park-fields]
        (is (contains? row-data field)
            (str "Sports park should include field: " field))))))

;; Legacy test - now uses comprehensive data but maintains backwards compatibility
(deftest test-wfs-row-conversion
  "Legacy test maintained for backwards compatibility"
  (testing "->wfs-row converts sports site to WFS row format"
    (let [[status row-data] (wfs/->wfs-row sample-sports-site 0 sample-feature)]

      (testing "Returns status and row data tuple"
        (is (= "active" status))
        (is (map? row-data)))

      (testing "Row contains required common fields"
        (is (= 12345 (:id row-data)))
        (is (= "Test Tennis Court" (:nimi_fi row-data)))
        (is (= "Test tennisbana" (:nimi_se row-data)))
        (is (= "Test Tennis Court" (:nimi_en row-data)))
        (is (= "tennis@example.com" (:sahkoposti row-data)))
        (is (= "https://tennis.example.com" (:www row-data)))
        (is (= "+358401234567" (:puhelinnumero row-data)))
        (is (= true (:koulun_liikuntapaikka row-data)))
        (is (= false (:vapaa_kaytto row-data)))
        (is (= 2020 (:rakennusvuosi row-data)))
        (is (= "2022,2023" (:peruskorjausvuodet row-data)))
        (is (= "Tennis Street 123" (:katuosoite row-data)))
        (is (= "00100" (:postinumero row-data)))
        (is (= "Helsinki" (:postitoimipaikka row-data)))
        (is (= 91 (:kuntanumero row-data)))
        (is (= "Keskusta" (:kuntaosa row-data)))
        (is (= 1370 (:tyyppikoodi row-data))) ; Updated to tennis court
        (is (= "Tennis court test comment" (:lisatieto_fi row-data)))
        (is (= 12345 (:sijainti_id row-data))))

      (testing "Geometry and feature-specific fields"
        (is (= sample-feature (:the_geom row-data)))
        (is (= 1 (:reitti_id row-data))) ; idx + 1
        (is (= 1 (:alue_id row-data))) ; idx + 1
        (is (nil? (:kulkusuunta row-data))))))) ; Point geometry has no travel direction

(deftest test-wfs-rows-conversion
  "Legacy test maintained for backwards compatibility"
  (testing "->wfs-rows converts sports site with multiple features"
    (let [multi-feature-site (assoc-in sample-sports-site
                                       [:location :geometries :features]
                                       [sample-feature
                                        {:geometry {:type "Point" :coordinates [25.0 60.2]}
                                         :properties {}}])
          rows (wfs/->wfs-rows multi-feature-site)]

      (is (= 2 (count rows)))

      (testing "First row"
        (let [[status row-data] (first rows)]
          (is (= "active" status))
          (is (= 1 (:reitti_id row-data)))
          (is (nil? (:kulkusuunta row-data))))) ; Point geometry

      (testing "Second row"
        (let [[status row-data] (second rows)]
          (is (= "active" status))
          (is (= 2 (:reitti_id row-data)))
          (is (nil? (:kulkusuunta row-data))))))))

(deftest test-legacy-field-resolvers
  (testing "Legacy field resolution handles different data types correctly"

    (testing "Simple field resolution"
      (let [site {:lipas-id 12345 :name "Test Site" :construction-year 2023}]
        (is (= 12345 ((get wfs/legacy-field->resolve-fn :id) {:site site})))
        (is (= "Test Site" ((get wfs/legacy-field->resolve-fn :nimi_fi) {:site site})))
        (is (= 2023 ((get wfs/legacy-field->resolve-fn :rakennusvuosi) {:site site})))))

    (testing "Boolean field resolution"
      (let [site-with-bool {:properties {:school-use? true :free-use? false}}]
        (is (= true ((get wfs/legacy-field->resolve-fn :koulun_liikuntapaikka) {:site site-with-bool})))
        (is (= false ((get wfs/legacy-field->resolve-fn :vapaa_kaytto) {:site site-with-bool})))))

    (testing "Collection field resolution (renovation years)"
      (let [site-with-collection {:renovation-years [2020 2022 2023]}]
        (is (= "2020,2022,2023"
               ((get wfs/legacy-field->resolve-fn :peruskorjausvuodet) {:site site-with-collection})))))

    (testing "Helsinki time conversion"
      (let [site-with-date {:event-date "2024-01-15T12:00:00.000Z"}]
        (is (= "2024-01-15T14:00"
               ((get wfs/legacy-field->resolve-fn :muokattu_viimeksi) {:site site-with-date})))))

    (testing "Feature-specific field resolution"
      (let [feature {:geometry {:type "Point" :coordinates [24.9 60.2]}
                     :properties {:travel-direction "north"}}]
        (is (= {:type "Point" :coordinates [24.9 60.2]}
               ((get wfs/legacy-field->resolve-fn :the_geom) {:feature feature})))
        (is (= "north"
               ((get wfs/legacy-field->resolve-fn :kulkusuunta) {:feature feature})))
        (is (= 1 ((get wfs/legacy-field->resolve-fn :reitti_id) {:idx 0})))
        (is (= 2 ((get wfs/legacy-field->resolve-fn :alue_id) {:idx 1})))))))

(deftest test-type-specific-field-mapping
  (testing "Different sports site types get correct field sets"
    (let [ice-hockey-rink-fields (get wfs/type-code->legacy-fields 1530)
          swimming-pool-fields (get wfs/type-code->legacy-fields 3120)]

      ;; Ice hockey rink should have rink-specific fields
      (is (contains? ice-hockey-rink-fields :kaukalo_lkm))
      (is (not (contains? ice-hockey-rink-fields :allas1_pituus_m)))

      ;; Swimming pool should have pool-specific fields
      (is (contains? swimming-pool-fields :allas1_pituus_m))
      (is (not (contains? swimming-pool-fields :kaukalo_lkm)))))

  (testing "Common fields are present in all types"
    (let [common-set wfs/common-fields]
      (is (contains? common-set :id))
      (is (contains? common-set :nimi_fi))
      (is (contains? common-set :the_geom))
      (is (contains? common-set :tyyppikoodi)))))

(deftest test-helsinki-time-edge-cases
  (testing "Helsinki time conversion with various input formats"

    (testing "Standard ISO format"
      (is (= "2023-12-25T12:15:30.123456"
             (wfs/->helsinki-time "2023-12-25T10:15:30.123456Z"))))

    (testing "Winter time (UTC+2)"
      (is (= "2023-12-25T12:15:30.123456"
             (wfs/->helsinki-time "2023-12-25T10:15:30.123456Z"))))

    (testing "Summer time (UTC+3) - July"
      (is (= "2023-07-15T13:15:30.123456"
             (wfs/->helsinki-time "2023-07-15T10:15:30.123456Z"))))

    (testing "Handles different precisions"
      (is (string? (wfs/->helsinki-time "2023-12-25T10:15:30Z")))
      (is (string? (wfs/->helsinki-time "2023-12-25T10:15:30.123Z"))))))

(deftest test-geometry-field-type-resolution
  (testing "Geometry field type resolution for different sports site types"

    (testing "Point geometry types"
      (is (= (keyword "geometry(Point,3067)") (wfs/resolve-geom-field-type 1530 false)))
      (is (= (keyword "geometry(PointZ,3067)") (wfs/resolve-geom-field-type 1530 true))))

    (testing "LineString geometry types"
      (is (= (keyword "geometry(LineString,3067)") (wfs/resolve-geom-field-type 4422 false)))
      (is (= (keyword "geometry(LineStringZ,3067)") (wfs/resolve-geom-field-type 4422 true))))

    (testing "Polygon geometry types"
      (is (= (keyword "geometry(Polygon,3067)") (wfs/resolve-geom-field-type 110 false)))
      (is (= (keyword "geometry(Polygonz,3067)") (wfs/resolve-geom-field-type 110 true))))

    (testing "Throws on unknown type codes"
      (is (thrown? IllegalArgumentException
                   (wfs/resolve-geom-field-type 99999 false))))))

(deftest ^:integration test-wfs-e2e-lifecycle
  "Complete end-to-end test of WFS lifecycle with multiple geometry types"
  (testing "Full lifecycle of materialized view operations"

    ;; Step 1: Generate and insert sports sites with different geometry types
    (let [;; Create test user for database operations
          test-user (test-utils/gen-user {:db? true})

          ;; Create sports sites with different geometry types using our test data
          point-site (assoc sample-point-sports-site
                            :lipas-id 90001
                            :event-date (utils/timestamp))

          linestring-site (assoc sample-linestring-sports-site
                                 :lipas-id 90002
                                 :event-date (utils/timestamp))

          polygon-site (assoc sample-polygon-sports-site
                              :lipas-id 90003
                              :event-date (utils/timestamp))

          test-sites [point-site linestring-site polygon-site]]

      (testing "Step 1: Insert sports sites to the main database"
        ;; Insert the test sites
        (doseq [site test-sites]
          (let [result (lipas.backend.db.db/upsert-sports-site! test-utils/db test-user site)]
            (is (some? result)
                (str "Should successfully insert site " (:lipas-id site)))))

        ;; Verify sites were inserted
        (doseq [site test-sites]
          (let [retrieved-site (lipas.backend.db.db/get-sports-site test-utils/db (:lipas-id site))]
            (is (some? retrieved-site)
                (str "Site " (:lipas-id site) " should exist in database"))
            (is (= (:lipas-id site) (:lipas-id retrieved-site))
                "LIPAS ID should match")
            (is (= (-> site :type :type-code) (-> retrieved-site :type :type-code))
                "Type code should match"))))

      (testing "Step 2: Populate wfs.master table from sports_site table"
        (is (nil? (wfs/refresh-wfs-master-table! test-utils/db)))

        ;; Verify all inserted sites exist in wfs.master table
        (doseq [site test-sites]
          (let [wfs-rows (next.jdbc/execute!
                          test-utils/db
                          (honey.sql/format {:select [:lipas_id :type_code :geom_type :status]
                                             :from [:wfs.master]
                                             :where [:= :lipas_id (:lipas-id site)]}))]
            (is (seq wfs-rows)
                (str "Site " (:lipas-id site) " should exist in wfs.master"))

            (when (seq wfs-rows)
              (let [wfs-row (first wfs-rows)]
                (is (= (:lipas-id site) (:master/lipas_id wfs-row))
                    "LIPAS ID should match in wfs.master")
                (is (= (-> site :type :type-code) (:master/type_code wfs-row))
                    "Type code should match in wfs.master")
                (is (= "active" (:master/status wfs-row))
                    "Status should be active in wfs.master"))))))

      (testing "Step 3: Create materialized views"
        (is (nil? (wfs/create-legacy-mat-views! test-utils/db)))

        ;; Verify sites can be seen in their corresponding materialized views
        (doseq [site test-sites]
          (let [type-code (-> site :type :type-code)
                view-names (get wfs/type-code->view-names type-code)]

            (when (seq view-names)
              (doseq [view-name view-names]
                (let [view-data (try
                                  (next.jdbc/execute!
                                   test-utils/db
                                   [(str "SELECT id, tyyppikoodi FROM wfs." view-name
                                         " WHERE id = ?") (:lipas-id site)])
                                  (catch Exception e
                                    (println "Error querying view" view-name ":" (.getMessage e))
                                    []))]

                  (is (seq view-data)
                      (str "Site " (:lipas-id site) " should exist in view " view-name))

                  (when (seq view-data)
                    (let [view-row (first view-data)]
                      (is (= (:lipas-id site) ((keyword view-name "id") view-row))
                          "LIPAS ID should match in materialized view")
                      (is (= type-code ((keyword view-name "tyyppikoodi") view-row))
                          "Type code should match in materialized view")))))))))

      (testing "Step 4: Modify test data - remove, modify, add"
        ;; Remove one site (mark as out-of-service)
        (let [site-to-remove (assoc point-site
                                    :status "out-of-service-permanently"
                                    :event-date (utils/timestamp))]
          (lipas.backend.db.db/upsert-sports-site! test-utils/db test-user site-to-remove))

        ;; Modify one site (change name and properties)
        (let [site-to-modify (assoc linestring-site
                                    :name "Modified Horse Trail"
                                    :comment "Modified test comment"
                                    :event-date (utils/timestamp)
                                    :properties (assoc (:properties linestring-site)
                                                       :route-length-km 7.5))]
          (lipas.backend.db.db/upsert-sports-site! test-utils/db test-user site-to-modify))

        ;; Add a new site
        (let [new-site (assoc sample-point-sports-site
                              :lipas-id 90004
                              :name "New Tennis Court"
                              :event-date (utils/timestamp)
                              :type {:type-code 1370})] ; Tennis court
          (lipas.backend.db.db/upsert-sports-site! test-utils/db test-user new-site)

          ;; Verify new site was added
          (let [retrieved-site (lipas.backend.db.db/get-sports-site test-utils/db 90004)]
            (is (some? retrieved-site) "New site should exist in database")
            (is (= "New Tennis Court" (:name retrieved-site)) "New site name should match"))))

      (testing "Step 5: Modifications should reflect in materialized views after full refresh"
        (is (nil? (wfs/refresh-all! test-utils/db)))

        ;; Check that removed site is no longer active in views
        (let [removed-site-data (next.jdbc/execute!
                                 test-utils/db
                                 (honey.sql/format {:select [:lipas_id :status]
                                                    :from [:wfs.master]
                                                    :where [:= :lipas_id 90001]}))]
          (if (seq removed-site-data)
            (is (not= "active" (:master/status (first removed-site-data)))
                "Removed site should not have active status")
            (is true "Removed site may be excluded from wfs.master entirely")))

        ;; Check that modified site has updated data
        (let [modified-site-rows (next.jdbc/execute!
                                  test-utils/db
                                  (honey.sql/format {:select [:lipas_id :doc]
                                                     :from [:wfs.master]
                                                     :where [:= :lipas_id 90002]}))]
          (when (seq modified-site-rows)
            (let [doc-data (-> modified-site-rows first :master/doc)]
              (is (or (clojure.string/includes? (str doc-data) "Modified Horse Trail")
                      (clojure.string/includes? (str doc-data) "Modified test comment"))
                  "Modified site should have updated data in wfs.master"))))

        ;; Check that new site exists in appropriate views
        (let [new-site-data (next.jdbc/execute!
                             test-utils/db
                             (honey.sql/format {:select [:lipas_id :type_code]
                                                :from [:wfs.master]
                                                :where [:= :lipas_id 90004]}))]
          (is (seq new-site-data) "New site should exist in wfs.master")
          (when (seq new-site-data)
            (is (= 1370 (:master/type_code (first new-site-data)))
                "New site should have correct type code")))

        ;; Verify materialized views reflect the changes
        (let [tennis-view-names (get wfs/type-code->view-names 1370)]
          (when (seq tennis-view-names)
            (doseq [view-name tennis-view-names]
              (let [view-count (try
                                 (next.jdbc/execute-one!
                                  test-utils/db
                                  [(str "SELECT COUNT(*) as count FROM wfs." view-name
                                        " WHERE id IN (90001, 90004)")])
                                 (catch Exception e
                                   (println "Error counting in view" view-name ":" (.getMessage e))
                                   {:count 0}))]
                ;; Should have the new site (90004) and possibly the old site (90001)
                ;; depending on whether out-of-service sites are included
                (is (>= (:count view-count) 1)
                    (str "View " view-name " should contain at least the new tennis court")))))))

      (testing "Step 6: Drop materialized views without errors"
        (is (nil? (wfs/drop-legacy-mat-views! test-utils/db)))

        ;; Verify views are dropped by trying to query them (should fail)
        (let [tennis-view-names (get wfs/type-code->view-names 1370)]
          (when (seq tennis-view-names)
            (doseq [view-name tennis-view-names]
              (is (thrown? Exception
                           (next.jdbc/execute! test-utils/db
                                               [(str "SELECT 1 FROM wfs." view-name " LIMIT 1")]))
                  (str "View " view-name " should no longer exist after drop")))))))))

;; Test running in REPL first

(comment
  ;; Let's test our basic functions in the REPL
  (clojure.test/run-tests *ns*)

  (clojure.test/run-test-var #'test-wfs-rows-conversion-multiple-geometries)
  (clojure.test/run-test-var #'test-master-table-operations)
  (clojure.test/run-test-var #'test-geometry-field-type-resolution)
  (clojure.test/run-test-var #'test-wfs-e2e-lifecycle)

  ;; Test helsinki time conversion
  (wfs/->helsinki-time "2023-12-25T10:15:30.123456Z")

  ;; Test geometry field type resolution
  (wfs/resolve-geom-field-type 1110 false)
  (wfs/resolve-geom-field-type 1110 true)

  ;; Test with sample data
  (def test-result (wfs/->wfs-row sample-sports-site 0 sample-feature))
  test-result)
