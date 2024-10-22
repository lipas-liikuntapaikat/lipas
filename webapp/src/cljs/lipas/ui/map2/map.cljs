(ns lipas.ui.map2.map
  (:require ["@mui/material/Stack$default" :as Stack]
            ["ol-new" :as ol]
            ["ol-new/extent" :as extent]
            ["ol-new/layer/Tile$default" :as TileLayer]
            ["ol-new/proj" :as proj]
            ["ol-new/proj/proj4" :refer [register]]
            ["ol-new/source/WMTS$default" :as WMTSSource]
            ["ol-new/tilegrid/WMTS$default" :as WMTSTileGrid]
            ["proj4" :as proj4]
            [lipas.ui.map.subs :as subs]
            [lipas.ui.map2.subs :as subs2]
            [lipas.ui.uix.hooks :refer [use-subscribe]]
            [uix.core :as uix :refer [$ defui]]))

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

(defui WmtsLayer
  [{:keys [url layer-name visible? base-layer? min-res max-res
           resolutions matrix-ids]
    :or   {visible?    false
           base-layer? true
           max-res     8192
           min-res     0.25
           resolutions mml-resolutions
           matrix-ids  mml-matrix-ids}}]
  (let [layer-ref (uix/use-ref)
        {:keys [ol-ref]} (uix/use-context MapContext)]
    ;; TODO: Same comments about not using use-effect for the initial initialization as in map component
    (uix/use-effect (fn []
                      (let [layer (TileLayer. #js {:visible       visible?
                                                   :opacity       1.0
                                                   :minResolution min-res
                                                   :maxResolution max-res
                                                   :source (WMTSSource. #js {:url             url
                                                                             :layer           layer-name
                                                                             :projection      "EPSG:3067"
                                                                             :matrixSet       "mml_grid"
                                                                             :tileGrid        (WMTSTileGrid. #js {:origin      epsg3067-top-left
                                                                                                                  :extent      epsg3067-extent
                                                                                                                  :resolutions resolutions
                                                                                                                  :matrixIds   matrix-ids})
                                                                             :format          "png"
                                                                             :requestEncoding "REST"
                                                                             :isBaseLayer     base-layer?})})
                            ol (.-current ol-ref)]
                        ;; (js/console.log "init layer" ol-ref ol)
                        (set! (.-current layer-ref) layer)
                        (.addLayer ol layer)
                        (fn []
                          (.removeLayer ol layer))))
                    ^:lint/disable
                    [])
    nil))

(defui map-container [{:keys [center zoom]}]
  (let [map-el-ref (uix/use-ref)
        ol-ref (uix/use-ref)
        view-ref (uix/use-ref)
        ctx (uix/use-memo (fn []
                            {:map-el-ref map-el-ref
                             :ol-ref ol-ref})
                          [])]
    ;; FIXME: This isn't really correct, but
    ;; regular effect would run AFTER child component effects.
    ;; Need to consider better way for these interop effects.
    (uix/use-layout-effect (fn []
                             (let [view (ol/View. #js {:center              #js [(:lon center) (:lat center)]
                                                       :extent              epsg3067-extent
                                                       :showFullExtent      true
                                                       :constrainOnlyCenter true
                                                       :zoom                zoom
                                                       :projection          "EPSG:3067"
                                                       :resolutions         mml-resolutions
                                                       :units               "m"
                                                       :enableRotation      false})
                                   opts #js {:target (.-current map-el-ref)
                                             :layers #js []
                                             :controls #js []
                                             :overlays #js []
                                             :view view}
                                   ol (ol/Map. opts)]
                               ;; (js/console.log "init ol" ol)
                               (set! (.-current ol-ref) ol)))
                           ;; FIXME: This effect just handles the initial setup,
                           ;; handling the property changes is outside of this effect.
                           ;; Maybe this shouldn't be an effect at all?
                           ^:lint/disable
                           [])
    ($ MapContextProvider
       {:value ctx}
       ($ Stack
          {:ref map-el-ref
           :sx #js {:flex 1}
           :tabIndex -1})
       ($ WmtsLayer
          {:url (:taustakartta urls)
           :layer-name "MML-Taustakartta"
           :visible? true}))))

(defui map-view []
  ;; Subscribe to re-frame data here, then just pass to the pure components?
  (let [geoms  (use-subscribe [::subs2/geometries])
        center (use-subscribe [::subs/center])
        zoom   (use-subscribe [::subs/zoom])]
    (js/console.log geoms)
    ($ map-container
       {:center center
        :zoom zoom})))
