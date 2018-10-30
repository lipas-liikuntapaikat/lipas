(ns lipas.ui.map.events
  (:require proj4
            "ol"
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
         wgs-coords (-> rev :location :geometries :features first
                        :geometry :coordinates clj->js)
         proj       (.get ol.proj "EPSG:3067")
         [lon lat]  (js->clj (ol.proj.fromLonLat wgs-coords proj))
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
 ::toggle-filter
 (fn [db [_ filter]]
   (update-in db [:map :filters filter] not)))

(re-frame/reg-event-db
 ::show-popup
 (fn [db [_ feature]]
   (assoc-in db [:map :popup] feature)))

(re-frame/reg-event-db
 ::show-sports-site
 (fn [db [_ lipas-id]]
   (assoc-in db [:map :sports-site] lipas-id)))

(re-frame/reg-event-db
 ::start-editing
 (fn [db [_ lipas-id]]
   (assoc-in db [:map :editing :lipas-id] lipas-id)))

(re-frame/reg-event-db
 ::stop-editing
 (fn [db [_]]
   (assoc-in db [:map :editing :lipas-id] nil)))

(re-frame/reg-event-fx
 ::update-geometry
 (fn [_ [_ lipas-id geoJSON]]
   (let [path [:location :geometries]
         geom (-> geoJSON
                  (js->clj :keywordize-keys true)
                  (as-> $ (update $ :features
                                  (fn [fs] (map #(dissoc % :properties) fs)))))]
     {:dispatch [:lipas.ui.sports-sites.events/edit-field lipas-id path geom]})))

(re-frame/reg-event-db
 ::start-drawing
 (fn [db [_ geom-type]]
   (assoc-in db [:map :drawing :geom-type] geom-type)))

(re-frame/reg-event-fx
 ::stop-drawing
 (fn [{:keys [db]} [_ geoJSON]]
   (let [geoms     (js->clj geoJSON :keywordize-keys true)
         type-code (-> db :new-sports-site :type)]
     {:db (-> db
              (assoc-in [:map :drawing] nil))
      :dispatch-n
      [[:lipas.ui.sports-sites.events/init-new-site type-code geoms]]})))
