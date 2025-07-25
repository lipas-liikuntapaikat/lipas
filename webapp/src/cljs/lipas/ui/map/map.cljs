(ns lipas.ui.map.map
  (:require ["ol" :as ol]
            ["ol/events/condition" :as events-condition]
            ["ol/extent" :as extent]
            ["ol/interaction/DoubleClickZoom$default" :as DoubleClickZoom]
            ["ol/interaction/DragPan$default" :as DragPan]
            ["ol/interaction/KeyboardPan$default" :as KeyboardPan]
            ["ol/interaction/KeyboardZoom$default" :as KeyboardZoom]
            ["ol/interaction/MouseWheelZoom$default" :as MouseWheelZoom]
            ["ol/interaction/PinchZoom$default" :as PinchZoom]
            ["ol/interaction/Select$default" :as SelectInteraction]
            ["ol/layer/Image$default" :as ImageLayer]
            ["ol/layer/Tile$default" :as TileLayer]
            ["ol/layer/Vector$default" :as VectorLayer]
            ["ol/layer/VectorImage$default" :as VectorImageLayer]
            ["ol/proj" :as ol-proj]
            ["ol/source/ImageWMS$default" :as ImageWMSSource]
            ["ol/source/Vector$default" :as VectorSource]
            ["ol/source/WMTS$default" :as WMTSSource]
            ["ol/tilegrid/WMTS$default" :as WMTSTileGrid]
            [lipas.ui.analysis.reachability.events]
            [lipas.ui.map.editing :as editing]
            [lipas.ui.map.events :as events]
            [lipas.ui.map.projection :as proj]
            [lipas.ui.map.styles :as styles]
            [lipas.ui.map.subs :as subs]
            [lipas.ui.map.utils :as map-utils]
            [lipas.ui.analysis.heatmap.map :as heatmap]
            [lipas.ui.mui :as mui]
            [lipas.ui.utils :refer [==>] :as utils]
            [re-frame.core :as rf]
            [reagent.core :as r]))

#_(set! *warn-on-infer* true)

(def mml-resolutions
  #js [8192, 4096, 2048, 1024, 512, 256, 128, 64, 32, 16, 8, 4, 2, 1, 0.5, 0.25])

(def mml-matrix-ids (clj->js (range (count mml-resolutions))))

(defn ->wmts-url [layer-name]
  (str "/mapproxy/wmts/"
       layer-name
       "/{TileMatrixSet}/{TileMatrix}/{TileCol}/{TileRow}.png"))

(def urls
  {:taustakartta (->wmts-url "mml_taustakartta")
   :maastokartta (->wmts-url "mml_maastokartta")
   :ortokuva (->wmts-url "mml_ortokuva")
   :kiinteisto (->wmts-url "mml_kiinteisto")
   :kiinteistotunnukset (->wmts-url "mml_kiinteistotunnukset")
   :kuntarajat (->wmts-url "mml_kuntarajat")})

(defn ->wmts
  [{:keys [url layer-name visible? base-layer? min-res max-res
           resolutions matrix-ids]
    :or {visible? false
         base-layer? true
         max-res 8192
         min-res 0.25
         resolutions mml-resolutions
         matrix-ids mml-matrix-ids}}]
  (TileLayer. #js {:visible visible?
                   :opacity 1.0
                   :minResolution min-res
                   :maxResolution max-res
                   :source (WMTSSource. #js {:url url
                                             :layer layer-name
                                             :projection "EPSG:3067"
                                             :matrixSet "mml_grid"
                                             :tileGrid (WMTSTileGrid. #js {:origin proj/epsg3067-top-left
                                                                           :extent proj/epsg3067-extent
                                                                           :resolutions resolutions
                                                                           :matrixIds matrix-ids})
                                             :format "png"
                                             :requestEncoding "REST"
                                             :isBaseLayer base-layer?})}))

