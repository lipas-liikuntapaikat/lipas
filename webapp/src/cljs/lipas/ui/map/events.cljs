(ns lipas.ui.map.events
  (:require
   ["ol/proj" :as proj]
   ["togpx" :as togpx]
   [ajax.core :as ajax]
   [goog.object :as gobj]
   [goog.string :as gstring]
   [goog.string.format]
   [lipas.ui.map.utils :as map-utils]
   [lipas.ui.utils :refer [==>] :as utils]
   [re-frame.core :as re-frame]))

(defn wgs84->epsg3067 [wgs84-coords]
  (let [proj      (proj/get "EPSG:3067")
        [lon lat] (js->clj (proj/fromLonLat (clj->js wgs84-coords) proj))]
    {:lon lon :lat lat}))

(defn epsg3067->wgs84-fast [wgs84-coords]
  (let [proj      (proj/get "EPSG:3067")]
    (proj/toLonLat wgs84-coords proj)))

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
      [(when (and extent width) [:lipas.ui.search.events/submit-search])
       (when (and extent width) [:lipas.ui.loi.events/search])]})))

;; Map displaying events ;;

(re-frame/reg-event-db
 ::fit-to-current-vectors
 (fn [db _]
   (assoc-in db [:map :mode :fit-nonce] (str (gensym)))))

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
 ::zoom-to-loi
 (fn [{:keys [db]} [_ loi-fcoll width]]
   (let [geom       (-> loi-fcoll :features first :geometry)
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
 ::show-elevation-marker
 (fn [db [_ js-obj]]
   (assoc-in db [:map :mode :elevation] js-obj)))

(re-frame/reg-event-db
 ::hide-elevation-marker
 (fn [db _]
   (assoc-in db [:map :mode :elevation] nil)))

(re-frame/reg-event-db
 ::show-sports-site*
 (fn [db [_ lipas-id]]
   (let [drawer-open? (or lipas-id (-> db :screen-size #{"sm" "xs"} boolean not))]
     (-> db
         (assoc-in [:map :mode :lipas-id] lipas-id)
         (assoc-in [:map :drawer-open?] drawer-open?)
         (assoc-in [:map :selected-sports-site-tab] 0)))))

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
 (fn [{:keys [db]} [_ view-only?]]
   (let [geoms    (-> db :map :mode :geoms)
         sub-mode (if view-only? :view-only :editing)]
     {:db (update-in db [:map :mode] merge {:name :editing :sub-mode sub-mode})
      :dispatch-n
      [[::show-problems (map-utils/find-problems geoms)]]})))

(re-frame/reg-event-fx
 ::stop-editing
 (fn [{:keys [db]} [_]]
   {:db       (assoc-in db [:map :mode :name] :default)
    :fx [[:dispatch [::clear-undo-redo]]]}))

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

(def ensure-fids utils/ensure-fids)

(re-frame/reg-event-fx
 ::update-geometries
 (fn [{:keys [db]} [_ lipas-id geoms]]

   (let [path      [:sports-sites lipas-id :editing :location :geometries]
         old-geoms (-> db :map :mode :geoms)
         new-geoms (ensure-fids geoms)]
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
   (let [geoms      (ensure-fids geoms)
         curr-geoms (-> db :map :mode :geoms)]
     {:db (cond-> db
            curr-geoms (update-in [:map :temp "new" :undo-stack] conj curr-geoms)
            true       (assoc-in [:map :temp "new" :redo-stack] '())
            true       (update-in [:map :mode] merge {:name     :adding
                                                      :geoms    geoms
                                                      :sub-mode :editing}))

      :dispatch-n [[::show-problems (map-utils/find-problems geoms)]]})))

(re-frame/reg-event-fx
 ::update-new-geom
 (fn [{:keys [db]} [_ geoms]]
   (let [geoms      (ensure-fids geoms)
         curr-geoms (-> db :map :mode :geoms)]
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
   (let [geoms (ensure-fids geoms)]
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
 (fn [{:keys [db]} [_ ^js event]]
   (let [fids       (atom #{})
         lmap       (.-map event)
         opts       #js{:layerFilter  (fn [layer] (= "edits" (.get layer "name")))
                        :hitTolerance 5}
         selecting? (= :selecting (-> db :map :mode :sub-mode))]
     #_(js/console.log event)
     #_(js/console.log (.-pixel event))
     (when selecting?
       (.forEachFeatureAtPixel lmap (.-pixel event)
                               (fn [f]
                                 (swap! fids conj (.getId f)))
                               opts))
     {:fx (into [[:dispatch [::hide-address]]]
                (if (seq @fids)
                  (for [fid @fids]
                    [:dispatch [::toggle-selected-feature-id fid]])
                  [(when-not selecting?
                     [:dispatch [::clear-highlight]])]))})))

(re-frame/reg-event-fx
 ::sports-site-selected
 (fn [{:keys [db]} [_ _ lipas-id]]
   (let [mode (-> db :map :mode :name)]
     {:fx
      [(when (= mode :analysis)
         [:dispatch [:lipas.ui.analysis.reachability.events/show-analysis lipas-id]])
       (when (not= mode :analysis)
         [:dispatch [::show-sports-site lipas-id]])]})))

(re-frame/reg-event-fx
 ::loi-selected
 (fn [{:keys [db]} [_ event _f1]]
   (let [fcoll (map-utils/->geoJSON-clj (gobj/get event "selected"))]
     {:fx
      [[:dispatch [::show-loi fcoll]]]})))

(re-frame/reg-event-fx
 ::unselected
 (fn [{:keys [db]} [_ _]]
   (let [mode (-> db :map :mode :name)]
     {:fx
      [(when (not= mode :analysis)
         [:dispatch [::show-sports-site nil]])
       (when (not= mode :analysis)
         [:dispatch [::show-loi nil]])]})))

;;; Higher order events ;;;

(re-frame/reg-event-fx
 ::show-loi
 (fn [{:keys [db]} [_ loi-fcoll]]
   (let [width (:screen-size db)]
     {:fx
      [[:dispatch [:lipas.ui.loi.events/select-loi loi-fcoll]]
       (when loi-fcoll [:dispatch [:lipas.ui.map.events/zoom-to-loi loi-fcoll width]])
       [:dispatch [:lipas.ui.events/navigate :lipas.ui.routes.map/map]]]})))

(re-frame/reg-event-fx
 ::show-sports-site
 (fn [_ [_ lipas-id]]
   {:dispatch-n
    (if lipas-id
      (let [params {:lipas-id lipas-id}]
        [[:lipas.ui.events/navigate :lipas.ui.routes.map/details-view params]
         [:lipas.ui.accessibility.events/get-statements lipas-id]])
      [[:lipas.ui.events/navigate :lipas.ui.routes.map/map]])}))

(re-frame/reg-event-fx
 ::edit-site
 (fn [_ [_ lipas-id geom-type edit-activities-only?]]
   (let [sub-mode (if edit-activities-only? :view-only :editing)]
     {:dispatch-n
      [[:lipas.ui.sports-sites.events/edit-site lipas-id]
       ;;[::zoom-to-site lipas-id]
       [::clear-undo-redo]
       [::start-editing lipas-id sub-mode geom-type]]})))

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
 (fn [{:keys [db]} [_ template]]
   {:db         (assoc-in db [:map :mode] {:name :default}) ;; cleanup
    :dispatch-n [[:lipas.ui.search.events/set-results-view :list]
                 [:lipas.ui.sports-sites.events/start-adding-new-site template]
                 [:lipas.ui.loi.events/start-adding-new-loi]]}))

(re-frame/reg-event-fx
 ::discard-edits
 (fn [{:keys [db]} [_ lipas-id]]
   (let [tr (-> db :translator)]
     {:dispatch
      [:lipas.ui.events/confirm
       (tr :confirm/discard-changes?)
       (fn []
         (==> [:lipas.ui.sports-sites.events/discard-edits lipas-id])
         (==> [::stop-editing])
         (==> [:lipas.ui.search.events/submit-search]))]})))

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
                    utils/make-saveable
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
 (fn [db [_ geoJSON geom-type]]
   (let [fcoll (js->clj geoJSON :keywordize-keys true)
         fs    (->> fcoll
                    :features
                    (filter (comp #{geom-type} :type :geometry))
                    (reduce
                     (fn [res f]
                       (let [id (str (gensym))]
                         (assoc res id (assoc-in f [:properties :id] id))))
                     {})
                    (merge (map-utils/normalize-geom-colls fcoll geom-type)
                           (map-utils/normalize-multi-geoms fcoll geom-type)))]
     (-> db
         (assoc-in [:map :import :data] fs)
         (assoc-in [:map :import :batch-id] (str (gensym)))))))

(re-frame/reg-event-fx
 ::load-geoms-from-file
 (fn [{:keys [db]} [_ files geom-type]]
   (let [file   (aget files 0)
         params {:enc  (-> db :map :import :selected-encoding)
                 :file file
                 :ext  (map-utils/parse-ext file)
                 :cb   (fn [data] (==> [::set-import-candidates data geom-type]))}]

     (if-let [ext (:unknown (map-utils/file->geoJSON params))]
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
                     (togpx #js{:creator "LIPAS"}))]
     {:lipas.ui.effects/save-as! {:blob (js/Blob. #js[xml-str]) :filename fname}})))

;; Geom simplification ;;

(re-frame/reg-event-fx
 ::open-simplify-tool
 (fn [{:keys [db]} _]
   {:db (assoc-in db [:map :mode :sub-mode] :simplifying)
    :fx [[:dispatch [::set-simplify-tolerance 0]]
         [:dispatch [::toggle-simplify-dialog]]]}))

(re-frame/reg-event-fx
 ::close-simplify-tool
 (fn [{:keys [db]} _]
   {:fx [[:dispatch [::toggle-simplify-dialog]]
         [:dispatch [::continue-editing]]]}))

(re-frame/reg-event-db
 ::toggle-simplify-dialog
 (fn [db _]
   (update-in db [:map :simplify :dialog-open?] not)))

(re-frame/reg-event-db
 ::set-simplify-tolerance
 (fn [db [_ v]]
   (assoc-in db [:map :simplify :tolerance] v)))

(re-frame/reg-event-fx
 ::simplify
 (fn [_ [_ lipas-id geoms tolerance]]
   (let [simplified (map-utils/simplify geoms (map-utils/simplify-scale tolerance))]
     {:fx [[:dispatch [::update-geometries lipas-id simplified]]
           [:dispatch [::close-simplify-tool]]
           [:dispatch [::continue-editing]]]})))

(re-frame/reg-event-fx
 ::simplify-new
 (fn [_ [_ geoms tolerance]]
   (let [simplified (map-utils/simplify geoms (map-utils/simplify-scale tolerance))]
     {:fx [[:dispatch [::new-geom-drawn simplified]]
           [:dispatch [::toggle-simplify-dialog]]]})))

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
 (fn [_ [_ s]]
   (if (not-empty s)
     {:http-xhrio
      {:method          :get
       :uri             (str "https://"
                             (utils/domain)
                             "/digitransit"
                             "/geocoding/v1"
                             "/autocomplete?"
                             "sources=oa,osm"
                             "&text=" s)
       :response-format (ajax/json-response-format {:keywords? true})
       :on-success      [::address-search-success]
       :on-failure      [::address-search-failure]}}
     {:dispatch [::clear-address-search-results]})))

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

(re-frame/reg-event-fx
 ::set-overlay
 (fn [{:keys [db]} [_ val layer]]
   {:db (update-in db [:map :selected-overlays] (if val conj disj) layer)}))

;; Takes coll of pairs like [:analysis true] [:population false]
(re-frame/reg-event-fx
 ::set-overlays
 (fn [{:keys [db]} [_ layers]]
   (let [adds    (->> layers (filter second) (map first))
         removes (->> layers (filter (complement second)) (map first))]
     {:db (update-in db [:map :selected-overlays]
                     (fn [current-layers]
                       (as-> (set current-layers) $
                         (apply conj $ adds)
                         (apply disj $ removes))))})))

(re-frame/reg-event-fx
 ::enable-overlays
 (fn [{:keys [db]} [_ layers]]
   {:db (update-in db [:map :selected-overlays] into layers)}))

(re-frame/reg-event-fx
 ::show-analysis*
 (fn [{:keys [db]} _]
   {:db (-> db
            (assoc-in [:map :mode :name] :analysis)
            (assoc-in [:map :mode :sub-mode] :reachability))
    :dispatch-n
    [[::set-overlays [[:vectors true]
                      [:schools true]
                      [:population true]
                      [:diversity-grid false]
                      [:diversity-area false]
                      [:analysis true]]]
     [:lipas.ui.search.events/set-status-filter ["planning"] :append]]}))

(re-frame/reg-event-fx
 ::show-analysis
 (fn [{:keys [db]} [_ lipas-id]]
   {:dispatch-n
    [(when-not lipas-id
       [::show-analysis*])
     [:lipas.ui.analysis.reachability.events/show-analysis lipas-id]]}))

(re-frame/reg-event-fx
 ::hide-analysis
 (fn [{:keys [db]} _]
   {:db (-> db
            (assoc-in [:map :mode :name] :default)
            (update-in [:map :mode] dissoc :sub-mode))
    :dispatch-n [[:lipas.ui.analysis.reachability.events/clear]
                 [::set-overlays [[:vectors true]
                                  [:lois true]
                                  [:schools false]
                                  [:population false]
                                  [:diversity-grid false]
                                  [:diversity-area false]
                                  [:analysis false]]]
                 [:lipas.ui.search.events/remove-status-filter "planning"]
                 [:lipas.ui.search.events/clear-filters]]}))

(re-frame/reg-event-fx
 ::add-analysis-target
 (fn [{:keys [db]} _]
   (let [template {:status "planning"
                   :name   "Analyysikohde"
                   :owner  "unknown"
                   :admin  "unknown"
                   :location
                   {:address     "Testikatu 123"
                    :postal-code "12345"}}]
     {:dispatch-n
      [[::start-adding-new-site template]]})))

(re-frame/reg-event-fx
 ::add-point-from-coords
 (fn [_ [_ {:keys [crs lon lat]}]]
   (let [coords (if (= :epsg3067 crs)
                  (epsg3067->wgs84-fast #js[lat lon])
                  [lon lat])
         fcoll  {:type     "FeatureCollection"
                 :features [{:type "Feature" :geometry {:type "Point" :coordinates coords}}]}]
     {:dispatch-n [[::new-geom-drawn fcoll]]})))

(re-frame/reg-event-db
 ::select-sports-site-tab
 (fn [db [_ tab]]
   (assoc-in db [:map :selected-sports-site-tab] tab)))

(re-frame/reg-event-db
 ::select-new-sports-site-tab
 (fn [db [_ tab]]
   (assoc-in db [:map :selected-new-sports-site-tab] tab)))

(re-frame/reg-event-db
 ::toggle-selected-feature-id
 (fn [db [_ fid]]
   (update-in db [:map :mode :selected-features] (fn [fids]
                                                   (let [fids (or fids #{})]
                                                     (if (contains? fids fid)
                                                       (disj fids fid)
                                                       (conj fids fid)))))))

(re-frame/reg-event-db
 ::highlight-features
 (fn [db [_ fids]]
   (assoc-in db [:map :mode :selected-features] fids)))

(re-frame/reg-event-db
 ::clear-highlight
 (fn [db _]
   (assoc-in db [:map :mode :selected-features] #{})))

(re-frame/reg-event-db
 ::select-add-mode
 (fn [db [_ add-mode]]
   (assoc-in db [:map :add-mode] add-mode)))

(re-frame/reg-event-fx
 ::toggle-travel-direction
 (fn [{:keys [db]} [_ lipas-id fid]]
   (let [geoms (-> db
                   (get-in [:map :mode :geoms])
                   (update :features
                           (fn [fs]
                             (map (fn [{:keys [id properties] :as f}]
                                    (if (= id fid)
                                      (let [curr-direction (get properties :travel-direction)
                                            new-direction  (case curr-direction
                                                             nil            "start-to-end"
                                                             "start-to-end" "end-to-start"
                                                             "end-to-start" nil)]
                                        (assoc-in f [:properties :travel-direction] new-direction))
                                      f))
                                  fs))))]
     {:fx [[:dispatch [::update-geometries lipas-id geoms]]]})))

;; Reverse geocoding

(re-frame/reg-event-fx
 ::resolve-address
 (fn [_ [_ {:keys [lat lon on-success]}]]
   (when (and lat lon)
     {:http-xhrio
      {:method          :get
       :uri             (str "https://"
                             (utils/domain)
                             "/digitransit"
                             "/geocoding/v1"
                             "/reverse?"
                             "point.lat=" lat
                             "&point.lon=" lon
                             "&sources=osm,oa"
                             "&layers=address"
                             "&size=" 10)
       :response-format (ajax/json-response-format {:keywords? true})
       :on-success      on-success
       :on-failure      [::reverse-geocoding-search-failure]}})))

(re-frame/reg-event-fx
 ::reverse-geocoding-search-failure
 (fn [{:keys [db]} [_  resp]]
   (let [tr (:translator db)]
     {:db (assoc-in db [:map :address-locator :error] resp)
      :fx [[:dispatch [:lipas.ui.events/set-active-notification
                       {:message  (tr :notifications/get-failed)
                        :success? false}]]]})))

(re-frame/reg-event-fx
 ::on-reverse-geocoding-success
 (fn [{:keys [db]} [_ resp]]
   (let [addresses (->> resp
                        :features
                        (map :properties)
                        (map #(select-keys % [:name :localadmin :postalcode :locality :label :confidence :distance])))]
     {:db (assoc-in db [:map :address-locator :reverse-geocoding-results] addresses)})))

(re-frame/reg-event-fx
 ::populate-address-with-reverse-geocoding-results
 (fn [{:keys [db]} [_ lipas-id cities reverse-geocoding-results]]
   (let [results      (->> reverse-geocoding-results
                           :features
                           (map :properties)
                           (map #(select-keys % [:name :localadmin :postalcode :locality :label :confidence :distance])))
         first-result (first results)
         city-match   (first (filter #(= (:localadmin first-result) (get-in % [:name :fi])) cities))
         path->val    {[:location :address]         (:name first-result)
                       [:location :postal-code]     (:postalcode first-result)
                       [:location :postal-office]   (:locality first-result)
                       [:location :city :city-code] (:city-code city-match)}]
     {:db (assoc-in db [:map :address-locator :reverse-geocoding-results] results)
      :fx (if lipas-id

            ;; editing existing sports site
            [[:dispatch [:lipas.ui.sports-sites.events/edit-fields lipas-id path->val]]]

            ;; editing new sports site
            [[:dispatch [:lipas.ui.sports-sites.events/edit-new-site-fields path->val]]])})))


(re-frame/reg-event-db
 ::select-address-locator-address
 (fn [db [_ m]]
   (assoc-in db [:map :address-locator :selected-address] m)))

(re-frame/reg-event-db
 ::open-address-locator-dialog
 (fn [db _]
   (assoc-in db [:map :address-locator :dialog-open?] true)))

(re-frame/reg-event-db
 ::close-address-locator-dialog
 (fn [db _]
   (assoc-in db [:map :address-locator :dialog-open?] false)))
