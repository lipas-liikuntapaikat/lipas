(ns lipas.ui.analysis.reachability.views
  (:require ["mdi-material-ui/MapMarkerDistance$default" :as MapMarkerDistance]
            ["rc-slider$default" :as Slider]
            [lipas.ui.analysis.reachability.events :as events]
            [lipas.ui.analysis.reachability.subs :as subs]
            [lipas.ui.charts :as charts]
            [lipas.ui.components :as lui]
            [lipas.ui.map.events :as map-events]
            ["@mui/material/CircularProgress$default" :as CircularProgress]
            ["@mui/material/Divider$default" :as Divider]
            ["@mui/material/GridLegacy$default" :as Grid]
            ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/IconButton$default" :as IconButton]
            ["@mui/material/Link$default" :as Link]
            ["@mui/material/List$default" :as List]
            ["@mui/material/ListItem$default" :as ListItem]
            ["@mui/material/ListItemText$default" :as ListItemText]
            ["@mui/material/Paper$default" :as Paper]
            ["@mui/material/Tab$default" :as Tab]
            ["@mui/material/Tabs$default" :as Tabs]
            ["@mui/material/Tooltip$default" :as Tooltip]
            ["@mui/material/Typography$default" :as Typography]
            [lipas.ui.utils :refer [<== ==>] :as utils]
            [reagent.core :as r]))

