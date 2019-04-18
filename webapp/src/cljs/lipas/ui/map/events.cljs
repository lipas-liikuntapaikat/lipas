(ns lipas.ui.map.events
  (:require
   ["ol"]
   [ajax.core :as ajax]
   [clojure.string :as string]
   [goog.object :as gobj]
   [goog.string :as gstring]
   [goog.string.path :as gpath]
   [lipas.ui.utils :refer [==>] :as utils]
   proj4
   [re-frame.core :as re-frame]))

(re-frame/reg-event-db
 ::toggle-drawer
 (fn [db _]
   (update-in db [:map :drawer-open?] not)))

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

;; Map displaying events ;;

(re-frame/reg-event-db
 ::fit-to-current-vectors
 (fn [db _]
   (assoc-in db [:map :mode :fit-nonce] (gensym))))

(defn wgs84->epsg3067 [wgs84-coords]
  (let [proj      (.get js/ol.proj "EPSG:3067")
        [lon lat] (js->clj (ol.proj.fromLonLat (clj->js wgs84-coords) proj))]
    {:lon lon :lat lat}))

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
         center     (wgs84->epsg3067 wgs-coords)
         zoom       14]
     {:db         (-> db
                      (assoc-in [:map :zoom] zoom)
                      (assoc-in [:map :center] center))
      :dispatch-n [(case width ("xs" "sm") [::toggle-drawer] nil)]})))

(re-frame/reg-event-fx
 ::zoom-to-users-position
 (fn [_ _]
   {:lipas.ui.effects/request-geolocation!
    (fn [position]
      (let [lon*    (-> position .-coords .-longitude)
            lat*    (-> position .-coords .-latitude)
            {:keys [lon lat]} (wgs84->epsg3067 [lon* lat*])]
        (when (and lon lat)
          (==> [::set-center lat lon])
          (==> [::set-zoom 12]))))}))

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

