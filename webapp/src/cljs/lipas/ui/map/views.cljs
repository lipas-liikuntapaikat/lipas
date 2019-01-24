(ns lipas.ui.map.views
  (:require
   [lipas.ui.components :as lui]
   [lipas.ui.ice-stadiums.subs :as ice-stadiums-subs]
   [lipas.ui.map.events :as events]
   [lipas.ui.map.map :as ol-map]
   [lipas.ui.map.subs :as subs]
   [lipas.ui.mui :as mui]
   [lipas.ui.navbar :as nav]
   [lipas.ui.sports-sites.events :as sports-site-events]
   [lipas.ui.sports-sites.subs :as sports-site-subs]
   [lipas.ui.sports-sites.views :as sports-sites]
   [lipas.ui.user.subs :as user-subs]
   [lipas.ui.utils :refer [<== ==>] :as utils]
   [lipas.ui.search.views :as search]
   [reagent.core :as r]))

(defn floating-container [{:keys [top right bottom left style elevation]
                           :or   {elevation 2}}
                          & children]
  (into
   [mui/paper
    {:elevation elevation
     :style
     (merge {:position         :fixed
             :z-index          999
             :background-color "#fff"
             :top              top
             :right            right
             :bottom           bottom
             :left             left
             :margin           "0.5em"
             :padding-left     "1em"
             :padding-right    "1em"}
            style)}]
   children))

