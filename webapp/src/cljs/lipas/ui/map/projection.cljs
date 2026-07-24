(ns lipas.ui.map.projection
  "Loading this namespace causes side-effects to global OpenLayers
  object (js/ol)."
  (:require ["ol/extent" :as extent]
            ["ol/proj" :as proj]
            ["ol/proj/proj4" :refer [register]]
            ["proj4" :as proj4]))

(def epsg3067-extent #js [-548576.0 6291456.0 1548576.0 8388608.0])

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

(defn wgs84->epsg3067
  "WGS84 [lon lat] (js array) → EPSG:3067 coords. Defined here (the :geo
   module) so the lazy :ptv module can transform coordinates without
   depending on the full :map module."
  [wgs84-coords]
  (proj/fromLonLat wgs84-coords epsg3067))
