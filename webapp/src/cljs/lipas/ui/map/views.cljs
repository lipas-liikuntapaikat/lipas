(ns lipas.ui.map.views
  (:require ["@mui/material/Alert$default" :as Alert]
            ["@mui/material/MenuItem$default" :as MenuItem]
            ["@mui/material/Paper$default" :as Paper]
            ["@mui/material/TextField$default" :as TextField]
            ["@mui/material/Typography$default" :as Typography]
            ["mdi-material-ui/ContentCut$default" :as ContentCut]
            ["mdi-material-ui/ContentDuplicate$default" :as ContentDuplicate]
            ["mdi-material-ui/Eraser$default" :as Eraser]
            ["mdi-material-ui/FileUpload$default" :as FileUpload]
            ["mdi-material-ui/MapSearchOutline$default" :as MapSearchOutline]
            ["react" :as react]
            [clojure.string :as str]
            [lipas.data.activities :as activities-data]
            [lipas.schema.common :as common-schema]
            [malli.core :as m]
            [lipas.data.ptv :as ptv-data]
            [lipas.data.sports-sites :as ss]
            [lipas.roles :as roles]
            [lipas.ui.accessibility.views :as accessibility]
            [lipas.ui.analysis.views :as analysis]
            [lipas.ui.analysis.heatmap.subs :as heatmap-subs]
            [lipas.ui.components.autocompletes :as autocompletes]
            [lipas.ui.components.buttons :as buttons]
            [lipas.ui.components.dialogs :as dialogs]
            [lipas.ui.components.layouts :as layouts]
            [lipas.ui.components.selects :as selects]
            [lipas.ui.components.text-fields :as text-fields]
            [lipas.ui.components.misc :as misc]
            [lipas.ui.loi.views :as loi]
            [lipas.ui.map.events :as events]
            [lipas.ui.map.import :as import]
            [lipas.ui.map.map :as ol-map]
            [lipas.ui.map.subs :as subs]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/Checkbox$default" :as Checkbox]
            ["@mui/material/Drawer$default" :as Drawer]
            ["@mui/material/Fab$default" :as Fab]
            ["@mui/material/Grid$default" :as Grid2]
            ["@mui/material/GridLegacy$default" :as Grid]
            ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/IconButton$default" :as IconButton]
            ["@mui/material/List$default" :as List]
            ["@mui/material/ListItem$default" :as ListItem]
            ["@mui/material/ListItemButton$default" :as ListItemButton]
            ["@mui/material/ListItemIcon$default" :as ListItemIcon]
            ["@mui/material/ListItemText$default" :as ListItemText]
            ["@mui/material/Menu$default" :as Menu]
            ["@mui/material/Popper$default" :as Popper]
            ["@mui/material/Slide$default" :as Slide]
            ["@mui/material/Slider$default" :as Slider]
            ["@mui/material/Stack$default" :as Stack]
            ["@mui/material/Step$default" :as Step]
            ["@mui/material/StepLabel$default" :as StepLabel]
            ["@mui/material/Stepper$default" :as Stepper]
            ["@mui/material/Tab$default" :as Tab]
            ["@mui/material/Table$default" :as Table]
            ["@mui/material/TableBody$default" :as TableBody]
            ["@mui/material/TableCell$default" :as TableCell]
            ["@mui/material/TableContainer$default" :as TableContainer]
            ["@mui/material/TableHead$default" :as TableHead]
            ["@mui/material/TableRow$default" :as TableRow]
            ["@mui/material/Tabs$default" :as Tabs]
            ["@mui/material/Toolbar$default" :as Toolbar]
            ["@mui/material/Tooltip$default" :as Tooltip]
            [lipas.ui.mui :as mui]
            [lipas.ui.navbar :as nav]
            [lipas.ui.ptv.site-view :as ptv-site]
            [lipas.ui.ptv.views :as ptv]
            [lipas.ui.reminders.views :as reminders]
            [lipas.ui.reports.views :as reports]
            [lipas.ui.search.views :as search]
            [lipas.ui.sports-sites.activities.views :as activities]
            [lipas.ui.sports-sites.events :as sports-site-events]
            [lipas.ui.sports-sites.floorball.views :as floorball]
            [lipas.ui.sports-sites.views :as sports-sites]
            [lipas.ui.user.subs :as user-subs]
            [lipas.ui.utils :refer [<== ==>] :as utils]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [reagent.hooks :as hooks]))

;; TODO: Juho later This pattern makes development inconvenient as
;; the component might crash and shadow-cljs reloads don't update it.
;; The pattern is used to change the tool properties
;; between editing existing sites and adding new site.
(defonce simplify-tool-component (r/atom nil))

(defn rreset!
  "Like `reset!` but returns nil"
  [a newval]
  (reset! a newval)
  nil)

