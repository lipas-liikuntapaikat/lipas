(ns lipas.jobs.system
  "Integrant system configuration for the job worker.

  Reuses existing system components (db, search, emailer) from the main
  webapp system and adds the worker and scheduler."
  (:require
   [integrant.core :as ig]
   [lipas.backend.config :as config]
   [lipas.jobs.scheduler :as scheduler]
   [lipas.jobs.worker :as worker]
   [taoensso.timbre :as log]))

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
  (worker/start-mixed-duration-worker!
   {:db db :search search :emailer emailer}
   config))

(defmethod ig/halt-key! :lipas.jobs/worker
  [_ _]
  (log/info "Stopping unified job worker")
  (worker/stop-mixed-duration-worker!))

(defn get-worker-config
  "Build worker configuration with environment variable overrides.
  Per-job-type timeouts live in lipas.jobs.registry."
  []
  (let [env-config (fn [key default]
                     (if-let [env-val (System/getenv (str "WORKER_"
                                                          (-> key name
                                                              (.replace "-" "_")
                                                              (.toUpperCase))))]
                       (try
                         (Long/parseLong env-val)
                         (catch Exception _
                           (log/warn "Invalid env var value, using default"
                                     {:key key :value env-val})
                           default))
                       default))]
    {:fast-threads (env-config :fast-threads 2)
     :general-threads (env-config :general-threads 2)
     :batch-size (env-config :batch-size 10)
     :poll-interval-ms (env-config :poll-interval-ms 3000)}))

(def worker-system-config
  (merge
   ;; Reuse existing system components
   (select-keys config/system-config
                [:lipas/db :lipas/search :lipas/emailer])

   {:lipas.jobs/scheduler
    {:db (ig/ref :lipas/db)}

    :lipas.jobs/worker
    {:db (ig/ref :lipas/db)
     :search (ig/ref :lipas/search)
     :emailer (ig/ref :lipas/emailer)
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
