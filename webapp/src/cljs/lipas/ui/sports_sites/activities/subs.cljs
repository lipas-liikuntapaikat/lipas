(ns lipas.ui.sports-sites.activities.subs
  (:require [clojure.set]
            [lipas.roles :as roles]
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
         (roles/check-privilege user (assoc role-context :activity #{activity-value}) :activity/view))))

(rf/reg-sub ::edit-activities?
  :<- [:lipas.ui.user.subs/user-data]
  (fn [user [_ activity-value role-context]]
    (and activity-value
         (roles/check-privilege user (assoc role-context :activity #{activity-value}) :activity/edit))))

(rf/reg-sub ::selected-features
  :<- [:lipas.ui.map.subs/selected-features]
  (fn [fs _]
    fs))

(rf/reg-sub ::route-view
  :<- [::activities]
  (fn [activities _]
    (:route-view activities)))

(rf/reg-sub ::routes
  (fn [[_ lipas-id _]]
    [(rf/subscribe [:lipas.ui.sports-sites.subs/editing-rev lipas-id])
     (rf/subscribe [:lipas.ui.sports-sites.subs/latest-rev lipas-id])])
  (fn [[edit-data display-data] [_ _lipas-id activity-k]]
    ;; Use edit-data if available, otherwise use display-data
    (let [sports-site (or edit-data display-data)
          routes (get-in sports-site [:activities activity-k :routes] [])]

      ;; Apply logic to allow "easy first entry" of data only when editing
      (if edit-data
        (condp = (count routes)
          ;; No routes (yet).
          ;; Generate first empty "route", assume no sub-routes
          ;; (all segments belong to this route)
          0 (let [all-fids (-> sports-site
                               :location
                               :geometries
                               :features
                               (->> (map :id))
                               set)]
              [{:id (str (random-uuid))
                :route-name {:fi (:name sports-site)
                             :se (get-in sports-site [:name-localized :se])
                             :en (:name sports-site)}
                :fids all-fids}])

          ;; Multiple routes or single route, don't modify
          routes)
        ;; In read-only mode, just return routes as-is
        routes))))

(rf/reg-sub ::selected-route-id
  :<- [::activities]
  (fn [activities _]
    (:selected-route-id activities)))

(rf/reg-sub ::lipas-prop-value
  :<- [:lipas.ui.map.subs/selected-sports-site]
  (fn [site-data [_ prop-k read-only?]]
    ;; NOTE: This returns quite different data for most properties because
    ;; display and edit data have different schema
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

(rf/reg-sub ::route-itrs-classification?
  (fn [[_ lipas-id type-code]]
    [(rf/subscribe [:lipas.ui.sports-sites.subs/editing-rev lipas-id])
     (rf/subscribe [::activity-value-for-type-code type-code])
     (rf/subscribe [::selected-route-id lipas-id])])
  (fn [[site activity-val route-id] _]
    (when (and site activity-val)
      (let [routes (get-in site [:activities (keyword activity-val) :routes] [])]
        (if route-id
          (some #(when (= (:id %) route-id)
                   (:itrs-classification? %))
                routes)
          (:itrs-classification? (first routes)))))))

(rf/reg-sub ::route-with-calculated-length
  (fn [[_ lipas-id activity-k route-id]]
    [(rf/subscribe [:lipas.ui.sports-sites.subs/editing-rev lipas-id])
     (rf/subscribe [:lipas.ui.sports-sites.subs/latest-rev lipas-id])
     (rf/subscribe [::routes lipas-id activity-k])])
  (fn [[editing display routes] [_ _lipas-id _activity-k route-id]]
    (when-let [route (first (filter #(= (:id %) route-id) routes))]
                ;; Use editing data if available, otherwise fall back to display data
      (let [sports-site (or editing display)
            geometries (get-in sports-site [:location :geometries])
            filtered-geoms (when (and geometries (:fids route))
                             (update geometries :features
                                     (fn [features]
                                       (filterv #(contains? (:fids route) (:id %)) features))))
            length-km (if (and filtered-geoms (seq (:features filtered-geoms)))
                        (map-utils/calculate-length-km filtered-geoms)
                        0)]
        (assoc route :route-length-km length-km)))))

(rf/reg-sub ::routes-with-calculated-lengths
  (fn [[_ lipas-id activity-k]]
    [(rf/subscribe [:lipas.ui.sports-sites.subs/editing-rev lipas-id])
     (rf/subscribe [:lipas.ui.sports-sites.subs/latest-rev lipas-id])
     (rf/subscribe [::routes lipas-id activity-k])])
  (fn [[editing display routes] _]
    ;; Use editing data if available, otherwise fall back to display data
    (let [sports-site (or editing display)
          geometries (get-in sports-site [:location :geometries])
          all-features (get geometries :features [])]
      (mapv (fn [route]
              (let [route-fids (:fids route)
                    filtered-geoms (when (and geometries route-fids)
                                     (update geometries :features
                                             (fn [features]
                                               (let [feature-ids (set (map :id features))
                                                     fids-set (if (set? route-fids)
                                                                route-fids
                                                                (set route-fids))
                                                     filtered (filterv #(contains? fids-set (:id %)) features)]
                                                 filtered))))
                    length-km (if (and filtered-geoms (seq (:features filtered-geoms)))
                                (map-utils/calculate-length-km filtered-geoms)
                                0)]
                (assoc route :route-length-km length-km)))
            routes))))