(defn sports-sites-tab
  [{:keys [tr]}]
  (let [sports-sites-list (<== [::subs/sports-sites-list])
        sports-sites-view (<== [::subs/sports-sites-view])
        sports-sites-chart-data (<== [::subs/sports-sites-chart-data-v2])
        selected-types (<== [:lipas.ui.search.subs/types-filter])
        labels (<== [::subs/population-labels])
        zones (<== [::subs/zones-by-selected-metric])
        metric (<== [::subs/selected-travel-metric])
        zone-colors (<== [::subs/zone-colors metric])]
    [:<>
     ;; Tools

     [:> Grid {:item true :xs 12}
      [lui/expansion-panel
       {:label (tr :analysis/filter-types)
        :default-expanded false}

       [:> Grid {:container true}
        ;; Types selector
        [:> Grid {:item true :xs 10}
         [lui/type-category-selector
          {:value selected-types
           :on-change #(==> [::events/set-type-codes-filter %])
           :label (tr :actions/select-types)}]]

        ;; Clear filter button
        (when (seq selected-types)
          [:> Grid {:item true :xs 2}
           [:> Tooltip {:title (tr :search/clear-filters)}
            [:> IconButton {:on-click #(==> [::events/set-type-codes-filter []])}
             [:> Icon {:color "secondary"} "filter_alt"]]]])]]]

     ;; Tabs
     [:> Grid {:item true :xs 12}
      [:> Tabs {:value sports-sites-view
                 :indicator-color "primary"
                 :text-color "inherit"
                 :variant "fullWidth"

                 :on-change #(==> [::events/select-sports-sites-view %2])}
       [:> Tab {:icon (r/as-element [:> Icon "list"]) :value "list"}]
       [:> Tab {:icon (r/as-element [:> Icon "analytics"]) :value "chart"}]]]

     ;; List view
     (when (= sports-sites-view "list")
       [:> Grid {:item true :xs 12}
        (into [:> List]
              (for [m sports-sites-list]
                [:> ListItem
                 {:divider true}
                 [:> ListItemText
                  {:primary (:name m)
                   :secondary (:display-val m)}]]))])

     ;; Chart view
     (when (= sports-sites-view "chart")
       [:> Grid {:item true :xs 12}
        [charts/sports-sites-bar-chart
         {:data sports-sites-chart-data
          :labels labels
          :zones zones
          :zone-colors zone-colors}]])]))

(defn travel-metric-selector
  [{:keys [tr]}]
  (let [metric (<== [::subs/selected-travel-metric])]
    [lui/select
     {:on-change #(==> [::events/select-travel-metric %])
      :value metric
      :items
      [{:label (tr :analysis/travel-time) :value :travel-time}
       {:label (tr :analysis/distance) :value :distance}]}]))

(defn travel-profile-selector
  [{:keys [tr]}]
  (let [profile (<== [::subs/selected-travel-profile])
        metric (<== [::subs/selected-travel-metric])]
    [:> Grid {:container true :spacing 2 :align-items "center"}

     ;; Direct
     [:> Grid {:item true}
      [:> Tooltip
       {:title (tr :analysis/direct)}
       [:span
        [:> IconButton
         {:on-click #(==> [::events/select-travel-profile :direct])
          :disabled (= metric :travel-time)
          :color (if (= profile :direct) "secondary" "default")}
         [:> MapMarkerDistance]]]]]

     ;; Car
     [:> Grid {:item true}
      [:> Tooltip
       {:title (tr :analysis/by-car)}
       [:> IconButton
        {:on-click #(==> [::events/select-travel-profile :car])
         :color (if (= profile :car) "secondary" "default")}
        [:> Icon "directions_car"]]]]

     ;; Bicycle
     [:> Grid {:item true}
      [:> Tooltip
       {:title (tr :analysis/by-bicycle)}
       [:> IconButton
        {:on-click #(==> [::events/select-travel-profile :bicycle])
         :color (if (= profile :bicycle) "secondary" "default")}
        [:> Icon "directions_bike"]]]]

     ;; Foot
     [:> Grid {:item true}
      [:> Tooltip
       {:title (tr :analysis/by-foot)}
       [:> IconButton
        {:on-click #(==> [::events/select-travel-profile :foot])
         :color (if (= profile :foot) "secondary" "default")}
        [:> Icon "directions_walk"]]]]

     ;; Distance vs travel time selector
     [:> Grid {:item true}
      [travel-metric-selector {:tr tr}]]

     ;; Settings button
     [:> Grid {:item true}
      [:> IconButton
       {:on-click #(==> [::events/select-analysis-tab "settings"])}
       [:> Icon "settings"]]]]))

(defn population-tab [{:keys [tr]}]
  (let [data (<== [::subs/population-chart-data-v3])
        labels (<== [::subs/population-labels])
        chart-mode (<== [::subs/population-chart-mode])]
    [:<>

     ;; Area chart
     (when (seq data)
       [:> Grid {:item true :xs 12}
        [charts/population-area-chart
         {:data data
          :labels (merge labels
                         {:naiset "Naiset"
                          :miehet "Miehet"
                          :vaesto "Yhteensä"})}]])

     ;; Cumulative / non-cumulative selector
     [:> Grid {:item true :xs 12 :style {:padding-left "1em"}}
      [lui/checkbox
       {:label (tr :analysis/cumulative-results)
        :on-change #(==> [::events/set-population-chart-mode %1])
        :value (= "cumulative" chart-mode)}]]

     ;; Tilastokeskus copyright notice (demographics data)
     [:> Grid {:item true :xs 12}
      [:> Typography {:variant "caption"}
       "© "
       (tr :map.demographics/copyright1)
       " "
       [:> Link
        {:href "https://bit.ly/2WzrRwf"
         :underline "always"}
        (tr :map.demographics/copyright2)]
       " "
       (tr :map.demographics/copyright3)
       " "
       [:> Link
        {:href "https://creativecommons.org/licenses/by/4.0/deed.fi"
         :underline "always"}
        "CC BY 4.0"]
       "."]
      [:> Typography {:variant "caption"}
       "© Tilastokeskus väestörakenne 2022"]]]))

(defn schools-tab [{:keys [tr]}]
  (let [schools-list (<== [::subs/schools-list])
        schools-view (<== [::subs/schools-view])
        schools-chart-data (<== [::subs/schools-chart-data-v2])
        labels (<== [::subs/population-labels])
        chart-mode (<== [::subs/schools-chart-mode])]
    [:<>

     [:> Grid {:item true :xs 12}
      [:> Tabs {:value schools-view
                 :indicator-color "primary"
                 :text-color "inherit"
                 :variant "fullWidth"

                 :on-change #(==> [::events/select-schools-view %2])}
       [:> Tab {:icon (r/as-element [:> Icon "list"]) :value "list"}]
       [:> Tab {:icon (r/as-element [:> Icon "analytics"]) :value "chart"}]]]

     #_[:> Grid {:item true :xs 12}
        [travel-profile-selector]]

     (when (= schools-view "list")
       [:> Grid {:item true :xs 12}
        (into [:> List]
              (for [m schools-list]
                [:> ListItem
                 {:divider true}
                 [:> ListItemText
                  {:primary (:name m)
                   :secondary (:display-val m)}]]))])

     (when (= schools-view "chart")
       [:> Grid {:item true :xs 12}
        [charts/schools-area-chart
         {:data schools-chart-data
          :labels labels}]

        ;; Cumulative / non-cumulative selector
        [:> Grid {:item true :xs 12 :style {:padding-left "1em"}}
         [lui/checkbox
          {:label (tr :analysis/cumulative-results)
           :on-change #(==> [::events/set-schools-chart-mode %1])
           :value (= "cumulative" chart-mode)}]]])]))

(defn zones-selector
  [{:keys [tr metric]}]
  (let [selector-marks (<== [::subs/zones-selector-marks metric])
        selector-colors (<== [::subs/zones-selector-colors metric])
        value (<== [::subs/zones-selector-value metric])
        max-v (<== [::subs/zones-selector-max metric])
        zones-count (<== [::subs/zones-count metric])
        zones-count-max (<== [::subs/zones-count-max metric])]

    (r/with-let [value* (r/atom value)]
      [:> Grid {:container true}
       ;; Slider
       ;; Rc-slider works better here, MUI does allow
       ;; multiple values, but probably not separate track colors
       ;; between the values ("korit")
       [:> Grid {:item true :xs 10 :style {:padding "1em"}}
        [:> Slider
         {:min 0
          :max max-v
          :pushable true
          :marks selector-marks
          ;; For multi-handle sliders in v11, pass an array of values
          :value @value*
          ;; Use the styles prop to style individual track segments
          :trackStyle (clj->js selector-colors)
          #_#_:styles {:tracks #_(clj->js selector-colors) "linear-gradient(to right ,#C8D4D9, #006190)"}
          :onAfterChange #(==> [::events/set-zones %1 metric])
          :onChange (fn [v]
                      (reset! value* v))
          :className "rc-slider-custom"
          ;; Enable range mode for multi-handle support
          :range true}]]

       ;; Zone count selector
       [:> Grid {:item true :xs 2}
        [:> Grid {:container true :justify-content "center"}
         [:> Grid {:item true}
          [lui/number-selector
           {:value zones-count
            :label (tr :analysis/zones)
            :on-change #(==> [::events/set-zones-count % metric value value*])
            :items (range 1 zones-count-max)}]]]]])))

(defn settings-tab
  [{:keys [tr]}]
  (let [show-analysis? (<== [:lipas.ui.map.subs/overlay-visible? :analysis])
        show-sports-sites? (<== [:lipas.ui.map.subs/overlay-visible? :vectors])
        show-population? (<== [:lipas.ui.map.subs/overlay-visible? :population])
        show-schools? (<== [:lipas.ui.map.subs/overlay-visible? :schools])]
    [:<>

     [:> Grid {:container true}

      ;; What's visible on map
      [:> Grid {:item true :xs 12}
       [lui/expansion-panel
        {:label (tr :analysis/settings-map)
         :default-expanded true}

        ;; Switches
        [:> Grid {:container true :style {:padding "1em"}}

         ;; Sports facilities
         [:> Grid {:item true}
          [lui/switch
           {:label (tr :sport/headline)
            :value show-sports-sites?
            :on-change #(==> [::map-events/set-overlay % :vectors])}]]

         ;; Analysis buffer
         [:> Grid {:item true}
          [lui/switch
           {:label (tr :analysis/analysis-buffer)
            :value show-analysis?
            :on-change #(==> [::map-events/set-overlay % :analysis])}]]

         ;; Population
         [:> Grid {:item true}
          [lui/switch
           {:label (tr :analysis/population)
            :value show-population?
            :on-change #(==> [::map-events/set-overlay % :population])}]]

         ;; Schools
         [:> Grid {:item true}
          [lui/switch
           {:label (tr :analysis/schools)
            :value show-schools?
            :on-change #(==> [::map-events/set-overlay % :schools])}]]]]]

      ;; Distances and travel times
      [:> Grid {:item true :xs 12}
       [lui/expansion-panel
        {:label (tr :analysis/settings-zones)
         :default-expanded true}
        [:> Grid {:container true :spacing 1}

         ;; Helper text
         [:> Grid {:item true :xs 12 :style {:margin-bottom "1em"}}
          [:> Paper {:style {:padding "1em" :background-color "#f5e642"}}
           [:> Typography {:variant "body1" :paragraph false}
            (tr :analysis/settings-help)]]]

         ;; Distance zones selector
         [:> Grid {:item true :xs 12}
          [:> Typography (tr :analysis/distance)]
          [zones-selector {:tr tr :metric :distance}]]

         ;; Travel time zones selector
         [:> Grid {:item true :xs 12 :style {:margin-top "2em"}}
          [:> Typography (tr :analysis/travel-time)]
          [zones-selector {:tr tr :metric :travel-time}]]]]]]]))

(defn analysis-view []
  (let [tr (<== [:lipas.ui.subs/translator])
        sites (<== [::subs/sports-sites-with-analysis])
        selected-site (<== [::subs/selected-sports-site])
        loading? (<== [::subs/loading?])
        selected-tab (<== [::subs/selected-analysis-tab])]

    [:> Grid
     {:container true
      :spacing 2
      :style {:padding (if selected-site "1em" "0.5em")}}

     ;; Helper text
     (when (empty? sites)
       [:> Grid
        {:item true :xs 12
         :container true
         :align-items "center"
         :spacing 1}

        [:> Grid {:item true}
         [:> Typography
          (tr :map.demographics/helper-text)
          [:> Link
           {:color "secondary"
            :on-click #(==> [::map-events/add-analysis-target])
            :sx #js {:cursor "pointer"}}
           (tr :general/here)]
          "."]]

        ;; Analysis tool description
        [:> Grid {:item true :xs 12 :style {:margin-top "1em"}}
         [:> Paper
          {:style
           {:padding "1em" :background-color "#f5e642"}}
          [:> Typography {:variant "body2" :paragraph true}
           "Info"]
          [:> Typography {:variant "body1" :paragraph true}
           (tr :analysis/description)]
          [:> Typography {:variant "body1" :paragraph true}
           (tr :analysis/description2)]
          [:> Typography {:variant "body1" :paragraph true}
           (tr :analysis/description3)]
          [:> Typography {:variant "body1" :paragraph true}
           (tr :analysis/description4)]]]])

     ;; Site selector
     (when (and (seq sites) (not loading?))
       [:> Grid
        {:item true
         :xs 12
         :container true
         :justify-content "space-between"
         :align-items "center"}
        [:> Grid {:item true
                   :container true
                   :align-items "center"
                   :spacing 1}
         [:> Grid {:item true}
          [lui/select
           {:items sites
            :value selected-site
            :style {:fontFamily "Lato, serif",
                    :fontWeight 700,
                    :fontSize "1.25rem",
                    :lineHeight 1.6,
                    :textTransform "uppercase"}
            :label-fn :site-name
            :value-fn :lipas-id
            :on-change #(==> [::events/select-sports-site %])}]]

         [:> Grid {:item true}
          [:> Tooltip {:title "Analysoi lisää kohteita klikkaamalla liikuntapaikkaa kartalla"}
           [:> IconButton
            {:on-click #()
             :color "secondary"
             :size "medium"}
            [:> Icon "add"]]]]]

        ;; Craete report button
        (when selected-site
          [:> Grid {:item true :style {:padding-right "1em"}}
           [lui/download-button
            {:size "small"
             :disabled loading?
             :on-click #(==> [::events/create-report])
             :label (tr :reports/download-as-excel)}]])])

     ;; Travel profile selector
     (when selected-site
       [:> Grid {:item true :xs 12}
        [travel-profile-selector {:tr tr}]])

     ;; Analysis tabs
     (when selected-site
       [:> Grid {:item true :xs 12}
        [:> Tabs {:value selected-tab
                   :on-change #(==> [::events/select-analysis-tab %2])
                   :style {:margin-bottom "1em"}
                   :indicator-color "secondary"
                   :text-color "secondary"}
         [:> Tab {:label (tr :sport/headline) :value :sports-sites}]
         [:> Tab {:label (tr :analysis/population) :value :population}]
         [:> Tab {:label (tr :analysis/schools) :value :schools}]
         [:> Tab {:label (tr :analysis/settings) :value :settings}]]])

     ;; No data available text
     #_(when (and selected-site (empty? data-bar))
         [:> Grid {:item true :xs 12}
          [:> Typography {:color "error"}
           (tr :error/no-data)]])

     (when selected-site
       [:> Grid {:item true :xs 12}
        [:> Divider]])

     (when loading?
       [:> Grid {:item true :xs 12}
        [:> CircularProgress]])

     (when (and selected-site (not loading?))
       (condp = selected-tab
         "settings" [settings-tab {:tr tr}]
         "sports-sites" [sports-sites-tab {:tr tr}]
         "population" [population-tab {:tr tr}]
         "schools" [schools-tab {:tr tr}]))

     ;; Small nest where floating controls can "land"
     [:> Grid {:item true :xs 12 :style {:height "70px"}}]]))
