(ns lipas.ui.service-notices.subs
  (:require [re-frame.core :as rf]))

(rf/reg-sub ::dismissed
  (fn [db _]
    (get-in db [:service-notices :dismissed] #{})))

(rf/reg-sub ::visible
  :<- [::dismissed]
  (fn [dismissed _]
    ;; One `(when <who should see it> :notice-id)` per ongoing problem, in
    ;; display order.
    (->> []
         (remove nil?)
         (remove dismissed)
         vec)))
