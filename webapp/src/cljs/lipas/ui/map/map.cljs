(ns lipas.ui.map.map
  (:require
   ["ol" :as ol]
   [goog.object :as gobj]
   [lipas.ui.analysis.reachability.events :as analysis-events]
   [lipas.ui.map.editing :as editing]
   [lipas.ui.map.events :as events]
   [lipas.ui.map.projection :as proj]
   [lipas.ui.map.styles :as styles]
   [lipas.ui.map.subs :as subs]
   [lipas.ui.map.utils :as map-utils]
   [lipas.ui.mui :as mui]
   [lipas.ui.utils :refer [<== ==>] :as utils]
   [re-frame.core :as re-frame]
   [reagent.core :as r]))

;; (set! *warn-on-infer* true)

(def mml-resolutions
  #js[8192, 4096, 2048, 1024, 512, 256, 128, 64, 32, 16, 8, 4, 2, 1, 0.5, 0.25])

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

(defn ->wmts
  [{:keys [url layer-name visible? base-layer? min-res max-res
           resolutions matrix-ids]
    :or   {visible?    false
           base-layer? true
           max-res     8192
           min-res     0.25
           resolutions mml-resolutions
           matrix-ids  mml-matrix-ids}}]
  (ol/layer.Tile.
   #js{:visible       visible?
       :minResolution min-res
       :maxResolution max-res
       :source
       (ol/source.WMTS.
        #js{:url             url
            :layer           layer-name
            :projection      "EPSG:3067"
            :matrixSet       "mml_grid"
            :tileGrid        (ol/tilegrid.WMTS.
                              #js{:origin      proj/epsg3067-top-left
                                  :extent      proj/epsg3067-extent
                                  :resolutions resolutions
                                  :matrixIds   matrix-ids})
            :format          "png"
            :requestEncoding "REST"
            :isBaseLayer     base-layer?})}))

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
    (ol/layer.Vector.
     #js{:source     (ol/source.Vector.)
         :name       "vectors"
         :style      styles/feature-style
         :renderMode "image"})
    :edits
    (ol/layer.Vector.
     #js{:source     (ol/source.Vector.)
         :style      #js[styles/edit-style styles/vertices-style]
         :renderMode "vector"})
    :markers
    (ol/layer.Vector.
     #js{:source     (ol/source.Vector.)
         :style      styles/blue-marker-style
         :renderMode "image"})
    :analysis
    (ol/layer.Vector.
     #js{:source     (ol/source.Vector.)
         :style      styles/population-style
         :name       "analysis"
         :renderMode "image"})
    :population
    (ol/layer.Vector.
     #js{:source     (ol/source.Vector.)
         :style      styles/population-style2
         :name       "population"
         :renderMode "image"})
    :schools
    (ol/layer.Vector.
     #js{:source     (ol/source.Vector.)
         :style      styles/school-style
         :name       "schools"
         :renderMode "image"})
    :diversity-grid
    (ol/layer.Vector.
     #js{:source     (ol/source.Vector.)
         :style      styles/diversity-grid-style
         :name       "diversity-grid"
         :renderMode "image"})
    :diversity-area
    (ol/layer.Vector.
     #js{:source     (ol/source.Vector.)
         :style      styles/diversity-area-style
         :name       "diversity-area"
         :renderMode "image"})
    :light-traffic
    (ol/layer.Image.
     #js{:visible false
         :source
         (ol/source.ImageWMS.
          #js{:url         "https://julkinen.vayla.fi/inspirepalvelu/avoin/wms"
              :params      #js{:LAYERS "TL166"}
              :serverType  "geoserver"
              :crossOrigin "anonymous"})})
    :retkikartta-snowmobile-tracks
    (ol/layer.Image.
     #js{:visible false
         :source
         (ol/source.ImageWMS.
          #js{:url         "/geoserver/lipas/wms?"
              :params      #js{:LAYERS "lipas:metsahallitus_urat2019"}
              :serverType  "geoserver"
              :crossOrigin "anonymous"})})
    :mml-kiinteisto
    (->wmts
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
      :layer-name  "MML-Kiinteistö"})
    :mml-kiinteistotunnukset
    (->wmts
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
      :layer-name  "MML-Kiinteistötunnukset"})
    :mml-kuntarajat
    (->wmts
     {:url        (:kuntarajat urls)
      :layer-name "MML-Kuntarajat"})}})

