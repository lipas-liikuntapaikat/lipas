(ns lipas.jobs.system
  "Integrant system configuration for the unified job worker.
  
  Separate from the main webapp system to allow independent deployment
  as a background worker process."
  (:require
   [integrant.core :as ig]
   [lipas.backend.config :as config]
   [lipas.backend.email :as email]
   [lipas.backend.search :as search]
   [lipas.backend.db.db :as db]
   [lipas.jobs.scheduler :as scheduler]
   [lipas.jobs.worker :as worker]
   [next.jdbc :as jdbc]
   [next.jdbc.connection :as connection]
   [taoensso.timbre :as log])
  (:import
   [com.zaxxer.hikari HikariDataSource]))

;; Database connection (reused from main system)
(defmethod ig/init-key :lipas/db [_ config]
  (log/info "Connecting to database for job worker")
  (-> config
      (connection/->pool HikariDataSource)
      (jdbc/with-options db/jdbc-opts)))

(defmethod ig/halt-key! :lipas/db [_ db]
  (log/info "Closing database connection")
  (.close ^HikariDataSource (:connectable db)))

;; Search client (reused from main system)
(defmethod ig/init-key :lipas/search [_ config]
  (log/info "Initializing search client for job worker")
  (search/->client config))

(defmethod ig/halt-key! :lipas/search [_ _]
  (log/info "Search client stopped"))

;; Email service (reused from main system)
(defmethod ig/init-key :lipas/emailer [_ config]
  (log/info "Initializing emailer for job worker")
  (email/create-emailer config))

(defmethod ig/halt-key! :lipas/emailer [_ _]
  (log/info "Emailer stopped"))

;; Job scheduler component
(defmethod ig/init-key :lipas.jobs/scheduler [_ {:keys [db]}]
  (log/info "Starting job scheduler")
  (scheduler/start-scheduler! db))

(defmethod ig/halt-key! :lipas.jobs/scheduler [_ _]
  (log/info "Stopping job scheduler")
  (scheduler/stop-scheduler!))

;; Mixed-duration worker component
(defmethod ig/init-key :lipas.jobs/worker [_ {:keys [db search emailer config]}]
  (log/info "Starting mixed-duration worker")
  (let [system {:db db :search search :emailer emailer}]
    (worker/start-mixed-duration-worker! system config)
    {:system system :config config}))

(defmethod ig/halt-key! :lipas.jobs/worker [_ _]
  (log/info "Stopping mixed-duration worker")
  (worker/stop-mixed-duration-worker!))

;; Worker system configuration
(def worker-system-config
  {:lipas/db (config/db-config)

   :lipas/search (config/search-config)

   :lipas/emailer (config/email-config)

   :lipas.jobs/scheduler
   {:db (ig/ref :lipas/db)}

   :lipas.jobs/worker
   {:db (ig/ref :lipas/db)
    :search (ig/ref :lipas/search)
    :emailer (ig/ref :lipas/emailer)
    :config {:fast-threads 3
             :general-threads 5
             :batch-size 12
             :poll-interval-ms 3000
             :fast-timeout-minutes 2
             :slow-timeout-minutes 20}}})

(defn start-worker-system!
  "Start the worker system with all components."
  []
  (log/info "Starting LIPAS job worker system")
  (ig/init worker-system-config))

(defn stop-worker-system!
  "Stop the worker system."
  [system]
  (log/info "Stopping LIPAS job worker system")
  (ig/halt! system))
