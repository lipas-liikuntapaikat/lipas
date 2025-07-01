(ns lipas.jobs.worker
  "Mixed-duration job worker with fast lane and general lane processing.
  
  Prevents head-of-line blocking by reserving threads for fast jobs while 
  allowing slow jobs to run in the general pool."
  (:require
   [lipas.jobs.core :as jobs]
   [lipas.jobs.dispatcher :as dispatcher]
   [lipas.jobs.monitoring :as monitoring]
   [lipas.jobs.patterns :as patterns]
   [taoensso.timbre :as log])
  (:import
   [java.util.concurrent Executors ThreadPoolExecutor TimeUnit]))

(defonce worker-state (atom {:running? false
                             :pools nil
                             :futures []}))

(def default-config
  {:fast-threads 2
   :general-threads 4
   :batch-size 5
   :poll-interval-ms 3000
   :fast-timeout-minutes 2
   :slow-timeout-minutes 20})

(defn create-worker-pools
  "Create separate thread pools for fast and general job processing."
  [{:keys [fast-threads general-threads]}]
  {:fast-pool (Executors/newFixedThreadPool fast-threads)
   :general-pool (Executors/newFixedThreadPool general-threads)})

(defn shutdown-pools!
  "Gracefully shutdown thread pools."
  [{:keys [fast-pool general-pool]}]
  (when fast-pool
    (.shutdown ^ThreadPoolExecutor fast-pool)
    (.awaitTermination ^ThreadPoolExecutor fast-pool 10 TimeUnit/SECONDS))
  (when general-pool
    (.shutdown ^ThreadPoolExecutor general-pool)
    (.awaitTermination ^ThreadPoolExecutor general-pool 30 TimeUnit/SECONDS))
  (log/info "Thread pools shut down"))

(defn process-job-batch
  "Process a batch of jobs using the appropriate thread pool with enhanced reliability."
  [system pools jobs lane-type]
  (let [pool (case lane-type
               :fast (:fast-pool pools)
               :general (:general-pool pools))
        {:keys [db]} system
        timeout-ms (case lane-type
                     :fast 120000 ; 2 minutes for fast jobs
                     :general 1200000)] ; 20 minutes for slow jobs

    (doseq [job jobs]
      (.submit pool
               ^Runnable
               (fn []
                 (let [correlation-id (:correlation_id job)
                       job-id (:id job)
                       job-type (:type job)
                       started-at (java.sql.Timestamp. (System/currentTimeMillis))]
                   (try
                     ;; Add correlation ID to logging context
                     (log/with-context {:correlation-id correlation-id
                                        :job-id job-id
                                        :job-type job-type}
                       (log/debug "Processing job" job)

                       ;; Execute with timeout protection
                       (patterns/with-timeout timeout-ms
                         (dispatcher/dispatch-job system job))

                       ;; Record success metrics
                       (monitoring/record-job-metric! db job-type "completed"
                                                      started-at (:created_at job) correlation-id))

                     (catch java.util.concurrent.TimeoutException ex
                       (log/error "Job timed out" {:job-id job-id :timeout-ms timeout-ms})
                       (jobs/fail-job! db job-id "Job execution timed out"
                                       {:current-attempt (:attempts job)
                                        :max-attempts (:max_attempts job)
                                        :correlation-id correlation-id})
                       (monitoring/record-job-metric! db job-type "failed"
                                                      started-at (:created_at job) correlation-id))

                     (catch Exception ex
                       (log/error ex "Job processing failed" {:job job})
                       (jobs/fail-job! db job-id (.getMessage ex)
                                       {:current-attempt (:attempts job)
                                        :max-attempts (:max_attempts job)
                                        :correlation-id correlation-id})
                       (monitoring/record-job-metric! db job-type "failed"
                                                      started-at (:created_at job) correlation-id)))))))))