(defn init-view [center zoom]
  (ol/View. #js{:center         #js[(:lon center) (:lat center)]
                :extent         proj/epsg3067-extent
                :zoom           zoom
                :projection     "EPSG:3067"
                :resolutions    mml-resolutions
                :units          "m"
                :enableRotation false}))

(defn init-overlay []
  (ol/Overlay. #js{:offset #js[-15 0]
                   :element
                   (js/document.getElementById "popup-anchor")}))

(defn init-map! [{:keys [center zoom]}]
  (let [layers        (init-layers)
        view          (init-view center zoom)
        popup-overlay (init-overlay)

        opts #js {:target   "map"
                  :layers   #js[(-> layers :basemaps :taustakartta)
                                (-> layers :basemaps :maastokartta)
                                (-> layers :basemaps :ortokuva)
                                (-> layers :overlays :analysis)
                                (-> layers :overlays :population)
                                (-> layers :overlays :schools)
                                (-> layers :overlays :diversity-area)
                                (-> layers :overlays :diversity-grid)
                                (-> layers :overlays :vectors)
                                (-> layers :overlays :edits)
                                (-> layers :overlays :markers)
                                (-> layers :overlays :light-traffic)
                                (-> layers :overlays :retkikartta-snowmobile-tracks)
                                (-> layers :overlays :mml-kiinteisto)
                                (-> layers :overlays :mml-kiinteistotunnukset)
                                (-> layers :overlays :mml-kuntarajat)]
                  :overlays #js[popup-overlay]
                  :view     view}

        vector-hover (ol/interaction.Select.
                      #js{:layers    #js[(-> layers :overlays :vectors)]
                          :style     styles/feature-style-hover
                          :multi     true
                          :condition ol/events.condition.pointerMove})

        marker-hover (ol/interaction.Select.
                      #js{:layers    #js[(-> layers :overlays :markers)]
                          :style     styles/feature-style-hover
                          :multi     true
                          :condition ol/events.condition.pointerMove})

        population-hover (ol/interaction.Select.
                          #js{:layers    #js[(-> layers :overlays :population)]
                              :style     styles/population-hover-style2
                              :multi     false
                              :condition ol/events.condition.pointerMove})

        schools-hover (ol/interaction.Select.
                       #js{:layers    #js[(-> layers :overlays :schools)]
                           :style     styles/school-hover-style
                           :multi     false
                           :condition ol/events.condition.pointerMove})

        diversity-grid-hover (ol/interaction.Select.
                              #js{:layers    #js[(-> layers :overlays :diversity-grid)]
                                  :style     styles/diversity-grid-hover-style
                                  :multi     false
                                  :condition ol/events.condition.pointerMove})

        diversity-area-hover (ol/interaction.Select.
                              #js{:layers    #js[(-> layers :overlays :diversity-area)]
                                  :style     styles/diversity-area-hover-style
                                  :multi     false
                                  :condition ol/events.condition.pointerMove})

        diversity-area-select (ol/interaction.Select.
                               #js{:layers #js[(-> layers :overlays :diversity-area)]})

        select (ol/interaction.Select. #js{:layers #js[(-> layers :overlays :vectors)]
                                           :style  styles/feature-style-selected})

        lmap (ol/Map. opts)]

    (.on vector-hover "select"
         (fn [e]
           (let [coords   (gobj/getValueByKeys e "mapBrowserEvent" "coordinate")
                 selected (gobj/get e "selected")]

             ;; Uncommenting this would enable selecting all geoms
             ;; attached to Lipas-ID on hover. However this causes
             ;; terrible flickering and workaround hasn't been found
             ;; yet.
             ;;
             ;; (let [f1       (aget selected 0)
             ;;       lipas-id (when f1 (.get f1 "lipas-id"))
             ;;       fs       (map-utils/find-features-by-lipas-id
             ;;                 {:layers layers} lipas-id)]
             ;;   (doto (.getFeatures hover)
             ;;     (.clear)
             ;;     (.extend fs)))

             (.setPosition popup-overlay coords)
             (==> [::events/show-popup
                   (when (not-empty selected)
                     {:anchor-el (.getElement popup-overlay)
                      :data      (-> selected map-utils/->geoJSON-clj)})]))))

    (.on marker-hover "select"
         (fn [e]
           (let [coords   (gobj/getValueByKeys e "mapBrowserEvent" "coordinate")
                 selected (gobj/get e "selected")]

             (.setPosition popup-overlay coords)
             (==> [::events/show-popup
                   (when (not-empty selected)
                     {:anchor-el (.getElement popup-overlay)
                      :data      (-> selected map-utils/->geoJSON-clj)})]))))

    (.on population-hover "select"
         (fn [e]
           (let [coords   (gobj/getValueByKeys e "mapBrowserEvent" "coordinate")
                 selected (gobj/get e "selected")]

             (.setPosition popup-overlay coords)
             (==> [::events/show-popup
                   (when (not-empty selected)
                     {:anchor-el (.getElement popup-overlay)
                      :type      :population
                      :data      (-> selected map-utils/->geoJSON-clj)})]))))

    (.on schools-hover "select"
         (fn [e]
           (let [coords   (gobj/getValueByKeys e "mapBrowserEvent" "coordinate")
                 selected (gobj/get e "selected")]

             (.setPosition popup-overlay coords)
             (==> [::events/show-popup
                   (when (not-empty selected)
                     {:anchor-el (.getElement popup-overlay)
                      :type      :school
                      :data      (-> selected map-utils/->geoJSON-clj)})]))))

    (.on diversity-grid-hover "select"
         (fn [e]
           (let [coords   (gobj/getValueByKeys e "mapBrowserEvent" "coordinate")
                 selected (gobj/get e "selected")]

             (.setPosition popup-overlay coords)
             (==> [::events/show-popup
                   (when (not-empty selected)
                     {:anchor-el (.getElement popup-overlay)
                      :type      :diversity-grid
                      :data      (-> selected map-utils/->geoJSON-clj)})]))))

    (.on diversity-area-hover "select"
         (fn [e]
           (let [coords   (gobj/getValueByKeys e "mapBrowserEvent" "coordinate")
                 selected (gobj/get e "selected")]

             (.setPosition popup-overlay coords)
             (==> [::events/show-popup
                   (when (not-empty selected)
                     {:anchor-el (.getElement popup-overlay)
                      :type      :diversity-area
                      :data      (-> selected map-utils/->geoJSON-clj)})]))))

    (.on diversity-area-select "select"
         (fn [e]
           (let [selected (gobj/get e "selected")
                 f1       (when (seq selected) (aget selected 0))
                 fid      (when f1 (.get f1 "id"))]
             (when fid
               (==> [:lipas.ui.analysis.diversity.events/calc-diversity-indices {:id (symbol fid)}])))))

    ;; It's not possible to have multiple selects with
    ;; same "condition" (at least I couldn't get it
    ;; working). Therefore we have to detect which layer we're
    ;; selecting from and decide actions accordingly.
    (.on select "select"
         (fn [e]
           (let [coords   (gobj/getValueByKeys e "mapBrowserEvent" "coordinate")
                 selected (gobj/get e "selected")
                 f1       (aget selected 0)]
             (.setPosition popup-overlay coords)
             (==> [::events/sports-site-selected e (when f1 (.get f1 "lipas-id"))]))))

    (.on lmap "click"
         (fn [e]
           (==> [::events/map-clicked e])))

    (.on lmap "moveend"
         (fn [_]
           (let [center (.getCenter view)
                 lonlat (ol/proj.toLonLat center proj/epsg3067)
                 zoom   (.getZoom view)
                 extent (.calculateExtent view)
                 width  (.getWidth ol/extent extent)
                 height (.getHeight ol/extent extent)]

             (when (and (> width 0) (> height 0))
               (==> [::events/set-view center lonlat zoom extent width height])))))

    {:lmap     lmap
     :view     view
     :center   center
     :zoom     zoom
     :layers   layers
     ;; We don't re-create :hover and :select each time when we toggle
     ;; them because it causes buggy behavior. We keep refs to
     ;; singleton instances under special :interactions* key in
     ;; map-ctx where we can find them when they need to be enabled.
     :interactions*
     {:select                select
      :vector-hover          vector-hover
      :marker-hover          marker-hover
      :population-hover      population-hover
      :schools-hover         schools-hover
      :diversity-grid-hover  diversity-grid-hover
      :diversity-area-hover  diversity-area-hover
      :diversity-area-select diversity-area-select}
     :overlays {:popup popup-overlay}}))

