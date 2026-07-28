(ns lipas.jobs.worker-test
  "Tests for worker job execution: watchdog timeouts, retry/dead-letter
  handling and thread-capacity-aware fetching.

  Execution semantics are tested through worker/execute-job! against a real
  database; only the job handler itself is redefined (there is no external
  service to call in tests)."
  (:require
    [clojure.test :refer [deftest testing is use-fixtures]]
    [lipas.jobs.core :as jobs]
    [lipas.jobs.dispatcher :as dispatcher]
    [lipas.jobs.registry :as registry]
    [lipas.jobs.worker :as worker]
    [lipas.test-utils :as test-utils])
  (:import
    [java.util.concurrent Executors ScheduledExecutorService ThreadPoolExecutor]))

(defonce test-system (atom nil))

(let [{:keys [once each]} (test-utils/db-only-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn test-db [] (:lipas/db @test-system))

(defn with-watchdog [f]
  (let [watchdog (Executors/newSingleThreadScheduledExecutor)]
    (try
      (f watchdog)
      (finally
        (.shutdownNow ^ScheduledExecutorService watchdog)))))

(defn enqueue-and-fetch!
  "Enqueue an email job and fetch it into processing state.
  Returns the fetched job map."
  [db & [opts]]
  (jobs/enqueue-job! db "email" {:to "w@example.com" :subject "W" :body "B"} opts)
  (let [[job] (jobs/fetch-next-jobs db {:limit 1})]
    (is (some? job) "Job should be fetchable")
    job))

;; =============================================================================
;; Execution semantics
;; =============================================================================

(deftest execute-job-success-test
  (testing "Successful handler marks the job completed"
    (with-watchdog
      (fn [watchdog]
        (let [db (test-db)
              job (enqueue-and-fetch! db)
              handled (atom nil)]
          (with-redefs [dispatcher/handle-job (fn [_system j] (reset! handled (:id j)))]
            (worker/execute-job! {:db db} watchdog job))

          (is (= (:id job) @handled))
          (is (= "completed" (:jobs/status (test-utils/get-job-by-id db (:id job))))))))))

(deftest execute-job-failure-retry-test
  (testing "Failing handler schedules a retry"
    (with-watchdog
      (fn [watchdog]
        (let [db (test-db)
              job (enqueue-and-fetch! db)]
          (with-redefs [dispatcher/handle-job (fn [_ _] (throw (ex-info "boom" {})))]
            (worker/execute-job! {:db db} watchdog job))

          (let [after (test-utils/get-job-by-id db (:id job))]
            (is (= "pending" (:jobs/status after)))
            (is (= 1 (:jobs/attempts after)))
            (is (= "boom" (:jobs/error_message after)))))))))

(deftest execute-job-exhausted-dead-letter-test
  (testing "Failing handler with no attempts left dead-letters the job"
    (with-watchdog
      (fn [watchdog]
        (let [db (test-db)
              job (enqueue-and-fetch! db {:max-attempts 1})]
          (with-redefs [dispatcher/handle-job (fn [_ _] (throw (ex-info "fatal" {})))]
            (worker/execute-job! {:db db} watchdog job))

          (is (nil? (test-utils/get-job-by-id db (:id job)))
              "Dead jobs are removed from the jobs table")
          (let [[dlj] (jobs/get-dead-letter-jobs db {:acknowledged false})]
            (is (= "fatal" (:error-message dlj)))))))))

(deftest execute-job-timeout-test
  (testing "Job exceeding its timeout is interrupted and marked failed"
    (with-watchdog
      (fn [watchdog]
        (let [db (test-db)
              job (enqueue-and-fetch! db)
              interrupted? (atom false)]
          (with-redefs [registry/timeout-ms (fn [_] 200)
                        dispatcher/handle-job (fn [_ _]
                                                (try
                                                  (Thread/sleep 5000)
                                                  (catch InterruptedException _
                                                    (reset! interrupted? true)
                                                    (throw (InterruptedException.)))))]
            (worker/execute-job! {:db db} watchdog job))

          (is @interrupted? "Handler thread must actually be interrupted")
          (let [after (test-utils/get-job-by-id db (:id job))]
            (is (= "pending" (:jobs/status after)) "Timed-out job is retried")
            (is (re-find #"timed out" (:jobs/error_message after))))))))

  (testing "Timeout interrupt does not leak into subsequent jobs on the same thread"
    (with-watchdog
      (fn [watchdog]
        (let [db (test-db)
              slow-job (enqueue-and-fetch! db)]
          ;; First: a job that times out on this very thread
          (with-redefs [registry/timeout-ms (fn [_] 100)
                        dispatcher/handle-job (fn [_ _]
                                                (try (Thread/sleep 3000)
                                                     (catch InterruptedException _
                                                       (throw (InterruptedException.)))))]
            (worker/execute-job! {:db db} watchdog slow-job))

          ;; Then: a normal job on the same thread must run undisturbed
          (let [ok-job (enqueue-and-fetch! db)
                completed? (atom false)]
            (with-redefs [dispatcher/handle-job
                          (fn [_ _]
                            ;; A leaked interrupt flag would make sleep throw
                            (Thread/sleep 50)
                            (reset! completed? true))]
              (worker/execute-job! {:db db} watchdog ok-job))

            (is @completed? "Next job on the same thread runs normally")
            (is (= "completed" (:jobs/status (test-utils/get-job-by-id db (:id ok-job))))))))))

  (testing "Job finishing just under the timeout is not marked as timed out"
    (with-watchdog
      (fn [watchdog]
        (let [db (test-db)
              job (enqueue-and-fetch! db)]
          (with-redefs [registry/timeout-ms (fn [_] 10000)
                        dispatcher/handle-job (fn [_ _] (Thread/sleep 50))]
            (worker/execute-job! {:db db} watchdog job))
          (is (= "completed" (:jobs/status (test-utils/get-job-by-id db (:id job))))))))))

(defn- uninterruptible-handler
  "Simulates a handler stuck in non-interruptible IO: swallows interrupts
  and keeps running for duration-ms, then returns normally."
  [duration-ms]
  (fn [_ _]
    (let [deadline (+ (System/currentTimeMillis) duration-ms)]
      (while (< (System/currentTimeMillis) deadline)
        (try (Thread/sleep 20)
             (catch InterruptedException _))))))

(deftest watchdog-escalation-zombie-test
  (testing "Handler ignoring the interrupt: watchdog finalizes at timeout+grace, late completion is a fenced no-op"
    (with-watchdog
      (fn [watchdog]
        (let [db (test-db)
              job (enqueue-and-fetch! db)
              zombies-before (:zombie-count @worker/worker-state 0)]
          (with-redefs [registry/timeout-ms (fn [_] 100)
                        worker/watchdog-grace-ms 200
                        dispatcher/handle-job (uninterruptible-handler 1200)]
            ;; Blocks for the full 1200ms; the watchdog escalates at ~300ms
            (worker/execute-job! {:db db} watchdog job))

          (let [after (test-utils/get-job-by-id db (:id job))]
            (is (= "pending" (:jobs/status after))
                "The watchdog force-finalized the job as a retry, and the zombie's late completion could not overwrite it")
            (is (re-find #"did not respond to interrupt" (:jobs/error_message after)))
            (is (= 1 (:jobs/attempts after))))

          (is (= (inc zombies-before) (:zombie-count @worker/worker-state))
              "The zombie is counted in worker stats")))))

  (testing "Same scenario with attempts exhausted: watchdog dead-letters the job"
    (with-watchdog
      (fn [watchdog]
        (let [db (test-db)
              job (enqueue-and-fetch! db {:max-attempts 1})]
          (with-redefs [registry/timeout-ms (fn [_] 100)
                        worker/watchdog-grace-ms 200
                        dispatcher/handle-job (uninterruptible-handler 1200)]
            (worker/execute-job! {:db db} watchdog job))

          (is (nil? (test-utils/get-job-by-id db (:id job)))
              "The job left the jobs table at timeout+grace")
          (let [[dlj] (jobs/get-dead-letter-jobs db {:acknowledged false})]
            (is (re-find #"did not respond to interrupt" (:error-message dlj)))))))))

(deftest watchdog-escalation-not-triggered-on-cooperative-handlers-test
  (testing "A handler that unwinds promptly on interrupt is finalized by the pool thread, not the watchdog"
    (with-watchdog
      (fn [watchdog]
        (let [db (test-db)
              job (enqueue-and-fetch! db)
              zombies-before (:zombie-count @worker/worker-state 0)]
          (with-redefs [registry/timeout-ms (fn [_] 100)
                        worker/watchdog-grace-ms 200
                        dispatcher/handle-job (fn [_ _]
                                                (try (Thread/sleep 3000)
                                                     (catch InterruptedException _
                                                       (throw (InterruptedException.)))))]
            (worker/execute-job! {:db db} watchdog job))

          ;; Give a cancelled escalation task time to (not) fire
          (Thread/sleep 400)
          (let [after (test-utils/get-job-by-id db (:id job))]
            (is (= "pending" (:jobs/status after)))
            (is (re-find #"timed out" (:jobs/error_message after)))
            (is (not (re-find #"did not respond" (:jobs/error_message after)))
                "The pool thread's finalization wins; escalation was cancelled"))
          (is (= zombies-before (:zombie-count @worker/worker-state))
              "No zombie is counted for a cooperative handler"))))))

(deftest execute-job-error-message-fallback-test
  (testing "Exceptions without a message still produce a non-null error"
    (with-watchdog
      (fn [watchdog]
        (let [db (test-db)
              job (enqueue-and-fetch! db {:max-attempts 1})]
          (with-redefs [dispatcher/handle-job (fn [_ _] (throw (NullPointerException.)))]
            (worker/execute-job! {:db db} watchdog job))
          (let [[dlj] (jobs/get-dead-letter-jobs db {:acknowledged false})]
            (is (string? (:error-message dlj)))
            (is (seq (:error-message dlj)))))))))

;; =============================================================================
;; Full worker loop
;; =============================================================================

(deftest worker-loop-integration-test
  (testing "Worker polls, executes and completes jobs end to end"
    (let [db (test-db)
          test-emailer (test-utils/create-test-emailer)
          {:keys [id]} (jobs/enqueue-job! db "email" {:to "loop@example.com"
                                                      :subject "Loop"
                                                      :body "Hello"})]
      (try
        (worker/start-mixed-duration-worker!
          {:db db :emailer test-emailer :search nil}
          {:fast-threads 1 :general-threads 1 :batch-size 5 :poll-interval-ms 200})

        (is (test-utils/wait-for-condition
              (fn [] (= "completed" (:jobs/status (test-utils/get-job-by-id db id))))
              10000)
            "Email job should complete")

        (is (= 1 (count @(:sent-emails test-emailer))))
        (is (= "loop@example.com" (:to (first @(:sent-emails test-emailer)))))

        (finally
          (worker/stop-mixed-duration-worker!))))))

(deftest worker-double-start-guard-test
  (testing "Starting an already-running worker is a no-op"
    (let [db (test-db)]
      (try
        (worker/start-mixed-duration-worker! {:db db} {:poll-interval-ms 200})
        (let [pools-before (:pools @worker/worker-state)]
          (worker/start-mixed-duration-worker! {:db db} {:poll-interval-ms 200})
          (is (identical? pools-before (:pools @worker/worker-state))
              "Second start must not replace the running pools"))
        (finally
          (worker/stop-mixed-duration-worker!))))))

;; =============================================================================
;; Capacity-aware fetching
;; =============================================================================

(deftest thread-capacity-respect-test
  (testing "No jobs are fetched when thread pools are at capacity"
    (let [pools (worker/create-worker-pools {:fast-threads 1 :general-threads 2})
          config {:batch-size 10}
          fetch-count (atom 0)]
      (try
        (with-redefs [jobs/fetch-next-jobs (fn [_ _] (swap! fetch-count inc) [])]
          ;; Fill both pools with blocking tasks
          (let [{:keys [fast-pool general-pool]} pools]
            (.submit ^ThreadPoolExecutor fast-pool ^Runnable #(Thread/sleep 200))
            (.submit ^ThreadPoolExecutor general-pool ^Runnable #(Thread/sleep 200))
            (.submit ^ThreadPoolExecutor general-pool ^Runnable #(Thread/sleep 200))
            (Thread/sleep 50)

            (worker/fetch-and-process-jobs {:db nil} pools nil config)
            (is (= 0 @fetch-count)
                "Must not fetch jobs when no thread capacity is available")))
        (finally
          (worker/shutdown-pools! pools)))))

  (testing "Fetch limits match free thread capacity"
    (let [pools (worker/create-worker-pools {:fast-threads 2 :general-threads 3})
          config {:batch-size 10}
          fetch-calls (atom [])]
      (try
        (with-redefs [jobs/fetch-next-jobs (fn [_ opts] (swap! fetch-calls conj opts) [])]
          (worker/fetch-and-process-jobs {:db nil} pools nil config)

          (let [calls @fetch-calls]
            (is (= 2 (count calls)) "One fetch per lane")
            (is (some #(and (= 2 (:limit %)) (:job-types %)) calls)
                "Fast lane fetches up to fast thread capacity")
            (is (some #(and (= 3 (:limit %)) (not (:job-types %))) calls)
                "General lane fetches up to general thread capacity")))
        (finally
          (worker/shutdown-pools! pools))))))
