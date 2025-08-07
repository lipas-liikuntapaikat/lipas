(ns lipas.ui.sports-sites.routes.subs
  (:require [re-frame.core :as rf]))

;; Get the routes UI state
(rf/reg-sub ::routes-ui
            (fn [db _]
              (get-in db [:sports-sites :routes])))

;; Check if routes mode is active
(rf/reg-sub ::route-mode-active?
            :<- [::routes-ui]
            (fn [routes-ui _]
              (:active? routes-ui)))

;; Check if in simple mode
(rf/reg-sub ::simple-mode?
            :<- [::routes-ui]
            (fn [routes-ui _]
              (get routes-ui :simple-mode? true)))

;; Get current lipas-id for routes
(rf/reg-sub ::routes-lipas-id
            :<- [::routes-ui]
            (fn [routes-ui _]
              (:lipas-id routes-ui)))

;; Check for unsaved changes
(rf/reg-sub ::unsaved-changes?
            :<- [::routes-ui]
            (fn [routes-ui _]
              (:unsaved-changes? routes-ui)))