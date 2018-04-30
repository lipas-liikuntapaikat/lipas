(ns lipas-ui.events
  (:require [re-frame.core :as re-frame]
            [lipas-ui.db :as db]))

(re-frame/reg-event-db
 ::initialize-db
 (fn  [_ _]
   db/default-db))

(re-frame/reg-event-db
 ::set-active-panel
 (fn [db [_ active-panel]]
   (assoc db :active-panel active-panel)))

(re-frame/reg-event-db
 ::set-menu-anchor
 (fn [db [_ menu-anchor]]
   (assoc db :menu-anchor menu-anchor)))

(re-frame/reg-event-db
 ::toggle-drawer
 (fn [db [_ _]]
   (update db :drawer-open? not)))

(re-frame/reg-event-db
 ::set-translator
 (fn [db [_ translator]]
   (assoc db :translator translator)))
