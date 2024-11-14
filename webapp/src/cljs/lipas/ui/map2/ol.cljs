(ns lipas.ui.map2.ol
  (:require ["ol-new/layer/Image$default" :as ImageLayer]
            ["ol-new/layer/Tile$default" :as TileLayer]
            ["ol-new/layer/Vector$default" :as OLVectorLayer]
            ["ol-new/layer/VectorImage$default" :as OLVectorImageLayer]
            ["ol-new/source/ImageWMS$default" :as ImageWMSSource]
            ["ol-new/source/Vector$default" :as VectorSource]
            ["ol-new/source/WMTS$default" :as WMTSSource]
            ["ol-new/tilegrid/WMTS$default" :as WMTSTileGrid]
            [lipas.ui.map2.projection :as projection]
            [lipas.ui.map2.utils :refer [use-object use-ol]]
            [uix.core :as uix :refer [$ defui]]))

(def SourceContext (uix/create-context))
(def SourceContextProvider (.-Provider SourceContext))

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
           min-res     0.25}}]
  (let [ol (use-ol)

        ;; TODO: Is is it a problem that the fn is created each time? but only called on the init
        ;; Make this a macro?
        [_ ^js source]
        (use-object (WMTSSource. #js {:url             url
                                      :layer           layer-name
                                      :projection      "EPSG:3067"
                                      :matrixSet       "mml_grid"
                                      :tileGrid        (WMTSTileGrid. #js {:origin      projection/epsg3067-top-left
                                                                           :extent      projection/epsg3067-extent
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

(defui VectorImageLayer [{:keys [name style children]}]
  (let [ol (use-ol)

        [_ ^js source]
        (use-object (VectorSource.))

        [_ ^js layer]
        (use-object (OLVectorImageLayer.
                      #js {:source source
                           :name name
                           :style style}))]

    (uix/use-effect
      (fn []
        (.addLayer ol layer)
        (fn []
          (.removeLayer ol layer)))
      [ol layer])

    ($ SourceContextProvider
       {:value source}
       children)))

(defui VectorLayer [{:keys [name style children]}]
  (let [ol (use-ol)

        [_ ^js source]
        (use-object (VectorSource.))

        [_ ^js layer]
        (use-object (OLVectorLayer.
                      #js {:source source
                           :name name
                           :style style}))]

    (uix/use-effect
      (fn []
        (.addLayer ol layer)
        (fn []
          (.removeLayer ol layer)))
      [ol layer])

    ($ SourceContextProvider
       {:value source}
       children)))

(defui GeoJSONMarker [{:keys [geojson style]}]
  nil)

(defui SelectInteraction []
  nil)
