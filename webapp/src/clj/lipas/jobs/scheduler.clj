(ns lipas.jobs.scheduler
  "Lightweight scheduler for periodic queue maintenance.

  Runs maintenance directly instead of routing it through the job queue:
  producing ~300 no-op 'check reminders' job rows per day through the
  queue machinery provided no value. Only real work (sending an email)
  becomes a job."
  (:require
    [lipas.jobs.core :as jobs]
    [lipas.jobs.monitoring :as monitoring]
    [lipas.reminders :as reminders]
    [taoensso.timbre :as log])
  (:import
    [java.util.concurrent Executors ScheduledExecutorService TimeUnit]))

(defonce scheduler-state (atom {:running? false
                                :executor nil
                                :scheduled-tasks []}))

(defn produce-reminder-emails!
  "Enqueue an email job for every overdue reminder. Returns the number of
  reminders processed."
  [db]
  (let [overdue (reminders/get-overdue db)]
    (doseq [reminder overdue]
      (jobs/enqueue-job! db "email" (reminders/->email db reminder))
      (reminders/mark-processed! db (:id reminder)))
    (when (seq overdue)
      (log/info "Produced reminder emails" {:count (count overdue)}))
    (count overdue)))

(def schedule-configs
  "Periodic maintenance tasks. Each :task-fn takes the db and is isolated:
  a failing run is logged and retried on the next tick."
  {:produce-reminder-emails
   {:interval-seconds 300
    :task-fn produce-reminder-emails!}

   :nightly-help-kb-sync
   ;; Code-derived KB docs (types, props) can change with any deploy, so
   ;; resync daily; content-hash diffing makes a no-change run cheap.
   {:interval-seconds 86400
    :task-fn (fn [db] (jobs/enqueue-job! db "help-kb-sync" {}))}

   :nightly-gdpr-removals
   ;; Anonymize users inactive for >5 years. The batch is idempotent and
   ;; capped (see core/process-gdpr-removals!), so the interval anchoring
   ;; to worker restarts is harmless.
   {:interval-seconds 86400
    :task-fn (fn [db] (jobs/enqueue-job! db "gdpr-removals" {}))}

   :recover-stuck-jobs
   {:interval-seconds 600
    :task-fn (fn [db] (jobs/recover-stuck-jobs! db jobs/stuck-job-timeout-minutes))}

   :queue-health-check
   {:interval-seconds 900
    :task-fn (fn [db] (monitoring/log-queue-health! db))}

   :cleanup-jobs
   {:interval-seconds 86400
    :task-fn (fn [db] (jobs/cleanup-jobs! db))}})

(defn- schedule-task
  [^ScheduledExecutorService executor db task-key {:keys [interval-seconds task-fn]}]
  (log/info "Scheduling task" {:key task-key :interval-seconds interval-seconds})
  (.scheduleAtFixedRate executor
                        ^Runnable
                        (fn []
                          (try
                            (task-fn db)
                            (catch Exception ex
                              (log/error ex "Scheduled task failed" {:key task-key}))))
                        0
                        (long interval-seconds)
                        TimeUnit/SECONDS))

(defn start-scheduler!
  "Start the scheduler with all configured periodic tasks."
  [db]
  (if (:running? @scheduler-state)
    (do (log/warn "Scheduler already running")
        nil)
    (let [executor (Executors/newScheduledThreadPool 1)]
      (log/info "Starting job scheduler")
      (swap! scheduler-state assoc
             :running? true
             :executor executor)
      (doseq [[task-key config] schedule-configs]
        (let [scheduled (schedule-task executor db task-key config)]
          (swap! scheduler-state update :scheduled-tasks conj scheduled)))
      (log/info "Job scheduler started" {:task-count (count schedule-configs)})
      {:status :running :scheduled-count (count schedule-configs)})))

(defn stop-scheduler!
  "Stop the scheduler and cancel all scheduled tasks."
  []
  (log/info "Stopping job scheduler")
  (swap! scheduler-state assoc :running? false)

  (doseq [task (:scheduled-tasks @scheduler-state)]
    (when task
      (.cancel task false)))

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
