(ns lipas.ui.reminders.subs
  (:require
   [clojure.spec.alpha :as s]
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::reminders
 (fn [db _]
   (:reminders db)))

(re-frame/reg-sub
 ::dialog-open?
 :<- [::reminders]
 (fn [reminders _]
   (:dialog-open? reminders)))

(re-frame/reg-sub
 ::form
 :<- [::reminders]
 (fn [reminders _]
   (let [data (:form reminders)]
     {:data   data
      :valid? (s/valid? :lipas/new-reminder data)})))

(re-frame/reg-sub
 ::upcoming-reminders
 :<- [::reminders]
 (fn [reminders _]
   (:data reminders)))
