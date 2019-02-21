(ns lipas.ui.map.events
  (:require
   ["ol"]
   [clojure.string :as string]
   [goog.object :as gobj]
   [goog.string.path :as gpath]
   [lipas.ui.utils :refer [==>] :as utils]
   proj4
   [re-frame.core :as re-frame]))

;; Width and height are in meters when using EPSG:3067 projection
(re-frame/reg-event-fx
 ::set-view
 (fn [{:keys [db]} [_ center lonlat zoom extent width height]]
   (let [center       {:lat (aget center 1) :lon (aget center 0)}
         center-wgs84 {:lat (aget lonlat 1) :lon (aget lonlat 0)}]
     {:db (-> db
              (assoc-in [:map :center] center)
              (assoc-in [:map :center-wgs84] center-wgs84)
              (assoc-in [:map :zoom] zoom)
              (assoc-in [:map :extent] extent)
              (assoc-in [:map :width] width)
              (assoc-in [:map :height] height))
      :dispatch-n
      [(when (and extent width)
         [:lipas.ui.search.events/submit-search])]})))

(re-frame/reg-event-db
 ::fit-to-current-vectors
 (fn [db _]
   (assoc-in db [:map :mode :fit-nonce] (gensym))))

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
 ::continue-editing
 (fn [db _]
   (update-in db [:map :mode] merge {:name :editing :sub-mode :editing})))

(re-frame/reg-event-db
 ::stop-editing
 (fn [db [_]]
   (assoc-in db [:map :mode :name] :default)))

(re-frame/reg-event-fx
 ::update-geometries
 (fn [{:keys [db]} [_ lipas-id geoms]]
   (let [path  [:sports-sites lipas-id :editing :location :geometries]
         geoms (update geoms :features
                       (fn [fs] (map #(dissoc % :properties :id) fs)))]
     {:db (assoc-in db path geoms)})))

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
   (update-in db [:map :import :dialog-open?] not)))

(re-frame/reg-event-db
 ::select-import-file-encoding
 (fn [db [_ encoding]]
   (assoc-in db [:map :import :selected-encoding] encoding)))

(re-frame/reg-event-db
 ::set-import-candidates
 (fn [db [_ geoJSON]]
   (let [fcoll (js->clj geoJSON :keywordize-keys true)
         fs    (->> fcoll
                    :features
                    (filter (comp #{"LineString"} :type :geometry))
                    (reduce
                     (fn [res f]
                       (let [id (gensym)]
                         (assoc res id (assoc-in f [:properties :id] id))))
                     {}))]
     (assoc-in db [:map :import :data] fs))))

(defn- xml->GeoJSON [file ext enc cb]
  (let [reader (js/FileReader.)
        parser (js/DOMParser.)
        cb     (fn [e]
                 (let [text (-> e .-target .-result)
                       dom  (.parseFromString parser text "text/xml")
                       fun  (if (= "gpx" ext) js/toGeoJSON.gpx js/toGeoJSON.kml)]
                   (cb (fun dom))))]
    (set! (.-onload reader) cb)
    (.readAsText reader file enc)))

(re-frame/reg-event-fx
 ::load-shape-file
 (fn [{:keys [db]} [_ files]]
   (let [enc  (-> db :map :import :selected-encoding)
         file (aget files 0)
         ext  (-> file
                  (gobj/get "name" "")
                  gpath/extension
                  string/lower-case)
         cb   (fn [data] (==> [::set-import-candidates data]))]
     (prn ext)
     (if (#{"zip" "kml" "gpx"} ext)
         (do
           (case ext
             "zip"         (js/shp2geojson.loadshp #js{:url file :encoding enc} cb)
             ("kml" "gpx") (xml->GeoJSON file ext enc cb))
           {})
         {:dispatch-n [(let [tr (-> db :translator)]
                         [:lipas.ui.events/set-active-notification
                          {:message  (tr :map.import/unknown-format ext)
                           :success? false}])]}))))

(re-frame/reg-event-db
 ::select-import-items
 (fn [db [_ ids]]
   (assoc-in db [:map :import :selected-items] (set ids))))

(re-frame/reg-event-db
 ::toggle-replace-existing-selection
 (fn [db _]
   (update-in db [:map :import :replace-existing?] not)))

(re-frame/reg-event-fx
 ::import-selected-geoms
 (fn [{:keys [db]} _]
   (let [ids      (-> db :map :import :selected-items)
         replace? (-> db :map :import :replace-existing?)
         data     (select-keys (-> db :map :import :data) ids)
         fcoll    {:type     "FeatureCollection"
                   :features (into [] cat
                                   [(when-not replace?
                                      (-> db :map :mode :geoms :features))
                                    (->> data vals (map #(dissoc % :properties)))])}]
     {:db         (-> db
                      (assoc-in [:map :mode :geoms] fcoll)
                      (assoc-in [:map :mode :sub-mode] :importing))
      :dispatch-n [[::toggle-import-dialog]
                   [::select-import-items #{}]]})))

(re-frame/reg-event-fx
 ::import-selected-geoms-to-new
 (fn [{:keys [db]} _]
   (let [ids      (-> db :map :import :selected-items)
         data     (select-keys (-> db :map :import :data) ids)
         fcoll    {:type     "FeatureCollection"
                   :features (into [] (->> data vals (map #(dissoc % :properties))))}]
     {:db         (assoc-in db [:map :mode :geoms] fcoll)
      :dispatch-n [[::new-geom-drawn fcoll]
                   [::toggle-import-dialog]
                   [::select-import-items #{}]]})))
