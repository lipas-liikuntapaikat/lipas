(ns lipas.ui.map2.map
  (:require-macros [lipas.ui.map2.map :refer [use-object]])
  (:require ["@mui/material/Stack$default" :as Stack]
            ["ol-new" :as ol]
            ["ol-new/extent" :as extent]
            ["ol-new/layer/Image$default" :as ImageLayer]
            ["ol-new/layer/Tile$default" :as TileLayer]
            ["ol-new/proj" :as proj]
            ["ol-new/proj/proj4" :refer [register]]
            ["ol-new/source/ImageWMS$default" :as ImageWMSSource]
            ["ol-new/source/WMTS$default" :as WMTSSource]
            ["ol-new/tilegrid/WMTS$default" :as WMTSTileGrid]
            ["proj4" :as proj4]
            [lipas.ui.map.subs :as subs]
            [lipas.ui.map2.subs :as subs2]
            [lipas.ui.uix.hooks :refer [use-subscribe]]
            [uix.core :as uix :refer [$ defui]]))

;; OL component patterns
;; - Create ref to store the OL object state for the component
;; - Initialize the OL object in component render if ref.current is empty:
;;   https://react.dev/reference/react/useRef#avoiding-recreating-the-ref-contents
;; - OL Map is available in Content through a ref
;; - Use effect with empty deps (i.e. runs just on component mount and unmount) to add OL object to the Map
;;   (or what ever is the parent for the OL object)
;; - Use effect with dynamic properties to update the OL object

(def mml-resolutions
  #js [8192, 4096, 2048, 1024, 512, 256, 128, 64, 32, 16, 8, 4, 2, 1, 0.5, 0.25])

(def mml-matrix-ids (clj->js (range (count mml-resolutions))))

(defn ->wmts-url [layer-name]
  (str "/mapproxy/wmts/"
       layer-name
       "/{TileMatrixSet}/{TileMatrix}/{TileCol}/{TileRow}.png"))

(def urls
  {:taustakartta        (->wmts-url "mml_taustakartta")
   :maastokartta        (->wmts-url "mml_maastokartta")
   :ortokuva            (->wmts-url "mml_ortokuva")
   :kiinteisto          (->wmts-url "mml_kiinteisto")
   :kiinteistotunnukset (->wmts-url "mml_kiinteistotunnukset")
   :kuntarajat          (->wmts-url "mml_kuntarajat")})

(def epsg3067-extent #js [-548576.0 6291456.0 1548576.0 8388608.0])

;; initialize proj
(def epsg3067-defs
  (str "+proj=utm"
       "+zone=35"
       "+ellps=GRS80"
       "+towgs84=0,0,0,0,0,0,0"
       "+units=m"
       "+no_defs"))

(defn init! []
  (proj4/defs "EPSG:3067" epsg3067-defs)
  (register proj4)

  (let [proj (proj/get "EPSG:3067")]
    (.setExtent proj epsg3067-extent)
    {:proj4 proj4 :epsg3067 proj}))

(def proj (init!))

(def ^js epsg3067 (:epsg3067 proj))
(def epsg3067-top-left (extent/getTopLeft (.getExtent epsg3067)))


(def MapContext (uix/create-context))
(def MapContextProvider (.-Provider MapContext))

(defn ^js use-ol []
  (let [ctx (uix/use-context MapContext)]
    (.-current (:ol-ref ctx))))

(defui WmtsLayer
  "Dynamic props:
  - :visible?
  - :opacity"
  [{:keys [url layer-name base-layer? min-res max-res
           resolutions matrix-ids
           visible? opacity]
    :or   {visible?    false
           base-layer? true
           max-res     8192
           min-res     0.25
           resolutions mml-resolutions
           matrix-ids  mml-matrix-ids}}]
  (let [ol (use-ol)

        ;; TODO: Is is it a problem that the fn is created each time? but only called on the init
        ;; Make this a macro?
        [_ ^js source]
        (use-object (WMTSSource. #js {:url             url
                                      :layer           layer-name
                                      :projection      "EPSG:3067"
                                      :matrixSet       "mml_grid"
                                      :tileGrid        (WMTSTileGrid. #js {:origin      epsg3067-top-left
                                                                           :extent      epsg3067-extent
                                                                           :resolutions resolutions
                                                                           :matrixIds   matrix-ids})
                                      :format          "png"
                                      :requestEncoding "REST"
                                      :isBaseLayer     base-layer?}))

        [_ ^js layer]
        (use-object (TileLayer. #js {:visible       visible?
                                     :opacity       1.0
                                     :minResolution min-res
                                     :maxResolution max-res
                                     :source        source}))]

    ;; mount and unmount the layer
    (uix/use-effect
      (fn []
        (.addLayer ol layer)
        (fn []
          (.removeLayer ol layer)))
      [ol layer])

    ;; toggle visible
    (uix/use-effect (fn [] (.setVisible layer visible?)) [layer visible?])
    (uix/use-effect (fn [] (when opacity (.setOpacity layer opacity))) [layer opacity])

    nil))

