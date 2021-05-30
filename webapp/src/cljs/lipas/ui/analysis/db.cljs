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

(def default-db
  {:selected-tab            "sports-sites"
   :distance-km             15
   :travel-profiles         [:car :foot :bicycle :direct]
   :selected-travel-profile :car
   :selected-travel-metric  :travel-time
   :zones
   {:colors zone-colors
    :distance
    [{:min 0 :max 2 :id :zone1}
     {:min 2 :max 5 :id :zone2}
     {:min 5 :max 10 :id :zone3}
     {:min 10 :max 15 :id :zone4}]
    :travel-time
    [{:min 0 :max 15 :id :zone1}
     {:min 15 :max 30 :id :zone2}
     {:min 30 :max 45 :id :zone3}
     {:min 45 :max 60 :id :zone4}]}
   :population
   {:view "chart"}
   :sports-sites
   {:view "chart"}
   :schools
   {:view "chart"}})
