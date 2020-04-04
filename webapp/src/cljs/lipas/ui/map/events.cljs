(ns lipas.ui.map.events
  (:require
   ["ol" :as ol]
   [ajax.core :as ajax]
   [cemerick.url :as url]
   [clojure.string :as string]
   [goog.object :as gobj]
   [goog.string :as gstring]
   [goog.string.path :as gpath]
   [lipas.ui.map.utils :as map-utils]
   [lipas.ui.utils :refer [==>] :as utils]
   proj4
   [re-frame.core :as re-frame]))

(defn wgs84->epsg3067 [wgs84-coords]
  (let [proj      (.get ol/proj "EPSG:3067")
        [lon lat] (js->clj (ol/proj.fromLonLat (clj->js wgs84-coords) proj))]
    {:lon lon :lat lat}))

(defn epsg3067->wgs84-fast [wgs84-coords]
  (let [proj      (.get ol/proj "EPSG:3067")]
    (ol/proj.toLonLat wgs84-coords proj)))

(defn top-left [extent]
  (epsg3067->wgs84-fast
   #js[(aget extent 0) (aget extent 3)]))

(defn bottom-right [extent]
  (epsg3067->wgs84-fast
   #js[(aget extent 2) (aget extent 1)]))

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
              (assoc-in [:map :top-left-wgs84] (top-left extent))
              (assoc-in [:map :bottom-right-wgs84] (bottom-right extent))
              (assoc-in [:map :width] width)
              (assoc-in [:map :height] height))
      :dispatch-n
      [(when (and extent width) [:lipas.ui.search.events/submit-search])]})))

;; Map displaying events ;;

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
 ::toggle-overlay
 (fn [db [_ k]]
   (let [op (if (-> db :map :selected-overlays (contains? k))
              disj
              conj)]
     (update-in db [:map :selected-overlays] op k))))

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
  (or (get-in db [:sports-sites lipas-id :editing])
      (let [latest (get-in db [:sports-sites lipas-id :latest])]
        (get-in db [:sports-sites lipas-id :history latest]))))

(re-frame/reg-event-fx
 ::start-editing
 (fn [{:keys [db]} [_ lipas-id sub-mode geom-type]]
   (let [site  (get-latest-rev db lipas-id)
         geoms (utils/->feature site)]
     {:db         (update-in db [:map :mode] merge {:name      :editing
                                                    :lipas-id  lipas-id
                                                    :geoms     geoms
                                                    :sub-mode  sub-mode
                                                    :geom-type geom-type})
      :dispatch-n [[::show-problems (map-utils/find-problems geoms)]]})))

(re-frame/reg-event-fx
 ::continue-editing
 (fn [{:keys [db]} _]
   (let [geoms (-> db :map :mode :geoms)]
     {:db (update-in db [:map :mode] merge {:name :editing :sub-mode :editing})
      :dispatch-n
      [[::show-problems (map-utils/find-problems geoms)]]})))

(re-frame/reg-event-fx
 ::stop-editing
 (fn [{:keys [db]} [_]]
   {:db       (assoc-in db [:map :mode :name] :default)
    :dispatch [::clear-undo-redo]}))

