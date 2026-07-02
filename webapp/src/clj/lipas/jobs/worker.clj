(ns lipas.jobs.worker
  "Mixed-duration job worker with fast lane and general lane processing.

  Prevents head-of-line blocking by reserving threads for fast jobs while
  slow jobs run in the general pool.

  Jobs execute directly on the pool threads. A hard per-job-type timeout
  (from the registry) is enforced by a watchdog that interrupts the pool
  thread; the job's status update runs only after the interrupt flag has
  been cleared, so a timed-out job is marked failed exactly once and no
  orphaned execution keeps running in the background."
  (:require
   [clojure.set :as set]
   [lipas.jobs.core :as jobs]
   [lipas.jobs.dispatcher :as dispatcher]
   [lipas.jobs.registry :as registry]
   [taoensso.timbre :as log])
  (:import
   [java.util.concurrent Executors ScheduledExecutorService ThreadPoolExecutor TimeUnit]))

(defonce worker-state (atom {:running? false
                             :pools nil
                             :watchdog nil
                             :futures []}))

(def default-config
  {:fast-threads 2
   :general-threads 2
   :batch-size 10
   :poll-interval-ms 3000})

(defn- assert-handlers-registered!
  "Fail fast at startup when a registered job type has no dispatcher method."
  []
  (let [handled (set (keys (methods dispatcher/handle-job)))
        registered (set (keys registry/job-types))
        missing (set/difference registered handled)]
    (when (seq missing)
      (throw (ex-info "Job types registered without a dispatcher handler"
                      {:missing missing})))))

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

(defn execute-job!
  "Run a single job on the current thread with a hard timeout.

  The watchdog interrupts this thread when the timeout elapses. The lock
  guarantees the interrupt is delivered only while the job body is running:
  after the body finishes (normally or not) the interrupt flag is cleared
  before any DB status update, so retries can never race a still-running
  execution of the same job."
  [{:keys [db] :as system} ^ScheduledExecutorService watchdog job]
  (let [job-id (:id job)
        job-type (:type job)
        timeout-ms (registry/timeout-ms job-type)
        thread (Thread/currentThread)
        lock (Object.)
        state (atom :running)
        watchdog-task (.schedule watchdog
                                 ^Runnable
                                 (fn []
                                   (locking lock
                                     (when (compare-and-set! state :running :timed-out)
                                       (.interrupt thread))))
                                 (long timeout-ms)
                                 TimeUnit/MILLISECONDS)
        started-ms (System/currentTimeMillis)
        result (try
                 (log/with-context {:job-id job-id :job-type job-type}
                   (log/debug "Processing job" {:type job-type :attempt (:attempts job)})
                   (dispatcher/handle-job system job))
                 ::ok
                 (catch Throwable t t)
                 (finally
                   (locking lock
                     (compare-and-set! state :running :done))
                   (.cancel watchdog-task false)
                   ;; Clear the interrupt flag in case the watchdog fired
                   (Thread/interrupted)))
        duration-ms (- (System/currentTimeMillis) started-ms)]
    (cond
      (= ::ok result)
      (do
        (jobs/mark-completed! db job-id)
        (log/info "Job completed" {:id job-id :type job-type :duration-ms duration-ms}))

      (= :timed-out @state)
      (do
        (log/error "Job timed out" {:id job-id
                                    :type job-type
                                    :timeout-ms timeout-ms
                                    :duration-ms duration-ms})
        (jobs/fail-job! db job-id
                        (format "Job execution timed out after %d minutes"
                                (long (/ timeout-ms 60000)))
                        {:current-attempt (:attempts job)
                         :max-attempts (:max_attempts job)}))

      :else
      (let [^Throwable ex result]
        (log/error ex "Job failed" {:id job-id :type job-type :duration-ms duration-ms})
        (jobs/fail-job! db job-id
                        (or (.getMessage ex) (str (class ex)))
                        {:current-attempt (:attempts job)
                         :max-attempts (:max_attempts job)})))))