(defn address-search-dialog []
  (let [tr (<== [:lipas.ui.subs/translator])
        open? (<== [::subs/address-search-dialog-open?])
        value (<== [::subs/address-search-keyword])
        toggle (fn [] (==> [::events/toggle-address-search-dialog]))
        results (<== [::subs/address-search-results])]
    [dialogs/dialog
     {:open? open?
      :title (tr :map.address-search/title)
      :on-close toggle
      :save-enabled? false
      :cancel-label (tr :actions/close)}
     [:> Grid {:container true}
      [:> Grid {:item true :xs 12}
       [text-fields/text-field
        {:auto-focus true
         :fullWidth true
         :defer-ms 150
         :label (tr :search/search)
         :value value
         :on-change #(==> [::events/update-address-search-keyword %])}]]
      [:> Grid {:item true :xs 12}
       (into
        [:> List]
        (for [m results]
          [:> ListItemButton {:on-click #(==> [::events/show-address m])}
           [:> ListItemText
            (:label m)]]))]]]))

(defn restore-site-backup-dialog []
  (let [tr (<== [:lipas.ui.subs/translator])
        open? (<== [::subs/restore-site-backup-dialog-open?])
        lipas-id (<== [::subs/restore-site-backup-lipas-id])
        error (<== [::subs/restore-site-backup-error])]
    [dialogs/dialog
     {:open? open?
      :title (tr :map.tools/restore-backup-tooltip)
      :on-close #(==> [::events/close-restore-site-backup-dialog])
      :save-enabled? false
      :cancel-label (tr :actions/close)}
     [:> Grid {:container true}
      [:> Grid {:item true :xs 12}
       [:> Grid {:item true}
        [:input
         {:type "file"
          :accept ".json"
          :on-change #(==> [::events/restore-site-backup
                            (-> % .-target .-files)
                            lipas-id])}]]]
      (when error
        [:> Grid {:item true :xs 12}
         [:> Typography {:variant "h6"} "Error"]
         (pr-str error)])]]))

(defn simplify-tool-container
  []
  (when-let [open? (<== [::subs/simplify-dialog-open?])]
    [:> Slide {:direction "up" :in open?}
     [:r> (react/forwardRef
           (fn [_props ref]
             (r/as-element [layouts/floating-container {:ref ref :bottom 12 :left 550}
                            @simplify-tool-component])))]]))

(defn simplify-tool
  [{:keys [tr on-change on-close]
    :or {on-close #(==> [::events/close-simplify-tool])}}]
  (let [tolerance (<== [::subs/simplify-tolerance])]
    [:> Paper {:style {:padding "1em"} :elevation 5}
     [:> Grid {:container true}

      ;; Header
      [:> Grid {:item true :xs 12}
       [:h4 (tr :map.tools.simplify/headline)]]

      ;; Slider
      [:> Grid {:item true :xs 12}

       [:> Grid {:container true :spacing 2}

        ;; Less
        [:> Grid {:item true}
         [:> Typography (tr :general/less)]]

        ;; The Slider
        [:> Grid {:item true :xs true}
         [:> Slider
          {:size "small"
           :on-change #(==> [::events/set-simplify-tolerance %2])
           :value tolerance
           :marks (mapv (fn [n] {:label (str n) :value n}) (range 11))
           :step 0.5
           :min 0
           :max 10}]]

        ;; More
        [:> Grid {:item true}
         [:> Typography (tr :general/more)]]]]

      ;; Buttons
      [:> Grid {:item true :xs 12}
       [:> Grid {:container true :spacing 1}

        ;; OK
        [:> Grid {:item true}
         [:> Button
          {:variant "contained"
           :color "secondary"
           :on-click #(on-change tolerance)}
          "OK"]]

        ;; Cancel
        [:> Grid {:item true}
         [:> Button
          {:variant "outlined"
           :on-click on-close}
          (tr :actions/cancel)]]]]]]))

(defn layer-switcher [{:keys [tr]}]
  (let [basemaps {:taustakartta (tr :map.basemap/taustakartta)
                  :maastokartta (tr :map.basemap/maastokartta)
                  :ortokuva (tr :map.basemap/ortokuva)}
        basemap (<== [::subs/basemap])]
    [:> Grid {:container true :direction "column"}
     [:> Grid {:item true}
      [selects/select
       {:items basemaps
        :value (:layer basemap)
        :label-fn second
        :value-fn first
        :on-change #(==> [::events/select-basemap %])}]]
     [:> Grid {:item true}
      [:> Typography {:variant "caption"}
       (tr :map.basemap/copyright)]]]))

(defn overlay-selector
  [{:keys [tr]}]
  (r/with-let [anchor-el (r/atom nil)]
    (let [overlays {:light-traffic
                    {:label (tr :map.overlay/light-traffic)
                     :label2 "© Väylävirasto"
                     :icon [:> Icon "timeline"]}
                    :retkikartta-snowmobile-tracks
                    {:label (str (tr :map.overlay/retkikartta-snowmobile-tracks) " 2025")
                     :label2 "© Metsähallitus"
                     :icon [:> Icon
                            {:style {:color "#0000FF"}}
                            "timeline"]}
                    :mml-kiinteisto
                    {:label (tr :map.overlay/mml-kiinteisto)
                     :label2 "© Maanmittauslaitos"
                     :icon [:> Icon
                            {:style {:color "red"}}
                            "timeline"]}
                    :mml-kiinteistotunnukset
                    {:label (tr :map.overlay/mml-property-identifiers)
                     :label2 "© Maanmittauslaitos"
                     :icon [:> Icon
                            {:style {:color "black"}}
                            "text_format"]}
                    :mml-kuntarajat
                    {:label (tr :map.overlay/municipal-boundaries)
                     :label2 "© Maanmittauslaitos"
                     :icon [:> Icon
                            {:style {:color "#6222BC"}}
                            "timeline"]}}
          selected-overlays (<== [::subs/selected-overlays])]
      [:<>
       (into

        [:> Menu
         {:open (boolean @anchor-el)
          :anchorEl @anchor-el
          :anchorOrigin {:vertical "top" :horizontal "left"}
          :transformOrigin {:vertical "bottom" :horizontal "left"}
          :on-close #(reset! anchor-el nil)}]

        (for [[k {:keys [label label2 icon]}] overlays
              :let [v (contains? selected-overlays k)]]
          [:> MenuItem
           {:on-click #(==> [::events/toggle-overlay k])}
           [:> ListItemIcon
            [:> Checkbox
             {:checked (boolean v)
              :size "medium"
              :value (str v)
              :color "secondary"
              :on-change #()}]]
           [:> ListItemText
            {:primaryTypographyProps {:style {:font-size "0.9em" :margin-right "2em"}}
             :secondaryTypographyProps {:style {:font-size "0.7em" :margin-right "2em"}}
             :primary label :secondary label2}]
           [:> ListItemIcon
            icon]]))

       [:> Grid {:item true}
        [:> Tooltip {:title (tr :map.overlay/tooltip)}
         [:> IconButton
          {:color (if @anchor-el "secondary" "default")
           :on-click
           (fn [evt] (reset! anchor-el (if @anchor-el nil (.-currentTarget evt))))}
          [:> Icon "layers"]]]]])))

(defn basemap-transparency-selector
  [{:keys [tr]}]
  (r/with-let [anchor-el (r/atom nil)]
    (let [opacity (<== [::subs/basemap-opacity])]
      [:<>
       [:> Popper
        {:id "basemap-transparency-selector"
         :placement "top"
         :open (some? @anchor-el)
         :anchorEl @anchor-el}
        [:> Paper
         {:style
          {:width "200px"
           :padding-right "2em"
           :padding-left "2em"
           :padding-top "0.5em"
           :padding-bottom "0.5em"
           :margin-bottom "1em"}}
         #_[:> Typography {:variant "caption"} (tr :map.basemap/transparency)]
         [:> Slider
          {:size "small"
           :value (- 1 opacity)
           :on-change #(==> [::events/set-basemap-opacity (- 1 %2)])
           :marks [{:value 0 :label "0%"}
                   {:value 1 :label "100%"}]
           :step 0.05
           :min 0.0
           :max 1.0}]]]
       [:> Tooltip {:title (tr :map.basemap/transparency)}
        [:> IconButton
         {:on-click (fn [evt]
                      (reset! anchor-el (if @anchor-el nil (.-currentTarget evt))))}
         [:> Icon {:color (if @anchor-el "secondary" "default")} "opacity"]]]])))

(defn user-location-btn
  [{:keys [tr]}]
  [:> Tooltip {:title (tr :map/zoom-to-user)}
   [:> IconButton {:on-click #(==> [::events/zoom-to-users-position])}
    [:> Icon {:color "inherit" :font-size "medium"}
     "my_location"]]])

(defn filter-by-term [term table-data]
  (let [lower-case-term (str/lower-case term)]
    (filter #(or (str/includes? (str/lower-case (% :name)) lower-case-term)
                 (str/includes? (str/lower-case (% :geometry-type)) lower-case-term)
                 (str/includes? (str/lower-case (% :description)) lower-case-term)
                 (str/includes? (str/lower-case (str/join (% :tags))) lower-case-term))
            table-data)))

(defn type-helper-table [{:keys [tr on-select types]}]
  (r/with-let [search-term (r/atom "")
               table-data (<== [:lipas.ui.sports-sites.subs/type-table types])]
    (let [filtered-table-data (filter-by-term @search-term table-data)
          sorted-and-filtered-table-data (sort-by :name filtered-table-data)]
      [:> Grid {:container true}
       [:> Grid {:item true :xs 12}
        [:> TextField {:label (tr :search/search)
                         :xs 3
                         :on-change #(reset! search-term (-> % .-target .-value))
                         :placeholder nil
                         :variant "standard"}]]
       [:> Grid {:item true :xs 12}
        [:> TableContainer
         [:> Table
          [:> TableHead
           [:> TableRow
            [:> TableCell (tr :type/name)]
            [:> TableCell (tr :type/geometry)]
            [:> TableCell (tr :general/description)]]]
          (into
           [:> TableBody {:component "th" :scope "row"}]
           (for [row sorted-and-filtered-table-data]
             [:> TableRow {:on-click #(on-select (row :type-code))}
              [:> TableCell (row :name)]
              [:> TableCell (->> row :geometry-type (keyword :type) tr)]
              [:> TableCell (row :description)]]))]]]])))

(defn type-selector-single [{:keys [tr value on-change types]}]
  (r/with-let [selected-type (r/atom value)
               geom-help-open? (r/atom false)]
    (let [locale (tr)]
      [:<>

       ;; Modal
       [dialogs/dialog {:open? @geom-help-open?
                    :cancel-label (tr :actions/close)
                    :title (tr :type/name)
                    :max-width "xl"
                    :on-close #(swap! geom-help-open? not)}

        ;; Apu ankka table
        [type-helper-table {:tr tr
                            :types types
                            :geom-help-open? geom-help-open?
                            :on-select (fn [element]
                                         (swap! geom-help-open? not)
                                         (reset! selected-type element))}]]
       [:> Grid {:container true}

        ;; Autocomplete
        [:> Grid {:item true :xs 11}
         [autocompletes/autocomplete
          {:multi? false
           :items (vals types)
           :value @selected-type
           :label (tr :type/name)
           :value-fn :type-code
           :label-fn (comp locale :name)
           :on-change #(reset! selected-type %)}]]

        ;; Apu ankka button
        [:> Grid {:item true :xs 1}
         [:> IconButton
          {:xs 1
           :type "button"
           :on-click #(swap! geom-help-open? not)}
          [:> Icon "help"]]]

        ;; Description + OK button
        (when @selected-type
          [:> Grid {:item true :xs 12}
           [:> Typography {:style {:margin-top "1em" :margin-bottom "1em"}}
            (get-in types [@selected-type :description locale])]
           [:> Button {:on-click #(on-change @selected-type)
                        :auto-focus true
                        :variant "contained"
                        :color "secondary"}
            "OK"]])]])))

(defmulti popup-body :type)

(defmethod popup-body :default [popup]
  (let [name' (-> popup :data :features first :properties :name)
        status (-> popup :data :features first :properties :status)
        tr (-> popup :tr)
        locale (tr)]
    [:> Paper
     {:style
      {:padding "0.5em"
       :width (when (< 100 (count name')) "150px")}}
     [:> Typography {:variant "body2"}
      name']
     (when-not (#{"active"} status)
       [:> Typography {:variant "body2" :color "error"}
        (get-in ss/statuses [status locale])])]))

(defmethod popup-body :loi [popup]
  (let [loi-type (-> popup :data :features first :properties :loi-type)
        loi-category (-> popup :data :features first :properties :loi-category)
        #_#_tr (-> popup :tr)
        texts (<== [:lipas.ui.loi.subs/popup-localized loi-type loi-category])]
    [:> Paper
     {:style
      {:padding "0.5em"
       :width (when (< 100 (count loi-type)) "150px")}}
     [:> Typography {:variant "body2"}
      (:loi-type texts)]
     [:> Typography {:variant "caption"}
      (:loi-category texts)]]))

(defmethod popup-body :population [popup]
  (let [tr (<== [:lipas.ui.subs/translator])
        zone-labels (<== [:lipas.ui.analysis.reachability.subs/zones-popup-labels])
        metric (<== [:lipas.ui.analysis.reachability.subs/selected-travel-metric])
        data (-> popup :data :features first :properties)
        zone-id (keyword (:zone data))]
    [:> Paper
     {:style
      {:padding "0.5em"}}
     [:> Table {:padding "normal" :size "small"}
      [:> TableBody

       ;; Population
       [:> TableRow {:style {:height "24px"}}
        [:> TableCell
         [:> Typography {:variant "caption" :noWrap true}
          (tr :analysis/population)]]
        [:> TableCell
         [:> Typography {:variant "caption" :no-wrap true}
          (if-let [v (:vaesto data)] v "<10")]]]

       ;; Profile / Zone / Metric
       [:> TableRow {:style {:height "24px"}}
        [:> TableCell
         [:> Typography {:variant "caption" :noWrap true}
          (if (= metric :travel-time)
            (tr :analysis/travel-time)
            (tr :analysis/distance))]]
        [:> TableCell
         [:> Typography {:variant "caption" :no-wrap true}
          (get-in zone-labels [[metric zone-id]])]]]]]]))

(defmethod popup-body :school [popup]
  (let [data (-> popup :data :features first :properties)]
    [:> Paper
     {:style
      {:padding "0.5em"}}
     [:> Typography {:variant "body2"}
      (:name data)]
     [:> Typography {:variant "caption"}
      (:type data)]]))

(defmethod popup-body :diversity-grid [popup]
  (let [tr (<== [:lipas.ui.subs/translator])
        data (-> popup :data :features first :properties)]
    [:> Paper
     {:style {:padding "0.5em"}}
     [:> Table {:padding "normal" :size "small"}
      [:> TableBody

       ;; Diversity index
       [:> TableRow
        [:> TableCell
         [:> Typography {:variant "caption"} (tr :analysis/diversity-idx)]]
        [:> TableCell
         [:> Typography {:variant "caption" :no-wrap true}
          (:diversity_idx data)]]]

       ;; Population
       [:> TableRow
        [:> TableCell
         [:> Typography {:variant "caption"} (tr :analysis/population)]]
        [:> TableCell
         [:> Typography {:variant "caption" :no-wrap true}
          (or (:population data) "<10")]]]]]]))

(defmethod popup-body :diversity-area [popup]
  (let [tr (<== [:lipas.ui.subs/translator])
        data (-> popup :data :features first :properties)]
    [:> Paper
     {:style
      {:padding "0.5em"}}

     (if (or (:population-weighted-mean data) (:population data))
       ;; Results table
       [:> Table {:padding "normal" :size "small"}
        [:> TableBody

         ;; Area name
         (when-let [s (:nimi data)]
           [:> TableRow
            [:> TableCell
             [:> Typography {:variant "caption"}
              "Alue"]]
            [:> TableCell
             [:> Typography {:variant "caption" :no-wrap true}
              s]]])

         ;; Postal code
         (when-let [s (:posti_alue data)]
           [:> TableRow
            [:> TableCell
             [:> Typography {:variant "caption"}
              "Postinumero"]]
            [:> TableCell
             [:> Typography {:variant "caption" :no-wrap true}
              s]]])

         ;; Population weighted mean
         [:> TableRow
          [:> TableCell
           [:> Typography {:variant "caption"}
            (tr :analysis/population-weighted-mean)]]
          [:> TableCell
           [:> Typography {:variant "caption" :no-wrap true}
            (utils/round-safe
             (:population-weighted-mean data))]]]

         ;; Population
         [:> TableRow
          [:> TableCell
           [:> Typography {:variant "caption"}
            (tr :analysis/population)]]
          [:> TableCell
           [:> Typography {:variant "caption" :no-wrap true}
            (let [n (:population data 0)]
              (if (< n 10) "<10" n))]]]

         ;; Mean
         #_[:> TableRow
            [:> TableCell
             [:> Typography (tr :analysis/mean)]]
            [:> TableCell
             (:diversity-idx-mean data)]]

         ;; Median
         #_[:> TableRow
            [:> TableCell
             [:> Typography (tr :analysis/median)]]
            [:> TableCell
             (:diversity-idx-median data)]]

         ;; Mode
         #_[:> TableRow
            [:> TableCell
             [:> Typography (tr :analysis/mode)]]
            [:> TableCell
             (when (seq (:diversity-idx-mode data))
               (str/join "," (:diversity-idx-mode data)))]]]]

       ;; No data available
       [:div {:style {:width "200px" :padding "0.5em 0.5em 0em 0.5em"}}
        [:> Typography {:paragraph true}
         (str (:nimi data) " " (:posti_alue data))]
        [:> Typography {:paragraph true :variant "caption"}
         "Analyysiä ei ole tehty"]
        [:> Typography {:paragraph true :variant "caption"}
         "Klikkaa aluetta hiirellä tai valitse alue taulukosta."]])]))

(r/defc route-part-difficulty [{:keys [data]}]
  (let [{:keys [lipas-id fid]} data
        tr @(rf/subscribe [:lipas.ui.subs/translator])
        locale (tr)
        properties @(rf/subscribe [::subs/edit-geom-properties fid])
        value (:route-part-difficulty properties)]
    [:> Paper
     {:sx
      #js {:padding 2
           :width "350px"}}
     [:> TextField
      {:label (tr :map/route-part-difficulty)
       :select true
       :fullWidth true
       :value (or value "")
       :onChange (fn [e]
                   (rf/dispatch [::events/set-route-part-difficulty lipas-id fid (.. e -target -value)]))}
      [:> MenuItem
       {:key "empty"
        :value ""}
       "-"]
      (for [[k {:keys [label description]}] activities-data/cycling-route-part-difficulty]
        [:> MenuItem
         {:key k
          :value k
          :sx #js {:flexDirection "column"
                   :alignItems "flex-start"
                   :maxWidth "350px"}}
         [:> Typography
          (get label locale)]
         [:> Typography
          {:sx #js {:fontSize "body2.fontSize"
                    :whiteSpace "normal"}}
          (get description locale)]])]]))

(defmethod popup-body :route-part-difficulty [popup]
  [route-part-difficulty {:data (:data popup)}])

(defmethod popup-body :heatmap [popup]
  (let [tr (<== [:lipas.ui.subs/translator])
        locale (tr)
        data (-> popup :data :features first :properties)
        dimension (<== [::heatmap-subs/dimension])
        weight-by (<== [::heatmap-subs/weight-by])
        ;; Get type labels for type-distribution dimension
        types-db (<== [:lipas.ui.sports-sites.subs/all-types])]

    [:> Paper
     {:style
      {:padding "0.5em"
       :min-width "200px"}}

     [:> Stack {:direction "column"}
      ;; Facility count
      [:> Typography {:variant "body2" :style {:font-weight "bold"}}
       (str (:doc_count data) " " (if (= 1 (:doc_count data))
                                    (tr :analysis/heatmap-popup-facility-singular)
                                    (tr :analysis/heatmap-popup-facility-plural)))]

      ;; Type distribution for type-distribution dimension
      (when (and (= :type-distribution dimension) (:types data))
        [:<>
         [:> Typography {:variant "caption" :style {:margin-top "0.5em" :font-weight "bold"}}
          (str (tr :analysis/heatmap-popup-top-types) (count (:types data)) ")")]
         (into [:> List {:dense true :style {:padding 0}}]
               (for [{:keys [key doc_count]} (take 5 (sort-by :doc_count > (:types data)))
                     :let [type-label (get-in types-db [key :name locale] (str (tr :analysis/heatmap-popup-type-fallback) key))]]
                 [:> ListItem {:style {:padding "2px 0"}}
                  [:> Typography {:variant "caption"}
                   (str type-label ": " doc_count)]]))])

      ;; Activities for activities dimension
      (when (and (= :activities dimension) (:activities data))
        [:<>
         [:> Typography {:variant "caption" :style {:margin-top "0.5em" :font-weight "bold"}}
          (tr :analysis/heatmap-popup-activities)]
         (into [:> List {:dense true :style {:padding 0}}]
               (for [{:keys [key doc_count]} (take 5 (sort-by :doc_count > (:activities data)))]
                 [:> ListItem {:style {:padding "2px 0"}}
                  [:> Typography {:variant "caption"}
                   (str key ": " doc_count)]]))])]

     ;; Grid reference (optional, for debugging)
     #_[:> Typography {:variant "caption" :color "textSecondary" :style {:margin-top "0.5em"}}
        (str "Grid: " (:grid_key data))]]))

(r/defc popup [{:keys [popup-ref]}]
  (let [{:keys [data placement]
         :as popup'} (<== [::subs/popup])
        tr (<== [:lipas.ui.subs/translator])
        [anchor-el set-anchor-el] (hooks/use-state nil)]
    [:<>
     [:div {:ref (fn [el]
                   (set-anchor-el el)
                   (set! (.-current popup-ref) el))}]
     (when (seq data)
       [:> Popper
        {:open (boolean (seq data))
         :placement (or placement "top-end")
         ;; FIXME:
         :anchor-el anchor-el
         :container anchor-el
         :modifiers [{:name "offset"
                      :options {:offset [0 10]}}]}
        [popup-body (assoc popup' :tr tr)]])]))

(defn set-field
  [lipas-id & args]
  (==> [::sports-site-events/edit-field lipas-id (butlast args) (last args)]))

(defn address-locator
  [{:keys [tr lipas-id cities]}]
  (let [first-point (<== [:lipas.ui.sports-sites.subs/editing-first-point lipas-id])
        dialog-open? (<== [::subs/address-locator-dialog-open?])
        selected-address (<== [::subs/address-locator-selected-address])
        addresses (<== [::subs/address-locator-addresses])]

    [:<>

     ;; Dialog
     [dialogs/dialog
      {:open? dialog-open?
       :title (tr :map.resolve-address/choose-address)
       :save-label "Ok"
       :save-enabled? (some? selected-address)
       :on-close #(==> [::events/close-address-locator-dialog])
       :cancel-label (tr :actions/cancel)
       :on-save (fn []
                  (==> [::events/close-address-locator-dialog])
                  (==> [::events/populate-address-with-reverse-geocoding-results lipas-id cities {:features [{:properties selected-address}]}]))}

      [:> Grid {:container true :spacing 2}

       ;; Helper text 1
       [:> Grid {:item true :xs 12}
        [:> Typography (tr :map.resolve-address/helper-text1)]]

       ;; Helper text 2
       [:> Grid {:item true :xs 12}
        [:> Typography (tr :map.resolve-address/helper-text2)]]

       ;; Address selector
       [:> Grid {:item true :xs 12}
        [autocompletes/autocomplete
         {:label (tr :map.resolve-address/addresses)
          :items addresses
          :value selected-address
          :label-fn :label
          #_#_:label-fn #(str (:label %) " " (:confidence %) " " (:distance %))
          :value-fn identity
          :sort-fn (juxt (comp - :confidence) :distance)
          :on-change #(==> [::events/select-address-locator-address %])}]]]]

     ;; Button
     [buttons/locator-button
      {:tooltip (tr :map.resolve-address/tooltip)
       :on-click (fn []
                   (==> [::events/open-address-locator-dialog])
                   (==> [::events/resolve-address
                         {:lon (first first-point)
                          :lat (second first-point)
                          :on-success [:lipas.ui.map.events/on-reverse-geocoding-success]}]))}]]))

(defn get-map-tool-items [{:keys [tr lipas-id type-code sub-mode activity-value edit-activities? editing? can-edit-map? geom-type]}]
  (->> [;; Import geom
        (when (and editing? can-edit-map? (#{"LineString" "Polygon"} geom-type))
          [:> MenuItem {:on-click #(do
                                       (==> [::events/close-more-tools-menu])
                                       (==> [::events/toggle-import-dialog]))}
           [:> ListItemIcon
            [:> FileUpload]]
           [:> ListItemText (tr :map.import/tooltip)]])

        ;; Simplify
        (when (and editing? can-edit-map? (#{"LineString" "Polygon"} geom-type))
          [:> MenuItem {:on-click #(do
                                       (==> [::events/close-more-tools-menu])
                                       (==> [::events/open-simplify-tool]))}
           [:> ListItemIcon
            [:> Icon "auto_fix_high"]]
           [:> ListItemText (tr :map.tools/simplify)]])

        ;; Draw hole
        (when (and editing? can-edit-map? (#{"Polygon"} geom-type))
          [:> MenuItem
           {:on-click
            #(do
               (==> [::events/close-more-tools-menu])
               (==> [::events/start-editing lipas-id :drawing-hole geom-type]))}
           [:> ListItemIcon
            [:> Icon
             {:color (if (= sub-mode :drawing-hole) "secondary" "inherit")}
             "vignette"]]
           [:> ListItemText (tr :map/draw-hole)]])

        ;; Add new geom
        (when (and editing? can-edit-map? (#{"LineString" "Polygon"} geom-type))

          [:> MenuItem
           {:on-click
            #(do
               (==> [::events/close-more-tools-menu])
               (==> [::events/start-editing lipas-id :drawing geom-type]))}
           [:> ListItemIcon
            (if (= geom-type "LineString")
              [:> Icon
               {:color (if (= sub-mode :drawing)
                         "secondary"
                         "inherit")} "timeline"]
              [:> Icon {:color (if (= sub-mode :drawing) "secondary" "inherit")}
               "change_history"])]
           [:> ListItemText (case geom-type
                                 "LineString" (tr :map/draw-linestring)
                                 "Polygon" (tr :map/draw-polygon))]])

        ;; Delete geom
        (when (and editing? can-edit-map? (#{"LineString" "Polygon"} geom-type))
          [:> MenuItem
           {:on-click
            #(do
               (==> [::events/close-more-tools-menu])
               (==> [::events/start-editing lipas-id :deleting geom-type]))}
           [:> ListItemIcon
            [:> Eraser
             {:color (if (= sub-mode :deleting) "secondary" "inherit")}]]
           [:> ListItemText (case geom-type
                                 "LineString" (tr :map/remove-linestring)
                                 "Polygon" (tr :map/remove-polygon))]])

        ;; Split linestring
        (when (and editing? can-edit-map? (#{"LineString"} geom-type))
          [:> MenuItem
           {:on-click
            #(do
               (==> [::events/close-more-tools-menu])
               (==> [::events/start-editing lipas-id :splitting geom-type]))}
           [:> ListItemIcon
            [:> ContentCut
             {:color (if (= sub-mode :splitting) "secondary" "inherit")}]]
           [:> ListItemText (tr :map/split-linestring)]])

        ;; Travel direction
        (when (and editing?
                   (or can-edit-map? edit-activities?)
                   (#{"LineString"} geom-type)
                   ;; check for activity = paddling?
                   ;; doesn't include 5150 now, but that would be Points
                   (#{;; paddling routes
                      4451 4452
                      ;; ski routes
                      4402 4440} type-code))
          [:> MenuItem
           {:on-click
            #(do
               (==> [::events/close-more-tools-menu])
               (==> [::events/start-editing lipas-id :travel-direction geom-type]))}
           [:> ListItemIcon
            [:> Icon
             {:color (if (= sub-mode :travel-direction) "secondary" "inherit")}
             "turn_slight_right"]]
           [:> ListItemText (tr :map/travel-direction)]])

        ;; Route part difficulty
        (when (and editing?
                   edit-activities?
                   (#{"LineString"} geom-type)
                   (= "cycling" activity-value))
          [:> MenuItem
           {:on-click
            #(do
               (==> [::events/close-more-tools-menu])
               (==> [::events/start-editing lipas-id :route-part-difficulty geom-type]))}
           [:> ListItemIcon
            [:> Icon
             {:color (if (= sub-mode :route-part-difficulty) "secondary" "inherit")}
             "warning"]]
           [:> ListItemText (tr :map/route-part-difficulty)]])

        ;; Download backup
        (when editing?
          [:> MenuItem
           {:on-click
            #(do
               (==> [::events/download-site-backup lipas-id])
               (==> [::events/close-more-tools-menu]))}
           [:> ListItemIcon
            [:> Icon {:color "inherit"} "cloud_download"]]
           [:> ListItemText (tr :map.tools/download-backup-tooltip)]])

        ;; Restore backup
        (when editing?
          [:> MenuItem
           {:on-click
            #(do
               (==> [::events/open-restore-site-backup-dialog lipas-id])
               (==> [::events/close-more-tools-menu]))}
           [:> ListItemIcon
            [:> Icon {:color "inherit"} "cloud_upload"]]
           [:> ListItemText (tr :map.tools/restore-backup-tooltip)]])

        ;; Edit tool
        (when (and editing? can-edit-map? (#{"LineString" "Polygon"} geom-type))
          [:> MenuItem
           {:on-click
            #(do
               (==> [::events/close-more-tools-menu])
               (==> [::events/start-editing lipas-id :editing geom-type]))}
           [:> ListItemIcon
            [:> Icon
             {:color (if (= sub-mode :editing) "secondary" "inherit")}
             "edit"]]
           [:> ListItemText (tr :map.tools/edit-tool)]])]
       (keep identity)))

;; Works as both display and edit views
(defn sports-site-view
  [{:keys [tr site-data width]}]
  (let [display-data (:display-data site-data)
        lipas-id (:lipas-id display-data)
        edit-data (:edit-data site-data)

        role-site-ctx (roles/site-roles-context display-data)

        type-code (-> display-data :type :type-code)
        #_#_football-types (<== [:lipas.ui.sports-sites.football.subs/type-codes])

        accessibility-type? (<== [:lipas.ui.accessibility.subs/accessibility-type? type-code])

        activity-value (<== [:lipas.ui.sports-sites.activities.subs/activity-value-for-type-code type-code])
        view-activities? (<== [:lipas.ui.sports-sites.activities.subs/show-activities? activity-value role-site-ctx])
        edit-activities? (<== [:lipas.ui.sports-sites.activities.subs/edit-activities? activity-value role-site-ctx])

        floorball-types (<== [:lipas.ui.sports-sites.floorball.subs/type-codes])
        floorball-type? (contains? floorball-types type-code)
        view-floorball? (when floorball-type? (<== [:lipas.ui.user.subs/check-privilege role-site-ctx :floorball/view]))
        edit-floorball? (when floorball-type? (<== [:lipas.ui.user.subs/check-privilege role-site-ctx :floorball/edit]))

        hide-actions? (<== [::subs/hide-actions?])

        ;; FIXME: Bad pattern to combine n subs into one
        {:keys [types admins owners editing? edits-valid?
                problems? editing-allowed? delete-dialog-open?
                can-publish? logged-in? size-categories sub-mode
                geom-type portal save-in-progress? undo redo
                more-tools-menu-anchor dead? selected-tab]}
        (<== [::subs/sports-site-view lipas-id type-code])

        ;; Show PTV tab in read-only mode only when sync is enabled.
        ;; In edit mode show only when it's possible to sync.
        view-ptv? (and (if editing?
                         (ptv-data/ptv-candidate? edit-data)
                         (:sync-enabled (:ptv display-data)))
                       (<== [:lipas.ui.user.subs/check-privilege {:city-code ::roles/any} :ptv/manage]))

        ;; Allow map tools to be used with either regular or activity privileges
        can-use-map-tools? (or can-publish?
                               edit-activities?)
        can-edit-map? can-publish?

        map-tool-items (when (and editing? (#{"LineString" "Polygon"} geom-type))
                         (get-map-tool-items {:tr tr
                                              :lipas-id lipas-id
                                              :type-code type-code
                                              :sub-mode sub-mode
                                              :activity-value activity-value
                                              :edit-activities? edit-activities?
                                              :editing? editing?
                                              :can-edit-map? can-edit-map?
                                              :geom-type geom-type}))

        cities @(rf/subscribe [::user-subs/permission-to-cities])

        ;; We have three privileges:
        ;; - can-publish? - :site/create-edit - Edit basic info and properties
        ;; - edit-activities? - :activity/edit - Edit activity
        ;; - edit-floorball? - :floorball/edit - Edit floorball information
        ;; If either is true, we show the edit button

        set-field (partial set-field lipas-id)]

    [:> Grid
     {:container true
      :style (merge {:padding "1em"} (when (utils/ie?) {:width "420px"}))}

     (when editing?
       [import/import-geoms-view
        {:geom-type geom-type
         :on-import #(==> [::events/import-selected-geoms])}])

     (when editing?
       [restore-site-backup-dialog])

     [:> Grid {:item true :xs 12}

      ;; Headline
      [:> Grid
       {:container true
        :style {:flex-wrap "nowrap"}
        :align-items :center}

       [:> Grid {:item true :style {:margin-top "0.5em" :flex-grow 1}}
        [:> Typography {:variant "h5"}
         (:name display-data)]]

       (when editing?
         (rreset! simplify-tool-component
                  [simplify-tool
                   {:on-change (fn [tolerance]
                                 (let [geoms (-> edit-data :location :geometries)]
                                   (==> [::events/simplify lipas-id geoms tolerance])))
                    :tr tr}]))

       [:> Grid {:item true}
        ;; Close button
        [:> Grid {:item true}
         (when (not editing?)
           [:> IconButton
            {:style {:margin-left "-0.25em"}
             :on-click #(==> [::events/show-sports-site nil])}
            [:> Icon "close"]])]]]

      ;; Tabs
      [:> Grid {:item true :xs 12}
       [:> Tabs
        {:value selected-tab
         :on-change #(==> [::events/select-sports-site-tab %2])
         :variant (if view-activities?
                    "scrollable"
                    "fullWidth")
         #_#_:variant "scrollable"
         #_#_:variant "standard"
         :style {:margin-bottom "1em"}
         :indicator-color "secondary"
         :text-color "secondary"}
        [:> Tab
         {:style {:min-width 0}
          :value 0
          :label (tr :lipas.sports-site/basic-data)}]

        [:> Tab
         {:style {:min-width 0}
          :value 1
          :label (tr :lipas.sports-site/properties)}]

       ;; Disabled in prod until this can be released
        (when (and (not (utils/prod?)) accessibility-type?)
          [:> Tab
           {:style {:min-width 0}
            :value 2
            :label (tr :lipas.sports-site/accessibility)}])

        (when view-floorball?
          [:> Tab
           {:style {:min-width 0}
            :value 3
            :label (tr :lipas.floorball/headline)}])

        (when view-activities?
          [:> Tab
           {:style {:min-width 0}
            :value 5
            :label (tr :utp/headline)}])

        (when (#{"LineString"} geom-type)
          [:> Tab
           {:style {:min-width 0}
            :value 4
            :label (tr :sports-site.elevation-profile/headline)}])

        (when view-ptv?
          [:> Tab
           {:style {:min-width 0}
            :value 6
            :label "PTV"}])]

       (when delete-dialog-open?
         [sports-sites/delete-dialog
          {:tr tr
           :lipas-id lipas-id
           :on-close #(==> [::sports-site-events/toggle-delete-dialog])}])

       (case selected-tab

         ;; Basic info tab
         0 [:> Grid {:container true}

            [:> Grid {:item true :xs 12}

             (when (and (<== [:lipas.ui.sports-sites.subs/editing? lipas-id])
                        (not can-publish?))
               [:> Alert
                {:severity "info"}
                (tr :lipas.sports-site/no-permission-tab)])

             ^{:key (str "basic-data-" lipas-id)}
             [sports-sites/form
              {:tr tr
               :display-data display-data
               :edit-data edit-data
               :read-only? (or (not editing?) (not can-publish?))
               :status-read-only? false
               :types (vals types)
               :size-categories size-categories
               :admins admins
               :owners owners
               :on-change set-field
               :lipas-id lipas-id
               :sub-headings? true}]

             ^{:key (str "location-" lipas-id)}
             [sports-sites/location-form
              {:tr tr
               :read-only? (or (not editing?) (not can-publish?))
               :cities (vals cities)
               :edit-data (:location edit-data)
               :display-data (:location display-data)
               :on-change (partial set-field :location)
               :sub-headings? true
               :address-locator-component (when editing?
                                            [address-locator {:tr tr :lipas-id lipas-id :cities (vals cities)}])
               :address-required? (not (#{201 2011} type-code))}]]]

         ;; Properties tab - "Lisätiedot"
         1 [sports-sites/properties-form
            {:tr tr
             :type-code (or (-> edit-data :type :type-code) type-code)
             :read-only? (or (not editing?) (not can-publish?))
             :on-change (partial set-field :properties)
             :display-data (:properties display-data)
             :edit-data (:properties edit-data)
             :editing? (<== [:lipas.ui.sports-sites.subs/editing? lipas-id])
             :geoms (-> edit-data :location :geometries)
             :geom-type geom-type
             :problems? problems?
             :key (-> edit-data :type :type-code)
             :pools (:pools edit-data)}]

         ;; Accessibility
         2 [accessibility/view {:lipas-id lipas-id}]

         3 (cond
             floorball-type?
             [:<>
              [:> Tabs {:value "floorball" :variant "standard"}
               [:> Tab {:value "floorball" :label "Salibandy"}]]
              [floorball/form
               {:tr tr
                :lipas-id lipas-id
                :type-code (or (-> edit-data :type :type-code) type-code)
                :read-only? (or (not edit-floorball?) (not editing?))
                :on-change set-field
                :display-data display-data
                :edit-data edit-data
                :key (-> edit-data :type :type-code)}]]

             ;; Football specific
             #_#_football-types
               [football/circumstances-form
                {:tr tr
                 :type-code (or (-> edit-data :type :type-code) type-code)
                 :read-only? (not editing?)
                 :on-change (partial set-field :circumstances)
                 :display-data (:circumstances display-data)
                 :edit-data (:circumstances edit-data)
                 :key (-> edit-data :type :type-code)}])

         4 (when (#{"LineString"} geom-type)
             [:> Grid {:item true :xs 12 :style {:margin-top "0.5em"}}
              [sports-sites/elevation-profile {:lipas-id lipas-id}]])

         5 [:> Grid {:item true :xs 12}
            [activities/view
             {:tr tr
              :lipas-id lipas-id
              :type-code type-code
              :display-data display-data
              :edit-data edit-data
              :can-edit? edit-activities?}]]

         6 [:> Grid {:item true :xs 12}
            [ptv-site/site-view
             {:tr tr
              :lipas-id lipas-id
              :type-code type-code
              ; :display-data display-data
              :edit-data edit-data
              :can-edit? can-publish?}]])]

;; "Landing bay" for floating tools
      [:> Grid {:item true :xs 12 :style {:height "3em"}}]

     ;; Actions
      (when-not hide-actions?
        [layouts/floating-container
         {:bottom 0 :background-color "transparent"}
         (into
          [:> Grid
           {:container true
            :align-items "center"
            :align-content "flex-start"
            :spacing 1
            :style {:padding "0.5em 0em 0.5em 0em"}}]
          (->>
           [;; Undo
              ;; TODO: Undo/redo are only for map edits, so not useful if
              ;; you only have floorball or activity privileges (if current activity doesn't have map tools)
            (when editing?
              [:> Tooltip {:title (tr :actions/undo)}
               [:span
                [:> Fab
                 {:disabled (not undo)
                  :size "small"
                  :on-click #(==> [::events/undo lipas-id])}
                 [:> Icon "undo"]]]])

           ;; Redo
            (when editing?
              [:> Tooltip {:title (tr :actions/redo)}
               [:span
                [:> Fab
                 {:disabled (not redo)
                  :size "small"
                  :on-click #(==> [::events/redo lipas-id])}
                 [:> Icon "redo"]]]])

            ;; Active editing tool
            (when (and editing? (seq map-tool-items))
              [:> Tooltip
               {:title
                (case sub-mode
                  :drawing (tr :map.tools/drawing-tooltip)
                  :drawing-hole (tr :map.tools/drawing-hole-tooltip)
                  (:editing :undo) (tr :map/delete-vertices-hint)
                  :importing (tr :map.tools/importing-tooltip)
                  :deleting (tr :map.tools/deleting-tooltip)
                  :splitting (tr :map.tools/splitting-tooltip)
                  :simplifying (tr :map.tools/simplifying-tooltip)
                  :selecting (tr :map.tools/selecting-tooltip)
                  :travel-direction (tr :map.tools/travel-direction-tooltip)
                  :route-part-difficulty (tr :map.tools/route-part-difficulty-tooltip)
                  :view-only "-")}
               [:> Fab
                {:size "small"
                 :on-click #() ; noop
                 :color "inherit"}
                (let [props {:color "secondary"}]
                  (case sub-mode
                    :drawing (case geom-type
                               "Point" [:> Icon props "edit"]
                               "LineString" [:> Icon props "timeline"]
                               "Polygon" [:> Icon props "change_history"])
                    :drawing-hole [:> Icon props "vignette"]
                    (:editing :undo) [:> Icon props "edit"]
                    :importing [:> FileUpload props]
                    :deleting [:> Eraser props]
                    :splitting [:> ContentCut props]
                    :simplifying [:> Icon props "auto_fix_high"]
                    :selecting [:> Icon props "handshake"]
                    :travel-direction [:> Icon props "turn_slight_right"]
                    :route-part-difficulty [:> Icon props "warning"]
                    :view-only [:> Icon props "dash"]))]])

           ;; Tool select button
            (when editing?
              (when (seq map-tool-items)
                [:<>
                 [:> Tooltip {:title (tr :actions/select-tool)}
                  [:> Fab
                   {:size "medium"
                    :on-click #(==> [::events/open-more-tools-menu (.-currentTarget %)])
                    :color "secondary"}
                   [:> Icon "more_horiz"]]]

                 (into [:> Menu
                        {:variant "menu"
                         :auto-focus false
                         :anchor-el more-tools-menu-anchor
                         :open (some? more-tools-menu-anchor)
                         :on-close #(==> [::events/close-more-tools-menu])}]
                       map-tool-items)]))

           ;; Download GPX
            (when (and (not editing?) (#{"LineString"} geom-type))
              [:> Tooltip {:title (tr :map/download-gpx)}
               [:> Fab
                {:size "small"
                 :on-click #(==> [::events/download-gpx lipas-id])
                 :color "inherit"}
                [:> Icon "save_alt"]]])

           ;; Zoom to site
            (when-not editing?
              [:> Tooltip {:title (tr :map/zoom-to-site)}
               [:> Fab
                {:size "small"
                 :on-click #(==> [::events/zoom-to-site lipas-id width])
                 :color "inherit"}
                [:> Icon {:color "inherit"}
                 "place"]]])

           ;; Add reminder
            (when (and logged-in? (not editing?))
              (let [name (-> display-data :name)
                    link (-> js/window .-location .-href)]
                [reminders/add-button
                 {:message (tr :reminders/placeholder name link)}]))

           ;; Copy sports site
            (when (and logged-in? (not editing?))
              [:> Tooltip {:title (tr :actions/duplicate)}
               [:> Fab
                {:size "small"
                 :on-click #(==> [::events/duplicate-sports-site lipas-id])}
                [:> ContentDuplicate]]])

           ;; Resurrect button
            (when (and dead? logged-in? can-publish? editing-allowed?)
              [:> Tooltip {:title (tr :actions/resurrect)}
               [:> Fab
                {:size "small"
                 :on-click #(==> [::events/resurrect lipas-id])}
                [:> Icon "360"]]])

           ;; Analysis
            (when (and @(rf/subscribe [:lipas.ui.user.subs/check-privilege
                                       (roles/site-roles-context display-data)
                                       :analysis-tool/use])
                       (not editing?))
              [:> Tooltip {:title (tr :map.demographics/tooltip)}
               [:> Fab
                {:size "small"
                 :on-click #(==> [::events/show-analysis lipas-id])}
                [:> Icon "insights"]]])

           ;; ;; Import geom
           ;; (when (and editing? (#{"LineString"} geom-type))
           ;;   [:> Tooltip {:title (tr :map.import/tooltip)}
           ;;    [:> Fab
           ;;     {:size     "small"
           ;;      :on-click #(==> [::events/toggle-import-dialog])
           ;;      :color    "inherit"}
           ;;     [:> FileUpload]]])

           ;; ;; Draw hole
           ;; (when (and editing? (#{"Polygon"} geom-type))
           ;;   [:> Tooltip {:title (tr :map/draw-hole)}
           ;;    [:> Fab
           ;;     {:size     "small"
           ;;      :on-click #(if (= sub-mode :drawing-hole)
           ;;                   (==> [::events/start-editing lipas-id :editing geom-type])
           ;;                   (==> [::events/start-editing lipas-id :drawing-hole geom-type]))
           ;;      :style    (when (= sub-mode :drawing-hole)
           ;;                  {:border (str "5px solid " mui/secondary)})
           ;;      :color    "inherit"}
           ;;     [:> Icon "vignette"]]])

           ;; ;; Add new geom
           ;; (when (and editing? (#{"LineString" "Polygon"} geom-type))
           ;;   [:> Tooltip {:title (case geom-type
           ;;                          "LineString" (tr :map/draw-linestring)
           ;;                          "Polygon"    (tr :map/draw-polygon))}
           ;;    [:> Fab
           ;;     {:size     "small"
           ;;      :on-click #(if (= sub-mode :drawing)
           ;;                   (==> [::events/start-editing lipas-id :editing geom-type])
           ;;                   (==> [::events/start-editing lipas-id :drawing geom-type]))
           ;;      :style    (when (= sub-mode :drawing)
           ;;                  {:border (str "5px solid " mui/secondary)})
           ;;      :color    "inherit"}
           ;;     (if (= geom-type "LineString")
           ;;       [:> Icon "timeline"]
           ;;       [:> Icon "change_history"])]])

           ;; ;; Delete geom
           ;; (when (and editing? (#{"LineString" "Polygon"} geom-type))
           ;;   [:> Tooltip {:title (case geom-type
           ;;                          "LineString" (tr :map/remove-linestring)
           ;;                          "Polygon"    (tr :map/remove-polygon))}
           ;;    [:> Fab
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
           ;;   [:> Tooltip {:title (tr :map/split-linestring)}
           ;;    [:> Fab
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
           ;;   [:> Tooltip {:title (tr :map/delete-vertices-hint)}
           ;;    [:> Typography
           ;;     {:style
           ;;      {:font-size 24 :margin-left "4px" :margin-right "16px"}}
           ;;     "?"]])
            ]

           (concat
           ;; FIXME: Just reagent elements, maybe :<>
            (misc/edit-actions-list
             {:editing? editing?
              :editing-allowed? editing-allowed?
              :edit-activities? edit-activities?
              :edit-floorball? edit-floorball?
              :save-in-progress? save-in-progress?
              :valid? edits-valid?
              :logged-in? logged-in?
              :user-can-publish? can-publish?
              :on-discard #(==> [::events/discard-edits lipas-id])
              :discard-tooltip (tr :actions/cancel)
              :on-edit-start #(==> [::events/edit-site lipas-id geom-type can-publish? edit-activities? edit-floorball?])
              :edit-tooltip (tr :actions/edit)
              :on-publish #(==> [::events/save-edits lipas-id])
              :publish-tooltip (tr :actions/save)
              :invalid-message (tr :error/invalid-form)
              :on-delete #(==> [::events/delete-site])
              :delete-tooltip (tr :lipas.sports-site/delete-tooltip)}))

           (remove nil?)
           (map (fn [tool] [:> Grid {:item true} tool]))))])]]))

(defn add-btn [{:keys [tr]}]
  [:> Tooltip {:title (tr :lipas.sports-site/add-new)}
   [:> Fab
    {:color "secondary"
     :on-click #(==> [::events/start-adding-new-site])}
    [:> Icon "add"]]])

(defn set-new-site-field [& args]
  (==> [::sports-site-events/edit-new-site-field (butlast args) (last args)]))

(defn add-sports-site-view
  [{:keys [tr width]}]
  (r/with-let [geom-tab (r/atom "draw")]
    (let [locale (tr)

          {:keys [type data is-planning? save-enabled? admins owners
                  problems? size-categories zoomed? geom active-step
                  sub-mode undo redo
                  selected-tab]} (<== [::subs/add-sports-site-view])

          geom-type (-> geom :features first :geometry :type)
          types (<== [::subs/new-site-types is-planning? geom-type])

          ;; Allow all cities if current status is planning and user has permission to create
          ;; planning sites on any city/type
          cities (if is-planning?
                   @(rf/subscribe [:lipas.ui.sports-sites.subs/cities-by-city-code])
                   @(rf/subscribe [::user-subs/permission-to-cities]))

          role-site-ctx (roles/site-roles-context data)
          type-code (-> data :type :type-code)
          floorball-types (<== [:lipas.ui.sports-sites.floorball.subs/type-codes])
          floorball-type? (contains? floorball-types type-code)
          ;; view-floorball?      (when floorball-type? (<== [:lipas.ui.user.subs/check-privilege role-site-ctx :floorball/view]))
          edit-floorball? (when floorball-type? (<== [:lipas.ui.user.subs/check-privilege role-site-ctx :floorball/edit]))
          set-field set-new-site-field
          geom-type (:geometry-type type)]

      [:> Grid {:container true :spacing 2 :style {:padding "1em"}}

       [:> Grid {:item true :xs 12}

        [import/import-geoms-view
         {:geom-type geom-type
          :on-import #(==> [::events/import-selected-geoms-to-new])
          :show-replace? false}]

        (rreset! simplify-tool-component
                 [simplify-tool
                  {:on-close (fn []
                               (==> [::events/new-geom-drawn geom])
                               (==> [::events/toggle-simplify-dialog]))
                   :on-change (fn [tolerance] (==> [::events/simplify-new geom tolerance]))
                   :tr tr}])

        [:> Typography {:variant "h6" :style {:margin-left "8px"}}
         (if-let [type-name (get-in type [:name locale])]
           (tr :lipas.sports-site/new-site-of-type type-name)
           (tr :lipas.sports-site/new-site {:type type :locale locale}))]]

       ;; Steps
       [:> Grid {:item true :xs 12}
        (when is-planning?
          [:> Alert
           {:severity "info"
            :sx #js {:mb 2}}
           (tr :lipas.sports-site/creating-planning-site)])

        [:> Stepper
         {:active-step active-step
          :alternativeLabel true
          :style {:margin-left "-18px"}
          :orientation "horizontal"
          :sx (fn [theme]
                #js {".Mui-active" #js {:fill (.. theme -palette -secondary -main)}})}

         ;; Step 1 - Select type
         [:> Step {:active (= (dec 1) active-step)}
          [:> StepLabel (tr :actions/select-type)]]

         ;; Step 2 - Add to map
         [:> Step {:active (= (dec 2) active-step)}
          [:> StepLabel (tr :map/draw)]]

         ;; Step 3 - Fill data
         [:> Step {:active (= (dec 3) active-step)}
          [:> StepLabel (tr :actions/fill-data)]]]]

       [:> Grid {:item true :xs 12}

        ;; Step 1 type
        (when (= active-step (dec 1))
          [:> Grid {:container true}
           [:> Grid {:item true :xs 12}
            [type-selector-single
             {:value (when type [(:type-code type)])
              :tr tr
              :types types
              :on-change #(==> [::sports-site-events/select-new-site-type %])}]]])

        ;; Step 2 geom
        (when (= active-step (dec 2))
          (let [type-code (:type-code type)]
            (if-not geom

              ;; Draw new geom
              [:> Grid {:container true :spacing 2}

               ;; Tabs for selecting btw drawing and importing geoms
               #_(when (#{"LineString" "Polygon"} geom-type))
               [:> Grid {:item true :xs 12}

                [:> Tabs {:value @geom-tab
                           :on-change #(reset! geom-tab %2)
                           :variant "fullWidth"
                           :indicator-color "secondary"
                           :text-color "secondary"}
                 [:> Tab {:value "draw" :label (tr :map/draw-geoms)}]
                 (when (#{"LineString" "Polygon"} geom-type)
                   [:> Tab {:value "import" :label (tr :map.import/tab-header)}])
                 (when (#{"Point"} geom-type)
                   [:> Tab {:value "coords" :label "Syötä koordinaatit"}])]]

               ;; Draw
               (when (= "draw" @geom-tab)
                 [:<>
                  ;; Helper text
                  [:> Grid {:item true :xs 12}
                   [:> Typography {:variant "body2"}
                    (tr :map/zoom-to-site)]]

                  ;; Zoom closer info text
                  (when (not zoomed?)
                    [:> Grid {:item true :xs 12}
                     [:> Typography {:variant "body2" :color :error}
                      (tr :map/zoom-closer)]])

                  ;; Add initial geom button
                  [:> Grid {:item true}
                   [:> Button
                    {:disabled (not zoomed?)
                     :color "secondary"
                     :variant "contained"
                     :on-click #(==> [::events/start-adding-geom geom-type])}
                    [:> Icon "add_location"]
                    (tr :map/add-to-map)]]])

               ;; Enter coordinates
               (when (= "coords" @geom-tab)
                 (r/with-let [state (r/atom {:crs :epsg4326
                                             :lon nil
                                             :lat nil})]
                   (let [[lon-spec lat-spec] (condp = (:crs @state)
                                               :epsg4326 [common-schema/lon
                                                          common-schema/lat]
                                               :epsg3067 [common-schema/lon-euref
                                                          common-schema/lat-euref])
                         valid? (and (:lon @state) (:lat @state)
                                     (m/validate lon-spec (:lon @state))
                                     (m/validate lat-spec (:lat @state)))]
                     [:<>
                      [:> Grid {:item true :xs 12}
                       [selects/select
                        {:style {:min-width "150px"}
                         :label "CRS"
                         :items [{:value :epsg3067 :label "TM35FIN EUREF"}
                                 {:value :epsg4326 :label "WGS84"}]
                         :value (:crs @state)
                         :on-change #(swap! state assoc :crs %1)}]]
                      [:> Grid {:item true :xs 6}
                       [text-fields/text-field
                        {:label (condp = (:crs @state)
                                  :epsg4326 "Lon"
                                  :epsg3067 "N")
                         :type "number"
                         :spec lon-spec
                         :value (:lon @state)
                         :on-change #(swap! state assoc :lon %1)}]]
                      [:> Grid {:item true :xs 6}
                       [text-fields/text-field
                        {:label (condp = (:crs @state)
                                  :epsg4326 "Lat"
                                  :epsg3067 "E")
                         :type "number"
                         :spec lat-spec
                         :value (:lat @state)
                         :on-change #(swap! state assoc :lat %1)}]]
                      [:> Grid {:item true}
                       [:> Button
                        {:color "secondary"
                         :disabled (not valid?)
                         :variant "contained"
                         :on-click #(==> [::events/add-point-from-coords @state])}
                        [:> Icon "add_location"]
                        (tr :map/add-to-map)]]])))

               ;; Import geoms
               (when (= "import" @geom-tab)
                 (when (#{"LineString" "Polygon"} geom-type)
                   [:<>
                    ;; Supported formats helper text
                    [:> Grid {:item true :xs 12}
                     [:> Typography {:variant "body2"}
                      (tr :map.import/supported-formats import/import-formats-str)]]

                    ;; Open import dialog button
                    [:> Grid {:item true}
                     [:> Button
                      {:color "secondary"
                       :variant "contained"
                       :on-click #(==> [::events/toggle-import-dialog])}
                      (tr :map.import/tooltip)]]]))]

              ;; Modify new geom
              [:> Grid {:container true :spacing 1}

               [:> Grid {:item true}
                (when (not zoomed?)
                  [:> Typography {:variant "body2" :color "error"}
                   (tr :map/zoom-closer)])]

               [:> Grid {:item true :xs 12}
                [:> Typography {:variant "body2"}
                 (case geom-type
                   "LineString" (tr :map/modify-linestring)
                   "Polygon" (tr :map/modify-polygon)
                   (tr :map/modify))]
                [:> Typography {:variant "caption" :style {:margin-top "0.5em"}}
                 (tr :map/edit-later-hint)]]

               ;; Add additional geom button
               (when (#{"LineString" "Polygon"} geom-type)
                 [:> Grid {:item true}
                  [:> Button
                   {:on-click #(==> [::events/start-adding-geom geom-type])
                    :variant "contained"
                    :color "secondary"}
                   (case geom-type
                     "LineString" (tr :map/draw-linestring)
                     "Polygon" (tr :map/draw-polygon)
                     (tr :map/draw))]])

               ;; Delete geom
               (when (#{"LineString" "Polygon"} geom-type)
                 [:> Grid {:item true}
                  [:> Tooltip
                   {:title (case geom-type
                             "LineString" (tr :map/remove-linestring)
                             "Polygon" (tr :map/remove-polygon)
                             (tr :map/remove))}
                   [:span
                    [:> Button
                     {:on-click #(if (= sub-mode :deleting)
                                   (==> [::events/stop-deleting-geom geom-type])
                                   (==> [::events/start-deleting-geom geom-type]))
                      :disabled (-> geom :features empty?)
                      :style (when (= sub-mode :deleting)
                               {:outline (str "2px solid " mui/secondary)})
                      :variant "contained"
                      :color "gray1"}
                     [:> Eraser]]]]])

               ;; Split
               (when (#{"LineString"} geom-type)
                 [:> Grid {:item true}
                  [:> Tooltip {:title (tr :map/split-linestring)}
                   [:span
                    [:> Button
                     {:on-click #(if (= sub-mode :splitting)
                                   (==> [::events/stop-splitting-geom geom-type])
                                   (==> [::events/start-splitting-geom geom-type]))
                      :disabled (-> geom :features empty?)
                      :style (when (= sub-mode :splitting)
                               {:outline (str "2px solid " mui/secondary)})
                      :variant "contained"
                      :color "gray1"}
                     [:> ContentCut]]]]])

               ;; Simplify
               (when (#{"LineString" "Polygon"} geom-type)
                 [:> Grid {:item true}
                  [:> Tooltip {:title (tr :map.tools/simplify)}
                   [:span
                    [:> Button
                     {:on-click #(==> [::events/open-simplify-tool])
                      :disabled (or (-> geom :features empty?)
                                    (= sub-mode :simplifying))
                      :style (when (= sub-mode :simplifying)
                               {:outline (str "2px solid " mui/secondary)})
                      :variant "contained"
                      :color "gray1"}
                     [:> Icon "auto_fix_high"]]]]])

               ;; Delete vertices helper text
               (when (#{"LineString" "Polygon"} geom-type)
                 [:> Grid {:item true :xs 12}
                  [:> Typography {:variant "caption" :style {:margin-top "0.5em"}}
                   (tr :map/delete-vertices-hint)]])

               ;; Done button and undo / redo
               [:> Grid {:item true :xs 12}
                [:> Grid
                 {:container true
                  :justify-content "flex-start"
                  :align-items "center"
                  :spacing 1}

                 ;; Ready button
                 [:> Grid {:item true}
                  [:> Button
                   {:on-click #(==> [::events/finish-adding-geom geom type-code])
                    :variant "contained"
                    :disabled (-> geom :features empty?)
                    :color "secondary"}
                   (tr :general/done)]]
                 [:> Grid {:item true}
                  [:> Grid {:item true}

                   ;; Undo & Redo
                   [:> Grid {:container true}

                    ;; Undo
                    [:> Grid {:item true}
                     [:> Tooltip {:title "Undo"}
                      [:span
                       [:> IconButton
                        {:disabled (not undo)
                         :on-click #(==> [::events/undo "new"])}
                        [:> Icon "undo"]]]]]

                    ;; Redo
                    [:> Grid {:item true}
                     [:> Tooltip {:title "Redo"}
                      [:span
                       [:> IconButton
                        {:disabled (not redo)
                         :on-click #(==> [::events/redo "new"])}
                        [:> Icon "redo"]]]]]]]]]]

               ;; Retkikartta Problems
               (when (and (#{"LineString"} geom-type) problems?)
                 [:> Grid {:item true}
                  [:> Tooltip
                   {:placement "right"
                    :title (str
                            (tr :map/retkikartta-problems-warning)
                            " "
                            (tr :map/retkikartta-checkbox-reminder))}
                   [:span
                    [misc/icon-text
                     {:icon "warning"
                      :text "Retkikartta.fi"}]]]])])))

        ;; Step3 content
        (when (= active-step (dec 3))
          [:> Grid {:container true :style {:flex-direction "column"}}

           ;; Tabs
           [:> Grid {:item true}
            [:> Tabs
             {:value selected-tab
              :on-change #(==> [::events/select-new-sports-site-tab %2])
              :variant "fullWidth"
              :indicator-color "secondary"
              :text-color "inherit"
              :style {:margin-bottom "1em" :margin-top "1em"}}
             [:> Tab {:label (tr :lipas.sports-site/basic-data)}]
             [:> Tab {:label (tr :lipas.sports-site/properties)}]
             ;; TODO: This could be view-floorball? but right not it is not useful to
             ;; show the tab in create-site if user can't edit the data
             (when edit-floorball?
               [:> Tab {:label "Olosuhteet"}])]

            (case selected-tab

              ;; Basic info tab
              0 [:> Grid {:container true}
                 [:> Grid {:item true :xs 12}

                  [sports-sites/form
                   {:tr tr
                    :edit-data data
                    :read-only? false
                    :status-read-only? is-planning?
                    :types (vals types)
                    :size-categories size-categories
                    :admins admins
                    :owners owners
                    :on-change set-field
                    :sub-headings? true
                    :lipas-id 0}]

                  [sports-sites/location-form
                   {:tr tr
                    :read-only? false
                    :cities (vals cities)
                    :edit-data (:location data)
                    :on-change (partial set-field :location)
                    :sub-headings? true
                    :address-locator-component [address-locator
                                                {:tr tr
                                                 :cities (vals cities)}]
                    :address-required? (not (#{201 2011} (:type-code type)))}]]]

              ;; Properties tab
              1 [sports-sites/properties-form
                 {:key (-> data :type :type-code)
                  :tr tr
                  :type-code (-> data :type :type-code)
                  :read-only? false
                  :width width
                  :on-change (partial set-field :properties)
                  :edit-data (:properties data)
                  :geoms (-> data :location :geometries)
                  :problems? problems?}]
              2 (cond
                  ;; Floorball specific
                  floorball-type?
                  [:<>
                   [:> Tabs
                    {:value "floorball"
                     :variant "standard"
                     :indicator-color "secondary"
                     :text-color "inherit"}
                    [:> Tab {:value "floorball" :label "Salibandy"}]]
                   [floorball/form
                    {:tr tr
                     :lipas-id nil
                     :type-code (-> data :type :type-code)
                     :read-only? (not edit-floorball?)
                     :on-change set-field
                     :display-data data
                     :edit-data data
                     :key (-> data :type :type-code)}]]

                  ;; Football specific
                  #_#_football-types
                    [football/circumstances-form
                     {:tr tr
                      :type-code (or (-> edit-data :type :type-code) type-code)
                      :read-only? (not editing?)
                      :on-change (partial set-field :circumstances)
                      :display-data (:circumstances display-data)
                      :edit-data (:circumstances edit-data)
                      :key (-> edit-data :type :type-code)}]))]])]

       ;; Actions
       [:> Grid {:container true :align-items "flex-end"}
        [:> Grid {:item true :xs 12 :style {:height "50px"}}
         [layouts/floating-container {:bottom 0 :background-color "transparent"}
          [:> Grid
           {:container true
            :align-items "center"
            :spacing 1
            :style {:padding "0.5em"}}

           [address-search-dialog]

           ;; Save
           (when data
             [:> Grid {:item true}
              [buttons/save-button
               {:tooltip (tr :actions/save)
                :disabled-tooltip (tr :actions/fill-required-fields)
                :disabled (not save-enabled?)
                :on-click #(==> [::events/save-new-site data])}]])

           [:> Grid {:item true}

            ;; Discard
            [buttons/discard-button
             {:on-click #(==> [::events/discard-new-site])
              :tooltip (tr :actions/discard)}]]

           ;; Address search button
           [:> Tooltip {:title (tr :map.address-search/tooltip)}
            [:> Grid {:item true}
             [:> Fab
              {:size "small"
               :on-click #(==> [::events/toggle-address-search-dialog])}
              [:> MapSearchOutline]]]]]]]]])))

(defn default-tools
  [{:keys [tr logged-in?]}]
  (let [result-view (<== [:lipas.ui.search.subs/search-results-view])
        mode-name (<== [::subs/mode-name])
        show-create-button? (<== [::subs/show-create-button?])
        ptv-dialog-open? (<== [:lipas.ui.ptv.subs/dialog-open?])
        ptv-manage-privilege (<== [:lipas.ui.user.subs/check-privilege {:city-code ::roles/any} :ptv/manage])
        ptv-audit-privilege (<== [:lipas.ui.user.subs/check-privilege {:city-code ::roles/any} :ptv/audit])
        ptv-privilege (or ptv-manage-privilege ptv-audit-privilege)]
    [:<>
     ;; PTV dialog
     ;; TODO Disabled until ready for release
     (when ptv-privilege
       [ptv/dialog {:tr tr}])

     ;; Address search dialog
     [address-search-dialog]

     ;; Floating container
     [layouts/floating-container {:bottom 0 :background-color "transparent"}
      [:> Grid
       {:container true
        :align-items "center"
        :spacing 1
        :style {:padding-bottom "0.5em"}}

       ;; Create sports site btn
       (when show-create-button?
         [:> Grid {:item true}
          [add-btn {:tr tr}]])

       (when (= :analysis mode-name)
         [:> Grid {:item true}
          [:> Tooltip {:title (tr :lipas.sports-site/add-new-planning)}
           [:> Fab
            {:color "secondary"
             :variant "extended"
             :on-click #(==> [::events/add-analysis-target])}
            [:> Icon "add"]
            (tr :lipas.sports-site/planning-site)]]])

       ;; Address search btn
       [:> Tooltip {:title (tr :map.address-search/tooltip)}
        [:> Grid {:item true}
         [:> Fab
          {:size "small" :on-click #(==> [::events/toggle-address-search-dialog])}
          [:> MapSearchOutline]]]]

       ;; Create Excel report btn
       (when (= :list result-view)
         [:> Grid {:item true}
          [reports/dialog {:tr tr :btn-variant :fab}]])

       ;; Analysis tool btn
       (when (and @(rf/subscribe [:lipas.ui.user.subs/check-privilege
                                  {:type-code ::roles/any
                                   :city-code ::roles/any
                                   :lipas-id ::roles/any}
                                  :analysis-tool/use])
                  ;; logged-in?
                  (= :list result-view))
         [:> Tooltip {:title (tr :map.demographics/tooltip)}
          [:> Grid {:item true}
           [:> Fab
            {:size "small"
             :style (when (= mode-name :analysis)
                      {:border (str "5px solid " mui/secondary)})
             :on-click #(==> (if (= mode-name :analysis)
                               [::events/hide-analysis]
                               [::events/show-analysis]))}
            [:> Icon "insights"]]]])

       ;; PTV button
       (when ptv-privilege
         [:> Tooltip {:title (tr :ptv/tooltip)}
          [:> Grid {:item true}
           [:> Fab
            {:size "small"
             :on-click #(==> (if ptv-dialog-open?
                               [:lipas.ui.ptv.events/close-dialog]
                               [:lipas.ui.ptv.events/open-dialog]))}
            [:> Icon "ios_share"]]]])]]]))

(defn add-view
  [{:keys [tr width]}]
  (let [add-mode (<== [::subs/selected-add-mode])
        can-add-sports-sites? (<== [:lipas.ui.user.subs/can-add-sports-sites?])
        can-add-lois? (<== [:lipas.ui.user.subs/can-add-lois?])]
    [:<>
     (when (and can-add-sports-sites? can-add-lois?)
       [:> Tabs
        {:value add-mode
         :on-change #(==> [::events/select-add-mode %2])
         :variant "fullWidth"
         :indicator-color "secondary"
         :text-color "inherit"}
        [:> Tab {:value "sports-site" :label (tr :lipas.sports-site/headline)}]
        [:> Tab {:value "loi" :label (tr :loi/headline)}]])

     (case add-mode
       "sports-site" [add-sports-site-view {:tr tr :width width}]
       "loi" [loi/view])]))

(defn map-contents-view [{:keys [tr logged-in? width]}]
  (let [selected-site (<== [::subs/selected-sports-site])
        show-tools? (<== [::subs/show-default-tools?])
        view (<== [::subs/view])
        loi (<== [:lipas.ui.loi.subs/selected-loi])]

    [:<>
     ;; Search, filters etc.
     (case view
       :adding [add-view {:tr tr :width width}]
       :analysis [analysis/view
                  {:tr tr}]
       :site [sports-site-view {:tr tr :site-data selected-site :width width}]
       :loi [loi/view {:display-data loi}]
       :search [search/search-view
                {:tr tr
                 :on-result-click (fn [{:keys [lipas-id]}]
                                    (==> [::events/show-sports-site lipas-id]))}])

     ;; Floating bottom toolbar
     (when show-tools?
       [:> Stack
        {:sx {:px 1}}
        [default-tools {:tr tr :logged-in? logged-in?}]])]))

(r/defc map-view [_props]
  (let [tr (<== [:lipas.ui.subs/translator])
        logged-in? (<== [:lipas.ui.subs/logged-in?])
        drawer-open? (<== [::subs/drawer-open?])
        width (mui/use-width)
        drawer-width (<== [::subs/drawer-width width])
        popup-ref (hooks/use-ref nil)]

    [:> Grid {:container true :style {:height "100%" :width "100%"}}

     ;; Mini-nav
     [layouts/floating-container
      {:right 0 :background-color "transparent"}
      [:> Grid
       {:container true :direction "column" :align-items "flex-end" :spacing 2}
       [:> Grid {:item true}
        [nav/mini-nav {:tr tr :logged-in? logged-in?}]]]]

     [mui/mui-theme-provider {:theme mui/jyu-theme-light}

      ;; Simplify tool container hack for Safari
      [simplify-tool-container]

      (when-not drawer-open?
        ;; Open Drawer Button
        [layouts/floating-container {:background-color "transparent"}
         [:> Toolbar {:disable-gutters true :style {:padding "8px 0px 0px 8px"}}
          [:> Fab
           {:size (if (utils/mobile? width) "small" "medium")
            :on-click #(==> [::events/toggle-drawer])
            :color "secondary"}
           [:> Icon "search"]]]])

      ;; Closable left sidebar drawer
      [:> Drawer
       {:variant "persistent"
        :PaperProps {:style {:width drawer-width}}
        :SlideProps {:direction "down"}
        :open drawer-open?}

       ;; Close button
       [:> Button
        {:on-click #(==> [::events/toggle-drawer])
         :style {:min-height "36px" :margin-bottom "1em"}
         :variant "outlined"
         :color "inherit"}
        [:> Icon "expand_less"]]

       ;; Content
       [map-contents-view {:tr tr :logged-in? logged-in? :width width}]]]

     ;; Floating container (bottom right)
     [layouts/floating-container
      {:bottom "0.2em"
       :right "3.5em"
       :background-color "transparent"}

      [:> Grid2
       {:container true
        :spacing 1
        :sx #js {:bgcolor mui/gray2
                 :padding 1
                 :justifyContent "center"
                 :alignItems "center"
                 :border-radius 4}
        :wrap "nowrap"}

       ;; Feedback btn
       #_[:> Grid
          [:> Paper {:style {:background-color "rgba(255,255,255,0.9)"}}
           [feedback/feedback-btn]]]

       ;; Zoom to users location btn
       [:> Grid
        [:> Paper {:style {:background-color "rgba(255,255,255,0.9)"}}
         [user-location-btn {:tr tr}]]]

       ;; Overlay selector
       [:> Grid
        [:> Paper {:style {:background-color "rgba(255,255,255,0.9)"}}
         [overlay-selector {:tr tr}]]]

       ;; Basemap opacity selector
       [:> Grid
        [:> Paper {:style {:background-color "rgba(255,255,255,0.9)"}}
         [basemap-transparency-selector {:tr tr}]]]

       ;; Basemap switcher
       [:> Grid
        [:> Paper
         {:elevation 1
          :style
          {:background-color "rgba(255,255,255,0.9)"
           :padding-left "0.5em" :padding-right "0.5em"}}
         [layer-switcher {:tr tr}]]]]]

     [popup
      {:popup-ref popup-ref}]

     ;; The map
     [ol-map/map-outer
      {:popup-ref popup-ref}]]))

(defn main []
  [map-view])