(defui ImageLayerWMS
  "Dynamic props:
  - visible"
  [{:keys [visible source-props]}]
  (let [ol (use-ol)
        [_ ^js source]
        (use-object (ImageWMSSource. source-props))

        [_ ^js layer]
        (use-object (ImageLayer. #js {:source source
                                      :visible visible}))]
    (uix/use-effect
      (fn []
        (.addLayer ol layer)
        (fn []
          (.removeLayer ol layer)))
      [ol layer])
    (uix/use-effect (fn [] (.setVisible layer visible)) [layer visible])
    nil))

(defui map-inner [{:keys [map-el center zoom children]}]
  (let [[_ ^js view]
        (use-object (ol/View. #js {:center              #js [(:lon center) (:lat center)]
                                   :extent              epsg3067-extent
                                   :showFullExtent      true
                                   :constrainOnlyCenter true
                                   :zoom                zoom
                                   :projection          "EPSG:3067"
                                   :resolutions         mml-resolutions
                                   :units               "m"
                                   :enableRotation      false}))

        [ol-ref ^js _ol]
        (use-object (ol/Map. #js {:target map-el
                                  :layers #js []
                                  :controls #js []
                                  :overlays #js []
                                  :view view}))

        ctx
        (uix/use-memo (fn []
                        {:map-el map-el
                         :ol-ref ol-ref})
                      [map-el ol-ref])]

    ($ MapContextProvider
       {:value ctx}
       children)))

(defui map-container [props]
  (let [[map-el set-map-el] (uix/use-state nil)
        map-el-ref-fn (uix/use-callback (fn [el] (set-map-el el)) [])]
    ($ Stack
       {:ref map-el-ref-fn
        :sx #js {:flex 1}
        :tabIndex -1}
       ;; Delay the OL component initialization to after the target DOM element is mounted.
       (when map-el
         ($ map-inner (assoc props :map-el map-el))))))

(defui baselayers []
  (let [basemap (use-subscribe [::subs/basemap])]
    ($ :<>
       ($ WmtsLayer
          {:url        (:taustakartta urls)
           :layer-name "MML-Taustakartta"
           :visible?   (= :taustakartta (:layer basemap))
           :opacity    (:opacity basemap)})
       ($ WmtsLayer
          {:url        (:maastokartta urls)
           :layer-name "MML-Maastokartta"
           :visible?   (= :maastokartta (:layer basemap))
           :opacity    (:opacity basemap)})
       ($ WmtsLayer
          {:url        (:ortokuva urls)
           :layer-name "MML-Ortokuva"
           :visible?   (= :ortokuva (:layer basemap))
           :opacity    (:opacity basemap)}))))

