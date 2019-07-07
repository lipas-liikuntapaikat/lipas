(ns lipas.ui.map.projection
  " Loading this namespace causes side-effects to global OpenLayers
  object (js/ol)."
  (:require
   ["proj4" :as proj4]
   ["ol" :as ol]))

(def epsg3067-extent #js[-548576.0 6291456.0 1548576.0 8388608.0])

(def epsg3067-defs
  (str "+proj=utm"
       "+zone=35"
       "+ellps=GRS80"
       "+towgs84=0,0,0,0,0,0,0"
       "+units=m"
       "+no_defs"))

(defn init! []
  (proj4/defs "EPSG:3067" epsg3067-defs)
  (ol/proj.proj4.register proj4)

  (let [proj (ol/proj.get "EPSG:3067")]
    (.setExtent proj epsg3067-extent)
    {:proj4 proj4 :epsg3067 proj}))

(def proj (init!))

(def ^js/ol.proj.Projection epsg3067 (:epsg3067 proj))
(def epsg3067-top-left (ol/extent.getTopLeft (.getExtent epsg3067)))
