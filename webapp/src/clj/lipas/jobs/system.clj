(ns lipas.jobs.system
  "Integrant system configuration for the unified job worker.
  
  Reuses existing system components from the main webapp system
  and adds worker-specific components."
  (:require
   [integrant.core :as ig]
   [lipas.backend.config :as config]
   [lipas.jobs.core :as jobs]
   [lipas.jobs.scheduler :as scheduler]
   [lipas.jobs.worker :as worker]
   [taoensso.timbre :as log]))

;; Worker-specific Integrant components only
;; (db, search, emailer components are reused from main system)

(defmethod ig/init-key :lipas.jobs/scheduler
  [_ {:keys [db]}]
  (log/info "Starting job scheduler")
  (scheduler/start-scheduler! db))

(defmethod ig/halt-key! :lipas.jobs/scheduler
  [_ _]
  (log/info "Stopping job scheduler")
  (scheduler/stop-scheduler!))

(defmethod ig/init-key :lipas.jobs/worker
  [_ {:keys [db search emailer config]}]
  (log/info "Starting unified job worker")

  ;; Reset any jobs stuck in processing state from previous crashes
  (log/info "Checking for stuck jobs from previous worker crashes")
  (let [timeout-minutes (get config :stuck-job-timeout-minutes 60)]
    (jobs/reset-stuck-jobs! db timeout-minutes))

  ;; Log memory status at startup
  (let [runtime (Runtime/getRuntime)
        max-memory (/ (.maxMemory runtime) 1024 1024)
        total-memory (/ (.totalMemory runtime) 1024 1024)
        free-memory (/ (.freeMemory runtime) 1024 1024)]
    (log/info "Worker memory status at startup"
              {:max-memory-mb max-memory
               :total-memory-mb total-memory
               :free-memory-mb free-memory
               :used-memory-mb (- total-memory free-memory)}))

  (worker/start-mixed-duration-worker!
   {:db db :search search :emailer emailer}
   config))

(defmethod ig/halt-key! :lipas.jobs/worker
  [_ _]
  (log/info "Stopping unified job worker")
  (worker/stop-mixed-duration-worker!))

(defmethod ig/init-key :lipas.jobs/health-monitor
  [_ {:keys [db config]}]
  (log/info "Starting job health monitor")
  (require '[lipas.jobs.health :as health])
  ((resolve 'lipas.jobs.health/start-health-monitor!) db config))

(defmethod ig/halt-key! :lipas.jobs/health-monitor
  [_ _]
  (log/info "Stopping job health monitor")
  (require '[lipas.jobs.health :as health])
  ((resolve 'lipas.jobs.health/stop-health-monitor!)))

;; Configuration can be overridden from environment variables
(defn get-worker-config
  "Build worker configuration with environment variable overrides."
  []
  (let [env-config (fn [key default]
                     (if-let [env-val (System/getenv (str "WORKER_"
                                                          (-> key name
                                                              (.replace "-" "_")
                                                              (.toUpperCase))))]
                       (try
                         (Long/parseLong env-val)
                         (catch Exception _
                           (log/warn "Invalid env var value, using default" {:key key :value env-val})
                           default))
                       default))]
    {:fast-threads (env-config :fast-threads 3)
     :general-threads (env-config :general-threads 5)
     :batch-size (env-config :batch-size 10)
     :poll-interval-ms (env-config :poll-interval-ms 3000)
     :fast-timeout-minutes (env-config :fast-timeout-minutes 2)
     :slow-timeout-minutes (env-config :slow-timeout-minutes 20)
     :stuck-job-timeout-minutes (env-config :stuck-job-timeout-minutes 60)

     ;; New configuration for memory management
     :memory-check-interval-ms (env-config :memory-check-interval-ms 60000)
     :memory-threshold-percent (env-config :memory-threshold-percent 85)

     ;; Per-job-type timeout overrides (in minutes)
     :job-type-timeouts {"analysis" (env-config :analysis-timeout-minutes 30)
                         "elevation" (env-config :elevation-timeout-minutes 10)
                         "email" (env-config :email-timeout-minutes 1)
                         "webhook" (env-config :webhook-timeout-minutes 2)
                         "produce-reminders" (env-config :reminders-timeout-minutes 5)
                         "cleanup-jobs" (env-config :cleanup-timeout-minutes 5)}}))

;; Worker system configuration that reuses main system components
(def worker-system-config
  (merge
    ;; Reuse existing system components (this ensures no multimethod conflicts)
   (select-keys config/system-config
                [:lipas/db :lipas/search :lipas/emailer])

    ;; Add worker-specific components
   {:lipas.jobs/scheduler
    {:db (ig/ref :lipas/db)}

    :lipas.jobs/worker
    {:db (ig/ref :lipas/db)
     :search (ig/ref :lipas/search)
     :emailer (ig/ref :lipas/emailer)
     :config (get-worker-config)}

    :lipas.jobs/health-monitor
    {:db (ig/ref :lipas/db)
     :config (get-worker-config)}}))

(defn start-worker-system!
  "Start the worker system using the main system configuration."
  []
  (log/info "Starting LIPAS worker system")
  (ig/init worker-system-config))

(defn stop-worker-system!
  "Stop the worker system."
  [system]
  (log/info "Stopping LIPAS worker system")
  (ig/halt! system))