(ns lipas.backend.api.v2-activities-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [lipas.backend.api.v2 :as v2]))

(deftest sports-sites-query-activities-filtering-test
  (testing "Activity filtering uses search-meta.activities field"
    (testing "Single activity filter"
      (let [query (v2/->sports-sites-query {:activities ["cycling"]})]
        (is (some? (get-in query [:query :bool :filter]))
            "Query should have a filter clause")

        (let [filters (get-in query [:query :bool :filter])
              activity-filter (first (filter #(contains? % :terms) filters))]
          (is (some? activity-filter)
              "Should have a terms filter")
          (is (= ["cycling"] (get-in activity-filter [:terms :search-meta.activities]))
              "Should filter by search-meta.activities with cycling"))))

    (testing "Multiple activity filters"
      (let [query (v2/->sports-sites-query {:activities ["cycling" "swimming" "hiking"]})]
        (let [filters (get-in query [:query :bool :filter])
              activity-filter (first (filter #(contains? % :terms) filters))]
          (is (= ["cycling" "swimming" "hiking"]
                 (get-in activity-filter [:terms :search-meta.activities]))
              "Should filter by search-meta.activities with all activities"))))

    (testing "No activities filter when parameter is nil"
      (let [query (v2/->sports-sites-query {})]
        (let [filters (get-in query [:query :bool :filter])
              activity-filters (filter #(contains? (get-in % [:terms]) :search-meta.activities) filters)]
          (is (empty? activity-filters)
              "Should not have activity filter when activities param is nil"))))

    (testing "No activities filter when parameter is empty"
      (let [query (v2/->sports-sites-query {:activities []})]
        (let [filters (get-in query [:query :bool :filter])
              activity-filters (filter #(contains? (get-in % [:terms]) :search-meta.activities) filters)]
          (is (empty? activity-filters)
              "Should not have activity filter when activities param is empty")))))

  (testing "Activities filter works with other filters"
    (let [query (v2/->sports-sites-query
                 {:activities ["cycling"]
                  :statuses ["active"]
                  :type-codes [101 102]
                  :city-codes [91]})]
      (let [filters (get-in query [:query :bool :filter])]
        (is (= 4 (count filters))
            "Should have 4 filters: status, type, city, and activities")

        (let [activity-filter (first (filter #(contains? (get % :terms {}) :search-meta.activities) filters))
              activities (get-in activity-filter [:terms :search-meta.activities])]
          (is (= ["cycling"] activities)
              "Activity filter should be present alongside other filters"))))))
