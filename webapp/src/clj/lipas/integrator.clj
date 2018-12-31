(ns lipas.integrator
  (:require
   [clojure.java.jdbc :as jdbc]
   [clojure.stacktrace :as stacktrace]
   [lipas.backend.config :as config]
   [lipas.backend.core :as core]
   [lipas.backend.db.db :as db]
   [lipas.backend.system :as backend]
   [lipas.migrate-data :as migrate]
   [lipas.utils :as utils]
   [taoensso.timbre :as log]
   [tea-time.core :as tt]))

;; Data integrations between old Lipas and new LIPAS.

;; Inbound integration can be removed once old Lipas stops receiving
;; new data (old UI is disabled).

;; TODO initial timestamps from env vars
(def initial-timestamps
  {"old->new" "2018-12-14T00:00:00.000Z"
   "new->old" "2018-12-14T00:00:00.000Z"})

(defn handle-error [db name e]
  (let [data (or (ex-data e)
                 {:msg   (.getMessage e)
                  :error (with-out-str (stacktrace/print-stack-trace e))})]
    (log/error "Integration failed!" data)
    (db/add-integration-entry! db {:status     "failure"
                                   :name       name
                                   :event-date (utils/timestamp)
                                   :document   data})))

(defn handle-success [db name res]
  (let [total (-> res :total)

        entry {:status     "success"
               :name       name
               :event-date (:latest res)
               :document   res}]

    (when (> total 0) (db/add-integration-entry! db entry))

    (log/info "Total:"   (-> res :total)
              "Updated:" (-> res :updated count)
              "Ignored:" (-> res :ignored count)
              "Failed:"  (-> res :failed count))))

;;;;;;;;;;;;;
;; INBOUND ;;
;;;;;;;;;;;;;

(defn old->new [db search user]
  (let [name         "old->new"
        last-success (or (db/get-last-integration-timestamp db name)
                         (initial-timestamps name))]
    (log/info "Starting to fetch changes from old Lipas since" last-success)
    (try
      (jdbc/with-db-transaction [tx db]
        (let [res (migrate/migrate-changed-since! db search user last-success)]
          (handle-success db name res)))
      (catch Exception e
        (handle-error db name e)))))

;;;;;;;;;;;;;;
;; OUTBOUND ;;
;;;;;;;;;;;;;;

(defn new->old [db]
  (let [name         "new->old"
        last-success (or (db/get-last-integration-timestamp db name)
                         (initial-timestamps name))]
    (log/info "Starting to push changes to old Lipas since" last-success)
    (try
      (migrate/add-changed-to-out-queue! db last-success)
      (->> (migrate/process-integration-out-queue! db)
           (handle-success db name))
      (catch Exception e
        (handle-error db name e)))))

;; TODO

(def tasks (atom {}))

(defn -main [& args]
  (let [config              (select-keys config/default-config [:db :search])
        {:keys [db search]} (backend/start-system! config)
        user                (core/get-user db "import@lipas.fi")
        task                (tt/every! 30 (bound-fn [] (old->new db search user)))]
    (swap! tasks assoc :old->new task)
    (tt/start!)))

(comment
  (-main)
  (tt/cancel! (-> @tasks :old->new))
  (tt/stop!)

  (def config (select-keys config/default-config [:db :search]))
  (def system (backend/start-system! config))
  (def db (:db system))
  (def search (:search system))
  (def user (core/get-user db "import@lipas.fi"))
  (old->new db search user))
