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
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [lipas.data.activities :as activities-data]
            [lipas.data.ptv :as ptv-data]
            [lipas.data.sports-sites :as ss]
            [lipas.roles :as roles]
            [lipas.ui.accessibility.views :as accessibility]
            [lipas.ui.analysis.views :as analysis]
            [lipas.ui.analysis.heatmap.subs :as heatmap-subs]
            [lipas.ui.components :as lui]
            [lipas.ui.components.misc :as misc]
            [lipas.ui.loi.views :as loi]
            [lipas.ui.map.events :as events]
            [lipas.ui.map.import :as import]
            [lipas.ui.map.map :as ol-map]
            [lipas.ui.map.subs :as subs]
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
            [lipas.ui.uix.hooks :refer [use-subscribe]]
            [lipas.ui.user.subs :as user-subs]
            [lipas.ui.utils :refer [<== ==>] :as utils]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [uix.core :as uix :refer [$ defui]]))

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
    [lui/dialog
     {:open? open?
      :title (tr :map.address-search/title)
      :on-close toggle
      :save-enabled? false
      :cancel-label (tr :actions/close)}
     [mui/grid {:container true}
      [mui/grid {:item true :xs 12}
       [lui/text-field
        {:auto-focus true
         :fullWidth true
         :defer-ms 150
         :label (tr :search/search)
         :value value
         :on-change #(==> [::events/update-address-search-keyword %])}]]
      [mui/grid {:item true :xs 12}
       (into
         [mui/list]
         (for [m results]
           [mui/list-item-button {:on-click #(==> [::events/show-address m])}
            [mui/list-item-text
             (:label m)]]))]]]))

(defn restore-site-backup-dialog []
  (let [tr (<== [:lipas.ui.subs/translator])
        open? (<== [::subs/restore-site-backup-dialog-open?])
        lipas-id (<== [::subs/restore-site-backup-lipas-id])
        error (<== [::subs/restore-site-backup-error])]
    [lui/dialog
     {:open? open?
      :title (tr :map.tools/restore-backup-tooltip)
      :on-close #(==> [::events/close-restore-site-backup-dialog])
      :save-enabled? false
      :cancel-label (tr :actions/close)}
     [mui/grid {:container true}
      [mui/grid {:item true :xs 12}
       [mui/grid {:item true}
        [:input
         {:type "file"
          :accept ".json"
          :on-change #(==> [::events/restore-site-backup
                            (-> % .-target .-files)
                            lipas-id])}]]]
      (when error
        [mui/grid {:item true :xs 12}
         [mui/typography {:variant "h6"} "Error"]
         (pr-str error)])]]))

(defn simplify-tool-container
  []
  (when-let [open? (<== [::subs/simplify-dialog-open?])]
    [mui/slide {:direction "up" :in open?}
     [:r> (react/forwardRef
            (fn [_props ref]
              (r/as-element [lui/floating-container {:ref ref :bottom 12 :left 550}
                             @simplify-tool-component])))]]))

(defn simplify-tool
  [{:keys [tr on-change on-close]
    :or {on-close #(==> [::events/close-simplify-tool])}}]
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
          {:size "small"
           :on-change #(==> [::events/set-simplify-tolerance %2])
           :value tolerance
           :marks (mapv (fn [n] {:label (str n) :value n}) (range 11))
           :step 0.5
           :min 0
           :max 10}]]

        ;; More
        [mui/grid {:item true}
         [mui/typography (tr :general/more)]]]]

      ;; Buttons
      [mui/grid {:item true :xs 12}
       [mui/grid {:container true :spacing 1}

        ;; OK
        [mui/grid {:item true}
         [mui/button
          {:variant "contained"
           :color "secondary"
           :on-click #(on-change tolerance)}
          "OK"]]

        ;; Cancel
        [mui/grid {:item true}
         [mui/button
          {:variant "outlined"
           :on-click on-close}
          (tr :actions/cancel)]]]]]]))

