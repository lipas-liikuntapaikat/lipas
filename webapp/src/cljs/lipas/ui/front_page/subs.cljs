(ns lipas.ui.front-page.subs
  (:require [lipas.ui.utils :as utils]
            [re-frame.core :as rf]))

(rf/reg-sub ::newsletter
  (fn [db _]
    (get-in db [:front-page :newsletter])))

(rf/reg-sub ::newsletter-in-progress?
  :<- [::newsletter]
  (fn [newsletter _]
    (:in-progress? newsletter)))

(rf/reg-sub ::newsletter-data
  :<- [::newsletter]
  (fn [newsletter _]
    (->> newsletter
         :data
         (sort-by :send-time utils/reverse-cmp)
         (take 5)
         (map
           (fn [m]
             (update m :send-time utils/->human-date))))))

(rf/reg-sub ::newsletter-error
  :<- [::newsletter]
  (fn [newsletter _]
    (:error newsletter)))
