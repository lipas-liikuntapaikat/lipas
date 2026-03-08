(ns lipas.ui.analysis.heatmap.views
  (:require [lipas.ui.analysis.heatmap.events :as events]
            [lipas.ui.analysis.heatmap.subs :as subs]
            [lipas.ui.components.selects :as selects]
            ["@mui/material/Accordion$default" :as Accordion]
            ["@mui/material/AccordionDetails$default" :as AccordionDetails]
            ["@mui/material/AccordionSummary$default" :as AccordionSummary]
            ["@mui/material/FormControl$default" :as FormControl]
            ["@mui/material/FormControlLabel$default" :as FormControlLabel]
            ["@mui/material/FormHelperText$default" :as FormHelperText]
            ["@mui/material/GridLegacy$default" :as Grid]
            ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/InputLabel$default" :as InputLabel]
            ["@mui/material/LinearProgress$default" :as LinearProgress]
            ["@mui/material/MenuItem$default" :as MenuItem]
            ["@mui/material/Paper$default" :as Paper]
            ["@mui/material/Select$default" :as Select]
            ["@mui/material/Slider$default" :as Slider]
            ["@mui/material/Stack$default" :as Stack]
            ["@mui/material/Switch$default" :as Switch]
            ["@mui/material/Typography$default" :as Typography]
            [re-frame.core :as rf]
            [reagent.core :as r]))

#_(defn dimension-selector []
  (let [dimension @(rf/subscribe [::subs/dimension])
        tr @(rf/subscribe [:lipas.ui.subs/translator])]
    [:> FormControl {:full-width true :margin "normal"}
     [:> InputLabel (tr :analysis/heatmap-dimension)]
     [:> Select
      {:value dimension
       :on-change #(rf/dispatch [::events/update-dimension-and-refresh (keyword (.. % -target -value))])}
      [:> MenuItem {:value :density} (tr :analysis/heatmap-facility-density)]
      [:> MenuItem {:value :area} (tr :analysis/heatmap-total-area-coverage)]
      [:> MenuItem {:value :capacity} (tr :analysis/heatmap-capacity-distribution)]
      [:> MenuItem {:value :type-distribution} (tr :analysis/heatmap-facility-types)]
      [:> MenuItem {:value :year-round} (tr :analysis/heatmap-year-round-availability)]
      [:> MenuItem {:value :lighting} (tr :analysis/heatmap-facilities-with-lighting)]
      [:> MenuItem {:value :activities} (tr :analysis/heatmap-activity-distribution)]]]))

#_(defn weight-selector []
  (let [weight-by @(rf/subscribe [::subs/weight-by])
        dimension @(rf/subscribe [::subs/dimension])
        tr @(rf/subscribe [:lipas.ui.subs/translator])]
    (when (= dimension :density)
      [:> FormControl {:full-width true :margin "normal"}
       [:> InputLabel (tr :analysis/heatmap-weight-by)]
       [:> Select
        {:value weight-by
         :on-change #(rf/dispatch [::events/update-weight-by-and-refresh (keyword (.. % -target -value))])}
        [:> MenuItem {:value :count} (tr :analysis/heatmap-count)]
        [:> MenuItem {:value :area-m2} (tr :analysis/heatmap-area-m2)]
        [:> MenuItem {:value :route-length-km} (tr :analysis/heatmap-route-length-km)]]])))

#_(defn precision-selector []
  (let [precision @(rf/subscribe [::subs/precision])
        zoom @(rf/subscribe [:lipas.ui.map.subs/zoom])
        tr @(rf/subscribe [:lipas.ui.subs/translator])]
    [:> FormControl {:full-width true :margin "normal"}
     [:> InputLabel (tr :analysis/heatmap-geohash-precision)]
     [:> Select
      {:value (or precision "auto")
       :on-change #(let [val (.. % -target -value)]
                     (rf/dispatch [::events/update-precision-and-refresh
                                   (if (= val "auto") nil (js/parseInt val))]))}
      [:> MenuItem {:value "auto"} (str (tr :analysis/heatmap-auto-current) (cond
                                                                                (<= zoom 5) 4
                                                                                (<= zoom 7) 5
                                                                                (<= zoom 9) 6
                                                                                (<= zoom 11) 7
                                                                                (<= zoom 13) 8
                                                                                (<= zoom 15) 8
                                                                                :else 8) ")")]
      [:> MenuItem {:value 1} (tr :analysis/heatmap-precision-1)]
      [:> MenuItem {:value 2} (tr :analysis/heatmap-precision-2)]
      [:> MenuItem {:value 3} (tr :analysis/heatmap-precision-3)]
      [:> MenuItem {:value 4} (tr :analysis/heatmap-precision-4)]
      [:> MenuItem {:value 5} (tr :analysis/heatmap-precision-5)]
      [:> MenuItem {:value 6} (tr :analysis/heatmap-precision-6)]
      [:> MenuItem {:value 7} (tr :analysis/heatmap-precision-7)]
      [:> MenuItem {:value 8} (tr :analysis/heatmap-precision-8)]
      [:> MenuItem {:value 9} (tr :analysis/heatmap-precision-9)]
      [:> MenuItem {:value 10} (tr :analysis/heatmap-precision-10)]]
     [:> FormHelperText (tr :analysis/heatmap-precision-help)]]))

