(ns lipas.integrator
  "See also `lipas.worker` namespace."
  (:require
   [lipas.backend.config :as config]
   [lipas.backend.core :as core]
   [lipas.backend.system :as backend]
   [lipas.integration.old-lipas.tasks :as integration]
   [taoensso.timbre :as log]
   [tea-time.core :as tt]))

(defonce tasks (atom {}))

(def all-tasks
  [:new->old :old->new])

(defn -main
  "Runs all integrations if no task names are given in args. Else runs
  only integrations defined in args."
  [& args]
  (let [config (select-keys config/default-config [:db :search])

        {:keys [db search]} (backend/start-system! config)
        user                (core/get-user db "import@lipas.fi")
        task-ks             (or (not-empty (mapv keyword args))
                                all-tasks)]

    (log/info "Starting to run integrations:" task-ks)

    (tt/with-threadpool

      (when (some #{:new->old} task-ks)
        (let [task (tt/every! 60 (fn [] (integration/new->old db)))]
          (swap! tasks assoc :new->old task)))

      (when (some #{:old->new} task-ks)
        (let [task (tt/every! 60 30 (fn [] (integration/old->new db search user)))]
          (swap! tasks assoc :old->new task)))

      ;; Keep running forever
      (while true (Thread/sleep 1000)))))

(comment
  (-main "new->old")
  (:new->old @tasks)
  (tt/cancel! (-> @tasks :old->new))
  (tt/cancel! (-> @tasks :new->old))
  (tt/stop!))
