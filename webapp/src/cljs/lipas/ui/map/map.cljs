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

(defn init-map [{:keys [center zoom]}]
  (let [layers (init-layers)
        view   (ol.View. #js{:center      #js[(:lon center) (:lat center)]
                             :zoom        zoom
                             :projection  "EPSG:3067"
                             :resolutions mml-resolutions
                             :units       "m"})

        overlay (ol.Overlay. #js{:offset #js[-15 0]
                                 :element
                                 (js/document.getElementById "popup-anchor")})

        opts #js {:target   "map"
                  :layers   #js[(-> layers :basemaps :taustakartta)
                                (-> layers :basemaps :maastokartta)
                                (-> layers :basemaps :ortokuva)
                                (-> layers :overlays :vectors)]
                  :overlays #js[overlay]
                  :view     view}

        lmap (ol.Map. opts)

        hover (ol.interaction.Select.
               #js{:layers    [(-> layers :overlays :vectors)]
                   :style     blue-marker-style
                   :condition ol.events.condition.pointerMove})

        select (ol.interaction.Select.
                #js{:layers #js[(-> layers :overlays :vectors)]
                    :style  blue-marker-style})]

    (.on hover "select"
         (fn [e]
           (let [coords   (gobj/getValueByKeys e "mapBrowserEvent" "coordinate")
                 selected (aget (gobj/get e "selected") 0)]
             (.setPosition overlay coords)
             (==> [::events/show-popup
                   (when selected
                     {:anchor-el (.getElement overlay)
                      :data      (-> selected
                                     ->geoJSON-clj)})]))))

    (.addInteraction lmap hover)

    (.on select "select"
         (fn [e]
           (let [coords   (gobj/getValueByKeys e "mapBrowserEvent" "coordinate")
                 selected (aget (gobj/get e "selected") 0)]
             (.setPosition overlay coords)
             (==> [::events/show-sports-site
                   (when selected
                     (.get selected "lipas-id"))]))))

    (.addInteraction lmap select)

    (.on lmap "moveend"
         (fn [e]
           (let [lon  (aget (.getCenter view) 0)
                 lat  (aget (.getCenter view) 1)
                 zoom (.getZoom view)]
             (==> [::events/set-view lat lon zoom]))))

    {:lmap   lmap
     :view   view
     :interactions
     {:hover  hover
      :select select}
     :layers layers}))

(defn update-geoms! [{:keys [layers]} geoms]
  (let [vectors (-> layers :overlays :vectors)
        source  (.getSource vectors)]
    (.clear source)
    (doseq [g    geoms
            :let [f (-> g
                        clj->js
                        ->ol-features)]]
      (.addFeatures source f))))

(defn set-basemap! [{:keys [layers]} basemap]
  (doseq [[k v] (:basemaps layers)
          :let [visible? (= k basemap)]]
    (.setVisible v visible?)))

(defn select-feature [{:keys [interactions] :as map-ctx} feature]
  (let [select (-> interactions :select)]
    (doto (.getFeatures select)
      (.clear)
      (.push feature)))
  map-ctx)

(defn select-sports-site! [{:keys [layers interactions] :as map-ctx} lipas-id]
  (let [layer   (-> layers :overlays :vectors)
        source  (.getSource layer)
        fid     (str lipas-id "-0") ; First feature in coll
        feature (.getFeatureById source fid)]
    (if feature
      (select-feature map-ctx feature)
      map-ctx)))

