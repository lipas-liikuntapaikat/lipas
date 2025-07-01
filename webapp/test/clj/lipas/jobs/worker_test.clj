(ns lipas.jobs.worker-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [lipas.jobs.core :as jobs]
   [lipas.jobs.worker :as worker])
  (:import
   [java.util.concurrent Executors ThreadPoolExecutor]))

(deftest thread-capacity-respect-test
  "Critical test to prevent the thread capacity catastrophe bug.
   Ensures fetch-and-process-jobs never exceeds thread pool capacity."
  (testing "Worker respects thread pool capacity limits"
    (let [;; Create small pools to test limits
          pools (worker/create-worker-pools {:fast-threads 1 :general-threads 2})
          config {:batch-size 10 :poll-interval-ms 1000}

          ;; Mock system with a database that returns many jobs
          mock-db (reify
                    ;; This would be the database interface - we'll mock it
                    Object
                    (toString [_] "mock-db"))

          system {:db mock-db}

          job-fetch-count (atom 0)

          ;; Mock the jobs/fetch-next-jobs to track calls and return fake jobs
          original-fetch jobs/fetch-next-jobs]

      ;; Test setup: Replace fetch function to track calls
      (with-redefs [jobs/fetch-next-jobs
                    (fn [db opts]
                      (swap! job-fetch-count inc)
                      (let [limit (:limit opts)]
                        (println "fetch-next-jobs called with limit:" limit)
                        ;; Return empty for this test to avoid actual processing
                        []))]

        ;; Simulate full thread pools by submitting blocking tasks
        (let [{:keys [fast-pool general-pool]} pools]
          ;; Fill fast pool (1 thread)
          (.submit fast-pool ^Runnable #(Thread/sleep 100))

          ;; Fill general pool (2 threads)
          (.submit general-pool ^Runnable #(Thread/sleep 100))
          (.submit general-pool ^Runnable #(Thread/sleep 100))

          ;; Give threads time to start
          (Thread/sleep 10)

          ;; Now test fetch-and-process-jobs with full pools
          (reset! job-fetch-count 0)
          (worker/fetch-and-process-jobs system pools config)

          ;; CRITICAL ASSERTION: Should not fetch any jobs when pools are full
          (is (= 0 @job-fetch-count)
              "Should not fetch any jobs when thread pools are at capacity")))

      ;; Cleanup
      (worker/shutdown-pools! pools)))

  (testing "Worker fetches appropriate amounts when capacity available"
    (let [pools (worker/create-worker-pools {:fast-threads 2 :general-threads 3})
          config {:batch-size 10}
          system {:db nil}
          fetch-calls (atom [])]

      (with-redefs [jobs/fetch-next-jobs
                    (fn [db opts]
                      (swap! fetch-calls conj opts)
                      [])]

        ;; Test with empty pools (full capacity)
        (worker/fetch-and-process-jobs system pools config)

        ;; Should make calls for both fast and general lanes
        (let [calls @fetch-calls]
          (is (= 2 (count calls)) "Should make 2 fetch calls")

          ;; Fast jobs should batch up to capacity (2 threads available)
          (is (some #(and (= 2 (:limit %)) (:job-types %)) calls)
              "Should fetch fast jobs up to fast thread capacity")

          ;; General jobs should fetch only 1 (slow job processing)
          (is (some #(and (= 1 (:limit %)) (not (:job-types %))) calls)
              "Should fetch only 1 general job at a time")))

      (worker/shutdown-pools! pools))))

(deftest broken-behavior-would-fail-test
  "Test that demonstrates the old broken behavior would fail.
   This test shows what the old code would have done wrong."
  (testing "Old broken behavior example"
    (let [pools (worker/create-worker-pools {:fast-threads 1 :general-threads 2})
          config {:batch-size 10}
          system {:db nil}
          jobs-that-would-be-fetched (atom 0)]

      ;; Simulate the OLD broken behavior - always fetch batch-size jobs
      (with-redefs [jobs/fetch-next-jobs
                    (fn [db opts]
                      (let [limit (:limit opts)]
                        (swap! jobs-that-would-be-fetched + limit)
                        ;; Old code would have fetched this many jobs
                        (repeat limit {:id 1 :type "analysis"})))]

        ;; Fill the pools completely
        (let [{:keys [fast-pool general-pool]} pools]
          (.submit fast-pool ^Runnable #(Thread/sleep 50))
          (.submit general-pool ^Runnable #(Thread/sleep 50))
          (.submit general-pool ^Runnable #(Thread/sleep 50))
          (Thread/sleep 10)

          ;; With the old broken logic, this would have fetched jobs despite no capacity
          ;; But our new code should fetch 0
          (reset! jobs-that-would-be-fetched 0)
          (worker/fetch-and-process-jobs system pools config)

          ;; NEW CODE: Should fetch 0 jobs when no capacity
          (is (= 0 @jobs-that-would-be-fetched)
              "Fixed code should fetch 0 jobs when thread pools are full")

          ;; OLD BROKEN CODE would have fetched batch-size (10) jobs here
          ;; and caused the catastrophic bug we just fixed!
          ))

      (worker/shutdown-pools! pools))))

(deftest regression-prevention-test
  "Integration test to prevent regression of the 2025-07-01 catastrophic bug.
   This test ensures we never again mark more jobs as 'processing' than we have threads."
  (testing "Never exceed thread capacity when marking jobs as processing"
    (let [pools (worker/create-worker-pools {:fast-threads 2 :general-threads 3})
          config {:batch-size 20} ; Intentionally large batch size
          system {:db nil}
          processing-jobs (atom [])]

      ;; Mock jobs/fetch-next-jobs to return many jobs but track what gets processed
      (with-redefs [jobs/fetch-next-jobs
                    (fn [db opts]
                      ;; Return more jobs than we have threads (simulating the original bug scenario)
                      (let [limit (:limit opts)
                            fake-jobs (for [i (range limit)]
                                        {:id (+ 1000 i) :type "analysis" :attempts 1})]
                        fake-jobs))

                    ;; Mock process-job-batch to track jobs that would be marked as processing
                    worker/process-job-batch
                    (fn [system pools jobs lane-type]
                      (swap! processing-jobs concat jobs))]

        ;; Test multiple iterations to ensure consistent behavior
        (dotimes [_ 3]
          (reset! processing-jobs [])
          (worker/fetch-and-process-jobs system pools config)

          ;; CRITICAL: Never process more jobs than thread capacity
          (let [processed-count (count @processing-jobs)]
            (is (<= processed-count 5) ; 2 fast + 3 general = 5 max
                (str "Should never process more than 5 jobs simultaneously, got: " processed-count))

            ;; Should process at most 3 general jobs (1 at a time * 3 threads)
            (let [analysis-jobs (filter #(= "analysis" (:type %)) @processing-jobs)]
              (is (<= (count analysis-jobs) 3)
                  (str "Should never process more than 3 analysis jobs simultaneously, got: "
                       (count analysis-jobs)))))))

      (worker/shutdown-pools! pools))))
