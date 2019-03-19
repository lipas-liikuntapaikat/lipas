(ns lipas.ui.map.utils
  "NOTE: make sure `lipas.ui.map.projection` is loaded first for
  necessary side-effects to take effect.`"
  (:require
   ["ol"]
   [lipas.ui.map.projection]
   [clojure.string :as string]
   [lipas.ui.map.events :as events]
   [lipas.ui.map.styles :as styles]
   [lipas.ui.utils :refer [<== ==>] :as utils]))

(def geoJSON (ol.format.GeoJSON. #js{:dataProjection    "EPSG:4326"
                                     :featureProjection "EPSG:3067"}))

(defn ->ol-features [geoJSON-features]
  (.readFeatures geoJSON geoJSON-features))

(defn ->geoJSON [ol-features]
  (.writeFeaturesObject geoJSON ol-features))

(defn ->clj [x]
  (js->clj x :keywordize-keys true))

(def ->geoJSON-clj (comp ->clj ->geoJSON))

(defn show-address-marker!
  [{:keys [layers] :as map-ctx} address]
  (let [f (.readFeature geoJSON (clj->js address))]
    (.setStyle f styles/blue-marker-style)
    (-> layers :overlays :markers .getSource (.addFeature f))
    map-ctx))

;; Popups are rendered 'outside' OpenLayers by React so we need to
;; inform the outside world.
(defn clear-popup! [map-ctx]
  (==> [::events/show-popup nil])
  map-ctx)

(defn update-geoms! [{:keys [layers] :as map-ctx} geoms]
  (let [vectors (-> layers :overlays :vectors)
        source  (.getSource vectors)]

    ;; Remove existing features
    (.clear source)

    ;; Add new geoms
    (doseq [g    geoms
            :let [fs (-> g clj->js ->ol-features)]]
      (.addFeatures source fs))

    (assoc map-ctx :geoms geoms)))

(defn set-basemap! [{:keys [layers] :as map-ctx} basemap]
  (doseq [[k v] (:basemaps layers)
          :let  [visible? (= k basemap)]]
    (.setVisible v visible?))
  map-ctx)

(defn select-features! [{:keys [interactions] :as map-ctx} features]
  (let [select (-> interactions :select)]
    (doto (.getFeatures select)
      (.clear)
      (.extend features))
    map-ctx))

(defn clear-markers! [{:keys [layers] :as map-ctx}]
  (-> layers :overlays :markers .getSource .clear)
  map-ctx)

(defn enable-hover! [{:keys [^js/ol.Map lmap interactions*] :as map-ctx}]
  (let [hover (:hover interactions*)]
    (-> hover .getFeatures .clear)
    (.addInteraction lmap hover)
    (assoc-in map-ctx [:interactions :hover] hover)))

(defn enable-select! [{:keys [^js/ol.Map lmap interactions*] :as map-ctx}]
  (let [select (:select interactions*)]
    (-> select .getFeatures .clear)
    (.addInteraction lmap select)
    (assoc-in map-ctx [:interactions :select] select)))

(defn find-feature-by-id [{:keys [layers]} fid]
  (let [layer  (-> layers :overlays :vectors)
        source (.getSource layer)]
    (.getFeatureById source fid)))

(defn find-features-by-lipas-id [{:keys [layers]} lipas-id]
  (let [layer  (-> layers :overlays :vectors)
        source (.getSource layer)
        res    #js[]]
    (.forEachFeature source
                     (fn [f]
                       (when (-> (.getId f)
                                 (string/split "-")
                                 first
                                 (= (str lipas-id)))
                         (.push res f))
                       ;; Iteration stops if truthy val is returned
                       ;; but we want to find all matching features so
                       ;; nil is returned.
                       nil))
    res))

(def finite? (complement infinite?))

(defn fit-to-extent!
  [{:keys [^js/ol.View view ^js.ol.Map lmap] :as map-ctx} extent]
  (let [padding (or (-> map-ctx :mode :content-padding) #js[0 0 0 0])]
    (when (and view lmap (some finite? extent))
      (.fit view extent #js{:size                (.getSize lmap)
                            :padding             (clj->js padding)
                            :constrainResolution true})))
  map-ctx)

(defn fit-to-features! [map-ctx fs]
  (let [extent (-> fs first .getGeometry .getExtent)]
    (doseq [f (rest fs)]
      (ol.extent.extend extent (-> f .getGeometry .getExtent)))
    (fit-to-extent! map-ctx extent)))

(defn select-sports-site! [map-ctx lipas-id]
  (if-let [fs (not-empty (find-features-by-lipas-id map-ctx lipas-id))]
    (-> map-ctx
        (select-features! fs)
        (fit-to-features! fs))
    map-ctx))

(defn update-center! [{:keys [^js/ol.View view] :as map-ctx}
                      {:keys [lon lat] :as center}]
  (.setCenter view #js[lon lat])
  (assoc map-ctx :center center))

(defn update-zoom! [{:keys [^js/ol.View view] :as map-ctx} zoom]
  (.setZoom view zoom)
  (assoc map-ctx :zoom zoom))

(defn show-feature! [{:keys [layers] :as map-ctx} geoJSON-feature]
  (let [vectors (-> layers :overlays :edits)
        source  (.getSource vectors)
        fs      (-> geoJSON-feature clj->js ->ol-features)]
    (.addFeatures source fs)
    map-ctx))

(defn clear-interactions! [{:keys [^js/ol.Map lmap interactions interactions*]
                            :as   map-ctx}]
  ;; Special treatment for 'singleton' interactions*. OpenLayers
  ;; doesn't treat 'copies' identical to original ones. Therefore we
  ;; need to pass the original ones explicitly.
  (doseq [v     (vals (merge interactions interactions*))
          :when (some? v)]
    (.removeInteraction lmap v))

  (assoc map-ctx :interactions {}))
