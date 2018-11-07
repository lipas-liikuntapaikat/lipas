(ns lipas.ui.map.map
  (:require proj4
            "ol"
            [goog.object :as gobj]
            [lipas.ui.map.events :as events]
            [lipas.ui.map.subs :as subs]
            [lipas.ui.mui :as mui]
            [lipas.ui.svg :as svg]
            [lipas.ui.utils :refer [<== ==>] :as utils]
            [reagent.core :as r]
            [re-frame.core :as re-frame]))

;; (set! *warn-on-infer* true)

(defn ->marker-style [opts]
  (ol.style.Style.
   #js{:image
       (ol.style.Icon.
        #js{:src    (str "data:image/svg+xml;charset=utf-8,"
                         (-> opts
                             svg/->marker-str
                             js/encodeURIComponent))
            :anchor #js[0.5 0.85]
            :offset #js[0 0]})}))

(defn ->wmts-url [layer-name]
  (str "/mapproxy/wmts/"
       layer-name
       "/{TileMatrixSet}/{TileMatrix}/{TileCol}/{TileRow}.png"))

(def urls
  {:taustakartta (->wmts-url "mml_taustakartta")
   :maastokartta (->wmts-url "mml_maastokartta")
   :ortokuva     (->wmts-url "mml_ortokuva")})


(js/proj4.defs "EPSG:3067" (str "+proj=utm"
                                "+zone=35"
                                "+ellps=GRS80"
                                "+towgs84=0,0,0,0,0,0,0"
                                "+units=m"
                                "+no_defs"))

(let [^js/ol ol                  ol
      ^js/ol.proj ol-proj        (.-proj ol)
      ^js/ol.proj.proj4 ol-proj4 (.-proj4 ol-proj)]
  (.register ol-proj4 proj4))

(def mml-resolutions
  #js[8192, 4096, 2048, 1024, 512, 256, 128, 64, 32, 16, 8, 4, 2, 1, 0.5, 0.25])

(def mml-matrix-ids (clj->js (range (count mml-resolutions))))

(def ^js/ol.proj.Projection epsg3067 (ol.proj.get "EPSG:3067"))
(def ^js/ol.Extent epsg3067-extent #js[-548576.0 6291456.0 1548576.0 8388608.0])

(.setExtent epsg3067 epsg3067-extent)

(def epsg3067-top-left (ol.extent.getTopLeft (.getExtent epsg3067)))

(def jyvaskyla #js[435047 6901408])
(def center-wgs84 (ol.proj.fromLonLat #js[24 65]))

(def geoJSON (ol.format.GeoJSON. #js{:dataProjection    "EPSG:4326"
                                     :featureProjection "EPSG:3067"}))

(defn ->ol-features [geoJSON-features]
  (.readFeatures geoJSON geoJSON-features))

(defn ->geoJSON [ol-feature]
  (.writeFeaturesObject geoJSON #js[ol-feature]))

(defn ->clj [x]
  (js->clj x :keywordize-keys true))

(def ->geoJSON-clj (comp ->clj ->geoJSON))

(defn ->wmts [{:keys [url layer-name visible?]
               :or   {visible? false}}]
  (ol.layer.Tile.
   #js{:visible visible?
       :source
       (ol.source.WMTS.
        #js{:url             url
            :layer           layer-name
            :projection      "EPSG:3067"
            :matrixSet       "mml_grid"
            :tileGrid        (ol.tilegrid.WMTS.
                              #js{:origin      epsg3067-top-left
                                  :extent      epsg3067-extent
                                  :resolutions mml-resolutions
                                  :matrixIds   mml-matrix-ids})
            :format          "png"
            :requestEncoding "REST"
            :isBaseLayer     true})}))

(def circle-style (ol.style.Style.
                   #js{:image
                       (ol.style.Circle.
                        #js{:radius 10
                            :stroke (ol.style.Stroke
                                     #js{:color mui/primary})
                            :fill   (ol.style.Fill.
                                     #js{:color mui/secondary2})})}))

(def circle-style2 (ol.style.Style.
                    #js{:image
                        (ol.style.Circle.)}))

(def blue-marker-style (->marker-style {}))
(def red-marker-style (->marker-style {:color mui/secondary}))

(defn init-layers []
  {:basemaps
   {:taustakartta (->wmts {:url        (:taustakartta urls)
                           :layer-name "MML-Taustakartta"
                           :visible?   true})
    :maastokartta (->wmts {:url        (:maastokartta urls)
                           :layer-name "MML-Maastokartta"})
    :ortokuva     (->wmts {:url        (:ortokuva urls)
                           :layer-name "MML-Ortokuva"})}
   :overlays
   {:vectors (ol.layer.Vector.
              #js{:source (ol.source.Vector.)})}})

(defn init-map! [{:keys [center zoom]}]
  (let [layers (init-layers)
        view   (ol.View. #js{:center      #js[(:lon center) (:lat center)]
                             :zoom        zoom
                             :projection  "EPSG:3067"
                             :resolutions mml-resolutions
                             :units       "m"})

        popup-overlay (ol.Overlay. #js{:offset #js[-15 0]
                                       :element
                                       (js/document.getElementById "popup-anchor")})

        opts #js {:target   "map"
                  :layers   #js[(-> layers :basemaps :taustakartta)
                                (-> layers :basemaps :maastokartta)
                                (-> layers :basemaps :ortokuva)
                                (-> layers :overlays :vectors)]
                  :overlays #js[popup-overlay]
                  :view     view}

        hover (ol.interaction.Select.
               #js{:layers    [(-> layers :overlays :vectors)]
                   :style     blue-marker-style
                   :condition ol.events.condition.pointerMove})

        select (ol.interaction.Select.
                #js{:layers #js[(-> layers :overlays :vectors)]
                    :style  blue-marker-style})

        lmap (ol.Map. opts)]

    (.on hover "select"
         (fn [e]
           (let [coords   (gobj/getValueByKeys e "mapBrowserEvent" "coordinate")
                 selected (aget (gobj/get e "selected") 0)]
             (.setPosition popup-overlay coords)
             (==> [::events/show-popup
                   (when selected
                     {:anchor-el (.getElement popup-overlay)
                      :data      (-> selected ->geoJSON-clj)})]))))

    (.on select "select"
         (fn [e]
           (let [coords   (gobj/getValueByKeys e "mapBrowserEvent" "coordinate")
                 selected (aget (gobj/get e "selected") 0)]
             (.setPosition popup-overlay coords)
             (==> [::events/show-sports-site
                   (when selected
                     (.get selected "lipas-id"))]))))

    (.on lmap "moveend"
         (fn [e]
           (let [lon  (aget (.getCenter view) 0)
                 lat  (aget (.getCenter view) 1)
                 zoom (.getZoom view)]
             (==> [::events/set-view lat lon zoom]))))

    {:lmap          lmap
     :view          view
     :center        center
     :zoom          zoom
     :layers        layers
     ;; We don't re-create :hover and :select each time when we toggle
     ;; them because it causes buggy behavior. We keep refs to
     ;; singleton instances under special :interactions* key in
     ;; map-ctx where we can find them when they need to be enabled.
     :interactions* {:select select
                     :hover  hover}
     :overlays      {:popup popup-overlay}}))

(defn update-geoms! [{:keys [layers] :as map-ctx} geoms]
  (let [vectors (-> layers :overlays :vectors)
        source  (.getSource vectors)]
    (.clear source)
    (doseq [g    geoms
            :let [f (-> g
                        clj->js
                        ->ol-features)]]
      (.addFeatures source f))
    (assoc map-ctx :geoms geoms)))

(defn set-basemap! [{:keys [layers] :as map-ctx} basemap]
  (doseq [[k v] (:basemaps layers)
          :let  [visible? (= k basemap)]]
    (.setVisible v visible?))
  map-ctx)

(defn select-feature! [{:keys [interactions] :as map-ctx} feature]
  (let [select (-> interactions :select)]
    (doto (.getFeatures select)
      (.clear)
      (.push feature))
    map-ctx))

(defn find-feature-by-id [{:keys [layers] :as map-ctx} fid]
  (let [layer  (-> layers :overlays :vectors)
        source (.getSource layer)]
    (.getFeatureById source fid)))

(defn select-sports-site! [{:keys [layers] :as map-ctx} lipas-id]
  (let [feature (find-feature-by-id map-ctx lipas-id) ]
    (if feature
      (select-feature! map-ctx feature)
      map-ctx)))

(defn start-editing! [{:keys [^js/ol.Map lmap layers interactions]
                              :as   map-ctx} geoJSON-feature on-modifyend]
  (let [layer    (-> layers :overlays :vectors)
        source   (.getSource layer)
        fid      (-> geoJSON-feature :features first :id)
        features (-> geoJSON-feature clj->js ->ol-features)
        _        (.addFeatures source features)
        modify   (ol.interaction.Modify. #js{:features features
                                             :source   source})]
    (.addInteraction lmap modify)
    (.on modify "modifyend"
         (fn [e]
           (let [f (.getFeatureById source fid)]
             (on-modifyend (->geoJSON-clj f)))))
    (assoc-in map-ctx [:interactions :modify] modify)))

(defn start-editing-site! [{:keys [layers] :as map-ctx} lipas-id on-modifyend]
  (let [layer   (-> layers :overlays :vectors)
        source  (.getSource layer)
        fid     (str lipas-id "-0") ; First feature in coll
        feature (.getFeatureById source fid)]
    (start-editing! map-ctx (->geoJSON-clj feature) on-modifyend)))

(defn start-drawing! [{:keys [^js/ol.Map lmap layers interactions]
                      :as   map-ctx} geom-type on-draw-end]
  (let [layer  (-> layers :overlays :vectors)
        source (.getSource layer)
        draw   (ol.interaction.Draw. #js{:source source
                                         :type   geom-type})]
    (.addInteraction lmap draw)
    (.on draw "drawend"
         (fn [e]
           (let [f (gobj/get e "feature")
                 _ (.setId f (str (gensym)))]
             (on-draw-end (->geoJSON-clj f)))))
    (assoc-in map-ctx [:interactions :draw] draw)))

(defn update-center! [{:keys [^js/ol.View view] :as map-ctx}
                      {:keys [lon lat] :as center}]
  (.setCenter view #js[lon lat])
  (assoc map-ctx :center center))

(defn update-zoom! [{:keys [^js/ol.View view] :as map-ctx} zoom]
  (.setZoom view zoom)
  (assoc map-ctx :zoom zoom))

(defn show-feature! [{:keys [layers] :as map-ctx} geoJSON-feature]
  (let [vectors (-> layers :overlays :vectors)
        source  (.getSource vectors)
        fs      (-> geoJSON-feature clj->js ->ol-features)]
    (.addFeatures source fs)
    map-ctx))

(defn clear-interactions! [{:keys [^js/ol.Map lmap interactions interactions*]
                            :as   map-ctx}]
  ;; Special treatment for 'singleton' interactions*. OpenLayers
  ;; doesn't treat 'copies' identical to original ones. Therefore we
  ;; need to pass the original ones explicitly.
  (doseq [v     (vals (merge interactions interactions*))
          :when (some? v)]
    (.removeInteraction lmap v))

  (assoc map-ctx :interactions {}))

;; Adding new features
(defn set-adding-mode! [map-ctx mode]
  (let [map-ctx (clear-interactions! map-ctx)]
    (case (:sub-mode mode)
      :drawing  (start-drawing! map-ctx (:geom-type mode)
                                (fn [f] (==> [::events/new-geom-drawn f])))
      :editing  (start-editing! map-ctx (:geom mode)
                                (fn [f] (==> [::events/update-new-geom f])))
      :finished (show-feature! map-ctx (:geom mode)))))

(defn update-adding-mode! [map-ctx mode]
  (let [old-mode (:mode map-ctx)]
    (if (= (:sub-mode mode) (:sub-mode old-mode))
      map-ctx ;; Noop
      (set-adding-mode! map-ctx mode))))

;; Editing existing features
(defn set-editing-mode! [map-ctx mode]
  (let [map-ctx (clear-interactions! map-ctx)]
    (let [lipas-id     (:lipas-id mode)
          on-modifyend (fn [f] (==> [::events/update-geometries lipas-id f]))]
      (start-editing-site! map-ctx lipas-id on-modifyend))))

(defn update-editing-mode! [map-ctx mode]
  map-ctx)

(defn enable-hover! [{:keys [^js/ol.Map lmap interactions*] :as map-ctx}]
  (let [hover (:hover interactions*)]
    (.addInteraction lmap hover)
    (assoc-in map-ctx [:interactions :hover] hover)))

(defn enable-select! [{:keys [^js/ol.Map lmap interactions*] :as map-ctx}]
  (let [select (:select interactions*)]
    (.addInteraction lmap select)
    (assoc-in map-ctx [:interactions :select] select)))

;; Browsing and selecting features
(defn set-default-mode! [{:keys [^js/ol.Map lmap interactions] :as map-ctx} mode]
  (let [map-ctx (-> map-ctx
                    clear-interactions!
                    enable-hover!
                    enable-select!)]
    (if-let [lipas-id (:lipas-id mode)]
      (select-sports-site! map-ctx lipas-id)
      map-ctx)))

(defn update-default-mode! [{:keys [^js/ol.Map lmap interactions] :as map-ctx} mode]
  (if-let [lipas-id (:lipas-id mode)]
    (select-sports-site! map-ctx lipas-id)
    map-ctx))

(defn set-mode! [map-ctx mode]
  (let [mode (case (:name mode)
                   :default (set-default-mode! map-ctx mode)
                   :editing (set-editing-mode! map-ctx mode)
                   :adding  (set-adding-mode! map-ctx mode))]
    (assoc map-ctx :mode mode)))

(defn update-mode! [map-ctx mode]
  (let [update? (= (-> map-ctx :mode :name) (:name mode))
        map-ctx (case (:name mode)
                  :default (if update?
                             (update-default-mode! map-ctx mode)
                             (set-default-mode! map-ctx mode))
                  :editing (if update?
                             (update-editing-mode! map-ctx mode)
                             (set-editing-mode! map-ctx mode))
                  :adding  (if update?
                             (update-adding-mode! map-ctx mode)
                             (set-adding-mode! map-ctx mode)))]
    (assoc map-ctx :mode mode)))

(defn map-inner []

  ;; Internal state
  (let [map-ctx* (atom nil)]

    (r/create-class

     {:reagent-render
      (fn [] [mui/grid {:id    "map"
                        :item  true
                        :style {:flex "1 0 0"}
                        :xs    12}])

      :component-did-mount
      (fn [comp]
        (let [opts    (r/props comp)
              basemap (:basemap opts)
              geoms   (:geoms opts)
              center  (-> opts :center)
              zoom    (-> opts :zoom)
              mode    (-> opts :mode)

              map-ctx (-> (init-map! opts)
                          (update-geoms! geoms)
                          (set-basemap! basemap)
                          (set-mode! mode))]

          (reset! map-ctx* map-ctx)))

      :component-did-update
      (fn [comp]
        (let [opts    (r/props comp)
              geoms   (-> opts :geoms)
              basemap (-> opts :basemap)
              center  (-> opts :center)
              zoom    (-> opts :zoom)
              mode    (-> opts :mode)]

          (cond-> @map-ctx*
            (not= (:geoms @map-ctx*) geoms)     (update-geoms! geoms)
            (not= (:basemap @map-ctx*) basemap) (set-basemap! basemap)
            (not= (:center @map-ctx*) center)   (update-center! center)
            (not= (:zoom @map-ctx*) zoom)       (update-zoom! zoom)
            (not= (:mode @map-ctx*) mode)       (update-mode! mode)
            true                                (as-> $ (reset! map-ctx* $)))))

      :display-name "map-inner"})))

(defn map-outer []
  (==> [:lipas.ui.sports-sites.events/get-by-type-code 3110])
  (==> [:lipas.ui.sports-sites.events/get-by-type-code 3130])
  (==> [:lipas.ui.sports-sites.events/get-by-type-code 2510])
  (==> [:lipas.ui.sports-sites.events/get-by-type-code 2520])
  (let [geoms   (re-frame/subscribe [::subs/geometries])
        basemap (re-frame/subscribe [::subs/basemap])
        center  (re-frame/subscribe [::subs/center])
        zoom    (re-frame/subscribe [::subs/zoom])
        mode    (re-frame/subscribe [::subs/mode])]
    (fn []
      [map-inner
       {:geoms   @geoms
        :basemap @basemap
        :center  @center
        :zoom    @zoom
        :mode    @mode}])))
