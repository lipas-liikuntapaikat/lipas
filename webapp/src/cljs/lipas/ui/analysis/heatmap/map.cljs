(ns lipas.ui.analysis.heatmap.map
  (:require ["ol/Feature$default" :as Feature]
            ["ol/format/GeoJSON$default" :as GeoJSON]
            ["ol/layer/Heatmap$default" :as HeatmapLayer]
            ["ol/source/Vector$default" :as VectorSource]
            [lipas.ui.analysis.heatmap.db :as db]))

(def gradients
  {:default #js ["#0000ff" "#00ffff" "#00ff00" "#ffff00" "#ff0000"]
   :cool #js ["#800080" "#0000ff" "#00ffff" "#ffffff"]
   :warm #js ["#ffff00" "#ffa500" "#ff0000"]
   :grayscale #js ["#ffffff" "#808080" "#000000"]
   :accessibility #js ["#ff0000" "#ffff00" "#00ff00"]})

(defn create-weight-fn [type]
  (case type
    :linear (fn [^js feature]
              (let [weight (.get feature "normalized-weight")]
                (if (number? weight) weight 0)))
    :logarithmic (fn [^js feature]
                   (let [weight (.get feature "normalized-weight")]
                     (js/Math.log (inc (if (number? weight) weight 0)))))
    :exponential (fn [^js feature]
                   (let [weight (.get feature "weight")]
                     (js/Math.pow (if (number? weight) weight 0) 2)))
    :sqrt (fn [^js feature]
            (let [weight (.get feature "normalized-weight")]
              (js/Math.sqrt (if (number? weight) weight 0))))))

(defn geojson->features [geojson-data]
  (when (seq geojson-data)
    (let [format (GeoJSON. #js {:dataProjection "EPSG:4326"
                                :featureProjection "EPSG:3067"})]
      (.readFeatures format
                     (clj->js {:type "FeatureCollection"
                               :features geojson-data})
                     #js {:featureProjection "EPSG:3067"}))))

(defn create-heatmap-source []
  (VectorSource.))

(defn create-heatmap-layer [source]
  (let [visual-params (:visual db/default-db)
        {:keys [radius blur opacity gradient weight-fn]} visual-params]
    (HeatmapLayer.
     #js {:source source
          :visible true
          :radius (or radius 20)
          :blur (or blur 15)
          :opacity (or opacity 0.8)
          :gradient (get gradients gradient (get gradients :default))
          :weight (create-weight-fn (or weight-fn :linear))})))

(defn update-heatmap-source!
  [^js source heatmap-data]
  (.clear source)
  (when-let [features (geojson->features heatmap-data)]
    (.addFeatures source features)))

(defn update-heatmap-layer!
  [^js layer visual-params]
  (let [{:keys [radius blur opacity gradient weight-fn]} visual-params]
    (when weight-fn
      (.setWeight layer (create-weight-fn weight-fn)))
    (when radius
      (.setRadius layer radius))
    (when blur
      (.setBlur layer blur))
    (when (some? opacity)
      (.setOpacity layer opacity))
    (when gradient
      (.setGradient layer (get gradients gradient (get gradients :default))))))

(defn update-heatmap-data!
  [{:keys [layers] :as map-ctx} heatmap]
  (when-let [^js layer (-> layers :overlays :heatmap)]
    (when-let [source (.getSource layer)]
      (let [heatmap-data (:heatmap-data heatmap)]
        (update-heatmap-source! source heatmap-data))))
  map-ctx)

(defn update-heatmap-visuals!
  [{:keys [layers] :as map-ctx}
   {:keys [visual] :as heatmap}]
  (when-let [layer (-> layers :overlays :heatmap)]
    (update-heatmap-layer! layer visual))
  map-ctx)
