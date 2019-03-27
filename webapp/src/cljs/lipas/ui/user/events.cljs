(ns lipas.ui.user.events
  (:require
   [re-frame.core :as re-frame]))

(re-frame/reg-event-fx
 ::get-users-sports-sites
 (fn [{:keys [db]} _]
   (let [permissions (-> db :user :login :permissions)]
     {:dispatch-n
      (->> permissions
           :sports-sites
           (mapv (fn [lipas-id]
                   [:lipas.ui.sports-sites.events/get lipas-id])))})))

(re-frame/reg-event-fx
 ::select-sports-site
 (fn [{:keys [db]} [_ site]]
   (let [portal-site? (-> site :type-code #{3110 3130 2510 2520})]
     {:db (if (or portal-site? (nil? site))
            (assoc-in db [:user :selected-sports-site] site)
            db)
      :dispatch-n [(when-not portal-site?
                     [:lipas.ui.events/navigate :lipas.ui.routes.map/details-view site])]})))
