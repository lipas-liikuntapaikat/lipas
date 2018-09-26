(ns lipas.ui.map.views
  (:require [lipas.ui.components :as lui]
            [lipas.ui.map.events :as events]
            [lipas.ui.map.map :as ol-map]
            [lipas.ui.map.subs :as subs]
            [lipas.ui.mui :as mui]
            [lipas.ui.utils :refer [<== ==>] :as utils]))

(defn tools []
  (let [filters (<== [::subs/filters])
        toggle  #(==> [::events/toggle-filter %])]
    [mui/paper {:style {:position         :fixed
                        :z-index          9999
                        :background-color mui/gray2
                        :bottom           0
                        :left             0
                        :padding-left     "1em"}}
     [lui/checkbox
      {:value     (-> filters :ice-stadium)
       :label     "Jäähallit"
       :on-change #(toggle :ice-stadium)}]
     [lui/checkbox
      {:value     (-> filters :swimming-pool)
       :label     "Uimahallit"
       :on-change #(toggle :swimming-pool)}]]))

(defn map-view [tr]
  [mui/grid {:container true
             :style     {:flex-direction "column"
                         :flex           "1 0 auto"}}
   [tools]
   [ol-map/map-outer]])
