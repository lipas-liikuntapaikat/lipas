(ns lipas.ui.map.views
  (:require
   [clojure.string :as string]
   [lipas.ui.components :as lui]
   [lipas.ui.map.events :as events]
   [lipas.ui.map.map :as ol-map]
   [lipas.ui.map.subs :as subs]
   [lipas.ui.mui :as mui]
   [lipas.ui.navbar :as nav]
   [lipas.ui.reminders.views :as reminders]
   [lipas.ui.reports.views :as reports]
   [lipas.ui.search.views :as search]
   [lipas.ui.sports-sites.events :as sports-site-events]
   [lipas.ui.sports-sites.subs :as sports-site-subs]
   [lipas.ui.sports-sites.views :as sports-sites]
   [lipas.ui.utils :refer [<== ==>] :as utils]
   [reagent.core :as r]))

(def import-formats [".zip" ".kml" ".gpx" ".json"])

(defn helper [{:keys [label tooltip]}]
  [mui/tooltip {:title tooltip}
   [mui/link {:style {:margin "0.5em"} :underline "always"} label]])

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
        {:style      {:width "250px"}
         :auto-focus true
         :defer-ms   150
         :label      (tr :search/search)
         :value      value
         :on-change  #(==> [::events/update-address-search-keyword %])}]]
      (into
       [mui/list]
       (for [m results]
         [mui/list-item
          {:button   true
           :on-click #(==> [::events/show-address m])}
          [mui/list-item-text
           (:label m)]]))]]))

