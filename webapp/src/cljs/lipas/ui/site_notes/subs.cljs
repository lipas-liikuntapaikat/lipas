(ns lipas.ui.site-notes.subs
  (:require [re-frame.core :as rf]))

(rf/reg-sub ::site-notes
  (fn [db [_ lipas-id]]
    (get-in db [:site-notes :by-site lipas-id])))

(rf/reg-sub ::active-notes
  (fn [[_ lipas-id]]
    (rf/subscribe [::site-notes lipas-id]))
  (fn [notes _]
    (filterv #(= "active" (:status %)) notes)))

(rf/reg-sub ::archived-notes
  (fn [[_ lipas-id]]
    (rf/subscribe [::site-notes lipas-id]))
  (fn [notes _]
    (filterv #(= "archived" (:status %)) notes)))

(rf/reg-sub ::drawer
  (fn [db _]
    (get-in db [:site-notes :drawer])))

(rf/reg-sub ::draft
  (fn [db [_ lipas-id]]
    (get-in db [:site-notes :drafts lipas-id] "")))

(rf/reg-sub ::saving?
  (fn [db _]
    (boolean (get-in db [:site-notes :saving?]))))

(rf/reg-sub ::confirm-delete
  (fn [db _]
    (get-in db [:site-notes :confirm-delete])))

(rf/reg-sub ::org-notes
  (fn [db _]
    (get-in db [:site-notes :org])))
