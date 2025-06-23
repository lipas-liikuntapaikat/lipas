(ns lipas.ui.analysis.heatmap.views
  (:require [lipas.ui.analysis.heatmap.events :as events]
            [lipas.ui.analysis.heatmap.subs :as subs]
            [lipas.ui.components.selects :as selects]
            [lipas.ui.mui :as mui]
            [re-frame.core :as rf]
            [reagent.core :as r]))

(defn dimension-selector []
  (let [dimension @(rf/subscribe [::subs/dimension])]
    [mui/form-control {:full-width true :margin "normal"}
     [mui/input-label "Dimension"]
     [mui/select
      {:value dimension
       :on-change #(rf/dispatch [::events/update-dimension-and-refresh (keyword (.. % -target -value))])}
      [mui/menu-item {:value :density} "Facility Density"]
      [mui/menu-item {:value :area} "Total Area Coverage"]
      [mui/menu-item {:value :capacity} "Capacity Distribution"]
      [mui/menu-item {:value :type-distribution} "Facility Types"]
      [mui/menu-item {:value :year-round} "Year-round Availability"]
      [mui/menu-item {:value :lighting} "Facilities with Lighting"]
      [mui/menu-item {:value :activities} "Activity Distribution"]]]))

(defn weight-selector []
  (let [weight-by @(rf/subscribe [::subs/weight-by])
        dimension @(rf/subscribe [::subs/dimension])]
    (when (= dimension :density)
      [mui/form-control {:full-width true :margin "normal"}
       [mui/input-label "Weight By"]
       [mui/select
        {:value weight-by
         :on-change #(rf/dispatch [::events/update-weight-by-and-refresh (keyword (.. % -target -value))])}
        [mui/menu-item {:value :count} "Count"]
        [mui/menu-item {:value :area-m2} "Area (m²)"]
        [mui/menu-item {:value :route-length-km} "Route Length (km)"]]])))

(defn precision-selector []
  (let [precision @(rf/subscribe [::subs/precision])
        zoom @(rf/subscribe [:lipas.ui.map.subs/zoom])]
    [mui/form-control {:full-width true :margin "normal"}
     [mui/input-label "Geohash Precision"]
     [mui/select
      {:value (or precision "auto")
       :on-change #(let [val (.. % -target -value)]
                     (rf/dispatch [::events/update-precision-and-refresh
                                   (if (= val "auto") nil (js/parseInt val))]))}
      [mui/menu-item {:value "auto"} (str "Auto (current: " (cond
                                                              (<= zoom 5) 4
                                                              (<= zoom 7) 5
                                                              (<= zoom 9) 6
                                                              (<= zoom 11) 7
                                                              (<= zoom 13) 8
                                                              (<= zoom 15) 8
                                                              :else 8) ")")]
      [mui/menu-item {:value 1} "1 - Very Coarse (~5,000km)"]
      [mui/menu-item {:value 2} "2 - Coarse (~1,250km)"]
      [mui/menu-item {:value 3} "3 - (~156km)"]
      [mui/menu-item {:value 4} "4 - (~39km)"]
      [mui/menu-item {:value 5} "5 - (~5km)"]
      [mui/menu-item {:value 6} "6 - (~1.2km)"]
      [mui/menu-item {:value 7} "7 - (~153m)"]
      [mui/menu-item {:value 8} "8 - Fine (~38m)"]
      [mui/menu-item {:value 9} "9 - Very Fine (~5m)"]
      [mui/menu-item {:value 10} "10 - Ultra Fine (~1.2m)"]]
     [mui/form-helper-text "Higher precision = more detailed heatmap but slower performance"]]))

(defn bbox-filter-selector []
  (let [use-bbox-filter? @(rf/subscribe [::subs/use-bbox-filter?])]
    [mui/form-control {:full-width true :margin "normal"}
     [mui/form-control-label
      {:control (r/as-element
                 [mui/switch
                  {:checked use-bbox-filter?
                   :on-change #(rf/dispatch [::events/update-bbox-filter-and-refresh
                                             (.. % -target -checked)])}])
       :label "Filter by current map view"}]
     [mui/form-helper-text
      (if use-bbox-filter?
        "Analyzing data within current map bounds"
        "Analyzing data for whole Finland")]]))

;; Updated selectors using existing components
(defn type-filter []
  (let [tr @(rf/subscribe [:lipas.ui.subs/translator])
        filters @(rf/subscribe [::subs/filters])
        selected-types (get filters :type-codes [])]
    [mui/grid {:item true :xs 12 :style {:margin-top "8px"}}
     [selects/type-selector
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

(defn city-filter []
  (let [filters @(rf/subscribe [::subs/filters])
        selected-cities (get filters :city-codes [])]
    [mui/grid {:item true :xs 12 :style {:margin-top "8px"}}
     ;; TODO: Add city selector component when available
     [mui/typography {:variant "body2" :color "textSecondary"}
      "City filter (to be implemented)"]]))

(defn boolean-filters []
  (let [filters @(rf/subscribe [::subs/filters])]
    [mui/grid {:container true :spacing 1}
     [mui/grid {:item true :xs 12}
      [mui/form-control-label
       {:control (r/as-element
                  [mui/switch
                   {:checked (boolean (:year-round-only filters))
                    :on-change #(rf/dispatch [::events/update-filter-and-refresh
                                              :year-round-only
                                              (.. % -target -checked)])}])
        :label "Year-round facilities only"}]]
     [mui/grid {:item true :xs 12}
      [mui/form-control-label
       {:control (r/as-element
                  [mui/switch
                   {:checked (boolean (:retkikartta? filters))
                    :on-change #(rf/dispatch [::events/update-filter-and-refresh
                                              :retkikartta?
                                              (.. % -target -checked)])}])
        :label "Retkikartta sites only"}]]
     [mui/grid {:item true :xs 12}
      [mui/form-control-label
       {:control (r/as-element
                  [mui/switch
                   {:checked (boolean (:harrastuspassi? filters))
                    :on-change #(rf/dispatch [::events/update-filter-and-refresh
                                              :harrastuspassi?
                                              (.. % -target -checked)])}])
        :label "Harrastuspassi sites only"}]]
     [mui/grid {:item true :xs 12}
      [mui/form-control-label
       {:control (r/as-element
                  [mui/switch
                   {:checked (boolean (:school-use? filters))
                    :on-change #(rf/dispatch [::events/update-filter-and-refresh
                                              :school-use?
                                              (.. % -target -checked)])}])
        :label "School use only"}]]]))

