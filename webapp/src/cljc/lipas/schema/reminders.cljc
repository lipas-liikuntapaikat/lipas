(ns lipas.schema.reminders
  (:require [lipas.data.reminders :as reminders]
            [lipas.schema.common :as common]
            [malli.core :as m]))

(def reminder-status
  (m/schema (into [:enum] (keys reminders/statuses))))

(def reminder-body
  (m/schema
   [:map
    [:message [:string {:min 1 :max 2048}]]]))

(def new-reminder
  (m/schema
   [:map
    [:event-date common/iso8601-timestamp]
    [:body reminder-body]]))

(def reminder
  (m/schema
   [:map
    [:id common/uuid]
    [:created-at common/iso8601-timestamp]
    [:event-date common/iso8601-timestamp]
    [:status reminder-status]
    [:body reminder-body]]))
