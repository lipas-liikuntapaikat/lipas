(ns lipas.ui.map.map
  (:require proj4
            [goog.object :as gobj]
            [lipas.ui.map.events :as events]
            [lipas.ui.map.subs :as subs]
            [lipas.ui.mui :as mui]
            [lipas.ui.utils :refer [<== ==>] :as utils]
            [reagent.core :as r]
            [re-frame.core :as re-frame]))

;;(set! *warn-on-infer* true)

(defn ->wmts-url [layer-name]
  (str "/mapproxy/wmts/"
       layer-name
       "/{TileMatrixSet}/{TileMatrix}/{TileCol}/{TileRow}.png"))

(def urls
  {:taustakartta (->wmts-url "mml_taustakartta")
   :maastokartta (->wmts-url "mml_maastokartta")
   :ortokuva     (->wmts-url "mml_ortokuva")})

(def mml-resolutions
  #js[8192, 4096, 2048, 1024, 512, 256, 128, 64, 32, 16, 8, 4, 2, 1, 0.5, 0.25])

(js/proj4.defs "EPSG:3067" (str "+proj=utm"
                                "+zone=35"
                                "+ellps=GRS80"
                                "+towgs84=0,0,0,0,0,0,0"
                                "+units=m"
                                "+no_defs"))

(def mml-matrix-ids (clj->js (range (count mml-resolutions))))

(js/ol.proj.proj4.register proj4)

(def epsg3067 (js/ol.proj.get "EPSG:3067"))
(def epsg3067-extent #js[-548576.0 6291456.0 1548576.0 8388608.0])

(.setExtent epsg3067 epsg3067-extent)

(def epsg3067-topLeft (js/ol.extent.getTopLeft (.getExtent epsg3067)))

(def jyvaskyla #js[435047 6901408])
(def center-wgs84 (js/ol.proj.fromLonLat #js[24 65]))

(def geoJSON (js/ol.format.GeoJSON. #js{:dataProjection    "EPSG:4326"
                                        :featureProjection "EPSG:3067"}))

(defn ->ol-features [geoJSON-features]
  (.readFeatures geoJSON geoJSON-features))

(defn ->geoJSON [ol-feature]
  (.writeFeaturesObject geoJSON #js[ol-feature]))

(defn ->wmts [{:keys [url layer-name visible?]
               :or   [visible? false]}]
  (js/ol.layer.Tile.
   #js{:visible visible?
       :source
       (js/ol.source.WMTS.
        #js{:url             url
            :layer           layer-name
            :projection      "EPSG:3067"
            :matrixSet       "mml_grid"
            :tileGrid        (js/ol.tilegrid.WMTS.
                              #js{:origin      epsg3067-topLeft
                                  :extent      epsg3067-extent
                                  :resolutions mml-resolutions
                                  :matrixIds   mml-matrix-ids})
            :format          "png"
            :requestEncoding "REST"
            :isBaseLayer     true})}))

(defn init-layers []
  {:basemaps
   {:taustakartta (->wmts {:url        (:taustakartta urls)
                           :layer-name "MML-Taustakartta"
                           :visible?   true})
    :maastokartta (->wmts {:url        (:maastokartta urls)
                           :layer-name "MML-Maastokartta"})
    :ortokuva     (->wmts {:url        (:ortokuva urls)
                           :layer-name "MML-Ortokuva"})
    :osm          (js/ol.layer.Tile. #js{:source (js/ol.source.OSM.)})}
   :overlays
   {:vectors (js/ol.layer.Vector.
              #js{:source (js/ol.source.Vector.)})
    :draw    (js/ol.layer.Vector.
              #js{:source (js/ol.source.Vector.)})}})

(defn init-map [{:keys [center zoom]}]
  (let [layers (init-layers)
        view   (js/ol.View. #js{:center      #js[(:lon center) (:lat center)]
                                :zoom        zoom
                                :projection  "EPSG:3067"
                                :resolutions mml-resolutions
                                :units       "m"})

        overlay (js/ol.Overlay. #js{:element
                                    (js/document.getElementById "popup-anchor")})

        opts #js {:target   "map"
                  :layers   #js[(-> layers :basemaps :taustakartta)
                                (-> layers :basemaps :maastokartta)
                                (-> layers :basemaps :ortokuva)
                                (-> layers :overlays :vectors)]
                  :overlays #js[overlay]
                  :view     view}

        lmap (js/ol.Map. opts)

        hover (js/ol.interaction.Select.
               #js{:layers    [(-> layers :overlays :vectors)]
                   :condition js/ol.events.condition.pointerMove})

        select (js/ol.interaction.Select.
                #js{:layers #js[(-> layers :overlays :vectors)]})]

    (.on hover "select"
         (fn [^js e]
           (let [coords   (gobj/getValueByKeys e "mapBrowserEvent" "coordinate")
                 selected (aget (gobj/get e "selected") 0)]
             (.setPosition overlay coords)
             (==> [::events/show-popup
                   (when selected
                     {:anchor-el (.getElement overlay)
                      :data      (-> selected
                                     ->geoJSON
                                     (js->clj :keywordize-keys true))})]))))

    (.addInteraction lmap hover)

    (.on select "select"
         (fn [^js e]
           (let [coords   (gobj/getValueByKeys e "mapBrowserEvent" "coordinate")
                 selected (aget (gobj/get e "selected") 0)]
             (.setPosition overlay coords)
             (==> [::events/show-sports-site
                   (when selected (.get selected "lipas-id"))]))))

    (.addInteraction lmap select)

    (.on lmap "moveend"
         (fn [e]
           (let [lon  (aget (.getCenter view) 0)
                 lat  (aget (.getCenter view) 1)
                 zoom (.getZoom view)]
             (==> [::events/set-view lat lon zoom]))))

    [lmap layers]))

(defn update-geoms [layers geoms]
  (let [^js vectors (-> layers :overlays :vectors)
        ^js source  (.getSource vectors)]
    (.clear source)
    (doseq [g    geoms
            :let [f (-> g
                        clj->js
                        ->ol-features)]]
      (.addFeatures source f))))

(defn set-basemap [layers basemap]
  (doseq [[k ^js v] (:basemaps layers)
          :let [visible? (= k basemap)]]
    (.setVisible v visible?)))

(defn map-inner []
  (let [layers*  (atom nil)
        lmap*    (atom nil)
        geoms*   (atom nil)
        basemap* (atom nil)]
    (r/create-class

     {:reagent-render
      (fn [] [mui/grid {:id    "map"
                        :item  true
                        :style {:flex "1 0 0"}
                        :xs    12}])

      :component-did-mount
      (fn [comp]
        (let [opts          (r/props comp)
              basemap       (:basemap opts)
              [lmap layers] (init-map opts)]

          (when-let [geoms (not-empty (:geoms opts))]
            (update-geoms layers geoms))

          (set-basemap layers basemap)
          (reset! basemap* basemap)
          (reset! lmap* lmap)
          (reset! layers* layers)))

      :component-did-update
      (fn [comp]
        (let [opts    (r/props comp)
              geoms   (:geoms opts)
              basemap (:basemap opts)]

          (when (not= @geoms* geoms)
            (update-geoms @layers* geoms)
            (reset! geoms* geoms))

          (when (not= @basemap* basemap)
            (set-basemap @layers* basemap)
            (reset! basemap* basemap))))

      :display-name "map-inner"})))

(defn map-outer []
  (==> [:lipas.ui.sports-sites.events/get-by-type-code 3110])
  (==> [:lipas.ui.sports-sites.events/get-by-type-code 3130])
  (==> [:lipas.ui.sports-sites.events/get-by-type-code 2510])
  (==> [:lipas.ui.sports-sites.events/get-by-type-code 2520])
  (let [geoms   (re-frame/subscribe [::subs/geometries])
        basemap (re-frame/subscribe [::subs/basemap])
        center  (re-frame/subscribe [::subs/center])
        zoom    (re-frame/subscribe [::subs/zoom])]
    (fn []
      [map-inner {:geoms   @geoms
                  :basemap @basemap
                  :center  @center
                  :zoom    @zoom}])))
