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

(defn- ensure-segments
  "Auto-migrate: if route has fids but no segments, generate segments in feature-array order."
  [route all-features]
  (if (seq (:segments route))
    route
    (let [fids     (set (:fids route))
          segments (->> all-features
                        (filter #(contains? fids (:id %)))
                        (mapv (fn [f] {:fid (:id f) :reversed? false})))]
      (assoc route :segments segments))))

(defn- resolve-segments
  "Resolve segments to ordered features, respecting reversed? flag."
  [segments features-by-id]
  (->> segments
       (keep (fn [{:keys [fid reversed?]}]
               (when-let [f (get features-by-id fid)]
                 (if reversed?
                   (update-in f [:geometry :coordinates] #(vec (reverse %)))
                   f))))
       vec))

(defn- coords-close?
  "Check if two coordinate points are approximately equal (within ~1m)."
  [[x1 y1] [x2 y2]]
  (when (and x1 y1 x2 y2)
    (let [threshold 0.00001] ;; ~1m at equator
      (and (< (abs (- x1 x2)) threshold)
           (< (abs (- y1 y2)) threshold)))))

(defn- compute-segment-details
  "Compute per-segment details: length, connectivity."
  [segments features-by-id]
  (let [resolved (resolve-segments segments features-by-id)
        details  (mapv (fn [idx {:keys [fid reversed?]} feature]
                         (let [coords (get-in feature [:geometry :coordinates])
                               fcoll  {:type "FeatureCollection" :features [feature]}
                               length (map-utils/calculate-length-km fcoll)]
                           {:fid       fid
                            :reversed? (boolean reversed?)
                            :length-km length
                            :first-coord (first coords)
                            :last-coord  (last coords)}))
                       (range) segments resolved)]
    ;; Add connectivity info
    (mapv (fn [idx detail]
            (let [next-detail (get details (inc idx))]
              (assoc detail :connected-to-next?
                     (if next-detail
                       (boolean (coords-close? (:last-coord detail) (:first-coord next-detail)))
                       true))))
          (range) details)))

(rf/reg-sub ::routes
  (fn [[_ lipas-id _]]
    [(rf/subscribe [:lipas.ui.sports-sites.subs/editing-rev lipas-id])])
  (fn [[edit-data] [_ _lipas-id activity-k]]
    (let [all-features (get-in edit-data [:location :geometries :features] [])
          features-by-id (into {} (map (juxt :id identity)) all-features)
          routes (get-in edit-data [:activities activity-k :routes] [])]

     ;; Enrich routes with segment details, lengths, and elevation stats
      (for [route routes]
        (let [route    (ensure-segments route all-features)
              segments (:segments route)
              details  (compute-segment-details segments features-by-id)
              ordered-features (resolve-segments segments features-by-id)
              fcoll    {:type "FeatureCollection" :features ordered-features}]
          (-> route
              (assoc :segment-details details)
              (assoc :route-length (map-utils/calculate-length-km fcoll))
              (assoc :elevation-stats (map-utils/calculate-elevation-stats fcoll))))))))

(rf/reg-sub ::selected-route-id
  :<- [::activities]
  (fn [activities _]
    (:selected-route-id activities)))

(rf/reg-sub ::lipas-prop-value
  :<- [:lipas.ui.map.subs/selected-sports-site]
  (fn [site-data  [_ prop-k read-only?]]
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