(defui overlays []
  (let [selected-overlays (use-subscribe [::subs/selected-overlays])]
    (js/console.log selected-overlays)
    ($ :<>
       ;; TODO: Rest of the overlays are used by search results, edit tools etc.?

       ; :vectors
       ; (VectorImageLayer.
       ;   #js {:source (VectorSource.)
       ;        :name   "vectors"
       ;        :style  styles/feature-style})
       ; :lois
       ; (VectorImageLayer.
       ;   #js {:source (VectorSource.)
       ;        :name   "lois"
       ;        :style  styles/loi-style})
       ; :edits
       ; (VectorLayer.
       ;   #js {:source (VectorSource.)
       ;        :name   "edits"
       ;        :zIndex 10
       ;        :style  #js [styles/edit-style styles/vertices-style]})
       ; :highlights
       ; (VectorLayer.
       ;   #js {:source (VectorSource.)
       ;        :name   "highlights"
       ;        :style  #js [styles/highlight-style]})
       ; :markers
       ; (VectorLayer.
       ;   #js {:source (VectorSource.)
       ;        :name   "markers"
       ;        :style  styles/blue-marker-style})
       ; :analysis
       ; (VectorLayer.
       ;   #js {:source (VectorSource.)
       ;        :style  styles/analysis-style
       ;        :name   "analysis"})
       ; :population
       ; (VectorImageLayer.
       ;   #js {:source (VectorSource.)
       ;        :style  styles/population-style3
       ;        :name   "population"})

       ; :schools
       ; (VectorImageLayer.
       ;   #js {:source (VectorSource.)
       ;        :style  styles/school-style
       ;        :name   "schools"})
       ; :diversity-grid
       ; (VectorImageLayer.
       ;   #js {:source (VectorSource.)
       ;        :style  styles/diversity-grid-style
       ;        :name   "diversity-grid"})
       ; :diversity-area
       ; (VectorImageLayer.
       ;   #js {:source (VectorSource.)
       ;        :style  styles/diversity-area-style
       ;        :name   "diversity-area"})

       ($ ImageLayerWMS
          {:visible (contains? selected-overlays :light-traffic)
           :source-props #js {:url         "/vaylavirasto/vaylatiedot/ows"
                              :params      #js {:LAYERS #_"TL166" "tierekisteri:tl166"}
                              :serverType  "geoserver"
                              :crossOrigin "anonymous"}})

       ($ ImageLayerWMS
          {:visible (contains? selected-overlays :retkikartta-snowmobile-tracks)
           :source-props #js {:url         "/geoserver/lipas/wms?"
                              :params      #js {:LAYERS "lipas:metsahallitus_urat2023"}
                              :serverType  "geoserver"
                              :crossOrigin "anonymous"}})

       ($ WmtsLayer
          {:url         (:kiinteisto urls)
           ;; Source (MML WMTS) won't return anything with res 0.25 so we
           ;; limit this layer grid to min resolution of 0.5 but allow
           ;; zooming to 0.25. Limiting the grid has a desired effect that
           ;; WMTS won't try to get the data and it shows geoms of
           ;; the "previous" resolution.
           :resolutions (.slice mml-resolutions 0 15)
           :matrix-ids  (.slice mml-matrix-ids 0 15)
           :min-res     0.25
           :max-res     8
           :layer-name  "MML-Kiinteistö"
           :visible?    (contains? selected-overlays :mml-kiinteisto)})

       ($ WmtsLayer
          {:url         (:kiinteistotunnukset urls)
           ;; Source (MML WMTS) won't return anything with res 0.25 so we
           ;; limit this layer grid to min resolution of 0.5 but allow
           ;; zooming to 0.25. Limiting the grid has a desired effect that
           ;; WMTS won't try to get the data and it shows geoms of
           ;; the "previous" resolution.
           :resolutions (.slice mml-resolutions 0 15)
           :matrix-ids  (.slice mml-matrix-ids 0 15)
           :min-res     0.25
           :max-res     8
           :layer-name  "MML-Kiinteistötunnukset"
           :visible?    (contains? selected-overlays :mml-kiinteistotunnukset)})

       ($ WmtsLayer
          {:url        (:kuntarajat urls)
           :layer-name "MML-Kuntarajat"
           :visible?   (contains? selected-overlays :mml-kuntarajat)}))))

(defui map-view []
  ;; Subscribe to re-frame data here, then just pass to the pure components?
  (let [;; geoms   (use-subscribe [::subs2/geometries])
        center  (use-subscribe [::subs/center])
        zoom    (use-subscribe [::subs/zoom])]
    ;; NOTE: Avoid adding Lipas specific props to map-container/map-inner
    ;; Most of stuff for Lipas map should be handled by children components (like baselayers)
    ;; Children can just subscribe to specific rf subs they need.
    ($ map-container
       {:center center
        :zoom zoom}
       ($ baselayers)
       ($ overlays))))
