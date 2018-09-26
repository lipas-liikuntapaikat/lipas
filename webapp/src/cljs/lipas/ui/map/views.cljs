(ns lipas.ui.map.views
  (:require [lipas.ui.components :as lui]
            [lipas.ui.map.events :as events]
            [lipas.ui.map.map :as ol-map]
            [lipas.ui.map.subs :as subs]
            [lipas.ui.mui :as mui]
            [reagent.core :as r]
            [clojure.set :refer [map-invert]]
            [lipas.ui.utils :refer [<== ==>] :as utils]))

(defn layer-switcher []
  (let [basemaps {:taustakartta "Taustakartta"
                  :maastokartta "Maastokartta"
                  :ortokuva     "Ortokuva"}
        basemap  (<== [::subs/basemap])]
    [mui/form-control {:component "fieldset"}
     (into
      [mui/radio-group
       {:value     (get basemaps basemap)
        :on-change #(==> [::events/select-basemap (get (map-invert basemaps) %2)])}]
      (for [k (vals basemaps)]
        [mui/form-control-label {:control (r/as-element [mui/radio])
                                 :value   k
                                 :label   k}]))]))

(defn floating-container [{:keys [top right bottom left]} & children]
  (into
   [mui/paper {:style {:position         :fixed
                       :z-index          9999
                       :background-color mui/gray2
                       :top              top
                       :right            right
                       :bottom           bottom
                       :left             left
                       :padding-left     "1em"}}]
   children))

(defn filters []
  (let [filters (<== [::subs/filters])
        toggle  #(==> [::events/toggle-filter %])]
    [:div
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
   [floating-container {:right 0}
    [layer-switcher]]
   [floating-container {:bottom 0 :left 0}
    [filters]]
   [ol-map/map-outer]])
