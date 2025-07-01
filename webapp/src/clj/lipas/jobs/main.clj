(ns lipas.jobs.main
  "Main entry point for the unified job queue system worker.

   Usage:
   - No args: Run full worker system (scheduler + mixed-duration worker)
   - 'worker': Run only the mixed-duration worker
   - 'scheduler': Run only the scheduler"
  (:require
   [lipas.backend.config :as config]
   [lipas.backend.system :as backend]
   [lipas.jobs.scheduler :as scheduler]
   [lipas.jobs.system :as jobs-system]
   [lipas.jobs.worker :as worker]
   [taoensso.timbre :as log])
  (:gen-class))

(defn -main
  [& args]
  (println "🚀 LIPAS Jobs Main starting...")
  (println "📋 Arguments received:" (vec args))
  (try

    (log/set-min-level! :info)

    (let [mode (first args)]
      (println "🎯 Running in mode:" (or mode "default (scheduler + worker)"))
      (case mode
        "worker"
        (do
          (log/info "Starting unified job worker (worker only)")
          (println "⚙️  Initializing worker-only system...")
          (let [config (select-keys config/system-config [:lipas/db :lipas/search :lipas/emailer])
                {:lipas/keys [db search emailer]} (backend/start-system! config)
                system {:db db :search search :emailer emailer}]
            (println "✅ Backend system started, starting job worker...")
            (worker/start-mixed-duration-worker! system {})
            ;; Add shutdown hook
            (.addShutdownHook (Runtime/getRuntime)
                              (Thread. #(worker/stop-mixed-duration-worker!) "shutdown-hook"))
            ;; Keep running
            (log/info "Unified job worker started, press Ctrl+C to stop")
            (println "🎉 Worker system running! Press Ctrl+C to stop")
            (while true (Thread/sleep 1000))))

        "scheduler"
        (do
          (log/info "Starting unified job scheduler (scheduler only)")
          (println "⚙️  Initializing scheduler-only system...")
          (let [config (select-keys config/system-config [:lipas/db])
                {:lipas/keys [db]} (backend/start-system! config)]
            (println "✅ Backend system started, starting job scheduler...")
            (scheduler/start-scheduler! db)
            ;; Add shutdown hook
            (.addShutdownHook (Runtime/getRuntime)
                              (Thread. #(scheduler/stop-scheduler!) "shutdown-hook"))
            ;; Keep running
            (log/info "Unified job scheduler started, press Ctrl+C to stop")
            (println "🎉 Scheduler system running! Press Ctrl+C to stop")
            (while true (Thread/sleep 1000))))

        ;; Default: run both scheduler and worker
        (do
          (log/info "Starting unified job system (scheduler + worker)")
          (println "⚙️  Initializing full job system (scheduler + worker)...")
          (let [system (jobs-system/start-worker-system!)]
            (println "✅ Job system started successfully!")
            ;; Add shutdown hook
            (.addShutdownHook (Runtime/getRuntime)
                              (Thread. #(jobs-system/stop-worker-system! system) "shutdown-hook"))
            ;; Keep running
            (log/info "Unified job system started, press Ctrl+C to stop")
            (println "🎉 Full job system running! Press Ctrl+C to stop")
            (while true (Thread/sleep 1000))))))
    (catch Exception e
      (println "❌ ERROR during startup:" (.getMessage e))
      (.printStackTrace e)
      (System/exit 1))))
