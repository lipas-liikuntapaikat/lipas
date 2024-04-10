(ns lipas.ui.map.views
  (:require
   ["mdi-material-ui/ContentCut$default" :as ContentCut]
   ["mdi-material-ui/ContentDuplicate$default" :as ContentDuplicate]
   ["mdi-material-ui/Eraser$default" :as Eraser]
   ["mdi-material-ui/FileUpload$default" :as FileUpload]
   ["mdi-material-ui/MapSearchOutline$default" :as MapSearchOutline]
   #_[lipas.ui.feedback.views :as feedback]
   #_[lipas.ui.sports-sites.football.views :as football]
   [clojure.spec.alpha :as s]
   [clojure.string :as string]
   [lipas.data.sports-sites :as ss]
   [lipas.ui.accessibility.views :as accessibility]
   [lipas.ui.analysis.views :as analysis]
   [lipas.ui.components :as lui]
   [lipas.ui.loi.views :as loi]
   [lipas.ui.map.events :as events]
   [lipas.ui.map.import :as import]
   [lipas.ui.map.map :as ol-map]
   [lipas.ui.map.subs :as subs]
   [lipas.ui.mui :as mui]
   [lipas.ui.navbar :as nav]
   [lipas.ui.reminders.views :as reminders]
   [lipas.ui.reports.views :as reports]
   [lipas.ui.search.views :as search]
   [lipas.ui.sports-sites.activities.views :as activities]
   [lipas.ui.sports-sites.events :as sports-site-events]
   [lipas.ui.sports-sites.floorball.views :as floorball]
   [lipas.ui.sports-sites.views :as sports-sites]
   [lipas.ui.utils :refer [<== ==>] :as utils]
   [reagent.core :as r]))

(defonce simplify-tool-component (r/atom nil))

(defn rreset!
  "Like `reset!` but returns nil"
  [a newval]
  (reset! a newval)
  nil)

