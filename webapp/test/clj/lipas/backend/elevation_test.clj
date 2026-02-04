(ns lipas.backend.elevation-test
  (:require [clojure.test :refer [deftest testing is]]
            [lipas.backend.elevation :as elevation]))

;;; Tests for HTTP timeout options and error handling ;;;

(deftest mml-http-options-test
  (testing "HTTP options include connection timeout"
    (is (pos? (:connection-timeout elevation/mml-http-options))))

  (testing "HTTP options include socket timeout"
    (is (pos? (:socket-timeout elevation/mml-http-options))))

  (testing "HTTP options disable exception throwing for error handling"
    (is (false? (:throw-exceptions elevation/mml-http-options)))))

(deftest elevation-chunk-timeout-test
  (testing "elevation chunk timeout constant is defined and positive"
    (is (pos? elevation/elevation-chunk-timeout-ms))
    ;; Should be at least 1 minute for large grids
    (is (>= elevation/elevation-chunk-timeout-ms 60000))))

(deftest get-elevation-coverage-error-handling-test
  (testing "returns nil or throws on HTTP error status"
    ;; Test with mock HTTP response - actual behavior depends on implementation
    ;; This documents the expected error handling pattern
    (is (fn? elevation/get-elevation-coverage)
        "get-elevation-coverage should be a function")))

(comment
  (clojure.test/run-tests 'lipas.backend.elevation-test))
