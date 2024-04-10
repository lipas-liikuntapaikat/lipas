(ns lipas.ui.analysis.reachability.views
  (:require
   ["rc-slider" :as Slider]
   ["mdi-material-ui/MapMarkerDistance$default" :as MapMarkerDistance]
   [lipas.ui.analysis.reachability.events :as events]
   [lipas.ui.analysis.reachability.subs :as subs]
   [lipas.ui.charts :as charts]
   [lipas.ui.components :as lui]
   [lipas.ui.map.events :as map-events]
   [lipas.ui.mui :as mui]
   [lipas.ui.utils :refer [<== ==>] :as utils]
   [reagent.core :as r]))

#_(def ^js Slider (.-Slider js/rcslider))
(def ^js RangeSlider (.createSliderWithTooltip Slider (.-Range Slider)))

(defn sports-sites-tab
  [{:keys [tr]}]
  (let [sports-sites-list       (<== [::subs/sports-sites-list])
        sports-sites-view       (<== [::subs/sports-sites-view])
        sports-sites-chart-data (<== [::subs/sports-sites-chart-data-v2])
        selected-types          (<== [:lipas.ui.search.subs/types-filter])
        labels                  (<== [::subs/population-labels])
        zones                   (<== [::subs/zones-by-selected-metric])
        metric                  (<== [::subs/selected-travel-metric])
        zone-colors             (<== [::subs/zone-colors metric])]
    [:<>

     ;; Tools

     [mui/grid {:item true :xs 12}
      [lui/expansion-panel
       {:label            (tr :analysis/filter-types)
        :default-expanded false}

       ;; Types selector
       [mui/grid {:item true :xs 10}
        [lui/type-category-selector
         {:value     selected-types
          :on-change #(==> [::events/set-type-codes-filter %])
          :label     (tr :actions/select-types)}]]

       ;; Clear filter button
       (when (seq selected-types)
         [mui/grid {:item true :xs 2}
          [mui/tooltip {:title (tr :search/clear-filters)}
           ;; FIXME: :size "large"
           [mui/icon-button {:on-click #(==> [::events/set-type-codes-filter []])}
            [mui/icon {:color "secondary"} "filter_alt"]]]])]]

     ;; Tabs
     [mui/grid {:item true :xs 12}
      [mui/tabs {:value          sports-sites-view
                 :indicator-color "primary"
                 :text-color     "inherit"
                 :variant        "fullWidth"

                 :on-change #(==> [::events/select-sports-sites-view %2])}
       [mui/tab {:icon (r/as-element [mui/icon "list"]) :value "list"}]
       [mui/tab {:icon (r/as-element [mui/icon "analytics"]) :value "chart"}]]]

     ;; List view
     (when (= sports-sites-view "list")
       [mui/grid {:item true :xs 12}
        (into [mui/list]
              (for [m sports-sites-list]
                [mui/list-item
                 {:divider true}
                 [mui/list-item-text
                  {:primary   (:name m)
                   :secondary (:display-val m)}]]))])

     ;; Chart view
     (when (= sports-sites-view "chart")
       [mui/grid {:item true :xs 12}
        [charts/sports-sites-bar-chart
         {:data        sports-sites-chart-data
          :labels      labels
          :zones       zones
          :zone-colors zone-colors}]])]))

(defn travel-metric-selector
  [{:keys [tr]}]
  (let [metric (<== [::subs/selected-travel-metric])]
    [lui/select
     {:on-change #(==> [::events/select-travel-metric %])
      :value     metric
      :items
      [{:label (tr :analysis/travel-time) :value :travel-time}
       {:label (tr :analysis/distance) :value :distance}]}]))

(defn travel-profile-selector
  [{:keys [tr]}]
  (let [profile (<== [::subs/selected-travel-profile])
        metric  (<== [::subs/selected-travel-metric])]
    [mui/grid {:container true :spacing 2 :align-items "center"}

     ;; Direct
     [mui/grid {:item true}
      [mui/tooltip
       {:title (tr :analysis/direct)}
       [:span
        [mui/icon-button
         {:on-click #(==> [::events/select-travel-profile :direct])
          :disabled (= metric :travel-time)
          :color    (if (= profile :direct) "secondary" "default")}
         [:> MapMarkerDistance]]]]]

     ;; Car
     [mui/grid {:item true}
      [mui/tooltip
       {:title (tr :analysis/by-car)}
       [mui/icon-button
        {:on-click #(==> [::events/select-travel-profile :car])
         :color    (if (= profile :car) "secondary" "default")}
        [mui/icon "directions_car"]]]]

     ;; Bicycle
     [mui/grid {:item true}
      [mui/tooltip
       {:title (tr :analysis/by-bicycle)}
       [mui/icon-button
        {:on-click #(==> [::events/select-travel-profile :bicycle])
         :color    (if (= profile :bicycle) "secondary" "default")}
        [mui/icon "directions_bike"]]]]

     ;; Foot
     [mui/grid {:item true}
      [mui/tooltip
       {:title (tr :analysis/by-foot)}
       [mui/icon-button
        {:on-click #(==> [::events/select-travel-profile :foot])
         :color    (if (= profile :foot) "secondary" "default")}
        [mui/icon "directions_walk"]]]]

     ;; Distance vs travel time selector
     [mui/grid {:item true}
      [travel-metric-selector {:tr tr}]]

     ;; Settings button
     [mui/grid {:item true}
      [mui/icon-button
       {:on-click #(==> [::events/select-analysis-tab "settings"])}
       [mui/icon "settings"]]]]))

(defn population-tab [{:keys [tr]}]
  (let [data       (<== [::subs/population-chart-data-v3])
        labels     (<== [::subs/population-labels])
        chart-mode (<== [::subs/population-chart-mode])]
    [:<>

     ;; Area chart
     (when (seq data)
       [mui/grid {:item true :xs 12}
        [charts/population-area-chart
         {:data   data
          :labels (merge labels
                         {:naiset "Naiset"
                          :miehet "Miehet"
                          :vaesto "Yhteensä"})}]])

     ;; Cumulative / non-cumulative selector
     [mui/grid {:item true :xs 12 :style {:padding-left "1em"}}
      [lui/checkbox
       {:label     "Kumulatiiviset tulokset"
        :on-change #(==> [::events/set-population-chart-mode %1])
        :value     (= "cumulative" chart-mode)}]]

     ;; Tilastokeskus copyright notice (demographics data)
     [mui/grid {:item true :xs 12}
      [mui/typography {:variant "caption"}
       "© "
       (tr :map.demographics/copyright1)
       " "
       [mui/link
        {:href      "https://bit.ly/2WzrRwf"
         :underline "always"}
        (tr :map.demographics/copyright2)]
       " "
       (tr :map.demographics/copyright3)
       " "
       [mui/link
        {:href      "https://creativecommons.org/licenses/by/4.0/deed.fi"
         :underline "always"}
        "CC BY 4.0"]
       "."]
      [mui/typography {:variant "caption"}
       "© Tilastokeskus väestörakenne 2022"]]]))

(defn schools-tab [{:keys [tr]}]
  (let [schools-list       (<== [::subs/schools-list])
        schools-view       (<== [::subs/schools-view])
        schools-chart-data (<== [::subs/schools-chart-data-v2])
        labels             (<== [::subs/population-labels])
        chart-mode         (<== [::subs/schools-chart-mode])]
    [:<>

     [mui/grid {:item true :xs 12}
      [mui/tabs {:value          schools-view
                 :indicator-color "primary"
                 :text-color     "inherit"
                 :variant        "fullWidth"

                 :on-change #(==> [::events/select-schools-view %2])}
       [mui/tab {:icon (r/as-element [mui/icon "list"]) :value "list"}]
       [mui/tab {:icon (r/as-element [mui/icon "analytics"]) :value "chart"}]]]

     #_[mui/grid {:item true :xs 12}
        [travel-profile-selector]]

     (when (= schools-view "list")
       [mui/grid {:item true :xs 12}
        (into [mui/list]
              (for [m schools-list]
                [mui/list-item
                 {:divider true}
                 [mui/list-item-text
                  {:primary   (:name m)
                   :secondary (:display-val m)}]]))])

     (when (= schools-view "chart")
       [mui/grid {:item true :xs 12}
        [charts/schools-area-chart
         {:data   schools-chart-data
          :labels labels}]

        ;; Cumulative / non-cumulative selector
        [mui/grid {:item true :xs 12 :style {:padding-left "1em"}}
         [lui/checkbox
          {:label     "Kumulatiiviset tulokset"
           :on-change #(==> [::events/set-schools-chart-mode %1])
           :value     (= "cumulative" chart-mode)}]]])]))

(defn zones-selector
  [{:keys [tr metric]}]
  (let [selector-marks  (<== [::subs/zones-selector-marks metric])
        selector-colors (<== [::subs/zones-selector-colors metric])
        value           (<== [::subs/zones-selector-value metric])
        max-v           (<== [::subs/zones-selector-max metric])
        zones-count     (<== [::subs/zones-count metric])
        zones-count-max (<== [::subs/zones-count-max metric])]
    (r/with-let [value* (r/atom value)]

      [mui/grid {:container true}

       ;; Slider
       [mui/grid {:item true :xs 10 :style {:padding "1em"}}
        [:> RangeSlider
         {:min             0
          :max             max-v
          :pushable        true
          :marks           selector-marks
          :trackStyle      selector-colors
          :value           @value*
          :on-after-change #(==> [::events/set-zones %1 metric])
          :on-change       (fn [v]
                             (reset! value* v))
          :style           {:font-family "Lato, sans-serif"}
          :tipFormatter    (fn [idx] (get selector-marks idx))}]]

       ;; Zone count selector
       [mui/grid {:item true :xs 2}
        [mui/grid {:container true :justify-content "center"}
         [mui/grid {:item true}
          [lui/number-selector
           {:value     zones-count
            :label     (tr :analysis/zones)
            :on-change #(==> [::events/set-zones-count % metric value value*])
            :items     (range 1 zones-count-max)}]]]]])))

(defn settings-tab
  [{:keys [tr]}]
  (let [show-analysis?     (<== [:lipas.ui.map.subs/overlay-visible? :analysis])
        show-sports-sites? (<== [:lipas.ui.map.subs/overlay-visible? :vectors])
        show-population?   (<== [:lipas.ui.map.subs/overlay-visible? :population])
        show-schools?      (<== [:lipas.ui.map.subs/overlay-visible? :schools])]
    [:<>

     [mui/grid {:container true}

      ;; What's visible on map
      [mui/grid {:item true :xs 12}
       [lui/expansion-panel
        {:label            (tr :analysis/settings-map)
         :default-expanded true}

        ;; Switches
        [mui/grid {:container true :style {:padding "1em"}}

         ;; Sports facilities
         [mui/grid {:item true}
          [lui/switch
           {:label     (tr :sport/headline)
            :value     show-sports-sites?
            :on-change #(==> [::map-events/set-overlay % :vectors])}]]

         ;; Analysis buffer
         [mui/grid {:item true}
          [lui/switch
           {:label     (tr :analysis/analysis-buffer)
            :value     show-analysis?
            :on-change #(==> [::map-events/set-overlay % :analysis])}]]

         ;; Population
         [mui/grid {:item true}
          [lui/switch
           {:label     (tr :analysis/population)
            :value     show-population?
            :on-change #(==> [::map-events/set-overlay % :population])}]]

         ;; Schools
         [mui/grid {:item true}
          [lui/switch
           {:label     (tr :analysis/schools)
            :value     show-schools?
            :on-change #(==> [::map-events/set-overlay % :schools])}]]]]]

      ;; Distances and travel times
      [mui/grid {:item true :xs 12}
       [lui/expansion-panel
        {:label            (tr :analysis/settings-zones)
         :default-expanded true}
        [mui/grid {:container true :spacing 1}

         ;; Helper text
         [mui/grid {:item true :xs 12 :style {:margin-bottom "1em"}}
          [mui/paper {:style {:padding "1em" :background-color "#f5e642"}}
           [mui/typography {:variant "body1" :paragraph false}
            (tr :analysis/settings-help)]]]

         ;; Distance zones selector
         [mui/grid {:item true :xs 12}
          [mui/typography (tr :analysis/distance)]
          [zones-selector {:tr tr :metric :distance}]]

         ;; Travel time zones selector
         [mui/grid {:item true :xs 12 :style {:margin-top "2em"}}
          [mui/typography (tr :analysis/travel-time)]
          [zones-selector {:tr tr :metric :travel-time}]]]]]]]))

(defn analysis-view []
  (let [tr                 (<== [:lipas.ui.subs/translator])
        sites              (<== [::subs/sports-sites-with-analysis])
        selected-site      (<== [::subs/selected-sports-site])
        loading?           (<== [::subs/loading?])
        selected-tab       (<== [::subs/selected-analysis-tab])]

    [mui/grid
     {:container true
      :spacing   2
      :style     {:padding (if selected-site "1em" "0.5em")}}

     ;; Helper text
     (when (empty? sites)
       [mui/grid
        {:item        true :xs 12
         :container   true
         :align-items "center"
         :spacing     1}

        [mui/grid {:item true}
         [mui/typography
          (tr :map.demographics/helper-text)
          [mui/link
           {:color    "secondary"
            :on-click #(==> [::map-events/add-analysis-target])}
           (tr :general/here)]
          "."]]

        ;; Analysis tool description
        [mui/grid {:item true :xs 12 :style {:margin-top "1em"}}
         [mui/paper
          {:style
           {:padding "1em" :background-color "#f5e642"}}
          [mui/typography {:variant "body2" :paragraph true}
           "Info"]
          [mui/typography {:variant "body1" :paragraph true}
           (tr :analysis/description)]
          [mui/typography {:variant "body1" :paragraph true}
           (tr :analysis/description2)]
          [mui/typography {:variant "body1" :paragraph true}
           (tr :analysis/description3)]
          [mui/typography {:variant "body1" :paragraph true}
           (tr :analysis/description4)]]]])

     ;; Site selector
     (when (and (seq sites) (not loading?))
       [mui/grid
        {:item            true
         :xs              12
         :container       true
         :justify-content "space-between"
         :align-items     "center"}
        [mui/grid {:item true}

         [mui/grid {:container true :align-items "center" :spacing 1}
          [mui/grid {:item true}
           [lui/select
            {:items     sites
             :value     selected-site
             :style     {:fontFamily    "Lato, serif",
                         :fontWeight    700,
                         :fontSize      "1.25rem",
                         :lineHeight    1.6,
                         :textTransform "uppercase"}
             :label-fn  :site-name
             :value-fn  :lipas-id
             :on-change #(==> [::events/select-sports-site %])}]]

          [mui/grid {:item true}
           [mui/tooltip {:title "Analysoi lisää kohteita klikkaamalla liikuntapaikkaa kartalla"}
            [mui/icon-button
             {:on-click #()
              :color    "secondary"
              :size     "medium"}
             [mui/icon "add"]]]]]]

        ;; Craete report button
        (when selected-site
          [mui/grid {:item true :style {:padding-right "1em"}}
           [lui/download-button
            {:size     "small"
             :disabled loading?
             :on-click #(==> [::events/create-report])
             :label    (tr :reports/download-as-excel)}]])])

     ;; Travel profile selector
     (when selected-site
       [mui/grid {:item true :xs 12}
        [travel-profile-selector {:tr tr}]])

     ;; Analysis tabs
     (when selected-site
       [mui/grid {:item true :xs 12}
        [mui/tabs {:value      selected-tab
                   :on-change  #(==> [::events/select-analysis-tab %2])
                   :style      {:margin-bottom "1em"}
                   :indicator-color "secondary"
                   :text-color "secondary"}
         [mui/tab {:label (tr :sport/headline) :value :sports-sites}]
         [mui/tab {:label (tr :analysis/population) :value :population}]
         [mui/tab {:label (tr :analysis/schools) :value :schools}]
         [mui/tab {:label (tr :analysis/settings) :value :settings}]]])

     ;; No data available text
     #_(when (and selected-site (empty? data-bar))
         [mui/grid {:item true :xs 12}
          [mui/typography {:color "error"}
           (tr :error/no-data)]])

     (when selected-site
       [mui/grid {:item true :xs 12}
        [mui/divider]])

     (when loading?
       [mui/grid {:item true :xs 12}
        [mui/circular-progress]])

     (when (and selected-site (not loading?))
       (condp = selected-tab
         "settings"     [settings-tab {:tr tr}]
         "sports-sites" [sports-sites-tab {:tr tr}]
         "population"   [population-tab {:tr tr}]
         "schools"      [schools-tab {:tr tr}]))

     ;; Small nest where floating controls can "land"
     [mui/grid {:item true :xs 12 :style {:height "70px"}}]]))
