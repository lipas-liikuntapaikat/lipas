(ns lipas.ui.help.events
  (:require [re-frame.core :as rf]))

(rf/reg-event-db ::open-dialog
  (fn [db _]
    (assoc-in db [:help :dialog :open?] true)))

(rf/reg-event-db ::close-dialog
  (fn [db]
    (assoc-in db [:help :dialog :open?] false)))

(rf/reg-event-db ::select-section
  (fn [db [_ v]]
    (-> db
        (assoc-in [:help :dialog :selected-section] v)
        (assoc-in [:help :dialog :selected-page] nil))))

(rf/reg-event-db ::select-page
  (fn [db [_ v]]
    (assoc-in db [:help :dialog :selected-page] v)))

(rf/reg-event-db ::open-edit-mode
  (fn [db _]
    (assoc-in db [:help :dialog :mode] :edit)))

(rf/reg-event-db ::close-edit-mode
  (fn [db _]
    (assoc-in db [:help :dialog :mode] :read)))
