(ns lipas.worker
  "Legacy worker using tea-time scheduler.
   See also `lipas.integrator` namespace.
   
   For the new unified job queue system, use `lipas.jobs.main` instead."
  (:require [lipas.backend.config :as config]
            [lipas.backend.core :as core]
            [lipas.backend.system :as backend]
            [lipas.integration.utp.webhook :as utp-webhook]
            [lipas.reminders :as reminders]
            [taoensso.timbre :as log]
            [tea-time.core :as tt]))

(defonce tasks (atom {}))

(def all-tasks
  [:reminders :analysis :elevation #_:utp-webhook])

(defn -main
  "Runs all tasks if no task names are given in args. Else runs tasks
  defined in args."
  [& args]
  (let [config (select-keys config/system-config [:lipas/db :lipas/emailer :lipas/search])

        {:lipas/keys [db emailer search]} (backend/start-system! config)
        task-ks (or (not-empty (mapv keyword args))
                    all-tasks)]

    (log/info "Starting to run tasks:" task-ks)

    (tt/with-threadpool

      (when (some #{:reminders} task-ks)
        (let [task (tt/every! 300 15 (fn [] (reminders/process! db emailer)))]
          (swap! tasks assoc :reminders task)))

      (when (some #{:analysis} task-ks)
        (let [task (tt/every! 120 (fn [] (core/process-analysis-queue! db search)))]
          (swap! tasks assoc :analysis task)))

      (when (some #{:elevation} task-ks)
        (let [task (tt/every! 15 5 (fn [] (core/process-elevation-queue! db search)))]
          (swap! tasks assoc :elevation task)))

      (when (some #{:utp-webhook} task-ks)
        (let [config (get-in config/default-config [:app :utp])
              task (tt/every! 30 (fn [] (utp-webhook/process! db config)))]
          (swap! tasks assoc :utp-webhook task)))

      ;; Keep running forever
      (while true (Thread/sleep 100)))))

(comment
  (-main)

  (:analysis @tasks)

  (def my-conf (select-keys config/system-config [:lipas/db :lipas/emailer :lipas/search]))
  (def my-system (backend/start-system! my-conf))
  (core/process-analysis-queue! (:lipas/db my-system) (:lipas/search my-system)))
