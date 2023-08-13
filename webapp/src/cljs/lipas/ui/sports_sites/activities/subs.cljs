(ns lipas.ui.sports-sites.activities.subs
  (:require
   [re-frame.core :as re-frame]
   [lipas.ui.map.utils :as map-utils]))

(re-frame/reg-sub
 ::activities
 (fn [db _]
   (->> db :sports-sites :activities)))

(re-frame/reg-sub
 ::mode
 :<- [::activities]
 (fn [activities _]
   (:mode activities)))

(re-frame/reg-sub
 ::activities-by-type-code
 :<- [::activities]
 (fn [activities _]
   (:by-type-code activities)))

(re-frame/reg-sub
 ::activities-for-type
 :<- [::activities-by-type-code]
 (fn [activities [_ type-code]]
   (get activities type-code)))

(re-frame/reg-sub
 ::activity-type?
 (fn [[_ type-code]]
   (re-frame/subscribe [::activities-for-type type-code]))
 (fn [activities _]
   (some? activities)))

(re-frame/reg-sub
 ::selected-features
 :<- [:lipas.ui.map.subs/selected-features]
 (fn [fs _]
   fs))

(re-frame/reg-sub
 ::routes
 (fn [[_ lipas-id _]]
   [(re-frame/subscribe [:lipas.ui.sports-sites.subs/editing-rev lipas-id])
    (re-frame/subscribe [:lipas.ui.sports-sites.subs/display-site lipas-id])])
 (fn [[edit-data display-data] [_ _lipas-id activity-k]]
   (let [routes (get-in edit-data [:activities activity-k :routes])]
     (for [{:keys [fids] :as route} routes]
       (let [fids (set fids)]
         (assoc route
                :route-length (-> edit-data
                                  :location
                                  :geometries
                                  (update :features (fn [fs]
                                                      (filterv #(contains? fids (:id %)) fs)))
                                  (map-utils/calculate-length))))))))

(re-frame/reg-sub
 ::selected-route-id
 :<- [::activities]
 (fn [activities _]
   (:selected-route-id activities)))