#_(defn bbox-filter-selector []
  (let [use-bbox-filter? @(rf/subscribe [::subs/use-bbox-filter?])
        tr @(rf/subscribe [:lipas.ui.subs/translator])]
    [:> FormControl {:full-width true :margin "normal"}
     [:> FormControlLabel
      {:control (r/as-element
                 [:> Switch
                  {:checked use-bbox-filter?
                   :on-change #(rf/dispatch [::events/update-bbox-filter-and-refresh
                                             (.. % -target -checked)])}])
       :label (tr :analysis/heatmap-filter-by-map-view)}]
     [:> FormHelperText
      (if use-bbox-filter?
        (tr :analysis/heatmap-analyzing-current-bounds)
        (tr :analysis/heatmap-analyzing-whole-finland))]]))

;; Updated selectors using existing components
(defn type-filter []
  (let [tr @(rf/subscribe [:lipas.ui.subs/translator])
        filters @(rf/subscribe [::subs/filters])
        selected-types (get filters :type-codes [])]
    [:> Grid {:item true :xs 12 :style {:margin-top "8px"}}
     [selects/type-category-selector
      {:value selected-types
       :label (tr :actions/select-types)
       :on-change #(rf/dispatch [::events/update-filter-and-refresh :type-codes %])}]]))

(defn owner-filter []
  (let [tr @(rf/subscribe [:lipas.ui.subs/translator])
        filters @(rf/subscribe [::subs/filters])
        selected-owners (get filters :owners [])]
    [:> Grid {:item true :xs 12 :style {:margin-top "8px"}}
     [selects/owner-selector
      {:value selected-owners
       :label (tr :actions/select-owners)
       :on-change #(rf/dispatch [::events/update-filter-and-refresh :owners %])}]]))

(defn admin-filter []
  (let [tr @(rf/subscribe [:lipas.ui.subs/translator])
        filters @(rf/subscribe [::subs/filters])
        selected-admins (get filters :admins [])]
    [:> Grid {:item true :xs 12 :style {:margin-top "8px"}}
     [selects/admin-selector
      {:value selected-admins
       :label (tr :actions/select-admins)
       :on-change #(rf/dispatch [::events/update-filter-and-refresh :admins %])}]]))

(defn status-filter []
  (let [filters @(rf/subscribe [::subs/filters])
        selected-statuses (get filters :status-codes [])]
    [:> Grid {:item true :xs 12 :style {:margin-top "8px"}}
     [selects/status-selector
      {:value selected-statuses
       :on-change #(rf/dispatch [::events/update-filter-and-refresh :status-codes %])}]]))

(defn year-range-filter []
  (let [tr @(rf/subscribe [:lipas.ui.subs/translator])
        facets @(rf/subscribe [::subs/facets])
        filters @(rf/subscribe [::subs/filters])
        year-range (get facets :year-range)
        current-range (or (:year-range filters)
                          (when year-range [(:min year-range) (:max year-range)]))]
    (when (and year-range current-range)
      [:> Grid {:item true :xs 12 :sx #js{:mt 1}}
       [:> Typography {:gutterBottom true} (tr :actions/filter-construction-year)]
       [:> Stack {:direction "column" :style {:padding "1em"}}
        [:> Slider
         {:value current-range
          :min (:min year-range)
          :max (:max year-range)
          :marks true
          :value-label-display "auto"
          :on-change (fn [_ value]
                       (rf/dispatch [::events/set-filter :year-range value]))
          :on-change-committed (fn [_ value]
                                 (rf/dispatch [::events/update-filter-and-refresh :year-range value]))}]]])))

(defn visual-controls []
  (let [visual @(rf/subscribe [::subs/visual-params])
        tr @(rf/subscribe [:lipas.ui.subs/translator])]
    [:> Grid {:container true :spacing 2}

     [:> Grid {:item true :xs 12}
      [:> Typography {:gutterBottom true} (str (tr :analysis/heatmap-radius) (:radius visual) "px")]
      [:> Slider
       {:value (:radius visual)
        :min 5
        :max 100
        :on-change #(rf/dispatch [::events/update-visual-param :radius (.-value (.-target %))])}]]

     [:> Grid {:item true :xs 12}
      [:> Typography {:gutterBottom true} (str (tr :analysis/heatmap-blur) (:blur visual) "px")]
      [:> Slider
       {:value (:blur visual)
        :min 0
        :max 50
        :on-change #(rf/dispatch [::events/update-visual-param :blur (.-value (.-target %))])}]]

     [:> Grid {:item true :xs 12}
      [:> Typography {:gutterBottom true} (str (tr :analysis/heatmap-opacity) (:opacity visual))]
      [:> Slider
       {:value (:opacity visual)
        :min 0.1
        :max 1
        :step 0.1
        :on-change #(rf/dispatch [::events/update-visual-param :opacity (.-value (.-target %))])}]]

     [:> Grid {:item true :xs 12}
      [:> FormControl {:full-width true :margin "normal"}
       [:> InputLabel (tr :analysis/heatmap-color-gradient)]
       [:> Select
        {:value (:gradient visual)
         :on-change #(rf/dispatch [::events/update-visual-param :gradient (keyword (.. % -target -value))])}
        [:> MenuItem {:value :cool} (tr :analysis/heatmap-gradient-cool)]
        [:> MenuItem {:value :warm} (tr :analysis/heatmap-gradient-warm)]
        [:> MenuItem {:value :grayscale} (tr :analysis/heatmap-gradient-grayscale)]]]]]))

(defn filters-panel []
  (let [tr @(rf/subscribe [:lipas.ui.subs/translator])]
    [:> Accordion {:default-expanded true}
     [:> AccordionSummary {:expand-icon (r/as-element [:> Icon "expand_more"])}
      [:> Typography (tr :analysis/heatmap-filters)]]
     [:> AccordionDetails
      [:> Grid {:container true :spacing 2}
       [type-filter]
       [status-filter]
       [owner-filter]
       [admin-filter]
       [year-range-filter]
       ;; Boolean filters hidden for now. Maybe revealed later
       #_[:> Grid {:item true :xs 12}
          [boolean-filters]]]]]))

(defn heatmap-controls []
  (let [loading? @(rf/subscribe [::subs/loading?])
        error @(rf/subscribe [::subs/error])
        tr @(rf/subscribe [:lipas.ui.subs/translator])]
    [:> Stack {:direction "column" :spacing 2}
     [:> Paper {:style {:padding "0.5em"
                         :overflow-y "auto"}}
      [:> Typography {:variant "h5" :gutterBottom true}
       (tr :analysis/heatmap)]

      ;; Info text
      [:> Paper {:style {:margin-bottom "16px"
                          :padding "1em"
                          :background-color "#f5e642"}}
       [:> Typography {:variant "body2"}
        (tr :analysis/heatmap-info-text)]]

      (when error
        [:> Paper {:style {:margin-bottom "16px" :padding "8px" :background-color "#f44336" :color "white"}}
         [:> Typography error]])

      (when loading?
        [:> LinearProgress {:style {:margin-bottom "16px"}}])

      ;; All locked selectors are now hidden:
      ;; - dimension-selector (locked to :density)
      ;; - weight-selector (locked to :count)
      ;; - precision-selector (locked to auto/nil)
      ;; - bbox-filter-selector (locked to true)

      [filters-panel]

      [:> Accordion
       [:> AccordionSummary {:expand-icon (r/as-element [:> Icon "expand_more"])}
        [:> Typography (tr :analysis/heatmap-visual-settings)]]
       [:> AccordionDetails
        [visual-controls]]]

      ;; "Landing bay" so the floating controls don't overlap content
      [:div {:style {:height "3.5em"}}]]]))
