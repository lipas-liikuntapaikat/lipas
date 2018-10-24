(ns lipas.ui.map.views
  (:require [lipas.ui.components :as lui]
            [lipas.ui.map.events :as events]
            [lipas.ui.map.map :as ol-map]
            [lipas.ui.map.subs :as subs]
            [lipas.ui.mui :as mui]
            [lipas.ui.navbar :as nav]
            [lipas.ui.sports-sites.events :as sports-site-events]
            [lipas.ui.sports-sites.subs :as sports-site-subs]
            [lipas.ui.sports-sites.views :as sports-sites]
            [lipas.ui.ice-stadiums.subs :as ice-stadiums-subs]
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

(defn filters []
  (let [filters (<== [::subs/filters])
        toggle  #(==> [::events/toggle-filter %])]
    [mui/grid {:container true}
     [mui/grid {:item true}
      [mui/typography {:variant :headline
                       :style   {:margin-top "0.5em"}}
       "LIPAS"]
      [lui/checkbox
       {:value     (-> filters :ice-stadium)
        :label     "Jäähallit"
        :on-change #(toggle :ice-stadium)}]
      [lui/checkbox
       {:value     (-> filters :swimming-pool)
        :label     "Uimahallit"
        :on-change #(toggle :swimming-pool)}]]]))

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

(defn sports-site-view [{:keys [tr site-data]}]
  (r/with-let [selected-tab (r/atom 0)]
    (let [display-data (:display-data site-data)
          lipas-id     (:lipas-id display-data)
          edit-data    (:edit-data site-data)
          types        (<== [::sports-site-subs/types-list])
          cities       (<== [::sports-site-subs/cities-list])
          admins       (<== [::sports-site-subs/admins])
          owners       (<== [::sports-site-subs/owners])
          editing?     (<== [::sports-site-subs/editing? lipas-id])
          edits-valid? (<== [::sports-site-subs/edits-valid? lipas-id])
          can-publish? (<== [::user-subs/permission-to-publish? lipas-id])
          logged-in?   (<== [::user-subs/logged-in?])

          set-field       (partial set-field lipas-id)

          size-categories (<== [::ice-stadiums-subs/size-categories])

          portal (case (-> display-data :type :type-code)
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
          [mui/icon-button {:on-click #(==> [::events/show-sports-site nil])
                            }
           [mui/icon "close"]]]]]

       ;; Tabs
       [mui/grid {:item true}
        [mui/tabs {:value     @selected-tab
                   :on-change #(reset! selected-tab %2)
                   :style     {:margin-bottom "1em"}}
         [mui/tab {:label "Perustiedot"}]
         [mui/tab {:label "Lisätiedot"}]
         [mui/tab {:label "Osoite"}]]

        (case @selected-tab

          ;; Basic info
          0 [sports-sites/form
             {:tr              tr
              :display-data    display-data
              :edit-data       edit-data
              :read-only?      (not editing?)
              :types           types
              :size-categories size-categories
              :admins          admins
              :owners          owners
              :on-change       set-field}]

          ;; Properties
          1 (if portal
              [mui/button {:href (str "/#/" portal "/hallit/" lipas-id)}
               [mui/icon "arrow_right"] (str "Kaikki tiedot " portal "ssa")]
              [mui/typography "Ei mitään vielä"])

          ;; Location
          2 [sports-sites/location-form
             {:tr           tr
              :read-only?   (not editing?)
              :cities       cities
              :edit-data    (:location edit-data)
              :display-data (:location display-data)
              :on-change    (partial set-field :location)}])]

       [mui/grid {:container true
                  :justify   "flex-end"}
        (into
         [mui/grid {:item  true
                    :style {:padding-top    "1em"
                            :padding-bottom "1em"}}]
         (interpose
          [:span {:style {:margin-left  "0.25em"
                          :margin-right "0.25em"}}]
          (lui/edit-actions-list
           {:editing?           editing?
            :valid?             edits-valid?
            :logged-in?         logged-in?
            :user-can-publish?  can-publish?
            :on-discard         #(==> [:lipas.ui.events/confirm
                                       (tr :confirm/discard-changes?)
                                       (fn []
                                         (==> [::sports-site-events/discard-edits lipas-id]))])
            :discard-tooltip    (tr :actions/discard)
            :on-edit-start      #(==> [::sports-site-events/edit-site lipas-id])
            :edit-tooltip       (tr :actions/edit)
            :on-save-draft      #(==> [::sports-site-events/save-draft lipas-id])
            :save-draft-tooltip (tr :actions/save-draft)
            :on-publish         #(==> [::sports-site-events/save-edits lipas-id])
            :publish-tooltip    (tr :actions/save)
            :invalid-message    (tr :error/invalid-form)})))]
       ])))

(defn add-btn []
  [mui/button {:variant  :fab
               :color    :secondary
               :on-click #(js/alert "Trala")}
   [mui/icon "add"]])

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
        [nav/mini-nav {:tr tr :logged-in? logged-in?}]]
       [mui/grid {:item true}
        [layer-switcher]]]]

     ;; Left sidebar
     [floating-container {:style {:max-height "100%"
                                  :overflow-y "scroll"}}
      [mui/grid {:container true
                 :direction :column
                 :style     {:max-width      "100%"
                             :min-width      "300px"
                             :padding-bottom "0.5em"}}

       [mui/grid {:item true}
        (if selected-site
          [sports-site-view {:tr tr :site-data selected-site}]
          [filters])]]]

     ;; Add button
     [floating-container {:bottom 0 :right 0 :elevation 0
                          :style
                          {:background-color "transparent"
                           :padding-right    0}}
      ;;[add-btn]
      ]

     ;; Popup anchor
     [:div {:id "popup-anchor" :display :none}]
     [popup]

     ;; The map
     [ol-map/map-outer]]))

(defn main [tr]
  [map-view {:tr tr}])
