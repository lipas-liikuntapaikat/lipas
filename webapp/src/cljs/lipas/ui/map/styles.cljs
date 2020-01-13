(ns lipas.ui.map.styles
  (:require
   ["ol" :as ol]
   [goog.color :as gcolor]
   [goog.color.alpha :as gcolora]
   [lipas.ui.mui :as mui]
   [lipas.ui.svg :as svg]
   [lipas.data.styles :as styles]))

(defn ->marker-style [opts]
  (ol/style.Style.
   #js{:image
       (ol/style.Icon.
        #js{:src    (str "data:image/svg+xml;charset=utf-8,"
                         (-> opts
                             svg/->marker-str
                             js/encodeURIComponent))
            :anchor #js[0.5 0.85]
            :offset #js[0 0]})}))

(def blue-marker-style (->marker-style {}))
(def red-marker-style (->marker-style {:color mui/secondary}))
(def default-stroke (ol/style.Stroke. #js{:color "#3399CC" :width 3}))
(def default-fill (ol/style.Fill. #js{:color "rgba(255,255,0,0.4)"}))
(def hover-stroke (ol/style.Stroke. #js{:color "rgba(255,0,0,0.4)" :width 3.5}))
(def hover-fill (ol/style.Fill. #js{:color "rgba(255,0,0,0.4)"}))

;; Draw circles to all LineString and Polygon vertices
(def vertices-style
  (ol/style.Style.
   #js{:image
       (ol/style.Circle.
        #js{:radius 5
            :stroke (ol/style.Stroke.
                     #js{:color mui/primary})
            :fill   (ol/style.Fill.
                     #js{:color mui/secondary2})})
       :geometry
       (fn [f]
         (let [geom-type (-> f .getGeometry .getType)
               coords    (case geom-type
                           "Polygon"    (-> f
                                            .getGeometry
                                            .getCoordinates
                                            js->clj
                                            (as-> $ (mapcat identity $))
                                            clj->js)
                           "LineString" (-> f .getGeometry .getCoordinates)
                           nil)]
           (when coords
             (ol/geom.MultiPoint. coords))))}))

(def edit-style
  (ol/style.Style.
   #js{:stroke
       (ol/style.Stroke.
        #js{:width 3 :color "blue"})
       :fill default-fill
       :image
       (ol/style.Circle.
        #js{:radius 7
            :fill (ol/style.Fill. #js{:color "rgba(255,255,0,0.85)"})
            :stroke default-stroke})}))

(def invalid-style
  (ol/style.Style.
   #js{:stroke
       (ol/style.Stroke.
        #js{:width 3
            :color "red"})
       :fill default-fill
       :image
       (ol/style.Circle.
        #js{:radius 5
            :fill   default-fill
            :stroke default-stroke})}))

(def default-style
  (ol/style.Style.
   #js{:stroke default-stroke
       :fill default-fill
       :image
       (ol/style.Circle.
        #js{:radius 5
            :fill   default-fill
            :stroke default-stroke})}))

(def hover-style
  (ol/style.Style.
   #js{:stroke hover-stroke
       :fill   default-fill
       :image
       (ol/style.Circle.
        #js{:radius 7
            :fill   default-fill
            :stroke hover-stroke})}))

(def editing-hover-style
  (ol/style.Style.
   #js{:stroke hover-stroke
       :fill   (ol/style.Fill. #js{:color "rgba(255,255,0,0.2)"})
       :image
       (ol/style.Circle.
        #js{:radius 7
            :fill   default-fill
            :stroke hover-stroke})}))

(def hover-styles #js[hover-style blue-marker-style])

(defn ->rgba [hex alpha]
  (when (and hex alpha)
    (let [rgb  (gcolor/hexToRgb hex)
          rgba (doto rgb (.push alpha))]
      (gcolora/rgbaArrayToRgbaStyle rgba))))

(defn ->symbol-style [m & {hover? :hover selected? :selected planned? :planned}]
  (let [fill-alpha   (case (:shape m)
                       "polygon" (if hover? 0.3 0.2)
                       0.85)
        fill-color   (-> m :fill :color (->rgba fill-alpha))
        fill         (ol/style.Fill. #js{:color fill-color})
        stroke-alpha (case (:shape m)
                       "polygon" 0.6
                       0.9)

        stroke-width       (-> m :stroke :width)
        stroke-hover-width (* 2 stroke-width)
        stroke-color       (-> m :stroke :color (->rgba stroke-alpha))
        stroke-black       (ol/style.Stroke. #js{:color "#00000" :width 1})
        stroke-planned     (ol/style.Stroke.
                            #js{:color "#5cd8fa"
                                :width (if (#{"polygon" "linestring"} (:shape m))
                                         10
                                         5)})
        stroke             (ol/style.Stroke.
                            #js{:color    stroke-color
                                :lineDash (when (or selected? hover?)
                                            #js[2 8])
                                :width    (if (or selected? hover?)
                                            stroke-hover-width
                                            stroke-width)})
        on-top?            (or selected? hover?)
        style              (ol/style.Style.
                            #js{:stroke stroke
                                :fill   fill
                                :zIndex (condp = (:shape m)
                                          "polygon"    (if on-top? 100 99)
                                          "linestring" (if on-top? 200 199)
                                          (if on-top? 300 299))
                                :image
                                (when-not (#{"polygon" "linestring"} (:shape m))
                                  (ol/style.Circle.
                                   #js{:radius (cond
                                                 hover?   8
                                                 planned? 10
                                                 :else    7)
                                       :fill   fill
                                       :stroke
                                       (cond
                                         planned? stroke-planned
                                         hover?   hover-stroke
                                         :else    stroke-black)}))})
        planned-stroke     (ol/style.Style.
                            #js{:stroke stroke-planned})]

    (if (and selected? (:shape m))
      #js[style blue-marker-style]
      (if planned?
        #js[style planned-stroke]
        #js[style]))))

(def styleset styles/adapted-temp-symbols)

(def symbols
  (reduce (fn [m [k v]] (assoc m k (->symbol-style v))) {} styleset))

(def hover-symbols
  (reduce (fn [m [k v]] (assoc m k (->symbol-style v :hover true))) {} styleset))

(def selected-symbols
  (reduce (fn [m [k v]] (assoc m k (->symbol-style v :selected true))) {} styleset))

(def planned-symbols
  (reduce (fn [m [k v]] (assoc m k (->symbol-style v :planned true))) {} styleset))

(defn shift-likely-overlapping!
  [type-code style resolution f]
  (when (#{4402} type-code)
    (let [delta (* resolution 4)
          copy  (-> f .getGeometry .clone)]
      (doto style
        (.setGeometry (doto copy
                        (.translate delta delta)))))))

(defn feature-style [f resolution]
  (let [type-code (.get f "type-code")
        status    (.get f "status")
        style     (if (= "planned" status)
                    (get planned-symbols type-code)
                    (get symbols type-code))]
    (shift-likely-overlapping! type-code (first style) resolution f)
    style))

(defn feature-style-hover [f resolution]
  (let [type-code (.get f "type-code")
        style     (get hover-symbols type-code)]
    (shift-likely-overlapping! type-code (first style) resolution f)
    style))

(defn feature-style-selected [f resolution]
  (let [type-code (.get f "type-code")
        style     (get selected-symbols type-code)]
    (shift-likely-overlapping! type-code (first style) resolution f)
    style))

;; Color scheme from Tilastokeskus
;; http://www.stat.fi/org/avoindata/paikkatietoaineistot/vaestoruutuaineisto_1km.html
(def population-colors
  ["rgba(255,237,169,0.5)"
   "rgba(255,206,123,0.5)"
   "rgba(247,149,72,0.5)"
   "rgba(243,112,67,0.5)"
   "rgba(237,26,59,0.5)"
   "rgba(139,66,102,0.5)"])

(defn make-population-styles
  ([] (make-population-styles false "LawnGreen"))
  ([hover? stroke-color]
   (->> population-colors
        (map-indexed
         (fn [idx color]
           [idx (ol/style.Style.
                 #js{:stroke (if hover?
                               (ol/style.Stroke. #js{:color stroke-color :width 5})
                               (ol/style.Stroke. #js{:color "black" :width 2}))
                     :fill   (ol/style.Fill. #js{:color color})})]))
        (into {}))))

(def population-styles
  (make-population-styles))

(def population-hover-styles
  (make-population-styles :hover "LawnGreen"))

(def population-zone1
  (make-population-styles :hover "#008000"))

(def population-zone2
  (make-population-styles :hover "#2db92d"))

(def population-zone3
  (make-population-styles :hover "#73e600"))

(defn population-style
  [f _resolution]
  (let [n (.get f "vaesto")]
    (condp > n
      5    (population-styles 0)
      20   (population-styles 1)
      50   (population-styles 2)
      500  (population-styles 3)
      5000 (population-styles 4)
      (population-styles 5))))

(defn population-hover-style
  [f _resolution]
  (let [n (.get f "vaesto")]
    (condp > n
      5    (population-hover-styles 0)
      20   (population-hover-styles 1)
      50   (population-hover-styles 2)
      500  (population-hover-styles 3)
      5000 (population-hover-styles 4)
      (population-styles 5))))

(defn population-zone1-fn
  [f _resolution]
  (let [n (.get f "vaesto")]
    (condp > n
      5    (population-zone1 0)
      20   (population-zone1 1)
      50   (population-zone1 2)
      500  (population-zone1 3)
      5000 (population-zone1 4)
      (population-zone1 5))))

(defn population-zone2-fn
  [f _resolution]
  (let [n (.get f "vaesto")]
    (condp > n
      5    (population-zone2 0)
      20   (population-zone2 1)
      50   (population-zone2 2)
      500  (population-zone2 3)
      5000 (population-zone2 4)
      (population-zone2 5))))

(defn population-zone3-fn
  [f _resolution]
  (let [n (.get f "vaesto")]
    (condp > n
      5    (population-zone3 0)
      20   (population-zone3 1)
      50   (population-zone3 2)
      500  (population-zone3 3)
      5000 (population-zone3 4)
      (population-zone3 5))))
