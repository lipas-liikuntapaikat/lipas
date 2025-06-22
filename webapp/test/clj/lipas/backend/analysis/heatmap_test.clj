(ns lipas.backend.analysis.heatmap-test
  (:require [clojure.test :refer [deftest is use-fixtures testing]]
            [lipas.backend.analysis.heatmap :as heatmap]
            [lipas.backend.core :as core]
            [lipas.test-utils :refer [<-transit ->transit app db search] :as tu]
            [malli.core :as m]
            [ring.mock.request :as mock]))

;;; Fixtures ;;;

(use-fixtures :once (fn [f]
                      (tu/init-db!)
                      (f)))

(use-fixtures :each (fn [f]
                      (tu/prune-db!)
                      (tu/prune-es!)
                      (f)))

;;; Helper Functions ;;;

(defn- create-test-sports-sites
  "Creates multiple test sports sites with different characteristics and indexes them"
  []
  (let [admin-user (tu/gen-user {:db? true :admin? true})

        ;; Helper function to set coordinates consistently
        set-coordinates (fn [site [lon lat]]
                          (-> site
                              (assoc-in [:location :wgs84-point :coordinates] [lon lat])
                              (assoc-in [:location :geometries :features 0 :geometry :coordinates] [lon lat])))

        ;; Site 1: Swimming hall in Helsinki
        site1 (-> (tu/gen-sports-site)
                  (assoc :status "active")
                  (assoc :author admin-user)
                  (assoc-in [:type :type-code] 3110) ; swimming hall
                  (assoc-in [:location :city :city-code] 91) ; Helsinki
                  (set-coordinates [24.9384 60.1699])
                  (assoc-in [:properties :area-m2] 1000)
                  (assoc :construction-year 2010)
                  (assoc :owner "Helsinki")
                  (assoc :admin "Helsinki"))

        ;; Site 2: Football field in Espoo
        site2 (-> (tu/gen-sports-site)
                  (assoc :status "active")
                  (assoc :author admin-user)
                  (assoc-in [:type :type-code] 1110) ; football field
                  (assoc-in [:location :city :city-code] 49) ; Espoo
                  (set-coordinates [24.8200 60.1719])
                  (assoc-in [:properties :area-m2] 5000)
                  (assoc :construction-year 2015)
                  (assoc :owner "Espoo")
                  (assoc :admin "Espoo"))

        ;; Site 3: Ice hockey hall in Helsinki with year-round use
        site3 (-> (tu/gen-sports-site)
                  (assoc :status "active")
                  (assoc :author admin-user)
                  (assoc-in [:type :type-code] 2120) ; ice hockey hall
                  (assoc-in [:location :city :city-code] 91) ; Helsinki
                  (set-coordinates [24.9500 60.1800])
                  (assoc-in [:properties :area-m2] 2000)
                  (assoc-in [:properties :year-round-use?] true)
                  (assoc-in [:properties :lighting?] true)
                  (assoc :construction-year 2020)
                  (assoc :owner "Helsinki")
                  (assoc :admin "Helsinki"))

        ;; Site 4: Planned gym in Vantaa
        site4 (-> (tu/gen-sports-site)
                  (assoc :status "planned")
                  (assoc :author admin-user)
                  (assoc-in [:type :type-code] 4510) ; gym
                  (assoc-in [:location :city :city-code] 92) ; Vantaa
                  (set-coordinates [25.0400 60.2900])
                  (assoc-in [:properties :area-m2] 800)
                  (assoc :construction-year 2025)
                  (assoc :owner "Vantaa")
                  (assoc :admin "Vantaa"))

        sites [site1 site2 site3 site4]]

    ;; Save all sites to database and index to Elasticsearch
    (doseq [site sites]
      (core/upsert-sports-site!* db admin-user site)
      (core/index! search site :sync))

    ;; Return created sites for reference
    sites))

(defn- valid-bbox
  "Returns a valid bounding box for Helsinki area"
  []
  {:min-x 24.8 :max-x 25.1 :min-y 60.1 :max-y 60.3})

(defn- valid-heatmap-params
  "Returns valid heatmap parameters for testing"
  ([]
   (valid-heatmap-params {}))
  ([overrides]
   (merge {:zoom 10
           :bbox (valid-bbox)
           :dimension :density
           :weight-by :count}
          overrides)))

;;; Tests ;;;

