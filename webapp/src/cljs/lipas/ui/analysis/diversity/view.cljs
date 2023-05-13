(ns lipas.ui.analysis.diversity.view
  (:require
   ["rc-slider$default" :as Slider]
   ["recharts/es6/cartesian/Bar" :refer [Bar]]
   ["recharts/es6/cartesian/CartesianGrid" :refer [CartesianGrid]]
   ["recharts/es6/cartesian/XAxis" :refer [XAxis]]
   ["recharts/es6/cartesian/YAxis" :refer [YAxis]]
   ["recharts/es6/chart/BarChart" :refer [BarChart]]
   ["recharts/es6/component/Cell" :refer [Cell]]
   ["recharts/es6/component/Legend" :refer [Legend]]
   ["recharts/es6/component/ResponsiveContainer" :refer [ResponsiveContainer]]
   ["recharts/es6/component/Tooltip" :refer [Tooltip]]
   [clojure.string :as str]
   [goog.color :as gcolor]
   [goog.object :as gobj]
   [lipas.ui.analysis.diversity.events :as events]
   [lipas.ui.analysis.diversity.subs :as subs]
   [lipas.ui.charts :as charts]
   [lipas.ui.components :as lui]
   [lipas.ui.components.misc :as misc]
   [lipas.ui.mui :as mui]
   [lipas.ui.utils :refer [<== ==>] :as utils]
   [reagent.core :as r]))

(def import-formats [".zip" ".kml" ".json" ".geojson"])
(def geom-type "Polygon")

(defn helper [{:keys [label tooltip]}]
  [mui/tooltip {:title tooltip}
   [mui/link
    {:style     {:font-family "Lato" :font-size "0.9em" :margin "0.5em"}
     :underline "always"}
    label]])

