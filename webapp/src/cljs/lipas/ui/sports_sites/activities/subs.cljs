(ns lipas.ui.sports-sites.activities.subs
  (:require [lipas.roles :as roles]
            [lipas.ui.map.utils :as map-utils]
            [re-frame.core :as rf]))

(rf/reg-sub ::activities
  (fn [db _]
    (->> db :sports-sites :activities)))

(rf/reg-sub ::data
  :<- [::activities]
  (fn [activities _]
    (:data activities)))

(rf/reg-sub ::activity-by-value
  :<- [::data]
  (fn [activities [_ value]]
    (get activities value)))

(rf/reg-sub ::mode
  :<- [::activities]
  (fn [activities _]
    (:mode activities)))

(rf/reg-sub ::activities-by-type-code
  :<- [::activities]
  (fn [activities _]
    (:by-type-code activities)))

(rf/reg-sub ::activity-for-type-code
  :<- [::activities-by-type-code]
  (fn [activities [_ type-code]]
    (get activities type-code)))

(rf/reg-sub ::activity-value-for-type-code
  (fn [[_ type-code]]
    (rf/subscribe [::activity-for-type-code type-code]))
  (fn [activity _]
    (:value activity)))

(rf/reg-sub ::show-activities?
  :<- [:lipas.ui.user.subs/user-data]
  (fn [user [_ activity-value role-context]]
    (and activity-value
         (roles/check-privilege user (assoc role-context :activity activity-value) :activity/edit))))

(rf/reg-sub ::selected-features
  :<- [:lipas.ui.map.subs/selected-features]
  (fn [fs _]
    fs))

(rf/reg-sub ::route-view
  :<- [::activities]
  :<- [::routes]
  (fn [[activities routes] _]
    (or
      (:route-view activities)
      (if (> (count routes) 1)
        :multi
        :single))))

(rf/reg-sub ::routes
  (fn [[_ lipas-id _]]
    [(rf/subscribe [:lipas.ui.sports-sites.subs/editing-rev lipas-id])
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

(rf/reg-sub ::route-count
  (fn [[_ lipas-id activity-k]]
    [(rf/subscribe [::routes lipas-id activity-k])])
  (fn [[routes] _]
    (count routes)))

(rf/reg-sub ::selected-route-id
  :<- [::activities]
  :<- [::routes]
  (fn [[activities routes] _]
    (:selected-route-id activities)))

(rf/reg-sub ::lipas-prop-value
  :<- [:lipas.ui.map.subs/selected-sports-site]
  (fn [site-data  [_ prop-k read-only?]]
    (if read-only?
      (get-in site-data [:display-data :properties prop-k])
      (get-in site-data [:edit-data :properties prop-k]))))

(rf/reg-sub ::geoms
  :<- [:lipas.ui.map.subs/selected-sports-site]
  (fn [site-data [_ read-only?]]
    (if read-only?
      (get-in site-data [:display-data :location :geometries])
      (get-in site-data [:edit-data :location :geometries]))))

(rf/reg-sub ::geom-type
  (fn [[_ read-only?]]
    [(rf/subscribe [::geoms read-only?])])
  (fn [[geoms] _]
    (-> geoms :features first :geometry :type)))

(rf/reg-sub ::field-sorter
  :<- [::activities]
  (fn [activities [_ activity-k]]
    (or (get-in activities [:field-sorters activity-k])
        (get-in activities [:field-sorters :default]))))