(defn import-geoms-view [{:keys [on-import show-replace?]
                          :or   {show-replace? true}}]
  (let [tr       (<== [:lipas.ui.subs/translator])
        open?    (<== [::subs/import-dialog-open?])
        encoding (<== [::subs/selected-import-file-encoding])
        data     (<== [::subs/import-candidates])
        headers  (<== [::subs/import-candidates-headers])
        selected (<== [::subs/selected-import-items])
        replace? (<== [::subs/replace-existing-geoms?])

        on-close #(==> [::events/toggle-import-dialog])]

    [mui/dialog
     {:open       open?
      :full-width true
      :max-width  "xl"
      :on-close   on-close}

     [mui/dialog-title (tr :map.import/headline)]

     [mui/dialog-content

      [mui/grid {:container true :spacing 8}

       ;; File selector, helpers and encoding selector
       [mui/grid {:item true :xs 12}
        [mui/grid {:container true :spacing 16 :align-items "flex-end" :justify "space-between"}

         ;; File selector
         [mui/grid {:item true}
          [:input
           {:type      "file"
            :accept    (string/join "," import-formats)
            :on-change #(==> [::events/load-geoms-from-file (-> % .-target .-files)])}]]

         ;; Helper texts
         [mui/grid {:item true}
          [mui/typography {:inline true} (str (tr :help/headline) ":")]
          [helper {:label "Shapefile" :tooltip (tr :map.import/shapefile)}]
          [helper {:label "GeoJSON" :tooltip (tr :map.import/geoJSON)}]
          [helper {:label "GPX" :tooltip (tr :map.import/gpx)}]
          [helper {:label "KML" :tooltip (tr :map.import/kml)}]]

         ;; File encoding selector
        [mui/grid {:item true}
         [lui/select
          {:items     ["utf-8" "ISO-8859-1"]
           :label     (tr :map.import/select-encoding)
           :style     {:min-width "120px"}
           :value     encoding
           :value-fn  identity
           :label-fn  identity
           :on-change #(==> [::events/select-import-file-encoding %])}]]]]

       [mui/grid {:item true :xs 12}
        (when (not-empty data)
          [lui/table-v2
           {:items         (->> data vals (map :properties))
            :key-fn        :id
            :multi-select? true
            :on-select     #(==> [::events/select-import-items %])
            :headers       headers}])]]]

     [mui/dialog-actions

      ;; Replace existing feature checkbox
      (when show-replace?
        [lui/checkbox
         {:label     (tr :map.import/replace-existing?)
          :value     replace?
          :on-change #(==> [::events/toggle-replace-existing-selection])}])

      ;; Cancel button
      [mui/button {:on-click on-close}
       (tr :actions/cancel)]

      ;; Import button
      [mui/button {:on-click on-import :disabled (empty? selected)}
       (tr :map.import/import-selected)]]]))

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

(defn user-location-btn [{:keys [tr]}]
  [mui/tooltip {:title (tr :map/zoom-to-user)}
   [mui/fab {:size "small" :on-click #(==> [::events/zoom-to-users-position])}
    [mui/icon {:color "default" :font-size "default"}
     "my_location"]]])

(defn type-selector-single [{:keys [tr value on-change types]}]
  (r/with-let [selected-type (r/atom value)]
    (let [locale (tr)]
      [mui/grid {:container true}
       [mui/grid {:item true}
        [lui/autocomplete
         {:multi?    false
          :show-all? true
          :items     (vals types)
          :value     value
          :label     (tr :type/name)
          :value-fn  :type-code
          :label-fn  (comp locale :name)
          :on-change #(reset! selected-type (first %))}]
        (when @selected-type
          [mui/grid {:item true}
           [mui/typography {:style {:margin-top "1em" :margin-bottom "1em"}}
            (get-in types [@selected-type :description locale])]
           [mui/button {:on-click   #(on-change @selected-type)
                        :auto-focus true
                        :variant    "contained"
                        :color      "secondary"}
            "OK"]])]])))

(defn popup []
  (let [{:keys [data anchor-el]} (<== [::subs/popup])
        {:keys [name]}           (-> data :features first :properties)]
    [mui/popper
     {:open      (boolean (seq data))
      :placement "top-end"
      :anchor-el anchor-el
      :container anchor-el
      :modifiers {:offset {:enabled true :offset "0px,10px"}}}
     [mui/paper {:style {:padding "0.5em"}}
      [mui/typography {:variant "body2"}
       name]]]))

(defn set-field
  [lipas-id & args]
  (==> [::sports-site-events/edit-field lipas-id (butlast args) (last args)]))

(defn- sticky-bottom-container [& children]
  (let [sticky (cond
                 (utils/supports-sticky?)        "sticky"
                 (utils/supports-webkit-sticky?) "webkit-sticky"
                 :else                           nil)]
    (into
     [mui/grid
      {:container true :direction "row" :spacing 8 :align-items "center"
       :style {:position sticky :bottom (when sticky 0)}}]
     children)))

;; Works as both display and edit views
(defn sports-site-view [{:keys [tr site-data width]}]
  (r/with-let [selected-tab (r/atom 0)]
    (let [display-data (:display-data site-data)
          lipas-id     (:lipas-id display-data)
          edit-data    (:edit-data site-data)

          type-code (-> display-data :type :type-code)

          {:keys [cities admins owners editing? edits-valid?
                  editing-allowed? delete-dialog-open? can-publish? logged-in?
                  size-categories sub-mode types-props geom-type portal]}
          (<== [::subs/sports-site-view lipas-id type-code])

          allowed-types (<== [::sports-site-subs/types-by-geom-type geom-type])
          set-field     (partial set-field lipas-id)]

      [mui/grid
       {:container true :style {:padding "1em"}}

       (when editing?
         [import-geoms-view {:on-import #(==> [::events/import-selected-geoms])}])

       [mui/grid {:item true :xs 12}

        ;; Headline
        [mui/grid
         {:container   true
          :style       {:flex-wrap "nowrap"}
          :align-items :center}

         [mui/grid {:item true :style {:margin-top "0.5em" :flex-grow 1}}
          [mui/typography {:variant "h5"}
           (:name display-data)]]

         ;; Close button
         [mui/grid {:item true}
          (when (not editing?)
            [mui/icon-button
             {:style    {:margin-left "-0.25em"}
              :on-click #(==> [::events/show-sports-site nil])}
             [mui/icon "close"]])]]]

       ;; Tabs
       [mui/grid {:item true :xs 12}
        [mui/tool-bar
         [mui/tabs
          {:value      @selected-tab
           :on-change  #(reset! selected-tab %2)
           :style      {:margin-bottom "1em"}
           :text-color "secondary"}
          [mui/tab {:label (tr :lipas.sports-site/basic-data)}]
          [mui/tab {:label (tr :lipas.sports-site/properties)}]]]

        (when delete-dialog-open?
          [sports-sites/delete-dialog
           {:tr       tr
            :lipas-id lipas-id
            :on-close #(==> [::sports-site-events/toggle-delete-dialog])}])

        (case @selected-tab

          ;; Basic info tab
          0 [mui/grid {:container true}
             [mui/grid {:item true :xs 12}

              ^{:key (str "basic-data-" lipas-id)}
              [sports-sites/form
               {:tr              tr
                :display-data    display-data
                :edit-data       edit-data
                :read-only?      (not editing?)
                :types           (vals allowed-types)
                :size-categories size-categories
                :admins          admins
                :owners          owners
                :on-change       set-field
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
          1 (if portal
              [mui/button {:href (str "/#/" portal "/hallit/" lipas-id)}
               [mui/icon "arrow_right"]
               (tr :lipas.sports-site/details-in-portal portal)]

              ^{:key (str "props-" lipas-id)}
              [sports-sites/properties-form
               {:tr           tr
                :types-props  types-props
                :read-only?   (not editing?)
                :on-change    (partial set-field :properties)
                :display-data (:properties display-data)
                :edit-data    (:properties edit-data)
                :key          type-code}]))]

       ;; Actions
       [sticky-bottom-container
        (into
         [mui/grid
          {:container true :align-items "center" :spacing 8
           :style     {:padding "0.5em 0em 0.5em 0em"}}]
         (->>

          [;; Download GPX
           (when (and (not editing?) (#{"LineString"} geom-type))
             [mui/tooltip {:title (tr :map/download-gpx)}
              [mui/fab
               {:size     "small"
                :on-click #(==> [::events/download-gpx lipas-id])
                :color    "default"}
               [mui/icon "save_alt"]]])

           ;; Zoom to site
           (when-not editing?
             [mui/tooltip {:title (tr :map/zoom-to-site)}
              [mui/fab
               {:size     "small"
                :on-click #(==> [::events/zoom-to-site lipas-id width])
                :color    "default"}
               [mui/icon {:color "default"}
                "place"]]])

           ;; Add reminder
           (when (and logged-in? (not editing?))
             (let [name (-> display-data :name)
                   link (-> js/window .-location .-href)]
               [reminders/add-button
                {:message (tr :reminders/placeholder name link)}]))

           ;; Import geom
           (when (and editing? (#{"LineString"} geom-type))
             [mui/tooltip {:title (tr :map.import/tooltip)}
              [mui/fab
               {:size     "small"
                :on-click #(==> [::events/toggle-import-dialog])
                :color    "default"}
               [:> js/materialIcons.FileUpload]]])

           ;; Draw hole
           (when (and editing? (#{"Polygon"} geom-type))
             [mui/tooltip {:title (tr :map/draw-hole)}
              [mui/fab
               {:size     "small"
                :on-click #(if (= sub-mode :drawing-hole)
                             (==> [::events/start-editing lipas-id :editing geom-type])
                             (==> [::events/start-editing lipas-id :drawing-hole geom-type]))
                :style    (when (= sub-mode :drawing-hole)
                            {:border (str "5px solid " mui/secondary)})
                :color    "default"}
               [mui/icon "vignette"]]])

           ;; Add new geom
           (when (and editing? (#{"LineString" "Polygon"} geom-type))
             [mui/tooltip {:title (tr :map/draw geom-type)}
              [mui/fab
               {:size     "small"
                :on-click #(if (= sub-mode :drawing)
                             (==> [::events/start-editing lipas-id :editing geom-type])
                             (==> [::events/start-editing lipas-id :drawing geom-type]))
                :style    (when (= sub-mode :drawing)
                            {:border (str "5px solid " mui/secondary)})
                :color    "default"}
               (if (= geom-type "LineString")
                 [mui/icon "timeline"]
                 [mui/icon "change_history"])]])

           ;; Delete geom
           (when (and editing? (#{"LineString" "Polygon"} geom-type))
             [mui/tooltip {:title (tr :map/remove geom-type)}
              [mui/fab
               {:size     "small"
                :on-click #(if (= sub-mode :deleting)
                             (==> [::events/start-editing lipas-id :editing geom-type])
                             (==> [::events/start-editing lipas-id :deleting geom-type]))
                :style    (when (= sub-mode :deleting)
                            {:border (str "5px solid " mui/secondary)})
                :color    "default"}
               [:> js/materialIcons.Eraser]]])

           ;; Split linestring
           (when (and editing? (#{"LineString"} geom-type))
             [mui/tooltip {:title (tr :map/split-route-segment)}
              [mui/fab
               {:size     "small"
                :on-click #(if (= sub-mode :splitting)
                             (==> [::events/start-editing lipas-id :editing geom-type])
                             (==> [::events/start-editing lipas-id :splitting geom-type]))
                :style    (when (= sub-mode :splitting)
                            {:border (str "5px solid " mui/secondary)})
                :color    "default"}
               [:> js/materialIcons.ContentCut]]])]

          (concat
           (lui/edit-actions-list
            {:editing?          editing?
             :editing-allowed?  editing-allowed?
             :valid?            edits-valid?
             :logged-in?        logged-in?
             :user-can-publish? can-publish?
             :on-discard        #(==> [::events/discard-edits lipas-id])
             :discard-tooltip   (tr :actions/discard)
             :on-edit-start     #(==> [::events/edit-site lipas-id geom-type])
             :edit-tooltip      (tr :actions/edit)
             :on-publish        #(==> [::events/save-edits lipas-id])
             :publish-tooltip   (tr :actions/save)
             :invalid-message   (tr :error/invalid-form)
             :on-delete         #(==> [::events/delete-site])
             :delete-tooltip    (tr :lipas.sports-site/delete-tooltip)}))

          (remove nil?)
          (map (fn [tool] [mui/grid {:item true} tool]))))]])))

(defn add-btn [{:keys [tr]}]
  [mui/tooltip {:title (tr :lipas.sports-site/add-new)}
   [mui/fab
    {:color    "secondary"
     :on-click #(==> [::events/start-adding-new-site])}
    [mui/icon "add"]]])

(defn set-new-site-field [& args]
  (==> [::sports-site-events/edit-new-site-field (butlast args) (last args)]))

(defn add-sports-site-view [{:keys [tr]}]
  (r/with-let [selected-tab (r/atom 0)
               geom-tab     (r/atom 0)]
    (let [locale                         (tr)
          {:keys [type data new-site-valid? admins owners cities
                  types types-props size-categories zoomed? geom geom-type
                  active-step sub-mode]} (<== [::subs/add-sports-site-view])

          allowed-types (<== [::sports-site-subs/types-by-geom-type geom-type])
          set-field     set-new-site-field]

      [mui/grid
       {:container true
        :direction "row"
        :style     {:padding "0.5em 1em 0.5em 1em" :flex 1 :height "100%"}}

       [mui/grid {:item true :xs 12 :style {:padding-top "1em" :flex 1}}

        [import-geoms-view
         {:on-import     #(==> [::events/import-selected-geoms-to-new])
          :show-replace? false}]

        [mui/typography {:variant "h6"}
         (tr :lipas.sports-site/new-site {:type type :locale locale})]

        ;; Steps
        [mui/stepper
         {:active-step active-step
          :orientation "vertical"}

         ;; Step 1 - Select type
         [mui/step
          [mui/step-label (tr :actions/select-type)]
          [mui/step-content
           [mui/grid {:item true :xs 12}
            [type-selector-single
             {:value     (when type [(:type-code type)])
              :tr        tr
              :types     types
              :on-change #(==> [::sports-site-events/select-new-site-type %])}]]]]

         ;; Step 2 - Add to map
         [mui/step
          [mui/step-label (tr :map/draw)]
          [mui/step-content {:style {:padding-top "1em"}}

           (let [geom-type (:geometry-type type)
                 type-code (:type-code type)]

             (if-not geom

               ;; Draw new geom
               [mui/grid {:container true :spacing 16}

                ;; Tabs for selecting btw drawing and importing geoms
                (when (#{"LineString"} geom-type)
                  [mui/grid {:item true}

                   [mui/tabs {:value      @geom-tab
                              :on-change  #(reset! geom-tab %2)
                              :style      {:margin-bottom "1em"}
                              :text-color "secondary"}
                    [mui/tab {:label (tr :map/draw-geoms)}]
                    [mui/tab {:label (tr :map.import/tab-header)}]]])

                ;; Draw
                (when (= 0 @geom-tab)
                  [:<>
                   ;; Helper text
                   [mui/grid {:item true :xs 12}
                    [mui/typography {:variant "body2"}
                     (tr :map/center-map-to-site)]]

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

                (when (= 1 @geom-tab)
                  (when (#{"LineString"} geom-type)
                    [:<>
                     ;; Supported formats helper text
                     [mui/grid {:item true :xs 12}
                      [mui/typography {:variant "body2"}
                       (tr :map.import/supported-formats import-formats)]]

                     ;; Open import dialog button
                     [mui/grid {:item true}
                      [mui/button
                       {:color    "secondary"
                        :variant  "contained"
                        :on-click #(==> [::events/toggle-import-dialog])}
                       (tr :map.import/tooltip)]]]))]

               ;; Modify new geom
               [mui/grid {:container true :spacing 8}

                [mui/grid {:item true}
                 (when (not zoomed?)
                   [mui/typography {:variant "body2" :color "error"}
                    (tr :map/zoom-closer)])]

                [mui/grid {:item true}
                 [mui/typography {:variant "body2"}
                  (tr :map/modify geom-type)]
                 [mui/typography {:variant "caption" :style {:margin-top "0.5em"}}
                  (tr :map/edit-later-hint)]]

                ;; Add additional geom button
                (when (#{"LineString" "Polygon"} geom-type)
                  [mui/grid {:item true :xs 8}
                   [mui/button
                    {:on-click #(==> [::events/start-adding-geom geom-type])
                     :variant  "contained"
                     :color    "secondary"}
                    (tr :map/draw geom-type)]])

                ;; Delete geom
                (when (#{"LineString" "Polygon"} geom-type)
                  [mui/grid {:item true :xs 4}
                   [mui/tooltip {:title (tr :map/remove geom-type)}
                    [mui/button
                     {:on-click #(if (= sub-mode :deleting)
                                   (==> [::events/stop-deleting-geom geom-type])
                                   (==> [::events/start-deleting-geom geom-type]))
                      :disabled (-> geom :features empty?)
                      :style    (when (= sub-mode :deleting)
                                  {:outline (str "2px solid " mui/secondary)})
                      :variant  "contained"}
                     [:> js/materialIcons.Eraser]]]])

                ;; Done button
                [mui/grid {:item true :xs 12}
                 [mui/button
                  {:on-click #(==> [::events/finish-adding-geom geom type-code])
                   :variant  "contained"
                   :disabled (-> geom :features empty?)
                   :color    "secondary"}
                  (tr :general/done)]]]))]]

         ;; Step 3 - Fill data
         [mui/step
          [mui/step-label (tr :actions/fill-data)]
          [mui/step-content
           {:style
            {:margin-left  "-24px" ;; Undo Stepper default padding
             :margin-right "-24px"
             :margin-top   "1em"
             :padding      0 }}
           [mui/grid {:container true :style {:flex-direction "column"}}

            ;; Tabs
            [mui/grid {:item true}
             [mui/tool-bar
              [mui/tabs
               {:value     @selected-tab
                :on-change #(reset! selected-tab %2)
                :style     {:margin-bottom "1em"}}
               [mui/tab {:label (tr :lipas.sports-site/basic-data)}]
               [mui/tab {:label (tr :lipas.sports-site/properties)}]]]

            (case @selected-tab

              ;; Basic info tab
              0 [mui/grid {:container true}
                 [mui/grid {:item true :xs 12}

                  [sports-sites/form
                   {:tr              tr
                    :edit-data       data
                    :read-only?      false
                    :types           (vals allowed-types)
                    :size-categories size-categories
                    :admins          admins
                    :owners          owners
                    :on-change       set-field
                    :sub-headings?   true}]

                  [sports-sites/location-form
                   {:tr            tr
                    :read-only?    false
                    :cities        (vals cities)
                    :edit-data     (:location data)
                    :on-change     (partial set-field :location)
                    :sub-headings? true}]]]

              ;; Properties tab
              1 [sports-sites/properties-form
                 {:tr          tr
                  :types-props types-props
                  :read-only?  false
                  :on-change   (partial set-field :properties)
                  :edit-data   (:properties data)
                  :key         type}])]]]]]]

       ;; Actions
       [mui/grid {:item true}
        [sticky-bottom-container
         [mui/grid
          {:container true :align-items "center" :spacing 8
           :style     {:padding-bottom "0.5em"}}

          [address-search-dialog]

          ;; Save
          (when data
            [mui/grid {:item true}
             [lui/save-button
              {:tooltip          (tr :actions/save)
               :disabled-tooltip (tr :actions/fill-required-fields)
               :disabled         (not new-site-valid?)
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
             [:> js/materialIcons.MapSearchOutline]]]]]]]])))

(defn default-tools [{:keys [tr logged-in?]}]
  (let [result-view (<== [:lipas.ui.search.subs/search-results-view])]
    [:<>
     [address-search-dialog]
     [lui/floating-container {:bottom 0 :background "transparent"}
      [mui/grid
       {:container true :align-items "center" :spacing 8
        :style     {:padding-bottom "0.5em"}}

       (when logged-in?
         [mui/grid {:item true}
          [add-btn {:tr tr}]])

       [mui/tooltip {:title (tr :map.address-search/tooltip)}
        [mui/grid {:item true}
         [mui/fab
          {:size "small" :on-click #(==> [::events/toggle-address-search-dialog])}
          [:> js/materialIcons.MapSearchOutline]]]]

       (when (= :list result-view)
         [mui/grid {:item true}
          [reports/dialog {:tr tr :btn-variant :fab}]])]]]))

(defn map-contents-view [{:keys [tr logged-in? width]}]
  (let [adding?       (<== [::sports-site-subs/adding-new-site?])
        result-view   (<== [:lipas.ui.search.subs/search-results-view])
        selected-site (<== [::subs/selected-sports-site])
        view          (cond
                        adding?       :adding
                        selected-site :site
                        :else         :search)]

    [:<>
     ;; Search, filters etc.
     (case view
       :adding [add-sports-site-view {:tr tr}]
       :site   [sports-site-view {:tr tr :site-data selected-site :width width}]
       :search [search/search-view
                {:tr tr
                 :on-result-click
                 (fn [{:keys [lipas-id]}]
                   (==> [::events/show-sports-site lipas-id])
                   (==> [::events/zoom-to-site lipas-id width]))}])

     (when (and (= :list result-view) (#{:search} view))
       [:div {:style {:padding "0.5em"}}
        [default-tools {:tr tr :logged-in? logged-in?}]])]))

(defn map-view [{:keys [width]}]
  (let [tr            (<== [:lipas.ui.subs/translator])
        logged-in?    (<== [:lipas.ui.subs/logged-in?])
        drawer-open?  (<== [::subs/drawer-open?])
        result-view   (<== [:lipas.ui.search.subs/search-results-view])
        selected-site (<== [::subs/selected-sports-site])
        drawer-width  (cond
                        (#{"xs"} width)              "100%"
                        (and (#{"sm" "md"} width)
                             (= :table result-view)) "100%"
                        (and (= :table result-view)
                             (empty? selected-site)) "100%"
                        :else                        "430px")]

    [mui/grid {:container true :style {:height "100%" :width "100%"}}

     ;; Mini-nav
     [lui/floating-container
      {:right 0 :background-color "transparent"}
      [mui/grid
       {:container true :direction "column" :align-items "flex-end" :spacing 24}
       [mui/grid {:item true}
        [nav/mini-nav {:tr tr :logged-in? logged-in?}]]]]

     [mui/mui-theme-provider {:theme mui/jyu-theme-light}

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
         :color    "default"}
        [mui/icon "expand_less"]]

       ;; Content
       [map-contents-view {:tr tr :logged-in? logged-in? :width width}]]]

     ;; Floating container (bottom right)
     [lui/floating-container {:bottom "0.5em" :right "2.75em"}

      [mui/grid {:container true :align-items "center" :spacing 8 :wrap "nowrap"}

       ;; Zoom to users location btn
       [mui/grid {:item true}
        [user-location-btn {:tr tr}]]

       ;; Layer switcher
       [mui/grid {:item true}
        [mui/paper
         {:elevation 1
          :style
          {:background-color "rgba(255,255,255,0.9)"
           :margin           "0.25em" :padding-left "0.5em" :padding-right "0.5em"}}
         [layer-switcher {:tr tr}]]]]]

     ;; We use this div to bind Popper to OpenLayers overlay
     [:div {:id "popup-anchor"}]
     [popup]

     ;; The map
     [ol-map/map-outer]]))

(defn main [tr]
  [:> (mui/with-width* (r/reactify-component map-view))])