(defn process-job-batch
  "Submit a batch of jobs to the appropriate thread pool."
  [system pools watchdog jobs lane-type]
  (let [pool (case lane-type
               :fast (:fast-pool pools)
               :general (:general-pool pools))]
    (doseq [job jobs]
      (.submit ^ThreadPoolExecutor pool
               ^Runnable
               (fn []
                 (try
                   (execute-job! system watchdog job)
                   (catch Throwable t
                     ;; execute-job! handles job failures itself; this only
                     ;; triggers if the status update itself blew up
                     (log/error t "Unexpected error finalizing job" {:id (:id job)}))))))))

(defn fetch-and-process-jobs
  "Fetch jobs and route them to thread pools. Only fetches as many jobs as
  there are free threads, so fetched jobs start executing immediately."
  [system pools watchdog config]
  (let [{:keys [batch-size]} config
        {:keys [db]} system
        {:keys [fast-pool general-pool]} pools

        fast-active (.getActiveCount ^ThreadPoolExecutor fast-pool)
        fast-capacity (.getCorePoolSize ^ThreadPoolExecutor fast-pool)
        fast-available (max 0 (- fast-capacity fast-active))

        general-active (.getActiveCount ^ThreadPoolExecutor general-pool)
        general-capacity (.getCorePoolSize ^ThreadPoolExecutor general-pool)
        general-available (max 0 (- general-capacity general-active))

        fast-fetch-limit (min batch-size fast-available)
        general-fetch-limit (min batch-size general-available)

        fast-jobs (when (pos? fast-fetch-limit)
                    (jobs/fetch-next-jobs db {:limit fast-fetch-limit
                                              :job-types (vec registry/fast-job-types)}))
        general-jobs (when (pos? general-fetch-limit)
                       (jobs/fetch-next-jobs db {:limit general-fetch-limit}))]

    (when (seq fast-jobs)
      (process-job-batch system pools watchdog fast-jobs :fast))
    (when (seq general-jobs)
      (process-job-batch system pools watchdog general-jobs :general))

    (+ (count fast-jobs) (count general-jobs))))

(defn worker-loop
  "Main worker loop that polls for jobs and processes them."
  [system pools watchdog config]
  (log/info "Starting worker loop" config)
  (while (:running? @worker-state)
    (try
      (let [processed-count (fetch-and-process-jobs system pools watchdog config)]
        (when (zero? processed-count)
          (Thread/sleep (long (:poll-interval-ms config)))))
      (catch InterruptedException _
        (log/info "Worker loop interrupted"))
      (catch Exception ex
        (log/error ex "Error in worker loop")
        (Thread/sleep 5000))))
  (log/info "Worker loop stopped"))

(defn start-mixed-duration-worker!
  "Start the mixed-duration worker with fast and general lanes."
  [system config]
  (if (:running? @worker-state)
    (log/warn "Worker already running")
    (let [merged-config (merge default-config config)
          pools (create-worker-pools merged-config)
          watchdog (Executors/newSingleThreadScheduledExecutor)
          {:keys [db]} system]

      (assert-handlers-registered!)
      (log/info "Starting mixed-duration worker" merged-config)

      ;; Recover any jobs left in processing state by a previous crash
      (try
        (jobs/recover-stuck-jobs! db jobs/stuck-job-timeout-minutes)
        (catch Exception e
          (log/error e "Failed to recover stuck jobs during startup")))

      (swap! worker-state assoc
             :running? true
             :pools pools
             :watchdog watchdog)

      (let [worker-future (future (worker-loop system pools watchdog merged-config))]
        (swap! worker-state update :futures conj worker-future))

      (log/info "Mixed-duration worker started successfully"))))

(defn stop-mixed-duration-worker!
  "Stop the mixed-duration worker and clean up resources."
  []
  (log/info "Stopping mixed-duration worker")
  (swap! worker-state assoc :running? false)

  (doseq [f (:futures @worker-state)]
    (try
      (deref f 10000 :timeout)
      (catch Exception ex
        (log/warn ex "Error stopping worker thread"))))

  (when-let [pools (:pools @worker-state)]
    (shutdown-pools! pools))

  (when-let [watchdog (:watchdog @worker-state)]
    (.shutdownNow ^ScheduledExecutorService watchdog))

  (swap! worker-state assoc
         :pools nil
         :watchdog nil
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