(deftest create-heatmap-success-test
  (testing "Successfully creates heatmap with valid data"
    (create-test-sports-sites)
    (let [params (valid-heatmap-params)

          resp (app (-> (mock/request :post "/api/actions/create-heatmap")
                        (mock/content-type "application/transit+json")
                        (mock/header "Accept" "application/transit+json")
                        (mock/body (->transit params))))

          body (<-transit (:body resp))
          features (:data body)
          metadata (:metadata body)]

      (is (= 200 (:status resp)))
      (is (some? body))
      (is (contains? body :data))
      (is (contains? body :metadata))

      ;; Verify we have the expected number of features (4 test sites)
      (is (= 4 (count features)) "Should have 4 grid cells for our 4 test sites")

      ;; Verify metadata structure and values
      (is (= :density (:dimension metadata)))
      (is (= :count (:weight-by metadata)))
      (is (= 4 (:total-features metadata)))

      ;; Verify each feature has proper GeoJSON structure
      (doseq [feature features]
        (is (= "Feature" (:type feature)))
        (is (= "Point" (get-in feature [:geometry :type])))
        (is (vector? (get-in feature [:geometry :coordinates])))
        (is (= 2 (count (get-in feature [:geometry :coordinates]))))

        ;; Verify coordinates are reasonable (within Finland)
        (let [[lon lat] (get-in feature [:geometry :coordinates])]
          (is (and (>= lon 24.0) (<= lon 26.0)) "Longitude should be within Finland")
          (is (and (>= lat 60.0) (<= lat 61.0)) "Latitude should be within Finland"))

        ;; Verify properties structure
        (is (contains? (:properties feature) :weight))
        (is (contains? (:properties feature) :grid_key))
        (is (contains? (:properties feature) :doc_count))
        (is (contains? (:properties feature) :normalized-weight))

        ;; Verify property types and ranges
        (is (pos? (get-in feature [:properties :weight])))
        (is (string? (get-in feature [:properties :grid_key])))
        (is (pos? (get-in feature [:properties :doc_count])))
        (is (and (>= (get-in feature [:properties :normalized-weight]) 0.0)
                 (<= (get-in feature [:properties :normalized-weight]) 1.0)))))))