(defn start-editing [{:keys [^js/ol.Map lmap layers interactions]
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

(defn start-editing-site [{:keys [layers] :as map-ctx} lipas-id on-modifyend]
  (let [layer   (-> layers :overlays :vectors)
        source  (.getSource layer)
        fid     (str lipas-id "-0") ; First feature in coll
        feature (.getFeatureById source fid)]
    (start-editing map-ctx (->geoJSON-clj feature) on-modifyend)))

(defn stop-editing [{:keys [^js/ol.Map lmap layers interactions] :as map-ctx}]
  (let [modify (-> interactions :modify)]
    (when modify
      (.removeInteraction lmap modify))
    (update-in map-ctx [:interactions] dissoc :modify)))

(defn start-drawing [{:keys [^js/ol.Map lmap layers interactions]
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

(defn stop-drawing [{:keys [^js/ol.Map lmap layers interactions] :as map-ctx}]
  (let [draw   (-> interactions :draw)
        modify (-> interactions :modify)]
    (when draw
      (.removeInteraction lmap draw))
    (when modify
      (.removeInteraction lmap modify))
    (update-in map-ctx [:interactions] dissoc :draw :modify)))

(defn update-view! [{:keys [^js/ol.View view]} {:keys [lon lat zoom]}]
  (.setCenter view #js[lon lat])
  (.setZoom view zoom))

(defn clear-interactions [{:keys [^js/ol.Map lmap interactions] :as map-ctx}]
  (.removeInteraction lmap (-> interactions :select))
  (.removeInteraction lmap (-> interactions :hover))
  (when-let [draw (:draw interactions)]
    (.removeInteraction lmap draw))
  (when-let [modify (:modify interactions)]
    (.removeInteraction lmap modify)))

(defn show-feature [{:keys [layers] :as map-ctx} geoJSON-feature]
  (let [vectors (-> layers :overlays :vectors)
        source  (.getSource vectors)
        fs      (-> geoJSON-feature clj->js ->ol-features)]
    (.addFeatures source fs)
    map-ctx))

;; Adding new features
(defn set-adding-mode! [map-ctx mode]
  (clear-interactions map-ctx)
  (case (:sub-mode mode)
    :drawing  (start-drawing map-ctx (:geom-type mode)
                             (fn [f] (==> [::events/new-geom-drawn f])))
    :editing  (start-editing map-ctx (:geom mode)
                             (fn [f] (==> [::events/update-new-geom f])))
    :finished (show-feature map-ctx (:geom mode))))

(defn update-adding-mode! [map-ctx mode old-mode]
  (if (= (:sub-mode mode) (:sub-mode old-mode))
    map-ctx ;; Noop
    (set-adding-mode! map-ctx mode)))

;; Editing existing features
(defn set-editing-mode [map-ctx mode]
  (clear-interactions map-ctx)
  (let [lipas-id     (:lipas-id mode)
        on-modifyend (fn [f] (==> [::events/update-geometries lipas-id f]))]
    (start-editing-site map-ctx lipas-id on-modifyend)))

(defn update-editing-mode! [map-ctx mode]
  map-ctx)

;; Browsing and selecting features
(defn set-default-mode! [{:keys [^js/ol.Map lmap interactions] :as map-ctx} mode]
  (clear-interactions map-ctx)
  (.addInteraction lmap (-> interactions :select))
  (.addInteraction lmap (-> interactions :hover))
  (when-let [lipas-id (:lipas-id mode)]
    (select-sports-site! map-ctx lipas-id))
  map-ctx)

(defn update-default-mode! [{:keys [^js/ol.Map lmap interactions] :as map-ctx} mode]
  (when-let [lipas-id (:lipas-id mode)]
    (select-sports-site! map-ctx lipas-id))
  map-ctx)

(defn map-inner []

  ;; Internal state atoms
  (let [map-ctx* (atom nil)
        geoms*   (atom nil)
        basemap* (atom nil)
        center*  (atom nil)
        zoom*    (atom nil)
        mode*    (atom nil)]

    (r/create-class

     {:reagent-render
      (fn [] [mui/grid {:id    "map"
                        :item  true
                        :style {:flex "1 0 0"}
                        :xs    12}])

      :component-did-mount
      (fn [comp]
        (let [opts     (r/props comp)
              basemap  (:basemap opts)
              geoms    (:geoms opts)
              lipas-id (-> opts :site :display-data :lipas-id)
              center   (-> opts :center)
              zoom     (-> opts :zoom)
              mode     (-> opts :mode)

              map-ctx (init-map opts)]

          (reset! map-ctx* map-ctx)

          (reset! basemap* basemap)
          (reset! zoom* zoom)
          (reset! center* center)
          (reset! mode* mode)

          (update-geoms! map-ctx geoms)
          (set-basemap! map-ctx basemap)
          (select-sports-site! map-ctx lipas-id)

          (case (:name mode)
            :default (reset! map-ctx* (set-default-mode! @map-ctx* mode))
            :editing (reset! map-ctx* (set-editing-mode @map-ctx* mode))
            :adding  (reset! map-ctx* (set-adding-mode! @map-ctx* mode)))))

      :component-did-update
      (fn [comp]
        (let [opts     (r/props comp)
              geoms    (-> opts :geoms)
              lipas-id (-> opts :site :display-data :lipas-id)
              basemap  (-> opts :basemap)
              center   (-> opts :center)
              zoom     (-> opts :zoom)
              mode     (-> opts :mode)]

          (when (not= @geoms* geoms)
            (update-geoms! @map-ctx* geoms)
            (reset! geoms* geoms))

          (when (not= @basemap* basemap)
            (set-basemap! @map-ctx* basemap)
            (reset! basemap* basemap))

          (when (or (not= @zoom* zoom)
                    (not= @center* center))
            (update-view! @map-ctx* (merge center {:zoom zoom}))
            (reset! center* center)
            (reset! zoom* zoom))

          (when (not= @mode* mode)
            ;; (prn "Mode changed from!" @mode* "to" mode)
            (let [update? (= (:name @mode*) (:name mode))
                  map-ctx (case (:name mode)
                            :default (if update?
                                       (update-default-mode! @map-ctx* mode)
                                       (set-default-mode! @map-ctx* mode))
                            :editing (if update?
                                       (update-editing-mode! @map-ctx* mode)
                                       (set-editing-mode @map-ctx* mode))
                            :adding  (if update?
                                       (update-adding-mode! @map-ctx* mode @mode*)
                                       (set-adding-mode! @map-ctx* mode)))]
              (reset! map-ctx* map-ctx)
              (reset! mode* mode)))))

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
        site    (re-frame/subscribe [::subs/selected-sports-site])
        mode    (re-frame/subscribe [::subs/mode])]
    (fn []
      [map-inner
       {:geoms   @geoms
        :basemap @basemap
        :center  @center
        :zoom    @zoom
        :site    @site
        :mode    @mode}])))
