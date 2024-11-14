(ns lipas.ui.map2.utils
  (:require-macros lipas.ui.map2.utils) 
  (:require ["ol-new/format/GeoJSON$default" :as GeoJSON]
            [cljs-bean.core :refer [->clj]]
            [uix.core :as uix]))

(def MapContext (uix/create-context))
(def MapContextProvider (.-Provider MapContext))

(defn ^js use-ol []
  (let [ctx (uix/use-context MapContext)]
    (.-current (:ol-ref ctx))))

(def geoJSON (GeoJSON. #js {:dataProjection    "EPSG:4326"
                            :featureProjection "EPSG:3067"}))

(defn ->ol-features [geoJSON-features]
  (.readFeatures geoJSON geoJSON-features))

(defn ->ol-feature [geoJSON-feature]
  (.readFeature geoJSON geoJSON-feature))

(defn ->geoJSON [ol-features]
  (.writeFeaturesObject geoJSON ol-features))

(def ->geoJSON-clj (comp ->clj ->geoJSON))