(defn address-search-dialog []
  (let [tr      (<== [:lipas.ui.subs/translator])
        open?   (<== [::subs/address-search-dialog-open?])
        value   (<== [::subs/address-search-keyword])
        toggle  (fn [] (==> [::events/toggle-address-search-dialog]))
        results (<== [::subs/address-search-results])]
    [lui/dialog
     {:open?         open?
      :title         (tr :map.address-search/title)
      :on-close      toggle
      :save-enabled? false
      :cancel-label  (tr :actions/close)}
     [mui/grid {:container true}
      [mui/grid {:item true :xs 12}
       [lui/text-field
        {:auto-focus true
         :fullWidth  true
         :defer-ms   150
         :label      (tr :search/search)
         :value      value
         :on-change  #(==> [::events/update-address-search-keyword %])}]]
      [mui/grid {:item true :xs 12}
       (into
        [mui/list]
        (for [m results]
          [mui/list-item
           {:button   true
            :on-click #(==> [::events/show-address m])}
           [mui/list-item-text
            (:label m)]]))]]]))

(defn simplify-tool-container
  []
  (when-let [open? (<== [::subs/simplify-dialog-open?])]
    [mui/slide {:direction "up" :in open?}
     [lui/floating-container {:bottom 12 :left 550}
      @simplify-tool-component]]))

(defn simplify-tool
  [{:keys [tr on-change on-close]
    :or   {on-close #(==> [::events/close-simplify-tool])}}]
  (let [tolerance (<== [::subs/simplify-tolerance])]
    [mui/paper {:style {:padding "1em"} :elevation 5}
     [mui/grid {:container true}

      ;; Header
      [mui/grid {:item true :xs 12}
       [:h4 (tr :map.tools.simplify/headline)]]

      ;; Slider
      [mui/grid {:item true :xs 12}

       [mui/grid {:container true :spacing 2}

        ;; Less
        [mui/grid {:item true}
         [mui/typography (tr :general/less)]]

        ;; The Slider
        [mui/grid {:item true :xs true}
         [mui/slider
          {:on-change #(==> [::events/set-simplify-tolerance %2])
           :value     tolerance
           :marks     (mapv (fn [n] {:label (str n) :value n}) (range 11))
           :step      0.5
           :min       0
           :max       10}]]

        ;; More
        [mui/grid {:item true}
         [mui/typography (tr :general/more)]]]]

      ;; Buttons
      [mui/grid {:item true :xs 12}
       [mui/grid {:container true :spacing 1}

        ;; OK
        [mui/grid {:item true}
         [mui/button
          {:variant  "contained"
           :color    "secondary"
           :on-click #(on-change tolerance)}
          "OK"]]

        ;; Cancel
        [mui/grid {:item true}
         [mui/button
          {:variant  "contained"
           :color    "default"
           :on-click on-close}
          (tr :actions/cancel)]]]]]]))

(defn layer-switcher [{:keys [tr]}]
  (let [basemaps {:taustakartta (tr :map.basemap/taustakartta)
                  :maastokartta (tr :map.basemap/maastokartta)
                  :ortokuva     (tr :map.basemap/ortokuva)}
        basemap  (<== [::subs/basemap])]
    [mui/grid {:container true :direction "column"}
     [mui/grid {:item true}
      [lui/select
       {:items     basemaps
        :value     basemap
        :label-fn  second
        :value-fn  first
        :on-change #(==> [::events/select-basemap %])}]]
     [mui/grid {:item true}
      [mui/typography {:variant "caption"}
       (tr :map.basemap/copyright)]]]))

(defn overlay-selector
  [{:keys [tr]}]
  (r/with-let [anchor-el (r/atom nil)]
    (let [overlays          {:light-traffic
                             {:label  (tr :map.overlay/light-traffic)
                              :label2 "© Väylävirasto"
                              :icon   [mui/icon "timeline"]}
                             :retkikartta-snowmobile-tracks
                             {:label  (tr :map.overlay/retkikartta-snowmobile-tracks)
                              :label2 "© Metsähallitus"
                              :icon   [mui/icon
                                       {:style {:color "#0000FF"}}
                                       "timeline"]}
                             :mml-kiinteisto
                             {:label  (tr :map.overlay/mml-kiinteisto)
                              :label2 "© Maanmittauslaitos"
                              :icon   [mui/icon
                                       {:style {:color "red"}}
                                       "timeline"]}
                             :mml-kiinteistotunnukset
                             {:label  "Kiinteistötunnukset"
                              :label2 "© Maanmittauslaitos"
                              :icon   [mui/icon
                                       {:style {:color "black"}}
                                       "text_format"]}
                             :mml-kuntarajat
                             {:label  "Kuntarajat"
                              :label2 "© Maanmittauslaitos"
                              :icon   [mui/icon
                                       {:style {:color "#6222BC"}}
                                       "timeline"]}}
          selected-overlays (<== [::subs/selected-overlays])]
      [:<>
       (into

        [mui/menu
         {:open      (boolean @anchor-el)
          :anchor-el @anchor-el
          :on-close  #(reset! anchor-el nil)}]

        (for [[k {:keys [label label2 icon]}] overlays
              :let                            [v (contains? selected-overlays k)]]
          [mui/menu-item
           {:button   true
            :on-click #(==> [::events/toggle-overlay k])}
           [mui/list-item-icon
            [mui/checkbox
             {:checked   (boolean v)
              :size      "medium"
              :value     (str v)
              :on-change #()}]]
           [mui/list-item-text
            {:primaryTypographyProps   {:style {:font-size "0.9em" :margin-right "2em"}}
             :secondaryTypographyProps {:style {:font-size "0.7em" :margin-right "2em"}}
             :primary                  label :secondary label2}]
           [mui/list-item-icon
            icon]]))

       [mui/grid {:item true}
        [mui/tooltip {:title (tr :map.overlay/tooltip)}
         [mui/icon-button
          {:on-click
           (fn [evt] (reset! anchor-el (.-currentTarget evt)))}
          [mui/icon "layers"]]]]])))

(defn user-location-btn
  [{:keys [tr]}]
  [mui/tooltip {:title (tr :map/zoom-to-user)}
   [mui/icon-button {:on-click #(==> [::events/zoom-to-users-position])}
    [mui/icon {:color "inherit" :font-size "medium"}
     "my_location"]]])

(defn helper-select-onclick [type-code geom-help-open?]
  (reset! geom-help-open? false)
  (==> [::sports-site-events/select-new-site-type type-code]))


(defn helper-row [{:keys [type geom description on-select]}]
  )

(defn filter-by-term [term table-data]
  (let [lower-case-term (string/lower-case term)]
    (filter #(or (string/includes? (string/lower-case (% :name)) lower-case-term)
                 (string/includes? (string/lower-case (% :geometry-type)) lower-case-term)
                 (string/includes? (string/lower-case (% :description)) lower-case-term)
                 (string/includes? (string/lower-case (string/join (% :tags))) lower-case-term))
            table-data)))

(defn type-helper-table [{:keys [tr on-select]}]
  (r/with-let [search-term (r/atom "")
               table-data (<== [:lipas.ui.sports-sites.subs/type-table])]
    (let [filtered-table-data (filter-by-term @search-term table-data)
          sorted-and-filtered-table-data (sort-by :name filtered-table-data)]
      [mui/grid {:container true}
       [mui/grid {:item true :xs 12}
        [mui/text-field {:label (tr :search/search)
                        :xs 3
                        :on-change #(reset! search-term (-> % .-target .-value))
                        :placeholder nil}]]
       [mui/grid {:item true :xs 12}
        [mui/table-container
        [mui/table
         [mui/table-head
          [mui/table-row
           [mui/table-cell (tr :type/name)]
           [mui/table-cell (tr :type/geometry)]
           [mui/table-cell (tr :general/description)]]]
         (into
          [mui/table-body {:component "th" :scope "row"}]
          (for [row sorted-and-filtered-table-data]
            [mui/table-row {:on-click #(on-select (row :type-code))}
             [mui/table-cell (row :name)]
             [mui/table-cell (->>  row :geometry-type (keyword :type) tr)]
             [mui/table-cell (row :description)]]))]]]])))

(defn type-selector-single [{:keys [tr value on-change types]}]
  (r/with-let [selected-type (r/atom value)
               geom-help-open? (r/atom false)]
    (let [locale        (tr)]
      [:<>

       ;; Modal
       [lui/dialog {:open? @geom-help-open?
                    :cancel-label (tr :actions/close)
                    :title (tr :type/name)
                    :max-width "xl"
                    :on-close #(swap! geom-help-open? not)}

        ;; Apu ankka table
        [type-helper-table {:tr tr
                            :geom-help-open? geom-help-open?
                            :on-select (fn [element]
                                         (swap! geom-help-open? not)
                                         (reset! selected-type element))}]]
       [mui/grid {:container true}

        ;; Autocomplete
        [mui/grid {:item true :xs 11}
         [lui/autocomplete
          {:multi?    false
           :items     (vals types)
           :value     @selected-type
           :label     (tr :type/name)
           :value-fn  :type-code
           :label-fn  (comp locale :name)
           :on-change #(reset! selected-type %)}]]

        ;; Apu ankka button
        [mui/grid {:item true :xs 1}
         [mui/icon-button
          {:xs 1
           :type "button"
           :on-click #(swap! geom-help-open? not)}
          [mui/icon "help"]]]

        ;; Description + OK button
        (when @selected-type
          [mui/grid {:item true :xs 12}
           [mui/typography {:style {:margin-top "1em" :margin-bottom "1em"}}
            (get-in types [@selected-type :description locale])]
           [mui/button {:on-click   #(on-change @selected-type)
                        :auto-focus true
                        :variant    "contained"
                        :color      "secondary"}
            "OK"]])]])))

(defmulti popup-body :type)

(defmethod popup-body :default [popup]
  (let [name'  (-> popup :data :features first :properties :name)
        status (-> popup :data :features first :properties :status)
        tr     (-> popup :tr)
        locale (tr)]
    [mui/paper
     {:style
      {:padding "0.5em"
       :width   (when (< 100 (count name')) "150px")}}
     [mui/typography {:variant "body2"}
      name']
     (when-not (#{"active"} status)
       [mui/typography {:variant "body2" :color "error"}
        (get-in ss/statuses [status locale])])]))

(defmethod popup-body :loi [popup]
  (let [loi-type     (-> popup :data :features first :properties :loi-type)
        loi-category (-> popup :data :features first :properties :loi-category)
        #_#_tr       (-> popup :tr)
        texts        (<== [:lipas.ui.loi.subs/popup-localized loi-type loi-category])
        ]
    [mui/paper
     {:style
      {:padding "0.5em"
       :width   (when (< 100 (count loi-type)) "150px")}}
     [mui/typography {:variant "body2"}
      (:loi-type texts)]
     [mui/typography {:variant "caption"}
      (:loi-category texts)]]))

(defmethod popup-body :population [popup]
  (let [tr          (<== [:lipas.ui.subs/translator])
        zone-labels (<== [:lipas.ui.analysis.reachability.subs/zones-popup-labels])
        metric      (<== [:lipas.ui.analysis.reachability.subs/selected-travel-metric])
        data        (-> popup :data :features first :properties)
        zone-id     (keyword (:zone data))]
    [mui/paper
     {:style
      {:padding "0.5em"}}
     [mui/table {:padding "normal" :size "small"}
      [mui/table-body

       ;; Population
       [mui/table-row {:style {:height "24px"}}
        [mui/table-cell
         [mui/typography {:variant "caption" :noWrap true}
          (tr :analysis/population)]]
        [mui/table-cell
         [mui/typography {:variant "caption" :no-wrap true}
          (if-let [v (:vaesto data)] v "<10")]]]

       ;; Profile / Zone / Metric
       [mui/table-row {:style {:height "24px"}}
        [mui/table-cell
         [mui/typography {:variant "caption" :noWrap true}
          (if (= metric :travel-time)
            (tr :analysis/travel-time)
            (tr :analysis/distance))]]
        [mui/table-cell
         [mui/typography {:variant "caption" :no-wrap true}
          (get-in zone-labels [[metric zone-id]])]]]]]]))

(defmethod popup-body :school [popup]
  (let [data   (-> popup :data :features first :properties)]
    [mui/paper
     {:style
      {:padding "0.5em"}}
     [mui/typography {:variant "body2"}
      (:name data)]
     [mui/typography {:variant "caption"}
      (:type data)]]))

(defmethod popup-body :diversity-grid [popup]
  (let [tr   (<== [:lipas.ui.subs/translator])
        data (-> popup :data :features first :properties)]
    [mui/paper
     {:style
      {:padding "0.5em"}}
     [mui/table {:padding "dense"}
      [mui/table-body

       ;; Diversity index
       [mui/table-row
        [mui/table-cell
         [mui/typography "Monipuolisuusindeksi"]]
        [mui/table-cell
         [mui/typography {:variant "caption" :no-wrap true}
          (:diversity_idx data)]]]

       ;; Population
       [mui/table-row
        [mui/table-cell
         [mui/typography (tr :analysis/population)]]
        [mui/table-cell
         [mui/typography {:variant "caption" :no-wrap true}
          (or (:population data) "<10")]]]]]]))

(defmethod popup-body :diversity-area [popup]
  (let [tr   (<== [:lipas.ui.subs/translator])
        data (-> popup :data :features first :properties)]
    [mui/paper
     {:style
      {:padding "0.5em"}}

     (if (or (:population-weighted-mean data) (:population data))
       ;; Results table
       [mui/table {:padding "normal"}
        [mui/table-body

         ;; Area name
         (when-let [s (:nimi data)]
           [mui/table-row
            [mui/table-cell
             [mui/typography {:variant "caption"}
              "Alue"]]
            [mui/table-cell
             [mui/typography {:variant "caption" :no-wrap true}
              s]]])

         ;; Postal code
         (when-let [s (:posti_alue data)]
           [mui/table-row
            [mui/table-cell
             [mui/typography {:variant "caption"}
              "Postinumero"]]
            [mui/table-cell
             [mui/typography {:variant "caption" :no-wrap true}
              s]]])

         ;; Population weighted mean
         [mui/table-row
          [mui/table-cell
           [mui/typography {:variant "caption"}
            (tr :analysis/population-weighted-mean)]]
          [mui/table-cell
           [mui/typography {:variant "caption" :no-wrap true}
            (utils/round-safe
             (:population-weighted-mean data))]]]

         ;; Population
         [mui/table-row
          [mui/table-cell
           [mui/typography {:variant "caption"}
            (tr :analysis/population)]]
          [mui/table-cell
           [mui/typography {:variant "caption" :no-wrap true}
            (let [n (:population data 0)]
              (if (< n 10) "<10" n))]]]

         ;; Mean
         #_[mui/table-row
            [mui/table-cell
             [mui/typography (tr :analysis/mean)]]
            [mui/table-cell
             (:diversity-idx-mean data)]]

         ;; Median
         #_[mui/table-row
            [mui/table-cell
             [mui/typography (tr :analysis/median)]]
            [mui/table-cell
             (:diversity-idx-median data)]]

         ;; Mode
         #_[mui/table-row
            [mui/table-cell
             [mui/typography (tr :analysis/mode)]]
            [mui/table-cell
             (when (seq (:diversity-idx-mode data))
               (string/join "," (:diversity-idx-mode data)))]]]]

       ;; No data available
       [:div {:style {:width "200px" :padding "0.5em 0.5em 0em 0.5em"}}
        [mui/typography {:paragraph true}
         (str (:nimi data) " " (:posti_alue data))]
        [mui/typography {:paragraph true :variant "caption"}
         "Analyysiä ei ole tehty"]
        [mui/typography {:paragraph true :variant "caption"}
         "Klikkaa aluetta hiirellä tai valitse alue taulukosta."]])]))

(defn popup []
  (let [{:keys [data anchor-el]
         :as   popup'} (<== [::subs/popup])
        tr             (<== [:lipas.ui.subs/translator])]
    (when anchor-el
      [mui/popper
       {:open      (boolean (seq data))
        :placement "top-end"
        :anchor-el anchor-el
        :container anchor-el
        :modifiers {:offset {:enabled true :offset "0px,10px"}}}
       [popup-body (assoc popup' :tr tr)]])))

(defn set-field
  [lipas-id & args]
  (==> [::sports-site-events/edit-field lipas-id (butlast args) (last args)]))

;; Works as both display and edit views
(defn sports-site-view
  [{:keys [tr site-data width]}]
  (let [display-data (:display-data site-data)
        lipas-id     (:lipas-id display-data)
        edit-data    (:edit-data site-data)

        type-code            (-> display-data :type :type-code)
        floorball-types      (<== [:lipas.ui.sports-sites.floorball.subs/type-codes])
        floorball-visibility (<== [:lipas.ui.sports-sites.floorball.subs/visibility])
        #_#_football-types   (<== [:lipas.ui.sports-sites.football.subs/type-codes])
        accessibility-type?  (<== [:lipas.ui.accessibility.subs/accessibility-type? type-code])
        activity-type?       (<== [:lipas.ui.sports-sites.activities.subs/activity-type? type-code])
        show-activities?     (<== [:lipas.ui.sports-sites.activities.subs/show-activities? type-code])
        hide-actions?        (<== [::subs/hide-actions?])
        admin?               (<== [:lipas.ui.user.subs/admin?])

        {:keys [types cities admins owners editing? edits-valid?
                problems?  editing-allowed? delete-dialog-open?
                can-publish? logged-in?  size-categories sub-mode
                geom-type portal save-in-progress? undo redo
                more-tools-menu-anchor dead? selected-tab can-edit-activities?]}
        (<== [::subs/sports-site-view lipas-id type-code])

        set-field (partial set-field lipas-id)]

    [mui/grid
     {:container true
      :style     (merge {:padding "1em"} (when (utils/ie?) {:width "420px"}))}

     (when editing?
       [import/import-geoms-view
        {:geom-type geom-type
         :on-import #(==> [::events/import-selected-geoms])}])

     [mui/grid {:item true :xs 12}

      ;; Headline
      [mui/grid
       {:container   true
        :style       {:flex-wrap "nowrap"}
        :align-items :center}

       [mui/grid {:item true :style {:margin-top "0.5em" :flex-grow 1}}
        [mui/typography {:variant "h5"}
         (:name display-data)]]

       (when editing?
         (rreset! simplify-tool-component
                  [simplify-tool
                   {:on-change (fn [tolerance]
                                 (let [geoms (-> edit-data :location :geometries)]
                                   (==> [::events/simplify lipas-id geoms tolerance])))
                    :tr        tr}]))

       [mui/grid {:item true}
        ;; Close button
        [mui/grid {:item true}
         (when (not editing?)
           [mui/icon-button
            {:style    {:margin-left "-0.25em"}
             :on-click #(==> [::events/show-sports-site nil])}
            [mui/icon "close"]])]]]

     ;; Tabs
      [mui/grid {:item true :xs 12}
      ;; [mui/tool-bar {:disableGutters true}]
      [mui/tabs
       {:value       selected-tab
        :on-change   #(==> [::events/select-sports-site-tab %2])
        :variant     (if admin? "scrollable" "fullWidth")
        #_#_:variant "scrollable"
        #_#_:variant     "standard"
        :style       {:margin-bottom "1em"}
        :text-color  "secondary"}
       [mui/tab
        {:style {:min-width 0}
         :value 0
         :label (tr :lipas.sports-site/basic-data)}]

       ;; Activities and properties are mutually exclusive
       (when (or admin? (not show-activities?))
         [mui/tab
          {:style {:min-width 0}
           :value 1
           :label (tr :lipas.sports-site/properties)}])

       ;; Disabled in prod until this can be released
       (when (and (not (utils/prod?)) accessibility-type?)
         [mui/tab
          {:style {:min-width 0}
           :value 2
           :label "Esteettömyys"}])

       (when (and (floorball-types type-code)
                  (= :floorball floorball-visibility))
         [mui/tab
          {:style {:min-width 0}
           :value 3
           :label "Olosuhteet"}])

       (when show-activities?
         [mui/tab
          {:style {:min-width 0}
           :value 5
           :label "Ulkoilutietopalvelu"}])

       (when (#{"LineString"} geom-type)
         [mui/tab
          {:style {:min-width 0}
           :value 4
           :label "Korkeusprofiili"}])]

      (when delete-dialog-open?
        [sports-sites/delete-dialog
         {:tr       tr
          :lipas-id lipas-id
          :on-close #(==> [::sports-site-events/toggle-delete-dialog])}])

      (case selected-tab

        ;; Basic info tab
        0 [mui/grid {:container true}

           [mui/grid {:item true :xs 12}

            ^{:key (str "basic-data-" lipas-id)}
            [sports-sites/form
             {:tr              tr
              :display-data    display-data
              :edit-data       edit-data
              :read-only?      (not editing?)
              :types           (vals types)
              :size-categories size-categories
              :admins          admins
              :owners          owners
              :on-change       set-field
              :lipas-id        lipas-id
              :sub-headings?   true}]

            ^{:key (str "location-" lipas-id)}
            [sports-sites/location-form
             {:tr            tr
              :read-only?    (not editing?)
              :cities        (vals cities)
              :edit-data     (:location edit-data)
              :display-data  (:location display-data)
              :on-change     (partial set-field :location)
              :sub-headings? true}]]]

        ;; Properties tab
        1 (r/with-let [prop-tab (r/atom (if (and activity-type? can-edit-activities?)
                                          "activities"
                                          "props"))]
            [:<>
             #_[mui/tabs
                {:style          {:margin-bottom "1em" :margin-top "0.5em"}
                 :value          @prop-tab
                 :variant        "standard"
                 :indicatorColor "primary"
                 :on-change      #(reset! prop-tab %2)}
                (when-not (and activity-type? can-edit-activities?)
                  [mui/tab {:value "props" :label "Ominaisuudet"}])
                (when (and activity-type? can-edit-activities?)
                  [mui/tab {:value "activities" :label "Ulkoilutietopalvelu"}])]

             #_(when (= "props" @prop-tab))
             [sports-sites/properties-form
              {:tr           tr
               :type-code    (or (-> edit-data :type :type-code) type-code)
               :read-only?   (not editing?)
               :on-change    (partial set-field :properties)
               :display-data (:properties display-data)
               :edit-data    (:properties edit-data)
               :geoms        (-> edit-data :location :geometries)
               :geom-type    geom-type
               :problems?    problems?
               :key          (-> edit-data :type :type-code)}]

             #_[:div {:style {:margin-top "1em" :margin-bottom "1em"}}]
             #_(when (= "activities" @prop-tab)
                 [activities/view
                  {:tr           tr
                   :read-only?   (not editing?)
                   :lipas-id     lipas-id
                   :type-code    type-code
                   :display-data display-data
                   :edit-data    edit-data
                   :geom-type    geom-type}])])

        ;; Accessibility
        2 [accessibility/view {:lipas-id lipas-id}]

        3 (condp contains? type-code

            ;; Floorball specific
            floorball-types
            [:<>
             [mui/tabs {:value "floorball" :variant "standard"}
              [mui/tab {:value "floorball" :label "Salibandy"}]]
             [floorball/form
              {:tr           tr
               :lipas-id     lipas-id
               :type-code    (or (-> edit-data :type :type-code) type-code)
               :read-only?   (not editing?)
               :on-change    set-field
               :display-data display-data
               :edit-data    edit-data
               :key          (-> edit-data :type :type-code)}]]

            ;; Football specific
            #_#_football-types
            [football/circumstances-form
             {:tr           tr
              :type-code    (or (-> edit-data :type :type-code) type-code)
              :read-only?   (not editing?)
              :on-change    (partial set-field :circumstances)
              :display-data (:circumstances display-data)
              :edit-data    (:circumstances edit-data)
              :key          (-> edit-data :type :type-code)}])

        4 (when (#{"LineString"} geom-type)
            [mui/grid {:item true :xs 12 :style {:margin-top "0.5em"}}
             [sports-sites/elevation-profile {:lipas-id lipas-id}]])

        5 [mui/grid {:item true :xs 12}
           [activities/view
            {:tr           tr
             :read-only?   (not editing?)
             :lipas-id     lipas-id
             :type-code    type-code
             :display-data display-data
             :edit-data    edit-data
             :geom-type    geom-type}]])]

     ;; "Landing bay" for floating tools
      [mui/grid {:item true :xs 12 :style {:height "3em"}}]

     ;; Actions
     (when-not hide-actions?
       [lui/floating-container
        {:bottom 0 :background-color "transparent"}
        (into
         [mui/grid
          {:container     true
           :align-items   "center"
           :align-content "flex-start"
           :spacing       1
           :style         {:padding "0.5em 0em 0.5em 0em"}}]
         (->>
          [ ;; Undo
           (when editing?
             [mui/tooltip {:title (tr :actions/undo)}
              [:span
               [mui/fab
                {:disabled (not undo)
                 :size     "small"
                 :on-click #(==> [::events/undo lipas-id])}
                [mui/icon "undo"]]]])

           ;; Redo
           (when editing?
             [mui/tooltip {:title (tr :actions/redo)}
              [:span
               [mui/fab
                {:disabled (not redo)
                 :size     "small"
                 :on-click #(==> [::events/redo lipas-id])}
                [mui/icon "redo"]]]])

           ;; Active editing tool
           (when (and editing? (#{"LineString" "Polygon"} geom-type))
             [mui/tooltip
              {:title
               (case sub-mode
                 :drawing         "Piirtotyökalu valittu"
                 :drawing-hole    "Reikäpiirtotyökalu valittu"
                 (:editing :undo) (tr :map/delete-vertices-hint)
                 :importing       "Tuontityökalu valittu"
                 :deleting        "Poistotyökalu valittu"
                 :splitting       "Katkaisutyökalu valittu"
                 :simplifying     "Yskinkertaistystyökalu valittu"
                 :selecting       "Valintatyökalu valittu")}
              [mui/fab
               {:size     "small"
                :on-click #() ; noop
                :color    "inherit"}
               (let [props {:color "secondary"}]
                 (case sub-mode
                   :drawing         (case geom-type
                                      "Point"      [mui/icon props "edit"]
                                      "LineString" [mui/icon props "timeline"]
                                      "Polygon"    [mui/icon props "change_history"])
                   :drawing-hole    [mui/icon props "vignette"]
                   (:editing :undo) [mui/icon props "edit"]
                   :importing       [:> FileUpload props]
                   :deleting        [:> Eraser props]
                   :splitting       [:> ContentCut props]
                   :simplifying     [mui/icon props "auto_fix_high"]
                   :selecting       [mui/icon props "handshake"]))]])

           ;; Tool select button
           (when (and editing? (#{"LineString" "Polygon"} geom-type))
             [:<>
              [mui/tooltip {:title (tr :actions/select-tool)}
               [mui/fab
                {:size     "medium"
                 :on-click #(==> [::events/open-more-tools-menu (.-currentTarget %)])
                 :color    "secondary"}
                [mui/icon "more_horiz"]]]

              [mui/menu
               {:variant    "menu"
                :auto-focus false
                :anchor-el  more-tools-menu-anchor
                :open       (some? more-tools-menu-anchor)
                :on-close   #(==> [::events/close-more-tools-menu])}

               ;; Import geom
               (when (and editing? (#{"LineString" "Polygon"} geom-type))
                 [mui/menu-item {:on-click #(do
                                              (==> [::events/close-more-tools-menu])
                                              (==> [::events/toggle-import-dialog]))}
                  [mui/list-item-icon
                   [:> FileUpload]]
                  [mui/list-item-text (tr :map.import/tooltip)]])

               ;; Simplify
               (when (and editing? (#{"LineString" "Polygon"} geom-type))
                 [mui/menu-item {:on-click #(do
                                              (==> [::events/close-more-tools-menu])
                                              (==> [::events/open-simplify-tool]))}
                  [mui/list-item-icon
                   [mui/icon "auto_fix_high"]]
                  [mui/list-item-text (tr :map.tools/simplify)]])

               ;; Draw hole
               (when (and editing? (#{"Polygon"} geom-type))
                 [mui/menu-item
                  {:on-click
                   #(do
                      (==> [::events/close-more-tools-menu])
                      (==> [::events/start-editing lipas-id :drawing-hole geom-type]))}
                  [mui/list-item-icon
                   [mui/icon
                    {:color (if (= sub-mode :drawing-hole) "secondary" "inherit")}
                    "vignette"]]
                  [mui/list-item-text (tr :map/draw-hole)]])

               ;; Add new geom
               (when (and editing? (#{"LineString" "Polygon"} geom-type))

                 [mui/menu-item
                  {:on-click
                   #(do
                      (==> [::events/close-more-tools-menu])
                      (==> [::events/start-editing lipas-id :drawing geom-type]))}
                  [mui/list-item-icon
                   (if (= geom-type "LineString")
                     [mui/icon
                      {:color (if (= sub-mode :drawing)
                                "secondary"
                                "inherit")} "timeline"]
                     [mui/icon {:color (if (= sub-mode :drawing) "secondary" "inherit")}
                      "change_history"])]
                  [mui/list-item-text (case geom-type
                                        "LineString" (tr :map/draw-linestring)
                                        "Polygon"    (tr :map/draw-polygon))]])

               ;; Delete geom
               (when (and editing? (#{"LineString" "Polygon"} geom-type))
                 [mui/menu-item
                  {:on-click
                   #(do
                      (==> [::events/close-more-tools-menu])
                      (==> [::events/start-editing lipas-id :deleting geom-type]))}
                  [mui/list-item-icon
                   [:> Eraser
                    {:color (if (= sub-mode :deleting) "secondary" "inherit")}]]
                  [mui/list-item-text (case geom-type
                                        "LineString" (tr :map/remove-linestring)
                                        "Polygon"    (tr :map/remove-polygon))]])

               ;; Split linestring
               (when (and editing? (#{"LineString"} geom-type))
                 [mui/menu-item
                  {:on-click
                   #(do
                      (==> [::events/close-more-tools-menu])
                      (==> [::events/start-editing lipas-id :splitting geom-type]))}
                  [mui/list-item-icon
                   [:> ContentCut
                    {:color (if (= sub-mode :splitting) "secondary" "inherit")}]]
                  [mui/list-item-text (tr :map/split-linestring)]])

               ;; Edit tool
               (when (and editing? (#{"LineString" "Polygon"} geom-type))
                 [mui/menu-item
                  {:on-click
                   #(do
                      (==> [::events/close-more-tools-menu])
                      (==> [::events/start-editing lipas-id :editing geom-type]))}
                  [mui/list-item-icon
                   [mui/icon
                    {:color (if (= sub-mode :editing) "secondary" "inherit")}
                    "edit"]]
                  [mui/list-item-text (tr :map.tools/edit-tool)]])]])

           ;; Download GPX
           (when (and (not editing?) (#{"LineString"} geom-type))
             [mui/tooltip {:title (tr :map/download-gpx)}
              [mui/fab
               {:size     "small"
                :on-click #(==> [::events/download-gpx lipas-id])
                :color    "inherit"}
               [mui/icon "save_alt"]]])

           ;; Zoom to site
           (when-not editing?
             [mui/tooltip {:title (tr :map/zoom-to-site)}
              [mui/fab
               {:size     "small"
                :on-click #(==> [::events/zoom-to-site lipas-id width])
                :color    "inherit"}
               [mui/icon {:color "inherit"}
                "place"]]])

           ;; Add reminder
           (when (and logged-in? (not editing?))
             (let [name (-> display-data :name)
                   link (-> js/window .-location .-href)]
               [reminders/add-button
                {:message (tr :reminders/placeholder name link)}]))

           ;; Copy sports site
           (when (and logged-in? (not editing?))
             [mui/tooltip {:title (tr :actions/duplicate)}
              [mui/fab
               {:size     "small"
                :on-click #(==> [::events/duplicate-sports-site lipas-id])}
               [:> ContentDuplicate]]])

           ;; Resurrect button
           (when (and dead? logged-in? editing-allowed?)
             [mui/tooltip {:title (tr :actions/resurrect)}
              [mui/fab
               {:size     "small"
                :on-click #(==> [::events/resurrect lipas-id])}
               [mui/icon "360"]]])

           ;; Analysis
           (when (and logged-in? (not editing?))
             [mui/tooltip {:title (tr :map.demographics/tooltip)}
              [mui/fab
               {:size     "small"
                :on-click #(==> [::events/show-analysis lipas-id])}
               [mui/icon "insights"]]])

           ;; ;; Import geom
           ;; (when (and editing? (#{"LineString"} geom-type))
           ;;   [mui/tooltip {:title (tr :map.import/tooltip)}
           ;;    [mui/fab
           ;;     {:size     "small"
           ;;      :on-click #(==> [::events/toggle-import-dialog])
           ;;      :color    "inherit"}
           ;;     [:> FileUpload]]])

           ;; ;; Draw hole
           ;; (when (and editing? (#{"Polygon"} geom-type))
           ;;   [mui/tooltip {:title (tr :map/draw-hole)}
           ;;    [mui/fab
           ;;     {:size     "small"
           ;;      :on-click #(if (= sub-mode :drawing-hole)
           ;;                   (==> [::events/start-editing lipas-id :editing geom-type])
           ;;                   (==> [::events/start-editing lipas-id :drawing-hole geom-type]))
           ;;      :style    (when (= sub-mode :drawing-hole)
           ;;                  {:border (str "5px solid " mui/secondary)})
           ;;      :color    "inherit"}
           ;;     [mui/icon "vignette"]]])

           ;; ;; Add new geom
           ;; (when (and editing? (#{"LineString" "Polygon"} geom-type))
           ;;   [mui/tooltip {:title (case geom-type
           ;;                          "LineString" (tr :map/draw-linestring)
           ;;                          "Polygon"    (tr :map/draw-polygon))}
           ;;    [mui/fab
           ;;     {:size     "small"
           ;;      :on-click #(if (= sub-mode :drawing)
           ;;                   (==> [::events/start-editing lipas-id :editing geom-type])
           ;;                   (==> [::events/start-editing lipas-id :drawing geom-type]))
           ;;      :style    (when (= sub-mode :drawing)
           ;;                  {:border (str "5px solid " mui/secondary)})
           ;;      :color    "inherit"}
           ;;     (if (= geom-type "LineString")
           ;;       [mui/icon "timeline"]
           ;;       [mui/icon "change_history"])]])

           ;; ;; Delete geom
           ;; (when (and editing? (#{"LineString" "Polygon"} geom-type))
           ;;   [mui/tooltip {:title (case geom-type
           ;;                          "LineString" (tr :map/remove-linestring)
           ;;                          "Polygon"    (tr :map/remove-polygon))}
           ;;    [mui/fab
           ;;     {:size     "small"
           ;;      :on-click #(if (= sub-mode :deleting)
           ;;                   (==> [::events/start-editing lipas-id :editing geom-type])
           ;;                   (==> [::events/start-editing lipas-id :deleting geom-type]))
           ;;      :style    (when (= sub-mode :deleting)
           ;;                  {:border (str "5px solid " mui/secondary)})
           ;;      :color    "inherit"}
           ;;     [:> Eraser]]])

           ;; ;; Split linestring
           ;; (when (and editing? (#{"LineString"} geom-type))
           ;;   [mui/tooltip {:title (tr :map/split-linestring)}
           ;;    [mui/fab
           ;;     {:size     "small"
           ;;      :on-click #(if (= sub-mode :splitting)
           ;;                   (==> [::events/start-editing lipas-id :editing geom-type])
           ;;                   (==> [::events/start-editing lipas-id :splitting geom-type]))
           ;;      :style    (when (= sub-mode :splitting)
           ;;                  {:border (str "5px solid " mui/secondary)})
           ;;      :color    "inherit"}
           ;;     [:> ContentCut]]])

           ;; Helper text
           ;; (when (and editing? (#{"LineString" "Polygon"} geom-type))
           ;;   [mui/tooltip {:title (tr :map/delete-vertices-hint)}
           ;;    [mui/typography
           ;;     {:style
           ;;      {:font-size 24 :margin-left "4px" :margin-right "16px"}}
           ;;     "?"]])
           ]

          (concat
           (lui/edit-actions-list
            {:editing?          editing?
             :editing-allowed?  editing-allowed?
             :save-in-progress? save-in-progress?
             :valid?            edits-valid?
             :logged-in?        logged-in?
             :user-can-publish? can-publish?
             :on-discard        #(==> [::events/discard-edits lipas-id])
             :discard-tooltip   (tr :actions/cancel)
             :on-edit-start     #(==> [::events/edit-site lipas-id geom-type])
             :edit-tooltip      (tr :actions/edit)
             :on-publish        #(==> [::events/save-edits lipas-id])
             :publish-tooltip   (tr :actions/save)
             :invalid-message   (tr :error/invalid-form)
             :on-delete         #(==> [::events/delete-site])
             :delete-tooltip    (tr :lipas.sports-site/delete-tooltip)}))

          (remove nil?)
          (map (fn [tool] [mui/grid {:item true} tool]))))])]]))

(defn add-btn [{:keys [tr]}]
  [mui/tooltip {:title (tr :lipas.sports-site/add-new)}
   [mui/fab
    {:color    "secondary"
     :on-click #(==> [::events/start-adding-new-site])}
    [mui/icon "add"]]])

(defn set-new-site-field [& args]
  (==> [::sports-site-events/edit-new-site-field (butlast args) (last args)]))

(defn add-sports-site-view
  [{:keys [tr width]}]
  (r/with-let [geom-tab     (r/atom "draw")]
    (let [locale (tr)

          {:keys [type data save-enabled? admins owners cities
          problems? types size-categories zoomed? geom active-step
          sub-mode undo redo
          selected-tab]} (<== [::subs/add-sports-site-view])

          floorball-types      (<== [:lipas.ui.sports-sites.floorball.subs/type-codes])
          floorball-visibility (<== [:lipas.ui.sports-sites.floorball.subs/visibility])
          set-field            set-new-site-field
          geom-type            (:geometry-type type)]

      [mui/grid {:container true :spacing 2 :style {:padding "1em"}}

       [mui/grid {:item true :xs 12}

        [import/import-geoms-view
         {:geom-type     geom-type
          :on-import     #(==> [::events/import-selected-geoms-to-new])
          :show-replace? false}]

        (rreset! simplify-tool-component
                 [simplify-tool
                  {:on-close  (fn []
                                (==> [::events/new-geom-drawn geom])
                                (==> [::events/toggle-simplify-dialog]))
                   :on-change (fn [tolerance] (==> [::events/simplify-new geom tolerance]))
                   :tr        tr}])

        [mui/typography {:variant "h6" :style {:margin-left "8px"}}
         (if-let [type-name (get-in type [:name locale])]
           (tr :lipas.sports-site/new-site-of-type type-name)
           (tr :lipas.sports-site/new-site {:type type :locale locale}))]]

       ;; Steps
       [mui/grid {:item true :xs 12}
        [mui/stepper
         {:active-step      active-step
          :alternativeLabel true
          :style            {:padding-left 0 :padding-right 0
                             :margin-left  "-18px"}
          :orientation      "horizontal"}

         ;; Step 1 - Select type
         [mui/step {:active (= (dec 1) active-step)}
          [mui/step-label (tr :actions/select-type)]]

         ;; Step 2 - Add to map
         [mui/step {:active (= (dec 2) active-step)}
          [mui/step-label (tr :map/draw)]]

         ;; Step 3 - Fill data
         [mui/step {:active (= (dec 3) active-step)}
          [mui/step-label (tr :actions/fill-data)]]]]

       [mui/grid {:item true :xs 12}

        ;; Step 1 type
        (when (= active-step (dec 1))
          [mui/grid {:container true}
           [mui/grid {:item true :xs 12}
            [type-selector-single
             {:value     (when type [(:type-code type)])
              :tr        tr
              :types     types
              :on-change #(==> [::sports-site-events/select-new-site-type %])}]]])

        ;; Step 2 geom
        (when (= active-step (dec 2))
          (let [type-code (:type-code type)]
            (if-not geom

              ;; Draw new geom
              [mui/grid {:container true :spacing 2}

               ;; Tabs for selecting btw drawing and importing geoms
               #_(when (#{"LineString" "Polygon"} geom-type))
               [mui/grid {:item true :xs 12}

                [mui/tabs {:value      @geom-tab
                           :on-change  #(reset! geom-tab %2)
                           :variant    "fullWidth"
                           :text-color "secondary"}
                 [mui/tab {:value "draw" :label (tr :map/draw-geoms)}]
                 (when (#{"LineString" "Polygon"} geom-type)
                   [mui/tab {:value "import" :label (tr :map.import/tab-header)}])
                 (when (#{"Point"} geom-type)
                   [mui/tab {:value "coords" :label "Syötä koordinaatit"}])]]

               ;; Draw
               (when (= "draw" @geom-tab)
                 [:<>
                  ;; Helper text
                  [mui/grid {:item true :xs 12}
                   [mui/typography {:variant "body2"}
                    (tr :map/zoom-to-site)]]

                  ;; Zoom closer info text
                  (when (not zoomed?)
                    [mui/grid {:item true :xs 12}
                     [mui/typography {:variant "body2" :color :error}
                      (tr :map/zoom-closer)]])

                  ;; Add initial geom button
                  [mui/grid {:item true}
                   [mui/button
                    {:disabled (not zoomed?)
                     :color    "secondary"
                     :variant  "contained"
                     :on-click #(==> [::events/start-adding-geom geom-type])}
                    [mui/icon "add_location"]
                    (tr :map/add-to-map)]]])

               ;; Enter coordinates
               (when (= "coords" @geom-tab)
                 (r/with-let [state (r/atom {:crs :epsg4326
                                             :lon nil
                                             :lat nil})]
                   (let [[lon-spec lat-spec] (condp = (:crs @state)
                                               :epsg4326 [:lipas.location.coordinates/lon
                                                          :lipas.location.coordinates/lat]
                                               :epsg3067 [:lipas.location.coordinates/lon-euref
                                                          :lipas.location.coordinates/lat-euref])]
                     [:<>
                      [mui/grid {:item true :xs 12}
                       [lui/select
                        {:style     {:min-width "150px"}
                         :label     "CRS"
                         :items     [{:value :epsg3067 :label "TM35FIN EUREF"}
                                     {:value :epsg4326 :label "WGS84"}]
                         :value     (:crs @state)
                         :on-change #(swap! state assoc :crs %1)}]]
                      [mui/grid {:item true :xs 6}
                       [lui/text-field
                        {:label     (condp = (:crs @state)
                                      :epsg4326 "Lon"
                                      :epsg3067 "N")
                         :type      "number"
                         :spec      lon-spec
                         :value     (:lon @state)
                         :on-change #(swap! state assoc :lon %1)}]]
                      [mui/grid {:item true :xs 6}
                       [lui/text-field
                        {:label     (condp = (:crs @state)
                                      :epsg4326 "Lat"
                                      :epsg3067 "E")
                         :type      "number"
                         :spec      lat-spec
                         :value     (:lat @state)
                         :on-change #(swap! state assoc :lat %1)}]]
                      [mui/grid {:item true}
                       [mui/button
                        {:color    "secondary"
                         :disabled (not (and
                                         (s/valid? lon-spec (:lon @state))
                                         (s/valid? lat-spec (:lat @state))))
                         :variant  "contained"
                         :on-click #(==> [::events/add-point-from-coords @state])}
                        [mui/icon "add_location"]
                        (tr :map/add-to-map)]]])))

               ;; Import geoms
               (when (= "import" @geom-tab)
                 (when (#{"LineString" "Polygon"} geom-type)
                   [:<>
                    ;; Supported formats helper text
                    [mui/grid {:item true :xs 12}
                     [mui/typography {:variant "body2"}
                      (tr :map.import/supported-formats import/import-formats-str)]]

                    ;; Open import dialog button
                    [mui/grid {:item true}
                     [mui/button
                      {:color    "secondary"
                       :variant  "contained"
                       :on-click #(==> [::events/toggle-import-dialog])}
                      (tr :map.import/tooltip)]]]))]

              ;; Modify new geom
              [mui/grid {:container true :spacing 1}

               [mui/grid {:item true}
                (when (not zoomed?)
                  [mui/typography {:variant "body2" :color "error"}
                   (tr :map/zoom-closer)])]

               [mui/grid {:item true :xs 12}
                [mui/typography {:variant "body2"}
                 (case geom-type
                   "LineString" (tr :map/modify-linestring)
                   "Polygon"    (tr :map/modify-polygon)
                   (tr :map/modify))]
                [mui/typography {:variant "caption" :style {:margin-top "0.5em"}}
                 (tr :map/edit-later-hint)]]

               ;; Add additional geom button
               (when (#{"LineString" "Polygon"} geom-type)
                 [mui/grid {:item true}
                  [mui/button
                   {:on-click #(==> [::events/start-adding-geom geom-type])
                    :variant  "contained"
                    :color    "secondary"}
                   (case geom-type
                     "LineString" (tr :map/draw-linestring)
                     "Polygon"    (tr :map/draw-polygon)
                     (tr :map/draw))]])

               ;; Delete geom
               (when (#{"LineString" "Polygon"} geom-type)
                 [mui/grid {:item true}
                  [mui/tooltip
                   {:title (case geom-type
                             "LineString" (tr :map/remove-linestring)
                             "Polygon"    (tr :map/remove-polygon)
                             (tr :map/remove))}
                   [:span
                    [mui/button
                     {:on-click #(if (= sub-mode :deleting)
                                   (==> [::events/stop-deleting-geom geom-type])
                                   (==> [::events/start-deleting-geom geom-type]))
                      :disabled (-> geom :features empty?)
                      :style    (when (= sub-mode :deleting)
                                  {:outline (str "2px solid " mui/secondary)})
                      :variant  "contained"}
                     [:> Eraser]]]]])

               ;; Split
               (when (#{"LineString"} geom-type)
                 [mui/grid {:item true}
                  [mui/tooltip {:title (tr :map/split-linestring)}
                   [:span
                    [mui/button
                     {:on-click #(if (= sub-mode :splitting)
                                   (==> [::events/stop-splitting-geom geom-type])
                                   (==> [::events/start-splitting-geom geom-type]))
                      :disabled (-> geom :features empty?)
                      :style    (when (= sub-mode :splitting)
                                  {:outline (str "2px solid " mui/secondary)})
                      :variant  "contained"}
                     [:> ContentCut]]]]])

               ;; Simplify
               (when (#{"LineString" "Polygon"} geom-type)
                 [mui/grid {:item true}
                  [mui/tooltip {:title (tr :map.tools/simplify)}
                   [:span
                    [mui/button
                     {:on-click #(==> [::events/open-simplify-tool])
                      :disabled (or (-> geom :features empty?)
                                    (= sub-mode :simplifying))
                      :style    (when (= sub-mode :simplifying)
                                  {:outline (str "2px solid " mui/secondary)})
                      :variant  "contained"}
                     [mui/icon "auto_fix_high"]]]]])

               ;; Delete vertices helper text
               (when (#{"LineString" "Polygon"} geom-type)
                 [mui/grid {:item true :xs 12}
                  [mui/typography {:variant "caption" :style {:margin-top "0.5em"}}
                   (tr :map/delete-vertices-hint)]])

               ;; Done button and undo / redo
               [mui/grid {:item true :xs 12}
                [mui/grid
                 {:container       true
                  :justify-content "flex-start"
                  :align-items     "center"
                  :spacing         1}

                 ;; Ready button
                 [mui/grid {:item true}
                  [mui/button
                   {:on-click #(==> [::events/finish-adding-geom geom type-code])
                    :variant  "contained"
                    :disabled (-> geom :features empty?)
                    :color    "secondary"}
                   (tr :general/done)]]
                 [mui/grid {:item true}
                  [mui/grid {:item true}

                   ;; Undo & Redo
                   [mui/grid {:container true}

                    ;; Undo
                    [mui/grid {:item true}
                     [mui/tooltip {:title "Undo"}
                      [:span
                       [mui/icon-button
                        {:disabled (not undo)
                         :on-click #(==> [::events/undo "new"])}
                        [mui/icon "undo"]]]]]

                    ;; Redo
                    [mui/grid {:item true}
                     [mui/tooltip {:title "Redo"}
                      [:span
                       [mui/icon-button
                        {:disabled (not redo)
                         :on-click #(==> [::events/redo "new"])}
                        [mui/icon "redo"]]]]]]]]]]

               ;; Retkikartta Problems
               (when (and (#{"LineString"} geom-type) problems?)
                 [mui/grid {:item true}
                  [mui/tooltip
                   {:placement "right"
                    :title     (str
                                (tr :map/retkikartta-problems-warning)
                                " "
                                (tr :map/retkikartta-checkbox-reminder))}
                   [:span
                    [lui/icon-text
                     {:icon "warning"
                      :text "Retkikartta.fi"}]]]])])))

        ;; Step3 content
        (when (= active-step (dec 3))
          [mui/grid {:container true :style {:flex-direction "column"}}

           ;; Tabs
           [mui/grid {:item true}
            [mui/tabs
             {:value     selected-tab
              :on-change #(==> [::events/select-new-sports-site-tab %2])
              :variant   "fullWidth"
              :style     {:margin-bottom "1em" :margin-top "1em"}}
             [mui/tab {:label (tr :lipas.sports-site/basic-data)}]
             [mui/tab {:label (tr :lipas.sports-site/properties)}]
             (when (and (contains? floorball-types (-> data :type :type-code))
                        (= :floorball floorball-visibility))
               [mui/tab {:label "Olosuhteet"}])]

            (case selected-tab

              ;; Basic info tab
              0 [mui/grid {:container true}
                 [mui/grid {:item true :xs 12}

                  [sports-sites/form
                   {:tr              tr
                    :edit-data       data
                    :read-only?      false
                    :types           (vals types)
                    :size-categories size-categories
                    :admins          admins
                    :owners          owners
                    :on-change       set-field
                    :sub-headings?   true
                    :lipas-id        0}]

                  [sports-sites/location-form
                   {:tr            tr
                    :read-only?    false
                    :cities        (vals cities)
                    :edit-data     (:location data)
                    :on-change     (partial set-field :location)
                    :sub-headings? true}]]]

              ;; Properties tab
              1 [sports-sites/properties-form
                 {:key        (-> data :type :type-code)
                  :tr         tr
                  :type-code  (-> data :type :type-code)
                  :read-only? false
                  :width      width
                  :on-change  (partial set-field :properties)
                  :edit-data  (:properties data)
                  :geoms      (-> data :location :geometries)
                  :problems?  problems?}]
              2 (condp contains? (-> data :type :type-code)

                  ;; Floorball specific
                  floorball-types
                  [:<>
                   [mui/tabs {:value "floorball" :variant "standard"}
                    [mui/tab {:value "floorball" :label "Salibandy"}]]
                   [floorball/form
                    {:tr           tr
                     :lipas-id     nil
                     :type-code    (-> data :type :type-code)
                     :read-only?   false
                     :on-change    set-field
                     :display-data data
                     :edit-data    data
                     :key          (-> data :type :type-code)}]]

                  ;; Football specific
                  #_#_football-types
                  [football/circumstances-form
                   {:tr           tr
                    :type-code    (or (-> edit-data :type :type-code) type-code)
                    :read-only?   (not editing?)
                    :on-change    (partial set-field :circumstances)
                    :display-data (:circumstances display-data)
                    :edit-data    (:circumstances edit-data)
                    :key          (-> edit-data :type :type-code)}]))]])]

       ;; Actions
       [mui/grid {:container true :align-items "flex-end"}
        [mui/grid {:item true :xs 12 :style {:height "50px"}}
         [lui/floating-container {:bottom 0 :background-color "transparent"}
          [mui/grid
           {:container   true
            :align-items "center"
            :spacing     1
            :style       {:padding "0.5em"}}

           [address-search-dialog]

           ;; Save
           (when data
             [mui/grid {:item true}
              [lui/save-button
               {:tooltip          (tr :actions/save)
                :disabled-tooltip (tr :actions/fill-required-fields)
                :disabled         (not save-enabled?)
                :on-click         #(==> [::events/save-new-site data])}]])

           [mui/grid {:item true}

            ;; Discard
            [lui/discard-button
             {:on-click #(==> [::events/discard-new-site])
              :tooltip  (tr :actions/discard)}]]

           ;; Address search button
           [mui/tooltip {:title (tr :map.address-search/tooltip)}
            [mui/grid {:item true}
             [mui/fab
              {:size     "small"
               :on-click #(==> [::events/toggle-address-search-dialog])}
              [:> MapSearchOutline]]]]]]]]])))

(defn default-tools [{:keys [tr logged-in?]}]
  (let [result-view (<== [:lipas.ui.search.subs/search-results-view])
        mode-name   (<== [::subs/mode-name])]
    [:<>
     [address-search-dialog]
     [lui/floating-container {:bottom 0 :background-color "transparent"}
      [mui/grid
       {:container   true
        :align-items "center"
        :spacing     1
        :style       {:padding-bottom "0.5em"}}

       ;; Create sports site btn
       (when logged-in?
         [mui/grid {:item true}
          [add-btn {:tr tr}]])

       ;; Address search btn
       [mui/tooltip {:title (tr :map.address-search/tooltip)}
        [mui/grid {:item true}
         [mui/fab
          {:size "small" :on-click #(==> [::events/toggle-address-search-dialog])}
          [:> MapSearchOutline]]]]

       ;; Create Excel report btn
       (when (= :list result-view)
         [mui/grid {:item true}
          [reports/dialog {:tr tr :btn-variant :fab}]])

       ;; Analysis tool btn
       (when (and logged-in? (= :list result-view))
         [mui/tooltip {:title (tr :map.demographics/tooltip)}
          [mui/grid {:item true}
           [mui/fab
            {:size     "small"
             :style    (when (= mode-name :analysis)
                         {:border (str "5px solid " mui/secondary)})
             :on-click #(==> (if (= mode-name :analysis)
                               [::events/hide-analysis]
                               [::events/show-analysis]))}
            [mui/icon "insights"]]]])]]]))

(defn add-view
  [{:keys [tr width]}]
  (let [add-mode  (<== [::subs/selected-add-mode])
        utp-user? (<== [:lipas.ui.user.subs/utp-user?])]
    [:<>
     (when utp-user?
       [mui/tabs
        {:value     add-mode
         :on-change #(==> [::events/select-add-mode %2])
         :variant   "fullWidth"}
        [mui/tab {:value "sports-site" :label (tr :lipas.sports-site/headline)}]
        [mui/tab {:value "loi" :label "Muu kohde"}]])

     (case add-mode
       "sports-site" [add-sports-site-view {:tr tr :width width}]
       "loi"         [loi/view])]))

(defn map-contents-view [{:keys [tr logged-in? width]}]
  (let [selected-site (<== [::subs/selected-sports-site])
        show-tools?   (<== [::subs/show-default-tools?])
        view          (<== [::subs/view])
        loi           (<== [:lipas.ui.loi.subs/selected-loi])]

    [:<>
     ;; Search, filters etc.
     (case view
       :adding   [add-view {:tr tr :width width}]
       :analysis [analysis/view]
       :site     [sports-site-view {:tr tr :site-data selected-site :width width}]
       :loi      [loi/view {:display-data loi}]
       :search   [search/search-view
                  {:tr              tr
                   :on-result-click (fn [{:keys [lipas-id]}]
                                      (==> [::events/show-sports-site lipas-id]))}])

     ;; Floating bottom toolbar
     (when show-tools?
       [:div {:style {:padding "0.5em"}}
        [default-tools {:tr tr :logged-in? logged-in?}]])]))

(defn map-view []
  (let [tr           (<== [:lipas.ui.subs/translator])
        logged-in?   (<== [:lipas.ui.subs/logged-in?])
        drawer-open? (<== [::subs/drawer-open?])
        width (mui/use-width)
        drawer-width (<== [::subs/drawer-width width])]

    [mui/grid {:container true :style {:height "100%" :width "100%"}}

     ;; Mini-nav
     [lui/floating-container
      {:right 0 :background-color "transparent"}
      [mui/grid
       {:container true :direction "column" :align-items "flex-end" :spacing 2}
       [mui/grid {:item true}
        [nav/mini-nav {:tr tr :logged-in? logged-in?}]]]]

     [mui/mui-theme-provider {:theme mui/jyu-theme-light}

      ;; Simplify tool container hack for Safari
      [simplify-tool-container]

      (when-not drawer-open?
        ;; Open Drawer Button
        [lui/floating-container {:background-color "transparent"}
         [mui/tool-bar {:disable-gutters true :style {:padding "8px 0px 0px 8px"}}
          [mui/fab
           {:size     (if (utils/mobile? width) "small" "medium")
            :on-click #(==> [::events/toggle-drawer])
            :color    "secondary"}
           [mui/icon "search"]]]])

      ;; Closable left sidebar drawer
      [mui/drawer
       {:variant    "persistent"
        :PaperProps {:style {:width drawer-width}}
        :SlideProps {:direction "down"}
        :open       drawer-open?}

       ;; Close button
       [mui/button
        {:on-click #(==> [::events/toggle-drawer])
         :style    {:min-height "36px" :margin-bottom "1em"}
         :variant  "outlined"
         :color    "inherit"}
        [mui/icon "expand_less"]]

       ;; Content
       [map-contents-view {:tr tr :logged-in? logged-in? :width width}]]]

     ;; Floating container (bottom right)
     [lui/floating-container
      {:bottom           "0.9em"
       :right            "3.5em"
       :background-color "transparent"}

      [mui/grid
       {:container   true
        :align-items "center"
        :spacing     1
        :style       {:background-color mui/gray2
                      :border-radius    "4px"}
        :wrap        "nowrap"}

       ;; Feedback btn
       #_[mui/grid {:item true}
          [mui/paper {:style {:background-color "rgba(255,255,255,0.9)"}}
           [feedback/feedback-btn]]]

       ;; Zoom to users location btn
       [mui/grid {:item true}
        [mui/paper {:style {:background-color "rgba(255,255,255,0.9)"}}
         [user-location-btn {:tr tr}]]]

       ;; Overlay selector
       [mui/grid {:item true}
        [mui/paper {:style {:background-color "rgba(255,255,255,0.9)"}}
         [overlay-selector {:tr tr}]]]

       ;; Base Layer switcher
       [mui/grid {:item true}
        [mui/paper
         {:elevation 1
          :style
          {:background-color "rgba(255,255,255,0.9)"
           :padding-left     "0.5em" :padding-right "0.5em"}}
         [layer-switcher {:tr tr}]]]]]

     ;; We use this div to bind Popper to OpenLayers overlay
     [:div {:id "popup-anchor"}]
     [popup]

     ;; The map
     [ol-map/map-outer]]))

(defn main []
  [:f> map-view])
