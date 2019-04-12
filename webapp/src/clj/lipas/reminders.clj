(ns lipas.reminders
  (:require
   [clojure.java.jdbc :as jdbc]
   [lipas.backend.core :as core]
   [lipas.backend.db.db :as db]
   [lipas.backend.email :as email]
   [taoensso.timbre :as log]))

(defn add-to-queue!
  [db emails]
  (log/info "Adding" (count emails) "reminder emails to queue")
  (doseq [{:keys [reminder-id] :as m} emails]
    (jdbc/with-db-transaction [tx db]
      (db/add-email-to-out-queue! tx {:message m})
      (db/update-reminder-status! tx {:id reminder-id :status "sent"}))))

(defn ->email
  [db {:keys [id body account-id]}]
  (let [user (db/get-user-by-id db {:id account-id})
        url  "https://liikuntapaikat.lipas.fi/#/kirjaudu"]
    {:reminder-id id
     :account-id  account-id
     :email       (:email user)
     :link        (core/create-magic-link url user)
     :type        "reminder"
     :body        body}))

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
  (let [entries (db/get-email-out-queue! db)]
    (log/info "Processing" (count entries) "entries from email out queue")
    (doseq [entry entries
            :let  [email (:message entry)]]
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

(comment
  (require '[lipas.backend.config :as config])
  (require '[lipas.backend.system :as backend])
  (def config (select-keys config/default-config [:db :emailer]))
  (def system (backend/start-system! config))
  (def db (:db system))
  (def emailer (:emailer system))
  (db/get-overdue-reminders db)
  (add-overdue-to-queue! db)
  (process-email-out-queue! db emailer)
  (process! db emailer))
