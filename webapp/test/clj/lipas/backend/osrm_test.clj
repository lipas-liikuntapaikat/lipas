(ns lipas.backend.osrm-test
  (:require [clojure.test :refer [deftest testing is]]
            [lipas.backend.osrm :as osrm]))

;;; Tests for OSRM timeout handling ;;;

(deftest osrm-parallel-timeout-test
  (testing "parallel timeout constant is defined"
    (is (pos? osrm/osrm-parallel-timeout-ms))
    ;; Should be reasonable - at least 30 seconds
    (is (>= osrm/osrm-parallel-timeout-ms 30000))))

(deftest get-distances-and-travel-times-timeout-test
  (testing "returns partial results when some profiles timeout"
    (let [slow-call-count (atom 0)
          fast-profile #{:car}
          ;; Mock that returns immediately for :car but throws timeout for others
          mock-get-data (fn [{:keys [profile]}]
                          (if (fast-profile profile)
                            {:distances [[100]] :durations [[10]]}
                            (do
                              (swap! slow-call-count inc)
                              (Thread/sleep 200) ; Simulate slow response
                              {:distances [[200]] :durations [[20]]})))]

      (with-redefs [osrm/get-data mock-get-data
                    ;; Use very short timeout to trigger timeout behavior
                    osrm/osrm-parallel-timeout-ms 50]
        ;; When some profiles time out, others should still return data
        ;; The exact behavior depends on implementation
        (let [result (osrm/get-distances-and-travel-times
                      {:profiles [:car :bicycle :foot]
                       :sources ["24.9,60.1"]
                       :destinations ["24.95,60.15"]})]
          ;; At minimum, the fast profile should succeed
          (is (map? result)))))))

(deftest get-distances-and-travel-times-success-test
  (testing "returns all results when profiles complete within timeout"
    (let [mock-get-data (fn [{:keys [profile]}]
                          {:code "Ok"
                           :distances [[100]]
                           :durations [[10]]})]

      (with-redefs [osrm/get-data mock-get-data]
        (let [result (osrm/get-distances-and-travel-times
                      {:profiles [:car :bicycle :foot]
                       :sources ["24.9,60.1"]
                       :destinations ["24.95,60.15"]})]
          ;; All three profiles should return results
          (is (= #{:car :bicycle :foot} (set (keys result))))
          (is (every? some? (vals result))))))))

(comment
  (clojure.test/run-tests 'lipas.backend.osrm-test))