(defn analysis-area-selector []
  (let [tr         (<== [:lipas.ui.subs/translator])
        candidates (<== [::subs/analysis-candidates-table-rows])
        headers    (<== [::subs/analysis-candidates-table-headers])
        loading?   (<== [::subs/loading?])]

    [mui/grid {:container true :spacing 2}

     [mui/grid {:item true :xs 12}
      [lui/expansion-panel {:label            "Info"
                            :default-expanded false}
       [mui/paper
        {:style
         {:padding "1em" :background-color "#f5e642"}}
        [mui/typography {:variant "body1" :paragraph true}
         (tr :analysis/diversity-help1)]
        [mui/typography {:variant "body1" :paragraph true}
         (tr :analysis/diversity-help2)]
        [mui/typography {:variant "body1" :paragraph true}
         (tr :analysis/description3)]
        [mui/typography {:variant "body1" :paragraph true}
         (tr :analysis/diversity-help3)]]]]

     [mui/grid {:item true :xs 12}

      [lui/expansion-panel {:label            "Käytä postinumeroalueita"
                            :default-expanded true}
       [mui/grid {:container true}
        [mui/grid {:item true :xs 12}
         [lui/city-selector-single
          {:on-change #(==> [::events/fetch-postal-code-areas %])}]]]]

      [lui/expansion-panel {:label            "Tuo omat alueet"
                            :default-expanded false}
       [:<>
        [mui/grid {:item true :xs 12}
         [mui/typography {:variant "body2"}
          (str (tr :map.import/headline) " (polygon)")]]
        [mui/grid {:item true}
         [:input
          {:type      "file"
           :accept    (str/join "," import-formats)
           :on-change #(==> [::events/load-geoms-from-file
                             (-> % .-target .-files)
                             geom-type])}]]
        [mui/grid {:item true}
         [mui/typography {:display "inline"} (str (tr :help/headline) ":")]
         [helper {:label "Shapefile" :tooltip (tr :map.import/shapefile)}]
         [helper {:label "GeoJSON" :tooltip (tr :map.import/geoJSON)}]
         [helper {:label "KML" :tooltip (tr :map.import/kml)}]]]]

      (when (seq candidates)
        [:<>
         [mui/grid {:item true :xs 12 :style {:margin-top "0.5em"}}
          [mui/button {:on-click  #(==> [::events/calc-all-diversity-indices])}
           [mui/icon {:style {:margin-right "0.25em"}} "refresh"]
           "Laske kaikki"]]
         [lui/table-v2
          {:headers            headers
           :items              candidates
           :in-progress?       loading?
           :action-icon        "refresh"
           :action-label       "Laske monipuolisuus"
           :on-select          #(==> [::events/calc-diversity-indices %])
           #_#_:allow-editing? (constantly true)
           #_#_:multi-select?  true}]])]]))

(defn seq-indexed [coll]
  (map-indexed vector coll))

(defn category-builder []
  (let [categories (<== [::subs/categories])]
    [mui/table
     [mui/table-head
      [mui/table-row
       [mui/table-cell "Kategoria"]
       [mui/table-cell "Liikuntapaikkatyypit"]]]
     (into [mui/table-body {:key (count categories)}]
           (for [[idx category] (seq-indexed categories)]
             [mui/table-row {}

              ;; Category name
              [mui/table-cell {:style {:width "40%"}}
               [lui/text-field
                {:full-width  true
                 :value       (:name category)
                 :placeholder "Uusi kategoria"
                 :on-change   #(==> [::events/set-category-name idx %])}]]

              ;; Types + delete
              [mui/table-cell {:style {:width "60%"}}
               [mui/grid {:container true :align-items "center" :spacing 2}

                ;; Type selector
                [mui/grid {:item true :xs 8}
                 [lui/type-selector
                  {:value     (:type-codes category)
                   :on-change #(==> [::events/set-category-type-codes idx %])}]]

                ;; Factor selector
                [mui/grid {:item true :xs 2}
                 [lui/select
                  {:label     "Kerroin"
                   :items     (map (fn [n] {:label n :value n}) [0 1 2 3 4 5])
                   :on-change #(==> [::events/set-category-factor idx %])
                   :value     (:factor category)}]]

                ;; Delete category button
                [mui/grid {:item true :xs 2}
                 [mui/tooltip {:title "Poista kategoria"}
                  [mui/icon-button
                   {:on-click #(==> [::events/delete-category idx])}
                   [mui/icon "delete"]]]]]]]))]))

(defn overlay-switches []
  (let [tr                   (<== [:lipas.ui.subs/translator])
        show-diversity-area? (<== [:lipas.ui.map.subs/overlay-visible? :diversity-area])
        show-diversity-grid? (<== [:lipas.ui.map.subs/overlay-visible? :diversity-grid])
        show-sports-sites?   (<== [:lipas.ui.map.subs/overlay-visible? :vectors])]
    [mui/grid {:container true :style {:padding "1em"}}

     ;; Sports facilities
     [mui/grid {:item true}
      [lui/switch
       {:label     (tr :sport/headline)
        :value     show-sports-sites?
        :on-change #(==> [:lipas.ui.map.events/set-overlay % :vectors])}]]

     ;; Diversity area
     [mui/grid {:item true}
      [lui/switch
       {:label     (tr :analysis/analysis-areas)
        :value     show-diversity-area?
        :on-change #(==> [:lipas.ui.map.events/set-overlay % :diversity-area])}]]

     ;; Diversity grid
     [mui/grid {:item true}
      [lui/switch
       {:label     (tr :analysis/diversity-grid)
        :value     show-diversity-grid?
        :on-change #(==> [:lipas.ui.map.events/set-overlay % :diversity-grid])}]]]))

(defn categories-settings []
  (let [tr                     (<== [:lipas.ui.subs/translator])
        selected-preset        (<== [::subs/selected-category-preset])
        category-presets       (<== [::subs/category-presets])
        summer-enabled?        (<== [::subs/seasonality-enabled? "summer"])
        winter-enabled?        (<== [::subs/seasonality-enabled? "winter"])
        all-year-enabled?      (<== [::subs/seasonality-enabled? "all-year"])
        save-dialog-open?      (<== [::subs/category-save-dialog-open?])
        new-preset-name        (<== [::subs/new-preset-name])
        new-preset-name-valid? (<== [::subs/new-preset-name-valid?])]

    [mui/grid {:container true :spacing 1}

     ;; Helper text
     [mui/grid {:item true :xs 12 :style {:margin-bottom "0.5em"}}
      [mui/paper {:style {:padding "1em" :background-color "#f5e642"}}
       [mui/typography {:variant "body1" :paragraph false}
        (tr :analysis/categories-help)]]]

     ;; Preset category selector
     [mui/grid {:item true :xs 12 :md 12}
      [mui/form-group {:style {:padding "0.5em" :margin-bottom "0.5em"}}
       [lui/select
        {:value     selected-preset
         :on-change #(==> [::events/select-category-preset %])
         :label     "Valmiit luokittelut"
         :items     category-presets}]]]

     ;; Seasonality switches
     [mui/grid {:item true :xs 12 :md 12}
      [mui/grid {:container true :style {:padding "0.5em"}}

       [mui/grid {:item true :xs 12}
        [mui/typography {:variant "caption"} "Rajaukset"]]

       ;; Summer season filter
       [mui/grid {:item true}
        [lui/switch
         {:label     "Käytössä kesällä"
          :value     summer-enabled?
          :on-change #(==> [::events/toggle-seasonality "summer" %])}]]

       ;; Winter season filter
       [mui/grid {:item true}
        [lui/switch
         {:label     "Käytössä talvella"
          :value     winter-enabled?
          :on-change #(==> [::events/toggle-seasonality "winter" %])}]]

       ;; All-year filter
       [mui/grid {:item true}
        [lui/switch
         {:label     "Käytössä vuoden ympäri"
          :value     all-year-enabled?
          :on-change #(==> [::events/toggle-seasonality "all-year" %])}]]]]

     ;; Add new category button
     [mui/grid {:item true :xs 12}

      [mui/grid {:container true :align-items "center"}

       [mui/grid {:item true :xs 12 :md 4 :style {:text-align "center"}}
        [mui/button
         {:variant  "text"
          :on-click #(==> [::events/add-new-category])}
         "Uusi kategoria"]]

       ;; Reset default categories button
       [mui/grid {:item true :xs 12 :md 4 :style {:text-align "center"}}
        [mui/button
         {:variant  "text"
          :on-click (fn [_]
                      (==> [:lipas.ui.events/confirm
                            "Tahdotko varmasti palauttaa oletuskategoriat?"
                            #(==> [::events/select-category-preset :default])
                            #()]))}
         "Palauta oletuskategoriat"]]

       [lui/dialog {:open?         save-dialog-open?
                    :title         "Tallenna kategorisointi"
                    :on-close      #(==> [::events/toggle-category-save-dialog])
                    :cancel-label  (tr :actions/cancel)
                    :save-label    (tr :actions/save)
                    :on-save       #(==> [::events/save-category-preset new-preset-name])
                    :save-enabled? new-preset-name-valid?}
        [lui/text-field {:full-width true
                         :label      "Kategorisoinnin nimi"
                         :value      new-preset-name
                         :on-change  #(==> [::events/set-new-preset-name %])}]]

       ;; Save category preset button
       [mui/grid {:item true :xs 12 :md 4 :style {:text-align "center"}}
        [mui/button
         {:variant  "text"
          :on-click #(==> [::events/toggle-category-save-dialog])}
         "Tallenna kategoriat"]]]]

     ;; Category builder
     [mui/grid {:item true :xs 12 :style {:margin-top "2em"}}
      [category-builder]]]))

(defn distance-settings []
  (let [max-distance-m    (<== [::subs/max-distance-m])]
    [mui/grid {:container true :spacing 2}

     ;; Helper text
     [mui/grid {:item true :xs 12 :style {:margin-bottom "1em"}}
      [mui/paper {:style {:padding "1em" :background-color "#f5e642"}}
       [mui/typography {:variant "body1" :paragraph false}
        "Monipuolisuus lasketaan jokaiselle väestöruudulle siten, että monipuolisuuteen vaikuttavat ne liikuntapaikat, jotka ovat annetun metrimäärän kävelyetäisyydellä tieverkkoa pitkin väestöruudun keskipisteestä."]]]

     ;; Distance zones selector
     [mui/grid {:item true :xs 12 :style {:margin-top    "1em"
                                          :margin-left   "1em"
                                          :margin-right  "1em"
                                          :margin-bottom "5em"}}

      (let [min 500 max 1500 step 100]
        [:> Slider
         {:min       min
          :max       max
          :step      step
          :value     max-distance-m
          :dots      true
          :marks     (into {}
                           (map (juxt identity #(str % "m")))
                           (range min (+ max step) step))
          :style     {:font-family "Lato, sans-serif"}
          :on-change #(==> [::events/set-max-distance-m %])}])]]))

(defn settings []
  (let [tr (<== [:lipas.ui.subs/translator])]
    [:<>

     [mui/grid {:container true}

      ;; What's visible on map
      [mui/grid {:item true :xs 12}
       [lui/expansion-panel
        {:label            (tr :analysis/settings-map)
         :default-expanded false}
        [overlay-switches]]]

      ;; Categories
      [mui/grid {:item true :xs 12}
       [lui/expansion-panel
        {:label            (tr :analysis/categories)
         :default-expanded false}
        [categories-settings]]]

      ;; Distances
      [mui/grid {:item true :xs 12}
       [lui/expansion-panel
        {:label            (tr :analysis/distance)
         :default-expanded false}
        [distance-settings]]]]]))

(defn export []
  (let [fmt (<== [::subs/selected-export-format])]
    [mui/grid {:container true :spacing 2}

     ;; Format selector
     [mui/grid {:item true :xs 12}
      [lui/select
       {:label     "Formaatti"
        :items     [{:label "Excel" :value "excel"}
                    {:label "JSON" :value "geojson"}]
        :on-change #(==> [::events/select-export-format %])
        :value     fmt}]]

     ;; Export aggregate results
     [mui/grid {:item true :xs 12}

      [mui/button
       {:on-click #(==> [::events/export-aggs fmt])}
       "Lataa alueet"]]

     ;; Export grid
     [mui/grid {:item true :xs 12}
      [mui/button
       {:on-click #(==> [::events/export-grid fmt])}
       "Lataa ruudukko"]]

     ;; Export categories
     [mui/grid {:item true :xs 12}
      [mui/button
       {:on-click #(==> [::events/export-categories fmt])}
       "Lataa kategoriat"]]

     ;; Export settings
     [mui/grid {:item true :xs 12}
      [mui/button
       {:on-click #(==> [::events/export-settings fmt])}
       "Lataa parametrit"]]]))

(def diversity-base-color "#9D191A")

(def diversity-colors
  (into {}
        (for [n (range 30)]
          [(- (dec 30) n)
           (-> diversity-base-color
               gcolor/hexToRgb
               (gcolor/lighten (/ n 40))
               gcolor/rgbArrayToHex)])))

(def population-base-color "#006190")

(comment
  (->> diversity-colors
       (sort-by first)))

(defn grid-chart []
  (let [chart-data (<== [::subs/grid-chart-data])
        labels     {:population    "Väestö"
                    :diversity-idx "Monipuolisuusindeksi"}]
    [:> ResponsiveContainer {:width "100%" :height 300}
     (into
      [:> BarChart
       {:data     chart-data
        :layout   "horizontal"
        #_#_:on-click #(println %)
        :margin   {:bottom 20 :left 20}}
       [:> CartesianGrid]
       [:> Tooltip {:content (fn [props]
                                  (charts/labeled-tooltip labels :label :hide-header props))}]
       [:> XAxis
        {:dataKey       "diversity-idx"
         :type          "number"
         :allowDecimals false
         :label         (merge {:value (:diversity-idx labels) :position "bottom"}
                               charts/font-styles)
         :tick          charts/font-styles
         :tickCount     10
         :domain        #js["dataMin" "dataMax"]
         :padding       #js{:left 16 :right 16}}]

       [:> YAxis
        {:tick  charts/font-styles
         :label (merge {:value (:population labels) :angle -90 :position "left"}
                       charts/font-styles)}]
       (into
        [:> Bar {:dataKey "population" :fill "#9D191A"}]
        (for [diversity-idx (->> chart-data (map :diversity-idx))]
          [:> Cell {:fill (diversity-colors diversity-idx)}]))])]))

(defn area-chart []
  (let [tr         (<== [:lipas.ui.subs/translator])
        chart-data (<== [::subs/area-chart-data])
        labels     {:population           "Kokonaisväestö"
                    :diversity-idx        "Monipuolisuusindeksi"
                    :pwm                  "Väestöpainotettu monipuolisuusindeksi"
                    :anonymized-count     "Ikä anonymisoitu"
                    :population-age-0-14  (str "0-14" (tr :duration/years-short))
                    :population-age-15-64 (str "15-64" (tr :duration/years-short))
                    :population-age-65-   (str "65" (tr :duration/years-short) "-")
                    :name                 "Nimi"}]

    [:> ResponsiveContainer
     {:width  "100%"
      :height (+ 100 (* 50 (count chart-data)))}
     [:> BarChart
      {:data         chart-data
       :layout       "vertical"
       #_#_:on-click #(println %)
       :margin       {:top 20 :bottom 20 :left 120}}
      [:> CartesianGrid]

      [:> Tooltip
       {:content (fn [^js props] (charts/labeled-tooltip labels :label :hide-header props))}]

      [:> Legend
       {:verticalAlign "top"
        :content       (fn [^js props]
                         #_(charts/legend labels props)
                         (let [payload (gobj/get props "payload")]
                           (r/as-element
                            (->> payload
                                 (map
                                  (fn [obj]
                                    {:label (or (labels (gobj/get obj "value"))
                                                (labels (keyword (gobj/get obj "value"))))
                                     :color (gobj/get obj "color")
                                     :type  (gobj/get obj "type")}))
                                 (sort-by :label)
                                 (map
                                  (fn [{:keys [label color type]}]
                                    [mui/grid {:item true}
                                     [misc/icon-text3
                                      {:icon       (charts/legend-icons type)
                                       :icon-color color
                                       :text       label}]]))
                                 (into
                                  [mui/grid
                                   {:container       true
                                    :style           {:margin-bottom "1.5em"}
                                    :justify-content "center"}])))))}]

      ;; Area names on y-axis
      [:> YAxis
       {:dataKey "name"
        :type    "category"
        :tick    charts/font-styles}]

      ;; 1st x-axis for population bars
      [:> XAxis
       {:xAxisId     "population"
        :orientation "top"
        :label       (merge {:value (:population labels) :position "top"}
                            charts/font-styles)
        :type        "number"
        :dataKey     "population"
        :tick        charts/font-styles
        :padding     #js{:left 16 :right 16}}]

      [:> Bar
       {:xAxisId "population"
        :dataKey "population-age-0-14"
        :stackId "population"
        :fill    (:age-0-14 charts/age-groups)}]
      [:> Bar
       {:xAxisId "population"
        :dataKey "population-age-15-64"
        :stackId "population"
        :fill    (:age-15-64 charts/age-groups)}]
      [:> Bar
       {:xAxisId "population"
        :dataKey "population-age-65-"
        :stackId "population"
        :fill    (:age-65- charts/age-groups)}]
      [:> Bar
       {:xAxisId "population"
        :dataKey "anonymized-count"
        :stackId "population"
        :fill    mui/gray1}]

      ;; 2nd x-axis for population weighted mean diversity bars
      [:> XAxis
       {:xAxisId     "pwm"
        :orientation "bottom"
        :label       (merge {:value (:pwm labels) :position "bottom"}
                            charts/font-styles)
        :type        "number"
        :dataKey     "pwm"
        :tick        charts/font-styles
        :padding     #js{:left 16 :right 16}}]

      (into
       [:> Bar {:xAxisId "pwm" :dataKey "pwm" :fill (get diversity-colors 8)}]
       (for [diversity-idx (->> chart-data (map (comp js/Math.round :pwm)))]
         [:> Cell {:fill (get diversity-colors diversity-idx)}]))]]))

(defn areas-selector []
  (let [result-areas   (<== [::subs/result-area-options])
        selected-areas (<== [::subs/selected-result-areas])]
    [lui/autocomplete
     {:on-change #(==> [::events/select-analysis-chart-areas %])
      :multi?    true
      :label     "Valitse alueet"
      :items     result-areas
      :value     selected-areas}]))

(defn results []
  (let [selected-chart-tab (<== [::subs/selected-analysis-chart-tab])]
    [:<>
     [lui/expansion-panel
      {:label            "Kuvaajat"
       :default-expanded true}
      [mui/grid {:container true :spacing 2}
       [mui/grid {:item true :xs 12}
        [areas-selector]]
       [mui/grid {:item true :xs 12}
        [mui/tabs
         {:size      "small"
          :value     selected-chart-tab
          :on-change #(==> [::events/select-analysis-chart-tab %2])}
         [mui/tab {:label "Alueittain" :value "area"}]
         [mui/tab {:label "Ruuduittain" :value "grid"}]]]
       [mui/grid {:item true :xs 12}
        (when (= "area" selected-chart-tab)
          [area-chart])
        (when (= "grid" selected-chart-tab)
          [grid-chart])]]]
     [lui/expansion-panel
      {:label            "Lataa tiedostona"
       :default-expanded false}
      [export]]]))


(defn view []
  (let [tr                 (<== [:lipas.ui.subs/translator])
        selected-tab       (<== [::subs/selected-analysis-tab])
        any-analysis-done? (<== [::subs/any-analysis-done?])]

    [mui/grid {:container true :spacing 2}

     #_[mui/grid {:item true :xs 12}
        [overlay-switches]]

     [mui/grid {:item true :xs 12}
      [mui/tabs {:value      selected-tab
                 :on-change  #(==> [::events/select-analysis-tab %2])
                 :variant    "fullWidth"
                 :centered   true
                 :text-color "secondary"}
       [mui/tab {:label "Analyysialueet" :value "analysis-area"}]
       [mui/tab {:label (tr :analysis/results) :value "results"}]
       [mui/tab {:label (tr :analysis/settings) :value "settings"}]]]

     [mui/grid {:item true :xs 12}
      (when (= selected-tab "analysis-area")
        [analysis-area-selector])

      (when (= selected-tab "settings")
        [settings])

      (when (= selected-tab "results")
        (if any-analysis-done?
          [results]
          [mui/typography "Analyysejä ei ole tehty."]))]
     [mui/grid {:item true :xs 12 :style {:height "3em"}}]]))

;; TODO translations

(comment
  (==> [::events/toggle-category-save-dialog])
  )