(defn layer-switcher [{:keys [:tr tr]}]
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

(defn type-selector-single [{:keys [tr value on-change]}]
  (r/with-let [selected-type (r/atom value)]
    (let [locale (tr)
          types  (<== [::sports-site-subs/all-types locale])]
      [mui/grid {:container true}
       [mui/grid {:item true}
        [lui/autocomplete
         {:multi?    false
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
    [mui/popper {:open      (boolean (seq data))
                 :placement :top-end
                 :anchor-el anchor-el
                 :container anchor-el
                 :modifiers {:offset
                             {:enabled true
                              :offset  "0px,10px"}}}
     [mui/paper {:style {:padding "0.5em"}}
      [mui/typography {:variant "body2"}
       name]]]))

(defn set-field
  [lipas-id & args]
  (==> [::sports-site-events/edit-field lipas-id (butlast args) (last args)]))

(defn- sticky-bottom-container [& children]
  (into
   [mui/grid
    (merge
     {:container true
      :justify   "flex-end"}
     (cond
       (utils/supports-sticky?)        {:style {:position "sticky"
                                                :bottom   0}}
       (utils/supports-webkit-sticky?) {:style {:position "-webkit-sticky"
                                                :bottom   0}}
       :else                           nil))]
   children))

;; Works as both display and edit views
(defn sports-site-view [{:keys [tr site-data]}]
  (r/with-let [selected-tab (r/atom 0)]
    (let [display-data (:display-data site-data)
          lipas-id     (:lipas-id display-data)
          edit-data    (:edit-data site-data)
          cities       (<== [::sports-site-subs/cities-list])
          admins       (<== [::sports-site-subs/admins])
          owners       (<== [::sports-site-subs/owners])
          editing?     (<== [::sports-site-subs/editing? lipas-id])
          edits-valid? (<== [::sports-site-subs/edits-valid? lipas-id])
          can-publish? (<== [::user-subs/permission-to-publish? lipas-id])
          logged-in?   (<== [::user-subs/logged-in?])

          mode (<== [::subs/mode])

          sub-mode (-> mode :sub-mode)

          type-code (-> display-data :type :type-code)
          type      (<== [::sports-site-subs/type-by-type-code type-code])
          geom-type (:geometry-type type)

          allowed-types (<== [::sports-site-subs/types-by-geom-type geom-type])

          types-props (<== [::sports-site-subs/types-props type-code])

          set-field (partial set-field lipas-id)

          size-categories (<== [::ice-stadiums-subs/size-categories])

          portal (case type-code
                   (3110 3130) "uimahalliportaali"
                   (2510 2520) "jaahalliportaali"
                   nil)]

      [mui/grid {:container true}
       [mui/grid {:item true :xs 12}

        ;; Headline
        [mui/grid {:container   true
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
         [mui/tabs {:value      @selected-tab
                    :on-change  #(reset! selected-tab %2)
                    :style      {:margin-bottom "1em"}
                    :text-color "secondary"}
          [mui/tab {:label (tr :lipas.sports-site/basic-data)}]
          [mui/tab {:label (tr :lipas.sports-site/properties)}]]]

        (case @selected-tab

          ;; Basic info tab
          0 [mui/grid {:container true}
             [mui/grid {:item true :xs 12}

              ^{:key (str "location-" lipas-id)}
              [sports-sites/location-form
               {:tr            tr
                :read-only?    (not editing?)
                :cities        cities
                :edit-data     (:location edit-data)
                :display-data  (:location display-data)
                :on-change     (partial set-field :location)
                :sub-headings? true}]

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
                :sub-headings?   true}]]]

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
         [mui/grid {:item  true
                    :style {:padding-top    "1em"
                            :padding-bottom "0.5em"}}]
         (interpose
          [:span {:style {:margin-left  "0.25em"
                          :margin-right "0.25em"}}]
          (remove
           nil?
           (into

            ;; Geom tools

            [(when-not editing?
               [mui/tooltip {:title (tr :map/zoom-to-site)}
                [mui/button
                 {:on-click #(==> [::events/zoom-to-site lipas-id])
                  :variant  "fab"
                  :color    "default"}
                 [mui/icon {:color "secondary"}
                  "place"]]])

             ;; Draw hole
             (when (and editing? (#{"Polygon"} geom-type))
               [mui/tooltip {:title (tr :map/draw-hole)}
                [mui/button
                 {:on-click #(if (= sub-mode :drawing-hole)
                               (==> [::events/start-editing lipas-id :editing geom-type])
                               (==> [::events/start-editing lipas-id :drawing-hole geom-type]))
                  :style    (when (= sub-mode :drawing-hole)
                              {:border (str "5px solid " mui/secondary)})
                  :variant  "fab"
                  :color    "default"}
                 [mui/icon "vignette"]]])

             ;; Add new geom
             (when (and editing? (#{"LineString" "Polygon"} geom-type))
               [mui/tooltip {:title (tr :map/draw geom-type)}
                [mui/button
                 {:on-click #(if (= sub-mode :drawing)
                               (==> [::events/start-editing lipas-id :editing geom-type])
                               (==> [::events/start-editing lipas-id :drawing geom-type]))
                  :style    (when (= sub-mode :drawing)
                              {:border (str "5px solid " mui/secondary)})
                  :variant  "fab"
                  :color    "default"}
                 (if (= geom-type "LineString")
                   [mui/icon "timeline"]
                   [mui/icon "change_history"])]])

             ;; Delete geom
             (when (and editing? (#{"LineString" "Polygon"} geom-type))
               [mui/tooltip {:title (tr :map/remove geom-type)}
                [mui/button
                 {:on-click #(if (= sub-mode :deleting)
                               (==> [::events/start-editing lipas-id :editing geom-type])
                               (==> [::events/start-editing lipas-id :deleting geom-type]))
                  :variant  "fab"
                  :style    (when (= sub-mode :deleting)
                              {:border (str "5px solid " mui/secondary)})
                  :color    "default"}
                 [mui/icon "delete"]]])]

            ;; Save and discard buttons
            (lui/edit-actions-list
             ;; TODO refactor (do ..) blocks to dispatch single event
             ;; according to user intention.
             {:editing?           editing?
              :valid?             edits-valid?
              :logged-in?         logged-in?
              :user-can-publish?  can-publish?
              :on-discard         #(==> [:lipas.ui.events/confirm
                                         (tr :confirm/discard-changes?)
                                         (fn []
                                           (==> [::sports-site-events/discard-edits lipas-id])
                                           (==> [::events/stop-editing]))])
              :discard-tooltip    (tr :actions/discard)
              :on-edit-start      #(do (==> [::sports-site-events/edit-site lipas-id])
                                       (==> [::events/zoom-to-site lipas-id])
                                       (==> [::events/start-editing lipas-id :editing geom-type]))
              :edit-tooltip       (tr :actions/edit)
              :on-save-draft      #(do (==> [::sports-site-events/save-draft lipas-id])
                                       (==> [::events/show-sports-site nil])
                                       (==> [::events/stop-editing]))
              :save-draft-tooltip (tr :actions/save-draft)
              :on-publish         #(do (==> [::sports-site-events/save-edits lipas-id])
                                       (==> [::events/show-sports-site nil])
                                       (==> [::events/stop-editing]))
              :publish-tooltip    (tr :actions/save)
              :invalid-message    (tr :error/invalid-form)})))))]])))

(defn add-btn [{:keys [tr]}]
  [mui/tooltip {:title (tr :lipas.sports-site/add-new)}
   [mui/button {:variant  :fab
                :style {:margin-bottom "0.5em"}
                :color    :secondary
                :on-click #(==> [::sports-site-events/start-adding-new-site])}
    [mui/icon "add"]]])

(defn set-new-site-field [& args]
  (==> [::sports-site-events/edit-new-site-field (butlast args) (last args)]))

(defn add-sports-site-view [{:keys [tr]}]
  (r/with-let [selected-tab (r/atom 0)]
    (let [locale (tr)
          type   (<== [::sports-site-subs/new-site-type])
          data   (<== [::sports-site-subs/new-site-data])
          cities (<== [::sports-site-subs/cities-list])
          admins (<== [::sports-site-subs/admins])
          owners (<== [::sports-site-subs/owners])

          type-code   (:type-code type)
          types-props (<== [::sports-site-subs/types-props type-code])

          set-field set-new-site-field

          new-site-valid? (<== [::sports-site-subs/new-site-valid?])
          can-publish?    (and
                           new-site-valid?
                           (<== [::user-subs/permission-to-publish-site? data]))

          size-categories (<== [::ice-stadiums-subs/size-categories])

          zoomed? (<== [::subs/zoomed-for-drawing?])
          geom    (<== [::subs/new-geom])

          geom-type     (:geometry-type type)
          allowed-types (<== [::sports-site-subs/types-by-geom-type geom-type])

          active-step (cond
                        (some? data) 2
                        (some? type) 1
                        :else        0)]

      [mui/grid {:container true
                 :direction "column"
                 :justify   "space-between"
                 :style     {:flex   1
                             :height "100%"}}

       [mui/grid {:item true :xs 12 :style {:padding-top "1em" :flex 1}}
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
              :on-change #(==> [::sports-site-events/select-new-site-type %])}]]]]

         ;; Step 2 -  Add to map
         [mui/step
          [mui/step-label (tr :map/draw)]
          [mui/step-content {:style {:padding-top "1em"}}

           (when (not zoomed?)
             [mui/typography {:variant "body2" :color :error}
              (tr :map/zoom-closer)])

           (let [geom-type (:geometry-type type)
                 type-code (:type-code type)]

             (if-not geom

               ;; Draw new geom
               [mui/grid {:container true}
                [mui/grid {:item true}
                 [mui/typography {:variant "body2"}
                  (tr :map/center-map-to-site)]]
                [mui/grid {:item true}

                 ;; Add initial geom button
                 [mui/button
                  {:style    {:margin-top "1em"}
                   :disabled (not zoomed?)
                   :color    :secondary
                   :variant  :contained
                   :on-click #(==> [::events/start-adding-geom geom-type])}
                  [mui/icon "add_location"]
                  (tr :map/add-to-map)]]]

               ;; Modify new geom
               [mui/grid {:container true :spacing 8}
                [mui/grid {:item true}
                 [mui/typography {:variant "body2"}
                  (tr :map/modify geom-type)]
                 [mui/typography {:variant "caption" :style {:margin-top "0.5em"}}
                  (tr :map/edit-later-hint)]]

                ;; Add additional geom button
                (when (#{"LineString" "Polygon"} geom-type)
                  [mui/grid {:item true :xs 12}
                   [mui/button
                    {:on-click #(==> [::events/start-adding-geom geom-type])
                     :variant  "contained"
                     :color    "secondary"}
                    (tr :map/draw geom-type)]])

                ;; Done button
                [mui/grid {:item true :xs 12}
                 [mui/button
                  {:on-click #(==> [::events/finish-adding-geom geom type-code])
                   :variant  "contained"
                   :color    "secondary"}
                  (tr :general/done)]]]))]]

         ;; Step 3 - Fill data
         [mui/step
          [mui/step-label (tr :actions/fill-data)]
          [mui/step-content {:style {:margin-top  "1em"
                                     :padding     0
                                     :margin-left "-24px"}}
           [mui/grid {:container true
                      :style     {:flex-direction "column"}}

            ;; Tabs
            [mui/grid {:item true}
             [mui/tool-bar
              [mui/tabs {:value     @selected-tab
                         :on-change #(reset! selected-tab %2)
                         :style     {:margin-bottom "1em"}}
               [mui/tab {:label (tr :lipas.sports-site/basic-data)}]
               [mui/tab {:label (tr :lipas.sports-site/properties)}]]]

            (case @selected-tab

              ;; Basic info tab
              0 [mui/grid {:container true}
                 [mui/grid {:item true :xs 12}

                  [sports-sites/location-form
                   {:tr            tr
                    :read-only?    false
                    :cities        cities
                    :edit-data     (:location data)
                    :on-change     (partial set-field :location)
                    :sub-headings? true}]

                  [sports-sites/form
                   {:tr              tr
                    :edit-data       data
                    :read-only?      false
                    :types           (vals allowed-types)
                    :size-categories size-categories
                    :admins          admins
                    :owners          owners
                    :on-change       set-field
                    :sub-headings?   true}]]]

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
         [mui/grid {:item true}

          ;; Discard
          [lui/discard-button
           {:on-click #(==> [:lipas.ui.events/confirm
                             (tr :confirm/discard-changes?)
                             (fn []
                               (==> [::sports-site-events/discard-new-site])
                               (==> [::events/discard-drawing]))])
            :tooltip  (tr :actions/discard)
            :variant  "fab"}]

          ;; Save
          (when data
            (let [draft? (not can-publish?)]
              [lui/save-button
               {:style            {:margin-top    "1em"
                                   :margin-right  "0em"
                                   :margin-bottom "1em"
                                   :margin-left   "1em"}
                :tooltip          (tr :actions/save)
                :disabled-tooltip (tr :actions/fill-required-fields)
                :variant          "extendedFab"
                :disabled         (not new-site-valid?)
                :on-click         #(==> [::sports-site-events/commit-rev data draft?])}]))]]]])))

(defn map-contents-view [{:keys [tr logged-in?]}]
  (let [adding? (<== [::sports-site-subs/adding-new-site?])]
    [mui/grid {:container true
               :style     {:flex   1
                           :height "100%"}
               :direction "column"
               :justify   "space-between"}

     ;; Search, filters etc.
     (when-not adding?
       [search/search-view
        {:tr tr
         :on-result-click #(==> [::events/show-sports-site (:lipas-id %)])}])

     ;; Add new sports-site view or big '+' button
     (when logged-in?
       (if adding?
         [add-sports-site-view {:tr tr}]
         [sticky-bottom-container
          [mui/grid {:item true}
           [add-btn {:tr tr}]]]))]))

(defn map-view [{:keys [width]}]
  (let [tr            (<== [:lipas.ui.subs/translator])
        logged-in?    (<== [:lipas.ui.subs/logged-in?])
        selected-site (<== [::subs/selected-sports-site])
        drawer-open?  (<== [::subs/drawer-open?])
        result-view   (<== [:lipas.ui.search.subs/search-results-view])
        drawer-width  (cond
                        (#{"xs"} width)              "100%"
                        (and (#{"sm" "md"} width)
                             (= :table result-view)) "100%"
                        (and (= :table result-view)
                             (empty? selected-site)) "1200px"
                        :else                        "430px")]
    [mui/grid {:container true
               :style     {:height "100%" :width "100%"}}

     ;; Mini-nav
     [floating-container {:right     0
                          :top       (cond
                                       (#{"xs"} width)              "2em"
                                       (and (#{"sm" "md"} width)
                                            (= :table result-view)) "2em"
                                       :else                        nil)
                          :elevation 0
                          :style     {:background-color "transparent"
                                      :padding-right    0
                                      :padding-top      0}}
      [mui/grid {:container   true
                 :direction   :column
                 :align-items :flex-end
                 :spacing     24}

       [mui/grid {:item true}
        [nav/mini-nav {:tr tr :logged-in? logged-in?}]]]]

     [mui/mui-theme-provider {:theme mui/jyu-theme-light}

      (when-not drawer-open?
        ;; Open Drawer Button
        [mui/grid {:container true
                   :style     {:position "fixed"
                               :left     0
                               :top      0
                               :width    drawer-width
                               :z-index  1200}}
         [mui/grid {:item true :xs 12}
          [mui/paper {:square true}
           [mui/button {:full-width true
                        :on-click   #(==> [::events/toggle-drawer])
                        :variant    "outlined"}
            [mui/icon "expand_more"]]]]])

      ;; Closable left sidebar drawer
      [mui/drawer {:variant    "persistent"
                   :PaperProps {:style {:width drawer-width}}
                   :SlideProps {:direction "down"}
                   :open       drawer-open?}

       ;; Close button
       [mui/button {:on-click #(==> [::events/toggle-drawer])
                    :style    {:margin-bottom "1em"}
                    :variant  "outlined"
                    :color    "default"}
        [mui/icon "expand_less"]]

       ;; Content
       [mui/grid {:container true
                  :direction :column
                  :justify   :space-between
                  :style     {:flex           1
                              :padding-left   "1em"
                              :padding-right  "1em"
                              :padding-bottom "0.5em"}}

        [mui/grid {:item  true
                   :style {:flex 1}}
         (if selected-site
           [sports-site-view {:tr tr :site-data selected-site}]
           [map-contents-view {:tr tr :logged-in? logged-in?}])]]]]

     ;; Layer switcher (bottom right)
     [floating-container {:bottom "0.5em" :right "3em" :elevation 0
                          :style
                          {:background-color "rgba(255,255,255,0.9)"
                           :z-index          888
                           :margin           "0"
                           :padding          "0.25em"
                           :padding-right    0
                           :padding-left     0}}
      [layer-switcher {:tr tr}]]

     ;; We use this div to bind Popper to OpenLayers overlay
     [:div {:id "popup-anchor"}]
     [popup]

     ;; The map
     [ol-map/map-outer]]))

(defn main [tr]
  [:> (mui/with-width* (r/reactify-component map-view))])
