(ns lipas.jobs.patterns-test
  (:require
   [clojure.test :refer [deftest testing is use-fixtures]]
   [integrant.core :as ig]
   [lipas.backend.config :as config]
   [lipas.jobs.patterns :as patterns]
   [lipas.jobs.db :as jobs-db]
   [lipas.test-utils :as test-utils]
   [next.jdbc :as jdbc])
  (:import
   [java.util.concurrent TimeoutException]))

;; Test system setup - following the pattern from correlation-test
(defonce test-system (atom nil))

(defn setup-test-system! []
  (test-utils/ensure-test-database!)
  (reset! test-system
          (ig/init (select-keys (config/->system-config test-utils/config) [:lipas/db]))))

(defn teardown-test-system! []
  (when @test-system
    (ig/halt! @test-system)
    (reset! test-system nil)))

(use-fixtures :once
  (fn [f]
    (setup-test-system!)
    (f)
    (teardown-test-system!)))

(use-fixtures :each
  (fn [f]
    ;; Clean up circuit breakers table before each test
    (let [db (:lipas/db @test-system)]
      (jdbc/execute! db ["DELETE FROM circuit_breakers"])
      (f))))

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

;; Circuit Breaker Tests using real database

(deftest circuit-breaker-state-test
  (testing "circuit-breaker-state function"
    (testing "returns :closed for nil breaker"
      (is (= :closed (patterns/circuit-breaker-state nil {}))))

    (testing "returns :closed when state is closed"
      (is (= :closed (patterns/circuit-breaker-state {:state "closed"} {}))))

    (testing "returns :open when state is open and within timeout"
      (let [breaker {:state "open"
                     :opened_at (java.sql.Timestamp. (System/currentTimeMillis))}]
        (is (= :open (patterns/circuit-breaker-state breaker {:open-duration-ms 60000})))))

    (testing "returns :half-open when state is open but timeout expired"
      (let [breaker {:state "open"
                     :opened_at (java.sql.Timestamp. (- (System/currentTimeMillis) 70000))}]
        (is (= :half-open (patterns/circuit-breaker-state breaker {:open-duration-ms 60000})))))

    (testing "returns keyword version of state"
      (is (= :half-open (patterns/circuit-breaker-state {:state "half-open"} {}))))))

