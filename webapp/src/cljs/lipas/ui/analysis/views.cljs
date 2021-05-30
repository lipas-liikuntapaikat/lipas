(ns lipas.ui.analysis.views
  (:require
   [clojure.string :as string]
   [goog.date.duration :as gduration]
   [lipas.ui.analysis.events :as events]
   [lipas.ui.charts :as charts]
   [lipas.ui.components :as lui]
   [lipas.ui.map.events :as map-events]
   [lipas.ui.map.subs :as map-subs]
   [lipas.ui.analysis.subs :as subs]
   [lipas.ui.mui :as mui]
   [lipas.ui.utils :refer [<== ==>] :as utils]
   [reagent.core :as r]))

(def ^js Slider (.-Slider js/rcslider))
(def ^js RangeSlider (.createSliderWithTooltip Slider (.-Range Slider)))

(defn sports-sites-tab
  [{:keys [tr]}]
  (let [sports-sites-list                (<== [::subs/sports-sites-list])
        sports-sites-view                (<== [::subs/sports-sites-view])
        sports-sites-chart-data          (<== [::subs/sports-sites-chart-data-v2])
        #_#_sports-sites-area-chart-data (<== [::subs/sports-sites-area-chart-data])
        selected-types                   (<== [:lipas.ui.search.subs/types-filter])
        labels                           (<== [::subs/population-labels])
        zones                            (<== [::subs/zones-by-selected-metric])
        zone-colors                      (<== [::subs/zone-colors])]
    [:<>

     ;; Category filter
     [mui/grid {:item true :xs 12}
      [lui/type-category-selector
       {:value     selected-types
        :on-change #(==> [::events/set-type-codes-filter %])
        :label     "Filter types"}]]

     ;; Tabs
     [mui/grid {:item :true :xs 12}
      [mui/tabs {:value          sports-sites-view
                 :indicatorColor "primary"
                 :variant        "fullWidth"

                 :on-change #(==> [::events/select-sports-sites-view %2])}
       [mui/tab {:icon (r/as-element [mui/icon "list"]) :value "list"}]
       [mui/tab {:icon (r/as-element [mui/icon "analytics"]) :value "chart"}]]]

     ;; List view
     (when (= sports-sites-view "list")
       [mui/grid {:item :true :xs 12}
        (into [mui/list]
              (for [m sports-sites-list]
                [mui/list-item
                 {:divider true}
                 [mui/list-item-text
                  {:primary   (:name m)
                   :secondary (:display-val m)}]]))])

     ;; Chart view
     (when (= sports-sites-view "chart")
       [mui/grid {:item :true :xs 12}
        [charts/sports-sites-bar-chart
         {:data        sports-sites-chart-data
          :labels      labels
          :zones       zones
          :zone-colors zone-colors}]])]))

(defn travel-profile-selector
  []
  (let [profile (<== [::subs/selected-travel-profile])
        metric  (<== [::subs/selected-travel-metric])]
    [mui/grid {:container true :spacing 2 :align-items "center"}
     ;; Direct
     [mui/grid {:item true}
      [mui/icon-button
       {:on-click #(==> [::events/select-travel-profile :direct])
        :color    (if (= profile :direct) "secondary" "default")}
       [:> js/materialIcons.Helicopter]]]

     ;; Car
     [mui/grid {:item true}
      [mui/icon-button
       {:on-click #(==> [::events/select-travel-profile :car])
        :color    (if (= profile :car) "secondary" "default")}
       [mui/icon "directions_car"]]]

     ;; Bicycle
     [mui/grid {:item true}
      [mui/icon-button
       {:on-click #(==> [::events/select-travel-profile :bicycle])
        :color    (if (= profile :bicycle) "secondary" "default")}
       [mui/icon "directions_bike"]]]

     ;; Walk
     [mui/grid {:item true}
      [mui/icon-button
       {:on-click #(==> [::events/select-travel-profile :foot])
        :color    (if (= profile :foot) "secondary" "default")}
       [mui/icon "directions_walk"]]]

     ;; Distance vs travel time
     [mui/grid {:item true}
      [lui/select
       {:on-change #(==> [::events/select-travel-metric %])
        :value     metric
        :items
        [{:label "Travel time" :value :travel-time}
         {:label "Distance" :value :distance}]}]]]))

(defn population-v2-tab [{:keys [tr]}]
  (let [data   (<== [::subs/population-chart-data-v2])
        labels (<== [::subs/population-labels])]
    [:<>

     #_[mui/grid {:item true :xs 12}
      [travel-profile-selector]]

     ;; Area chart
     (when (seq data)
       [mui/grid {:item true :xs 12}
        [charts/population-area-chart-v2
         {:data   data
          :labels (merge labels
                         {:naiset "Naiset"
                          :miehet "Miehet"
                          :vaesto "Yhteensä"})}]])

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
       "."]]]))

