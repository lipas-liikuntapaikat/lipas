(ns lipas.ui.map.projection
  " Loading this namespace causes side-effects to global OpenLayers
  object."
  (:require
   proj4
   ["ol"]))

(def epsg3067-extent #js[-548576.0 6291456.0 1548576.0 8388608.0])

(defn init! []
  (js/proj4.defs "EPSG:3067" (str "+proj=utm"
                                  "+zone=35"
                                  "+ellps=GRS80"
                                  "+towgs84=0,0,0,0,0,0,0"
                                  "+units=m"
                                  "+no_defs"))
  (js/ol.proj.proj4.register proj4)

  (let [proj (ol.proj.get "EPSG:3067")]
    (.setExtent proj epsg3067-extent)

    {:proj4    proj4
     :epsg3067 proj}))


(def proj (init!))

(def ^js/ol.proj.Projection epsg3067 (:epsg3067 proj))
(def epsg3067-top-left (ol.extent.getTopLeft (.getExtent epsg3067)))
