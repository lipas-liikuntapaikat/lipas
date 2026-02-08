(ns lipas.schema.reminders
  (:require [lipas.data.reminders :as reminders]
            [lipas.schema.common :as common]))

(def reminder-status
  (into [:enum] (keys reminders/statuses)))

(def reminder-body
  [:map
   [:message [:string {:min 1 :max 2048}]]])

(def new-reminder
  [:map
   [:event-date common/iso8601-timestamp]
   [:body reminder-body]])

(def reminder
  [:map
   [:id common/uuid]
   [:created-at common/iso8601-timestamp]
   [:event-date common/iso8601-timestamp]
   [:status reminder-status]
   [:body reminder-body]])