(defn show-population!
  [{:keys [layers] :as map-ctx}
   {:keys [population geoms lipas-id zones] :as analysis}]

  (let [source  (-> layers :overlays :population .getSource)
        metric  (:selected-travel-metric analysis)
        profile (:selected-travel-profile analysis)]

    (.clear source)

    ;; Add selected style to sports-site feature
    ;;(when lipas-id (display-as-selected map-ctx lipas-id))
    (when lipas-id
      (map-utils/select-sports-site! map-ctx lipas-id {:maxZoom 7}))

    (when-let [data (:data population)]
      (doseq [m    data
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
   {:keys [schools geoms lipas-id]}]
  (-> layers :overlays :schools .getSource .clear)

  ;; Add selected style to sports-site feature
  ;;(when lipas-id (display-as-selected map-ctx lipas-id))
  (when lipas-id
    (map-utils/select-sports-site! map-ctx lipas-id {:maxZoom 7}))

  (when-let [data (:data schools)]
    (doseq [m data]
      (let [source (-> layers :overlays :schools .getSource)
            f      (map-utils/<-wkt (:coords m))]
        (.set f "name" (:name m))
        (.set f "type" (:type m))
        #_(.set f "selected" true)
        (.addFeature source f))))

  map-ctx)

(defn show-diversity-grid!
  [{:keys [layers] :as map-ctx}
   {:keys [data results]}]
  (-> layers :overlays :diversity-grid .getSource .clear)

  (when (seq results)
    (let [source (-> layers :overlays :diversity-grid .getSource)]

      (doseq [fcoll (map :grid (vals results))
              f     (:features fcoll)]
        (let [[nw se] (-> f :properties :envelope_wgs84)
              min-x   (first se)
              max-x   (first nw)
              min-y   (second nw)
              max-y   (second se)
              coords  #js[#js[#js[min-x max-y]
                              #js[max-x max-y]
                              #js[max-x min-y]
                              #js[min-x min-y]
                              #js[min-x max-y]]]
              geojson #js{:type       "Feature"
                          :geometry   #js{:type        "Polygon"
                                          :coordinates coords}
                          :properties (clj->js (:properties f))}
              ol-f    (map-utils/->ol-feature geojson)]
          (.addFeature source ol-f)))))


  map-ctx)

;; Browsing and selecting features
(defn set-default-mode!
  [map-ctx {:keys [lipas-id address]}]
  (-> map-ctx
      editing/clear-edits!
      map-utils/clear-population!
      map-utils/unselect-features!
      map-utils/clear-interactions!
      map-utils/clear-markers!
      map-utils/enable-vector-hover!
      map-utils/enable-marker-hover!
      map-utils/enable-select!
      (cond->
        lipas-id  (map-utils/select-sports-site! lipas-id)
        address   (map-utils/show-address-marker! address))))

(defn update-default-mode!
  [{:keys [layers] :as map-ctx}
   {:keys [lipas-id fit-nonce address]}]
  (let [fit?      (and fit-nonce (not= fit-nonce (-> map-ctx :mode :fit-nonce)))]
    (-> map-ctx
        (map-utils/clear-markers!)
        (map-utils/unselect-features!)
        (map-utils/clear-population!)
        (cond->
            lipas-id  (map-utils/select-sports-site! lipas-id)
            fit?      (map-utils/fit-to-extent!
                       (-> layers :overlays :vectors .getSource .getExtent))
            address   (map-utils/show-address-marker! address)))))

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
  (let [diversity (:diversity analysis)]
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
        (map-utils/fit-to-extent! (-> layers :overlays :diversity-area .getSource .getExtent)))))