(deftest circuit-breaker-test
  (let [db (:lipas/db @test-system)]
    (testing "Circuit breaker functionality with real database"
      (testing "successful operations when circuit is closed"
        ;; First successful call - no breaker exists yet
        (is (= "success" (patterns/with-circuit-breaker db "test-service" {}
                           "success")))
        ;; Breaker should not be created for successful calls when none exists
        (is (nil? (patterns/get-circuit-breaker db "test-service")))

        ;; Create a breaker manually
        (patterns/update-circuit-breaker! db "test-service"
                                          {:state "closed"
                                           :failure_count 0
                                           :success_count 0})

        ;; Successful call should increment success count
        (is (= "success" (patterns/with-circuit-breaker db "test-service" {}
                           "success")))
        (let [breaker (patterns/get-circuit-breaker db "test-service")]
          (is (= 1 (:success_count breaker)))))

      (testing "circuit opens after threshold failures"
        (let [failure-count (atom 0)
              on-open-called (atom nil)]
          ;; Clean up any existing breaker
          (jdbc/execute! db ["DELETE FROM circuit_breakers WHERE service_name = ?" "failing-service"])

          ;; Fail multiple times to trigger circuit opening
          (dotimes [n 5]
            (try
              (patterns/with-circuit-breaker db "failing-service"
                {:failure-threshold 5
                 :on-open #(reset! on-open-called %)}
                (throw (Exception. "Service failure")))
              (catch Exception e
                (swap! failure-count inc))))

          (is (= 5 @failure-count))
          (let [breaker (patterns/get-circuit-breaker db "failing-service")]
            (is (= "open" (:state breaker)))
            (is (= 5 (:failure_count breaker)))
            (is (some? (:opened_at breaker)))
            (is (some? (:last_failure_at breaker))))

          ;; Check on-open callback was called
          (is (= {:service "failing-service"
                  :failure-count 5}
                 @on-open-called))))

      (testing "circuit breaker blocks calls when open"
        ;; Clean up and manually open the circuit
        (jdbc/execute! db ["DELETE FROM circuit_breakers WHERE service_name = ?" "blocked-service"])
        (patterns/update-circuit-breaker! db "blocked-service"
                                          {:state "open"
                                           :opened_at (java.sql.Timestamp. (System/currentTimeMillis))})

        (try
          (patterns/with-circuit-breaker db "blocked-service" {}
            "should not execute")
          (catch Exception e
            (is (= "Circuit breaker is open" (.getMessage e)))
            (is (= ::patterns/circuit-breaker-open (:type (ex-data e))))
            (is (= "blocked-service" (:service (ex-data e)))))))

      (testing "half-open state allows one test request"
        ;; Clean up and set up an open circuit that should transition to half-open
        (jdbc/execute! db ["DELETE FROM circuit_breakers WHERE service_name = ?" "half-open-service"])
        (patterns/update-circuit-breaker! db "half-open-service"
                                          {:state "open"
                                           :opened_at (java.sql.Timestamp. (- (System/currentTimeMillis) 70000))
                                           :failure_count 5})

        ;; Successful test request should close the circuit
        (is (= "recovered" (patterns/with-circuit-breaker db "half-open-service"
                             {:open-duration-ms 60000}
                             "recovered")))

        (let [breaker (patterns/get-circuit-breaker db "half-open-service")]
          (is (= "closed" (:state breaker)))
          (is (= 0 (:failure_count breaker)))
          (is (= 1 (:success_count breaker)))))

      (testing "half-open state reopens on failure"
        ;; Clean up and set up an open circuit that should transition to half-open
        (jdbc/execute! db ["DELETE FROM circuit_breakers WHERE service_name = ?" "still-failing-service"])
        (patterns/update-circuit-breaker! db "still-failing-service"
                                          {:state "open"
                                           :opened_at (java.sql.Timestamp. (- (System/currentTimeMillis) 70000))
                                           :failure_count 5})

        ;; Failed test request should reopen the circuit
        (try
          (patterns/with-circuit-breaker db "still-failing-service"
            {:open-duration-ms 60000}
            (throw (Exception. "Still failing")))
          (catch Exception e
            (is (= "Still failing" (.getMessage e)))))

        (let [breaker (patterns/get-circuit-breaker db "still-failing-service")]
          (is (= "open" (:state breaker)))
          (is (some? (:opened_at breaker)))))

      (testing "incremental failure tracking"
        ;; Clean up
        (jdbc/execute! db ["DELETE FROM circuit_breakers WHERE service_name = ?" "incremental-service"])

        ;; Fail a few times but stay under threshold
        (dotimes [n 3]
          (try
            (patterns/with-circuit-breaker db "incremental-service"
              {:failure-threshold 5}
              (throw (Exception. "Failure")))
            (catch Exception _)))

        (let [breaker (patterns/get-circuit-breaker db "incremental-service")]
          (is (= 3 (:failure_count breaker)))
          (is (not= "open" (:state breaker))))

        ;; One success doesn't reset failure count (in this implementation)
        (patterns/with-circuit-breaker db "incremental-service"
          {:failure-threshold 5}
          "success")

        ;; Continue failing to reach threshold
        (dotimes [n 2]
          (try
            (patterns/with-circuit-breaker db "incremental-service"
              {:failure-threshold 5}
              (throw (Exception. "Failure")))
            (catch Exception _)))

        (let [breaker (patterns/get-circuit-breaker db "incremental-service")]
          (is (= "open" (:state breaker)))
          (is (= 5 (:failure_count breaker))))))))

(deftest circuit-breaker-concurrency-test
  (let [db (:lipas/db @test-system)]
    (testing "Circuit breaker handles concurrent access correctly"
      ;; Clean up
      (jdbc/execute! db ["DELETE FROM circuit_breakers WHERE service_name = ?" "concurrent-service"])

      (let [results (atom [])
            threads 10]
        ;; Run multiple threads that will fail concurrently
        (let [futures (doall
                       (for [i (range threads)]
                         (future
                           (try
                             (patterns/with-circuit-breaker db "concurrent-service"
                               {:failure-threshold 5}
                               (throw (Exception. "Concurrent failure")))
                             (swap! results conj {:thread i :result :success})
                             (catch Exception e
                               (swap! results conj {:thread i
                                                    :result :failure
                                                    :error (.getMessage e)}))))))]
          ;; Wait for all threads to complete
          (doseq [f futures]
            @f))

        ;; Check that the circuit breaker opened correctly
        (let [breaker (patterns/get-circuit-breaker db "concurrent-service")
              failure-results (filter #(= :failure (:result %)) @results)]
          ;; All threads should have recorded failures
          (is (= threads (count failure-results)))
          ;; Circuit should be open after threshold is reached
          (is (= "open" (:state breaker)))
          ;; Failure count should be at least the threshold
          (is (>= (:failure_count breaker) 5)))))))

(deftest circuit-breaker-reset-test
  (let [db (:lipas/db @test-system)]
    (testing "Circuit breaker can be manually reset"
      ;; Set up a failed circuit breaker
      (jdbc/execute! db ["DELETE FROM circuit_breakers WHERE service_name = ?" "reset-service"])
      (patterns/update-circuit-breaker! db "reset-service"
                                        {:state "open"
                                         :failure_count 10
                                         :success_count 5
                                         :opened_at (java.sql.Timestamp. (System/currentTimeMillis))})

      ;; Reset it manually using SQL
      (jdbc/execute! db ["UPDATE circuit_breakers SET state = 'closed', failure_count = 0, success_count = 0 WHERE service_name = ?" "reset-service"])

      (let [breaker (patterns/get-circuit-breaker db "reset-service")]
        ;; Should be reset to closed state
        (is (= "closed" (:state breaker)))
        (is (= 0 (:failure_count breaker)))
        (is (= 0 (:success_count breaker)))))))

(comment
  (clojure.test/run-test-var #'circuit-breaker-state-test)
  (clojure.test/run-test-var #'circuit-breaker-test)
  (clojure.test/run-test-var #'circuit-breaker-concurrency-test)
  (clojure.test/run-test-var #'circuit-breaker-reset-test)
  )
