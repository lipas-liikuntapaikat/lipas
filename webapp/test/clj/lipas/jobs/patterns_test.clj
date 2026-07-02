(ns lipas.jobs.patterns-test
  "Tests for reliability patterns: exponential backoff and the in-memory
  circuit breaker."
  (:require
   [clojure.test :refer [deftest testing is use-fixtures]]
   [lipas.jobs.patterns :as patterns]))

(use-fixtures :each (fn [f] (patterns/reset-breakers!) (f)))

;; =============================================================================
;; Exponential backoff
;; =============================================================================

(deftest exponential-backoff-test
  (testing "Backoff grows exponentially"
    (let [delays (map #(patterns/exponential-backoff-ms % :jitter 0.0) (range 5))]
      (is (= [1000 2000 4000 8000 16000] delays))))

  (testing "Backoff is capped at max-ms"
    (is (= 5000 (patterns/exponential-backoff-ms 20 :max-ms 5000 :jitter 0.0))))

  (testing "Jitter stays within bounds"
    (dotimes [_ 50]
      (let [delay (patterns/exponential-backoff-ms 3 :jitter 0.1)]
        ;; 8000 ± 10%
        (is (<= 7200 delay 8800)))))

  (testing "Backoff never goes negative"
    (dotimes [_ 20]
      (is (>= (patterns/exponential-backoff-ms 0 :jitter 1.0) 0)))))

;; =============================================================================
;; In-memory circuit breaker
;; =============================================================================

(defn failing! []
  (patterns/with-circuit-breaker "test-service" {:failure-threshold 3
                                                 :open-duration-ms 60000}
    (throw (ex-info "service down" {}))))

(deftest circuit-breaker-opens-test
  (testing "Breaker opens after the failure threshold"
    ;; First failures pass the underlying exception through
    (dotimes [_ 3]
      (is (thrown-with-msg? Exception #"service down" (failing!))))

    ;; Now the breaker is open: calls fail fast without invoking the body
    (let [called? (atom false)]
      (let [ex (try
                 (patterns/with-circuit-breaker "test-service" {:failure-threshold 3
                                                                :open-duration-ms 60000}
                   (reset! called? true))
                 nil
                 (catch Exception e e))]
        (is (some? ex))
        (is (patterns/circuit-breaker-open? ex))
        (is (false? @called?) "Body must not run while the breaker is open")))))

(deftest circuit-breaker-independent-services-test
  (testing "Breakers are tracked per service"
    (dotimes [_ 3]
      (is (thrown? Exception (failing!))))

    ;; Other services are unaffected
    (is (= :ok (patterns/with-circuit-breaker "other-service" {}
                 :ok)))))

(deftest circuit-breaker-half-open-test
  (testing "After open-duration a trial call is allowed; success closes the breaker"
    (dotimes [_ 2]
      (is (thrown? Exception
                   (patterns/with-circuit-breaker "svc" {:failure-threshold 2
                                                         :open-duration-ms 50}
                     (throw (ex-info "down" {}))))))

    ;; Open: fails fast
    (let [ex (try (patterns/with-circuit-breaker "svc" {:failure-threshold 2
                                                        :open-duration-ms 50}
                    :ok)
                  nil
                  (catch Exception e e))]
      (is (patterns/circuit-breaker-open? ex)))

    ;; Wait past open duration - trial call succeeds and closes the breaker
    (Thread/sleep 80)
    (is (= :ok (patterns/with-circuit-breaker "svc" {:failure-threshold 2
                                                     :open-duration-ms 50}
                 :ok)))

    ;; Fully closed again
    (is (= :ok (patterns/with-circuit-breaker "svc" {:failure-threshold 2
                                                     :open-duration-ms 50}
                 :ok))))

  (testing "Failed trial call reopens the breaker"
    (patterns/reset-breakers!)
    (dotimes [_ 2]
      (is (thrown? Exception
                   (patterns/with-circuit-breaker "svc2" {:failure-threshold 2
                                                          :open-duration-ms 50}
                     (throw (ex-info "down" {}))))))

    (Thread/sleep 80)
    ;; Trial call fails
    (is (thrown-with-msg? Exception #"still down"
                          (patterns/with-circuit-breaker "svc2" {:failure-threshold 2
                                                                 :open-duration-ms 50}
                            (throw (ex-info "still down" {})))))

    ;; Immediately open again: fail fast
    (let [ex (try (patterns/with-circuit-breaker "svc2" {:failure-threshold 2
                                                         :open-duration-ms 50}
                    :ok)
                  nil
                  (catch Exception e e))]
      (is (patterns/circuit-breaker-open? ex)))))

(deftest circuit-breaker-success-resets-failures-test
  (testing "A success resets the consecutive-failure count"
    (dotimes [_ 2]
      (is (thrown? Exception
                   (patterns/with-circuit-breaker "svc3" {:failure-threshold 3}
                     (throw (ex-info "flaky" {}))))))

    ;; Success resets the count
    (is (= :ok (patterns/with-circuit-breaker "svc3" {:failure-threshold 3} :ok)))

    ;; Two more failures still don't reach the threshold of 3
    (dotimes [_ 2]
      (is (thrown? Exception
                   (patterns/with-circuit-breaker "svc3" {:failure-threshold 3}
                     (throw (ex-info "flaky" {}))))))

    (is (= :ok (patterns/with-circuit-breaker "svc3" {:failure-threshold 3} :ok))
        "Breaker must still be closed")))
