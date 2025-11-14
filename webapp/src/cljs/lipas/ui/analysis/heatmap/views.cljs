(ns lipas.ui.analysis.heatmap.views
  (:require [lipas.ui.analysis.heatmap.events :as events]
            [lipas.ui.analysis.heatmap.subs :as subs]
            [lipas.ui.components.selects :as selects]
            [lipas.ui.mui :as mui]
            [re-frame.core :as rf]
            [reagent.core :as r]))

#_(defn dimension-selector []
  (let [dimension @(rf/subscribe [::subs/dimension])
        tr @(rf/subscribe [:lipas.ui.subs/translator])]
    [mui/form-control {:full-width true :margin "normal"}
     [mui/input-label (tr :analysis/heatmap-dimension)]
     [mui/select
      {:value dimension
       :on-change #(rf/dispatch [::events/update-dimension-and-refresh (keyword (.. % -target -value))])}
      [mui/menu-item {:value :density} (tr :analysis/heatmap-facility-density)]
      [mui/menu-item {:value :area} (tr :analysis/heatmap-total-area-coverage)]
      [mui/menu-item {:value :capacity} (tr :analysis/heatmap-capacity-distribution)]
      [mui/menu-item {:value :type-distribution} (tr :analysis/heatmap-facility-types)]
      [mui/menu-item {:value :year-round} (tr :analysis/heatmap-year-round-availability)]
      [mui/menu-item {:value :lighting} (tr :analysis/heatmap-facilities-with-lighting)]
      [mui/menu-item {:value :activities} (tr :analysis/heatmap-activity-distribution)]]]))

#_(defn weight-selector []
  (let [weight-by @(rf/subscribe [::subs/weight-by])
        dimension @(rf/subscribe [::subs/dimension])
        tr @(rf/subscribe [:lipas.ui.subs/translator])]
    (when (= dimension :density)
      [mui/form-control {:full-width true :margin "normal"}
       [mui/input-label (tr :analysis/heatmap-weight-by)]
       [mui/select
        {:value weight-by
         :on-change #(rf/dispatch [::events/update-weight-by-and-refresh (keyword (.. % -target -value))])}
        [mui/menu-item {:value :count} (tr :analysis/heatmap-count)]
        [mui/menu-item {:value :area-m2} (tr :analysis/heatmap-area-m2)]
        [mui/menu-item {:value :route-length-km} (tr :analysis/heatmap-route-length-km)]]])))

#_(defn precision-selector []
  (let [precision @(rf/subscribe [::subs/precision])
        zoom @(rf/subscribe [:lipas.ui.map.subs/zoom])
        tr @(rf/subscribe [:lipas.ui.subs/translator])]
    [mui/form-control {:full-width true :margin "normal"}
     [mui/input-label (tr :analysis/heatmap-geohash-precision)]
     [mui/select
      {:value (or precision "auto")
       :on-change #(let [val (.. % -target -value)]
                     (rf/dispatch [::events/update-precision-and-refresh
                                   (if (= val "auto") nil (js/parseInt val))]))}
      [mui/menu-item {:value "auto"} (str (tr :analysis/heatmap-auto-current) (cond
                                                                                (<= zoom 5) 4
                                                                                (<= zoom 7) 5
                                                                                (<= zoom 9) 6
                                                                                (<= zoom 11) 7
                                                                                (<= zoom 13) 8
                                                                                (<= zoom 15) 8
                                                                                :else 8) ")")]
      [mui/menu-item {:value 1} (tr :analysis/heatmap-precision-1)]
      [mui/menu-item {:value 2} (tr :analysis/heatmap-precision-2)]
      [mui/menu-item {:value 3} (tr :analysis/heatmap-precision-3)]
      [mui/menu-item {:value 4} (tr :analysis/heatmap-precision-4)]
      [mui/menu-item {:value 5} (tr :analysis/heatmap-precision-5)]
      [mui/menu-item {:value 6} (tr :analysis/heatmap-precision-6)]
      [mui/menu-item {:value 7} (tr :analysis/heatmap-precision-7)]
      [mui/menu-item {:value 8} (tr :analysis/heatmap-precision-8)]
      [mui/menu-item {:value 9} (tr :analysis/heatmap-precision-9)]
      [mui/menu-item {:value 10} (tr :analysis/heatmap-precision-10)]]
     [mui/form-helper-text (tr :analysis/heatmap-precision-help)]]))

#_(defn bbox-filter-selector []
  (let [use-bbox-filter? @(rf/subscribe [::subs/use-bbox-filter?])
        tr @(rf/subscribe [:lipas.ui.subs/translator])]
    [mui/form-control {:full-width true :margin "normal"}
     [mui/form-control-label
      {:control (r/as-element
                 [mui/switch
                  {:checked use-bbox-filter?
                   :on-change #(rf/dispatch [::events/update-bbox-filter-and-refresh
                                             (.. % -target -checked)])}])
       :label (tr :analysis/heatmap-filter-by-map-view)}]
     [mui/form-helper-text
      (if use-bbox-filter?
        (tr :analysis/heatmap-analyzing-current-bounds)
        (tr :analysis/heatmap-analyzing-whole-finland))]]))

