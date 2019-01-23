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

(def temp-symbols "Temporary definitions for admin tool."
  {1530 {:type-code 1530, :fill "#59daff", :stroke "#000000"},
   1520 {:type-code 1520, :fill "#a6ebff", :stroke "#707172"},
   2320 {:type-code 2320, :fill "#aaaaff", :stroke "#000000"},
   6130 {:type-code 6130, :fill "#98713d", :stroke "#ee0000"},
   6210 {:type-code 6210, :fill "#d98e20", :stroke "#456d3a"},
   1370 {:type-code 1370, :fill "#e2ff1a", :stroke "#000000"},
   1360 {:type-code 1360, :fill "#c7a481", :stroke "#000000"},
   110  {:type-code 110, :fill "#739e38", :stroke "#000000"},
   2360 {:type-code 2360, :fill "#ffa60c", :stroke "#aa0000"},
   5310 {:type-code 5310, :fill "#7c8b8a", :stroke "#aa00ff"},
   1560 {:type-code 1560, :fill "#0095bf", :stroke "#000000"},
   205  {:type-code 205, :fill "#80c6b6", :stroke "#0055ff"},
   2150 {:type-code 2150, :fill "#fdebb5", :stroke "#000000"},
   2210 {:type-code 2210, :fill "#af8654", :stroke "#000000"},
   101  {:type-code 101, :fill "#fccc69", :stroke "#000000"},
   102  {:type-code 102, :fill "#fba626", :stroke "#000000"},
   7000 {:type-code 7000, :fill "#f8ffa0", :stroke "#000000"},
   1110 {:type-code 1110, :fill "#ffac11", :stroke "#000000"},
   6220 {:type-code 6220, :fill "#cc791e", :stroke "#456d3a"},
   4530 {:type-code 4530, :fill "#df7000", :stroke "#000000"},
   4720 {:type-code 4720, :fill "#a6a77c", :stroke "#000000"},
   1330 {:type-code 1330, :fill "#e5d788", :stroke "#707172"},
   206  {:type-code 206, :fill "#87d215", :stroke "#004800"},
   4830 {:type-code 4830, :fill "#fece0e", :stroke "#aa0000"},
   1180 {:type-code 1180, :fill "#8fed75", :stroke "#000000"},
   4422 {:type-code 4422, :fill "", :stroke "#c9189d"},
   4430 {:type-code 4430, :fill "", :stroke "#f97c00"},
   204  {:type-code 204, :fill "#73d28b", :stroke "#004800"},
   106  {:type-code 106, :fill "#50e76a", :stroke "#000000"},
   4610 {:type-code 4610, :fill "#d20000", :stroke "#000000"},
   2610 {:type-code 2610, :fill "#aa007f", :stroke "#000000"},
   2110 {:type-code 2110, :fill "#be55ff", :stroke "#000000"},
   3120 {:type-code 3120, :fill "#85b6fe", :stroke "#000000"},
   104  {:type-code 104, :fill "#39db3e", :stroke "#000000"},
   2330 {:type-code 2330, :fill "#b3d801", :stroke "#000000"},
   2280 {:type-code 2280, :fill "#d8dc23", :stroke "#000000"},
   6140 {:type-code 6140, :fill "#9b7986", :stroke "#ee0000"},
   2140 {:type-code 2140, :fill "#6635a2", :stroke "#000000"},
   4220 {:type-code 4220, :fill "#8f9bbc", :stroke "#000000"},
   2230 {:type-code 2230, :fill "#00aa00", :stroke "#ff5500"},
   1350 {:type-code 1350, :fill "#00aa00", :stroke "#ff5500"},
   4840 {:type-code 4840, :fill "#dcc210", :stroke "#aa0000"},
   1510 {:type-code 1510, :fill "#8ffadd", :stroke "#000000"},
   5350 {:type-code 5350, :fill "#5a6561", :stroke "#aa00ff"},
   4440 {:type-code 4440, :fill "", :stroke "#5bc8b8"},
   2520 {:type-code 2520, :fill "#5edbff", :stroke "#000000"},
   4710 {:type-code 4710, :fill "#7d8860", :stroke "#000000"},
   304  {:type-code 304, :fill "#c4b7a7", :stroke "#004800"},
   4412 {:type-code 4412, :fill "", :stroke "#b366ff"},
   4820 {:type-code 4820, :fill "#e2a005", :stroke "#aa0000"},
   1170 {:type-code 1170, :fill "#5f8651", :stroke "#000000"},
   4404 {:type-code 4404, :fill "", :stroke "#58c510"},
   108  {:type-code 108, :fill "#99d18f", :stroke "#000000"},
   4401 {:type-code 4401, :fill "", :stroke "#ee4548"},
   2350 {:type-code 2350, :fill "#ff77ab", :stroke "#000000"},
   2340 {:type-code 2340, :fill "#8d4600", :stroke "#000000"},
   2120 {:type-code 2120, :fill "#fea0f4", :stroke "#000000"},
   109  {:type-code 109, :fill "#94d024", :stroke "#000000"},
   5160 {:type-code 5160, :fill "#daadaf", :stroke "#00557f"},
   1550 {:type-code 1550, :fill "#052283", :stroke "#ffffff"},
   3230 {:type-code 3230, :fill "#9cadef", :stroke "#000000"},
   5130 {:type-code 5130, :fill "#743a3f", :stroke "#00557f"},
   5110 {:type-code 5110, :fill "#982757", :stroke "#00557f"},
   3240 {:type-code 3240, :fill "#00438c", :stroke "#000000"},
   4510 {:type-code 4510, :fill "#ff9562", :stroke "#000000"},
   4240 {:type-code 4240, :fill "#55007f", :stroke "#000000"},
   2270 {:type-code 2270, :fill "#81d5aa", :stroke "#000000"},
   4210 {:type-code 4210, :fill "#d6a9fe", :stroke "#000000"},
   301  {:type-code 301, :fill "#60b924", :stroke "#004800"},
   111  {:type-code 111, :fill "#68c613", :stroke "#000000"},
   4630 {:type-code 4630, :fill "#cf65a8", :stroke "#000000"},
   4810 {:type-code 4810, :fill "#ffb02d", :stroke "#aa0000"},
   1540 {:type-code 1540, :fill "#00b6ea", :stroke "#000000"},
   5320 {:type-code 5320, :fill "#9c997e", :stroke "#aa00ff"},
   3210 {:type-code 3210, :fill "#808bf0", :stroke "#000000"},
   4640 {:type-code 4640, :fill "#e08fd2", :stroke "#000000"},
   1150 {:type-code 1150, :fill "#548572", :stroke "#000000"},
   2310 {:type-code 2310, :fill "#d93a09", :stroke "#000000"},
   5210 {:type-code 5210, :fill "#1752ff", :stroke "#0055ff"},
   2380 {:type-code 2380, :fill "#c0c2c3", :stroke "#000000"},
   103  {:type-code 103, :fill "#57fba0", :stroke "#000000"},
   201  {:type-code 201, :fill "#8397de", :stroke "#0055ff"},
   1220 {:type-code 1220, :fill "#b12e07", :stroke "#000000"},
   4411 {:type-code 4411, :fill "", :stroke "#e588fc"},
   1140 {:type-code 1140, :fill "#78a380", :stroke "#000000"},
   4520 {:type-code 4520, :fill "#ff7b3c", :stroke "#000000"},
   2710 {:type-code 2710, :fill "#55007f", :stroke "#000000"},
   107  {:type-code 107, :fill "#daab0c", :stroke "#000000"},
   6110 {:type-code 6110, :fill "#8c4600", :stroke "#ee0000"},
   1120 {:type-code 1120, :fill "#b7d1b6", :stroke "#c7a481"},
   5340 {:type-code 5340, :fill "#a9b1ae", :stroke "#aa00ff"},
   302  {:type-code 302, :fill "#218413", :stroke "#004800"},
   4405 {:type-code 4405, :fill "", :stroke "#397e0b"},
   6120 {:type-code 6120, :fill "#614630", :stroke "#ee0000"},
   1310 {:type-code 1310, :fill "#ff860c", :stroke "#000000"},
   202  {:type-code 202, :fill "#397e0b", :stroke "#004800"},
   1620 {:type-code 1620, :fill "#62d53c", :stroke "#000000"},
   2250 {:type-code 2250, :fill "#707172", :stroke "#000000"},
   2530 {:type-code 2530, :fill "#caf3ff", :stroke "#000000"},
   112  {:type-code 112, :fill "#b7da00", :stroke "#000000"},
   2130 {:type-code 2130, :fill "#934de8", :stroke "#000000"},
   3220 {:type-code 3220, :fill "#5659e9", :stroke "#000000"},
   5330 {:type-code 5330, :fill "#86938e", :stroke "#aa00ff"},
   4230 {:type-code 4230, :fill "#ffdd1c", :stroke "#000000"},
   4320 {:type-code 4320, :fill "#9c7ada", :stroke "#000000"},
   3130 {:type-code 3130, :fill "#0077fb", :stroke "#000000"},
   3110 {:type-code 3110, :fill "#11acff", :stroke "#000000"},
   203  {:type-code 203, :fill "#4aa684", :stroke "#0055ff"},
   4620 {:type-code 4620, :fill "#9f0000", :stroke "#000000"},
   5360 {:type-code 5360, :fill "#867e60", :stroke "#aa00ff"},
   2290 {:type-code 2290, :fill "#769e86", :stroke "#000000"},
   2260 {:type-code 2260, :fill "#c47577", :stroke "#000000"},
   1160 {:type-code 1160, :fill "#879948", :stroke "#000000"},
   1210 {:type-code 1210, :fill "#d93a09", :stroke "#000000"},
   5140 {:type-code 5140, :fill "#c57682", :stroke "#00557f"},
   4310 {:type-code 4310, :fill "#b19ae2", :stroke "#000000"},
   207  {:type-code 207, :fill "#f4450b", :stroke "#000000"},
   1130 {:type-code 1130, :fill "#8cb887", :stroke "#000000"},
   5120 {:type-code 5120, :fill "#a94b7e", :stroke "#00557f"},
   4110 {:type-code 4110, :fill "#b0fff5", :stroke "#000000"},
   4452 {:type-code 4452, :fill "", :stroke "#0055ff"},
   5370 {:type-code 5370, :fill "#c4d0cf", :stroke "#aa00ff"},
   2240 {:type-code 2240, :fill "#8181bd", :stroke "#000000"},
   2510 {:type-code 2510, :fill "#94e8ff", :stroke "#000000"},
   1640 {:type-code 1640, :fill "#aadf75", :stroke "#000000"},
   1380 {:type-code 1380, :fill "#707172", :stroke "#707172"},
   4451 {:type-code 4451, :fill "", :stroke "#389acf"},
   4403 {:type-code 4403, :fill "", :stroke "#f0d50f"},
   5150 {:type-code 5150, :fill "#c75887", :stroke "#00557f"},
   1630 {:type-code 1630, :fill "#a5d92d", :stroke "#000000"},
   2370 {:type-code 2370, :fill "#97aa79", :stroke "#000000"},
   1340 {:type-code 1340, :fill "#cd9b62", :stroke "#000000"},
   1610 {:type-code 1610, :fill "#9fe956", :stroke "#000000"},
   4421 {:type-code 4421, :fill "", :stroke "#9f137c"},
   2220 {:type-code 2220, :fill "#af8654", :stroke "#000000"},
   1320 {:type-code 1320, :fill "#aaaaff", :stroke "#707172"},
   4402 {:type-code 4402, :fill "", :stroke "#94f4fc"}})

(def adapted-temp-symbols
  (reduce
   (fn [m [k v]]
     (let [v2     (temp-symbols k)
           stroke (if-let [c (not-empty (:stroke v2))] c "#000000")
           fill   (if-let [c (not-empty (:fill v2))] c "#000000")]
       (assoc m k (-> v
                      (assoc-in [:stroke :color] stroke)
                      (assoc-in [:fill :color] fill)))))
   {}
   all))