(defn fetch-and-process-jobs
  "Fetch jobs and route them to appropriate thread pools.
   Only fetch jobs that can actually be processed based on available thread capacity."
  [system pools config]
  (let [{:keys [batch-size]} config
        {:keys [db]} system
        {:keys [fast-pool general-pool]} pools

        ;; Check available thread capacity first
        fast-active (.getActiveCount ^ThreadPoolExecutor fast-pool)
        fast-capacity (.getCorePoolSize ^ThreadPoolExecutor fast-pool)
        fast-available (max 0 (- fast-capacity fast-active))

        general-active (.getActiveCount ^ThreadPoolExecutor general-pool)
        general-capacity (.getCorePoolSize ^ThreadPoolExecutor general-pool)
        general-available (max 0 (- general-capacity general-active))

        ;; For fast jobs: batch up to available capacity
        fast-fetch-limit (min batch-size fast-available)
        ;; For slow jobs: fetch one at a time to allow thread reuse
        general-fetch-limit (min 1 general-available)]

    (log/debug "Thread capacity check"
               {:fast-active fast-active :fast-available fast-available
                :general-active general-active :general-available general-available
                :fast-fetch-limit fast-fetch-limit :general-fetch-limit general-fetch-limit})

    ;; Fetch jobs for fast lane only if we have capacity
    (let [fast-jobs (when (pos? fast-fetch-limit)
                      (let [fast-job-types (vec (:fast jobs/job-duration-types))]
                        (jobs/fetch-next-jobs db {:limit fast-fetch-limit
                                                  :job-types fast-job-types})))

          ;; Fetch jobs for general lane only if we have capacity (one at a time)
          general-jobs (when (pos? general-fetch-limit)
                         (jobs/fetch-next-jobs db {:limit general-fetch-limit}))]

      ;; Process fast jobs in fast lane
      (when (seq fast-jobs)
        (log/debug "Processing fast jobs" {:count (count fast-jobs)})
        (process-job-batch system pools fast-jobs :fast))

      ;; Process general jobs in general lane  
      (when (seq general-jobs)
        (log/debug "Processing general jobs" {:count (count general-jobs)})
        (process-job-batch system pools general-jobs :general))

      ;; Return total processed
      (+ (count (or fast-jobs [])) (count (or general-jobs []))))))

(defn worker-loop
  "Main worker loop that polls for jobs and processes them."
  [system pools config]
  (log/info "Starting worker loop" config)

  (while (:running? @worker-state)
    (try
      (let [processed-count (fetch-and-process-jobs system pools config)]
        (when (zero? processed-count)
          ;; No jobs available, sleep before next poll
          (Thread/sleep (:poll-interval-ms config))))

      (catch InterruptedException _
        (log/info "Worker loop interrupted"))
      (catch Exception ex
        (log/error ex "Error in worker loop")
        (Thread/sleep 5000)))) ; Brief pause on error

  (log/info "Worker loop stopped"))

(defn start-mixed-duration-worker!
  "Start the mixed-duration worker with fast and general lanes."
  [system config]
  (when (:running? @worker-state)
    (log/warn "Worker already running")
    nil)

  (let [merged-config (merge default-config config)
        pools (create-worker-pools merged-config)]

    (log/info "Starting mixed-duration worker" merged-config)

    (swap! worker-state assoc
           :running? true
           :pools pools)

    ;; Start worker loop in background thread
    (let [worker-future (future (worker-loop system pools merged-config))]
      (swap! worker-state update :futures conj worker-future))

    (log/info "Mixed-duration worker started successfully")))

(defn stop-mixed-duration-worker!
  "Stop the mixed-duration worker and cleanup resources."
  []
  (log/info "Stopping mixed-duration worker")

  (swap! worker-state assoc :running? false)

  ;; Wait for worker threads to finish
  (doseq [future (:futures @worker-state)]
    (try
      (deref future 10000 :timeout) ; Wait up to 10 seconds
      (catch Exception ex
        (log/warn ex "Error stopping worker thread"))))

  ;; Shutdown thread pools
  (when-let [pools (:pools @worker-state)]
    (shutdown-pools! pools))

  (swap! worker-state assoc
         :pools nil
         :futures [])

  (log/info "Mixed-duration worker stopped"))

(defn worker-stats
  "Get current worker statistics."
  []
  (let [state @worker-state
        pools (:pools state)]
    (merge
     {:running? (:running? state)
      :active-futures (count (:futures state))}
     (when pools
       {:fast-pool-size (.getCorePoolSize ^ThreadPoolExecutor (:fast-pool pools))
        :fast-active (.getActiveCount ^ThreadPoolExecutor (:fast-pool pools))
        :general-pool-size (.getCorePoolSize ^ThreadPoolExecutor (:general-pool pools))
        :general-active (.getActiveCount ^ThreadPoolExecutor (:general-pool pools))}))))
