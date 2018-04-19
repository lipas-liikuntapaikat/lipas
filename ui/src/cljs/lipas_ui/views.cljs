(ns lipas-ui.views
  (:require [lipas-ui.events :as events]
            [lipas-ui.mui :as mui]
            [lipas-ui.mui-icons :as mui-icons]
            [lipas-ui.subs :as subs]
            [lipas-ui.svg :as svg]
            [lipas-ui.front-page.views :as front-page]
            [lipas-ui.swimming-pools.views :as swimming-pools]
            [lipas-ui.ice-stadiums.views :as ice-stadiums]
            [lipas-ui.open-data.views :as open-data]
            [lipas-ui.sports-places.views :as sports-places]
            [lipas-ui.help.views :as help]
            [lipas-ui.routes :refer [navigate!]]
            [re-frame.core :as re-frame]))

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
                           :on-close toggle-drawer
                           :Paper-props {}}
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
                      :on-click #(hide-and-navigate! "/#/avoin-data")}
       [mui/list-item-icon
        [mui-icons/build]]
       [mui/list-item-text {:primary "Avoin data"}]]
      [mui/list-item {:button true
                      :on-click #(hide-and-navigate! "/#/ohjeet")}
       [mui/list-item-icon
        [mui-icons/help]]
       [mui/list-item-text {:primary "Ohjeet"}]]]]))

;; Nav

(defn nav [menu-anchor drawer-open? active-panel]
  [mui/app-bar {:position "static"
                :color "primary"
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
                     :style {:flex 1
                             :font-size "1em"
                             :font-weight "bold"}}
     [mui/hidden {:sm-down true}
      [mui/typography {:component "a"
                       :variant "title"
                       :href "/#/"
                       :style {:display "inline"
                               :font-weight "bold"
                               :font-size "1em"
                               :text-decoration "none"}}
       "Jyväskylän yliopisto"]
      [mui/typography {:component "span"
                       :color "secondary"
                       :variant "title"
                       :style {:display "inline"
                               :font-weight "bold"
                               :font-size "1em"
                               :margin "0.5em"}} "|"]]
     (let [prefix "Lipas"]
       (str prefix " " (case active-panel
                         :sports-panel "liikuntapaikat"
                         :ice-panel "jäähalliportaali"
                         :swim-panel "uimahalliportaali"
                         :open-data-panel "avoin data"
                         :help-panel "ohjeet"
                         "")))]
    [mui/icon-button
     [mui-icons/search]]
    ;;[mui/text-field {:placeholder "Haku"}]
    [mui/icon-button {:on-click show-menu}
     [mui-icons/account-circle]]
    [mui/icon-button {:on-click toggle-drawer}
     [mui-icons/menu {:color "secondary"}]]]])

(defn- panels [panel-name]
  (case panel-name
    :home-panel [front-page/main]
    :sports-panel [sports-places/main]
    :ice-panel [ice-stadiums/main]
    :swim-panel [swimming-pools/main]
    :open-data-panel [open-data/main]
    :help-panel [help/main]
    [:div "Unknown page :/"]))

(defn show-panel [panel-name]
  [panels panel-name])

(defn main-panel []
  (let [active-panel (re-frame/subscribe [::subs/active-panel])
        menu-anchor (re-frame/subscribe [::subs/menu-anchor])
        drawer-open? (re-frame/subscribe [::subs/drawer-open?])]
    [mui/css-baseline
     [mui/mui-theme-provider {:theme mui/jyu-theme}
      [nav @menu-anchor @drawer-open? @active-panel]
      [show-panel @active-panel]]]))
