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

;; Selected section and page by index
(rf/reg-sub ::selected-section-idx
  :<- [::help]
  (fn [help _]
    (get-in help [:dialog :selected-section-idx])))

(rf/reg-sub ::selected-page-idx
  :<- [::help]
  (fn [help _]
    (get-in help [:dialog :selected-page-idx])))

;; Selected section and page by slug
(rf/reg-sub ::selected-section-slug
  :<- [::help]
  (fn [help _]
    (get-in help [:dialog :selected-section-slug])))

(rf/reg-sub ::selected-page-slug
  :<- [::help]
  (fn [help _]
    (get-in help [:dialog :selected-page-slug])))

;; Derived subscriptions for getting actual section and page data
(rf/reg-sub ::selected-section
  :<- [::help-data]
  :<- [::selected-section-idx]
  (fn [[data selected-idx] _]
    (when (and data (number? selected-idx) (< selected-idx (count data)))
      (nth data selected-idx))))

(rf/reg-sub ::selected-section-pages
  :<- [::selected-section]
  (fn [section _]
    (:pages section)))

(rf/reg-sub ::selected-page
  :<- [::selected-section-pages]
  :<- [::selected-page-idx]
  (fn [[pages selected-idx] _]
    (when (and pages (number? selected-idx) (< selected-idx (count pages)))
      (nth pages selected-idx))))

;; For backward compatibility
(rf/reg-sub ::mode
  :<- [::help]
  (fn [help _]
    (get-in help [:dialog :mode])))
