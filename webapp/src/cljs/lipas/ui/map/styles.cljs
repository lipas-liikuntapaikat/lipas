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

(defn ->school-style [opts]
  (ol/style.Style.
   #js{:image
       (ol/style.Icon.
        #js{:src    (str "data:image/svg+xml;charset=utf-8,"
                         (-> opts
                             svg/->school-str
                             js/encodeURIComponent))
            :anchor #js[0.0 0.0]
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

(defn ->symbol-style
  [m & {hover? :hover selected? :selected planned? :planned planning? :planning}]
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

        stroke-planned (ol/style.Stroke.
                        #js{:color    "#3b3b3b"
                            :lineDash #js[2 20]
                                        ; :lineDashOffset 1
                            :width    (case (:shape m)
                                        ("polygon" "linestring") 10
                                        ("circle")               5
                                        ("square")               4)})

        stroke-planning (ol/style.Stroke.
                         #js{:color    "#ee00ee"
                             :lineDash #js[2 20]
                                        ; :lineDashOffset 1
                             :width    (case (:shape m)
                                         ("polygon" "linestring") 10
                                         ("circle")               5
                                         ("square")               4)})

        stroke         (ol/style.Stroke.
                        #js{:color    stroke-color
                            :lineDash (when (or selected? hover?)
                                        #js[2 8])
                            :width    (if (or selected? hover?)
                                        stroke-hover-width
                                        stroke-width)})
        on-top?        (or selected? hover?)
        line-dash      (case (:shape m) ("square") #js[1 4] #js[2 5])
        line-cap       (case (:shape m) ("square") "square" "round")
        stroke-width   (case (:shape m) ("square") 2 3)
        style          (ol/style.Style.
                        #js{:stroke stroke
                            :fill   fill
                            :zIndex (condp = (:shape m)
                                      "polygon"    (if on-top? 100 99)
                                      "linestring" (if on-top? 200 199)
                                      (if on-top? 300 299))
                            :image
                            (when-not (#{"polygon" "linestring"} (:shape m))
                              (let [stroke (cond
                                             planning? (ol/style.Stroke.
                                                        #js{:color    "#ee00ee"
                                                            :width    stroke-width
                                                            :lineCap  line-cap
                                                            :lineDash line-dash})
                                             planned?  (ol/style.Stroke.
                                                        #js{:color    "black"
                                                            :width    stroke-width
                                                            :lineCap  line-cap
                                                            :lineDash line-dash})
                                             hover?    hover-stroke
                                             :else     stroke-black)
                                    radius (cond
                                             hover?    8
                                             planning? 7
                                             planned?  7
                                             :else     7)]
                                (case (:shape m)
                                  ("square") (ol/style.RegularShape.
                                              #js{:fill   fill
                                                  :stroke stroke
                                                  :points 4
                                                  :radius (inc (inc radius))
                                                  :angle  (/ js/Math.PI 4)})
                                  ;; Default
                                  (ol/style.Circle.
                                   #js{:radius radius
                                       :fill   fill
                                       :stroke stroke}))))})
        planned-stroke (ol/style.Style.
                        #js{:stroke stroke-planned})

        planning-stroke (ol/style.Style.
                         #js{:stroke stroke-planning})]

    (if (and selected? (:shape m))
      #js[style blue-marker-style]
      (cond
        planning? #js[style planning-stroke]
        planned?  #js[style planned-stroke]
        :else     #js[style]))))

(def styleset styles/symbols)

(def symbols
  (reduce (fn [m [k v]] (assoc m k (->symbol-style v))) {} styleset))

(def hover-symbols
  (reduce (fn [m [k v]] (assoc m k (->symbol-style v :hover true))) {} styleset))

(def selected-symbols
  (reduce (fn [m [k v]] (assoc m k (->symbol-style v :selected true))) {} styleset))

(def planned-symbols
  (reduce (fn [m [k v]] (assoc m k (->symbol-style v :planned true))) {} styleset))

(def planning-symbols
  (reduce (fn [m [k v]] (assoc m k (->symbol-style v :planning true))) {} styleset))

(defn shift-likely-overlapping!
  [type-code style resolution f]
  (when (#{4402 4440} type-code)
    (let [delta (* resolution 4)
          copy  (-> f .getGeometry .clone)]
      (doto style
        (.setGeometry (doto copy
                        (.translate delta delta)))))))

(defn feature-style [f resolution]
  (let [type-code (.get f "type-code")
        status    (.get f "status")
        style     (condp = status
                    "planning" (get planning-symbols type-code)
                    "planned"  (get planned-symbols type-code)
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

(def population-grid-radius
  "Population grid is 250x250m"
  (/ (* 250 (Math/sqrt 2)) 2))

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

(def analysis-style
  (ol/style.Style.
   #js{:stroke
       (ol/style.Stroke.
        #js{:width 3 :color "blue" :lineDash #js[2 10]})}))

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

(defn population-style2
  [f _resolution]
  (let [n     (.get f "vaesto")
        color (.get f "color")]
    (ol/style.Style.
     #js{:stroke
         (ol/style.Stroke.
          #js{:width 3 :color color})
         :fill (ol/style.Fill. #js{:color color})
         :image
         (ol/style.Circle.
          #js{:radius (min 7 (* 0.01 (js/Math.abs n)))
              :fill   (ol/style.Fill. #js{:color color})
              :stroke (ol/style.Stroke. #js{:color color :width 3})})})))

(defn population-hover-style2
  [f _resolution]
  (let [n (.get f "vaesto")]
    (ol/style.Style.
     #js{:stroke
         (ol/style.Stroke.
          #js{:width 3 :color "blue"})
         :fill default-fill
         :image
         (ol/style.Circle.
          #js{:radius (min 10 (* 0.1 (js/Math.abs n)))
              :fill (ol/style.Fill. #js{:color "rgba(255,255,0,0.85)"})
              :stroke hover-stroke})})))

(defn density [n]
  (cond
    (nil? n) 0.1
    (> 10 n) 0.1
    (> 25 n) 0.2
    (> 50 n) 0.3
    (> 100 n) 0.4
    (> 250 n) 0.5
    :else 0.7))

(defn population-style3
  [f resolution]
  (let [n     (.get f "vaesto")
        color (.get f "color")
        dens  (density n)]
    (ol/style.Style.
     #js{:stroke
         (ol/style.Stroke.
          #js{:width 3 :color color})
         :fill (ol/style.Fill. #js{:color color})
         :image
         (ol/style.RegularShape.
          #js{:radius (/ population-grid-radius resolution)
              :points 4
              :angle  (/ js/Math.PI 4)
              :fill   (ol/style.Fill. #js{:color (->rgba color dens)})
              :stroke (ol/style.Stroke. #js{:color color :width 2})})})))

(defn population-hover-style3
  [f resolution]
  (let [n     (.get f "vaesto")
        color (.get f "color")
        dens (density n)]
    (ol/style.Style.
     #js{:stroke
         (ol/style.Stroke.
          #js{:width 3 :color color})
         :fill (ol/style.Fill. #js{:color color})
         :image
         (ol/style.RegularShape.
          #js{:radius (/ population-grid-radius resolution)
              :points 4
              :angle  (/ js/Math.PI 4)
              :fill   (ol/style.Fill. #js{:color (->rgba color dens)})
              :stroke hover-stroke})})))


(def school-colors
  {"Peruskoulut"
   {:name "Peruskoulut" :color "#C17B0D"}
   "Peruskouluasteen erityiskoulut"
   {:name "Peruskouluasteen erityiskoulut" :color "#C2923E"}
   "Lukiot"
   {:name "Lukiot" :color "#4A69C2"}
   "Perus- ja lukioasteen koulut"
   {:name "Perus- ja lukioasteen koulut" :color "#0D3BC1"}
   "Varhaiskasvatusyksikkö"
   {:name "Varhaiskasvatusyksikkö" :color "#ff40ff"}})

(defn school-style [f resolution]
  (let [color (:color (get school-colors (.get f "type")))]
    (->school-style {:color color :width 24 :height 24})))

(defn school-hover-style [f resolution]
  (let [color (:color (get school-colors (.get f "type")))]
    (->school-style {:color color :width 24 :height 24 :hover? true})))

(def diversity-base-color "#9D191A")
(def transparent "rgba(0,0,0,0.25)")

(def diversity-stroke "#0D3BC1")
(def diversity-hover-stroke (-> diversity-stroke
                                gcolor/hexToRgb
                                (gcolor/lighten 0.2)
                                gcolor/rgbArrayToHex))

(def diversity-colors
  (into {}
        (for [n (range 30)]
          [(- (dec 30) n)
           (-> diversity-base-color
               gcolor/hexToRgb
               (gcolor/lighten (/ n 30))
               gcolor/rgbArrayToHex)])))

(def diversity-colors-alpha
  (into {}
        (for [n (range 30)]
          [(- (dec 30) n)
           (-> diversity-base-color
               gcolor/hexToRgb
               (gcolor/lighten (/ n 30))
               gcolor/rgbArrayToHex
               (->rgba 0.7))])))

(comment
  (sort-by first diversity-colors)
  (reverse (range 30)))

(defn diversity-grid-style
  [f resolution]
  (let [diversity-idx (.get f "diversity_idx")
        color (get diversity-colors diversity-idx diversity-base-color)]
    (ol/style.Style.
     #js{:stroke
         (ol/style.Stroke.
          #js{:width 3 :color color})
         :fill (ol/style.Fill. #js{:color color})
         :image
         (ol/style.RegularShape.
          #js{:radius (/ population-grid-radius resolution)
              :points 4
              :angle  (/ js/Math.PI 4)
              :fill   (ol/style.Fill. #js{:color color})
              :stroke (ol/style.Stroke. #js{:color "blue" :width 1})})})))

(defn diversity-grid-hover-style [f resolution]
  (let [diversity-idx (.get f "diversity_idx")
        color (get diversity-colors diversity-idx diversity-base-color)]
    (ol/style.Style.
     #js{:stroke
         (ol/style.Stroke.
          #js{:width 3 :color color})
         :fill (ol/style.Fill. #js{:color color})
         :image
         (ol/style.RegularShape.
          #js{:radius (/ population-grid-radius resolution)
              :points 4
              :angle  (/ js/Math.PI 4)
              :fill   (ol/style.Fill. #js{:color color})
              :stroke (ol/style.Stroke. #js{:color "blue" :width 2})})})))

(defn diversity-area-style [f _resolution]
  (let [diversity-idx (js/Math.round (.get f "population-weighted-mean"))
        fill-color (get diversity-colors-alpha diversity-idx transparent)]
    (ol/style.Style.
     #js{:stroke
         (ol/style.Stroke.
          #js{:width 3 :color "#0D3BC1"})
         :fill (ol/style.Fill. #js{:color fill-color})})))

(defn diversity-area-hover-style [f _resolution]
  (let [diversity-idx (js/Math.round (.get f "population-weighted-mean"))
        fill-color (get diversity-colors-alpha diversity-idx transparent)]
    (ol/style.Style.
     #js{:stroke
         (ol/style.Stroke.
          #js{:width 5 :color diversity-hover-stroke})
         :fill (ol/style.Fill. #js{:color fill-color})})))