(defn layer-switcher [{:keys [tr]}]
  (let [basemaps {:taustakartta (tr :map.basemap/taustakartta)
                  :maastokartta (tr :map.basemap/maastokartta)
                  :ortokuva (tr :map.basemap/ortokuva)}
        basemap (<== [::subs/basemap])]
    [mui/grid {:container true :direction "column"}
     [mui/grid {:item true}
      [lui/select
       {:items basemaps
        :value (:layer basemap)
        :label-fn second
        :value-fn first
        :on-change #(==> [::events/select-basemap %])}]]
     [mui/grid {:item true}
      [mui/typography {:variant "caption"}
       (tr :map.basemap/copyright)]]]))

(defn overlay-selector
  [{:keys [tr]}]
  (r/with-let [anchor-el (r/atom nil)]
    (let [overlays {:light-traffic
                    {:label (tr :map.overlay/light-traffic)
                     :label2 "© Väylävirasto"
                     :icon [mui/icon "timeline"]}
                    :retkikartta-snowmobile-tracks
                    {:label (tr :map.overlay/retkikartta-snowmobile-tracks)
                     :label2 "© Metsähallitus"
                     :icon [mui/icon
                            {:style {:color "#0000FF"}}
                            "timeline"]}
                    :mml-kiinteisto
                    {:label (tr :map.overlay/mml-kiinteisto)
                     :label2 "© Maanmittauslaitos"
                     :icon [mui/icon
                            {:style {:color "red"}}
                            "timeline"]}
                    :mml-kiinteistotunnukset
                    {:label (tr :map.overlay/mml-property-identifiers)
                     :label2 "© Maanmittauslaitos"
                     :icon [mui/icon
                            {:style {:color "black"}}
                            "text_format"]}
                    :mml-kuntarajat
                    {:label (tr :map.overlay/municipal-boundaries)
                     :label2 "© Maanmittauslaitos"
                     :icon [mui/icon
                            {:style {:color "#6222BC"}}
                            "timeline"]}}
          selected-overlays (<== [::subs/selected-overlays])]
      [:<>
       (into

         [mui/menu
          {:open (boolean @anchor-el)
           :anchorEl @anchor-el
           :anchorOrigin {:vertical "top" :horizontal "left"}
           :transformOrigin {:vertical "bottom" :horizontal "left"}
           :on-close #(reset! anchor-el nil)}]

         (for [[k {:keys [label label2 icon]}] overlays
               :let [v (contains? selected-overlays k)]]
           [mui/menu-item
            {:on-click #(==> [::events/toggle-overlay k])}
            [mui/list-item-icon
             [mui/checkbox
              {:checked (boolean v)
               :size "medium"
               :value (str v)
               :color "secondary"
               :on-change #()}]]
            [mui/list-item-text
             {:primaryTypographyProps {:style {:font-size "0.9em" :margin-right "2em"}}
              :secondaryTypographyProps {:style {:font-size "0.7em" :margin-right "2em"}}
              :primary label :secondary label2}]
            [mui/list-item-icon
             icon]]))

       [mui/grid {:item true}
        [mui/tooltip {:title (tr :map.overlay/tooltip)}
         [mui/icon-button
          {:color (if @anchor-el "secondary" "default")
           :on-click
           (fn [evt] (reset! anchor-el (if @anchor-el nil (.-currentTarget evt))))}
          [mui/icon "layers"]]]]])))

(defn basemap-transparency-selector
  [{:keys [tr]}]
  (r/with-let [anchor-el (r/atom nil)]
    (let [opacity (<== [::subs/basemap-opacity])]
      [:<>
       [mui/popper
        {:id "basemap-transparency-selector"
         :placement "top"
         :open (some? @anchor-el)
         :anchorEl @anchor-el}
        [mui/paper
         {:style
          {:width "200px"
           :padding-right "2em"
           :padding-left "2em"
           :padding-top "0.5em"
           :padding-bottom "0.5em"
           :margin-bottom "1em"}}
         #_[mui/typography {:variant "caption"} (tr :map.basemap/transparency)]
         [mui/slider
          {:size "small"
           :value (- 1 opacity)
           :on-change #(==> [::events/set-basemap-opacity (- 1 %2)])
           :marks [{:value 0 :label "0%"}
                   {:value 1 :label "100%"}]
           :step 0.05
           :min 0.0
           :max 1.0}]]]
       [mui/tooltip {:title (tr :map.basemap/transparency)}
        [mui/icon-button
         {:on-click (fn [evt]
                      (reset! anchor-el (if @anchor-el nil (.-currentTarget evt))))}
         [mui/icon {:color (if @anchor-el "secondary" "default")} "opacity"]]]])))

(defn user-location-btn
  [{:keys [tr]}]
  [mui/tooltip {:title (tr :map/zoom-to-user)}
   [mui/icon-button {:on-click #(==> [::events/zoom-to-users-position])}
    [mui/icon {:color "inherit" :font-size "medium"}
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
      [mui/grid {:container true}
       [mui/grid {:item true :xs 12}
        [mui/text-field {:label (tr :search/search)
                         :xs 3
                         :on-change #(reset! search-term (-> % .-target .-value))
                         :placeholder nil
                         :variant "standard"}]]
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
               [mui/table-cell (->> row :geometry-type (keyword :type) tr)]
               [mui/table-cell (row :description)]]))]]]])))

(defn type-selector-single [{:keys [tr value on-change types]}]
  (r/with-let [selected-type (r/atom value)
               geom-help-open? (r/atom false)]
    (let [locale (tr)]
      [:<>

       ;; Modal
       [lui/dialog {:open? @geom-help-open?
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
       [mui/grid {:container true}

        ;; Autocomplete
        [mui/grid {:item true :xs 11}
         [lui/autocomplete
          {:multi? false
           :items (vals types)
           :value @selected-type
           :label (tr :type/name)
           :value-fn :type-code
           :label-fn (comp locale :name)
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
           [mui/button {:on-click #(on-change @selected-type)
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
    [mui/paper
     {:style
      {:padding "0.5em"
       :width (when (< 100 (count name')) "150px")}}
     [mui/typography {:variant "body2"}
      name']
     (when-not (#{"active"} status)
       [mui/typography {:variant "body2" :color "error"}
        (get-in ss/statuses [status locale])])]))

(defmethod popup-body :loi [popup]
  (let [loi-type (-> popup :data :features first :properties :loi-type)
        loi-category (-> popup :data :features first :properties :loi-category)
        #_#_tr (-> popup :tr)
        texts (<== [:lipas.ui.loi.subs/popup-localized loi-type loi-category])]
    [mui/paper
     {:style
      {:padding "0.5em"
       :width (when (< 100 (count loi-type)) "150px")}}
     [mui/typography {:variant "body2"}
      (:loi-type texts)]
     [mui/typography {:variant "caption"}
      (:loi-category texts)]]))

(defmethod popup-body :population [popup]
  (let [tr (<== [:lipas.ui.subs/translator])
        zone-labels (<== [:lipas.ui.analysis.reachability.subs/zones-popup-labels])
        metric (<== [:lipas.ui.analysis.reachability.subs/selected-travel-metric])
        data (-> popup :data :features first :properties)
        zone-id (keyword (:zone data))]
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
  (let [data (-> popup :data :features first :properties)]
    [mui/paper
     {:style
      {:padding "0.5em"}}
     [mui/typography {:variant "body2"}
      (:name data)]
     [mui/typography {:variant "caption"}
      (:type data)]]))

(defmethod popup-body :diversity-grid [popup]
  (let [tr (<== [:lipas.ui.subs/translator])
        data (-> popup :data :features first :properties)]
    [mui/paper
     {:style {:padding "0.5em"}}
     [mui/table {:padding "normal" :size "small"}
      [mui/table-body

       ;; Diversity index
       [mui/table-row
        [mui/table-cell
         [mui/typography {:variant "caption"} (tr :analysis/diversity-idx)]]
        [mui/table-cell
         [mui/typography {:variant "caption" :no-wrap true}
          (:diversity_idx data)]]]

       ;; Population
       [mui/table-row
        [mui/table-cell
         [mui/typography {:variant "caption"} (tr :analysis/population)]]
        [mui/table-cell
         [mui/typography {:variant "caption" :no-wrap true}
          (or (:population data) "<10")]]]]]]))

(defmethod popup-body :diversity-area [popup]
  (let [tr (<== [:lipas.ui.subs/translator])
        data (-> popup :data :features first :properties)]
    [mui/paper
     {:style
      {:padding "0.5em"}}

     (if (or (:population-weighted-mean data) (:population data))
       ;; Results table
       [mui/table {:padding "normal" :size "small"}
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
               (str/join "," (:diversity-idx-mode data)))]]]]

       ;; No data available
       [:div {:style {:width "200px" :padding "0.5em 0.5em 0em 0.5em"}}
        [mui/typography {:paragraph true}
         (str (:nimi data) " " (:posti_alue data))]
        [mui/typography {:paragraph true :variant "caption"}
         "Analyysiä ei ole tehty"]
        [mui/typography {:paragraph true :variant "caption"}
         "Klikkaa aluetta hiirellä tai valitse alue taulukosta."]])]))

(defui route-part-difficulty [{:keys [data]}]
  (let [{:keys [lipas-id fid]} data
        tr (use-subscribe [:lipas.ui.subs/translator])
        locale (tr)
        properties (use-subscribe [::subs/edit-geom-properties fid])
        value (:route-part-difficulty properties)]
    ($ Paper
       {:sx
        #js {:padding 2
             :width "350px"}}
       ($ TextField
          {:label (tr :map/route-part-difficulty)
           :select true
           :fullWidth true
           :value (or value "")
           :onChange (fn [e]
                       (rf/dispatch [::events/set-route-part-difficulty lipas-id fid (.. e -target -value)]))}
          ($ MenuItem
             {:key "empty"
              :value ""}
             "-")
          (for [[k {:keys [label description]}] activities-data/cycling-route-part-difficulty]
            ($ MenuItem
               {:key k
                :value k
                :sx #js {:flexDirection "column"
                         :alignItems "flex-start"
                         :maxWidth "350px"}}
               ($ Typography
                  (get label locale))
               ($ Typography
                  {:sx #js {:fontSize "body2.fontSize"
                            :whiteSpace "normal"}}
                  (get description locale))))))))

(defmethod popup-body :route-part-difficulty [popup]
  ($ route-part-difficulty {:data (:data popup)}))

(defmethod popup-body :heatmap [popup]
  (let [tr (<== [:lipas.ui.subs/translator])
        locale (tr)
        data (-> popup :data :features first :properties)
        dimension (<== [::heatmap-subs/dimension])
        weight-by (<== [::heatmap-subs/weight-by])
        ;; Get type labels for type-distribution dimension
        types-db (<== [:lipas.ui.sports-sites.subs/all-types])]

    [mui/paper
     {:style
      {:padding "0.5em"
       :min-width "200px"}}

     [mui/stack {:direction "column"}
      ;; Facility count
      [mui/typography {:variant "body2" :style {:font-weight "bold"}}
       (str (:doc_count data) " " (if (= 1 (:doc_count data))
                                    "liikuntapaikka"
                                    "liikuntapaikkaa"))]

      ;; Weight info
      (when (and weight-by (not= "doc-count" weight-by))
        [mui/typography {:variant "caption" :style {:margin-top "0.5em"}}
         (case (keyword weight-by)
           :area-m2 (str "Pinta-ala yhteensä: "
                         (utils/round-safe (:weight data) 0) " m²")
           (str "Paino: " (:weight data) " "))])

      ;; Relative intensity
      (let [intensity (* 100 (:normalized-weight data))]
        [mui/typography {:variant "caption" :color "textSecondary"}
         (cond
           (> intensity 80) "Erittäin korkea tiheys"
           (> intensity 60) "Korkea tiheys"
           (> intensity 40) "Keskitaso"
           (> intensity 20) "Matala tiheys"
           :else "Erittäin matala tiheys")])

      ;; Type distribution for type-distribution dimension
      (when (and (= :type-distribution dimension) (:types data))
        [:<>
         [mui/typography {:variant "caption" :style {:margin-top "0.5em" :font-weight "bold"}}
          (str "TOP-5 Tyypit: (yht. " (count (:types data)) ")")]
         (into [mui/list {:dense true :style {:padding 0}}]
               (for [{:keys [key doc_count]} (take 5 (sort-by :doc_count > (:types data)))
                     :let [type-label (get-in types-db [key :name locale] (str "Tyyppi " key))]]
                 [mui/list-item {:style {:padding "2px 0"}}
                  [mui/typography {:variant "caption"}
                   (str type-label ": " doc_count)]]))])

      ;; Activities for activities dimension
      (when (and (= :activities dimension) (:activities data))
        [:<>
         [mui/typography {:variant "caption" :style {:margin-top "0.5em" :font-weight "bold"}}
          "Aktiviteetit:"]
         (into [mui/list {:dense true :style {:padding 0}}]
               (for [{:keys [key doc_count]} (take 5 (sort-by :doc_count > (:activities data)))]
                 [mui/list-item {:style {:padding "2px 0"}}
                  [mui/typography {:variant "caption"}
                   (str key ": " doc_count)]]))])]

     ;; Grid reference (optional, for debugging)
     #_[mui/typography {:variant "caption" :color "textSecondary" :style {:margin-top "0.5em"}}
        (str "Grid: " (:grid_key data))]]))

(defn popup [{:keys [popup-ref]}]
  (let [{:keys [data placement]
         :as popup'} (<== [::subs/popup])
        tr (<== [:lipas.ui.subs/translator])
        [anchor-el set-anchor-el] (uix/use-state nil)]
    [:<>
     [:div {:ref (fn [el]
                   (set-anchor-el el)
                   (set! (.-current popup-ref) el))}]
     (when (seq data)
       [mui/popper
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
     [lui/dialog
      {:open? dialog-open?
       :title (tr :map.resolve-address/choose-address)
       :save-label "Ok"
       :save-enabled? (some? selected-address)
       :on-close #(==> [::events/close-address-locator-dialog])
       :cancel-label (tr :actions/cancel)
       :on-save (fn []
                  (==> [::events/close-address-locator-dialog])
                  (==> [::events/populate-address-with-reverse-geocoding-results lipas-id cities {:features [{:properties selected-address}]}]))}

      [mui/grid {:container true :spacing 2}

       ;; Helper text 1
       [mui/grid {:item true :xs 12}
        [mui/typography (tr :map.resolve-address/helper-text1)]]

       ;; Helper text 2
       [mui/grid {:item true :xs 12}
        [mui/typography (tr :map.resolve-address/helper-text2)]]

       ;; Address selector
       [mui/grid {:item true :xs 12}
        [lui/autocomplete
         {:label (tr :map.resolve-address/addresses)
          :items addresses
          :value selected-address
          :label-fn :label
          #_#_:label-fn #(str (:label %) " " (:confidence %) " " (:distance %))
          :value-fn identity
          :sort-fn (juxt (comp - :confidence) :distance)
          :on-change #(==> [::events/select-address-locator-address %])}]]]]

     ;; Button
     [lui/locator-button
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
          [mui/menu-item {:on-click #(do
                                       (==> [::events/close-more-tools-menu])
                                       (==> [::events/toggle-import-dialog]))}
           [mui/list-item-icon
            [:> FileUpload]]
           [mui/list-item-text (tr :map.import/tooltip)]])

        ;; Simplify
        (when (and editing? can-edit-map? (#{"LineString" "Polygon"} geom-type))
          [mui/menu-item {:on-click #(do
                                       (==> [::events/close-more-tools-menu])
                                       (==> [::events/open-simplify-tool]))}
           [mui/list-item-icon
            [mui/icon "auto_fix_high"]]
           [mui/list-item-text (tr :map.tools/simplify)]])

        ;; Draw hole
        (when (and editing? can-edit-map? (#{"Polygon"} geom-type))
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
        (when (and editing? can-edit-map? (#{"LineString" "Polygon"} geom-type))

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
                                 "Polygon" (tr :map/draw-polygon))]])

        ;; Delete geom
        (when (and editing? can-edit-map? (#{"LineString" "Polygon"} geom-type))
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
                                 "Polygon" (tr :map/remove-polygon))]])

        ;; Split linestring
        (when (and editing? can-edit-map? (#{"LineString"} geom-type))
          [mui/menu-item
           {:on-click
            #(do
               (==> [::events/close-more-tools-menu])
               (==> [::events/start-editing lipas-id :splitting geom-type]))}
           [mui/list-item-icon
            [:> ContentCut
             {:color (if (= sub-mode :splitting) "secondary" "inherit")}]]
           [mui/list-item-text (tr :map/split-linestring)]])

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
          [mui/menu-item
           {:on-click
            #(do
               (==> [::events/close-more-tools-menu])
               (==> [::events/start-editing lipas-id :travel-direction geom-type]))}
           [mui/list-item-icon
            [mui/icon
             {:color (if (= sub-mode :travel-direction) "secondary" "inherit")}
             "turn_slight_right"]]
           [mui/list-item-text (tr :map/travel-direction)]])

        ;; Route part difficulty
        (when (and editing?
                   edit-activities?
                   (#{"LineString"} geom-type)
                   (= "cycling" activity-value))
          [mui/menu-item
           {:on-click
            #(do
               (==> [::events/close-more-tools-menu])
               (==> [::events/start-editing lipas-id :route-part-difficulty geom-type]))}
           [mui/list-item-icon
            [mui/icon
             {:color (if (= sub-mode :route-part-difficulty) "secondary" "inherit")}
             "warning"]]
           [mui/list-item-text (tr :map/route-part-difficulty)]])

        ;; Download backup
        (when editing?
          [mui/menu-item
           {:on-click
            #(do
               (==> [::events/download-site-backup lipas-id])
               (==> [::events/close-more-tools-menu]))}
           [mui/list-item-icon
            [mui/icon {:color "inherit"} "cloud_download"]]
           [mui/list-item-text (tr :map.tools/download-backup-tooltip)]])

        ;; Restore backup
        (when editing?
          [mui/menu-item
           {:on-click
            #(do
               (==> [::events/open-restore-site-backup-dialog lipas-id])
               (==> [::events/close-more-tools-menu]))}
           [mui/list-item-icon
            [mui/icon {:color "inherit"} "cloud_upload"]]
           [mui/list-item-text (tr :map.tools/restore-backup-tooltip)]])

        ;; Edit tool
        (when (and editing? can-edit-map? (#{"LineString" "Polygon"} geom-type))
          [mui/menu-item
           {:on-click
            #(do
               (==> [::events/close-more-tools-menu])
               (==> [::events/start-editing lipas-id :editing geom-type]))}
           [mui/list-item-icon
            [mui/icon
             {:color (if (= sub-mode :editing) "secondary" "inherit")}
             "edit"]]
           [mui/list-item-text (tr :map.tools/edit-tool)]])]
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

    [mui/grid
     {:container true
      :style (merge {:padding "1em"} (when (utils/ie?) {:width "420px"}))}

     (when editing?
       [import/import-geoms-view
        {:geom-type geom-type
         :on-import #(==> [::events/import-selected-geoms])}])

     (when editing?
       [restore-site-backup-dialog])

     [mui/grid {:item true :xs 12}

      ;; Headline
      [mui/grid
       {:container true
        :style {:flex-wrap "nowrap"}
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
                    :tr tr}]))

       [mui/grid {:item true}
        ;; Close button
        [mui/grid {:item true}
         (when (not editing?)
           [mui/icon-button
            {:style {:margin-left "-0.25em"}
             :on-click #(==> [::events/show-sports-site nil])}
            [mui/icon "close"]])]]]

      ;; Tabs
      [mui/grid {:item true :xs 12}
       [mui/tabs
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
        [mui/tab
         {:style {:min-width 0}
          :value 0
          :label (tr :lipas.sports-site/basic-data)}]

        [mui/tab
         {:style {:min-width 0}
          :value 1
          :label (tr :lipas.sports-site/properties)}]

       ;; Disabled in prod until this can be released
        (when (and (not (utils/prod?)) accessibility-type?)
          [mui/tab
           {:style {:min-width 0}
            :value 2
            :label (tr :lipas.sports-site/accessibility)}])

        (when view-floorball?
          [mui/tab
           {:style {:min-width 0}
            :value 3
            :label (tr :lipas.floorball/headline)}])

        (when view-activities?
          [mui/tab
           {:style {:min-width 0}
            :value 5
            :label (tr :utp/headline)}])

        (when (#{"LineString"} geom-type)
          [mui/tab
           {:style {:min-width 0}
            :value 4
            :label (tr :sports-site.elevation-profile/headline)}])

        (when view-ptv?
          [mui/tab
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
         0 [mui/grid {:container true}

            [mui/grid {:item true :xs 12}

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
              [mui/tabs {:value "floorball" :variant "standard"}
               [mui/tab {:value "floorball" :label "Salibandy"}]]
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
             [mui/grid {:item true :xs 12 :style {:margin-top "0.5em"}}
              [sports-sites/elevation-profile {:lipas-id lipas-id}]])

         5 [mui/grid {:item true :xs 12}
            [activities/view
             {:tr tr
              :lipas-id lipas-id
              :type-code type-code
              :display-data display-data
              :edit-data edit-data
              :can-edit? edit-activities?}]]

         6 [mui/grid {:item true :xs 12}
            ($ ptv-site/site-view
               {:tr tr
                :lipas-id lipas-id
                :type-code type-code
                ; :display-data display-data
                :edit-data edit-data
                :can-edit? can-publish?})])]

;; "Landing bay" for floating tools
      [mui/grid {:item true :xs 12 :style {:height "3em"}}]

     ;; Actions
      (when-not hide-actions?
        [lui/floating-container
         {:bottom 0 :background-color "transparent"}
         (into
           [mui/grid
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
                [mui/tooltip {:title (tr :actions/undo)}
                 [:span
                  [mui/fab
                   {:disabled (not undo)
                    :size "small"
                    :on-click #(==> [::events/undo lipas-id])}
                   [mui/icon "undo"]]]])

           ;; Redo
              (when editing?
                [mui/tooltip {:title (tr :actions/redo)}
                 [:span
                  [mui/fab
                   {:disabled (not redo)
                    :size "small"
                    :on-click #(==> [::events/redo lipas-id])}
                   [mui/icon "redo"]]]])

            ;; Active editing tool
              (when (and editing? (seq map-tool-items))
                [mui/tooltip
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
                 [mui/fab
                  {:size "small"
                   :on-click #() ; noop
                   :color "inherit"}
                  (let [props {:color "secondary"}]
                    (case sub-mode
                      :drawing (case geom-type
                                 "Point" [mui/icon props "edit"]
                                 "LineString" [mui/icon props "timeline"]
                                 "Polygon" [mui/icon props "change_history"])
                      :drawing-hole [mui/icon props "vignette"]
                      (:editing :undo) [mui/icon props "edit"]
                      :importing [:> FileUpload props]
                      :deleting [:> Eraser props]
                      :splitting [:> ContentCut props]
                      :simplifying [mui/icon props "auto_fix_high"]
                      :selecting [mui/icon props "handshake"]
                      :travel-direction [mui/icon props "turn_slight_right"]
                      :route-part-difficulty [mui/icon props "warning"]
                      :view-only [mui/icon props "dash"]))]])

           ;; Tool select button
              (when editing?
                (when (seq map-tool-items)
                  [:<>
                   [mui/tooltip {:title (tr :actions/select-tool)}
                    [mui/fab
                     {:size "medium"
                      :on-click #(==> [::events/open-more-tools-menu (.-currentTarget %)])
                      :color "secondary"}
                     [mui/icon "more_horiz"]]]

                   (into [mui/menu
                          {:variant "menu"
                           :auto-focus false
                           :anchor-el more-tools-menu-anchor
                           :open (some? more-tools-menu-anchor)
                           :on-close #(==> [::events/close-more-tools-menu])}]
                         map-tool-items)]))

           ;; Download GPX
              (when (and (not editing?) (#{"LineString"} geom-type))
                [mui/tooltip {:title (tr :map/download-gpx)}
                 [mui/fab
                  {:size "small"
                   :on-click #(==> [::events/download-gpx lipas-id])
                   :color "inherit"}
                  [mui/icon "save_alt"]]])

           ;; Zoom to site
              (when-not editing?
                [mui/tooltip {:title (tr :map/zoom-to-site)}
                 [mui/fab
                  {:size "small"
                   :on-click #(==> [::events/zoom-to-site lipas-id width])
                   :color "inherit"}
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
                  {:size "small"
                   :on-click #(==> [::events/duplicate-sports-site lipas-id])}
                  [:> ContentDuplicate]]])

           ;; Resurrect button
              (when (and dead? logged-in? can-publish? editing-allowed?)
                [mui/tooltip {:title (tr :actions/resurrect)}
                 [mui/fab
                  {:size "small"
                   :on-click #(==> [::events/resurrect lipas-id])}
                  [mui/icon "360"]]])

           ;; Analysis
              (when (and @(rf/subscribe [:lipas.ui.user.subs/check-privilege
                                         (roles/site-roles-context display-data)
                                         :analysis-tool/use])
                         (not editing?))
                [mui/tooltip {:title (tr :map.demographics/tooltip)}
                 [mui/fab
                  {:size "small"
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
             (map (fn [tool] [mui/grid {:item true} tool]))))])]]))

(defn add-btn [{:keys [tr]}]
  [mui/tooltip {:title (tr :lipas.sports-site/add-new)}
   [mui/fab
    {:color "secondary"
     :on-click #(==> [::events/start-adding-new-site])}
    [mui/icon "add"]]])

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

      [mui/grid {:container true :spacing 2 :style {:padding "1em"}}

       [mui/grid {:item true :xs 12}

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

        [mui/typography {:variant "h6" :style {:margin-left "8px"}}
         (if-let [type-name (get-in type [:name locale])]
           (tr :lipas.sports-site/new-site-of-type type-name)
           (tr :lipas.sports-site/new-site {:type type :locale locale}))]]

       ;; Steps
       [mui/grid {:item true :xs 12}
        (when is-planning?
          [:> Alert
           {:severity "info"
            :sx #js {:mb 2}}
           (tr :lipas.sports-site/creating-planning-site)])

        [mui/stepper
         {:active-step active-step
          :alternativeLabel true
          :style {:margin-left "-18px"}
          :orientation "horizontal"
          :sx (fn [theme]
                #js {".Mui-active" #js {:fill (.. theme -palette -secondary -main)}})}

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
             {:value (when type [(:type-code type)])
              :tr tr
              :types types
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

                [mui/tabs {:value @geom-tab
                           :on-change #(reset! geom-tab %2)
                           :variant "fullWidth"
                           :indicator-color "secondary"
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
                     :color "secondary"
                     :variant "contained"
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
                        {:style {:min-width "150px"}
                         :label "CRS"
                         :items [{:value :epsg3067 :label "TM35FIN EUREF"}
                                 {:value :epsg4326 :label "WGS84"}]
                         :value (:crs @state)
                         :on-change #(swap! state assoc :crs %1)}]]
                      [mui/grid {:item true :xs 6}
                       [lui/text-field
                        {:label (condp = (:crs @state)
                                  :epsg4326 "Lon"
                                  :epsg3067 "N")
                         :type "number"
                         :spec lon-spec
                         :value (:lon @state)
                         :on-change #(swap! state assoc :lon %1)}]]
                      [mui/grid {:item true :xs 6}
                       [lui/text-field
                        {:label (condp = (:crs @state)
                                  :epsg4326 "Lat"
                                  :epsg3067 "E")
                         :type "number"
                         :spec lat-spec
                         :value (:lat @state)
                         :on-change #(swap! state assoc :lat %1)}]]
                      [mui/grid {:item true}
                       [mui/button
                        {:color "secondary"
                         :disabled (not (and
                                          (s/valid? lon-spec (:lon @state))
                                          (s/valid? lat-spec (:lat @state))))
                         :variant "contained"
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
                      {:color "secondary"
                       :variant "contained"
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
                   "Polygon" (tr :map/modify-polygon)
                   (tr :map/modify))]
                [mui/typography {:variant "caption" :style {:margin-top "0.5em"}}
                 (tr :map/edit-later-hint)]]

               ;; Add additional geom button
               (when (#{"LineString" "Polygon"} geom-type)
                 [mui/grid {:item true}
                  [mui/button
                   {:on-click #(==> [::events/start-adding-geom geom-type])
                    :variant "contained"
                    :color "secondary"}
                   (case geom-type
                     "LineString" (tr :map/draw-linestring)
                     "Polygon" (tr :map/draw-polygon)
                     (tr :map/draw))]])

               ;; Delete geom
               (when (#{"LineString" "Polygon"} geom-type)
                 [mui/grid {:item true}
                  [mui/tooltip
                   {:title (case geom-type
                             "LineString" (tr :map/remove-linestring)
                             "Polygon" (tr :map/remove-polygon)
                             (tr :map/remove))}
                   [:span
                    [mui/button
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
                 [mui/grid {:item true}
                  [mui/tooltip {:title (tr :map/split-linestring)}
                   [:span
                    [mui/button
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
                 [mui/grid {:item true}
                  [mui/tooltip {:title (tr :map.tools/simplify)}
                   [:span
                    [mui/button
                     {:on-click #(==> [::events/open-simplify-tool])
                      :disabled (or (-> geom :features empty?)
                                    (= sub-mode :simplifying))
                      :style (when (= sub-mode :simplifying)
                               {:outline (str "2px solid " mui/secondary)})
                      :variant "contained"
                      :color "gray1"}
                     [mui/icon "auto_fix_high"]]]]])

               ;; Delete vertices helper text
               (when (#{"LineString" "Polygon"} geom-type)
                 [mui/grid {:item true :xs 12}
                  [mui/typography {:variant "caption" :style {:margin-top "0.5em"}}
                   (tr :map/delete-vertices-hint)]])

               ;; Done button and undo / redo
               [mui/grid {:item true :xs 12}
                [mui/grid
                 {:container true
                  :justify-content "flex-start"
                  :align-items "center"
                  :spacing 1}

                 ;; Ready button
                 [mui/grid {:item true}
                  [mui/button
                   {:on-click #(==> [::events/finish-adding-geom geom type-code])
                    :variant "contained"
                    :disabled (-> geom :features empty?)
                    :color "secondary"}
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
                    :title (str
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
             {:value selected-tab
              :on-change #(==> [::events/select-new-sports-site-tab %2])
              :variant "fullWidth"
              :indicator-color "secondary"
              :text-color "inherit"
              :style {:margin-bottom "1em" :margin-top "1em"}}
             [mui/tab {:label (tr :lipas.sports-site/basic-data)}]
             [mui/tab {:label (tr :lipas.sports-site/properties)}]
             ;; TODO: This could be view-floorball? but right not it is not useful to
             ;; show the tab in create-site if user can't edit the data
             (when edit-floorball?
               [mui/tab {:label "Olosuhteet"}])]

            (case selected-tab

              ;; Basic info tab
              0 [mui/grid {:container true}
                 [mui/grid {:item true :xs 12}

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
                   [mui/tabs
                    {:value "floorball"
                     :variant "standard"
                     :indicator-color "secondary"
                     :text-color "inherit"}
                    [mui/tab {:value "floorball" :label "Salibandy"}]]
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
       [mui/grid {:container true :align-items "flex-end"}
        [mui/grid {:item true :xs 12 :style {:height "50px"}}
         [lui/floating-container {:bottom 0 :background-color "transparent"}
          [mui/grid
           {:container true
            :align-items "center"
            :spacing 1
            :style {:padding "0.5em"}}

           [address-search-dialog]

           ;; Save
           (when data
             [mui/grid {:item true}
              [lui/save-button
               {:tooltip (tr :actions/save)
                :disabled-tooltip (tr :actions/fill-required-fields)
                :disabled (not save-enabled?)
                :on-click #(==> [::events/save-new-site data])}]])

           [mui/grid {:item true}

            ;; Discard
            [lui/discard-button
             {:on-click #(==> [::events/discard-new-site])
              :tooltip (tr :actions/discard)}]]

           ;; Address search button
           [mui/tooltip {:title (tr :map.address-search/tooltip)}
            [mui/grid {:item true}
             [mui/fab
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
     [lui/floating-container {:bottom 0 :background-color "transparent"}
      [mui/grid
       {:container true
        :align-items "center"
        :spacing 1
        :style {:padding-bottom "0.5em"}}

       ;; Create sports site btn
       (when show-create-button?
         [mui/grid {:item true}
          [add-btn {:tr tr}]])

       (when (= :analysis mode-name)
         [mui/grid {:item true}
          [mui/tooltip {:title (tr :lipas.sports-site/add-new-planning)}
           [mui/fab
            {:color "secondary"
             :variant "extended"
             :on-click #(==> [::events/add-analysis-target])}
            [mui/icon "add"]
            (tr :lipas.sports-site/planning-site)]]])

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
       (when (and @(rf/subscribe [:lipas.ui.user.subs/check-privilege
                                  {:type-code ::roles/any
                                   :city-code ::roles/any
                                   :lipas-id ::roles/any}
                                  :analysis-tool/use])
                  ;; logged-in?
                  (= :list result-view))
         [mui/tooltip {:title (tr :map.demographics/tooltip)}
          [mui/grid {:item true}
           [mui/fab
            {:size "small"
             :style (when (= mode-name :analysis)
                      {:border (str "5px solid " mui/secondary)})
             :on-click #(==> (if (= mode-name :analysis)
                               [::events/hide-analysis]
                               [::events/show-analysis]))}
            [mui/icon "insights"]]]])

       ;; PTV button
       (when ptv-privilege
         [mui/tooltip {:title (tr :ptv/tooltip)}
          [mui/grid {:item true}
           [mui/fab
            {:size "small"
             :on-click #(==> (if ptv-dialog-open?
                               [:lipas.ui.ptv.events/close-dialog]
                               [:lipas.ui.ptv.events/open-dialog]))}
            [mui/icon "ios_share"]]]])]]]))

(defn add-view
  [{:keys [tr width]}]
  (let [add-mode (<== [::subs/selected-add-mode])
        can-add-sports-sites? (<== [:lipas.ui.user.subs/can-add-sports-sites?])
        can-add-lois? (<== [:lipas.ui.user.subs/can-add-lois?])]
    [:<>
     (when (and can-add-sports-sites? can-add-lois?)
       [mui/tabs
        {:value add-mode
         :on-change #(==> [::events/select-add-mode %2])
         :variant "fullWidth"
         :indicator-color "secondary"
         :text-color "inherit"}
        [mui/tab {:value "sports-site" :label (tr :lipas.sports-site/headline)}]
        [mui/tab {:value "loi" :label (tr :loi/headline)}]])

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
       [mui/stack
        {:sx {:px 1}}
        [default-tools {:tr tr :logged-in? logged-in?}]])]))

(defn map-view []
  (let [tr (<== [:lipas.ui.subs/translator])
        logged-in? (<== [:lipas.ui.subs/logged-in?])
        drawer-open? (<== [::subs/drawer-open?])
        width (mui/use-width)
        drawer-width (<== [::subs/drawer-width width])
        popup-ref (uix/use-ref nil)]

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
           {:size (if (utils/mobile? width) "small" "medium")
            :on-click #(==> [::events/toggle-drawer])
            :color "secondary"}
           [mui/icon "search"]]]])

      ;; Closable left sidebar drawer
      [mui/drawer
       {:variant "persistent"
        :PaperProps {:style {:width drawer-width}}
        :SlideProps {:direction "down"}
        :open drawer-open?}

       ;; Close button
       [mui/button
        {:on-click #(==> [::events/toggle-drawer])
         :style {:min-height "36px" :margin-bottom "1em"}
         :variant "outlined"
         :color "inherit"}
        [mui/icon "expand_less"]]

       ;; Content
       [map-contents-view {:tr tr :logged-in? logged-in? :width width}]]]

     ;; Floating container (bottom right)
     [lui/floating-container
      {:bottom "0.2em"
       :right "3.5em"
       :background-color "transparent"}

      [mui/grid2
       {:container true
        :spacing 1
        :sx #js {:bgcolor mui/gray2
                 :padding 1
                 :justifyContent "center"
                 :alignItems "center"
                 :border-radius 4}
        :wrap "nowrap"}

       ;; Feedback btn
       #_[mui/grid
          [mui/paper {:style {:background-color "rgba(255,255,255,0.9)"}}
           [feedback/feedback-btn]]]

       ;; Zoom to users location btn
       [mui/grid
        [mui/paper {:style {:background-color "rgba(255,255,255,0.9)"}}
         [user-location-btn {:tr tr}]]]

       ;; Overlay selector
       [mui/grid
        [mui/paper {:style {:background-color "rgba(255,255,255,0.9)"}}
         [overlay-selector {:tr tr}]]]

       ;; Basemap opacity selector
       [mui/grid
        [mui/paper {:style {:background-color "rgba(255,255,255,0.9)"}}
         [basemap-transparency-selector {:tr tr}]]]

       ;; Basemap switcher
       [mui/grid
        [mui/paper
         {:elevation 1
          :style
          {:background-color "rgba(255,255,255,0.9)"
           :padding-left "0.5em" :padding-right "0.5em"}}
         [layer-switcher {:tr tr}]]]]]

     [:f> popup
      {:popup-ref popup-ref}]

     ;; The map
     [ol-map/map-outer
      {:popup-ref popup-ref}]]))

(defn main []
  [:f> map-view])