(defn init-layers []
  {:basemaps
   {:taustakartta
    (->wmts
      {:url (:taustakartta urls) :layer-name "MML-Taustakartta" :visible? true})
    :maastokartta
    (->wmts
      {:url (:maastokartta urls) :layer-name "MML-Maastokartta"}) :ortokuva
    (->wmts
      {:url (:ortokuva urls) :layer-name "MML-Ortokuva"})}
   :overlays
   {:vectors
    (VectorImageLayer.
      #js {:source (VectorSource.)
           :name "vectors"
           :style styles/feature-style})
    :lois
    (VectorImageLayer.
      #js {:source (VectorSource.)
           :name "lois"
           :style styles/loi-style})
    :edits
    (VectorLayer.
      #js {:source (VectorSource.)
           :name "edits"
           :zIndex 10
           :style #js [styles/edit-style styles/vertices-style]})
    :highlights
    (VectorLayer.
      #js {:source (VectorSource.)
           :name "highlights"
           :style #js [styles/highlight-style]})
    :markers
    (VectorLayer.
      #js {:source (VectorSource.)
           :name "markers"
           :style styles/blue-marker-style})
    :analysis
    (VectorLayer.
      #js {:source (VectorSource.)
           :style styles/analysis-style
           :name "analysis"})
    :population
    (VectorImageLayer.
      #js {:source (VectorSource.)
           :style styles/population-style3
           :name "population"})

    :schools
    (VectorImageLayer.
      #js {:source (VectorSource.)
           :style styles/school-style
           :name "schools"})
    :diversity-grid
    (VectorImageLayer.
      #js {:source (VectorSource.)
           :style styles/diversity-grid-style
           :name "diversity-grid"})
    :diversity-area
    (VectorImageLayer.
      #js {:source (VectorSource.)
           :style styles/diversity-area-style
           :name "diversity-area"})
    :light-traffic
    (ImageLayer.
      #js {:visible false
           :source (ImageWMSSource. #js {:url "/vaylavirasto/vaylatiedot/ows"
                                         :params #js {:LAYERS #_"TL166" "tierekisteri:tl166"}
                                         :serverType "geoserver"
                                         :crossOrigin "anonymous"})})
    :retkikartta-snowmobile-tracks
    (ImageLayer.
      #js {:visible false
           :source (ImageWMSSource. #js {:url "/geoserver/lipas/wms?"
                                         :params #js {:LAYERS "lipas:metsahallitus_urat2023"}
                                         :serverType "geoserver"
                                         :crossOrigin "anonymous"})})
    :mml-kiinteisto
    (->wmts
      {:url (:kiinteisto urls)
      ;; Source (MML WMTS) won't return anything with res 0.25 so we
      ;; limit this layer grid to min resolution of 0.5 but allow
      ;; zooming to 0.25. Limiting the grid has a desired effect that
      ;; WMTS won't try to get the data and it shows geoms of
      ;; the "previous" resolution.
       :resolutions (.slice mml-resolutions 0 15)
       :matrix-ids (.slice mml-matrix-ids 0 15)
       :min-res 0.25
       :max-res 8
       :layer-name "MML-Kiinteistö"})
    :mml-kiinteistotunnukset
    (->wmts
      {:url (:kiinteistotunnukset urls)
      ;; Source (MML WMTS) won't return anything with res 0.25 so we
      ;; limit this layer grid to min resolution of 0.5 but allow
      ;; zooming to 0.25. Limiting the grid has a desired effect that
      ;; WMTS won't try to get the data and it shows geoms of
      ;; the "previous" resolution.
       :resolutions (.slice mml-resolutions 0 15)
       :matrix-ids (.slice mml-matrix-ids 0 15)
       :min-res 0.25
       :max-res 8
       :layer-name "MML-Kiinteistötunnukset"})
    :mml-kuntarajat
    (->wmts
      {:url (:kuntarajat urls)
       :layer-name "MML-Kuntarajat"})
    :heatmap (heatmap/create-heatmap-layer (heatmap/create-heatmap-source))}})

