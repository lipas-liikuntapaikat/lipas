(ns lipas.ui.sports-sites.routes.events
  (:require [re-frame.core :as rf]))

;; Event to activate route mode when entering the REITIT tab
(rf/reg-event-fx ::activate-route-mode
                 (fn [{:keys [db]} [_ lipas-id]]
                   {:db (-> db
                            (assoc-in [:sports-sites :routes :active?] true)
                            ;; Set map mode directly to :editing with :selecting sub-mode
                            (update-in [:map :mode] merge {:name :editing :sub-mode :selecting}))
                    :fx [[:dispatch [:lipas.ui.map.events/show-problems nil]]]}))

;; Event to deactivate route mode when leaving the REITIT tab
(rf/reg-event-fx ::deactivate-route-mode
                 (fn [{:keys [db]} _]
                   (let [lipas-id (get-in db [:map :selected-sports-site :lipas-id])
                         editing? (get-in db [:sports-sites lipas-id :editing])]
                     {:db (-> db
                              (assoc-in [:sports-sites :routes :active?] false)
                              ;; When editing, restore to :editing mode with :editing sub-mode
                              (cond-> editing?
                                (update-in [:map :mode] merge {:name :editing :sub-mode :editing})))})))

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
(rf/reg-event-db ::cleanup-routes-uis
                 (fn [db _]
                   (update db :sports-sites dissoc :routes)))