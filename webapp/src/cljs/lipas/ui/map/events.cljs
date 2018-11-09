(ns lipas.ui.map.events
  (:require proj4
            ["ol"]
            [re-frame.core :as re-frame]))

(re-frame/reg-event-db
 ::set-view
 (fn [db [_ lat lon zoom]]
   (-> db
       (assoc-in [:map :center] {:lat lat :lon lon})
       (assoc-in [:map :zoom] zoom))))

(re-frame/reg-event-fx
 ::zoom-to-site
 (fn [{:keys [db]} [_ lipas-id]]
   (let [latest     (get-in db [:sports-sites lipas-id :latest])
         rev        (get-in db [:sports-sites lipas-id :history latest])
         geom       (-> rev :location :geometries :features first :geometry)
         _          (prn "GEOM" geom)
         wgs-coords (case (:type geom)
                      "Point"      (-> geom :coordinates)
                      "LineString" (-> geom :coordinates first)
                      "Polygon"    (-> geom :coordinates first first))
         proj       (.get ol.proj "EPSG:3067")
         [lon lat]  (js->clj (ol.proj.fromLonLat (clj->js wgs-coords) proj))
         zoom       14]
     {:dispatch [::set-view lat lon zoom]})))

(re-frame/reg-event-db
 ::set-center
 (fn [db [_ lat lon]]
   (assoc-in db [:map :center] {:lat lat :lon lon})))

(re-frame/reg-event-db
 ::set-zoom
 (fn [db [_ zoom]]
   (assoc-in db [:map :zoom] zoom)))

(re-frame/reg-event-db
 ::select-basemap
 (fn [db [_ basemap]]
   (assoc-in db [:map :basemap] basemap)))

(re-frame/reg-event-db
 ::show-types
 (fn [db [_ type-codes]]
   (assoc-in db [:map :filters :type-codes] type-codes)))

;; This is a hack to force vector layer re-draw
(re-frame/reg-event-fx
 ::refresh-filters
 (fn [{:keys [db]} _]
   (let [selected-types (-> db :map :filters :type-codes)]
     {:dispatch       [::show-types nil]
      :dispatch-later [{:ms 100 :dispatch [::show-types selected-types]}]})))

(re-frame/reg-event-db
 ::show-popup
 (fn [db [_ feature]]
   (assoc-in db [:map :popup] feature)))

(re-frame/reg-event-db
 ::show-sports-site
 (fn [db [_ lipas-id]]
   (assoc-in db [:map :mode :lipas-id] lipas-id)))

(re-frame/reg-event-db
 ::start-editing
 (fn [db [_ lipas-id sub-mode geom-type]]
   (update-in db [:map :mode] merge {:name      :editing
                                     :lipas-id  lipas-id
                                     :sub-mode  sub-mode
                                     :geom-type geom-type})))

(re-frame/reg-event-db
 ::stop-editing
 (fn [db [_]]
   (assoc-in db [:map :mode :name] :default)))

(re-frame/reg-event-fx
 ::update-geometries
 (fn [_ [_ lipas-id geoms]]
   (let [path  [:location :geometries]
         geoms (-> geoms
                   (as-> $ (update $ :features
                                   (fn [fs] (map #(dissoc % :properties) fs)))))]
     {:dispatch [:lipas.ui.sports-sites.events/edit-field lipas-id path geoms]})))

(re-frame/reg-event-db
 ::start-adding-geom
 (fn [db [_ geom-type]]
   (-> db
       (update-in [:map :mode] merge {:name      :adding
                                      :geom-type geom-type
                                      :sub-mode  :drawing}))))

(re-frame/reg-event-db
 ::new-geom-drawn
 (fn [db [_ geom]]
   (update-in db [:map :mode] merge {:name     :adding
                                     :geom     geom
                                     :sub-mode :editing})))

(re-frame/reg-event-db
 ::update-new-geom
 (fn [db [_ geom]]
   (assoc-in db [:map :mode :geom] geom)))

(re-frame/reg-event-fx
 ::discard-drawing
 (fn [{:keys [db]} _]
   {:db       (assoc-in db [:map :mode] {:name :default})
    :dispatch [::refresh-filters]}))

(re-frame/reg-event-fx
 ::finish-adding-geom
 (fn [{:keys [db]} [_ geoms type-code]]
   {:db         (assoc-in db [:map :mode :sub-mode] :finished)
    :dispatch-n [[:lipas.ui.sports-sites.events/init-new-site type-code geoms]]}))
