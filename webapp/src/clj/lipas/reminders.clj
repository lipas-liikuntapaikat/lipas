(ns lipas.reminders
  (:require
   [clojure.java.jdbc :as jdbc]
   [lipas.backend.core :as core]
   [lipas.backend.db.db :as db]
   [lipas.backend.email :as email]
   [taoensso.timbre :as log]))

(defn add-to-queue!
  [db emails]
  ;; DEPRECATED: This function is no longer used.
  ;; The new jobs system handles reminder emails through the "produce-reminders" job type.
  ;; Keeping for backward compatibility during migration.
  (log/warn "add-to-queue! is deprecated. Use the jobs system instead.")
  (log/info "Adding" (count emails) "reminder emails to queue")
  (doseq [{:keys [reminder-id] :as m} emails]
    (jdbc/with-db-transaction [tx db]
      (db/add-email-to-out-queue! tx {:message m})
      (db/update-reminder-status! tx {:id reminder-id :status "sent"}))))

(defn ->email
  [db {:keys [id body account-id]}]
  (let [user (db/get-user-by-id db {:id account-id})
        url "https://liikuntapaikat.lipas.fi/kirjaudu"]
    {:reminder-id id
     :account-id account-id
     :email (:email user)
     :link (core/create-magic-link url user)
     :type "reminder"
     :body body}))

(defn add-overdue-to-queue!
  [db]
  (->> (db/get-overdue-reminders db)
       (map (partial ->email db))
       (add-to-queue! db)))

(defn send-reminder-email!
  [emailer {:keys [email link body account-id]}]
  (email/send-reminder-email! emailer email link body)
  (log/info "Sent reminder email to user" account-id))

(defn process-email-out-queue!
  [db emailer]
  ;; DEPRECATED: This function is replaced by the new jobs system.
  ;; Email processing is now handled by the "email" job type.
  ;; Keeping for backward compatibility during migration.
  (log/warn "process-email-out-queue! is deprecated. Use the jobs system instead.")
  (let [entries (db/get-email-out-queue! db)]
    (log/info "Processing" (count entries) "entries from email out queue")
    (doseq [entry entries
            :let [email (:message entry)]]
      (case (:type email)
        "reminder" (send-reminder-email! emailer email)
        (throw (ex-info "Unknown email type!" (select-keys email [:type]))))

      (db/delete-email-from-out-queue! db entry))))

(defn process!
  [db emailer]
  (log/info "Checking for overdue reminders...")
  (try
    (add-overdue-to-queue! db)
    (process-email-out-queue! db emailer)
    (log/info "Done!")
    (catch Exception e (log/error e))))

(defn get-overdue [db]
  (db/get-overdue-reminders db))

(defn mark-processed!
  [db reminder-id]
  (db/update-reminder-status! db {:id reminder-id :status "sent"}))

(comment
  (require '[lipas.backend.config :as config])
  (require '[lipas.backend.system :as backend])
  (def config (select-keys config/default-config [:db :emailer]))
  (def system (backend/start-system! config))
  (def db (:db system))
  (def emailer (:emailer system))
  (db/get-overdue-reminders (repl/db))
  (add-overdue-to-queue! db)
  (process-email-out-queue! db emailer)
  (process! db emailer))
