(ns lipas.jobs.worker
  "Mixed-duration job worker with fast lane and general lane processing.
  
  Prevents head-of-line blocking by reserving threads for fast jobs while 
  allowing slow jobs to run in the general pool."
  (:require
   [lipas.jobs.core :as jobs]
   [lipas.jobs.dispatcher :as dispatcher]
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
  "Process a batch of jobs using the appropriate thread pool."
  [system pools jobs lane-type]
  (let [pool (case lane-type
               :fast (:fast-pool pools)
               :general (:general-pool pools))]

    (doseq [job jobs]
      (.submit pool
               ^Runnable
               (fn []
                 (try
                   (dispatcher/dispatch-job system job)
                   (catch Exception ex
                     (log/error ex "Unexpected error processing job" {:job job}))))))))

(defn fetch-and-process-jobs
  "Fetch jobs and route them to appropriate thread pools."
  [system pools config]
  (let [{:keys [batch-size]} config
        {:keys [db]} system

        ;; Fetch jobs for fast lane (only fast job types)
        fast-job-types (vec (:fast jobs/job-duration-types))
        fast-jobs (jobs/fetch-next-jobs db {:limit batch-size
                                            :job-types fast-job-types})

        ;; Fetch jobs for general lane (any remaining jobs)
        remaining-batch-size (max 0 (- batch-size (count fast-jobs)))
        general-jobs (when (pos? remaining-batch-size)
                       (jobs/fetch-next-jobs db {:limit remaining-batch-size}))]

    ;; Process fast jobs in fast lane
    (when (seq fast-jobs)
      (log/debug "Processing fast jobs" {:count (count fast-jobs)})
      (process-job-batch system pools fast-jobs :fast))

    ;; Process general jobs in general lane  
    (when (seq general-jobs)
      (log/debug "Processing general jobs" {:count (count general-jobs)})
      (process-job-batch system pools general-jobs :general))

    ;; Return total processed
    (+ (count fast-jobs) (count general-jobs))))

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
