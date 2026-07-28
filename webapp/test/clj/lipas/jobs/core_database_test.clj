(ns lipas.jobs.core-database-test
  "Integration tests for core queue operations against a real database."
  (:require
    [clojure.test :refer [deftest testing is use-fixtures]]
    [lipas.jobs.core :as jobs]
    [lipas.test-utils :as test-utils]
    [next.jdbc :as jdbc]))

;; Test system setup using shared fixture
(defonce test-system (atom nil))

(let [{:keys [once each]} (test-utils/db-only-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn test-db [] (:lipas/db @test-system))

(defn now-ts []
  (java.sql.Timestamp/from (java.time.Instant/now)))

(defn dead-letter-count [db]
  (count (jobs/get-dead-letter-jobs db {})))

;; =============================================================================
;; Enqueueing
;; =============================================================================

(deftest job-enqueueing-test
  (let [db (test-db)]

    (testing "Basic enqueueing with registry defaults"
      (let [{:keys [id]} (jobs/enqueue-job! db "email" {:to "test@example.com"
                                                        :subject "Test"
                                                        :body "Test email"})]
        (is (pos-int? id))
        (let [job (test-utils/get-job-by-id db id)]
          (is (= "email" (:jobs/type job)))
          (is (= 95 (:jobs/priority job)) "Default priority comes from registry")
          (is (= 3 (:jobs/max_attempts job)))
          (is (= "pending" (:jobs/status job)))
          (is (nil? (:jobs/dedup_key job)) "Email jobs have no dedup key"))))

    (testing "Registry-driven dedup key and debounce delay"
      (let [{:keys [id]} (jobs/enqueue-job! db "analysis" {:lipas-id 123})
            job (test-utils/get-job-by-id db id)]
        (is (= "analysis:123" (:jobs/dedup_key job)))
        (is (= 80 (:jobs/priority job)))
        (is (.after (:jobs/run_at job) (now-ts))
            "Debounce: analysis becomes runnable only after the delay")))

    (testing "Opts override registry defaults"
      (let [{:keys [id]} (jobs/enqueue-job! db "analysis" {:lipas-id 456}
                                            {:priority 50
                                             :max-attempts 5
                                             :run-at (now-ts)
                                             :created-by "test@lipas.fi"})
            job (test-utils/get-job-by-id db id)]
        (is (= 50 (:jobs/priority job)))
        (is (= 5 (:jobs/max_attempts job)))
        (is (= "test@lipas.fi" (:jobs/created_by job)))))

    (testing "Unknown job type throws"
      (is (thrown-with-msg? Exception #"Unknown job type"
                            (jobs/enqueue-job! db "invalid-type" {}))))

    (testing "Invalid payload throws and creates no job"
      (test-utils/prune-db! db)
      (is (thrown-with-msg? Exception #"Invalid job payload"
                            (jobs/enqueue-job! db "email" {:no-to-field true})))
      (is (empty? (test-utils/get-all-jobs db))))))

;; =============================================================================
;; Fetching
;; =============================================================================

(deftest job-fetching-test
  (let [db (test-db)]

    (testing "Jobs are fetched in priority order"
      (jobs/enqueue-job! db "email" {:to "high@example.com" :subject "Hi" :body "B"} {:priority 95})
      (jobs/enqueue-job! db "analysis" {:lipas-id 123} {:priority 50 :run-at (now-ts)})
      (jobs/enqueue-job! db "webhook" {:lipas-ids [1]} {:priority 30})

      (let [fetched (jobs/fetch-next-jobs db {:limit 2})]
        (is (= 2 (count fetched)))
        (is (= 95 (:priority (first fetched))))
        (is (>= (:priority (first fetched)) (:priority (second fetched))))))

    (testing "Job type filtering"
      (test-utils/prune-db! db)
      (jobs/enqueue-job! db "email" {:to "a@example.com" :subject "1" :body "B"})
      (jobs/enqueue-job! db "email" {:to "b@example.com" :subject "2" :body "B"})
      (jobs/enqueue-job! db "analysis" {:lipas-id 456} {:run-at (now-ts)})

      (let [email-jobs (jobs/fetch-next-jobs db {:job-types ["email"] :limit 5})]
        (is (= 2 (count email-jobs)))
        (is (every? #(= "email" (:type %)) email-jobs))))

    (testing "Debounced jobs are not fetched before run_at"
      (test-utils/prune-db! db)
      (jobs/enqueue-job! db "analysis" {:lipas-id 789}) ; debounced 30s
      (is (empty? (jobs/fetch-next-jobs db {:limit 5}))
          "Job inside its debounce window must not be fetched"))

    (testing "Fetched jobs are marked processing with incremented attempts"
      (test-utils/prune-db! db)
      (jobs/enqueue-job! db "email" {:to "t@example.com" :subject "T" :body "B"})
      (let [[job] (jobs/fetch-next-jobs db {:limit 1})]
        (is (= 1 (:attempts job)))
        (is (= "processing" (:jobs/status (test-utils/get-job-by-id db (:id job)))))))))

(deftest concurrent-job-fetching-test
  (testing "Concurrent fetches never hand out the same job (SKIP LOCKED)"
    (let [db (test-db)]
      (dotimes [i 5]
        (jobs/enqueue-job! db "email" {:to (str "t" i "@example.com") :subject (str i) :body "B"}))

      (let [batch1 (jobs/fetch-next-jobs db {:limit 2})
            batch2 (jobs/fetch-next-jobs db {:limit 2})]
        (is (= 2 (count batch1)))
        (is (= 2 (count batch2)))
        (is (empty? (clojure.set/intersection (set (map :id batch1))
                                              (set (map :id batch2)))))))))

;; =============================================================================
;; Status transitions
;; =============================================================================

(deftest job-status-transitions-test
  (let [db (test-db)]

    (testing "Completion"
      (let [{:keys [id]} (jobs/enqueue-job! db "email" {:to "t@example.com" :subject "T" :body "B"})]
        (jobs/fetch-next-jobs db {:limit 1})
        (jobs/mark-completed! db id)
        (let [job (test-utils/get-job-by-id db id)]
          (is (= "completed" (:jobs/status job)))
          (is (inst? (:jobs/completed_at job)))
          (is (nil? (:jobs/error_message job))))))

    (testing "Failure with attempts left schedules a pending retry"
      (test-utils/prune-db! db)
      (let [{:keys [id]} (jobs/enqueue-job! db "email" {:to "r@example.com" :subject "R" :body "B"}
                                            {:max-attempts 3})
            [job] (jobs/fetch-next-jobs db {:limit 1})]
        (jobs/fail-job! db id "Test error"
                        {:current-attempt (:attempts job)
                         :max-attempts (:max_attempts job)})
        (let [failed (test-utils/get-job-by-id db id)]
          (is (= "pending" (:jobs/status failed)))
          (is (= 1 (:jobs/attempts failed)))
          (is (= "Test error" (:jobs/error_message failed)))
          (is (.after (:jobs/run_at failed) (now-ts))
              "Retry is scheduled in the future (backoff)"))))

    (testing "Failure with attempts exhausted moves the job to the dead letter queue"
      (test-utils/prune-db! db)
      (let [{:keys [id]} (jobs/enqueue-job! db "webhook" {:lipas-ids [123]} {:max-attempts 1})
            [job] (jobs/fetch-next-jobs db {:limit 1})]
        (jobs/fail-job! db id "Final failure"
                        {:current-attempt (:attempts job)
                         :max-attempts (:max_attempts job)})

        (is (nil? (test-utils/get-job-by-id db id))
            "Dead jobs are removed from the jobs table")

        (let [[dlj] (jobs/get-dead-letter-jobs db {:acknowledged false})]
          (is (some? dlj))
          (is (= "Final failure" (:error-message dlj)))
          (is (= "webhook" (get-in dlj [:original-job :type])))
          (is (= {:lipas-ids [123]} (get-in dlj [:original-job :payload]))))))))

;; =============================================================================
;; Retry vs. successor (pending-only dedup collision)
;; =============================================================================

(deftest retry-superseded-by-successor-test
  (let [db (test-db)]

    (testing "A failing job whose successor is already pending is dropped, not retried"
      (let [{a-id :id} (jobs/enqueue-job! db "analysis" {:lipas-id 100} {:run-at (now-ts)})
            [a] (jobs/fetch-next-jobs db {:limit 1})
            b (jobs/enqueue-job! db "analysis" {:lipas-id 100} {:run-at (now-ts)})]
        (is (= a-id (:id a)))
        (is (some? b) "Successor is enqueued while the predecessor is processing")

        (let [n (jobs/fail-job! db a-id "boom"
                                {:current-attempt (:attempts a)
                                 :max-attempts (:max_attempts a)
                                 :attempts (:attempts a)})]
          (is (zero? n)
              "Retry goes through the dedup gate and is dropped as superseded"))

        (is (nil? (test-utils/get-job-by-id db a-id))
            "The superseded job is removed")
        (is (= "pending" (:jobs/status (test-utils/get-job-by-id db (:id b))))
            "The successor remains the single pending job for the site")
        (is (= 1 (count (test-utils/get-all-jobs db))))))

    (testing "A failing job with no successor is retried with attempts preserved"
      (test-utils/prune-db! db)
      (let [{id :id} (jobs/enqueue-job! db "analysis" {:lipas-id 200} {:run-at (now-ts)})
            [a] (jobs/fetch-next-jobs db {:limit 1})
            n (jobs/fail-job! db id "boom"
                              {:current-attempt (:attempts a)
                               :max-attempts (:max_attempts a)
                               :attempts (:attempts a)})]
        (is (pos? n))
        (let [after (test-utils/get-job-by-id db id)]
          (is (= "pending" (:jobs/status after)))
          (is (= 1 (:jobs/attempts after)))
          (is (= "analysis:200" (:jobs/dedup_key after))
              "The retry keeps its dedup key and stays part of the dedup universe"))))))

;; =============================================================================
;; Fenced finalization
;; =============================================================================

(deftest fenced-finalization-test
  (let [db (test-db)]
    (testing "Finalizers with a stale attempts fingerprint are no-ops"
      (let [{id :id} (jobs/enqueue-job! db "email" {:to "f@example.com" :subject "F" :body "B"})
            [job] (jobs/fetch-next-jobs db {:limit 1})]
        (is (= 1 (:attempts job)))

        (is (zero? (jobs/mark-completed! db id 999))
            "Stale completion updates zero rows")
        (is (zero? (jobs/fail-job! db id "stale" {:current-attempt 1
                                                  :max-attempts 3
                                                  :attempts 999}))
            "Stale retry updates zero rows")
        (is (zero? (jobs/move-to-dead-letter! db id "stale" 999))
            "Stale dead-lettering updates zero rows")

        (is (= "processing" (:jobs/status (test-utils/get-job-by-id db id)))
            "The claimed execution still owns the job")

        (is (pos? (jobs/mark-completed! db id (:attempts job)))
            "The rightful fingerprint finalizes normally")
        (is (= "completed" (:jobs/status (test-utils/get-job-by-id db id))))))))

;; =============================================================================
;; Stuck job recovery
;; =============================================================================

(deftest stuck-job-recovery-test
  (let [db (test-db)]

    (testing "Stuck job with attempts left goes back to pending"
      (let [{:keys [id]} (jobs/enqueue-job! db "email" {:to "s@example.com" :subject "S" :body "B"})]
        (jobs/fetch-next-jobs db {:limit 1})
        ;; Simulate a crash: job stays processing with an old started_at
        (jdbc/execute! db ["UPDATE jobs SET started_at = now() - interval '3 hours' WHERE id = ?" id])

        (let [result (jobs/recover-stuck-jobs! db 90)]
          (is (= 1 (:recovered result)))
          (is (= 0 (:dead-lettered result))))

        (let [job (test-utils/get-job-by-id db id)]
          (is (= "pending" (:jobs/status job)))
          (is (some? (:jobs/last_error job))))))

    (testing "Stuck job with attempts exhausted is dead-lettered"
      (test-utils/prune-db! db)
      (let [{:keys [id]} (jobs/enqueue-job! db "email" {:to "d@example.com" :subject "D" :body "B"}
                                            {:max-attempts 1})]
        (jobs/fetch-next-jobs db {:limit 1})
        (jdbc/execute! db ["UPDATE jobs SET started_at = now() - interval '3 hours' WHERE id = ?" id])

        (let [result (jobs/recover-stuck-jobs! db 90)]
          (is (= 0 (:recovered result)))
          (is (= 1 (:dead-lettered result))))

        (is (nil? (test-utils/get-job-by-id db id)))
        (is (= 1 (dead-letter-count db)))))

    (testing "Recently started jobs are left alone"
      (test-utils/prune-db! db)
      (let [{:keys [id]} (jobs/enqueue-job! db "email" {:to "ok@example.com" :subject "O" :body "B"})]
        (jobs/fetch-next-jobs db {:limit 1})
        (let [result (jobs/recover-stuck-jobs! db 90)]
          (is (= 0 (:recovered result)))
          (is (= 0 (:dead-lettered result))))
        (is (= "processing" (:jobs/status (test-utils/get-job-by-id db id))))))))

(deftest stuck-recovery-collision-test
  (let [db (test-db)
        backdate! (fn [id] (jdbc/execute! db ["UPDATE jobs SET started_at = now() - interval '3 hours' WHERE id = ?" id]))]

    (testing "A stuck job superseded by a pending duplicate does not poison the recovery batch"
      ;; A: stuck processing, with pending successor B carrying the same dedup key
      (let [{a-id :id} (jobs/enqueue-job! db "analysis" {:lipas-id 1} {:run-at (now-ts)})
            [a] (jobs/fetch-next-jobs db {:limit 1})
            _ (is (= a-id (:id a)))
            _ (backdate! a-id)
            b (jobs/enqueue-job! db "analysis" {:lipas-id 1} {:run-at (now-ts)})

            ;; C: unrelated stuck job with attempts left (B is blocked by the
            ;; sequential guard while A is processing, so the fetch claims C)
            {c-id :id} (jobs/enqueue-job! db "analysis" {:lipas-id 2} {:run-at (now-ts)})
            [c] (jobs/fetch-next-jobs db {:limit 1})
            _ (is (= c-id (:id c)))
            _ (backdate! c-id)

            ;; D: stuck job with attempts exhausted
            {d-id :id} (jobs/enqueue-job! db "analysis" {:lipas-id 3} {:run-at (now-ts) :max-attempts 1})
            [d] (jobs/fetch-next-jobs db {:limit 1})
            _ (is (= d-id (:id d)))
            _ (backdate! d-id)

            result (jobs/recover-stuck-jobs! db 90)]

        (is (= 1 (:recovered result)) "Only C is recovered")
        (is (= 1 (:dead-lettered result)) "Only D is dead-lettered")

        (is (nil? (test-utils/get-job-by-id db a-id))
            "A is dropped: its pending successor already covers the work")
        (is (= "pending" (:jobs/status (test-utils/get-job-by-id db (:id b))))
            "B is untouched")
        (is (= "pending" (:jobs/status (test-utils/get-job-by-id db c-id)))
            "C went back to pending despite A's collision")
        (is (nil? (test-utils/get-job-by-id db d-id)))
        (is (= 1 (dead-letter-count db)))))))

;; =============================================================================
;; Retention
;; =============================================================================

(deftest cleanup-retention-test
  (let [db (test-db)]

    (testing "Old completed jobs are deleted, recent kept"
      (let [{old-id :id} (jobs/enqueue-job! db "email" {:to "old@example.com" :subject "O" :body "B"})
            {recent-id :id} (jobs/enqueue-job! db "email" {:to "new@example.com" :subject "N" :body "B"})]
        (jobs/mark-completed! db old-id)
        (jobs/mark-completed! db recent-id)
        (jdbc/execute! db ["UPDATE jobs SET completed_at = now() - interval '40 days' WHERE id = ?" old-id])

        (let [result (jobs/cleanup-jobs! db {:completed-days 30 :dead-letter-days 90})]
          (is (= 1 (:completed-deleted result))))

        (is (nil? (test-utils/get-job-by-id db old-id)))
        (is (some? (test-utils/get-job-by-id db recent-id)))))

    (testing "Old acknowledged dead letters are purged, unacknowledged kept"
      (test-utils/prune-db! db)
      (let [old-acked (test-utils/create-test-dead-letter-job! db {:acknowledged true})
            _ (jdbc/execute! db ["UPDATE dead_letter_jobs SET acknowledged_at = now() - interval '100 days' WHERE id = ?"
                                 (:id old-acked)])
            recent-acked (test-utils/create-test-dead-letter-job! db {:acknowledged true})
            unacked (test-utils/create-test-dead-letter-job! db)
            _ (jdbc/execute! db ["UPDATE dead_letter_jobs SET died_at = now() - interval '200 days' WHERE id = ?"
                                 (:id unacked)])
            result (jobs/cleanup-jobs! db {:completed-days 30 :dead-letter-days 90})]
        (is (= 1 (:dead-letter-deleted result)))
        (let [remaining (set (map :id (jobs/get-dead-letter-jobs db {})))]
          (is (contains? remaining (:id recent-acked)))
          (is (contains? remaining (:id unacked))
              "Unacknowledged dead letters are never purged, however old"))))))

;; =============================================================================
;; Stats and health
;; =============================================================================

(deftest queue-stats-test
  (testing "Queue statistics reporting"
    (let [db (test-db)]
      (jobs/enqueue-job! db "email" {:to "pending@example.com" :subject "P" :body "B"})
      (let [{completed-id :id} (jobs/enqueue-job! db "email" {:to "done@example.com" :subject "D" :body "B"})]
        (jobs/fetch-next-jobs db {:limit 1})
        (jobs/mark-completed! db completed-id)

        (let [stats (jobs/get-queue-stats db)]
          (is (map? stats))
          (is (contains? stats :total)))))))

(deftest queue-health-test
  (testing "Queue health metrics"
    (let [db (test-db)]
      ;; One retrying job
      (let [{retry-id :id} (jobs/enqueue-job! db "email" {:to "r@example.com" :subject "R" :body "B"})]
        (jobs/fetch-next-jobs db {:limit 1})
        (jobs/fail-job! db retry-id "boom" {:current-attempt 1 :max-attempts 3}))

      ;; One fresh pending job
      (jobs/enqueue-job! db "email" {:to "p@example.com" :subject "P" :body "B"})

      ;; One processing job (higher priority so the fetch picks it)
      (let [{processing-id :id} (jobs/enqueue-job! db "email" {:to "w@example.com" :subject "W" :body "B"}
                                                   {:priority 100})]
        (jobs/fetch-next-jobs db {:limit 1})
        (is (= "processing" (:jobs/status (test-utils/get-job-by-id db processing-id)))))

      ;; One unacknowledged dead letter
      (test-utils/create-test-dead-letter-job! db)

      (let [health (jobs/get-queue-health db)]
        (is (= 2 (:pending_count health)) "One fresh + one retrying")
        (is (= 1 (:retrying_count health)))
        (is (= 1 (:processing_count health)))
        (is (= 1 (:dead_count health)) "Dead count = unacknowledged dead letters")))))
