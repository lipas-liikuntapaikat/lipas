(ns lipas.jobs.patterns-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [lipas.jobs.patterns :as patterns])
  (:import
   [java.util.concurrent TimeoutException]))

(deftest exponential-backoff-test
  (testing "Exponential backoff calculation"
    (testing "increases exponentially"
      (let [b0 (patterns/exponential-backoff-ms 0 :jitter 0)
            b1 (patterns/exponential-backoff-ms 1 :jitter 0)
            b2 (patterns/exponential-backoff-ms 2 :jitter 0)
            b3 (patterns/exponential-backoff-ms 3 :jitter 0)]
        (is (= 1000 b0))
        (is (= 2000 b1))
        (is (= 4000 b2))
        (is (= 8000 b3))))

    (testing "respects max delay"
      (let [delay (patterns/exponential-backoff-ms 100 :max-ms 5000 :jitter 0)]
        (is (= 5000 delay))))

    (testing "applies jitter"
      (let [delays (repeatedly 10 #(patterns/exponential-backoff-ms 2 :jitter 0.5))
            base 4000
            min-expected (* base 0.5)
            max-expected (* base 1.5)]
        (is (every? #(and (>= % min-expected) (<= % max-expected)) delays))
        (is (> (count (distinct delays)) 1))))))

(deftest timeout-test
  (testing "with-timeout macro"
    (testing "returns result when completed in time"
      (is (= 42 (patterns/with-timeout 1000
                  (Thread/sleep 10)
                  42))))

    (testing "throws TimeoutException when timeout exceeded"
      (is (thrown? TimeoutException
                   (patterns/with-timeout 50
                     (Thread/sleep 100)
                     42))))

    (testing "error message includes timeout duration"
      (try
        (patterns/with-timeout 50
          (Thread/sleep 100)
          42)
        (catch TimeoutException e
          (is (re-find #"50ms" (.getMessage e))))))))

(deftest retry-test
  (testing "with-retry macro"
    (testing "returns result on success"
      (is (= 42 (patterns/with-retry {:max-attempts 3}
                  42))))

    (testing "retries on failure"
      (let [counter (atom 0)
            result (patterns/with-retry {:max-attempts 3}
                     (if (< (swap! counter inc) 3)
                       (throw (Exception. "fail"))
                       "success"))]
        (is (= "success" result))
        (is (= 3 @counter))))

    (testing "respects max attempts"
      (let [counter (atom 0)]
        (is (thrown? Exception
                     (patterns/with-retry {:max-attempts 2}
                       (swap! counter inc)
                       (throw (Exception. "always fails")))))
        (is (= 2 @counter))))

    (testing "retry-on predicate filters exceptions"
      (let [counter (atom 0)]
        (is (thrown? IllegalArgumentException
                     (patterns/with-retry {:max-attempts 5
                                           :retry-on #(instance? java.io.IOException %)}
                       (swap! counter inc)
                       (throw (IllegalArgumentException. "not retryable")))))
        (is (= 1 @counter))))

    (testing "on-retry callback is called"
      (let [retry-calls (atom [])]
        (try
          (patterns/with-retry {:max-attempts 3
                                :on-retry #(swap! retry-calls conj %)}
            (throw (Exception. "fail")))
          (catch Exception _))
        (is (= 2 (count @retry-calls)))
        (is (every? #(contains? % :attempt) @retry-calls))
        (is (every? #(contains? % :delay-ms) @retry-calls))))))