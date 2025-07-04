(ns lipas.ui.map.utils
  "NOTE: make sure `lipas.ui.map.projection` is loaded first for the
  necessary side-effects to take effect.`"
  (:require ["@mapbox/togeojson" :as toGeoJSON]
            ["@turf/area$default" :as turf-area]
            ["@turf/buffer$default" :as turf-buffer]
            ["@turf/clean-coords$default" :as turf-clean-coords]
            ["@turf/combine$default" :as turf-combine]
            ["@turf/helpers" :refer [convertArea point]]
            ["@turf/kinks$default" :as turf-kinks]
            ["@turf/length$default" :as turf-length]
            ["@turf/line-split$default" :as turf-line-split]
            ["@turf/meta" :as turf-meta]
            ["@turf/nearest-point-on-line$default" :as turf-nearest-point-on-line]
            ["@turf/simplify$default" :as turf-simplify]
            ["@turf/truncate$default" :as turf-truncate]
            ["ol/Feature$default" :as Feature]
            ["ol/events/condition" :as events-condition]
            ["ol/extent" :as extent]
            ["ol/format/GeoJSON$default" :as GeoJSON]
            ["ol/format/WKT$default" :as WKT]
            ["ol/geom/Circle$default" :as Circle]
            ["ol/interaction" :as interaction]
            ["ol/proj" :as proj]
            ["shpjs" :as shp]
            [lipas.ui.map.projection] ;; Loaded for side-effects
            [clojure.reader :refer [read-string]]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [goog.array :as garray]
            [goog.object :as gobj]
            [goog.string.path :as gpath]
            [lipas.ui.map.styles :as styles]
            [lipas.ui.utils :refer [==>] :as utils]))

(def geoJSON (GeoJSON. #js {:dataProjection "EPSG:4326"
                            :featureProjection "EPSG:3067"}))

(def wkt (WKT. #js {:dataProjection "EPSG:4326"
                    :featureProjection "EPSG:3067"}))

(defn ->ol-features [geoJSON-features]
  (.readFeatures geoJSON geoJSON-features))

(defn ->ol-feature [geoJSON-feature]
  (.readFeature geoJSON geoJSON-feature))

(defn ->geoJSON [ol-features]
  (.writeFeaturesObject geoJSON ol-features))

(defn ->clj [x]
  (js->clj x :keywordize-keys true))

(def ->geoJSON-clj (comp ->clj ->geoJSON))

(defn ->wkt [ol-feature]
  (.writeFeature wkt ol-feature))

(defn <-wkt [s]
  (.readFeature wkt s #js {:dataProjection "EPSG:4326"
                           :featureProjection "EPSG:3067"}))

(defn ->geom-coll [fcoll]
  {:type "GeometryCollection"
   :geometries (->> fcoll :features (map :geometry))})

(defn every-coord-in-wgs84-finland-bounds?
  [js-fcoll]
  (every? (fn [[lon lat]]
            (and (s/valid? :lipas.location.coordinates/map-wgs84-bounds-lon lon)
                 (s/valid? :lipas.location.coordinates/map-wgs84-bounds-lat lat)))
          (turf-meta/coordAll js-fcoll)))

(defn parse-dom [text]
  (let [parser (js/DOMParser.)]
    (.parseFromString parser text "text/xml")))

(defn- text->geoJSON [{:keys [file ext enc cb]}]
  (let [reader (js/FileReader.)
        cb (fn [e]
             (let [text (-> e .-target .-result)
                   parsed (condp = ext
                            "geojson" (js/JSON.parse text)
                            "json" (js/JSON.parse text)
                            "kml" (-> text parse-dom toGeoJSON/kml)
                            "gpx" (-> text parse-dom toGeoJSON/gpx))]
               (cb parsed)))]
    (set! (.-onload reader) cb)
    (.readAsText reader file enc)
    {}))

(defmulti file->geoJSON :ext)

(defmethod file->geoJSON "zip" [{:keys [file enc cb]}]
  (-> file .arrayBuffer (.then shp) (.then cb)))

(defmethod file->geoJSON "gpx" [params] (text->geoJSON params))
(defmethod file->geoJSON "kml" [params] (text->geoJSON params))
(defmethod file->geoJSON "json" [params] (text->geoJSON params))
(defmethod file->geoJSON "geojson" [params] (text->geoJSON params))
(defmethod file->geoJSON :default [params] {:unknown (:ext params)})

(defn parse-ext [file]
  (-> file
      (gobj/get "name" "")
      gpath/extension
      str/lower-case))

(comment
  (def pf {:type "Feature"
           :geometry
           {:type "Point", :coordinates [25.635576 64.930784]}})

  (def lf {:type "LineString",
           :coordinates
           [[25.635078 64.9308]
            [25.635324 64.930785]
            [25.635576 64.930784]
            [25.635617 64.93085]]})

  (-> lf
      clj->js
      ->ol-feature
      ->wkt))

(defn simplify-scale
  "Scales given (natural) number to a usable simplification tolerance in
  degrees. The scale was found via experimentation.

  The tolerance is used as the Hausdorff distance parameter for
  Reimer-Douglas-Peucker simplification algorithm, where the tolerance
  is the maximum distance between the original and simplified curves.

  0.00001 degrees is between 4-6 meters in Finland's latitude."
  [n]
  (* 0.00001 n))

(defn simplify
  [fcoll tolerance]
  (-> fcoll
      clj->js
      (turf-simplify #js {:mutate true
                          :tolerance tolerance
                          :highQuality true})
      (->clj)))

(defn geom-coll->features [geom-coll]
  (->> geom-coll
       :geometries
       (map-indexed
         (fn [idx g]
           {:type "Feature"
            :geometry g
            :properties
            {:id (str (gensym))
             :name (str "geom-" (inc idx))}}))))

(defn normalize-geom-colls
  "Handles a special case where geometries are contained in a
  GeometryCollection. Normalizes geom coll into features with dummy
  props."
  [fcoll geom-type]
  (->> fcoll
       :features
       (filter (comp #{"GeometryCollection"} :type :geometry))
       (map :geometry)
       (mapcat geom-coll->features)
       (filter (comp #{geom-type} :type :geometry))
       (utils/index-by (comp :id :properties))))

(defn multi-geom->single-geoms
  [multi-geom geom-type props]
  (->> multi-geom
       :coordinates
       (map-indexed
         (fn [idx coords]
           {:type "Feature"
            :geometry
            {:type geom-type
             :coordinates coords}
            :properties
            (merge
              props
              {:id (str (gensym))}
              (when-let [s (:nimi props)]
                {:nimi (str s " osa " (inc idx))})
              (when-let [s (:name props)]
                {:name (str s " part " (inc idx))}))}))))

(defn normalize-multi-geoms
  "Makes an effort to convert multi-geoms into single geoms."
  [fcoll geom-type]
  (->> fcoll
       :features
       (filter (comp #{"MultiLineString" "MultiPolygon"} :type :geometry))
       (map (juxt :geometry :properties))
       (mapcat (fn [[g props]] (multi-geom->single-geoms g geom-type props)))
       (filter (comp #{geom-type} :type :geometry))
       (utils/index-by (comp :id :properties))))

(defn add-marker!
  [{:keys [layers] :as map-ctx} ^js f style]
  (let [^js marker-layer (-> layers :overlays :markers)]
    (.setStyle f style)
    (-> marker-layer .getSource (.addFeature f))
    map-ctx))

(defn show-address-marker!
  [map-ctx address]
  (let [f (.readFeature geoJSON (clj->js address))]
    (add-marker! map-ctx f styles/blue-marker-style)))

(defn show-elevation-marker!
  [map-ctx ^js elevation]
  (let [f (.readFeature geoJSON (point (.-coords elevation) elevation))]
    (add-marker! map-ctx f styles/blue-marker-style)))

(defn clear-problems! [{:keys [layers] :as map-ctx}]
  (let [^js marker-layer (-> layers :overlays :markers)]
    (-> marker-layer .getSource .clear))
  map-ctx)

(defn clear-population! [{:keys [layers] :as map-ctx}]
  (let [^js pop-layer (-> layers :overlays :population)]
    (-> pop-layer .getSource .clear))
  map-ctx)

(defn clear-highlights! [{:keys [layers] :as map-ctx}]
  (let [^js layer (-> layers :overlays :highlights)]
    (-> layer .getSource .clear))
  map-ctx)

(defn show-heatmap-hover-highlight!
  [{:keys [layers ^js view] :as map-ctx} coords]
  (let [^js layer (-> layers :overlays :highlights)
        source (.getSource layer)
        ;; Calculate radius based on current zoom level for better visual scaling
        ;; Higher zoom = smaller radius
        resolution (.getResolution view)
        radius (* 20 resolution) ; This will scale nicely with zoom
        feature (Feature. #js {:geometry (Circle. coords radius)})]
    ;; Clear previous highlights
    (.clear source)
    ;; Set the heatmap highlight style
    (.setStyle feature styles/heatmap-highlight-style)
    ;; Add new highlight
    (.addFeature source feature))
  map-ctx)

(defn show-problems!
  [map-ctx problems]
  (doseq [p (-> problems :data :features)]
    (let [f (.readFeature geoJSON (clj->js p))]
      (add-marker! map-ctx f styles/red-marker-style)))
  map-ctx)

;; Popups are rendered 'outside' OpenLayers by React so we need to
;; inform the outside world.
(defn clear-popup! [map-ctx]
  (==> [:lipas.ui.map.events/show-popup nil])
  map-ctx)

(defn update-geoms!
  [{:keys [layers] :as map-ctx} geoms]
  (let [^js vectors (-> layers :overlays :vectors)
        source (.getSource vectors)]

    ;; Remove existing features
    (.clear source)

    ;; Add new geoms
    (doseq [g geoms
            :let [fs (->ol-features g)]]
      (.addFeatures source fs))

    (assoc map-ctx :geoms geoms)))

(defn update-lois!
  [{:keys [layers] :as map-ctx} geoms]
  (let [^js lois (-> layers :overlays :lois)
        source (.getSource lois)]

    ;; Remove existing features
    (.clear source)

    ;; Add new geoms
    (doseq [g geoms
            :let [fs (->ol-features (clj->js g))]]
      (.addFeatures source fs))

    (assoc map-ctx :lois geoms)))

(defn set-basemap!
  [{:keys [layers] :as map-ctx} {:keys [opacity layer] :as _basemap}]
  (doseq [[k ^js v] (:basemaps layers)
          :let [visible? (= k layer)]]
    (.setOpacity v opacity)
    (.setVisible v visible?))
  map-ctx)

(defn set-overlays!
  [{:keys [layers] :as map-ctx} selected-overlays]
  (doseq [[k ^js v] (:overlays layers)
          :let [visible? (contains? selected-overlays k)]]
    (.setVisible v visible?))
  map-ctx)

(defn select-features!
  [{:keys [interactions] :as map-ctx} features]
  (let [^js select (-> interactions :select)]
    (doto (.getFeatures select)
      (.clear)
      (.extend features))
    map-ctx))

(defn unselect-features!
  [{:keys [interactions*] :as map-ctx}]
  (let [^js select (:select interactions*)]
    (-> select .getFeatures .clear))
  map-ctx)

(defn clear-markers! [{:keys [layers] :as map-ctx}]
  (let [^js marker-layer (-> layers :overlays :markers)]
    (-> marker-layer .getSource .clear))
  map-ctx)

(defn- enable-hover!
  [{:keys [^js lmap interactions*] :as map-ctx} k]
  (let [^js hover (k interactions*)]
    (-> hover .getFeatures .clear)
    (.addInteraction lmap hover)
    (assoc-in map-ctx [:interactions k] hover)))

(defn enable-vector-hover!
  [map-ctx]
  (enable-hover! map-ctx :vector-hover))

(defn enable-loi-hover!
  [map-ctx]
  (enable-hover! map-ctx :loi-hover))

(defn enable-marker-hover!
  [map-ctx]
  (enable-hover! map-ctx :marker-hover))

(defn enable-population-hover!
  [map-ctx]
  (enable-hover! map-ctx :population-hover))

(defn enable-schools-hover!
  [map-ctx]
  (enable-hover! map-ctx :schools-hover))

(defn enable-diversity-grid-hover!
  [map-ctx]
  (enable-hover! map-ctx :diversity-grid-hover))

(defn enable-diversity-area-hover!
  [map-ctx]
  (enable-hover! map-ctx :diversity-area-hover))

(defn enable-heatmap-hover!
  [map-ctx]
  (enable-hover! map-ctx :heatmap-hover))

(defn enable-edits-hover!
  [{:keys [^js lmap layers] :as map-ctx}]
  (let [layer (-> layers :overlays :edits)
        hover (interaction/Select. #js {:layers #js [layer]
                                        :style #js [styles/editing-hover-style styles/vertices-style]
                                        :condition events-condition/pointerMove})]
    (.addInteraction lmap hover)
    (assoc-in map-ctx [:interactions :edits-hover] hover)))

(defn enable-select!
  [{:keys [^js lmap interactions*] :as map-ctx}]
  (let [^js select (:select interactions*)]
    (-> select .getFeatures .clear)
    (.addInteraction lmap select)
    (assoc-in map-ctx [:interactions :select] select)))

(defn enable-diversity-area-select!
  [{:keys [^js lmap interactions*] :as map-ctx}]
  (let [^js select (:diversity-area-select interactions*)]
    (-> select .getFeatures .clear)
    (.addInteraction lmap select)
    (assoc-in map-ctx [:interactions :select] select)))

(defn find-feature-by-id
  [{:keys [layers]} fid]
  (let [^js layer (-> layers :overlays :vectors)
        source (.getSource layer)]
    (.getFeatureById source fid)))

(defn find-features-by-lipas-id
  [{:keys [layers]} lipas-id]
  (let [^js layer (-> layers :overlays :vectors)
        source (.getSource layer)
        res #js []]
    (.forEachFeature source
                     (fn [f]
                       (when (-> (.get f "lipas-id")
                                 (= lipas-id))
                         (.push res f))
                       ;; Iteration stops if truthy val is returned
                       ;; but we want to find all matching features so
                       ;; nil is returned.
                       nil))
    res))

(def finite? (complement infinite?))

(defn fit-to-extent!
  ([map-ctx extent]
   (fit-to-extent! map-ctx extent {}))
  ([{:keys [^js view ^js lmap] :as map-ctx} extent opts]
   (let [padding (or (-> map-ctx :mode :content-padding) #js [0 0 0 0])]
     (when (and view lmap (some finite? extent))
       (.fit view extent (clj->js
                           (merge
                             {:size (.getSize lmap)
                              :padding (clj->js padding)
                              :constrainResolution true}
                             opts))))
     map-ctx)))

(defn fit-to-features! [map-ctx fs opts]
  (let [^js f (first fs)
        ^js extent (-> f .getGeometry .getExtent)]
    (doseq [^js f (rest fs)]
      (extent/extend extent (-> f .getGeometry .getExtent)))
    (fit-to-extent! map-ctx extent opts)))

(defn fit-to-fcoll! [map-ctx fcoll]
  (let [fs (-> fcoll clj->js ->ol-features)]
    (fit-to-features! map-ctx fs {})))

(defn select-sports-site!
  ([map-ctx lipas-id]
   (select-sports-site! map-ctx lipas-id {}))
  ([map-ctx lipas-id fit-opts]
   (if-let [fs (not-empty (find-features-by-lipas-id map-ctx lipas-id))]
     (-> map-ctx
         (select-features! fs)
         (fit-to-features! fs fit-opts))
     (unselect-features! map-ctx))))

(defn update-center!
  [{:keys [^js view] :as map-ctx}
   {:keys [lon lat] :as center}]
  ;; TODO: Juho-later
  ;; By telling OL if the left side panel is open,
  ;; it can correctly take this into account when focusing on coordinates.
  ;; (set! (.-padding view) #js [0 0 0 530])
  (.setCenter view #js [lon lat])
  (assoc map-ctx :center center))

(defn update-zoom!
  [{:keys [^js view] :as map-ctx} zoom]
  (.setZoom view zoom)
  (assoc map-ctx :zoom zoom))

(defn show-feature!
  [{:keys [layers] :as map-ctx} geoJSON-feature]
  (let [^js vectors (-> layers :overlays :edits)
        source (.getSource vectors)
        fs (-> geoJSON-feature clj->js ->ol-features)]
    (.addFeatures source fs)
    map-ctx))

(defn clear-interactions!
  [{:keys [^js lmap interactions interactions*]
    :as map-ctx}]
  ;; Special treatment for 'singleton' interactions*. OpenLayers
  ;; doesn't treat 'copies' identical to original ones. Therefore we
  ;; need to pass the original ones explicitly.
  (doseq [v (vals (merge interactions interactions*))
          :when (some? v)]
    (.removeInteraction lmap v))

  (assoc map-ctx :interactions {}))

(defn refresh-select!
  [{:keys [interactions] :as map-ctx} lipas-id]
  (let [^js select (-> interactions :select)
        fs (find-features-by-lipas-id map-ctx lipas-id)]
    (doto (.getFeatures select)
      (.clear)
      (.extend fs))
    map-ctx))

(defn- ->splitter [fcoll]
  (let [fs (gobj/get fcoll "features")]
    (case (count fs)
      1 (first fs)
      (-> fcoll
          turf-combine
          (gobj/getValueByKeys "features" 0)))))

(defn- split-by-features [f kinks]
  (let [splitter (->splitter kinks)
        splitted (turf-line-split f splitter)]
    (garray/forEach (gobj/get splitted "features")
                    (fn [f] (gobj/set f "id" (str (random-uuid)))))
    splitted))

(defn split-at-coords [ol-feature coords]
  (let [point #js {:type "Point" :coordinates coords}
        line (.writeFeatureObject geoJSON ol-feature)
        nearest (turf-nearest-point-on-line line point)]
    (-> line
        (split-by-features nearest)
        ->ol-features)))

(defn fix-kinks* [f]
  (let [kinks (turf-kinks f)]
    (if (-> kinks (gobj/get "features") not-empty)
      (-> (split-by-features f kinks)
          (gobj/get "features"))
      #js [f])))

(defn ->fcoll [fs]
  #js {:type "FeatureCollection"
       :features (clj->js fs)})

(defn fix-kinks [fcoll]
  (-> fcoll
      :features
      clj->js
      (garray/concatMap fix-kinks*)
      ->fcoll
      ->clj))

(defn find-kinks [fcoll]
  (-> fcoll
      :features
      clj->js
      (garray/concatMap #(-> % turf-kinks (gobj/get "features")))
      ->fcoll
      ->clj))

(defn clean-coords-safe [f]
  (try
    (turf-clean-coords f)
    (catch js/Error _e)))

(defn strip-z [fcoll]
  (-> fcoll
      clj->js
      (turf-truncate #js {:coordinates 2 :mutate true})
      (gobj/get "features")
      (garray/map clean-coords-safe)
      (garray/filter some?)
      ->fcoll
      ->clj))

(defn find-problems [fcoll]
  (when (#{"LineString"} (-> fcoll :features first :geometry :type))
    {:kinks (find-kinks fcoll)
     ;;:intersections (find-intersections fcoll)
     }))

(defn fix-features [ol-features]
  ol-features)

(defn calculate-length-km
  [fcoll]
  (when (seq (:features fcoll))
    (-> fcoll
        clj->js
        turf-length ;; returns kilometers
        (utils/round-safe 2)
        read-string)))

(defn calculate-area-km2
  [fcoll]
  (-> fcoll
      clj->js
      turf-area ;; returns square meters
      (convertArea "meters" "kilometers")
      (utils/round-safe 2)
      read-string))

(defn calculate-elevation-stats
  [fcoll]
  (->> fcoll
       :features
       (map (comp :coordinates :geometry))
       (mapcat (fn [coll] (map (fn [coords] (get coords 2)) coll)))
       (partition 2 1)
       (reduce (fn [res [prev curr]]
                 (let [d (- curr prev)]
                   (cond
                     (zero? d) res
                     (pos? d) (update res :ascend-m + d)
                     (neg? d) (update res :descend-m + (Math/abs d)))))
               {:ascend-m 0 :descend-m 0})))

(defn wgs84->epsg3067 [wgs84-coords]
  (let [proj (proj/get "EPSG:3067")]
    (proj/fromLonLat wgs84-coords proj)))

(defn epsg3067->wgs84 [epsg3067-coords]
  (let [proj (proj/get "EPSG:3067")]
    (proj/toLonLat epsg3067-coords proj)))

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
                   (let [center (wgs84->epsg3067 #js [(:lon center) (:lat center)])]
                     (Feature. #js {:geometry (Circle. center (* distance-km 1000))})))

                 ("LineString" "Polygon")
                 (-> buffer-geom :features first clj->js ->ol-feature)

                 nil)]

      (-> analysis-layer .getSource (.addFeature buff-feature))))

  map-ctx)

(defn draw-diversity-areas!
  [{:keys [layers] :as map-ctx}
   {:keys [areas results]}]

  (let [^js area-layer (-> layers :overlays :diversity-area)]

    ;; Clear existing geoms
    (-> area-layer .getSource .clear)

    (doseq [[id feat] areas]
      (let [aggs (get-in results [id :aggs])
            ol-feat (->ol-feature (clj->js (update feat :properties merge aggs)))]

        #_(.setStyle ol-feat styles/diversity-aggs-style)
        (-> area-layer .getSource (.addFeature ol-feat)))))

  map-ctx)

;; Below is a WIP attempt to automagically fix badly drawn
;; linestrings.
;;
;; a) fix kinks
;; b) join lines that can be joined
;;
;; I got it kinda working for simple cases but I wasn't confident
;; enough to put it in production because it wasn't behaving correctly
;; with some of the more complex cases.. Needs more testing.
;;
;; Current implementation finds the problems and displays them to the
;; user but it's up to the human to fix the issues.

;; (defn merge-candidates [start end lines]
;;   (->> lines
;;        (filter #(some #{start end} [(first %) (last %)]))))

;; (defn merge-candidates2 [start end lines]
;;   (->> lines
;;        (filter #(and
;;                  (not= (first %) (last %))
;;                  (some #{start end} %)))))

;; (def cat-dedupe (comp cat (dedupe)))

;; (defn- join-lines
;;   "Joins l1 and l2 from corresponding elements in head or tail
;;   position. l1 or l2 may be reversed during the process.

;;   Examples:
;;   [1 2 3] [3 4 5] => [1 2 3 4 5]
;;   [1 2 3] [4 5 3] => [1 2 3 5 4]
;;   [1 2 3] [1 4 5] => [3 2 1 4 5]
;;   [1 2 3] [4 5 1] => [4 5 1 2 3]"
;;   [l1 l2]
;;   (cond
;;     (= (last l1) (first l2))  (into [] cat-dedupe [l1 l2])
;;     (= (last l1) (last l2))   (into [] cat-dedupe [l1 (reverse l2)])
;;     (= (first l1) (first l2)) (into [] cat-dedupe [(reverse l1) l2])
;;     (= (first l1) (last l2))  (into [] cat-dedupe [l2 l1])))

;; (defn merge-linestrings* [lines]
;;   (loop [wip {:res [] :todo lines}]

;;     ;; End condition
;;     (if (-> wip :todo empty?)
;;       (:res wip)

;;       (let [line   (-> wip :todo first)
;;             others (-> wip :todo (disj line))
;;             start  (first line)
;;             end    (last line)]

;;         (if (= start end)

;;           ;; Closed ring (can't merge)
;;           (recur
;;            (-> wip
;;                (update :res conj line)
;;                (assoc :todo others)))

;;           ;; Merge if exactly 1 candidate is found
;;           (let [candidates (merge-candidates2 start end others)]
;;             (if (= 1 (count candidates))

;;               (let [line2 (first candidates)]
;;                 (recur
;;                  (-> wip
;;                      (update :res conj (join-lines line line2))
;;                      (update :todo disj line line2))))

;;               (recur
;;                (-> wip
;;                    (update :res conj line)
;;                    (update :todo disj line))))))))))

;; (defn merge-linestrings [{:keys [features] :as fcoll}]
;;   (let [lines (into #{} (map (comp :coordinates :geometry)) features)]
;;     (if (> 2 (count lines))
;;       fcoll
;;       (assoc fcoll :features
;;               (->> (merge-linestrings* lines)
;;                    (map-indexed
;;                     (fn [idx coords]
;;                       {:type "Feature"
;;                        :geometry
;;                        {:type        "LineString"
;;                         :coordinates coords}
;;                        :id   (str (gensym temp-fid-prefix))}))
;;                    (into []))))))

;; (defn valid-line? [{:keys [geometry]}]
;;   (let [valid (and (= "LineString" (:type geometry))
;;                  (= 2 (->> geometry :coordinates distinct (take 2) count)))]
;;     (when-not valid
;;       (prn "Ditching invalid:")
;;       (prn geometry))
;;     valid))

;; (defn sensify [{:keys [geometry] :as f}]
;;   (let [start  (->> geometry :coordinates first)
;;         end    (->> geometry :coordinates last)
;;         coords (->> geometry :coordinates (into [] (distinct)))
;;         res    (if (= start end) (conj coords end) coords)]
;;     (assoc-in f [:geometry :coordinates] (into [] res))))

;; (defn split-by-intersections* [{:keys [features] :as fcoll} ixs]
;;   ;;(prn "Before split: " (count features))
;;   (let [splitter (-> ixs clj->js ->splitter)
;;         fs       (reduce
;;                   (fn [res f]
;;                     (into res (-> f
;;                                   clj->js
;;                                   (turf-line-split splitter)
;;                                   (gobj/get "features")
;;                                   ;;(garray/map turf-truncate)
;;                                   ;;(garray/map turf-clean-coords)
;;                                   ->clj
;;                                   (->>
;;                                    ;;(map sensify)
;;                                    (filter valid-line?)
;;                                    (mapv #(assoc % :id (str (gensym temp-fid-prefix))))))))
;;                   []
;;                   features)]
;;     ;;(prn "After split: " (count fs))
;;     (assoc fcoll :features fs)))

;; (defn find-intersections [{:keys [features] :as fcoll}]
;;   (let [fs (->> features
;;                 set
;;                 (reduce
;;                  (fn [res f]
;;                    (into res cat
;;                          (for [f2   (disj (set features) f)
;;                                :let [l1 (-> f :geometry clj->js)
;;                                      l2 (-> f2 :geometry clj->js)]]

;;                            (if (and l1 l2)
;;                              (-> (turf/lineIntersect l1 l2)
;;                                  (gobj/get "features")
;;                                  ;;(garray/map turf-truncate)
;;                                  ;;(garray/map turf-clean-coords)
;;                                  ->clj)))))
;;                  #{}))]
;;     (assoc fcoll :features fs)))

;; (defn- split-by-intersections [{:keys [features] :as fcoll}]
;;   (if (> 2 (count features))
;;     fcoll
;;     (let [ixs (find-intersections fcoll)]
;;       ;;(prn "Intersections: " (-> ixs :features count))
;;       ;;(prn ixs)
;;       (if (-> ixs :features seq)
;;         (split-by-intersections* fcoll ixs)
;;         fcoll))))

;; (defn fix-linestrings
;;   "Does following to given features:

;;   - Merges lines that can be merged (degree 2 nodes)
;;   - Fixes self-intersections (kinks) by splitting
;;   - Splits all lines at all intersections with any of the other lines"
;;   [ol-features]
;;   (-> ol-features
;;       ->geoJSON
;;       turf-truncate
;;       ->clj
;;       merge-linestrings
;;       (update :features #(filterv valid-line? %))
;;       fix-kinks
;;       split-by-intersections
;;       clj->js
;;       ->ol-features))

;; (defn fix-features [ol-features]
;;   (let [geom-type (-> ol-features first .getGeometry .getType)]
;;     (case geom-type
;;       "LineString" (fix-linestrings ol-features)
;;       ol-features)))

;; (def dying-geom
;;   {:type "FeatureCollection",
;;    :features
;;    [{:type "Feature",
;;      :geometry
;;      {:type "LineString",
;;       :coordinates [[25.61977 64.927513] [25.619746 64.927269]]},
;;      :properties nil,
;;      :id "G__3811"}
;;     {:type "Feature",
;;      :geometry
;;      {:type "LineString",
;;       :coordinates [[25.619746 64.927269] [25.620238 64.927283]]},
;;      :properties nil,
;;      :id "G__3783"}
;;     {:type "Feature",
;;      :geometry
;;      {:type "LineString",
;;       :coordinates [[25.61977 64.927513] [25.619746 64.927269]]},
;;      :properties nil,
;;      :id "G__3782"}
;;     {:type "Feature",
;;      :geometry
;;      {:type "LineString",
;;       :coordinates [[25.619746 64.927269] [25.61977 64.927513]]},
;;      :properties nil,
;;      :id "G__3786"}
;;     {:type "Feature",
;;      :geometry
;;      {:type "LineString",
;;       :coordinates [[25.61977 64.927513] [25.619746 64.927269]]},
;;      :properties nil,
;;      :id "G__3785"}
;;     {:type "Feature",
;;      :geometry
;;      {:type "LineString",
;;       :coordinates [[25.619746 64.927269] [25.61977 64.927513]]},
;;      :properties nil,
;;      :id "G__3784"}
;;     {:type "Feature",
;;      :geometry
;;      {:type "LineString",
;;       :coordinates [[25.619208 64.926996] [25.619342 64.927257]]},
;;      :properties nil,
;;      :id "G__3808"}
;;     {:type "Feature",
;;      :geometry
;;      {:type "LineString",
;;       :coordinates [[25.620238 64.927283] [25.61972 64.927007]]},
;;      :properties nil,
;;      :id "G__3793"}
;;     {:type "Feature",
;;      :geometry
;;      {:type "LineString",
;;       :coordinates [[25.619746 64.927269] [25.619342 64.927257]]},
;;      :properties nil,
;;      :id "G__3792"}
;;     {:type "Feature",
;;      :geometry
;;      {:type "LineString",
;;       :coordinates [[25.619746 64.927269] [25.619342 64.927257]]},
;;      :properties nil,
;;      :id "G__3813"}
;;     {:type "Feature",
;;      :geometry
;;      {:type "LineString",
;;       :coordinates [[25.619342 64.927257] [25.61972 64.927007]]},
;;      :properties nil,
;;      :id "G__3790"}
;;     {:type "Feature",
;;      :geometry
;;      {:type "LineString",
;;       :coordinates [[25.619746 64.927269] [25.619342 64.927257]]},
;;      :properties nil,
;;      :id "G__3789"}
;;     {:type "Feature",
;;      :geometry
;;      {:type "LineString",
;;       :coordinates [[25.619342 64.927257] [25.619746 64.927269]]},
;;      :properties nil,
;;      :id "G__3788"}
;;     {:type "Feature",
;;      :geometry
;;      {:type "LineString",
;;       :coordinates [[25.61972 64.927007] [25.619746 64.927269]]},
;;      :properties nil,
;;      :id "G__3794"}
;;     {:type "Feature",
;;      :geometry
;;      {:type "LineString",
;;       :coordinates [[25.619342 64.927257] [25.619746 64.927269]]},
;;      :properties nil,
;;      :id "G__3791"}
;;     {:type "Feature",
;;      :geometry
;;      {:type "LineString",
;;       :coordinates [[25.618741 64.92723] [25.617816 64.927202]]},
;;      :properties nil,
;;      :id "G__3797"}
;;     {:type "Feature",
;;      :geometry
;;      {:type "LineString",
;;       :coordinates [[25.617816 64.927202] [25.618741 64.92723]]},
;;      :properties nil,
;;      :id "G__3796"}
;;     {:type "Feature",
;;      :geometry
;;      {:type "LineString",
;;       :coordinates [[25.617816 64.927202] [25.618741 64.92723]]},
;;      :properties nil,
;;      :id "G__3801"}
;;     {:type "Feature",
;;      :geometry
;;      {:type "LineString",
;;       :coordinates [[25.619342 64.927257] [25.618741 64.92723]]},
;;      :properties nil,
;;      :id "G__3814"}
;;     {:type "Feature",
;;      :geometry
;;      {:type "LineString",
;;       :coordinates [[25.618741 64.92723] [25.617816 64.927202]]},
;;      :properties nil,
;;      :id "G__3795"}
;;     {:type "Feature",
;;      :geometry
;;      {:type "LineString",
;;       :coordinates [[25.619342 64.927257] [25.618741 64.92723]]},
;;      :properties nil,
;;      :id "G__3799"}
;;     {:type "Feature",
;;      :geometry
;;      {:type "LineString",
;;       :coordinates [[25.618741 64.92723] [25.619342 64.927257]]},
;;      :properties nil,
;;      :id "G__3802"}
;;     {:type "Feature",
;;      :geometry
;;      {:type "LineString",
;;       :coordinates [[25.618741 64.92723] [25.619342 64.927257]]},
;;      :properties nil,
;;      :id "G__3798"}
;;     {:type "Feature",
;;      :geometry
;;      {:type "LineString",
;;       :coordinates [[25.619342 64.927257] [25.618741 64.92723]]},
;;      :properties nil,
;;      :id "G__3800"}
;;     {:type "Feature",
;;      :geometry
;;      {:type "LineString",
;;       :coordinates [[25.617816 64.927202] [25.618262 64.92698]]},
;;      :properties nil,
;;      :id "G__3804"}
;;     {:type "Feature",
;;      :geometry
;;      {:type "LineString",
;;       :coordinates [[25.619208 64.926996] [25.618495 64.927102]]},
;;      :properties nil,
;;      :id "G__3806"}
;;     {:type "Feature",
;;      :geometry
;;      {:type "LineString",
;;       :coordinates [[25.618741 64.92723] [25.619208 64.926996]]},
;;      :properties nil,
;;      :id "G__3803"}
;;     {:type "Feature",
;;      :geometry
;;      {:type "LineString",
;;       :coordinates [[25.618495 64.927102] [25.618262 64.92698]]},
;;      :properties nil,
;;      :id "G__3807"}
;;     {:type "Feature",
;;      :geometry
;;      {:type "LineString",
;;       :coordinates [[25.619208 64.926996] [25.618262 64.92698]]},
;;      :properties nil,
;;      :id "G__3805"}
;;     {:type "Feature",
;;      :geometry
;;      {:type "LineString",
;;       :coordinates [[25.61972 64.927007] [25.619208 64.926996]]},
;;      :properties nil,
;;      :id "G__3787"}
;;     {:type "Feature",
;;      :geometry
;;      {:type "LineString",
;;       :coordinates [[25.618495 64.927102] [25.617816 64.927202]]},
;;      :properties nil,
;;      :id "G__3809"}
;;     {:type "Feature",
;;      :geometry
;;      {:type "LineString",
;;       :coordinates [[25.618741 64.92723] [25.618495 64.927102]]},
;;      :properties nil,
;;      :id "G__3810"}
;;     {:type "Feature",
;;      :geometry
;;      {:type "LineString",
;;       :coordinates [[25.618741 64.92723] [25.617816 64.927202]]},
;;      :properties nil,
;;      :id "G__3812"}
;;     {:type "Feature",
;;      :geometry
;;      {:type "LineString",
;;       :coordinates [[25.61977 64.927513] [25.617816 64.927202]]},
;;      :properties nil,
;;      :id "temp3815"}]})

;; (def killer-coords
;;   [[25.61977, 64.927513] [25.617815999999994, 64.927202]])
;; (def killer-geom {:type "LineString" :coordinates killer-coords})
;; (def killer-feature {:type "Feature" :geometry killer-geom})

;; (turf-truncate (clj->js killer-geom))

;; (-> dying-geom :features count)
;; (->> (update dying-geom :features conj killer-feature)
;;      (split-by-intersections)
;;      :features
;;      count
;;      )

;; (def easy
;;   {:type "FeatureCollection",
;;    :features
;;    [{:type "Feature",
;;      :geometry
;;      {:type "LineString",
;;       :coordinates
;;       [[25.74504757340173 62.60883756260095]
;;        [25.745300602130833 62.60866102563988]
;;        [25.74586650741268 62.60877169751404]]},
;;      :properties nil,
;;      :id "temp14"}
;;     {:type "Feature",
;;      :geometry
;;      {:type "LineString",
;;       :coordinates
;;       [[25.74586650741268 62.60877169751404]
;;        [25.74634838061013 62.608872629054495]
;;        [25.746625766677678 62.60870039304092]]},
;;      :properties nil,
;;      :id "temp15"}]})

;; (def easy2
;;   {:type "FeatureCollection",
;;    :features
;;    [{:type "Feature",
;;      :geometry
;;      {:type "LineString",
;;       :coordinates
;;       [[25.74504757340173 62.60883756260095]
;;        [25.745300602130833 62.60866102563988]
;;        [25.74586650741268 62.60877169751404]]},
;;      :properties nil,
;;      :id "temp14"}
;;     {:type "Feature",
;;      :geometry
;;      {:type "LineString",
;;       :coordinates
;;       [[25.74634838061013 62.608872629054495]
;;        [25.746625766677678 62.60870039304092]
;;        [25.74586650741268 62.60877169751404]]},
;;      :properties nil,
;;      :id "temp15"}]})

;; (merge-linestrings easy)
;; (merge-linestrings easy2)

;; (comment
;;   (def kink
;;     {:type "FeatureCollection",
;;      :features
;;      [{:type "Feature",
;;        :id "1"
;;        :geometry
;;        {:type "LineString",
;;         :coordinates
;;         [[25.74829599086967 62.60992094390775]
;;          [25.749438011346825 62.610070518117595]
;;          [25.749690556547957 62.609852625658625]
;;          [25.7484300396036 62.6100707537238]]}}]})

;;   (-> kink clj->js fix-kinks)
;;   (merge-line-strings kink)

;;   (turf/lineIntersect (clj->js kink) (clj->js kink))

;;   (def kinks
;;     {:type "FeatureCollection",
;;      :features
;;      [{:type     "Feature",
;;        :features [],
;;        :geometry
;;        {:type "LineString",
;;         :coordinates
;;         [[25.74809991565044 62.609440270660826]
;;          [25.7487270259911 62.60968963987866]
;;          [25.74834318779659 62.60938667187484]
;;          [25.74895985958416 62.60938494083431]
;;          [25.7480658612651 62.60963167475782]]}}]})

;;   (fix-kinks kinks)

;;   (turf/lineIntersect (clj->js kink) (clj->js kinks))

;;   (join-lines [1 2 3] [3 4 5])
;;   (join-lines [1 2 3] [4 5 3])
;;   (join-lines [1 2 3] [1 4 5])
;;   (join-lines [1 2 3] [4 5 1]))

;; (def spltr
;;   {:type "FeatureCollection", :features
;;    #{{:type "Feature", :properties {}, :geometry
;;       {:type "Point", :coordinates [25.635576 64.930784]}}}})

;; (def ls1 {:type "LineString", :coordinates [[25.635078 64.9308] [25.635324 64.930785] [25.635576 64.930784] [25.635617 64.93085]]})

;; (def ls2 {:type "LineString", :coordinates [[25.635576 64.930784] [25.635669 64.930721]]})

;; (def f1 {:type "Feature" :geometry ls1})
;; (def f2 {:type "Feature" :geometry ls2})

;; (def fcoll {:type "FeatureCollection" :features [f1 f2]})

;; (->clj
;;  (turf-line-split (clj->js f1) (-> spltr :features first clj->js)))

;; (->clj
;;  (turf-line-split (clj->js f2) (-> spltr :features first clj->js)))

;; (find-intersections fcoll)
;; (split-by-intersections2 fcoll)

;; (def edge
;;   {:type "FeatureCollection"
;;    :features
;;    [{:type "Feature"
;;      :geometry
;;      {:type "LineString"
;;       :coordinates [[0 0] [10 0]]}}
;;     {:type "Feature"
;;      :geometry
;;      {:type "LineString"
;;       :coordinates [[0 10] [0 0]]}}]})

;; (def cross
;;   {:type "FeatureCollection"
;;    :features
;;    [{:type "Feature"
;;      :geometry
;;      {:type "LineString"
;;       :coordinates [[0 0] [10 0]]}}
;;     {:type "Feature"
;;      :geometry
;;      {:type "LineString"
;;       :coordinates [[5 10] [5 -10]]}}]})

;; (find-intersections edge)
;; (split-by-intersections2 edge)

;; (find-intersections cross)
;; (split-by-intersections2 cross)

;; (def f1 (-> edge :features first clj->js))
;; (def f2 (-> edge :features second clj->js))

;; (def g1 (-> edge :features first :geometry clj->js))
;; (def g2 (-> edge :features second :geometry clj->js))

;; (split-by-intersections2 edge)
;; (split-by-features f1 (turf/lineIntersect g1 g2))

;; (turf/lineIntersect g1 g2)
;; (turf-combine (turf/lineIntersect g1 g2))
;; (def splitter (-> (turf/lineIntersect g1 g2) ->splitter))
;; (turf-line-split f1 splitter)
;; (turf-line-split f2 splitter)

;; (defn debug-fcoll [x]
;;   (-> x
;;       (gobj/get "features")
;;       (garray/map (fn [y] (-> y
;;                               (gobj/get "geometry")
;;                               (gobj/get "coordinates"))))
;;       (js/console.log))
;;   x)
