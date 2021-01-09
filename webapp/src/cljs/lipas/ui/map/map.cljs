(ns lipas.ui.map.map
  (:require
   ["ol" :as ol]
   [goog.object :as gobj]
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
    :population
    (ol/layer.Vector.
     #js{:source     (ol/source.Vector.)
         :style      styles/population-style
         :name       "population"
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
     {:url         (:kuntarajat urls)
      :layer-name  "MML-Kuntarajat"})}})

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
                                (-> layers :overlays :population)
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
                              :style     styles/population-hover-style
                              :multi     false
                              :condition ol/events.condition.pointerMove})

        select (ol/interaction.Select.
                #js{:layers #js[(-> layers :overlays :vectors)]
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
     {:select           select
      :vector-hover     vector-hover
      :marker-hover     marker-hover
      :population-hover population-hover}
     :overlays {:popup popup-overlay}}))

(defn any-intersects? [fs f buffer]
  (when-not (empty? fs)
    (let [extent (-> fs (aget 0) .getGeometry .getExtent)]
      (.forEach fs (fn [f']
                     (ol/extent.extend extent (-> f' .getGeometry .getExtent))))
      (let [buffered (ol/extent.buffer extent buffer)]
        (ol/extent.intersects buffered (-> f .getGeometry .getExtent))))))

;; Zones idea (within 2km,5km,10km) roughly from
;; https://www.oulu.fi/paikkatieto/Liikuntapaikkojen%20saavutettavuus.pdf
;; (pages 27-30)
(defn resolve-zone
  [geom-fs population-f]
  (cond
    (any-intersects? geom-fs population-f 2000)  [1 styles/population-zone1-fn]
    (any-intersects? geom-fs population-f 5000)  [2 styles/population-zone2-fn]
    (any-intersects? geom-fs population-f 10000) [3 styles/population-zone3-fn]))

(defn show-population!
  [{:keys [layers] :as map-ctx}
   {:keys [data geoms lipas-id]}]
  (-> layers :overlays :population .getSource .clear)
  (let [geom-fs (when geoms (map-utils/->ol-features (clj->js geoms)))]

    ;; Add selected style to sports-site feature
    ;;(when lipas-id (display-as-selected map-ctx lipas-id))
    (when lipas-id
      (map-utils/select-sports-site! map-ctx lipas-id {:maxZoom 7}))

    (if data
      (let [fs  (map-utils/->ol-features data)
            res #js[]]

        (doseq [f    fs
                :let [[zone style] (resolve-zone geom-fs f)]]

          (when zone
            (.setStyle f style)
            (.set f "selected" true)
            (.set f "zone" zone)
            (.push res f)))

        (-> layers :overlays :population .getSource (.addFeatures fs))
        (==> [::events/set-selected-population-grid (-> res map-utils/->geoJSON-clj)])

        map-ctx)
      map-ctx)))

;; Browsing and selecting features
(defn set-default-mode!
  [map-ctx {:keys [lipas-id address sub-mode population]}]
  (let [population? (= sub-mode :population)
        map-ctx     (-> map-ctx
                        editing/clear-edits!
                        map-utils/clear-population!
                        map-utils/unselect-features!
                        map-utils/clear-interactions!
                        map-utils/clear-markers!
                        map-utils/enable-vector-hover!
                        map-utils/enable-marker-hover!
                        map-utils/enable-select!)]
    (cond-> map-ctx
      lipas-id    (map-utils/select-sports-site! lipas-id)
      address     (map-utils/show-address-marker! address)
      population? (->
                   ;; map-utils/enable-population-hover!
                   (show-population! population)))))

(defn update-default-mode!
  [{:keys [layers] :as map-ctx}
   {:keys [lipas-id fit-nonce address sub-mode population]}]
  (let [fit? (and fit-nonce (not= fit-nonce (-> map-ctx :mode :fit-nonce)))
        pop? (= sub-mode :population)]
    (cond-> map-ctx
      true     (map-utils/clear-markers!)
      true     (map-utils/unselect-features!)
      true     (map-utils/clear-population!)
      lipas-id (map-utils/select-sports-site! lipas-id)
      fit?     (map-utils/fit-to-extent!
                (-> layers :overlays :vectors .getSource .getExtent))
      address  (map-utils/show-address-marker! address)
      pop?     (show-population! population))))

(defn set-mode! [map-ctx mode]
  (let [map-ctx (case (:name mode)
                  :default   (set-default-mode! map-ctx mode)
                  :editing   (editing/set-editing-mode! map-ctx mode)
                  :adding    (editing/set-adding-mode! map-ctx mode))]
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
                               (editing/set-adding-mode! map-ctx mode)))]
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