(re-frame/reg-event-db
 ::start-adding-geom
 (fn [db [_ geom-type]]
   (-> db
       (update-in [:map :mode] merge {:name      :adding
                                      :temp      {}
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

(re-frame/reg-event-db
 ::start-splitting-geom
 (fn [db [_ geom-type]]
   (-> db
       (update-in [:map :mode] merge {:name      :adding
                                      :geom-type geom-type
                                      :sub-mode  :splitting}))))

(re-frame/reg-event-db
 ::stop-splitting-geom
 (fn [db [_ geom-type]]
   (-> db
       (update-in [:map :mode] merge {:name      :adding
                                      :geom-type geom-type
                                      :sub-mode  :editing}))))


(re-frame/reg-event-fx
 ::undo
 (fn [{:keys [db]} [_ lipas-id]]
   (let [path       [:map :mode :geoms]
         curr-geoms (get-in db path)
         undo-stack (get-in db [:map :temp lipas-id :undo-stack])]
     {:db (-> db
              (assoc-in [:map :mode :sub-mode] :undo)
              (assoc-in [:map :mode :undo-geoms] (peek undo-stack))
              (update-in [:map :temp lipas-id :undo-stack] pop)
              (update-in [:map :temp lipas-id :redo-stack] conj curr-geoms))})))

(re-frame/reg-event-fx
 ::redo
 (fn [{:keys [db]} [_ lipas-id]]
   (let [path       [:map :mode :geoms]
         curr-geoms (get-in db path)
         redo-stack (get-in db [:map :temp lipas-id :redo-stack])]
     {:db (-> db
              (assoc-in [:map :mode :sub-mode] :undo)
              (assoc-in [:map :mode :undo-geoms] (peek redo-stack))
              (update-in [:map :temp lipas-id :redo-stack] pop)
              (update-in [:map :temp lipas-id :undo-stack] conj curr-geoms))})))

;; Callback from OpenLayers
(re-frame/reg-event-fx
 ::undo-done
 (fn [{:keys [db]} [_ lipas-id geoms]]
   (let [path [:sports-sites lipas-id :editing :location :geometries]]
     {:db         (cond-> db
                    true     (update-in [:map :mode] merge {:geoms geoms :sub-mode :editing})
                    lipas-id (assoc-in path geoms))
      :dispatch-n [[::show-problems (map-utils/find-problems geoms)]]})))

(re-frame/reg-event-db
 ::clear-undo-redo
 (fn [db _]
   (assoc-in db [:map :temp] {})))

(re-frame/reg-event-fx
 ::update-geometries
 (fn [{:keys [db]} [_ lipas-id geoms]]

   (let [path      [:sports-sites lipas-id :editing :location :geometries]
         old-geoms (-> db :map :mode :geoms)
         new-geoms (update geoms :features
                           (fn [fs] (map #(dissoc % :properties :id) fs)))]
     {:db (-> db
              (update-in [:map :temp lipas-id :undo-stack] conj old-geoms)
              (assoc-in [:map :mode :geoms] geoms)
              (assoc-in [:map :temp lipas-id :redo-stack] '())
              (assoc-in path new-geoms))
      :dispatch-n
      [[::show-problems (map-utils/find-problems new-geoms)]]})))

(re-frame/reg-event-fx
 ::new-geom-drawn
 (fn [{:keys [db]} [_ geoms]]
   (let [curr-geoms (-> db :map :mode :geoms)]
     {:db (cond-> db
            curr-geoms (update-in [:map :temp "new" :undo-stack] conj curr-geoms)
            true       (assoc-in [:map :temp "new" :redo-stack] '())
            true       (update-in [:map :mode] merge {:name     :adding
                                                      :geoms     geoms
                                                      :sub-mode :editing}))

      :dispatch-n [[::show-problems (map-utils/find-problems geoms)]]})))

(re-frame/reg-event-fx
 ::update-new-geom
 (fn [{:keys [db]} [_ geoms]]
   (let [curr-geoms (-> db :map :mode :geoms)]
     {:db         (-> db
                      (assoc-in [:map :mode :geoms] geoms)
                      (assoc-in [:map :temp "new" :redo-stack] '())
                      (update-in [:map :temp "new" :undo-stack] conj curr-geoms))
      :dispatch-n [[::show-problems (map-utils/find-problems geoms)]]})))

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

(re-frame/reg-event-db
 ::open-more-tools-menu
 (fn [db [_ el]]
   (assoc-in db [:map :more-tools-menu :anchor] el)))

(re-frame/reg-event-db
 ::close-more-tools-menu
 (fn [db _]
   (assoc-in db [:map :more-tools-menu :anchor] nil)))

;;; Map events ;;;

(re-frame/reg-event-fx
 ::map-clicked
 (fn [_]
   {:dispatch [::hide-address]}))

(re-frame/reg-event-fx
 ::sports-site-selected
 (fn [{:keys [db]} [_ _ lipas-id]]
   (let [sub-mode (-> db :map :mode :sub-mode)]
     (if (= sub-mode :population)
       {:dispatch [::show-sports-site-population lipas-id]}
       {:dispatch [::show-sports-site lipas-id]}))))

;;; Higher order events ;;;

(re-frame/reg-event-fx
 ::show-sports-site
 (fn [_ [_ lipas-id]]
   {:dispatch-n
    (if lipas-id
      (let [params {:lipas-id lipas-id}]
        [[:lipas.ui.events/navigate :lipas.ui.routes.map/details-view params]])
      [[:lipas.ui.events/navigate :lipas.ui.routes.map/map]])}))

(re-frame/reg-event-fx
 ::edit-site
 (fn [_ [_ lipas-id geom-type]]
   {:dispatch-n
    [[:lipas.ui.sports-sites.events/edit-site lipas-id]
     ;;[::zoom-to-site lipas-id]
     [::clear-undo-redo]
     [::start-editing lipas-id :editing geom-type]]}))

(defn- on-success-default [{:keys [lipas-id]}]
  [[::stop-editing]
   [:lipas.ui.search.events/submit-search]
   [:lipas.ui.map.events/show-sports-site lipas-id]])

(defn- on-failure-default [{:keys [lipas-id]}]
  [[:lipas.ui.map.events/show-sports-site lipas-id]])

(re-frame/reg-event-fx
 ::save-edits
 (fn [_ [_ lipas-id]]
   (let [on-success (partial on-success-default {:lipas-id lipas-id})
         on-failure (partial on-failure-default {:lipas-id lipas-id})]
     {:dispatch-n
      ;; We "unselect" sports-site while saving to make the
      ;; map "forget" and focus on updated entries once they're saved.
      ;; Some more elegant solution could be possibly implemented in
      ;; the future.
      [[:lipas.ui.map.events/show-sports-site nil]
       [:lipas.ui.sports-sites.events/save-edits lipas-id on-success on-failure]]})))

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
 ::resurrect
 (fn [{:keys [db]} [_ lipas-id]]
   (let [tr (:translator db)]
    {:dispatch
     [:lipas.ui.events/confirm
      (tr :confirm/resurrect?)
      (fn []
        (==> [:lipas.ui.sports-sites.events/resurrect
              lipas-id
              on-success-default on-failure-default]))]})))

(re-frame/reg-event-fx
 ::start-adding-new-site
 (fn [{:keys [db]} [_]]
   {:db         (assoc-in db [:map :mode] {:name :default}) ;; cleanup
    :dispatch-n [[:lipas.ui.search.events/set-results-view :list]
                 [:lipas.ui.sports-sites.events/start-adding-new-site]]}))

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

(defn- on-success-new [{:keys [lipas-id]}]
  [[:lipas.ui.sports-sites.events/discard-new-site]
   [:lipas.ui.map.events/stop-editing]
   [:lipas.ui.map.events/show-sports-site lipas-id]
   [:lipas.ui.search.events/submit-search]
   [:lipas.ui.login.events/refresh-login]])

(re-frame/reg-event-fx
 ::save-new-site
 (fn [{:keys [db]} [_ data]]
   (let [draft? false
         type   (get-in db [:sports-sites :types (-> data :type :type-code)])
         data   (-> data
                    (assoc :event-date (utils/timestamp))
                    (update :properties #(select-keys % (-> type :props keys))))]
     {:dispatch
      [:lipas.ui.sports-sites.events/commit-rev data draft? on-success-new]})))

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
     (-> db
         (assoc-in [:map :import :data] fs)
         (assoc-in [:map :import :batch-id] (gensym))))))

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
                                    (->> data vals (map #(dissoc % :properties)))])}
         fcoll    (map-utils/strip-z fcoll)]
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
         fcoll    (map-utils/strip-z
                   {:type     "FeatureCollection"
                    :features (into [] (->> data vals (map #(dissoc % :properties))))})]
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

(defn- problems->fcoll [tr {:keys [intersections kinks]}]
  (let [kfs (-> kinks
                :features
                (->> (map #(assoc-in % [:properties :name] (tr :map/kink))))
                not-empty)
        ifs (-> intersections
                :features
                (->> (map #(assoc-in % [:properties :name] (tr :map/intersection))))
                not-empty)]
    {:type     "FeatureCollection"
     :features (into [] cat [kfs ifs])}))

(re-frame/reg-event-db
 ::show-problems
 (fn [db [_ problems]]
   (let [tr (-> db :translator)]
     (assoc-in db [:map :mode :problems] {:data  (problems->fcoll tr problems)
                                          :show? true}))))

(defn- geom-type [fcoll]
  (-> fcoll :features first :geometry :type))

(re-frame/reg-event-fx
 ::duplicate-sports-site
 (fn [{:keys [db]} [_ lipas-id]]
   (let [ts     (get-in db [:sports-sites lipas-id :latest])
         latest (get-in db [:sports-sites lipas-id :history ts])
         geoms  (get-in latest [:location :geometries])
         geoms  (if (= "Point" (geom-type geoms))
                  ;; Shift point by ~11m so it doesn't overlap 1:1
                  ;; with the original point.
                  (update-in geoms [:features 0 :geometry :coordinates 0] + 0.0001)
                  geoms)]
     {:dispatch-n
      [[:lipas.ui.map.events/show-sports-site nil]
       [::start-adding-new-site]
       [::new-geom-drawn geoms]
       [:lipas.ui.sports-sites.events/duplicate latest]]})))

;; Population events ;;

(re-frame/reg-event-fx
 ::show-population
 (fn [{:keys [db]} _]
   {:db (-> db
            (assoc-in  [:map :mode :name] :default)
            (assoc-in  [:map :mode :sub-mode] :population))}))

(re-frame/reg-event-fx
 ::hide-population
 (fn [{:keys [db]} _]
   {:db (-> db
            (assoc-in [:map :mode :name] :default)
            (assoc-in [:map :population :data] nil)
            (assoc-in [:map :population :selected] nil)
            (assoc-in [:map :mode :population] nil)
            (update-in  [:map :mode] dissoc :sub-mode))}))

(defn- calc-buffered-bbox [fcoll buffer]
  (let [fs     (-> fcoll clj->js map-utils/->ol-features)
        extent (-> fs (aget 0) .getGeometry .getExtent)]
    (.forEach fs (fn [f']
                   (js/ol.extent.extend extent (-> f' .getGeometry .getExtent))))
    (js/ol.extent.buffer extent buffer)))

(defn- resolve-bbox [db]
  (let [geoms (-> db :map :mode :population :geoms)]
    (if geoms
      (calc-buffered-bbox geoms 15000) ; 15km buffer
      (-> db :map :extent))))

(re-frame/reg-event-fx
 ::get-population
 (fn [{:keys [db]} [_ cb]]
   (let [bbox     (resolve-bbox db)
         base-url "/tilastokeskus/geoserver/vaestoruutu/ows?"
         params   {:service      "WFS"
                   :version      "1.0.0"
                   :request      "GetFeature"
                   :typeName     "vaestoruutu:vaki2018_1km"
                   :outputFormat "application/json"
                   ;;:srsName      "EPSG:3067"
                   :srsName      "EPSG:4326"
                   :bbox         bbox}]
     {:http-xhrio
      {:method          :get
       :uri             (-> base-url url/url (assoc :query params) str (subs 3))
       :response-format (ajax/raw-response-format)
       :on-success      [::get-population-success cb]
       :on-failure      [::get-population-failure]}})))

(re-frame/reg-event-fx
 ::get-population-success
 (fn [{:keys [db]} [_ cb resp]]
   {:db         (assoc-in db [:map :population :data] (js/JSON.parse resp))
    :dispatch-n (if cb (cb resp) [])}))

(re-frame/reg-event-fx
 ::get-population-failure
 (fn [{:keys [db]} [_ error]]
   (let [tr (:translator db)]
     {:db       (assoc-in db [:errors :population (utils/timestamp)] error)
      :dispatch [:lipas.ui.events/set-active-notification
                 {:message  (tr :notifications/get-failed)
                  :success? false}]})))

(re-frame/reg-event-fx
 ::set-selected-population-grid
 (fn [{:keys [db]} [_ fcoll]]
   {:db (assoc-in db [:map :population :selected] fcoll)}))

(re-frame/reg-event-fx
 ::unselect-population
 (fn [{:keys [db]} _]
   (if (-> db :map :mode :population :geoms)
     {:db (-> db
              (assoc-in [:map :population :selected] nil)
              (assoc-in [:map :population :data] nil)
              (assoc-in [:map :population :selected] nil)
              (assoc-in [:map :mode :population] nil))}
     {:dispatch [::hide-population]})))

(re-frame/reg-event-fx
 ::show-near-by-population
 (fn [{:keys [db]} _]
   (let [coords (-> db :map :center-wgs84)
         zoom   (-> db :map :zoom)
         f      {:type "FeatureCollection"
                 :features
                 [{:type       "Feature"
                   :properties {}
                   :geometry
                   {:type        "Point"
                    :coordinates [(:lon coords) (:lat coords)]}}]}]
     {:db       (-> db
                    (assoc-in [:map :mode :population :geoms] f)
                    (assoc-in [:map :mode :population :lipas-id] nil)
                    (cond->
                        (< zoom 7) (assoc-in [:map :zoom] 7)))
      :dispatch [::get-population]
      :ga/event ["analysis" "show-near-by-population"]})))

(re-frame/reg-event-fx
 ::show-sports-site-population
 (fn [_ [_ lipas-id]]
   (if lipas-id
     (let [on-success [[::show-sports-site-population* lipas-id]]]
       {:dispatch [:lipas.ui.sports-sites.events/get lipas-id on-success]})
     {})))

(re-frame/reg-event-fx
 ::show-sports-site-population*
 (fn [{:keys [db]} [_ lipas-id]]
   (let [latest (get-in db [:sports-sites lipas-id :latest])
         rev    (get-in db [:sports-sites lipas-id :history latest])
         geoms  (-> rev :location :geometries)]
     {:db       (-> db
                    (assoc-in [:map :mode :population :geoms] geoms)
                    (assoc-in [:map :mode :population :lipas-id] lipas-id)
                    (assoc-in [:map :mode :population :site-name] (:name rev)))
      :dispatch [::get-population]
      :ga/event ["analysis" "show-sports-site-population" lipas-id]})))
