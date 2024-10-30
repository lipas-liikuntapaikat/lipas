(ns lipas.ui.map2.style
  (:require ["ol-new/style/Circle$default" :as Circle]
            ["ol-new/style/Fill$default" :as Fill]
            ["ol-new/style/Icon$default" :as Icon]
            ["ol-new/style/RegularShape$default" :as RegularShape]
            ["ol-new/style/Stroke$default" :as Stroke]
            ["ol-new/style/Style$default" :as Style]
            ;; ["ol-new/style/Text$default" :as Text]
            ;; TODO: closure lib is deprecated
            [goog.color :as gcolor]
            [goog.color.alpha :as gcolora]
            ; [lipas.data.activities :as activities]
            [lipas.data.styles :as styles]
            ; [lipas.ui.mui :as mui]
            [lipas.ui.svg :as svg]))

(defn ->marker-style [opts]
  (Style. #js {:image (Icon. #js {:src    (str "data:image/svg+xml;charset=utf-8,"
                                               (-> opts
                                                   svg/->marker-str
                                                   js/encodeURIComponent))
                                  :anchor #js [0.5 0.85]
                                  :offset #js [0 0]})}))

(def blue-marker-style (->marker-style {}))
; (def red-marker-style (->marker-style {:color mui/secondary}))
; (def default-stroke (Stroke. #js {:color "#3399CC" :width 3}))
; (def default-fill (Fill. #js {:color "rgba(255,255,0,0.4)"}))
(def hover-stroke (Stroke. #js {:color "rgba(255,0,0,0.4)"
                                :width 3.5}))
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
        fill         (Fill. #js {:color fill-color})
        stroke-alpha (case (:shape m)
                       "polygon" 0.6
                       0.9)

        stroke-width       (-> m :stroke :width)
        stroke-hover-width (* 2 stroke-width)
        stroke-color       (-> m :stroke :color (->rgba stroke-alpha))
        stroke-black       (Stroke. #js {:color "#00000"
                                         :width 1})

        stroke-planned (Stroke. #js {:color    "#3b3b3b"
                                     :lineDash #js [2 20]
                                     ; :lineDashOffset 1
                                     :width    (case (:shape m)
                                                 ("polygon" "linestring") 10
                                                 ("circle")               5
                                                 ("square")               4)})

        stroke-planning (Stroke. #js {:color    "#ee00ee"
                                      :lineDash #js [2 20]
                                      ; :lineDashOffset 1
                                      :width    (case (:shape m)
                                                  ("polygon" "linestring") 10
                                                  ("circle")               5
                                                  ("square")               4)})

        stroke         (Stroke. #js {:color    stroke-color
                                     :lineDash (when (or selected? hover?)
                                                 #js [2 8])
                                     :width    (if (or selected? hover?)
                                                 stroke-hover-width
                                                 stroke-width)})
        on-top?        (or selected? hover?)
        line-dash      (case (:shape m)
                         ("square") #js [1 4]
                         #js [2 5])
        line-cap       (case (:shape m)
                         ("square") "square"
                         "round")
        stroke-width   (case (:shape m)
                         ("square") 2
                         3)
        style          (Style. #js {:stroke stroke
                                    :fill   fill
                                    :zIndex (condp = (:shape m)
                                              "polygon"    (if on-top? 100 99)
                                              "linestring" (if on-top? 200 199)
                                              (if on-top? 300 299))
                                    :image
                                    (when-not (#{"polygon" "linestring"} (:shape m))
                                      (let [stroke (cond
                                                     planning? (Stroke. #js {:color    "#ee00ee"
                                                                             :width    stroke-width
                                                                             :lineCap  line-cap
                                                                             :lineDash line-dash})
                                                     planned?  (Stroke. #js {:color    "black"
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
                                          ("square") (RegularShape. #js {:fill   fill
                                                                         :stroke stroke
                                                                         :points 4
                                                                         :radius (inc (inc radius))
                                                                         :angle  (/ js/Math.PI 4)})
                                          ;; Default
                                          (Circle. #js {:radius radius
                                                        :fill   fill
                                                        :stroke stroke}))))})
        planned-stroke (Style. #js {:stroke stroke-planned})

        planning-stroke (Style. #js {:stroke stroke-planning})]

    (if (and selected? (:shape m))
      #js [style blue-marker-style]
      (cond
        planning? #js [style planning-stroke]
        planned?  #js [style planned-stroke]
        :else     #js [style]))))

(def styleset styles/symbols)

(def symbols
  (reduce (fn [m [k v]] (assoc m k (->symbol-style v))) {} styleset))

(def planned-symbols
  (reduce (fn [m [k v]] (assoc m k (->symbol-style v :planned true))) {} styleset))

(def planning-symbols
  (reduce (fn [m [k v]] (assoc m k (->symbol-style v :planning true))) {} styleset))

(defn shift-likely-overlapping!
  [type-code ^js style resolution f]
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
