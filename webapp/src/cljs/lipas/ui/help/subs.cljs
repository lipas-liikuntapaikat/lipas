(ns lipas.ui.help.subs
  (:require [re-frame.core :as rf]))

(rf/reg-sub ::help
  (fn [db _]
    (:help db)))

(rf/reg-sub ::help-data
  :<- [::help]
  (fn [help _]
    (:data help)))

(rf/reg-sub ::dialog-open?
  :<- [::help]
  (fn [help _]
    (get-in help [:dialog :open?])))

(rf/reg-sub ::selected-section
  :<- [::help]
  (fn [help _]
    (get-in help [:dialog :selected-section])))

(rf/reg-sub ::selected-page
  :<- [::help]
  (fn [help _]
    (get-in help [:dialog :selected-page])))