(defn population-tab [{:keys [tr]}]
  (let [data-bar  (<== [::subs/population-bar-chart-data])
        data-area (<== [::subs/population-area-chart-data])
        labels    (<== [::subs/population-labels])]
    [:<>

     ;; Bar chart
     (when (seq data-bar)
       [mui/grid {:item true :xs 12}
        [charts/population-bar-chart
         {:data   data-bar
          :labels labels}]])

     ;; Area chart
     (when (seq data-area)
       [mui/grid {:item true :xs 12}
        [charts/population-area-chart
         {:data   data-area
          :labels labels}]])

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
       "."]]]))

(defn schools-tab [{:keys [tr]}]
  (let [schools-list       (<== [::subs/schools-list])
        schools-view       (<== [::subs/schools-view])
        schools-chart-data (<== [::subs/schools-chart-data-v2])
        labels             (<== [::subs/population-labels])]
    [:<>

     [mui/grid {:item true :xs 12}
      [mui/tabs {:value          schools-view
                 :indicatorColor "primary"
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
          :labels labels}]])]))

(defn zones-selector
  []
  (let [selector-marks  (<== [::subs/zones-selector-marks])
        selector-colors (<== [::subs/zones-selector-colors])
        metric          (<== [::subs/selected-travel-metric])
        value           (<== [::subs/zones-selector-value])
        max-v           (<== [::subs/zones-selector-max])
        step            (<== [::subs/zones-selector-step])
        zones-count     (<== [::subs/zones-count])]

    (r/with-let [value* (r/atom value)
                 metric* (r/atom metric)]
      (let [_ (when (not= @metric* metric)
                (reset! value* value)
                (reset! metric* metric))]
        [mui/grid {:container true}
         [mui/grid {:item true :xs 10 :style {:padding "1em"}}

          [:> RangeSlider
           {:key             metric
            :step            step
            :min             0
            :max             max-v
            :pushable        step
            :marks           selector-marks
            :trackStyle      selector-colors
            :value           @value*
            :on-after-change #(==> [::events/set-zones %1 metric])
            :on-change       #(reset! value* %1)
            :style           {:fontf-family "Lato, sans-serif"}
            :tipFormatter    (fn [n]
                               (if (= :distance metric)
                                 (str n "km")
                                 (gduration/format (* 60 1000 n))))}]]

         ;; Zone count selector
         [mui/grid {:item true :xs 2}
          [mui/grid {:container true :justify "center"}
           [mui/grid {:item true}
            [lui/number-selector
             {:value     zones-count
              :label     "Zones"
              :on-change #(==> [::events/set-zones-count % metric value value*])
              :items     (range 1 10)}]]]]]))))

