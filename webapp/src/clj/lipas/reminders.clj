(ns lipas.reminders
  (:require
   [lipas.backend.core :as core]
   [lipas.backend.db.db :as db]))

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

(defn get-overdue [db]
  (db/get-overdue-reminders db))

(defn mark-processed!
  [db reminder-id]
  (db/update-reminder-status! db {:id reminder-id :status "sent"}))

(comment
  (db/get-overdue-reminders (repl/db)))
