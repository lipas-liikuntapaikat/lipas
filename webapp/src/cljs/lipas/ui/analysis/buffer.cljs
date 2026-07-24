(ns lipas.ui.analysis.buffer
  "Reachability buffer geometry + drawing. Moved out of
   lipas.ui.map.utils so @turf/buffer (which drags the ~260KB jsts
   geometry engine) ships in the lazy :analysis module instead of :map.
   The map component reaches draw-analytics-buffer! through
   lipas.ui.map.hooks once the module has installed itself."
  (:require ["@turf/buffer$default" :as turf-buffer]
            ["@turf/combine$default" :as turf-combine]
            ["ol/Feature$default" :as Feature]
            ["ol/geom/Circle$default" :as Circle]
            [lipas.ui.map.utils :as map-utils]))

(defn calc-buffer-geom [geoms distance-km]
  (case (-> geoms :features first :geometry :type)

    "Point"
    geoms

    ("LineString" "Polygon")
    (-> geoms
        clj->js
        (turf-combine)
        (turf-buffer distance-km #js {:units "kilometers"})
        (js->clj :keywordize-keys true))

    nil))

(defn draw-analytics-buffer!
  [{:keys [layers] :as map-ctx}
   {:keys [distance-km selected-sports-site] :as analysis}]

  (let [{:keys [buffer-geom center]} (get-in analysis [:runs selected-sports-site])
        ^js analysis-layer (-> layers :overlays :analysis)]

    ;; Clear existing buffer
    (-> analysis-layer .getSource .clear)

    (when-let [buff-feature
               (case (-> buffer-geom :features first :geometry :type)

                 "Point"
                 (when (and (:lon center) (:lat center))
                   (let [center (map-utils/wgs84->epsg3067 #js [(:lon center) (:lat center)])]
                     (Feature. #js {:geometry (Circle. center (* distance-km 1000))})))

                 ("LineString" "Polygon")
                 (-> buffer-geom :features first clj->js map-utils/->ol-feature)

                 nil)]

      (-> analysis-layer .getSource (.addFeature buff-feature))))

  map-ctx)