(deftest create-heatmap-dimension-density-test
  (testing "Density dimension returns correct weights based on facility count"
    (create-test-sports-sites)
    (let [params (valid-heatmap-params {:dimension :density :weight-by :count})

          resp (app (-> (mock/request :post "/api/actions/create-heatmap")
                        (mock/content-type "application/transit+json")
                        (mock/header "Accept" "application/transit+json")
                        (mock/body (->transit params))))

          body (<-transit (:body resp))
          features (:data body)]

      (is (= 200 (:status resp)))
      (is (every? #(contains? (:properties %) :weight) features))
      (is (every? #(>= (get-in % [:properties :weight]) 0) features))
      (is (every? #(contains? (:properties %) :doc_count) features))
      ;; Should have some results since we created test sites
      (is (> (count features) 0)))))

(deftest create-heatmap-dimension-area-test
  (testing "Area dimension returns correct weights based on total area"
    (create-test-sports-sites)
    (let [params (valid-heatmap-params {:dimension :area})

          resp (app (-> (mock/request :post "/api/actions/create-heatmap")
                        (mock/content-type "application/transit+json")
                        (mock/header "Accept" "application/transit+json")
                        (mock/body (->transit params))))

          body (<-transit (:body resp))
          features (:data body)]

      (is (= 200 (:status resp)))
      (is (every? #(contains? (:properties %) :weight) features))
      (is (every? #(>= (get-in % [:properties :weight]) 0) features)))))

(deftest create-heatmap-with-filters-test
  (testing "Filters correctly limit results"
    (create-test-sports-sites)

    ;; Test with type filter - only swimming halls (3110)
    (let [params (valid-heatmap-params {:filters {:type-codes [3110]}})

          resp (app (-> (mock/request :post "/api/actions/create-heatmap")
                        (mock/content-type "application/transit+json")
                        (mock/header "Accept" "application/transit+json")
                        (mock/body (->transit params))))

          body (<-transit (:body resp))
          features (:data body)]

      (is (= 200 (:status resp)))
      (is (coll? features)))

    ;; Test with city filter - only Helsinki (91)
    (let [params (valid-heatmap-params {:filters {:city-codes [91]}})

          resp (app (-> (mock/request :post "/api/actions/create-heatmap")
                        (mock/content-type "application/transit+json")
                        (mock/header "Accept" "application/transit+json")
                        (mock/body (->transit params))))

          body (<-transit (:body resp))]

      (is (= 200 (:status resp)))
      (is (coll? (:data body))))

    ;; Test with status filter - only active sites
    (let [params (valid-heatmap-params {:filters {:status-codes #{"active"}}})

          resp (app (-> (mock/request :post "/api/actions/create-heatmap")
                        (mock/content-type "application/transit+json")
                        (mock/header "Accept" "application/transit+json")
                        (mock/body (->transit params))))

          body (<-transit (:body resp))]

      (is (= 200 (:status resp)))
      (is (coll? (:data body))))))

(deftest create-heatmap-year-round-filter-test
  (testing "Year-round filter works correctly"
    (create-test-sports-sites)
    (let [params (valid-heatmap-params {:filters {:year-round-only true}})

          resp (app (-> (mock/request :post "/api/actions/create-heatmap")
                        (mock/content-type "application/transit+json")
                        (mock/header "Accept" "application/transit+json")
                        (mock/body (->transit params))))

          body (<-transit (:body resp))]

      (is (= 200 (:status resp)))
      (is (coll? (:data body))))))

(deftest create-heatmap-dimension-year-round-test
  (testing "Year-round dimension returns facilities with year-round use"
    (create-test-sports-sites)
    (let [params (valid-heatmap-params {:dimension :year-round})

          resp (app (-> (mock/request :post "/api/actions/create-heatmap")
                        (mock/content-type "application/transit+json")
                        (mock/header "Accept" "application/transit+json")
                        (mock/body (->transit params))))

          body (<-transit (:body resp))]

      (is (= 200 (:status resp)))
      (is (coll? (:data body))))))

(deftest create-heatmap-validation-test
  (testing "Validates parameters according to schema"
    ;; Invalid zoom (:keyword)
    (let [params (valid-heatmap-params {:zoom :kissa})

          resp (app (-> (mock/request :post "/api/actions/create-heatmap")
                        (mock/content-type "application/transit+json")
                        (mock/header "Accept" "application/transit+json")
                        (mock/body (->transit params))))]

      (is (= 400 (:status resp))))

    ;; Invalid dimension
    (let [params (valid-heatmap-params {:dimension :invalid})

          resp (app (-> (mock/request :post "/api/actions/create-heatmap")
                        (mock/content-type "application/transit+json")
                        (mock/header "Accept" "application/transit+json")
                        (mock/body (->transit params))))]

      (is (= 400 (:status resp))))

    ;; Missing required bbox
    (let [params (dissoc (valid-heatmap-params) :bbox)

          resp (app (-> (mock/request :post "/api/actions/create-heatmap")
                        (mock/content-type "application/transit+json")
                        (mock/header "Accept" "application/transit+json")
                        (mock/body (->transit params))))]

      (is (= 400 (:status resp))))

    ;; Invalid bbox coordinates
    (let [params (valid-heatmap-params {:bbox {:min-x "invalid" :max-x 25.1 :min-y 60.1 :max-y 60.3}})

          resp (app (-> (mock/request :post "/api/actions/create-heatmap")
                        (mock/content-type "application/transit+json")
                        (mock/header "Accept" "application/transit+json")
                        (mock/body (->transit params))))]

      (is (= 400 (:status resp))))))

(deftest get-heatmap-facets-success-test
  (testing "Successfully returns available facet values"
    (create-test-sports-sites)
    (let [params {:bbox (valid-bbox)}

          resp (app (-> (mock/request :post "/api/actions/get-heatmap-facets")
                        (mock/content-type "application/transit+json")
                        (mock/header "Accept" "application/transit+json")
                        (mock/body (->transit params))))

          body (<-transit (:body resp))]

      (is (= 200 (:status resp)))
      (is (some? body))
      (is (contains? body :type-codes))
      (is (contains? body :owners))
      (is (contains? body :admins))
      (is (contains? body :year-range))
      (is (contains? body :statuses))

      ;; Check structure of facets
      (is (vector? (:type-codes body)))
      (is (vector? (:owners body)))
      (is (vector? (:admins body)))
      (is (map? (:year-range body)))
      (is (vector? (:statuses body)))

      ;; Check that we have the expected type codes from our test data
      (let [type-codes (set (map :value (:type-codes body)))]
        (is (some #{3110} type-codes)) ; swimming hall
        (is (some #{1110} type-codes)) ; football field
        (is (some #{2120} type-codes)) ; ice hockey hall
        (is (some #{4510} type-codes))) ; gym

      ;; Check year range includes our test data years
      (let [year-range (:year-range body)]
        (is (<= (:min year-range) 2010))
        (is (>= (:max year-range) 2025))))))

(deftest get-heatmap-facets-with-filters-test
  (testing "Facets respect existing filters"
    (create-test-sports-sites)
    (let [params {:bbox (valid-bbox)
                  :filters {:status-codes #{"active"}}}

          resp (app (-> (mock/request :post "/api/actions/get-heatmap-facets")
                        (mock/content-type "application/transit+json")
                        (mock/header "Accept" "application/transit+json")
                        (mock/body (->transit params))))

          body (<-transit (:body resp))]

      (is (= 200 (:status resp)))
      (is (some? body))

      ;; Should only return "active" status, not "planned"
      (let [statuses (set (map :value (:statuses body)))]
        (is (contains? statuses "active"))
        (is (not (contains? statuses "planned"))))

      ;; Should have facets for 3 active sites (sites 1, 2, 3)
      ;; Types should only include those from active sites: 3110, 1110, 2120
      (let [type-codes (set (map :value (:type-codes body)))]
        (is (= 3 (count type-codes)))
        (is (contains? type-codes 3110)) ; swimming hall
        (is (contains? type-codes 1110)) ; football field  
        (is (contains? type-codes 2120)) ; ice hockey hall
        (is (not (contains? type-codes 4510)))) ; gym (planned site)

      ;; Owners should only include those with active sites
      (let [owners (set (map :value (:owners body)))]
        (is (contains? owners "Helsinki")) ; 2 active sites
        (is (contains? owners "Espoo")) ; 1 active site
        (is (not (contains? owners "Vantaa")))))))

(deftest get-heatmap-facets-validation-test
  (testing "Validates facet parameters"
    ;; Missing bbox
    (let [params {}

          resp (app (-> (mock/request :post "/api/actions/get-heatmap-facets")
                        (mock/content-type "application/transit+json")
                        (mock/header "Accept" "application/transit+json")
                        (mock/body (->transit params))))]

      (is (= 400 (:status resp))))

    ;; Invalid bbox structure
    (let [params {:bbox {:invalid "structure"}}

          resp (app (-> (mock/request :post "/api/actions/get-heatmap-facets")
                        (mock/content-type "application/transit+json")
                        (mock/header "Accept" "application/transit+json")
                        (mock/body (->transit params))))]

      (is (= 400 (:status resp))))))

(deftest empty-results-test
  (testing "Handles empty results gracefully"
    ;; Don't create any test sites
    (let [params (valid-heatmap-params)

          resp (app (-> (mock/request :post "/api/actions/create-heatmap")
                        (mock/content-type "application/transit+json")
                        (mock/header "Accept" "application/transit+json")
                        (mock/body (->transit params))))

          body (<-transit (:body resp))]

      (is (= 200 (:status resp)))
      (is (some? body))
      (is (coll? (:data body)))
      (is (empty? (:data body)))
      (is (= 0 (get-in body [:metadata :total-features]))))))

(deftest bbox-filtering-test
  (testing "Bounding box correctly filters spatial results"
    (let [test-sites (create-test-sports-sites)]

         ;; Test 1: Helsinki bbox should only include Helsinki sites (sites 1 & 3)
      (let [helsinki-bbox {:min-x 24.9 :max-x 25.0 :min-y 60.15 :max-y 60.25}
            params (valid-heatmap-params {:bbox helsinki-bbox})

            resp (app (-> (mock/request :post "/api/actions/create-heatmap")
                          (mock/content-type "application/transit+json")
                          (mock/header "Accept" "application/transit+json")
                          (mock/body (->transit params))))

            body (<-transit (:body resp))
            features (:data body)]

        (is (= 200 (:status resp)))
        (is (coll? features))

           ;; Verify that we have results within the Helsinki bbox
           ;; The heatmap aggregates by geohash, so we check that all returned
           ;; geohash buckets fall within our bbox bounds
        (is (> (count features) 0) "Should have at least one grid cell with data")

           ;; All returned features should have coordinates within the bbox
        (doseq [feature features]
          (let [coords (get-in feature [:geometry :coordinates])]
            (is (and (>= (first coords) 24.9)
                     (<= (first coords) 25.0)
                     (>= (second coords) 60.15)
                     (<= (second coords) 60.25))
                (str "Feature coordinates " coords " should be within Helsinki bbox")))))

         ;; Test 2: Espoo bbox should only include Espoo site (site 2)
      (let [espoo-bbox {:min-x 24.8 :max-x 24.85 :min-y 60.16 :max-y 60.18}
            params (valid-heatmap-params {:bbox espoo-bbox})

            resp (app (-> (mock/request :post "/api/actions/create-heatmap")
                          (mock/content-type "application/transit+json")
                          (mock/header "Accept" "application/transit+json")
                          (mock/body (->transit params))))

            body (<-transit (:body resp))
            features (:data body)]

        (is (= 200 (:status resp)))
        (is (coll? features))

           ;; Should have at least one result from Espoo
        (is (> (count features) 0) "Should have at least one grid cell with Espoo data")

           ;; All returned features should have coordinates within the Espoo bbox
        (doseq [feature features]
          (let [coords (get-in feature [:geometry :coordinates])]
            (is (and (>= (first coords) 24.8)
                     (<= (first coords) 24.85)
                     (>= (second coords) 60.16)
                     (<= (second coords) 60.18))
                (str "Feature coordinates " coords " should be within Espoo bbox")))))

         ;; Test 3: Empty bbox should return no results
      (let [empty-bbox {:min-x 20.0 :max-x 20.1 :min-y 50.0 :max-y 50.1}
            params (valid-heatmap-params {:bbox empty-bbox})

            resp (app (-> (mock/request :post "/api/actions/create-heatmap")
                          (mock/content-type "application/transit+json")
                          (mock/header "Accept" "application/transit+json")
                          (mock/body (->transit params))))

            body (<-transit (:body resp))
            features (:data body)]

        (is (= 200 (:status resp)))
        (is (coll? features))
        (is (= 0 (count features)) "Empty area should return no grid cells"))

         ;; Test 4: Large bbox should include all sites
      (let [large-bbox {:min-x 24.0 :max-x 26.0 :min-y 60.0 :max-y 61.0}
            params (valid-heatmap-params {:bbox large-bbox})

            resp (app (-> (mock/request :post "/api/actions/create-heatmap")
                          (mock/content-type "application/transit+json")
                          (mock/header "Accept" "application/transit+json")
                          (mock/body (->transit params))))

            body (<-transit (:body resp))
            features (:data body)]

        (is (= 200 (:status resp)))
        (is (coll? features))

           ;; Should have multiple grid cells covering all our test sites
        (is (= (count features) (count test-sites))
            "Large bbox should include grid cells from all areas")

           ;; All returned features should have coordinates within the large bbox
        (doseq [feature features]
          (let [coords (get-in feature [:geometry :coordinates])]
            (is (and (>= (first coords) 24.0)
                     (<= (first coords) 26.0)
                     (>= (second coords) 60.0)
                     (<= (second coords) 61.0))
                (str "Feature coordinates " coords " should be within large bbox"))))))))

(deftest different-zoom-levels-test
  (testing "Different zoom levels produce different precision"
    (create-test-sports-sites)

    ;; Test low zoom (coarse aggregation) - should group nearby sites together
    (let [low-zoom-params (valid-heatmap-params {:zoom 5})
          low-zoom-resp (app (-> (mock/request :post "/api/actions/create-heatmap")
                                 (mock/content-type "application/transit+json")
                                 (mock/header "Accept" "application/transit+json")
                                 (mock/body (->transit low-zoom-params))))
          low-zoom-body (<-transit (:body low-zoom-resp))
          low-zoom-features (:data low-zoom-body)]

      (is (= 200 (:status low-zoom-resp)))
      (is (= 2 (count low-zoom-features)) "Low zoom should group sites into 2 coarse grid cells")

      ;; Verify grid keys are shorter (coarser precision)
      (let [grid-keys (map #(get-in % [:properties :grid_key]) low-zoom-features)]
        (is (every? #(= 4 (count %)) grid-keys) "Low zoom grid keys should be 4 characters")
        (is (= #{"ud9w" "ud9y"} (set grid-keys)) "Should have expected coarse grid keys")))

    ;; Test medium zoom
    (let [medium-zoom-params (valid-heatmap-params {:zoom 10})
          medium-zoom-resp (app (-> (mock/request :post "/api/actions/create-heatmap")
                                    (mock/content-type "application/transit+json")
                                    (mock/header "Accept" "application/transit+json")
                                    (mock/body (->transit medium-zoom-params))))
          medium-zoom-body (<-transit (:body medium-zoom-resp))
          medium-zoom-features (:data medium-zoom-body)]

      (is (= 200 (:status medium-zoom-resp)))
      (is (= 4 (count medium-zoom-features)) "Medium zoom should have 4 grid cells for our 4 sites")

      ;; Verify grid keys are medium length
      (let [grid-keys (map #(get-in % [:properties :grid_key]) medium-zoom-features)]
        (is (every? #(= 7 (count %)) grid-keys) "Medium zoom grid keys should be 7 characters")))

    ;; Test high zoom (fine aggregation) - should have separate cells for each site
    (let [high-zoom-params (valid-heatmap-params {:zoom 15})
          high-zoom-resp (app (-> (mock/request :post "/api/actions/create-heatmap")
                                  (mock/content-type "application/transit+json")
                                  (mock/header "Accept" "application/transit+json")
                                  (mock/body (->transit high-zoom-params))))
          high-zoom-body (<-transit (:body high-zoom-resp))
          high-zoom-features (:data high-zoom-body)]

      (is (= 200 (:status high-zoom-resp)))
      (is (= 4 (count high-zoom-features)) "High zoom should have 4 fine grid cells")

      ;; Verify grid keys are longer (finer precision)
      (let [grid-keys (map #(get-in % [:properties :grid_key]) high-zoom-features)]
        (is (every? #(= 8 (count %)) grid-keys) "High zoom grid keys should be 8 characters")
        (is (every? #(> (count %) 7) grid-keys) "High zoom keys should be longer than medium zoom")))))

;;; Unit Tests for Helper Functions ;;;

(deftest zoom-precision-mapping-test
  (testing "Zoom levels map to correct geohash precision"
    (is (= 4 (heatmap/zoom->precision 5)))
    (is (= 5 (heatmap/zoom->precision 7)))
    (is (= 6 (heatmap/zoom->precision 9)))
    (is (= 7 (heatmap/zoom->precision 11)))
    (is (= 8 (heatmap/zoom->precision 13)))
    (is (= 8 (heatmap/zoom->precision 15)))
    (is (= 8 (heatmap/zoom->precision 20)))))

(deftest normalize-weights-test
  (testing "Weight normalization works correctly"
    ;; Normal case
    (let [weights [10 20 30]
          normalized (heatmap/normalize-weights weights)]
      (is (= [0.0 0.5 1.0] normalized)))

    ;; All same values
    (let [weights [5 5 5]
          normalized (heatmap/normalize-weights weights)]
      (is (= [0.5 0.5 0.5] normalized)))

    ;; Empty collection
    (is (nil? (heatmap/normalize-weights [])))

    ;; Single value
    (let [weights [42]
          normalized (heatmap/normalize-weights weights)]
      (is (= [0.5] normalized)))))

(deftest malli-schema-validation-test
  (testing "Malli schemas validate correctly"
    ;; Valid HeatmapParams
    (let [valid-params {:zoom 10
                        :bbox {:min-x 24.8 :max-x 25.1 :min-y 60.1 :max-y 60.3}
                        :dimension :density
                        :weight-by :count}]
      (is (m/validate heatmap/HeatmapParams valid-params)))

    ;; Invalid HeatmapParams - missing bbox
    (let [invalid-params {:zoom 10
                          :dimension :density}]
      (is (not (m/validate heatmap/HeatmapParams invalid-params))))

    ;; Valid FacetParams
    (let [valid-facet-params {:bbox {:min-x 24.8 :max-x 25.1 :min-y 60.1 :max-y 60.3}}]
      (is (m/validate heatmap/FacetParams valid-facet-params)))

    ;; Invalid FacetParams - missing bbox
    (let [invalid-facet-params {:filters {:type-codes [1110]}}]
      (is (not (m/validate heatmap/FacetParams invalid-facet-params))))))

(comment
  ;; Run all tests
  (clojure.test/run-tests *ns*)

  ;; Run specific test
  (clojure.test/run-test-var #'create-heatmap-success-test)
  (clojure.test/run-test-var #'get-heatmap-facets-success-test)
  (clojure.test/run-test-var #'create-heatmap-validation-test)
  (clojure.test/run-test-var #'different-zoom-levels-test)
  (clojure.test/run-test-var #'get-heatmap-facets-with-filters-test)
  (clojure.test/run-test-var #'bbox-filtering-test)

  ;; Run just unit tests
  (clojure.test/run-test-var #'zoom-precision-mapping-test)
  (clojure.test/run-test-var #'normalize-weights-test)
  (clojure.test/run-test-var #'malli-schema-validation-test))
