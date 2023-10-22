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
 ::route-view
 :<- [::activities]
 :<- [::routes]
 (fn [[activities routes] _]
   (or
    (:route-view activities)
    (if (> (count routes) 1)
      :multi
      :single))))

(re-frame/reg-sub
 ::routes
 (fn [[_ lipas-id _]]
   [(re-frame/subscribe [:lipas.ui.sports-sites.subs/editing-rev lipas-id])
    #_(re-frame/subscribe [:lipas.ui.sports-sites.subs/display-site lipas-id])])
 (fn [[edit-data #_display-data] [_ _lipas-id activity-k]]

   ;; Apply logic to allow "easy first entry" of data

   (let [routes (get-in edit-data [:activities activity-k :routes] [])
         routes (condp = (count routes)

                  ;; No routes (yet).
                  ;; Generate first empty "route", assume no sub-routes
                  ;; (all segments belong to this route)
                  0 [{:id   (str (random-uuid))
                      :fids (-> edit-data
                                :location
                                :geometries
                                :features
                                (->> (map :id))
                                set)}]

                  ;; Single route, assume no sub-routes
                  ;; (all segments belong to this route)
                  1 (assoc-in routes [0 :fids] (-> edit-data
                                                   :location
                                                   :geometries
                                                   :features
                                                   (->> (map :id))
                                                   set))

                  ;; Multiple routes, don't assume anything about sub-routes,
                  ;; let the user decide
                  routes)]

     ;; Calc route/sub-route lengths from segments
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
 ::route-count
 (fn [[_ lipas-id activity-k]]
   [(re-frame/subscribe [::routes lipas-id activity-k])])
 (fn [[routes] _]
   (count routes)))

(re-frame/reg-sub
 ::selected-route-id
 :<- [::activities]
 :<- [::routes]
 (fn [[activities routes] _]
   (:selected-route-id activities)))
