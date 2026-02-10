(ns lipas.ui.reminders.subs
  (:require [lipas.schema.reminders :as reminders-schema]
            [malli.core :as m]
            [re-frame.core :as rf]))

(rf/reg-sub ::reminders
  (fn [db _]
    (:reminders db)))

(rf/reg-sub ::dialog-open?
  :<- [::reminders]
  (fn [reminders _]
    (:dialog-open? reminders)))

(rf/reg-sub ::form
  :<- [::reminders]
  (fn [reminders _]
    (let [data (:form reminders)]
      {:data   data
       :valid? (m/validate reminders-schema/new-reminder data)})))

(rf/reg-sub ::upcoming-reminders
  :<- [::reminders]
  (fn [reminders _]
    (:data reminders)))
