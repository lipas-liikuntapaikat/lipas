(ns lipas.jobs.deduplication-test
  "Tests for job deduplication and debounce.

  Semantics: at most one *pending* job per (type, dedup-key). A job that is
  already processing does not suppress a new enqueue - the running execution
  may be working on stale data, so a successor must be allowed."
  (:require
    [clojure.test :refer [deftest testing is use-fixtures]]
    [lipas.jobs.core :as jobs]
    [lipas.test-utils :as test-utils]
    [next.jdbc :as jdbc]))

(defonce test-system (atom nil))

(let [{:keys [once each]} (test-utils/db-only-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn test-db [] (:lipas/db @test-system))

(defn job-count [db]
  (count (test-utils/get-all-jobs db)))

(deftest registry-dedup-test
  (let [db (test-db)]

    (testing "Same site enqueued twice yields a single pending analysis job"
      (let [first-result (jobs/enqueue-job! db "analysis" {:lipas-id 123})
            second-result (jobs/enqueue-job! db "analysis" {:lipas-id 123})]
        (is (some? first-result))
        (is (nil? second-result) "Duplicate enqueue returns nil")
        (is (= 1 (job-count db)))))

    (testing "Different sites are not deduplicated"
      (jobs/enqueue-job! db "analysis" {:lipas-id 456})
      (is (= 2 (job-count db))))

    (testing "Different types with the same site are not deduplicated"
      (jobs/enqueue-job! db "elevation" {:lipas-id 123})
      (is (= 3 (job-count db))))))

(deftest explicit-dedup-key-test
  (let [db (test-db)]
    (testing "Explicit dedup key overrides the registry key"
      (is (some? (jobs/enqueue-job! db "email"
                                    {:to "a@example.com" :subject "S" :body "B"}
                                    {:dedup-key "custom-key"})))
      (is (nil? (jobs/enqueue-job! db "email"
                                   {:to "b@example.com" :subject "S2" :body "B2"}
                                   {:dedup-key "custom-key"})))
      (is (= 1 (job-count db))))

    (testing "Jobs without dedup keys are never deduplicated"
      (jobs/enqueue-job! db "email" {:to "x@example.com" :subject "S" :body "B"})
      (jobs/enqueue-job! db "email" {:to "x@example.com" :subject "S" :body "B"})
      (is (= 3 (job-count db))))))

(deftest pending-only-dedup-test
  (let [db (test-db)]

    (testing "A processing job does not suppress a successor"
      (jobs/enqueue-job! db "analysis" {:lipas-id 42}
                         {:run-at (java.sql.Timestamp/from (java.time.Instant/now))})
      (let [[fetched] (jobs/fetch-next-jobs db {:limit 1})]
        (is (some? fetched) "Job was fetched into processing")

        ;; New edit arrives while the job is processing
        (let [successor (jobs/enqueue-job! db "analysis" {:lipas-id 42})]
          (is (some? successor)
              "Edit during processing must enqueue a new job - the running one may use stale data")
          (is (= 2 (job-count db))))))

    (testing "A completed job does not suppress a new enqueue"
      (test-utils/prune-db! db)
      (let [{:keys [id]} (jobs/enqueue-job! db "analysis" {:lipas-id 7})]
        (jdbc/execute! db ["UPDATE jobs SET run_at = now() WHERE id = ?" id])
        (jobs/fetch-next-jobs db {:limit 1})
        (jobs/mark-completed! db id)
        (is (some? (jobs/enqueue-job! db "analysis" {:lipas-id 7})))))))

(deftest sequential-execution-guard-test
  (let [db (test-db)
        now-ts #(java.sql.Timestamp/from (java.time.Instant/now))]

    (testing "A pending successor is not claimable while its predecessor is processing"
      (jobs/enqueue-job! db "analysis" {:lipas-id 42} {:run-at (now-ts)})
      (let [[a] (jobs/fetch-next-jobs db {:limit 10})
            successor (jobs/enqueue-job! db "analysis" {:lipas-id 42} {:run-at (now-ts)})]
        (is (some? a))
        (is (some? successor))
        (is (empty? (jobs/fetch-next-jobs db {:limit 10}))
            "Successor must wait until the processing job finishes")

        (testing "Unrelated sites are not blocked by the guard"
          (let [c (jobs/enqueue-job! db "analysis" {:lipas-id 43} {:run-at (now-ts)})
                claimed (jobs/fetch-next-jobs db {:limit 10})]
            (is (= [(:id c)] (map :id claimed)))))

        (testing "Once the predecessor finalizes, the successor becomes claimable"
          (jobs/mark-completed! db (:id a) (:attempts a))
          (let [claimed (jobs/fetch-next-jobs db {:limit 10})]
            (is (= [(:id successor)] (map :id claimed)))))))

    (testing "Jobs without dedup keys are never serialized"
      (test-utils/prune-db! db)
      (jobs/enqueue-job! db "email" {:to "a@example.com" :subject "1" :body "B"})
      (jobs/enqueue-job! db "email" {:to "b@example.com" :subject "2" :body "B"})
      (is (= 2 (count (jobs/fetch-next-jobs db {:limit 10})))
          "Both keyless jobs run concurrently"))))

(deftest concurrent-dedup-race-test
  (testing "Concurrent enqueues of the same key produce exactly one job and no errors"
    (let [db (test-db)
          n 20
          start-latch (java.util.concurrent.CountDownLatch. 1)
          results (atom [])
          threads (doall
                    (for [_ (range n)]
                      (Thread.
                        (fn []
                          (.await start-latch)
                          (let [result (try
                                         (jobs/enqueue-job! db "analysis" {:lipas-id 555})
                                         (catch Exception e e))]
                            (swap! results conj result))))))]
      (doseq [t threads] (.start t))
      (.countDown start-latch)
      (doseq [t threads] (.join t 10000))

      (is (= n (count @results)) "All threads finished")
      (is (not-any? #(instance? Exception %) @results)
          "Concurrent duplicate enqueues must not throw")
      (is (= 1 (count (remove nil? @results)))
          "Exactly one enqueue wins")
      (is (= 1 (job-count db))))))
