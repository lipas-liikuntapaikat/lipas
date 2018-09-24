(ns lipas.ui.map.map
  (:require [goog.object :as gobj]
            [lipas.ui.map.events :as events]
            [lipas.ui.map.subs :as subs]
            [lipas.ui.mui :as mui]
            [lipas.ui.utils :refer [<== ==>] :as utils]
            [reagent.core :as r]
            [re-frame.core :as re-frame]))

;; Kudos https://github.com/jleh/Leaflet.MML-layers

;; (set! *warn-on-infer* true)

(def base-url "/mapproxy/wmts")

(def urls
  {;:osm          "http://{s}.tile.osm.org/{z}/{x}/{y}.png"
   :taustakartta (str base-url "/mml_taustakartta/mml_grid/{z}/{x}/{y}.png")
   :maastokartta (str base-url "/mml_maastokartta/mml_grid/{z}/{x}/{y}.png")
   :ortokuva     (str base-url "/mml_ortokuva/mml_grid/{z}/{x}/{y}.png")})

(def resolutions
  #js[8192, 4096, 2048, 1024, 512, 256, 128, 64, 32, 16, 8, 4, 2, 1, 0.5, 0.25])


(defn init-map [opts]
  (let [osm  (js/ol.layer.Tile. #js{:source (js/ol.source.OSM.)})
        view (js/ol.View. #js{:center (js/ol.proj.fromLonLat #js[37.41 8.82])
                              :zoom   4})
        opts #js {:target "map"
                  :layers #js[osm]
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
