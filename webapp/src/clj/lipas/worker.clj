(ns lipas.worker
  "See also `lipas.integrator` namespace."
  (:require
   [lipas.backend.config :as config]
   [lipas.backend.system :as backend]
   [lipas.reminders :as reminders]
   [taoensso.timbre :as log]
   [tea-time.core :as tt]))

(defonce tasks (atom {}))

(def all-tasks
  [:reminders])

(defn -main
  "Runs all tasks if no task names are given in args. Else runs tasks
  defined in args."
  [& args]
  (let [config (select-keys config/default-config [:db :emailer])

        {:keys [db emailer]} (backend/start-system! config)
        task-ks              (or (not-empty (mapv keyword args))
                                 all-tasks)]

    (log/info "Starting to run tasks:" task-ks)

    (tt/with-threadpool

      (when (some #{:reminders} task-ks)
        (let [task (tt/every! 300 15 (fn [] (reminders/process! db emailer)))]
          (swap! tasks assoc :reminders task)))

      ;; Keep running forever
      (while true (Thread/sleep 1000)))))
