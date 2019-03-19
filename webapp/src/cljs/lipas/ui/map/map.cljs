(ns lipas.ui.map.map
  (:require
   ["ol"]
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
  {:taustakartta (->wmts-url "mml_taustakartta")
   :maastokartta (->wmts-url "mml_maastokartta")
   :ortokuva     (->wmts-url "mml_ortokuva")})

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
                              #js{:origin      proj/epsg3067-top-left
                                  :extent      proj/epsg3067-extent
                                  :resolutions mml-resolutions
                                  :matrixIds   mml-matrix-ids})
            :format          "png"
            :requestEncoding "REST"
            :isBaseLayer     true})}))

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
    (ol.layer.Vector.
     #js{:source     (ol.source.Vector.)
         :style      styles/feature-style
         :renderMode "image"})
    :edits
    (ol.layer.Vector.
     #js{:source     (ol.source.Vector.)
         :style      #js[styles/edit-style styles/vertices-style]
         :renderMode "vector"})
    :markers
    (ol.layer.Vector.
     #js{:source     (ol.source.Vector.)
         :style      styles/red-marker-style
         :renderMode "image"})}})

(defn init-view [center zoom]
  (ol.View. #js{:center         #js[(:lon center) (:lat center)]
                :extent         proj/epsg3067-extent
                :zoom           zoom
                :projection     "EPSG:3067"
                :resolutions    mml-resolutions
                :units          "m"
                :enableRotation false}))

(defn init-overlay []
  (ol.Overlay. #js{:offset #js[-15 0]
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
                                (-> layers :overlays :vectors)
                                (-> layers :overlays :edits)
                                (-> layers :overlays :markers)]
                  :overlays #js[popup-overlay]
                  :view     view}

        hover (ol.interaction.Select.
               #js{:layers    #js[(-> layers :overlays :vectors)
                                  (-> layers :overlays :markers)]
                   :style     styles/feature-style-hover
                   :condition ol.events.condition.pointerMove})

        select (ol.interaction.Select.
                #js{:layers #js[(-> layers :overlays :vectors)]
                    :style  styles/feature-style-selected})

        lmap (ol.Map. opts)]

    (.on hover "select"
         (fn [e]
           (let [coords   (gobj/getValueByKeys e "mapBrowserEvent" "coordinate")
                 selected (gobj/get e "selected")
                 f1       (aget selected 0)
                 lipas-id (when f1 (.get f1 "lipas-id"))
                 fs       (map-utils/find-features-by-lipas-id
                           {:layers layers} lipas-id)]

             (doto (.getFeatures hover)
               (.clear)
               (.extend fs))

             (.setPosition popup-overlay coords)
             (==> [::events/show-popup
                   (when (not-empty selected)
                     {:anchor-el (.getElement popup-overlay)
                      :data      (-> selected map-utils/->geoJSON-clj)})]))))

    (.on select "select"
         (fn [e]
           (let [coords   (gobj/getValueByKeys e "mapBrowserEvent" "coordinate")
                 selected (aget (gobj/get e "selected") 0)]
             (.setPosition popup-overlay coords)
             (==> [::events/show-sports-site (when selected
                                               (.get selected "lipas-id"))]))))

    (.on lmap "click"
         (fn [e]
           (==> [::events/hide-address])))

    (.on lmap "moveend"
         (fn [e]
           (let [center (.getCenter view)
                 lonlat (ol.proj.toLonLat center proj/epsg3067)
                 zoom   (.getZoom view)
                 extent (.calculateExtent view)
                 width  (.getWidth ol.extent extent)
                 height (.getHeight ol.extent extent)]

             (when (and (> width 0) (> height 0))
               (==> [::events/set-view center lonlat zoom extent width height])))))

    {:lmap          lmap
     :view          view
     :center        center
     :zoom          zoom
     :layers        layers
     ;; We don't re-create :hover and :select each time when we toggle
     ;; them because it causes buggy behavior. We keep refs to
     ;; singleton instances under special :interactions* key in
     ;; map-ctx where we can find them when they need to be enabled.
     :interactions* {:select select :hover hover}
     :overlays      {:popup popup-overlay}}))

;; Browsing and selecting features
(defn set-default-mode! [map-ctx mode]
  (let [map-ctx (-> map-ctx
                    editing/clear-edits!
                    map-utils/clear-interactions!
                    map-utils/clear-markers!
                    map-utils/enable-hover!
                    map-utils/enable-select!)]
    (let [lipas-id (:lipas-id mode)
          address  (:address mode)]
      (cond-> map-ctx
        lipas-id (map-utils/select-sports-site! lipas-id)
        address (map-utils/show-address-marker! address)))))

(defn update-default-mode!
  [{:keys [layers] :as map-ctx} {:keys [lipas-id fit-nonce address]}]
  (let [fit? (not= fit-nonce (-> map-ctx :mode :fit-nonce))]
    (cond-> map-ctx
      true     (map-utils/clear-markers!)
      lipas-id (map-utils/select-sports-site! lipas-id)
      fit?     (map-utils/fit-to-extent!
                (-> layers :overlays :vectors .getSource .getExtent))
      address  (map-utils/show-address-marker! address))))

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
        (let [opts    (r/props comp)
              basemap (:basemap opts)
              geoms   (:geoms opts)
              mode    (-> opts :mode)

              map-ctx (-> (init-map! opts)
                          (map-utils/update-geoms! geoms)
                          (map-utils/set-basemap! basemap)
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
            (not= (:geoms @map-ctx*) geoms)     (map-utils/update-geoms! geoms)
            (not= (:basemap @map-ctx*) basemap) (map-utils/set-basemap! basemap)
            (not= (:center @map-ctx*) center)   (map-utils/update-center! center)
            (not= (:zoom @map-ctx*) zoom)       (map-utils/update-zoom! zoom)
            (not= (:mode @map-ctx*) mode)       (update-mode! mode)
            true                                (as-> $ (reset! map-ctx* $)))))

      :display-name "map-inner"})))

(defn map-outer []
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
