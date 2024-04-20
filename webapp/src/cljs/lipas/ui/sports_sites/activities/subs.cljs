(ns lipas.ui.sports-sites.activities.subs
  (:require
   [re-frame.core :as re-frame]
   [lipas.ui.map.utils :as map-utils]))

(re-frame/reg-sub
 ::activities
 (fn [db _]
   (->> db :sports-sites :activities)))

(re-frame/reg-sub
 ::data
 :<- [::activities]
 (fn [activities _]
   (:data activities)))

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
 ::show-activities?
 (fn [[_ type-code]]
   [(re-frame/subscribe [::activities-for-type type-code])
    (re-frame/subscribe [:lipas.ui.user.subs/permission-to-activities])
    (re-frame/subscribe [:lipas.ui.user.subs/admin?])])
 (fn [[activity activities-perms admin?] _]
   (or admin?
       (and (some? activity)
            (some #{(:value activity)} (keys activities-perms))))))

(re-frame/reg-sub
 ::edit-activities-only?
 (fn [[_ type-code]]
   [(re-frame/subscribe [::show-activities? type-code])
    (re-frame/subscribe [:lipas.ui.user.subs/permissions])])
 (fn [[show-activities? {:keys [admin? types all-types?] :as lol}] [_ type-code can-publish?]]
   (and (not (true? admin?))
        #_(not can-publish?)
        show-activities?)))

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
                  0 [{:id (str (random-uuid))

                      :route-name {:fi (:name edit-data)
                                   :se (get-in edit-data [:name-localized :se])
                                   :en (:name edit-data)}

                      :fids (-> edit-data
                                :location
                                :geometries
                                :features
                                (->> (map :id))
                                set)}]

                  ;; Single route, assume no sub-routes
                  ;; (all segments belong to this route)
                  #_#_1 (assoc-in routes [0 :fids] (-> edit-data
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
       (let [fids  (set fids)
             fcoll (-> edit-data
                       :location
                       :geometries
                       (update :features (fn [fs]
                                           (filterv #(contains? fids (:id %)) fs))))]
         (-> route
             (assoc :route-length (map-utils/calculate-length-km fcoll))
             (assoc :elevation-stats (map-utils/calculate-elevation-stats fcoll))))))))

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

(re-frame/reg-sub
 ::lipas-prop-value
 :<- [:lipas.ui.map.subs/selected-sports-site]
 (fn [site-data  [_ prop-k read-only?]]
   (if read-only?
     (get-in site-data [:display-data :properties prop-k])
     (get-in site-data [:edit-data :properties prop-k]))))

(re-frame/reg-sub
 ::geoms
 :<- [:lipas.ui.map.subs/selected-sports-site]
 (fn [site-data [_ read-only?]]
   (if read-only?
     (get-in site-data [:display-data :location :geometries])
     (get-in site-data [:edit-data :location :geometries]))))

(re-frame/reg-sub
 ::geom-type
 (fn [[_ read-only?]]
   [(re-frame/subscribe [::geoms read-only?])])
 (fn [[geoms] _]
   (-> geoms :features first :geometry :type)))

(re-frame/reg-sub
 ::field-sorter
 :<- [::activities]
 (fn [activities [_ activity-k]]
   (or (get-in activities [:field-sorters activity-k])
       (get-in activities [:field-sorters :default]))))
