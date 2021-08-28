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
   {:view       "chart"
    :chart-mode "cumulative"}
   :sports-sites
   {:view "chart"}
   :schools
   {:view       "chart"
    :chart-mode "cumulative"}})