(defn visual-controls []
  (let [visual @(rf/subscribe [::subs/visual-params])]
    [mui/grid {:container true :spacing 2}
     [mui/grid {:item true :xs 12}
      [mui/typography {:variant "h6" :gutterBottom true} "Visual Settings"]]

     [mui/grid {:item true :xs 12}
      [mui/typography {:gutterBottom true} (str "Radius: " (:radius visual) "px")]
      [mui/slider
       {:value (:radius visual)
        :min 5
        :max 100
        :on-change #(rf/dispatch [::events/update-visual-param :radius (.-value (.-target %))])}]]

     [mui/grid {:item true :xs 12}
      [mui/typography {:gutterBottom true} (str "Blur: " (:blur visual) "px")]
      [mui/slider
       {:value (:blur visual)
        :min 0
        :max 50
        :on-change #(rf/dispatch [::events/update-visual-param :blur (.-value (.-target %))])}]]

     [mui/grid {:item true :xs 12}
      [mui/typography {:gutterBottom true} (str "Opacity: " (:opacity visual))]
      [mui/slider
       {:value (:opacity visual)
        :min 0.1
        :max 1
        :step 0.1
        :on-change #(rf/dispatch [::events/update-visual-param :opacity (.-value (.-target %))])}]]

     [mui/grid {:item true :xs 12}
      [mui/form-control {:full-width true :margin "normal"}
       [mui/input-label "Color Gradient"]
       [mui/select
        {:value (:gradient visual)
         :on-change #(rf/dispatch [::events/update-visual-param :gradient (keyword (.. % -target -value))])}
        [mui/menu-item {:value :default} "Default (Blue to Red)"]
        [mui/menu-item {:value :cool} "Cool (Purple to White)"]
        [mui/menu-item {:value :warm} "Warm (Yellow to Red)"]
        [mui/menu-item {:value :grayscale} "Grayscale"]
        [mui/menu-item {:value :accessibility} "Accessibility (Red to Green)"]]]]

     [mui/grid {:item true :xs 12}
      [mui/form-control {:full-width true :margin "normal"}
       [mui/input-label "Weight Function"]
       [mui/select
        {:value (:weight-fn visual)
         :on-change #(rf/dispatch [::events/update-visual-param :weight-fn (keyword (.. % -target -value))])}
        [mui/menu-item {:value :linear} "Linear"]
        [mui/menu-item {:value :logarithmic} "Logarithmic"]
        [mui/menu-item {:value :exponential} "Exponential"]
        [mui/menu-item {:value :sqrt} "Square Root"]]]]]))

(defn filters-panel []
  [mui/expansion-panel {:default-expanded true}
   [mui/expansion-panel-summary {:expand-icon (r/as-element [mui/icon "expand_more"])}
    [mui/typography "Filters"]]
   [mui/expansion-panel-details
    [mui/grid {:container true :spacing 2}
     [type-filter]
     [status-filter]
     [owner-filter]
     [admin-filter]
     [year-range-filter]
     ;; Boolean filters hidden for now. Maybe revealed later
     #_[mui/grid {:item true :xs 12}
        [boolean-filters]]]]])

(defn heatmap-controls []
  (let [loading? @(rf/subscribe [::subs/loading?])
        error @(rf/subscribe [::subs/error])]
    [mui/stack {:direction "column" :spacing 2}
     [mui/paper {:style {:padding "0.5em"
                         :overflow-y "auto"}}
      [mui/typography {:variant "h5" :gutterBottom true} "Heatmap Analysis"]

      (when error
        [mui/paper {:style {:margin-bottom "16px" :padding "8px" :background-color "#f44336" :color "white"}}
         [mui/typography error]])

      (when loading?
        [mui/linear-progress {:style {:margin-bottom "16px"}}])

      [dimension-selector]
      [weight-selector]
      [precision-selector]
      [bbox-filter-selector]

      [filters-panel]

      [mui/expansion-panel
       [mui/expansion-panel-summary {:expand-icon (r/as-element [mui/icon "expand_more"])}
        [mui/typography "Visual Controls"]]
       [mui/expansion-panel-details
        [visual-controls]]]

      ;; Add a helpful note for users
      [mui/typography {:variant "body2" :color "textSecondary" :style {:margin-top "16px" :text-align "center"}}
       "Heatmap updates automatically when you change any filter or setting"]

      ;; "Landing bay" so the floating controls don't overlap content
      [:div {:style {:height "3.5em"}}]]]))