;; Updated selectors using existing components
(defn type-filter []
  (let [tr @(rf/subscribe [:lipas.ui.subs/translator])
        filters @(rf/subscribe [::subs/filters])
        selected-types (get filters :type-codes [])]
    [mui/grid {:item true :xs 12 :style {:margin-top "8px"}}
     [selects/type-category-selector
      {:value selected-types
       :label (tr :actions/select-types)
       :on-change #(rf/dispatch [::events/update-filter-and-refresh :type-codes %])}]]))

(defn owner-filter []
  (let [tr @(rf/subscribe [:lipas.ui.subs/translator])
        filters @(rf/subscribe [::subs/filters])
        selected-owners (get filters :owners [])]
    [mui/grid {:item true :xs 12 :style {:margin-top "8px"}}
     [selects/owner-selector
      {:value selected-owners
       :label (tr :actions/select-owners)
       :on-change #(rf/dispatch [::events/update-filter-and-refresh :owners %])}]]))

(defn admin-filter []
  (let [tr @(rf/subscribe [:lipas.ui.subs/translator])
        filters @(rf/subscribe [::subs/filters])
        selected-admins (get filters :admins [])]
    [mui/grid {:item true :xs 12 :style {:margin-top "8px"}}
     [selects/admin-selector
      {:value selected-admins
       :label (tr :actions/select-admins)
       :on-change #(rf/dispatch [::events/update-filter-and-refresh :admins %])}]]))

(defn status-filter []
  (let [filters @(rf/subscribe [::subs/filters])
        selected-statuses (get filters :status-codes [])]
    [mui/grid {:item true :xs 12 :style {:margin-top "8px"}}
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
      [mui/grid {:item true :xs 12 :sx #js{:mt 1}}
       [mui/typography {:gutterBottom true} (tr :actions/filter-construction-year)]
       [mui/stack {:direction "column" :style {:padding "1em"}}
        [mui/slider
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
    [mui/grid {:container true :spacing 2}

     [mui/grid {:item true :xs 12}
      [mui/typography {:gutterBottom true} (str (tr :analysis/heatmap-radius) (:radius visual) "px")]
      [mui/slider
       {:value (:radius visual)
        :min 5
        :max 100
        :on-change #(rf/dispatch [::events/update-visual-param :radius (.-value (.-target %))])}]]

     [mui/grid {:item true :xs 12}
      [mui/typography {:gutterBottom true} (str (tr :analysis/heatmap-blur) (:blur visual) "px")]
      [mui/slider
       {:value (:blur visual)
        :min 0
        :max 50
        :on-change #(rf/dispatch [::events/update-visual-param :blur (.-value (.-target %))])}]]

     [mui/grid {:item true :xs 12}
      [mui/typography {:gutterBottom true} (str (tr :analysis/heatmap-opacity) (:opacity visual))]
      [mui/slider
       {:value (:opacity visual)
        :min 0.1
        :max 1
        :step 0.1
        :on-change #(rf/dispatch [::events/update-visual-param :opacity (.-value (.-target %))])}]]

     [mui/grid {:item true :xs 12}
      [mui/form-control {:full-width true :margin "normal"}
       [mui/input-label (tr :analysis/heatmap-color-gradient)]
       [mui/select
        {:value (:gradient visual)
         :on-change #(rf/dispatch [::events/update-visual-param :gradient (keyword (.. % -target -value))])}
        [mui/menu-item {:value :cool} (tr :analysis/heatmap-gradient-cool)]
        [mui/menu-item {:value :warm} (tr :analysis/heatmap-gradient-warm)]
        [mui/menu-item {:value :grayscale} (tr :analysis/heatmap-gradient-grayscale)]]]]]))

(defn filters-panel []
  (let [tr @(rf/subscribe [:lipas.ui.subs/translator])]
    [mui/expansion-panel {:default-expanded true}
     [mui/expansion-panel-summary {:expand-icon (r/as-element [mui/icon "expand_more"])}
      [mui/typography (tr :analysis/heatmap-filters)]]
     [mui/expansion-panel-details
      [mui/grid {:container true :spacing 2}
       [type-filter]
       [status-filter]
       [owner-filter]
       [admin-filter]
       [year-range-filter]
       ;; Boolean filters hidden for now. Maybe revealed later
       #_[mui/grid {:item true :xs 12}
          [boolean-filters]]]]]))

(defn heatmap-controls []
  (let [loading? @(rf/subscribe [::subs/loading?])
        error @(rf/subscribe [::subs/error])
        tr @(rf/subscribe [:lipas.ui.subs/translator])]
    [mui/stack {:direction "column" :spacing 2}
     [mui/paper {:style {:padding "0.5em"
                         :overflow-y "auto"}}
      [mui/typography {:variant "h5" :gutterBottom true}
       (tr :analysis/heatmap)]

      ;; Info text
      [mui/paper {:style {:margin-bottom "16px"
                          :padding "1em"
                          :background-color "#f5e642"}}
       [mui/typography {:variant "body2"}
        (tr :analysis/heatmap-info-text)]]

      (when error
        [mui/paper {:style {:margin-bottom "16px" :padding "8px" :background-color "#f44336" :color "white"}}
         [mui/typography error]])

      (when loading?
        [mui/linear-progress {:style {:margin-bottom "16px"}}])

      ;; All locked selectors are now hidden:
      ;; - dimension-selector (locked to :density)
      ;; - weight-selector (locked to :count)
      ;; - precision-selector (locked to auto/nil)
      ;; - bbox-filter-selector (locked to true)

      [filters-panel]

      [mui/expansion-panel
       [mui/expansion-panel-summary {:expand-icon (r/as-element [mui/icon "expand_more"])}
        [mui/typography (tr :analysis/heatmap-visual-settings)]]
       [mui/expansion-panel-details
        [visual-controls]]]

      ;; "Landing bay" so the floating controls don't overlap content
      [:div {:style {:height "3.5em"}}]]]))
