(ns lipas.data.styles)

(defn ->polygon-style
  ([fill-color stroke-color]
   (->polygon-style fill-color stroke-color 0.26))
  ([fill-color stroke-color stroke-width]
   {:shape "polygon"
    :fill
    {:color fill-color}
    :stroke
    {:color stroke-color
     :width stroke-width}}))

(defn ->stroke
  ([color]
   (->stroke color 2.0 "round" "round" [5 2]))
  ([color width linejoin linecap linedash]
   {:color     color
    :width     width
    :line-cap  linecap
    :line-join linejoin
    :line-dash linedash}))

(defn ->linestring-style
  [color1 color2]
  {:shape "linestring"
   :stroke
   (->stroke color1)})

(defn ->point-style
  ([shape fill-color]
   (->point-style shape fill-color "#000000" 9))
  ([shape fill-color stroke-color]
   (->point-style shape fill-color stroke-color 9))
  ([shape fill-color stroke-color radius]
   {:shape shape
    :radius radius
    :fill
    {:color fill-color}
    :stroke
    {:color stroke-color}}))

(def all
  {
   ;; Areas ;;
   101  (->polygon-style "#00ffe1" "#000000")
   102  (->polygon-style "#91fe00" "#000000")
   103  (->polygon-style "#00fd0e" "#000000")
   104  (->polygon-style "#75fd83" "#000000")
   106  (->polygon-style "#00f9b8" "#000000")
   107  (->polygon-style "#12bd00" "#000000")
   108  (->polygon-style "#99d58c" "#000000")
   109  (->polygon-style "#41b358" "#000000")
   111  (->polygon-style "#00d849" "#000000")
   112  (->polygon-style "#b7da00" "#000000")
   110  (->polygon-style "#451691" "#000000")
   1110 (->polygon-style "#7d7d7d" "#000000")
   4510 (->polygon-style "#32b03a" "#000000")
   4520 (->polygon-style "#65ceed" "#000000")
   4530 (->polygon-style "#becb28" "#000000")

   ;; Routes ;;
   1550 (->linestring-style "#ffffff" "#000000")
   4401 (->linestring-style "#ee4548" "#000000")
   4402 (->linestring-style "#00ffff" "#000000")
   4403 (->linestring-style "#ffff00" "#000000")
   4404 (->linestring-style "#489f0d" "#000000")
   4405 (->linestring-style "#397e0b" "#000000")
   4411 (->linestring-style "#e588fc" "#000000")
   4412 (->linestring-style "#b770ff" "#000000")
   4421 (->linestring-style "#ba1693" "#000000")
   4422 (->linestring-style "#ba1693" "#000000")
   4430 (->linestring-style "#ff5500" "#000000")
   4440 (->linestring-style "#fdc453" "#000000")
   4451 (->linestring-style "#0593ff" "#000000")
   4452 (->linestring-style "#0055ff" "#000000")

   ;; Points ;;
   201 (->point-style "circle" "#a2a3a4" "#0055ff")
   202 (->point-style "triangle" "#397e0b" "#004800")
   203 (->point-style "triangle" "#a2a3a4" "#0055ff")
   204 (->point-style "circle" "#397e0b" "#004800")
   205 (->point-style "triangle" "#a2a3a4" "#0055ff")
   206 (->point-style "triangle" "#397e0b" "#004800")
   207 (->point-style "triangle" "#ffffff" "#000000")
   ;;207 {:shape "svg" :uri ""} ;; TODO

   301 (->point-style "square" "#397e0b" "#004800")
   302 (->point-style "triangle" "#397e0b" "#004800")
   304 (->point-style "triangle" "#c4b7a7" "#004800")

   1120 (->point-style "square" "#707172" "#c7a481")
   1130 (->point-style "circle" "#707172")
   1140 (->point-style "triangle" "#707172")
   1150 (->point-style "square" "#707172")
   1160 (->point-style "triangle" "#707172")
   1170 (->point-style "circle" "#707172")
   1180 (->point-style "triangle" "#8fed75")
   1210 (->point-style "square" "#d93a09")
   1220 (->point-style "circle" "#d93a09")
   1310 (->point-style "square" "#ff860c")
   1320 (->point-style "square" "#aaaaff" "#707172")
   1330 (->point-style "square" "#e5d788" "#707172")
   1340 (->point-style "square" "#cd9b62")
   1350 (->point-style "circle" "#00aa00" "#ff5500")
   1360 (->point-style "circle" "#c7a481")
   1370 (->point-style "square" "#d5ff01")
   1380 (->point-style "square" "#707172" "#707172")
   1510 (->point-style "square" "#94e8ff")
   1520 (->point-style "square" "#94e8ff" "#707172")
   1530 (->point-style "circle" "#94e8ff")
   1540 (->point-style "triangle" "#94e8ff")
   1560 (->point-style "triangle" "#94e8ff")
   1610 (->point-style "circle" "#8fed75")
   1620 (->point-style "circle" "#8fed75")
   1630 (->point-style "square" "#8fed75")
   1640 (->point-style "square" "#8fed75")

   2110 (->point-style "square" "#be55ff")
   2120 (->point-style "square" "#e8bcf2")
   2130 (->point-style "square" "#934de8")
   2140 (->point-style "square" "#6635a2")
   2150 (->point-style "square" "#f0ffc6")
   2210 (->point-style "square" "#af8654")
   2220 (->point-style "square" "#af8654")
   2230 (->point-style "square" "#00aa00" "#ff5500")
   2240 (->point-style "square" "#8181bd")
   2250 (->point-style "square" "#707172")
   2260 (->point-style "square" "#c47577")
   2270 (->point-style "square" "#81d5aa")
   2280 (->point-style "square" "#d5ff01")
   2290 (->point-style "square" "#769e86")
   2310 (->point-style "circle" "#d93a09")
   2320 (->point-style "circle" "#aaaaff")
   2330 (->point-style "circle" "#d5ff01")
   2340 (->point-style "circle" "#8d4600")
   2350 (->point-style "circle" "#ff77ab")
   2360 (->point-style "square" "#ffa60c" "#aa0000")
   2370 (->point-style "triangle" "#97aa79")
   2380 (->point-style "triangle" "#c0c2c3")
   2510 (->point-style "square" "#94e8ff")
   2520 (->point-style "square" "#94e8ff")
   2530 (->point-style "triangle" "#94e8ff")
   2610 (->point-style "square" "#aa007f")
   2710 (->point-style "square" "#55007f")

   3110 (->point-style "square" "#2990ff")
   3120 (->point-style "circle" "#2990ff")
   3130 (->point-style "square" "#2990ff")
   3210 (->point-style "triangle" "#2990ff")
   3220 (->point-style "triangle" "#2990ff")
   3230 (->point-style "triangle" "#2990ff")
   3240 (->point-style "star" "#2990ff")

   4110 (->point-style "star" "#aaffff")
   4210 (->point-style "star" "#d787ff")
   4220 (->point-style "star" "#8f9bbc")
   4310 (->point-style "triangle" "#9898e4")
   4320 (->point-style "triangle" "#9898e4")
   4620 (->point-style "triangle" "#ee0000")
   4630 (->point-style "triangle" "#ee0000")
   4640 (->point-style "triangle" "#ee0000")
   4710 (->point-style "triangle" "#97aa79")
   4720 (->point-style "triangle" "#97aa79")

   5110 (->point-style "circle" "#00007f" "#00557f")
   5120 (->point-style "triangle" "#00007f" "#00557f")
   5130 (->point-style "triangle" "#00007f" "#00557f")
   5140 (->point-style "circle" "#00007f" "#00557f")
   5150 (->point-style "triangle" "#00007f" "#00557f")
   5160 (->point-style "square" "#00007f" "#00557f")
   5210 (->point-style "circle" "#ffffff" "#0055ff")

   5310 (->point-style "square" "#b8c0bd" "#aa00ff")
   5320 (->point-style "circle" "#b8c0bd" "#aa00ff")
   5330 (->point-style "circle" "#b8c0bd" "#aa00ff")
   5340 (->point-style "triangle" "#b8c0bd" "#aa00ff")
   5350 (->point-style "triangle" "#b8c0bd" "#aa00ff")
   5360 (->point-style "triangle" "#b8c0bd" "#aa00ff")
   5370 (->point-style "star" "#b8c0bd" "#aa00ff")

   4230 (->point-style "star" "#ffdd1c")
   4240 (->point-style "star" "#55007f")
   4610 (->point-style "triangle" "#ee0000")
   4810 (->point-style "square" "#ffa60c" "#aa0000")
   4820 (->point-style "square" "#ffa60c" "#aa0000")
   4830 (->point-style "triangle" "#ffa60c" "#aa0000")
   4840 (->point-style "triangle" "#ffa60c" "#aa0000")

   6110 (->point-style "square" "#664a33" "#ee0000")
   6120 (->point-style "square" "#664a33" "#ee0000")
   6130 (->point-style "triangle" "#664a33" "#ee0000")
   6140 (->point-style "circle" "#725339" "#ee0000")
   6210 (->point-style "square" "#664a33" "#456d3a")
   6220 (->point-style "square" "#664a33" "#456d3a")
   7000 (->point-style "triangle" "#f8ffa0")})
