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
                       :padding-left     "1em"
                       :padding-right    "1em"}}]
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

(defn popup []
  (let [{:keys [data anchor-el]} (<== [::subs/popup])
        {:keys [name]}  (-> data :features first :properties)]
    [mui/popper {:open      (boolean (seq data))
                 :placement :top-end
                 :anchor-el anchor-el
                 :container anchor-el
                 :modifiers {:offset
                             {:enabled true
                              :offset  "0px,10px"}}}
     [mui/paper {:style {:padding "0.5em"}}
      [mui/typography {:variant :body2}
       name]]]))

(defn sports-site-info []
  (let [site     (<== [::subs/selected-sports-site])
        lipas-id (:lipas-id site)
        portal   (case (-> site :type :type-code)
                   (3110 3130) "uimahalliportaali"
                   (2510 2520) "jaahalliportaali"
                   "")]

    (when site
      [:div {:style {:padding-top "0.5em"}}
       [mui/typography {:variant :headline}
        (:name site)]

       ;; [mui/typography {:variant :body2}
       ;;  (-> site :construction-year)]

       ;; [mui/typography {:variant :body2}
       ;;  (-> site :type :name)]

       [mui/typography {:variant :body2}
        (-> site :location :address)]
       [mui/typography {:variant :body2}
        (-> site :location :postal-code)]
       [mui/typography {:variant :body2}
        (-> site :location :city :name)]
       [mui/button {:href     (str "/#/" portal "/hallit/" lipas-id)}
        [mui/icon "arrow_right"] "Kaikki tiedot"]])))

(defn map-view [tr]
  [mui/grid {:container true
             :style     {:flex-direction "column"
                         :flex           "1 0 auto"}}
   [floating-container {:right 0}
    [layer-switcher]]
   [floating-container {:bottom 0 :left 0}
    [filters]]
   [floating-container {:bottom 0 :right 0}
    [sports-site-info]]
   [:div {:id "popup-anchor" :display :none}]
   [popup]
   [ol-map/map-outer]])
