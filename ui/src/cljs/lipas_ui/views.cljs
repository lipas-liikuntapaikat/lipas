(ns lipas-ui.views
  (:require [re-frame.core :as re-frame]
            [lipas-ui.subs :as subs]
            [lipas-ui.events :as events]
            [lipas-ui.mui :as mui]
            [lipas-ui.mui-icons :as mui-icons]
            [lipas-ui.svg :as svg]
            [reagent.core :as r]))

(defn navigate! [path]
  (set! (.-location js/window) path))

;; Menu

(defn create-menu [anchor]
  [mui/menu {:anchor-el anchor :open true
             :on-close #(re-frame/dispatch [::events/set-menu-anchor nil])}
   [mui/menu-item
    [mui/list-item-icon
     [mui-icons/lock]]
    [mui/list-item-text {:primary "Kirjaudu"}]]
   [mui/menu-item
    [mui/list-item-icon
     [mui-icons/group-add]]
    [mui/list-item-text {:primary "Rekisteröidy"}]]
   [mui/menu-item
    [mui/list-item-icon
     [mui-icons/help]]
    [mui/list-item-text {:primary "Ohjeet"}]]])

(defn show-menu [event]
  (re-frame/dispatch [::events/set-menu-anchor (.-currentTarget event)]))

(defn toggle-drawer [_]
  (re-frame/dispatch [::events/toggle-drawer]))

(defn create-drawer []
  (let [hide-and-navigate! (comp toggle-drawer navigate!)]
       [mui/swipeable-drawer {:open true
                              :on-open #(println "Drawer opened")
                              :on-close toggle-drawer}
        [mui/list
         [mui/list-item {:button true
                         :on-click #(hide-and-navigate! "/#/liikuntapaikat")}
          [mui/list-item-icon
           [mui-icons/place]]
          [mui/list-item-text {:primary "Liikuntapaikat"}]]
         [mui/list-item {:button true
                         :on-click #(hide-and-navigate! "/#/jaahalliportaali")}
          [mui/list-item-icon
           [mui-icons/ac-unit]]
          [mui/list-item-text {:primary "Jäähalliportaali"}]]
         [mui/list-item {:button true
                         :on-click #(hide-and-navigate! "/#/uimahalliportaali")}
          [mui/list-item-icon
           [mui-icons/pool]]
          [mui/list-item-text {:primary "Uimahalliportaali"}]]
         [mui/divider]
         [mui/list-item {:button true
                         :on-click #(hide-and-navigate! "/#/rajapinnat")}
          [mui/list-item-icon
           [mui-icons/build]]
          [mui/list-item-text {:primary "Rajapinnat"}]]
         [mui/list-item {:button true
                         :on-click #(hide-and-navigate! "/#/ohjeet")}
          [mui/list-item-icon
           [mui-icons/help]]
          [mui/list-item-text {:primary "Ohjeet"}]]]]))

;; Nav

(defn nav [menu-anchor drawer-open? active-panel]
  [mui/app-bar {:position "static" :color "primary"
                :style {:border-box "1px solid black"}}
   [mui/tool-bar {:disable-gutters true}
    (when menu-anchor
      (create-menu menu-anchor))
    (when drawer-open?
      (create-drawer))
    [:a {:href "/#/"}
     [mui/svg-icon {:view-box "0 0 132.54 301.95"
                    :style {:height "2em"
                            :margin "0.45em"}}
      svg/jyu-logo]]
    [mui/typography {:variant "title"
                     ;;:on-click #(navigate! "/#/")
                     :style {:flex 1 :font-size "1em" :font-weight "bold"}}
     [mui/typography {:component "a" :variant "title" :href "/#/"
                      :style {:text-decoration "none" :display "inline"
                              :font-weight "bold" :font-size "1em"}}
      "Jyväskylän yliopisto"]
     [:span {:style {:color "#f1563f" :margin "0.5em"}} "|"]
     (let [prefix "Lipas"]
       (str prefix " " (case active-panel
                         :sports-panel "liikuntapaikat"
                         :ice-panel "jäähalliportaali"
                         :swim-panel "uimahalliportaali"
                         :interfaces-panel "rajapinnat"
                         :help-panel "ohjeet"
                         "")))]
    [mui-icons/search]
    ;;[mui/text-field {:placeholder "Haku"}]
    [mui/icon-button {:on-click show-menu}
     [mui-icons/account-circle]]
    [mui/icon-button {:on-click toggle-drawer}
     [mui-icons/menu {:style {:color "#f1563f"}}]]]])

;; home

(defn home-panel []
  [mui/grid {:container true}
   [mui/grid {:item true :xs 12 :md 6 :lg 4}
    [mui/card
     [mui/card-content
      [mui/typography {:variant "headline"} "Liikuntapaikat"]
      [mui/typography "LIPAS on suomalaisten liikuntapaikkojen tietokanta."]
      [mui/button "kissa"]

      ]]]
   [mui/grid {:item true :xs 12 :md 6 :lg 4}
    [mui/card
     [mui/card-content
      [mui/typography {:variant "headline"} "Jäähalliportaali"]
      [mui/typography "Jäähalliportaali sisältää hallien perus- ja energiankulutustietoja, sekä ohjeita energiatehokkuuden parantamiseen."]]]]
   [mui/grid {:item true :xs 12 :md 6 :lg 4}
    [mui/card
     [mui/card-content
      [mui/typography {:variant "headline"} "Uimahalliportaali"]
      [mui/typography "Hieno on myös tämä toinen portaali."]]]
    ]])

;; about

(defn sports-panel []
  [mui/grid {:container true}
   [mui/grid {:item true :xs 12}
    [mui/card
     [mui/card-content
      [mui/typography {:variant "headline"} "Liikuntapaikat"]]]]])

;; ice

(defn change-tab [_ value]
  (re-frame/dispatch [::events/set-active-ice-panel-tab value]))

(defn ice-panel []
  (let [active-tab (re-frame/subscribe [::subs/active-ice-panel-tab])]
    [mui/grid {:container true}
     [mui/grid {:item true :xs 12}
      [mui/card
       [mui/card-content
        ;; [mui/typography {:variant "headline"} "Jäähalliportaali"]
        [mui/tabs {:full-width true
                   :on-change change-tab
                   :value @active-tab}
         [mui/tab {:label "Hallien tiedot"}]
         [mui/tab {:label "Vinkkejä energiatehokkuuteen"}]
         [mui/tab {:label "Ilmoita kulutustiedot"}]]
        (case @active-tab
          0 [mui/grid {:container true}
             [mui/grid {:item true :xs 12}
              [:iframe {:src "https://liikuntaportaalit.sportvenue.net/Jaahalli"
                        :style {:min-height "800px" :width "100%"}}]]]
          1 [mui/grid {:container true}
             [mui/grid {:item true :xs 12}
              [mui/typography "Tänne .pdf dokumentti"]]]
          2 [mui/grid {:container true}
             [mui/grid {:item true :xs 12}
              [mui/typography "Tähän syöttölomake"]]])]]]]))

;; swim

(defn info-tab [url]
  [mui/grid {:container true}
   [mui/grid {:item true :xs 12}
    [:iframe {:src url
              :style {:min-height "800px" :width "100%"}}]]]  )

(defn energy-tab []
  [mui/grid {:container true}
   [mui/grid {:item true :xs 12}
    [mui/typography "Tänne .pdf dokumentti"]]])

(defn form-tab []
  [mui/form-control
   [mui/form-label {:component "legend"} "Kulutustiedot"]
   [mui/form-group
    [mui/text-field {:select true
                     :label "Valitse halli"
                     :value "Halli 1"}
     (for [hall ["Halli 1" "Halli 2" "Halli 3"]]
       [mui/menu-item {:key hall :value hall} hall])]
    [mui/text-field {:label "Vuosi"
                     :type "number"
                     :select true
                     :value 2018}
     (for [year (range 2000 2019)]
       [mui/menu-item {:key year :value year} year])]
    [mui/text-field {:label "Sähkö"
                     :type "number"
                     :Input-props
                     {:end-adornment
                      (r/as-element
                       [mui/input-adornment "MWh"])}}]
    [mui/text-field {:label "Lämpö (ostettu)"
                     :type "number"
                     :Input-props
                     {:end-adornment
                      (r/as-element
                       [mui/input-adornment "MWh"])}}]
    [mui/text-field {:label "Vesi"
                     :type "number"
                     :Input-props
                     {:end-adornment
                      (r/as-element
                       [mui/input-adornment "m³"])}}]
    [mui/button {:color "primary" :size "large"} "Tallenna"]
    ]])

(defn portal-panel [{:keys [title url]}]
  (let [active-tab (re-frame/subscribe [::subs/active-ice-panel-tab])
        card-props {:square true}]
    [mui/grid {:container true}
     [mui/grid {:xs 12}
      [mui/card card-props
       [mui/card-content
        [mui/tabs {:scrollable true
                   :full-width false
                   :on-change change-tab
                   :value @active-tab}
         [mui/tab {:label "Hallien tiedot"
                   :icon (r/as-element [mui-icons/info])}]
         [mui/tab {:label "Vinkkejä energiatehokkuuteen"
                   :icon (r/as-element [mui-icons/flash-on])}]
         [mui/tab {:label "Ilmoita kulutustiedot"
                   :icon (r/as-element [mui-icons/add])}]]]]]
     [mui/grid {:item true :xs 12}
      [mui/card card-props
       [mui/card-content
        (case @active-tab
          0 (info-tab url)
          1 (energy-tab)
          2 (form-tab))]]]]))


(defn interfaces-panel []
  [mui/grid {:container true}
   [mui/grid {:item true :xs 12}
    [mui/card
     [mui/card-content
      [mui/typography {:variant "headline"} "Rajapinnat ja avoin data"]
      [mui/typography "Kaikki data on avointa blabalba."]]]]
   [mui/grid {:item true :xs 12 :md 6 :lg 4}
    [mui/card
     [mui/card-content
      [mui/typography {:variant "headline"} "REST"]
      [mui/list
       [mui/list-item {:button true
                       :on-click #(navigate!
                                   "http://lipas.cc.jyu.fi/api/index.html")}
        [mui/list-item-icon
         [mui-icons/build]]
        [mui/list-item-text {:primary "Swagger"}]]
       [mui/list-item {:button true
                       :on-click #(navigate!
                                   "https://github.com/lipas-liikuntapaikat/lipas-api")}
        [mui/list-item-icon
         [mui/svg-icon
          svg/github-icon]]
        [mui/list-item-text {:primary "GitHub"}]]]]]]
   [mui/grid {:item true :xs 12 :md 6 :lg 4}
    [mui/card
     [mui/card-content
      [mui/typography {:variant "headline"} "WMS"]]]]
   [mui/grid {:item true :xs 12 :md 6 :lg 4}
    [mui/card
     [mui/card-content
      [mui/typography {:variant "headline"} "WFS"]]]]])

(defn help-panel []
  [mui/grid {:container true}
   [mui/grid {:item true :xs 12}
    [mui/card
     [mui/card-content
      [mui/typography {:variant "headline"} "Ohjeet"]
      [mui/typography "Tänne tulevat ohjeet"]]]]])

;; main

(defn- panels [panel-name]
  (case panel-name
    :home-panel [home-panel]
    :sports-panel [sports-panel]
    :ice-panel [portal-panel {:title "Jäähalliportaali"
                            :url "https://liikuntaportaalit.sportvenue.net/Jaahalli"}]
    :swim-panel [portal-panel {:title "Uimahalliportaali"
                             :url "https://liikuntaportaalit.sportvenue.net/Uimahalli"}]
    :interfaces-panel [interfaces-panel]
    :help-panel [help-panel]
    [:div "Unknown page"]))

(defn show-panel [panel-name]
  [panels panel-name])

(defn main-panel []
  (let [active-panel (re-frame/subscribe [::subs/active-panel])
        menu-anchor (re-frame/subscribe [::subs/menu-anchor])
        drawer-open? (re-frame/subscribe [::subs/drawer-open?])]
    [mui/css-baseline
     [mui/mui-theme-provider {:theme mui/jyu-theme}
      [:div
       [nav @menu-anchor @drawer-open? @active-panel]
       [show-panel @active-panel]]]]))