(defn init-view [center zoom]
  ;; TODO: Juho later Left side padding
  (ol/View. #js {:center #js [(:lon center) (:lat center)]
                 :extent proj/epsg3067-extent
                 :showFullExtent true
                 :constrainOnlyCenter true
                 :zoom zoom
                 :projection "EPSG:3067"
                 :resolutions mml-resolutions
                 :units "m"
                 :enableRotation false}))

(defn init-overlay [popup-ref]
  (ol/Overlay.
    #js {:offset #js [-15 0]
         :element (.-current popup-ref)}))

(defn init-map! [{:keys [center zoom popup-ref]}]

  #_(when ^boolean goog.DEBUG
      (js/console.log "Creating new OpenLayers map instance")
      (js/setTimeout #(js/console.log "Active WebGL contexts:"
                                      (.-length (.querySelectorAll js/document "canvas"))) 100))

  (let [layers (init-layers)
        view (init-view center zoom)
        popup-overlay (init-overlay popup-ref)

        opts #js {:target "map"
                  :layers #js [(-> layers :basemaps :taustakartta)
                               (-> layers :basemaps :maastokartta)
                               (-> layers :basemaps :ortokuva)
                               (-> layers :overlays :analysis)
                               (-> layers :overlays :population)
                               (-> layers :overlays :schools)
                               (-> layers :overlays :diversity-area)
                               (-> layers :overlays :diversity-grid)
                               (-> layers :overlays :vectors)
                               (-> layers :overlays :lois)
                               (-> layers :overlays :edits)
                               (-> layers :overlays :highlights)
                               (-> layers :overlays :markers)
                               (-> layers :overlays :light-traffic)
                               (-> layers :overlays :retkikartta-snowmobile-tracks)
                               (-> layers :overlays :mml-kiinteisto)
                               (-> layers :overlays :mml-kiinteistotunnukset)
                               (-> layers :overlays :mml-kuntarajat)
                               (-> layers :overlays :heatmap)]
                  :interactions #js [(MouseWheelZoom.)
                                     (KeyboardZoom.)
                                     (KeyboardPan.)
                                     (PinchZoom.)
                                     (DragPan.)
                                     (DoubleClickZoom.)]
                  :overlays #js [popup-overlay]
                  :view view}

        vector-hover (SelectInteraction.
                       #js {:layers #js [(-> layers :overlays :vectors)]
                            :style styles/feature-style-hover
                            :multi true
                            :condition events-condition/pointerMove})

        loi-hover (SelectInteraction.
                    #js {:layers #js [(-> layers :overlays :lois)]
                         :style styles/loi-style-hover
                         :multi true
                         :condition events-condition/pointerMove})

        marker-hover (SelectInteraction.
                       #js {:layers #js [(-> layers :overlays :markers)]
                            :style styles/feature-style-hover
                            :multi true
                            :condition events-condition/pointerMove})

        population-hover (SelectInteraction.
                           #js {:layers #js [(-> layers :overlays :population)]
                                :style styles/population-hover-style3
                                :multi false
                                :condition events-condition/pointerMove})

        schools-hover (SelectInteraction.
                        #js {:layers #js [(-> layers :overlays :schools)]
                             :style styles/school-hover-style
                             :multi false
                             :condition events-condition/pointerMove})

        diversity-grid-hover (SelectInteraction.
                               #js {:layers #js [(-> layers :overlays :diversity-grid)]
                                    :style styles/diversity-grid-hover-style
                                    :multi false
                                    :condition events-condition/pointerMove})

        diversity-area-hover (SelectInteraction.
                               #js {:layers #js [(-> layers :overlays :diversity-area)]
                                    :style styles/diversity-area-hover-style
                                    :multi false
                                    :condition events-condition/pointerMove})

        diversity-area-select (SelectInteraction.
                                #js {:layers #js [(-> layers :overlays :diversity-area)]})

        heatmap-hover (SelectInteraction.
                        #js {:layers #js [(-> layers :overlays :heatmap)]
                             :style nil ; Heatmap has its own style
                             :multi false
                             :condition events-condition/pointerMove})

        select (SelectInteraction. #js {:layers #js [(-> layers :overlays :vectors)
                                                     (-> layers :overlays :lois)]
                                        :style styles/feature-style-selected})

        lmap (ol/Map. opts)]

    (.on vector-hover "select"
         (fn [^js e]
           (let [coords (some-> e .-mapBrowserEvent .-coordinate)
                 selected (.-selected e)]

             ;; Uncommenting this would enable selecting all geoms
             ;; attached to Lipas-ID on hover. However this causes
             ;; terrible flickering and workaround hasn't been found
             ;; yet.
             ;;
             ;; (let [f1       (aget selected 0)
             ;;       lipas-id (when f1 (.get f1 "lipas-id"))
             ;;       fs       (map-utils/find-features-by-lipas-id
             ;;                 {:layers layers} lipas-id)]
             ;;   (doto (.getFeatures vector-hover)
             ;;     (.clear)
             ;;     (.extend fs)))

             (.setPosition popup-overlay coords)
             (==> [::events/show-popup
                   (when (not-empty selected)
                     {:data (-> selected map-utils/->geoJSON-clj)})]))))

    (.on loi-hover "select"
         (fn [^js e]
           (let [coords (some-> e .-mapBrowserEvent .-coordinate)
                 selected (.-selected e)]

             (.setPosition popup-overlay coords)
             #_(js/console.log (aget selected 0))
             (==> [::events/show-popup
                   (when (not-empty selected)
                     {:type :loi
                      :data (-> selected
                                map-utils/->geoJSON-clj)})]))))

    (.on marker-hover "select"
         (fn [^js e]
           (let [coords (some-> e .-mapBrowserEvent .-coordinate)
                 selected (.-selected e)]

             (.setPosition popup-overlay coords)
             (==> [::events/show-popup
                   (when (not-empty selected)
                     {:data (-> selected map-utils/->geoJSON-clj)})]))))

    (.on population-hover "select"
         (fn [^js e]
           (let [coords (some-> e .-mapBrowserEvent .-coordinate)
                 selected (.-selected e)]

             (.setPosition popup-overlay coords)
             (==> [::events/show-popup
                   (when (not-empty selected)
                     {:type :population
                      :data (-> selected map-utils/->geoJSON-clj)})]))))

    (.on schools-hover "select"
         (fn [^js e]
           (let [coords (some-> e .-mapBrowserEvent .-coordinate)
                 selected (.-selected e)]

             (.setPosition popup-overlay coords)
             (==> [::events/show-popup
                   (when (not-empty selected)
                     {:type :school
                      :data (-> selected map-utils/->geoJSON-clj)})]))))

    (.on diversity-grid-hover "select"
         (fn [^js e]
           (let [coords (some-> e .-mapBrowserEvent .-coordinate)
                 selected (.-selected e)]

             (.setPosition popup-overlay coords)
             (==> [::events/show-popup
                   (when (not-empty selected)
                     {:type :diversity-grid
                      :data (-> selected map-utils/->geoJSON-clj)})]))))

    (.on diversity-area-hover "select"
         (fn [^js e]
           (let [coords (some-> e .-mapBrowserEvent .-coordinate)
                 selected (.-selected e)]

             (.setPosition popup-overlay coords)
             (==> [::events/show-popup
                   (when (not-empty selected)
                     {:type :diversity-area
                      :data (-> selected map-utils/->geoJSON-clj)})]))))

    (.on diversity-area-select "select"
         (fn [^js e]
           (let [selected (.-selected e)
                 f1 (when (seq selected) (aget selected 0))
                 fid (when f1 (.get f1 "id"))]
             (when fid
               (==> [:lipas.ui.analysis.diversity.events/calc-diversity-indices {:id fid}])))))

    (.on heatmap-hover "select"
         (fn [^js e]
           (let [coords (some-> e .-mapBrowserEvent .-coordinate)
                 selected (.-selected e)
                 deselected (.-deselected e)
                 ;; Get the actual feature center from geometry
                 feature-coords (when (not-empty selected)
                                  (let [^js feature (aget selected 0)
                                        geometry (.getGeometry feature)]
                                    (.getCoordinates geometry)))]

             (.setPosition popup-overlay coords)

             ;; Show highlight for hovered feature using feature's actual coordinates
             (when feature-coords
               (map-utils/show-heatmap-hover-highlight! {:layers layers :view view} feature-coords))

             ;; Clear highlight when nothing is hovered
             (when (and (empty? selected) (not-empty deselected))
               (map-utils/clear-highlights! {:layers layers}))

             (==> [::events/show-popup
                   (when (not-empty selected)
                     {:type :heatmap
                      :data (-> selected map-utils/->geoJSON-clj)})]))))

    ;; It's not possible to have multiple selects with
    ;; same "condition" (at least I couldn't get it
    ;; working). Therefore we have to detect which layer we're
    ;; selecting from and decide actions accordingly.
    (.on select "select"
         (fn [^js e]
           (let [coords (some-> e .-mapBrowserEvent .-coordinate)
                 selected (.-selected e)
                 f1 (aget selected 0)
                 lipas-id (when f1 (.get f1 "lipas-id"))
                 loi-id (when f1 (and (.get f1 "loi-type") (.get f1 "loi-id")))]
             (.setPosition popup-overlay coords)

             (cond
               lipas-id (==> [::events/sports-site-selected e lipas-id])
               loi-id (==> [::events/loi-selected e loi-id])
               :else (==> [::events/unselected e])))))

    (.on lmap "click"
         (fn [e]
           (==> [::events/map-clicked e])))

    (.on lmap "moveend"
         (fn [_]
           (let [center (.getCenter view)
                 lonlat (when (seq center) (ol-proj/toLonLat center proj/epsg3067))
                 zoom (.getZoom view)
                 extent (.calculateExtent view)
                 width (extent/getWidth extent)
                 height (extent/getHeight extent)]

             (when (and (seq center) (> width 0) (> height 0))
               (==> [::events/set-view center lonlat zoom extent width height])))))

    {:lmap lmap
     :view view
     :center center
     :zoom zoom
     :layers layers
     ;; We don't re-create :hover and :select each time when we toggle
     ;; them because it causes buggy behavior. We keep refs to
     ;; singleton instances under special :interactions* key in
     ;; map-ctx where we can find them when they need to be enabled.
     :interactions*
     {:select select
      :vector-hover vector-hover
      :loi-hover loi-hover
      :marker-hover marker-hover
      :population-hover population-hover
      :schools-hover schools-hover
      :diversity-grid-hover diversity-grid-hover
      :diversity-area-hover diversity-area-hover
      :diversity-area-select diversity-area-select
      :heatmap-hover heatmap-hover}
     :overlays {:popup popup-overlay}}))

