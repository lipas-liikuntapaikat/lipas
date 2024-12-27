(ns lipas.timestamps-test
  (:require [clojure.test :refer [deftest testing is]]
            [lipas.schema.common :as common-schema]))

(def iso8601-pattern common-schema/-iso8601-pattern)

(deftest iso8601-timestamp-test
  (testing "Valid UTC timestamps"
    (is (re-matches iso8601-pattern "2024-12-27T13:25:00Z"))
    (is (re-matches iso8601-pattern "2024-12-27T13:25:00.123Z"))
    (is (re-matches iso8601-pattern "2024-01-01T00:00:00Z"))
    (is (re-matches iso8601-pattern "2024-12-31T23:59:59Z"))
    (is (re-matches iso8601-pattern "2024-02-29T00:00:00Z")) ; Valid leap year
    (is (re-matches iso8601-pattern "2000-02-29T00:00:00Z"))) ; Valid century leap year

  (testing "Invalid timestamps"
    ; Non-UTC timestamps (should all fail)
    (is (nil? (re-matches iso8601-pattern "2024-12-27T13:25:00+02:00")))
    (is (nil? (re-matches iso8601-pattern "2024-12-27T13:25:00-02:00")))
    (is (nil? (re-matches iso8601-pattern "2024-12-27T13:25:00.123+02:00")))

    ; Invalid dates
    (is (nil? (re-matches iso8601-pattern "2024-13-01T00:00:00Z"))) ; Invalid month
    (is (nil? (re-matches iso8601-pattern "2024-00-01T00:00:00Z"))) ; Invalid month
    (is (nil? (re-matches iso8601-pattern "2024-12-32T00:00:00Z"))) ; Invalid day
    (is (nil? (re-matches iso8601-pattern "2024-12-00T00:00:00Z"))) ; Invalid day
    (is (nil? (re-matches iso8601-pattern "2023-02-29T00:00:00Z"))) ; Invalid leap year date

    ; Invalid times
    (is (nil? (re-matches iso8601-pattern "2024-12-27T24:00:00Z"))) ; Invalid hour
    (is (nil? (re-matches iso8601-pattern "2024-12-27T12:60:00Z"))) ; Invalid minute
    (is (nil? (re-matches iso8601-pattern "2024-12-27T12:00:60Z"))) ; Invalid second

    ; Invalid formats
    (is (nil? (re-matches iso8601-pattern "2024-12-27 12:00:00Z"))) ; Space instead of T
    (is (nil? (re-matches iso8601-pattern "2024-12-27T12:00:00"))) ; Missing Z
    (is (nil? (re-matches iso8601-pattern "24-12-27T12:00:00Z"))) ; Two-digit year
    (is (nil? (re-matches iso8601-pattern "2024/12/27T12:00:00Z")))) ; Wrong separators

  (testing "Edge cases for months with different lengths"
    (is (re-matches iso8601-pattern "2024-01-31T00:00:00Z"))      ; January 31 valid
    (is (nil? (re-matches iso8601-pattern "2024-04-31T00:00:00Z"))) ; April 31 invalid
    (is (re-matches iso8601-pattern "2024-04-30T00:00:00Z"))      ; April 30 valid
    (is (nil? (re-matches iso8601-pattern "2024-02-30T00:00:00Z"))) ; February 30 invalid
    (is (re-matches iso8601-pattern "2024-06-30T00:00:00Z"))      ; June 30 valid
    (is (nil? (re-matches iso8601-pattern "2024-06-31T00:00:00Z")))) ; June 31 invalid

  (testing "Fractional seconds variations"
    (is (re-matches iso8601-pattern "2024-12-27T13:25:00.1Z"))
    (is (re-matches iso8601-pattern "2024-12-27T13:25:00.12Z"))
    (is (re-matches iso8601-pattern "2024-12-27T13:25:00.123Z"))
    (is (re-matches iso8601-pattern "2024-12-27T13:25:00.1234567890Z"))))


(comment
  (clojure.test/run-tests *ns*)
  )
