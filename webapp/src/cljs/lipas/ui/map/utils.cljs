(ns lipas.ui.map.utils
  "NOTE: make sure `lipas.ui.map.projection` is loaded first for
  necessary side-effects to take effect.`"
  (:require
   ["ol"]
   ["turf" :as turf]
   [clojure.string :as string]
   [goog.array :as garray]
   [goog.object :as gobj]
   [lipas.ui.map.events :as events]
   [lipas.ui.map.projection]
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

(defn unselect-features! [{:keys [interactions*] :as map-ctx}]
  (-> interactions* :select .getFeatures .clear)
  map-ctx)

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
    (unselect-features! map-ctx)))

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

;; (defn- ->splitter [fcoll]
;;   (let [fs (gobj/get fcoll "features")]
;;     (case (count fs)
;;       1 (first fs)
;;       (-> fcoll
;;           turf/combine
;;           (gobj/getValueByKeys "features" 0 "geometry")))))

;; (defn- split-by-kinks [f kinks]
;;   (let [splitter (->splitter kinks)
;;         fid      (gobj/get f "id")
;;         splitted (turf/lineSplit f splitter)]
;;     (garray/forEach (gobj/get splitted "features")
;;                     (fn [f] (gobj/set f "id" (str (gensym fid)))))
;;     splitted))

;; (defn fix-kinks [ol-feature]
;;   (let [f     (.writeFeatureObject geoJSON ol-feature)
;;         kinks (turf/kinks f)]
;;     (if (-> kinks (gobj/get "features") not-empty)
;;       (-> (split-by-kinks f kinks) ->ol-features)
;;       #js[ol-feature])))

;; (defn merge-candidates [start end lines]
;;   (->> lines
;;        (filter #(some #{start end} [(first %) (last %)]))))

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
;;           (let [candidates (merge-candidates start end others)]
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
;;                        :id   (str (gensym))}))
;;                    (into []))))))

;; (defn- split-by-intersections* [l1 l2]
;;   (let [intersections (turf/lineIntersect l1 l2)]
;;     (if (-> intersections (gobj/get "features") not-empty)
;;       (let [ls1 (split-by-kinks l1 intersections)
;;             ls2 (split-by-kinks l2 intersections)]
;;         (-> #js[] (.concat (gobj/get ls1 "features")
;;                            (gobj/get ls2 "features"))))
;;       #js[l1 l2])))

;; (defn- split-by-intersections [fcoll]
;;   (let [fs (.reduce
;;             (gobj/get fcoll "features")
;;             (fn [res f]
;;               (if (empty? res)
;;                 (.push res f)
;;                 (.forEach res (fn [f2]
;;                                 (.concat res (split-by-intersections f2 f))))))
;;             #js[])]
;;     (gobj/set fcoll "features" fs)))

;; ;; (garray/concatMap fix-kinks)
;; (defn fix-linestrings [ol-features]
;;   (-> ol-features
;;       ->geoJSON-clj
;;       merge-linestrings
;;       clj->js
;;       split-by-intersections
;;       ))

;; (defn fix-features [ol-features]
;;   (let [geom-type (-> ol-features first .getGeometry .getType)]
;;     (case geom-type
;;       "LineString" (fix-linestrings ol-features)
;;       ol-features)))

(defn fix-features [ol-features]
  ;; TODO
  ol-features)

(comment
  (def easy
    {:type "FeatureCollection",
     :features
     [{:type "Feature",
       :geometry
       {:type "LineString",
        :coordinates
        [[25.74504757340173 62.60883756260095]
         [25.745300602130833 62.60866102563988]
         [25.74586650741268 62.60877169751404]]},
       :properties nil,
       :id "temp14"}
      {:type "Feature",
       :geometry
       {:type "LineString",
        :coordinates
        [[25.74586650741268 62.60877169751404]
         [25.74634838061013 62.608872629054495]
         [25.746625766677678 62.60870039304092]]},
       :properties nil,
       :id "temp15"}]})

  (def easy2
    {:type "FeatureCollection",
     :features
     [{:type "Feature",
       :geometry
       {:type "LineString",
        :coordinates
        [[25.74504757340173 62.60883756260095]
         [25.745300602130833 62.60866102563988]
         [25.74586650741268 62.60877169751404]]},
       :properties nil,
       :id "temp14"}
      {:type "Feature",
       :geometry
       {:type "LineString",
        :coordinates
        [[25.74634838061013 62.608872629054495]
         [25.746625766677678 62.60870039304092]
         [25.74586650741268 62.60877169751404]]},
       :properties nil,
       :id "temp15"}]})


  (merge-linestrings easy)
  (merge-linestrings easy2))

(comment
  (def kink
    {:type "FeatureCollection",
     :features
     [{:type "Feature",
       :id "1"
       :geometry
       {:type "LineString",
        :coordinates
        [[25.74829599086967 62.60992094390775]
         [25.749438011346825 62.610070518117595]
         [25.749690556547957 62.609852625658625]
         [25.7484300396036 62.6100707537238]]}}]})


  (-> kink clj->js fix-kinks)
  (merge-line-strings kink)

  (turf/lineIntersect (clj->js kink) (clj->js kink))

  (def kinks
    {:type "FeatureCollection",
     :features
     [{:type     "Feature",
       :features [],
       :geometry
       {:type "LineString",
        :coordinates
        [[25.74809991565044 62.609440270660826]
         [25.7487270259911 62.60968963987866]
         [25.74834318779659 62.60938667187484]
         [25.74895985958416 62.60938494083431]
         [25.7480658612651 62.60963167475782]]}}]})

  (fix-kinks kinks)

  (turf/lineIntersect (clj->js kink) (clj->js kinks))

  (join-lines [1 2 3] [3 4 5])
  (join-lines [1 2 3] [4 5 3])
  (join-lines [1 2 3] [1 4 5])
  (join-lines [1 2 3] [4 5 1]))
