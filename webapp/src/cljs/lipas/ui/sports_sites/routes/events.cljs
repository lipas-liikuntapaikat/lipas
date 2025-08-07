(ns lipas.ui.sports-sites.routes.events
  (:require [re-frame.core :as rf]))

;; Event to activate route mode when entering the REITIT tab
(rf/reg-event-fx ::activate-route-mode
                 (fn [{:keys [db]} [_ lipas-id]]
                   {:db (assoc-in db [:sports-sites :routes :active?] true)
                    :fx [[:dispatch [:lipas.ui.map.events/continue-editing :selecting "LineString"]]]}))

;; Event to deactivate route mode when leaving the REITIT tab
(rf/reg-event-fx ::deactivate-route-mode
                 (fn [{:keys [db]} _]
                   {:db (assoc-in db [:sports-sites :routes :active?] false)
                    :fx [[:dispatch [:lipas.ui.map.events/continue-editing :view-only]]]}))

;; Event to toggle between simple and advanced mode
(rf/reg-event-db ::toggle-mode
                 (fn [db [_ simple?]]
                   (assoc-in db [:sports-sites :routes :simple-mode?] simple?)))

;; Event to initialize routes UI state
(rf/reg-event-db ::init-routes-ui
                 (fn [db [_ lipas-id]]
                   (-> db
                       (assoc-in [:sports-sites :routes :lipas-id] lipas-id)
                       (assoc-in [:sports-sites :routes :simple-mode?] true)
                       (assoc-in [:sports-sites :routes :unsaved-changes?] false))))

;; Event to clean up routes UI state
(rf/reg-event-db ::cleanup-routes-ui
                 (fn [db _]
                   (update db :sports-sites dissoc :routes)))