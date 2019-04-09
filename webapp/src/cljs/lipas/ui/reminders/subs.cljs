(ns lipas.ui.reminders.subs
  (:require
   [clojure.spec.alpha :as s]
   [lipas.ui.utils :as utils]
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::dialog-open?
 (fn [db _]
   (-> db :reminders :dialog-open?)))

(re-frame/reg-sub
 ::form
 (fn [db _]
   (let [data (-> db :reminders :form)]
     {:data   data
      :valid? (s/valid? :lipas/new-reminder data)})))

(re-frame/reg-sub
 ::upcoming-reminders
 (fn [db _]
   (-> db :reminders :data)))