(defn set-analysis-mode!
  [map-ctx {:keys [sub-mode] :as mode}]
  (condp = sub-mode
    :reachability (set-reachability-mode! map-ctx mode)
    :diversity    (set-diversity-mode! map-ctx mode)))

(defn update-reachability-mode!
  [{:keys [layers] :as map-ctx}
   {:keys [lipas-id fit-nonce analysis]}]
  (let [reachability (:reachability analysis)
        fit?         (and fit-nonce (not= fit-nonce (-> map-ctx :mode :fit-nonce)))]
    (-> map-ctx
        (map-utils/clear-markers!)
        (map-utils/unselect-features!)
        (map-utils/clear-population!)
        (cond->
            lipas-id (map-utils/select-sports-site! lipas-id)
            fit? (map-utils/fit-to-extent!
                      (-> layers :overlays :vectors .getSource .getExtent)))
        (map-utils/enable-population-hover!)
        (show-population! reachability)
        (show-schools! reachability)
        (map-utils/enable-schools-hover!)
        (map-utils/draw-analytics-buffer! reachability))))

(defn update-diversity-mode!
  [{:keys [layers] :as map-ctx}
   {:keys [lipas-id fit-nonce sub-mode analysis]}]
  (let [diversity (:diversity analysis)]
    (-> map-ctx
        (map-utils/clear-markers!)
        (map-utils/unselect-features!)
        (map-utils/clear-population!)
        (show-diversity-grid! diversity)
        (map-utils/enable-diversity-grid-hover!)
        (map-utils/enable-diversity-area-hover!)
        (map-utils/draw-diversity-areas! diversity)
        (map-utils/fit-to-extent! (-> layers :overlays :diversity-area .getSource .getExtent)))))