(defn analysis-view []
  (let [tr            (<== [:lipas.ui.subs/translator])
        selected-site (<== [::subs/selected-analysis-center])

        show-analysis?     (<== [::map-subs/overlay-visible? :analysis])
        show-sports-sites? (<== [::map-subs/overlay-visible? :vectors])
        show-population?   (<== [::map-subs/overlay-visible? :population])
        show-schools?      (<== [::map-subs/overlay-visible? :schools])

        loading? (<== [::subs/loading?])

        selected-tab (<== [::subs/selected-analysis-tab])]

    [mui/grid {:container true :spacing 16 :style {:padding "0.5em"}}

     ;; Header and close button
     [mui/grid {:item true :container true :justify "space-between"}
      [mui/grid {:item true}
       [mui/typography {:variant "h4"}
        "Analysis"
        #_(tr :map.analysis/headline)]]
      [mui/grid {:item true}
       [mui/icon-button {:on-click #(==> [::map-events/hide-analysis])}
        [mui/icon "close"]]]]

     ;; Helper text
     (when (empty? selected-site)
       [mui/grid {:item true :xs 12 :container true :align-items "center"}
        [mui/grid {:item true}
         [mui/typography
          (tr :map.demographics/helper-text)
          " "
          [mui/link
           {:color    "secondary"
            :href     "javascript:;"
            :variant  "body2"
            :on-click #(==> [::events/show-near-by-analysis])}
           (tr :general/here)]
          "."]]])


     (when selected-site
       [:<>

        ;; Site name
        [mui/grid
         {:item        true
          :xs          12
          :container   true
          :justify     "space-between"
          :align-items "center"}
         [mui/grid {:item true}
          [mui/grid {:container true :align-items "center"}
           [mui/grid {:item true}
            [mui/icon "location_on"]]
           [mui/grid {:item true}
            [mui/typography selected-site]]]]
         [mui/grid {:item true :style {:padding-right "1em"}}
          [lui/download-button
           {:size     "small"
            :on-click #(==> [::events/create-report])
            :label    "Export report"}]]]

        ;; Switches
        [mui/grid {:container true :style {:padding "1em"}}

         [mui/grid {:item true}
          [lui/switch
           {:label     "Sports facilities"
            :value     show-sports-sites?
            :on-change #(==> [::map-events/set-overlay % :vectors])}]]

         [mui/grid {:item true}
          [lui/switch
           {:label     "Analysis buffer"
            :value     show-analysis?
            :on-change #(==> [::map-events/set-overlay % :analysis])}]]

         [mui/grid {:item true}
          [lui/switch
           {:label     "Population grid"
            :value     show-population?
            :on-change #(==> [::map-events/set-overlay % :population])}]]

         [mui/grid {:item true}
          [lui/switch
           {:label     "Schools"
            :value     show-schools?
            :on-change #(==> [::map-events/set-overlay % :schools])}]]]

        ;; Zones selector
        [mui/grid {:item true :xs 12}
         [zones-selector]]

        ;; Travel profile selector
        [mui/grid {:item true :xs 12}
         [travel-profile-selector]]

        ;; No data available text
        #_(when (and selected-site (empty? data-bar))
            [mui/grid {:item true :xs 12}
             [mui/typography {:color "error"}
              (tr :error/no-data)]])

        [mui/grid {:item true :xs 12}
         [mui/divider]]

        ;; Analysis tabs
        [mui/grid {:item true :xs 12}
         [mui/tabs {:value      selected-tab
                    :on-change  #(==> [::events/select-analysis-tab %2])
                    :style      {:margin-bottom "1em"}
                    :text-color "secondary"}
          [mui/tab {:label "Sports sites" :value :sports-sites}]
          [mui/tab {:label "Population" :value :population}]
          [mui/tab {:label "Schools" :value :schools}]]]

        (if loading?
          [mui/grid {:item true :xs 12}
           [mui/circular-progress]]

          [:<>

           ;; Sports-sites tab
           (when (= selected-tab "sports-sites")
             [sports-sites-tab {:tr tr}])

           ;; Population tab
           (when (= selected-tab "population")
             [population-v2-tab {:tr tr}])

           ;; Schools tab
           (when (= selected-tab "schools")
             [schools-tab {:tr tr}])

           ])

        ])

     ;; Small nest where floating controls can "land"
     [mui/grid {:item true :xs 12 :style {:height "70px"}}]]))