(defn show-population!
  [{:keys [layers] :as map-ctx}
   {:keys [selected-sports-site zones] :as analysis}]

  (let [{:keys [population]} (get-in analysis [:runs selected-sports-site])
        ^js layer (-> layers :overlays :population)
        source (.getSource layer)
        metric (:selected-travel-metric analysis)
        profile (:selected-travel-profile analysis)]

    (.clear source)

    ;; Add selected style to sports-site feature
    ;;(when lipas-id (display-as-selected map-ctx lipas-id))
    (when selected-sports-site
      (map-utils/select-sports-site! map-ctx selected-sports-site {:maxZoom 7}))

    (when-let [data (:data population)]
      (doseq [m data
              :let [f (map-utils/<-wkt (:coords m))
                    zone-id (get-in m [:zone profile metric])]
              :when zone-id]
        (.set f "vaesto" (:vaesto m))
        (.set f "zone" zone-id)
        (.set f "color" (get-in zones [:colors zone-id]))
        (.addFeature source f)))

    map-ctx))

(defn show-schools!
  [{:keys [layers] :as map-ctx}
   {:keys [selected-sports-site] :as analysis}]
  (let [{:keys [schools]} (get-in analysis [:runs selected-sports-site])
        ^js layer (-> layers :overlays :schools)]
    (-> layer .getSource .clear)

    ;; Add selected style to sports-site feature
    ;;(when lipas-id (display-as-selected map-ctx lipas-id))
    (when selected-sports-site
      (map-utils/select-sports-site! map-ctx selected-sports-site {:maxZoom 7}))

    (when-let [data (:data schools)]
      (doseq [m data]
        (let [source (.getSource layer)
              f (map-utils/<-wkt (:coords m))]
          (.set f "name" (:name m))
          (.set f "type" (:type m))
          #_(.set f "selected" true)
          (.addFeature source f)))))

  map-ctx)