(defn update-analysis-mode!
  [map-ctx
   {:keys [sub-mode] :as mode}]
  (let [old-sub-mode (-> map-ctx :mode :sub-mode)]
    (condp = sub-mode
      :reachability (if (#{:reachability} old-sub-mode)
                      (update-reachability-mode! map-ctx mode)
                      (set-reachability-mode! map-ctx mode))
      :diversity    (if (#{:diversity} old-sub-mode)
                      (update-diversity-mode! map-ctx mode)
                      (set-diversity-mode! map-ctx mode)))))

(defn set-mode! [map-ctx mode]
  (let [map-ctx (case (:name mode)
                  :default  (set-default-mode! map-ctx mode)
                  :editing  (editing/set-editing-mode! map-ctx mode)
                  :adding   (editing/set-adding-mode! map-ctx mode)
                  :analysis (set-analysis-mode! map-ctx mode))]
    (assoc map-ctx :mode mode)))

(defn update-mode! [map-ctx mode]
  (let [update? (= (-> map-ctx :mode :name) (:name mode))
        map-ctx (case (:name mode)
                  :default   (if update?
                               (update-default-mode! map-ctx mode)
                               (set-default-mode! map-ctx mode))
                  :editing   (if update?
                               (editing/update-editing-mode! map-ctx mode)
                               (editing/set-editing-mode! map-ctx mode))
                  :adding    (if update?
                               (editing/update-adding-mode! map-ctx mode)
                               (editing/set-adding-mode! map-ctx mode))
                  :analysis (if update?
                              (update-analysis-mode! map-ctx mode)
                              (set-analysis-mode! map-ctx mode)))]
    (assoc map-ctx :mode mode)))

(defn map-inner []

  ;; Internal state
  (let [map-ctx* (atom nil)]

    (r/create-class

     {:reagent-render
      (fn [] [mui/grid {:id    "map"
                        :item  true
                        :style {:height "100%" :width "100%"}
                        :xs    12}])

      :component-did-mount
      (fn [comp]
        (let [opts     (r/props comp)
              basemap  (:basemap opts)
              overlays (:overlays opts)
              geoms    (:geoms opts)
              mode     (-> opts :mode)

              map-ctx (-> (init-map! opts)
                          (map-utils/update-geoms! geoms)
                          (map-utils/set-basemap! basemap)
                          (set-mode! mode))]

          (reset! map-ctx* map-ctx)))

      :component-did-update
      (fn [comp]
        (let [opts     (r/props comp)
              geoms    (-> opts :geoms)
              basemap  (-> opts :basemap)
              overlays (-> opts :overlays)
              center   (-> opts :center)
              zoom     (-> opts :zoom)
              mode     (-> opts :mode)
              lipas-id (:lipas-id mode)]

          (cond-> @map-ctx*
            (not= (:geoms @map-ctx*) geoms)       (map-utils/update-geoms! geoms)
            (not= (:basemap @map-ctx*) basemap)   (map-utils/set-basemap! basemap)
            (not= (:overlays @map-ctx*) overlays) (map-utils/set-overlays! overlays)
            (not= (:center @map-ctx*) center)     (map-utils/update-center! center)
            (not= (:zoom @map-ctx*) zoom)         (map-utils/update-zoom! zoom)
            (not= (:mode @map-ctx*) mode)         (update-mode! mode)
            (and (= :default (:name mode))
                 lipas-id)                        (map-utils/refresh-select! lipas-id)
            true                                  (as-> $ (reset! map-ctx* $)))))

      :display-name "map-inner"})))

(defn map-outer []
  (let [geoms-fast (re-frame/subscribe [::subs/geometries-fast])
        basemap    (re-frame/subscribe [::subs/basemap])
        overlays   (re-frame/subscribe [::subs/selected-overlays])
        center     (re-frame/subscribe [::subs/center])
        zoom       (re-frame/subscribe [::subs/zoom])
        mode       (re-frame/subscribe [::subs/mode])]
    (fn []
      [map-inner
       {:geoms    @geoms-fast
        :basemap  @basemap
        :overlays @overlays
        :center   @center
        :zoom     @zoom
        :mode     @mode}])))
