(ns lipas.ui.map.views
  (:require [lipas.ui.components :as lui]
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
            [reagent.core :as r]))

(defn layer-switcher []
  (let [basemaps {:taustakartta "Taustakartta"
                  :maastokartta "Maastokartta"
                  :ortokuva     "Ortokuva"}
        basemap  (<== [::subs/basemap])]
    [lui/select
     {:items     basemaps
      :value     basemap
      :label-fn  second
      :value-fn  first
      :on-change #(==> [::events/select-basemap %])}]))

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
             ;;:background-color mui/gray2
             :top              top
             :right            right
             :bottom           bottom
             :left             left
             :margin           "0.5em"
             :padding-left     "1em"
             :padding-right    "1em"}
            style)}]
   children))

(defn type-selector [{:keys [tr value on-change]}]
  (let [locale (tr)
        types  (<== [::subs/types-list locale])]
    ^{:key value}
    [lui/autocomplete
     {:items     types
      :value     value
      :label     "Etsi..."
      :value-fn  :type-code
      :label-fn  :name
      :on-change on-change}]))

(defn filters [{:keys [tr]}]
  (let [type-codes (<== [::subs/types-filter])]
    [mui/grid {:container true}
     [mui/grid {:item true :xs 12}
      [mui/typography {:variant :caption}
       "Valitse kohteet"]]
     [mui/grid {:item true}
      [type-selector
       {:tr        tr
        :value     type-codes
        :on-change #(==> [::events/show-types %])}]]]))

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
          :label     "Liikuntapaikkatyyppi"
          :value-fn  :type-code
          :label-fn  (comp locale :name)
          :on-change #(reset! selected-type (first %))}]
        (when @selected-type
          [mui/grid {:item true}
           [mui/typography {:style
                            {:margin-top    "1em"
                             :margin-bottom "1em"}}
            (get-in types [@selected-type :description locale])]
           [mui/button {:on-click #(on-change @selected-type)
                        :variant  "contained"
                        :color    "secondary"}
            "OK"]])]])))

(defn popup []
  (let [{:keys [data anchor-el]} (<== [::subs/popup])
        {:keys [name]}  (-> data :features first :properties)]
    [mui/popper {:open      (boolean (seq data))
                 :placement :top-end
                 :anchor-el anchor-el
                 :container anchor-el
                 :modifiers {:offset
                             {:enabled true
                              :offset  "0px,10px"}}}
     [mui/paper {:style {:padding "0.5em"}}
      [mui/typography {:variant :body2}
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
          types        (<== [::sports-site-subs/all-types])
          cities       (<== [::sports-site-subs/cities-list])
          admins       (<== [::sports-site-subs/admins])
          owners       (<== [::sports-site-subs/owners])
          editing?     (<== [::sports-site-subs/editing? lipas-id])
          edits-valid? (<== [::sports-site-subs/edits-valid? lipas-id])
          can-publish? (<== [::user-subs/permission-to-publish? lipas-id])
          logged-in?   (<== [::user-subs/logged-in?])

          type-code (-> display-data :type :type-code)
          geom-type (get-in types [type-code :geometry-type])

          set-field (partial set-field lipas-id)

          size-categories (<== [::ice-stadiums-subs/size-categories])

          portal (case type-code
                   (3110 3130) "uimahalliportaali"
                   (2510 2520) "jaahalliportaali"
                   nil)]

      [mui/grid {:container true
                 :style     {:flex-direction "column"}}
       [mui/grid {:item true}

        ;; Headline
        [mui/grid {:container true}
         [mui/grid {:item  true :xs 11
                    :style {:margin-top "0.5em"}}
          [mui/typography {:variant :headline}
           (:name display-data)]]
         [mui/grid {:item true :xs 1}
          [mui/icon-button {:on-click #(==> [::events/show-sports-site nil])}
           [mui/icon "close"]]]]]

       ;; Tabs
       [mui/grid {:item true}
        [mui/tabs {:value     @selected-tab
                   :on-change #(reset! selected-tab %2)
                   :style     {:margin-bottom "1em"}}
         [mui/tab {:label "Perustiedot"}]
         [mui/tab {:label "Lisätiedot"}]]

        (case @selected-tab

          ;; Basic info tab
          0 [mui/grid {:container true}
             [mui/grid {:item true :xs 12}

              [sports-sites/location-form
               {:tr            tr
                :read-only?    (not editing?)
                :cities        cities
                :edit-data     (:location edit-data)
                :display-data  (:location display-data)
                :on-change     (partial set-field :location)
                :sub-headings? true}]

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
                :sub-headings?   true}]]]

          ;; Properties tab
          1 (if portal
              [mui/button {:href (str "/#/" portal "/hallit/" lipas-id)}
               [mui/icon "arrow_right"] (str "Kaikki tiedot " portal "ssa")]
              [mui/typography "Ei mitään vielä"]))]

       ;; Actions
       [sticky-bottom-container
        (into
         [mui/grid {:item  true
                    :style {:padding-top    "1em"
                            :padding-bottom "1em"}}]
         (interpose
          [:span {:style {:margin-left  "0.25em"
                          :margin-right "0.25em"}}]
          (into [(when (and editing? (#{"LineString" "Polygon"} geom-type))
                   [mui/button
                    {:on-click #(==> [::events/start-editing lipas-id :drawing geom-type])
                     :variant  "extendedFab"
                     :color    "secondary"}
                    "Lisää pötkö"])]
           (lui/edit-actions-list
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
                                      (==> [::events/stop-editing]))
             :save-draft-tooltip (tr :actions/save-draft)
             :on-publish         #(do (==> [::sports-site-events/save-edits lipas-id])
                                      (==> [::events/stop-editing]))
             :publish-tooltip    (tr :actions/save)
             :invalid-message    (tr :error/invalid-form)}))))]])))