(defn show-diversity-grid!
  [{:keys [layers] :as map-ctx}
   {:keys [data results]}]
  (let [^js layer (-> layers :overlays :diversity-grid)]
    (-> layer .getSource .clear)

    (when (seq results)
      (let [source (.getSource layer)]
        (doseq [fcoll (map :grid (vals results))
                f (:features fcoll)]
          (.addFeature source (-> f clj->js map-utils/->ol-feature))))))

  map-ctx)

;; Browsing and selecting features
(defn set-default-mode!
  [map-ctx {:keys [lipas-id address elevation]}]
  (-> map-ctx
      editing/clear-edits!
      map-utils/clear-population!
      map-utils/unselect-features!
      map-utils/clear-interactions!
      map-utils/clear-markers!
      map-utils/clear-highlights!
      map-utils/enable-vector-hover!
      map-utils/enable-marker-hover!
      map-utils/enable-loi-hover!
      map-utils/enable-select!
      (cond->
        lipas-id (map-utils/select-sports-site! lipas-id)
        address (map-utils/show-address-marker! address)
        elevation (-> (map-utils/show-elevation-marker! elevation)
                      #_(map-utils/highlight-segment! elevation)))))

(defn update-default-mode!
  [{:keys [layers] :as map-ctx}
   {:keys [lipas-id fit-nonce address elevation]}]
  (let [fit? (and fit-nonce (not= fit-nonce (-> map-ctx :mode :fit-nonce)))
        ^js layer (-> layers :overlays :vectors)]
    (-> map-ctx
        (map-utils/clear-markers!)
        (map-utils/unselect-features!)
        (map-utils/clear-population!)
        (map-utils/clear-highlights!)
        (cond->
          lipas-id (map-utils/select-sports-site! lipas-id)
          fit? (map-utils/fit-to-extent! (-> layer .getSource .getExtent))
          address (map-utils/show-address-marker! address)
          elevation (-> (map-utils/show-elevation-marker! elevation)
                        #_(map-utils/highlight-segment! elevation))))))

(defn set-reachability-mode!
  [map-ctx {:keys [analysis]}]
  (let [reachability (:reachability analysis)]
    (-> map-ctx
        editing/clear-edits!
        map-utils/clear-population!
        map-utils/unselect-features!
        map-utils/clear-interactions!
        map-utils/clear-markers!
        map-utils/enable-vector-hover!
        map-utils/enable-marker-hover!
        map-utils/enable-select!
        (map-utils/enable-population-hover!)
        (show-population! reachability)
        (show-schools! reachability)
        (map-utils/enable-schools-hover!)
        (map-utils/draw-analytics-buffer! reachability))))

(defn set-diversity-mode!
  [{:keys [layers] :as map-ctx}
   {:keys [analysis] :as mode}]
  (let [diversity (:diversity analysis)
        ^js layer (-> layers :overlays :diversity-area)]
    (-> map-ctx
        editing/clear-edits!
        map-utils/clear-population!
        map-utils/unselect-features!
        map-utils/clear-interactions!
        map-utils/clear-markers!
        map-utils/enable-diversity-area-select!
        (map-utils/draw-diversity-areas! diversity)
        (map-utils/enable-diversity-area-hover!)
        (map-utils/enable-diversity-grid-hover!)
        (show-diversity-grid! diversity)
        (map-utils/fit-to-extent! (-> layer .getSource .getExtent)))))

(defn set-heatmap-mode!
  [{:keys [layers] :as map-ctx}
   {:keys [analysis] :as mode}]
  (let [heatmap (:heatmap analysis)]
    (-> map-ctx
        editing/clear-edits!
        map-utils/clear-population!
        map-utils/unselect-features!
        map-utils/clear-interactions!
        map-utils/clear-markers!
        map-utils/enable-heatmap-hover!
        (heatmap/update-heatmap-data! heatmap)
        (heatmap/update-heatmap-visuals! heatmap))))

(defn set-analysis-mode!
  [map-ctx {:keys [sub-mode] :as mode}]
  (condp = sub-mode
    :reachability (set-reachability-mode! map-ctx mode)
    :diversity (set-diversity-mode! map-ctx mode)
    :heatmap (set-heatmap-mode! map-ctx mode)))

(defn update-reachability-mode!
  [{:keys [layers] :as map-ctx}
   {:keys [lipas-id fit-nonce analysis]}]
  (let [reachability (:reachability analysis)
        fit? (and fit-nonce (not= fit-nonce (-> map-ctx :mode :fit-nonce)))
        ^js vectors-layer (-> layers :overlays :vectors)]
    (-> map-ctx
        (map-utils/clear-markers!)
        (map-utils/unselect-features!)
        (map-utils/clear-population!)
        (cond->
          lipas-id (map-utils/select-sports-site! lipas-id)
          fit? (map-utils/fit-to-extent! (-> vectors-layer .getSource .getExtent)))
        (map-utils/enable-population-hover!)
        (show-population! reachability)
        (show-schools! reachability)
        (map-utils/enable-schools-hover!)
        (map-utils/draw-analytics-buffer! reachability))))

(defn update-diversity-mode!
  [{:keys [layers] :as map-ctx}
   {:keys [lipas-id fit-nonce sub-mode analysis]}]
  (let [diversity (:diversity analysis)
        ^js layer (-> layers :overlays :diversity-area)]
    (-> map-ctx
        (map-utils/clear-markers!)
        (map-utils/unselect-features!)
        (map-utils/clear-population!)
        (show-diversity-grid! diversity)
        (map-utils/enable-diversity-grid-hover!)
        (map-utils/enable-diversity-area-hover!)
        (map-utils/draw-diversity-areas! diversity)
        (map-utils/fit-to-extent! (-> layer .getSource .getExtent)))))

(defn update-analysis-mode!
  [map-ctx
   {:keys [sub-mode] :as mode}]
  (let [old-sub-mode (-> map-ctx :mode :sub-mode)]
    (condp = sub-mode
      :reachability (if (#{:reachability} old-sub-mode)
                      (update-reachability-mode! map-ctx mode)
                      (set-reachability-mode! map-ctx mode))
      :diversity (if (#{:diversity} old-sub-mode)
                   (update-diversity-mode! map-ctx mode)
                   (set-diversity-mode! map-ctx mode))
      :heatmap (if (#{:heatmap} old-sub-mode)
                 (set-heatmap-mode! map-ctx mode)
                 (set-heatmap-mode! map-ctx mode)))))

(defn set-mode! [map-ctx mode]
  (let [map-ctx (case (:name mode)
                  :default (set-default-mode! map-ctx mode)
                  :editing (editing/set-editing-mode! map-ctx mode)
                  :adding (editing/set-adding-mode! map-ctx mode)
                  :analysis (set-analysis-mode! map-ctx mode))]
    (assoc map-ctx :mode mode)))

(defn update-mode! [map-ctx mode]
  (let [update? (= (-> map-ctx :mode :name) (:name mode))
        map-ctx (case (:name mode)
                  :default (if update?
                             (update-default-mode! map-ctx mode)
                             (set-default-mode! map-ctx mode))
                  :editing (if update?
                             (editing/update-editing-mode! map-ctx mode)
                             (editing/set-editing-mode! map-ctx mode))
                  :adding (if update?
                            (editing/update-adding-mode! map-ctx mode)
                            (editing/set-adding-mode! map-ctx mode))
                  :analysis (if update?
                              (update-analysis-mode! map-ctx mode)
                              (set-analysis-mode! map-ctx mode)))]
    (assoc map-ctx :mode mode)))

(defn map-inner [opts]

  ;; Internal state
  (let [map-ctx* (atom nil)]

    (r/create-class

      {:reagent-render
       (fn [] [mui/grid {:id "map"
                        ;; Keyboard navigation requires that this element has a tabIndex
                        ;; see https://openlayers.org/en/latest/apidoc/module-ol_Map-Map.html
                         :tabIndex -1
                         :item true
                         :style {:height "100%" :width "100%"}
                         :xs 12}])

       :component-did-mount
       (fn [comp]
         (let [opts (r/props comp)
               basemap (:basemap opts)
               overlays (:overlays opts)
               geoms (:geoms opts)
               lois (:lois opts)
               mode (-> opts :mode)

               map-ctx (-> (init-map! opts)
                           (map-utils/update-geoms! geoms)
                           (map-utils/update-lois! lois)
                           (map-utils/set-basemap! basemap)
                           (set-mode! mode))]

           (reset! map-ctx* map-ctx)))

       :component-did-update
       (fn [comp]
         (let [opts (r/props comp)
               geoms (-> opts :geoms)
               lois (-> opts :lois)
               basemap (-> opts :basemap)
               overlays (-> opts :overlays)
               center (-> opts :center)
               zoom (-> opts :zoom)
               mode (-> opts :mode)
               lipas-id (:lipas-id mode)]

           (cond-> @map-ctx*
             (not= (:geoms @map-ctx*) geoms) (map-utils/update-geoms! geoms)
             (not= (:lois @map-ctx*) lois) (map-utils/update-lois! lois)
             (not= (:basemap @map-ctx*) basemap) (map-utils/set-basemap! basemap)
             (not= (:overlays @map-ctx*) overlays) (map-utils/set-overlays! overlays)
             (not= (:center @map-ctx*) center) (map-utils/update-center! center)
             (not= (:zoom @map-ctx*) zoom) (map-utils/update-zoom! zoom)
             (not= (:mode @map-ctx*) mode) (update-mode! mode)
             (and (= :default (:name mode))
                  lipas-id) (map-utils/refresh-select! lipas-id)
             true (as-> $ (reset! map-ctx* $)))))

       :component-will-unmount
       (fn [_comp]
         (when-let [map-ctx @map-ctx*]
          ;; Dispose of the OpenLayers map instance
           (when-let [ol-map (:lmap map-ctx)]
             (.dispose ol-map)

             #_(when ^boolean goog.DEBUG
                 (js/setTimeout #(js/console.log "After disposal, WebGL contexts:"
                                                 (.-length (.querySelectorAll js/document "canvas"))) 50)))

           (reset! map-ctx* nil)))

       :display-name "map-inner"})))

(defn map-outer [{:keys [popup-ref]}]
  (let [geoms-fast (rf/subscribe [::subs/geometries-fast])
        lois (rf/subscribe [::subs/loi-geoms])
        basemap (rf/subscribe [::subs/basemap])
        overlays (rf/subscribe [::subs/selected-overlays])
        center (rf/subscribe [::subs/center])
        zoom (rf/subscribe [::subs/zoom])
        mode (rf/subscribe [::subs/mode])]
    (fn []
      [map-inner
       {:geoms @geoms-fast
        :lois @lois
        :basemap @basemap
        :overlays @overlays
        :center @center
        :zoom @zoom
        :mode @mode
        :popup-ref popup-ref}])))
