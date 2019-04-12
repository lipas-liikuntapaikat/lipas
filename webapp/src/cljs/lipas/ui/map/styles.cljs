(ns lipas.ui.map.styles
  (:require
   ["ol"]
   [goog.color :as gcolor]
   [goog.color.alpha :as gcolora]
   [lipas.ui.mui :as mui]
   [lipas.ui.svg :as svg]
   [lipas.data.styles :as styles]))

(defn ->marker-style [opts]
  (ol.style.Style.
   #js{:image
       (ol.style.Icon.
        #js{:src    (str "data:image/svg+xml;charset=utf-8,"
                         (-> opts
                             svg/->marker-str
                             js/encodeURIComponent))
            :anchor #js[0.5 0.85]
            :offset #js[0 0]})}))

(def blue-marker-style (->marker-style {}))
(def red-marker-style (->marker-style {:color mui/secondary}))
(def default-stroke (ol.style.Stroke. #js{:color "#3399CC" :width 3}))
(def default-fill (ol.style.Fill. #js{:color "rgba(255,255,0,0.4)"}))
(def hover-stroke (ol.style.Stroke. #js{:color "rgba(255,0,0,0.4)" :width 3.5}))
(def hover-fill (ol.style.Fill. #js{:color "rgba(255,0,0,0.4)"}))

;; Draw circles to all LineString and Polygon vertices
(def vertices-style
  (ol.style.Style.
   #js{:image
       (ol.style.Circle.
        #js{:radius 5
            :stroke (ol.style.Stroke.
                     #js{:color mui/primary})
            :fill   (ol.style.Fill.
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
             (ol.geom.MultiPoint. coords))))}))

(def edit-style
  (ol.style.Style.
   #js{:stroke
       (ol.style.Stroke.
        #js{:width 3 :color "blue"})
       :fill default-fill
       :image
       (ol.style.Circle.
        #js{:radius 5
            :fill   default-fill
            :stroke default-stroke})}))

(def invalid-style
  (ol.style.Style.
   #js{:stroke
       (ol.style.Stroke.
        #js{:width 3
            :color "red"})
       :fill default-fill
       :image
       (ol.style.Circle.
        #js{:radius 5
            :fill   default-fill
            :stroke default-stroke})}))

(def default-style
  (ol.style.Style.
   #js{:stroke default-stroke
       :fill default-fill
       :image
       (ol.style.Circle.
        #js{:radius 5
            :fill   default-fill
            :stroke default-stroke})}))

(def hover-style
  (ol.style.Style.
   #js{:stroke hover-stroke
       :fill   default-fill
       :image
       (ol.style.Circle.
        #js{:radius 7
            :fill   default-fill
            :stroke hover-stroke})}))

(def editing-hover-style
  (ol.style.Style.
   #js{:stroke hover-stroke
       :fill   (ol.style.Fill. #js{:color "rgba(255,255,0,0.2)"})
       :image
       (ol.style.Circle.
        #js{:radius 7
            :fill   default-fill
            :stroke hover-stroke})}))

(def hover-styles #js[hover-style blue-marker-style])

(defn ->rgba [hex alpha]
  (when (and hex alpha)
    (let [rgb  (gcolor/hexToRgb hex)
          rgba (doto rgb (.push alpha))]
      (gcolora/rgbaArrayToRgbaStyle rgba))))

(defn ->symbol-style [m & {hover? :hover selected? :selected}]
  (let [fill-alpha         (case (:shape m)
                             "polygon" (if hover? 0.3 0.2)
                             0.85)
        fill-color         (-> m :fill :color (->rgba fill-alpha))
        fill               (ol.style.Fill. #js{:color fill-color})
        stroke-alpha       (case (:shape m)
                             "polygon" 0.6
                             0.9)

        stroke-width       (-> m :stroke :width)
        stroke-hover-width (* 2 stroke-width)
        stroke-color       (-> m :stroke :color (->rgba stroke-alpha))
        stroke-black       (ol.style.Stroke. #js{:color "#00000" :width 1})
        stroke             (ol.style.Stroke. #js{:color    stroke-color
                                                 :lineDash (when (or selected? hover?)
                                                             #js[2 8])
                                                 :width    (if (or selected? hover?)
                                                             stroke-hover-width
                                                             stroke-width)})
        style              (ol.style.Style.
                            #js{:stroke stroke
                                :fill   fill
                                :zIndex (if selected? 100 99)
                                :image
                                (when-not (#{"polygon" "linestring"} (:shape m))
                                  (ol.style.Circle.
                                   #js{:radius (if hover? 8 7)
                                       :fill   fill
                                       :stroke (if hover? hover-stroke stroke-black)}))})]

    (if (and selected? (:shape m))
      #js[style red-marker-style]
      style)))

(def styleset styles/adapted-temp-symbols)

(def symbols
  (reduce (fn [m [k v]] (assoc m k (->symbol-style v))) {} styleset))

(def hover-symbols
  (reduce (fn [m [k v]] (assoc m k (->symbol-style v :hover true))) {} styleset))

(def selected-symbols
  (reduce (fn [m [k v]] (assoc m k (->symbol-style v :selected true))) {} styleset))

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
        style     (get symbols type-code)]
    (shift-likely-overlapping! type-code style resolution f)
    style))

(defn feature-style-hover [f resolution]
  (let [type-code (.get f "type-code")
        style     (get hover-symbols type-code)]
    (shift-likely-overlapping! type-code style resolution f)
    style))

(defn feature-style-selected [f resolution]
  (let [type-code (.get f "type-code")
        style     (get selected-symbols type-code)]
    (shift-likely-overlapping! type-code (first style) resolution f)
    style))