(defn add-btn []
  [mui/button {:variant  :fab
               :color    :secondary
               :on-click #(==> [::sports-site-events/start-adding-new-site])}
   [mui/icon "add"]])

(defn set-new-site-field [& args]
  (==> [::sports-site-events/edit-new-site-field (butlast args) (last args)]))

(defn add-site-view [{:keys [tr]}]
  (r/with-let [selected-tab (r/atom 0)]
    (let [locale (tr)
          type   (<== [::sports-site-subs/new-site-type])
          data   (<== [::sports-site-subs/new-site-data])
          types  (<== [::sports-site-subs/types-list])
          cities (<== [::sports-site-subs/cities-list])
          admins (<== [::sports-site-subs/admins])
          owners (<== [::sports-site-subs/owners])

          set-field set-new-site-field

          new-site-valid? (<== [::sports-site-subs/new-site-valid?])
          can-publish?    (and
                           new-site-valid?
                           (<== [::user-subs/permission-to-publish-site? data]))

          size-categories (<== [::ice-stadiums-subs/size-categories])

          zoomed? (<== [::subs/zoomed-for-drawing?])
          geom    (<== [::subs/new-geom])

          active-step (cond
                        (some? data) 2
                        (some? type) 1
                        :else        0)]

      [mui/grid {:container true}
       [mui/grid {:item  true :xs 12
                  :style {:padding-top "1em"}}
        [mui/typography {:variant :title}
         (if type
           (str "Uusi " (-> type :name locale))
           "Uusi liikuntapaikka")]
        [mui/stepper
         {:active-step active-step
          :orientation "vertical"}

         ;; Select type
         [mui/step
          [mui/step-label "Valitse tyyppi"]
          [mui/step-content
           [mui/grid {:item true :xs 12}
            [type-selector-single
             {:value     (when type [(:type-code type)])
              :tr        tr
              :on-change (fn [t]
                           (==> [::sports-site-events/select-new-site-type t]))}]]]]

         ;; Add to map
         [mui/step
          [mui/step-label "Lisää kartalle"]
          [mui/step-content {:style {:padding-top "1em"}}
           [mui/typography {:variant :body2}
            "Kohdista kartta liikuntapaikkaan"]
           (when (not zoomed?)
             [mui/typography {:variant :body2
                              :color   :error}
              "Zoomaa lähemmäs"])

           (let [geom-type (:geometry-type type)
                 type-code (:type-code type)]
             (if geom
               [mui/grid {:container true :spacing 8}
                (when (#{"LineString" "Polygon"} geom-type)
                  [mui/grid {:item true}
                   [mui/button
                    {:on-click #(==> [::events/start-adding-geom geom-type])
                     :variant  "contained"
                     :color    "secondary"}
                    "Lisää pötkö"]])
                [mui/grid {:item true}
                 [mui/button
                  {:on-click #(==> [::events/finish-adding-geom geom type-code])
                   :variant  "contained"
                   :color    "secondary"}
                  "Valmis"]]]
               [mui/button
                {:style    {:margin-top "1em"}
                 :disabled (not zoomed?)
                 :color    :secondary
                 :variant  :contained
                 :on-click #(==> [::events/start-adding-geom geom-type])}
                [mui/icon "add_location"]
                "Lisää kartalle"]))]]

         ;; Fill data
         [mui/step
          [mui/step-label "Täytä tiedot"]
          [mui/step-content {:style {:margin-top  "1em"
                                     :padding     0
                                     :margin-left "-24px"}}
           [mui/grid {:container true
                      :style     {:flex-direction "column"}}

            ;; Tabs
            [mui/grid {:item true}
             [mui/tabs {:value     @selected-tab
                        :on-change #(reset! selected-tab %2)
                        :style     {:margin-bottom "1em"}}
              [mui/tab {:label "Perustiedot"}]
              [mui/tab {:label "Lisätiedot"}]]

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
                    :types           types
                    :size-categories size-categories
                    :admins          admins
                    :owners          owners
                    :on-change       set-field
                    :sub-headings?   true}]]]

              ;; Properties tab
              1 [mui/typography "Ei mitään vielä"])]]]]]

        ;; Actions
        [sticky-bottom-container
         [mui/grid {:item true}
          [lui/discard-button
           {:on-click #(==> [:lipas.ui.events/confirm
                             (tr :confirm/discard-changes?)
                             (fn []
                               (==> [::sports-site-events/discard-new-site])
                               (==> [::events/discard-drawing]))])
            :tooltip  (tr :actions/discard)
            :variant  "fab"
            :mini     true}]
          (when data
            (let [draft? (not can-publish?)]
              [lui/save-button
               {:style            {:margin-top    "1em"
                                   :margin-right  "0em"
                                   :margin-bottom "1em"
                                   :margin-left   "1em"}
                :tooltip          (tr :actions/save)
                :disabled-tooltip "Täytä pakolliset kentät"
                :variant          "extendedFab"
                :disabled         (not new-site-valid?)
                :on-click         #(==> [::sports-site-events/commit-rev data draft?])}]))]]]])))

(defn map-contents-view [{:keys [tr logged-in?]}]
  (let [adding? (<== [::sports-site-subs/adding-new-site?])]
    [mui/grid {:container true}

     [mui/grid {:item true :xs 12}
      (when-not adding?
        [mui/grid {:item true :xs 12}
         [mui/typography {:style   {:margin-top    "1em"
                                    :margin-bottom "1em"}
                          :variant :title}
          "LIPAS"]
         [filters {:tr tr}]])

      (when logged-in?
        (if adding?
          [add-site-view {:tr tr}]
          [sticky-bottom-container
           [mui/grid {:item true}
            [add-btn]]]))]]))

(defn map-view [{:keys [tr]}]
  (let [logged-in?    (<== [:lipas.ui.subs/logged-in?])
        selected-site (<== [::subs/selected-sports-site])]
    [mui/grid {:container true
               :style     {:flex-direction "column"
                           :flex           "1 0 auto"}}

     ;; Mini-nav
     [floating-container {:right     0
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

     ;; Left sidebar
     [floating-container {:style {:max-height "100%"
                                  :overflow-y "scroll"}}
      [mui/grid {:container true
                 :direction :column
                 :style     {:max-width      "100%"
                             :min-width      "350px"
                             :padding-bottom "0.5em"}}

       [mui/grid {:item true}
        (if selected-site
          [sports-site-view {:tr tr :site-data selected-site}]
          [map-contents-view {:tr tr :logged-in? logged-in?}])]]]

     ;; Layer switcher (bottom right)
     [floating-container {:bottom "0.5em" :right "3em" :elevation 0
                          :style
                          {:background-color "rgba(255,255,255,0.9)"
                           :z-index          888
                           :margin           "0.5em"
                           :padding-right    0
                           :padding-left     0}}
      [layer-switcher]]

     ;; Popup anchor
     [:div {:id "popup-anchor" :display :none}]
     [popup]

     ;; The map
     [ol-map/map-outer]]))

(defn main [tr]
  [map-view {:tr tr}])
