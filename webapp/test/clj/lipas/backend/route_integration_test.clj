(ns lipas.backend.route-integration-test
  "Integration tests for the complete route ordering flow"
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [lipas.backend.core :as core]
   [lipas.test-utils :as tu]
   [ring.mock.request :as mock]))

;;; Test setup ;;;

(use-fixtures :once
  (fn [f]
    (tu/ensure-test-database!)
    (f)))

(use-fixtures :each
  (fn [f]
    (tu/prune-db!)
    (tu/prune-es!)
    (f)))

;;; Integration tests ;;;

(deftest end-to-end-route-ordering-flow-test
  (testing "Complete flow: Create route → Get ordering suggestion → Save with ordering → Retrieve"
    (let [user (tu/gen-user {:db? true :admin? true})
          ;; Step 1: Create a route with unordered segments
          initial-geoms {:type "FeatureCollection"
                         :features [{:type "Feature"
                                     :id "10"
                                     :properties {:fid "10"}
                                     :geometry {:type "LineString"
                                                :coordinates [[0 0] [1 0]]}}
                                    {:type "Feature"
                                     :id "20"
                                     :properties {:fid "20"}
                                     :geometry {:type "LineString"
                                                :coordinates [[2 0] [3 0]]}}
                                    {:type "Feature"
                                     :id "30"
                                     :properties {:fid "30"}
                                     :geometry {:type "LineString"
                                                :coordinates [[1 0] [2 0]]}}]}
          initial-route (-> (tu/gen-sports-site)
                            (assoc :lipas-id 55555
                                   :type-code 4411
                                   :name {:fi "Integration Test Route"}
                                   :status "active"
                                   :activities {:cycling
                                                {:routes [{:fids ["10" "20" "30"]
                                                           :ordering-method "manual"}]}})
                            (assoc-in [:location :city] {:city-code "091"})
                            (assoc-in [:location :geometries] initial-geoms))
          _ (tu/save-sports-site! user initial-route)

          ;; Step 2: Call the route ordering API
          ordering-request {:lipas-id 55555
                            :activity-type "cycling"
                            :fids ["20" "10" "30"]} ; Wrong order
          resp (tu/app (-> (mock/request :post "/api/actions/suggest-route-order")
                           (mock/content-type "application/json")
                           (mock/body (tu/->json ordering-request))))
          ordering-result (tu/<-json (:body resp))]

      (is (= 200 (:status resp)))
      (is (= "high" (:confidence ordering-result)))
      ;; Should be reordered to connect properly: 10→30→20
      (is (= [{:fid "10" :direction "forward"}
              {:fid "30" :direction "forward"}
              {:fid "20" :direction "forward"}]
             (:segments ordering-result)))

      ;; Step 3: Update the route with the suggested ordering
      (let [updated-route (-> (core/get-sports-site2 tu/search 55555)
                              (assoc-in [:activities :cycling :routes 0 :segments] (:segments ordering-result))
                              (assoc-in [:activities :cycling :routes 0 :fids] (->> ordering-result :segments (mapv :fid)))
                              (assoc-in [:activities :cycling :routes 0 :ordering-method] "auto"))
            save-result (tu/save-sports-site! user updated-route)]

        (is (= 55555 (:lipas-id save-result)))
        (is (= (:segments ordering-result) (get-in save-result [:activities :cycling :routes 0 :segments])))
        (is (= ["10" "30" "20"] (get-in save-result [:activities :cycling :routes 0 :fids])))

        ;; Step 4: Verify the saved data is retrievable
        (let [final-route (core/get-sports-site2 tu/search 55555)]
          (is (= (:segments ordering-result) (get-in final-route [:activities :cycling :routes 0 :segments])))
          (is (= "auto" (get-in final-route [:activities :cycling :routes 0 :ordering-method])))
          (is (= ["10" "30" "20"] (get-in final-route [:activities :cycling :routes 0 :fids]))))))))

(comment
  (clojure.test/run-tests 'lipas.backend.route-integration-test)
  (clojure.test/run-test end-to-end-route-ordering-flow-test)

  )
