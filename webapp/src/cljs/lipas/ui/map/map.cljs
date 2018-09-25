(ns lipas.ui.map.map
  (:require proj4
            [goog.object :as gobj]
            [lipas.ui.map.events :as events]
            [lipas.ui.map.subs :as subs]
            [lipas.ui.mui :as mui]
            [lipas.ui.utils :refer [<== ==>] :as utils]
            [reagent.core :as r]
            [re-frame.core :as re-frame]))

;; Kudos https://github.com/jleh/Leaflet.MML-layers

;; (set! *warn-on-infer* true)

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

(defn ->wmts [{:keys [url layer-name]}]
  (js/ol.layer.Tile.
   #js{:source
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

(defn init-base-layers []
  {:taustakartta (->wmts {:url        (:taustakartta urls)
                          :layer-name "MML-Taustakartta"})
   :maastokartta (->wmts {:url        (:maastokartta urls)
                          :layer-name "MML-Maastokartta"})
   :ortokuva     (->wmts {:url        (:ortokuva urls)
                          :layer-name "MML-Ortokuva"})
   :osm          (js/ol.layer.Tile. #js{:source (js/ol.source.OSM.)})})

(defn init-map [opts]
  (let [layers (init-base-layers)
        view   (js/ol.View. #js{:center      jyvaskyla
                                :zoom        1
                                :projection  "EPSG:3067"
                                :resolutions mml-resolutions
                                :units       "m"})



        opts #js {:target "map"
                  :layers #js[(:taustakartta layers)]
                  :view   view}]
    (js/ol.Map. opts)))

(defn map-inner []
  (let [layers-state (atom nil)
        map-state    (atom nil)
        geoms-state  (atom nil)]
    (r/create-class
     {:reagent-render       (fn [] [mui/grid {:id    "map"
                                              :item  true
                                              :style {:flex "1 0 0"}
                                              :xs    12}])
      :component-did-mount  (fn [comp]
                              (let [{:keys [geoms] :as opts} (r/props comp)]
                                (init-map opts)))
      :component-did-update (fn [comp]
                              (let [opts  (r/props comp)
                                    geoms (:geoms opts)]
                                (when (not= @geoms-state geoms)
                                  (prn "Maybe should update something?"))))
      :display-name         "leaflet-inner"})))

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
