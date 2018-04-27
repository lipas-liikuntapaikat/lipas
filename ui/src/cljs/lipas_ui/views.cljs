(ns lipas-ui.views
  (:require [lipas-ui.events :as events]
            [lipas-ui.front-page.views :as front-page]
            [lipas-ui.help.views :as help]
            [lipas-ui.i18n :as i18n]
            [lipas-ui.ice-stadiums.views :as ice-stadiums]
            [lipas-ui.mui :as mui]
            [lipas-ui.mui-icons :as mui-icons]
            [lipas-ui.open-data.views :as open-data]
            [lipas-ui.routes :refer [navigate!]]
            [lipas-ui.sports-places.views :as sports-places]
            [lipas-ui.subs :as subs]
            [lipas-ui.svg :as svg]
            [lipas-ui.swimming-pools.views :as swimming-pools]
            [lipas-ui.user.views :as user]
            [re-frame.core :as re-frame]))

;; Menu

(defn create-menu [tr anchor]
  (let [close #(re-frame/dispatch [::events/set-menu-anchor nil])]
    [mui/menu {:anchor-el anchor :open true
               :on-close close}
     [mui/menu-item
      [mui/list-item-icon
       [mui-icons/lock]]
      [mui/list-item-text {:primary (tr :menu/login)}]]
     [mui/menu-item
      [mui/list-item-icon
       [mui-icons/group-add]]
      [mui/list-item-text {:primary (tr :menu/register)
                           :on-click (comp close #(navigate! "/#/profiili"))}]]
     [mui/menu-item
      [mui/list-item-icon
       [mui-icons/help]]
      [mui/list-item-text {:primary (tr :help/headline)
                           :on-click (comp close #(navigate! "/#/ohjeet"))}]]]))

(def separator
  [mui/typography {:component "span"
                   :color "secondary"
                   :variant "title"
                   :style {:display "inline"
                           :font-weight "bold"
                           :font-size "1em"
                           :margin "0.5em"}}
   "|"])

(defn ->lang-btn [locale]
  [mui/button {:style {:min-width "0px"
                       :padding 0
                       :font-size "1em"}
               :on-click #(re-frame/dispatch [::events/set-locale locale])}
   (name locale)])

(def lang-selector
  [mui/grid {:item true :style {:margin "1em"}}
   [->lang-btn :fi]
   separator
   [->lang-btn :sv]
   separator
   [->lang-btn :en]])

(defn show-menu [event]
  (re-frame/dispatch [::events/set-menu-anchor (.-currentTarget event)]))

(defn toggle-drawer [_]
  (re-frame/dispatch [::events/toggle-drawer]))

(defn create-drawer [tr]
  (let [hide-and-navigate! (comp toggle-drawer navigate!)]
    [mui/swipeable-drawer {:open true
                           :on-open #()
                           :on-close toggle-drawer
                           :Paper-props {}}
     lang-selector
     [mui/list
      [mui/list-item {:button true
                      :on-click #(hide-and-navigate! "/#/liikuntapaikat")}
       [mui/list-item-icon
        [mui-icons/place]]
       [mui/list-item-text {:primary (tr :sport/headline)}]]
      [mui/list-item {:button true
                      :on-click #(hide-and-navigate! "/#/jaahalliportaali")}
       [mui/list-item-icon
        [mui-icons/ac-unit]]
       [mui/list-item-text {:primary (tr :ice/headline)}]]
      [mui/list-item {:button true
                      :on-click #(hide-and-navigate! "/#/uimahalliportaali")}
       [mui/list-item-icon
        [mui-icons/pool]]
       [mui/list-item-text {:primary (tr :swim/headline)}]]
      [mui/divider]
      [mui/list-item {:button true
                      :on-click #(hide-and-navigate! "/#/avoin-data")}
       [mui/list-item-icon
        [mui-icons/build]]
       [mui/list-item-text {:primary (tr :open-data/headline)}]]
      [mui/list-item {:button true
                      :on-click #(hide-and-navigate! "/#/ohjeet")}
       [mui/list-item-icon
        [mui-icons/help]]
       [mui/list-item-text {:primary (tr :help/headline)}]]]]))

;; Nav

(defn nav [tr menu-anchor drawer-open? active-panel]
  [mui/app-bar {:position "static"
                :color "primary"
                :style {:border-box "1px solid black"}}
   [mui/tool-bar {:disable-gutters true}
    (when menu-anchor
      (create-menu tr menu-anchor))
    (when drawer-open?
      (create-drawer tr))
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
       (tr :menu/jyu)]
      separator]
     (let [prefix "Lipas"]
       (str prefix " " (case active-panel
                         :sports-panel (tr :sport/headline :lower-case)
                         :ice-panel (tr :ice/headline :lower-case)
                         :swim-panel (tr :swim/headline :lower-case)
                         :open-data-panel (tr :open-data/headline :lower-case)
                         :help-panel (tr :help/headline :lower-case)
                         :user-panel (tr :user/headline :lower-case)
                         "")))]
    [mui/hidden {:sm-down true}
     lang-selector]
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
    :user-panel [user/main]
    [:div "Unknown page :/"]))

(defn show-panel [panel-name]
  [panels panel-name])

(defn main-panel []
  (let [active-panel (re-frame/subscribe [::subs/active-panel])
        menu-anchor (re-frame/subscribe [::subs/menu-anchor])
        drawer-open? (re-frame/subscribe [::subs/drawer-open?])
        tr (i18n/->tr-fn @(re-frame/subscribe [::subs/locale]))]
    [mui/css-baseline
     [mui/mui-theme-provider {:theme mui/jyu-theme-dark}
      [nav tr @menu-anchor @drawer-open? @active-panel]]
     [mui/mui-theme-provider {:theme mui/jyu-theme-light}
      [show-panel @active-panel]]]))
