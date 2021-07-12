(ns lipas.ui.analysis.db
  (:require
   [goog.color :as gcolor]))

(def travel-metrics [:travel-time :distance])

;; Zone colors
(def base-color "#0073e6")
(def zone-colors
  (into {}
        (for [n (range 1 (inc 10))]
          [(keyword (str "zone" n))
           (-> base-color
               gcolor/hexToRgb
               (gcolor/lighten (/ n 10))
               gcolor/rgbArrayToHex)])))

#_(def zone-colors
  {:zone1  "#00c7ff"
   :zone2  "#1ab8eb"
   :zone3  "#25a9d8"
   :zone4  "#2b9ac4"
   :zone5  "#2e8cb1"
   :zone6  "#2f7d9f"
   :zone7  "#30708c"
   :zone8  "#2f627b"
   :zone9  "#2d5569"
   :zone10 "#2a4858"})

#_(def zone-colors
  {:zone1 "#ED71AD"
   :zone2 "#DF72B5"
   :zone3 "#D272BC"
   :zone4 "#C473C4"
   :zone5 "#B674CB"
   :zone6 "#A974D3"
   :zone7 "#9B75DA"
   :zone8 "#8D76E2"
   :zone9 "#8076E9"
   :zone10 "#7277F1"})

#_(def zone-colors
    {:zone1  "#F4F269",
     :zone2  "#E3EB6A",
     :zone3  "#D2E46B",
     :zone4  "#C1DD6B",
     :zone5  "#B0D66C",
     :zone6  "#A0CE6D",
     :zone7  "#8FC76E",
     :zone8  "#7EC06E",
     :zone9  "#6DB96F",
     :zone10 "#5CB270"})

(def distances
  (into {}
        (map-indexed
         (fn [idx v]
           [idx {:idx idx :value v :label (str v "km")}]))
        [0 0.5 1 1.5 2 3 4 5 7.5 10 15 20 30 40 50]))

(def travel-times
  (into {}
        (map-indexed
         (fn [idx v]
           [idx {:idx idx :value v :label (str v "min")}]))
        [0 10 20 30 40 50 60]))

(def default-db
  {:selected-tab            "sports-sites"
   :distance-km             15
   :travel-profiles         [:car :foot :bicycle :direct]
   :selected-travel-profile :car
   :selected-travel-metric  :travel-time
   :zones
   {:ranges
    {:distance    distances
     :travel-time travel-times}
    :colors zone-colors
    :distance
    (map-indexed
     (fn [idx [from-idx to-idx]]
       {:min     (get-in distances [from-idx :value])
        :min-idx from-idx
        :max     (get-in distances [to-idx :value])
        :max-idx to-idx
        :id      (keyword (str "zone" (inc idx)))})
     [[0 4] [4 7] [7 9] [9 10]])
    :travel-time
    (map-indexed
     (fn [idx [from-idx to-idx]]
       {:min     (get-in travel-times [from-idx :value])
        :min-idx from-idx
        :max     (get-in travel-times [to-idx :value])
        :max-idx to-idx
        :id      (keyword (str "zone" (inc idx)))})
     [[0 1] [1 2] [2 3] [3 4]])}
   :population
   {:view "chart"}
   :sports-sites
   {:view "chart"}
   :schools
   {:view "chart"}})
