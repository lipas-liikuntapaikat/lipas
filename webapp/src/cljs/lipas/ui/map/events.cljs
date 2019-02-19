(ns lipas.ui.map.events
  (:require
   proj4
   ["ol"]
   [lipas.ui.utils :refer [==>] :as utils]
   [re-frame.core :as re-frame]))

;; Width and height are in meters when using EPSG:3067 projection
(re-frame/reg-event-fx
 ::set-view
 (fn [{:keys [db]} [_ center lonlat zoom extent width height]]
   {:db       (-> db
                  (assoc-in [:map :center] {:lat (aget center 1) :lon (aget center 0)})
                  (assoc-in [:map :center-wgs84] {:lat (aget lonlat 1) :lon (aget lonlat 0)})
                  (assoc-in [:map :zoom] zoom)
                  (assoc-in [:map :extent] extent)
                  (assoc-in [:map :width] width)
                  (assoc-in [:map :height] height))
    :dispatch-n [(when (and extent width)
                   [:lipas.ui.search.events/submit-search])]}))

(re-frame/reg-event-fx
 ::zoom-to-site
 (fn [{:keys [db]} [_ lipas-id width]]
   (let [latest     (get-in db [:sports-sites lipas-id :latest])
         rev        (get-in db [:sports-sites lipas-id :history latest])
         geom       (-> rev :location :geometries :features first :geometry)
         wgs-coords (case (:type geom)
                      "Point"      (-> geom :coordinates)
                      "LineString" (-> geom :coordinates first)
                      "Polygon"    (-> geom :coordinates first first))
         proj       (.get ol.proj "EPSG:3067")
         [lon lat]  (js->clj (ol.proj.fromLonLat (clj->js wgs-coords) proj))
         center     {:lon lon :lat lat}
         zoom       14]
     {:db         (-> db
                      (assoc-in [:map :zoom] zoom)
                      (assoc-in [:map :center] center))
      :dispatch-n [(case width ("xs" "sm") [::toggle-drawer] nil)]})))

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
 ::show-popup
 (fn [db [_ feature]]
   (assoc-in db [:map :popup] feature)))

(defn- get-latest-rev [db lipas-id]
  (let [latest (get-in db [:sports-sites lipas-id :latest])]
    (get-in db [:sports-sites lipas-id :history latest])))

(re-frame/reg-event-db
 ::show-sports-site*
 (fn [db [_ lipas-id]]
   (-> db
       (assoc-in [:map :mode :lipas-id] lipas-id)
       (assoc-in [:map :drawer-open?] true))))

(re-frame/reg-event-fx
 ::show-sports-site
 (fn [db [_ lipas-id]]
   {:dispatch-n
    [(if lipas-id
       (let [params {:lipas-id lipas-id}]
         [:lipas.ui.events/navigate :lipas.ui.routes.map/details-view params])
       [:lipas.ui.events/navigate :lipas.ui.routes.map/map])]}))

(re-frame/reg-event-db
 ::start-editing
 (fn [db [_ lipas-id sub-mode geom-type]]
   (let [site (get-latest-rev db lipas-id)]
     (update-in db [:map :mode] merge {:name      :editing
                                       :lipas-id  lipas-id
                                       :geoms     (utils/->feature site)
                                       :sub-mode  sub-mode
                                       :geom-type geom-type}))))

(re-frame/reg-event-db
 ::stop-editing
 (fn [db [_]]
   (assoc-in db [:map :mode :name] :default)))

(re-frame/reg-event-fx
 ::update-geometries
 (fn [_ [_ lipas-id geoms]]
   (let [path  [:location :geometries]
         geoms (update geoms :features
                       (fn [fs] (map #(dissoc % :properties :id) fs)))]
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
    :dispatch [:lipas.ui.search.events/submit-search]}))

(re-frame/reg-event-fx
 ::finish-adding-geom
 (fn [{:keys [db]} [_ geoms type-code]]
   (let [geoms (update geoms :features
                       (fn [fs] (map #(dissoc % :properties :id) fs)))]
     {:db         (assoc-in db [:map :mode :sub-mode] :finished)
      :dispatch-n [[:lipas.ui.sports-sites.events/init-new-site type-code geoms]]})))

(re-frame/reg-event-db
 ::toggle-drawer
 (fn [db _]
   (update-in db [:map :drawer-open?] not)))

;; Import geoms ;;

(re-frame/reg-event-db
 ::toggle-import-dialog
 (fn [db _]
   (let [close? (-> db :map :import :dialog-open?)]
     (-> db
         (update-in [:map :import :dialog-open?] not)
         (assoc-in [:map :mode :name] (if close? :editing :importing))))))

(re-frame/reg-event-db
 ::select-import-file-encoding
 (fn [db [_ encoding]]
   (assoc-in db [:map :import :selected-encoding] encoding)))

(re-frame/reg-event-db
 ::set-import-candidates
 (fn [db [_ geoJSON]]
   (assoc-in db [:map :import :data] (js->clj geoJSON :keywordize-keys true))))

(re-frame/reg-event-fx
 ::import-geoms-from-shape-file
 (fn [{:keys [db]} [_ files]]
   (let [enc (-> db :map :import :selected-encoding)
         f   (aget files 0)
         cb  (fn [data] (==> [::set-import-candidates data]))]
     (js/shp2geojson.loadshp #js{:url f :encoding enc} cb))
   {:dispatch [::toggle-import-dialog]}))

(re-frame/reg-event-db
 ::toggle-import-item
 (fn [db [_ id]]
   (let [path [:map :import :selected-items]
         selected (set (get-in db path))
         op       (if (contains? selected id) disj conj)]
     (assoc-in db path (op selected id)))))

(re-frame/reg-event-fx
 ::import-geoms
 (fn [{:keys [db]} [_ data]]
   (let [lipas-id (-> db :map :mode :lipas-id)
         fcoll    {:type     "FeatureCollection"
                   :features (into [] (->> data vals (map #(dissoc % :properties))))}]
     {:db         (assoc-in db [:map :mode :geoms] fcoll)
      :dispatch-n [[::update-geometries lipas-id fcoll]]})))
