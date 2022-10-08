(ns lipas.worker
  "See also `lipas.integrator` namespace."
  (:require
   [lipas.backend.config :as config]
   [lipas.backend.core :as core]
   [lipas.backend.system :as backend]
   [lipas.reminders :as reminders]
   [taoensso.timbre :as log]
   [tea-time.core :as tt]))

(defonce tasks (atom {}))

(def all-tasks
  [:reminders :analysis])

(defn -main
  "Runs all tasks if no task names are given in args. Else runs tasks
  defined in args."
  [& args]
  (let [config (select-keys config/default-config [:db :emailer :search])

        {:keys [db emailer search]} (backend/start-system! config)
        task-ks                     (or (not-empty (mapv keyword args))
                                        all-tasks)]

    (log/info "Starting to run tasks:" task-ks)

    (tt/with-threadpool

      (when (some #{:reminders} task-ks)
        (let [task (tt/every! 300 15 (fn [] (reminders/process! db emailer)))]
          (swap! tasks assoc :reminders task)))

      (when (some #{:analysis} task-ks)
        (let [task (tt/every! 30 (fn [] (core/process-analysis-queue! db search)))]
          (swap! tasks assoc :analysis task)))

      ;; Keep running forever
      (while true (Thread/sleep 100)))))

(comment
  (-main)

  (:analysis @tasks)

  (def my-conf (select-keys config/default-config [:db :emailer :search]))
  (def my-system (backend/start-system! my-conf))
  (core/process-analysis-queue! (:db my-system) (:search my-system))

  )