(re-frame/reg-event-db
 ::show-sports-site*
 (fn [db [_ lipas-id]]
   (let [drawer-open? (or lipas-id (-> db :screen-size #{"sm" "xs"} boolean not))]
     (-> db
         (assoc-in [:map :mode :lipas-id] lipas-id)
         (assoc-in [:map :drawer-open?] drawer-open?)))))

;; Geom editing events ;;

(defn- get-latest-rev [db lipas-id]
  (let [latest (get-in db [:sports-sites lipas-id :latest])]
    (get-in db [:sports-sites lipas-id :history latest])))

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

(re-frame/reg-event-db
 ::start-adding-geom
 (fn [db [_ geom-type]]
   (-> db
       (update-in [:map :mode] merge {:name      :adding
                                      :geom-type geom-type
                                      :sub-mode  :drawing}))))

(re-frame/reg-event-db
 ::start-deleting-geom
 (fn [db [_ geom-type]]
   (-> db
       (update-in [:map :mode] merge {:name      :adding
                                      :geom-type geom-type
                                      :sub-mode  :deleting}))))

(re-frame/reg-event-db
 ::stop-deleting-geom
 (fn [db [_ geom-type]]
   (-> db
       (update-in [:map :mode] merge {:name      :adding
                                      :geom-type geom-type
                                      :sub-mode  :editing}))))

(re-frame/reg-event-fx
 ::update-geometries
 (fn [{:keys [db]} [_ lipas-id geoms]]
   (let [path  [:sports-sites lipas-id :editing :location :geometries]
         geoms (update geoms :features
                       (fn [fs] (map #(dissoc % :properties :id) fs)))]
     {:db (assoc-in db path geoms)})))

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
 ::confirm-remove-segment
 (fn [{:keys [db]} [_ callback]]
   (let [tr        (-> db :translator)
         geom-type (-> db :map :mode :geom-type)]
     {:dispatch
      [:lipas.ui.events/confirm (tr :map/confirm-remove geom-type) callback]})))

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

;;; Higher order events ;;;

(re-frame/reg-event-fx
 ::show-sports-site
 (fn [db [_ lipas-id]]
   {:dispatch-n
    [(if lipas-id
       (let [params {:lipas-id lipas-id}]
         [:lipas.ui.events/navigate :lipas.ui.routes.map/details-view params])
       [:lipas.ui.events/navigate :lipas.ui.routes.map/map])]}))

(re-frame/reg-event-fx
 ::edit-site
 (fn [_ [_ lipas-id geom-type]]
   {:dispatch-n
    [[:lipas.ui.sports-sites.events/edit-site lipas-id]
     ;;[::zoom-to-site lipas-id]
     [::start-editing lipas-id :editing geom-type]]}))

(re-frame/reg-event-fx
 ::save-edits
 (fn [_ [_ lipas-id]]
   {:dispatch-n
    [[:lipas.ui.sports-sites.events/save-edits lipas-id]
     [::stop-editing]]}))

(re-frame/reg-event-fx
 ::discard-edits
 (fn [{:keys [db]} [_ lipas-id]]
   (let [tr (-> db :translator)]
     {:dispatch
      [:lipas.ui.events/confirm
       (tr :confirm/discard-changes?)
       (fn []
         (==> [:lipas.ui.sports-sites.events/discard-edits lipas-id])
         (==> [::stop-editing]))]})))

(re-frame/reg-event-fx
 ::delete-site
 (fn [_]
   {:dispatch-n
    [[:lipas.ui.sports-sites.events/toggle-delete-dialog]]}))

(re-frame/reg-event-fx
 ::start-adding-new-site
 (fn [{:keys [db]} [_]]
   {:db         (assoc-in db [:map :mode] {:name :default}) ;; cleanup
    :dispatch-n [[:lipas.ui.sports-sites.events/start-adding-new-site]]}))

(re-frame/reg-event-fx
 ::discard-new-site
 (fn [{:keys [db]} _]
   (let [tr (-> db :translator)]
     {:dispatch
      [:lipas.ui.events/confirm
       (tr :confirm/discard-changes?)
       (fn []
         (==> [:lipas.ui.sports-sites.events/discard-new-site])
         (==> [::discard-drawing]))]})))

(re-frame/reg-event-fx
 ::save-new-site
 (fn [_ [_ data]]
   {:dispatch [:lipas.ui.sports-sites.events/commit-rev data]}))

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

(defn parse-dom [text]
  (let [parser (js/DOMParser.)]
    (.parseFromString parser text "text/xml")))

(defn- text->geoJSON [{:keys [file ext enc cb]}]
  (let [reader (js/FileReader.)
        cb     (fn [e]
                 (let [text   (-> e .-target .-result)
                       parsed (condp = ext
                                "json" (js/JSON.parse text)
                                "kml"  (-> text parse-dom js/toGeoJSON.kml)
                                "gpx"  (-> text parse-dom js/toGeoJSON.gpx))]
                   (cb parsed)))]
    (set! (.-onload reader) cb)
    (.readAsText reader file enc)
    {}))

(defmulti file->geoJSON :ext)

(defmethod file->geoJSON "zip" [{:keys [file enc cb]}]
  (js/shp2geojson.loadshp #js{:url file :encoding enc} cb))

(defmethod file->geoJSON "gpx" [params] (text->geoJSON params))
(defmethod file->geoJSON "kml" [params] (text->geoJSON params))
(defmethod file->geoJSON "json" [params] (text->geoJSON params))
(defmethod file->geoJSON :default [params] {:unknown (:ext params)})

(defn parse-ext [file]
  (-> file
      (gobj/get "name" "")
      gpath/extension
      string/lower-case))

(re-frame/reg-event-fx
 ::load-geoms-from-file
 (fn [{:keys [db]} [_ files]]
   (let [file   (aget files 0)
         params {:enc  (-> db :map :import :selected-encoding)
                 :file file
                 :ext  (parse-ext file)
                 :cb   (fn [data] (==> [::set-import-candidates data]))}]

     (if-let [ext (:unknown (file->geoJSON params))]
       {:dispatch-n [(let [tr (-> db :translator)]
                       [:lipas.ui.events/set-active-notification
                        {:message  (tr :map.import/unknown-format ext)
                         :success? false}])]}
       {}))))

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

(defn- add-gpx-props [{:keys [locale types cities site]} feature]
  (let [city-code (-> site :location :city :city-code)
        type-code (-> site :type :type-code)
        props     {:name        (-> site :name)
                   :type        (get-in types [type-code :name locale])
                   :city        (get-in cities [city-code :name locale])}]
    (assoc feature :properties props)))

(re-frame/reg-event-fx
 ::download-gpx
 (fn [{:keys [db]} [_ lipas-id]]
   (let [locale  (-> db :translator (apply []))
         latest  (get-in db [:sports-sites lipas-id :latest])
         site    (get-in db [:sports-sites lipas-id :history latest])
         data    {:site   site
                  :cities (-> db :cities)
                  :types  (-> db :types)
                  :locale locale}
         fname   (gstring/urlEncode (str (:name site) ".gpx"))
         xml-str (-> site :location :geometries
                     (update :features #(mapv (partial add-gpx-props data) %))
                     clj->js
                     (js/togpx #js{:creator "LIPAS"}))]
     {:lipas.ui.effects/save-as! {:blob (js/Blob. #js[xml-str]) :filename fname}})))

;; Address search ;;

(re-frame/reg-event-db
 ::toggle-address-search-dialog
 (fn [db _]
   (-> db
       (update-in [:map :address-search :dialog-open?] not)
       (assoc-in [:map :address-search :keyword] "")
      (assoc-in [:map :address-search :results] []))))

(re-frame/reg-event-db
 ::clear-address-search-results
 (fn [db _]
   (assoc-in db [:map :address-search :results] [])))

(re-frame/reg-event-fx
 ::update-address-search-keyword
 (fn [{:keys [db]} [_ s]]
   {:db         (assoc-in db [:map :address-search :keyword] s)
    :dispatch-n [[::search-address s]]}))

;; https://www.digitransit.fi/en/developers/apis/2-geocoding-api/autocomplete/
(re-frame/reg-event-fx
 ::search-address
 (fn [{:keys [db]} [_ s]]
   (let [base-url (-> db :map :address-search :base-url)]
     (if (not-empty s)
       {:http-xhrio
        {:method          :get
         :uri             (str base-url "/autocomplete?"
                               "sources=oa,osm"
                               "&text=" s)
         :response-format (ajax/json-response-format {:keywords? true})
         :on-success      [::address-search-success]
         :on-failure      [::address-search-failure]}}
       {:dispatch [::clear-address-search-results]}))))

(re-frame/reg-event-fx
 ::address-search-success
 (fn [{:keys [db]} [_ resp]]
   {:db (assoc-in db [:map :address-search :results] resp)}))

(re-frame/reg-event-fx
 ::address-search-failure
 (fn [{:keys [db]} [_ error]]
   (let [tr (:translator db)]
     {:db       (assoc-in db [:errors :address-search (utils/timestamp)] error)
      :dispatch [:lipas.ui.events/set-active-notification
                 {:message  (tr :notifications/get-failed)
                  :success? false}]})))

(re-frame/reg-event-fx
 ::show-address
 (fn [{:keys [db]} [_ {:keys [label geometry]}]]
   (let [{:keys [lon lat]} (-> geometry :coordinates wgs84->epsg3067)

         feature {:type "Feature" :geometry geometry :properties {:name label}}]
     {:db (assoc-in db [:map :mode :address] feature)
      :dispatch-n
      [[::set-center lat lon]
       [::set-zoom 14]
       [::toggle-address-search-dialog]]})))

(re-frame/reg-event-db
 ::hide-address
 (fn [db _]
   (assoc-in db [:map :mode :address] nil)))
