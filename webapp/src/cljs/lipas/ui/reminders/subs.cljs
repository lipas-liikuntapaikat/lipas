(ns lipas.ui.reminders.subs
  (:require [clojure.spec.alpha :as s]
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
       :valid? (s/valid? :lipas/new-reminder data)})))

(rf/reg-sub ::upcoming-reminders
  :<- [::reminders]
  (fn [reminders _]
    (:data reminders)))
