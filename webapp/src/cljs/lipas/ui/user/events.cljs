(ns lipas.ui.user.events
  (:require
   [re-frame.core :as re-frame]))

(re-frame/reg-event-fx
 ::get-users-sports-sites
 (fn [_ [_ {:keys [permissions]}]]
   ;; TODO Create proper endpoint for fetching sites based on
   ;; permissions. Current implementation fetches all sites from
   ;; backend.
   {:dispatch-n
    [[:lipas.ui.sports-sites.events/get-by-type-code 3110]
     [:lipas.ui.sports-sites.events/get-by-type-code 3130]
     [:lipas.ui.sports-sites.events/get-by-type-code 2510]
     [:lipas.ui.sports-sites.events/get-by-type-code 2520]]}))

(re-frame/reg-event-db
 ::select-sports-site
 (fn [db [_ site]]
   (assoc-in db [:user :selected-sports-site] site)))
