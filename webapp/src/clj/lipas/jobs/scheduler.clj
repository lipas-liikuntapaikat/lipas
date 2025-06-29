(ns lipas.jobs.scheduler
  "Lightweight scheduler that produces jobs at regular intervals. "
  (:require
   [lipas.jobs.core :as jobs]
   [taoensso.timbre :as log])
  (:import
   [java.util.concurrent Executors ScheduledExecutorService TimeUnit]))

(defonce scheduler-state (atom {:running? false
                                :executor nil
                                :scheduled-tasks []}))

(def schedule-configs
  "Configuration for periodic job producers."
  {:check-overdue-reminders
   {:job-type "produce-reminders"
    :payload {}
    :interval-seconds 300 ; Every 5 minutes
    :priority 90}

   :cleanup-old-jobs
   {:job-type "cleanup-jobs"
    :payload {:days-old 30}
    :interval-seconds 86400 ; Daily
    :priority 30}})

(defn schedule-job-producer
  "Schedule a recurring job producer."
  [^ScheduledExecutorService executor db job-key config]
  (let [{:keys [job-type payload interval-seconds priority]} config
        task-fn (fn []
                  (try
                    (log/debug "Producing scheduled job" {:type job-type})
                    (jobs/enqueue-job! db job-type payload {:priority priority})
                    (catch Exception ex
                      (log/error ex "Error producing scheduled job" {:type job-type}))))]

    (log/info "Scheduling job producer" {:key job-key :config config})

    (.scheduleAtFixedRate executor
                          ^Runnable task-fn
                          0 ; initial delay
                          interval-seconds ; period
                          TimeUnit/SECONDS)))

(defn start-scheduler!
  "Start the job scheduler with configured periodic tasks."
  [db]
  (when (:running? @scheduler-state)
    (log/warn "Scheduler already running")
    nil)

  (log/info "Starting job scheduler")

  (let [executor (Executors/newScheduledThreadPool 2)]

    (swap! scheduler-state assoc
           :running? true
           :executor executor)

    ;; Schedule all configured job producers
    (doseq [[job-key config] schedule-configs]
      (let [scheduled-future (schedule-job-producer executor db job-key config)]
        (swap! scheduler-state update :scheduled-tasks conj scheduled-future)))

    (log/info "Job scheduler started with" (count schedule-configs) "scheduled tasks")

    {:status :running :scheduled-count (count schedule-configs)}))

(defn stop-scheduler!
  "Stop the job scheduler and cancel all scheduled tasks."
  []
  (log/info "Stopping job scheduler")

  (swap! scheduler-state assoc :running? false)

  ;; Cancel all scheduled tasks
  (doseq [task (:scheduled-tasks @scheduler-state)]
    (when task
      (.cancel task false)))

  ;; Shutdown executor
  (when-let [executor (:executor @scheduler-state)]
    (.shutdown ^ScheduledExecutorService executor)
    (when-not (.awaitTermination ^ScheduledExecutorService executor 10 TimeUnit/SECONDS)
      (.shutdownNow ^ScheduledExecutorService executor)
      (log/warn "Scheduler executor did not terminate gracefully")))

  (swap! scheduler-state assoc
         :executor nil
         :scheduled-tasks [])

  (log/info "Job scheduler stopped"))

(defn scheduler-stats
  "Get current scheduler statistics."
  []
  (let [state @scheduler-state]
    {:running? (:running? state)
     :scheduled-tasks-count (count (:scheduled-tasks state))
     :configs-count (count schedule-configs)}))

(defn add-scheduled-job!
  "Dynamically add a new scheduled job producer (for testing/admin use)."
  [db job-key config]
  (when-not (:running? @scheduler-state)
    (throw (ex-info "Scheduler not running" {})))

  (when-let [executor (:executor @scheduler-state)]
    (let [scheduled-future (schedule-job-producer executor db job-key config)]
      (swap! scheduler-state update :scheduled-tasks conj scheduled-future)
      (log/info "Added dynamic scheduled job" {:key job-key :config config}))))
