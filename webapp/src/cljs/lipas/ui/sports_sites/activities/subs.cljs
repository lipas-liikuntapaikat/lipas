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

(defn- bearing->compass
  "Convert bearing in degrees (0-360) to 8-direction compass abbreviation."
  [bearing]
  (let [dirs ["N" "NE" "E" "SE" "S" "SW" "W" "NW"]
        idx  (Math/round (/ (mod bearing 360) 45))]
    (nth dirs (mod idx 8))))

(defn- compute-compass-direction
  "Compute compass direction from first-coord to last-coord.
   Coordinates are WGS84 [lon, lat]."
  [[lon1 lat1] [lon2 lat2]]
  (when (and lon1 lat1 lon2 lat2)
    (let [dx (- lon2 lon1)
          dy (- lat2 lat1)
          bearing (mod (- 90 (/ (* (Math/atan2 dy dx) 180) Math/PI)) 360)]
      (bearing->compass bearing))))

(defn- compute-segment-details
  "Compute per-segment details: length, connectivity, compass direction."
  [segments features-by-id]
  (let [resolved (resolve-segments segments features-by-id)
        details  (mapv (fn [idx {:keys [fid reversed?]} feature]
                         (let [coords (get-in feature [:geometry :coordinates])
                               fcoll  {:type "FeatureCollection" :features [feature]}
                               length (map-utils/calculate-length-km fcoll)
                               first-c (first coords)
                               last-c  (last coords)]
                           {:fid              fid
                            :reversed?        (boolean reversed?)
                            :length-km        length
                            :first-coord      first-c
                            :last-coord       last-c
                            :compass-direction (compute-compass-direction first-c last-c)}))
                       (range) segments resolved)]
    ;; Add connectivity and fix-suggestion info
    (mapv (fn [idx detail]
            (let [next-detail (get details (inc idx))]
              (if next-detail
                (let [connected? (boolean (coords-close? (:last-coord detail) (:first-coord next-detail)))]
                  (cond-> (assoc detail :connected-to-next? connected?)
                    ;; When disconnected, check if reversing current or next segment fixes it
                    (not connected?)
                    (assoc :fix-suggestion
                           (cond
                             ;; Reversing current segment: its first-coord connects to next's first-coord
                             (coords-close? (:first-coord detail) (:first-coord next-detail))
                             {:action :reverse :target-idx idx}
                             ;; Reversing next segment: current's last-coord connects to next's last-coord
                             (coords-close? (:last-coord detail) (:last-coord next-detail))
                             {:action :reverse :target-idx (inc idx)}
                             :else nil))))
                (assoc detail :connected-to-next? true))))
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
