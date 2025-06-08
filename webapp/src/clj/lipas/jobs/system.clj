(ns lipas.jobs.system
  "Integrant system configuration for the unified job worker.
  
  Reuses existing system components from the main webapp system
  and adds worker-specific components."
  (:require
   [integrant.core :as ig]
   [lipas.backend.config :as config]
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
  (worker/start-mixed-duration-worker!
   {:db db :search search :emailer emailer}
   config))

(defmethod ig/halt-key! :lipas.jobs/worker
  [_ _]
  (log/info "Stopping unified job worker")
  (worker/stop-mixed-duration-worker!))

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
     :config {:fast-threads 3
              :general-threads 5
              :batch-size 10
              :poll-interval-ms 3000
              :fast-timeout-minutes 2
              :slow-timeout-minutes 20}}}))

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
