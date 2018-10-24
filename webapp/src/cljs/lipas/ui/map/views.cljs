(ns lipas.ui.map.views
  (:require [lipas.ui.components :as lui]
            [lipas.ui.map.events :as events]
            [lipas.ui.map.map :as ol-map]
            [lipas.ui.map.subs :as subs]
            [lipas.ui.mui :as mui]
            [lipas.ui.navbar :as nav]
            [lipas.ui.sports-sites.views :as sports-sites]
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

(defn sports-site-info []
  (let [site     (:display-data (<== [::subs/selected-sports-site]))
        lipas-id (:lipas-id site)
        portal   (case (-> site :type :type-code)
                   (3110 3130) "uimahalliportaali"
                   (2510 2520) "jaahalliportaali"
                   "")]
    (when site
      [:div {:style {:padding-top "0.5em"}}
       [mui/typography {:variant :headline}
        (:name site)]

       ;; [mui/typography {:variant :body2}
       ;;  (-> site :construction-year)]

       ;; [mui/typography {:variant :body2}
       ;;  (-> site :type :name)]

       [mui/typography {:variant :body2}
        (-> site :location :address)]
       [mui/typography {:variant :body2}
        (-> site :location :postal-code)]
       [mui/typography {:variant :body2}
        (-> site :location :city :name)]
       [mui/button {:href     (str "/#/" portal "/hallit/" lipas-id)}
        [mui/icon "arrow_right"] "Kaikki tiedot"]])))

(defn sports-site-info2 [{:keys [tr site-data]}]
  (r/with-let [selected-tab (r/atom 0)
               site         (:display-data site-data)
               lipas-id     (:lipas-id site)]

    [mui/grid {:container true
               :style     {:flex-direction "column"}}
     [mui/grid {:item true}

      ;; Headline
      [mui/grid {:container true}
       [mui/grid {:item  true
                  :style {:margin-top "0.5em"
                          :flex-grow  "1"}}
        [mui/typography {:variant :headline}
         (:name site)]]
       [mui/grid {:item true}
        [mui/icon-button {:on-click #(==> [::events/show-sports-site nil])
                          :style {:margin-right "-16px"}}
         [mui/icon "close"]]]]]

     ;; Tabs
     [mui/grid {:item true}
      [mui/tabs {:value     @selected-tab
                 :on-change #(reset! selected-tab %2)
                 :style     {:margin-bottom "1em"}}
       [mui/tab {:label "Yleiset"}]
       [mui/tab {:label "Osoite"}]
       [mui/tab {:label "Lisätiedot"}]]
      (case @selected-tab

        ;; Basic info
        0 [sports-sites/form
           (merge
            {:tr         tr
             :read-only? true}
            site-data)]

        ;; Location
        1 [sports-sites/location-form
           (merge
            {:tr         tr
             :read-only? true}
            {:display-data (-> site-data :display-data :location)
             :edit-data    (-> site-data :edit-data :location)})]

        ;; Properties
        2 [mui/typography "Ei mitään vielä"]
        )]]))

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
     [floating-container {:left 0
                          :top  0}
      [mui/grid {:container true
                 :direction :column
                 :style     {:min-width "400px"}}

       [mui/grid {:item true}
        (if selected-site
          [sports-site-info2 {:tr tr :site-data selected-site}]
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
