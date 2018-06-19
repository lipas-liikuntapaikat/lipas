(ns lipas.ui.navbar
  (:require [lipas.ui.events :as events]
            [lipas.ui.subs :as subs]
            [lipas.ui.i18n :as i18n]
            [lipas.ui.mui :as mui]
            [lipas.ui.routes :refer [navigate!]]
            [lipas.ui.svg :as svg]
            [re-frame.core :as re-frame]))

(defn logout! []
  (re-frame/dispatch [::events/logout])
  (navigate! "/#/kirjaudu"))

(defn ->avatar [initials]
  [mui/avatar {:style {:font-size "0.65em"
                       :color "#fff"}}
   initials])

(defn avatar []
  (let [initials (re-frame/subscribe [::subs/user-initials])]
    (->avatar @initials)))

(defn create-menu [tr anchor logged-in?]
  (let [close #(re-frame/dispatch [::events/set-menu-anchor nil])]
    [mui/menu {:anchor-el anchor :open true
               :on-close close}

     (when (not logged-in?)
       [mui/menu-item {:on-click (comp close #(navigate! "/#/kirjaudu"))}
        [mui/list-item-icon
         [mui/icon "lock"]]
        [mui/list-item-text {:primary (tr :login/headline)}]])

     (when (not logged-in?)
       [mui/menu-item {:on-click (comp close #(navigate! "/#/rekisteroidy"))}
        [mui/list-item-icon
         [mui/icon "group_add"]]
        [mui/list-item-text {:primary (tr :register/headline)}]])

     (when logged-in?
       [mui/menu-item {:on-click (comp close #(navigate! "/#/profiili"))}
        [mui/list-item-icon
         [mui/icon "account_circle"]]
        [mui/list-item-text {:primary (tr :user/headline)}]])

     [mui/menu-item {:on-click (comp close #(navigate! "/#/ohjeet"))}
      [mui/list-item-icon
       [mui/icon "help"]]
      [mui/list-item-text {:primary (tr :help/headline)}]]

     (when logged-in?
       [mui/menu-item {:on-click (comp close logout!)}
        [mui/list-item-icon
         [mui/icon "exit_to_app"]]
        [mui/list-item-text {:primary (tr :login/logout)}]])]))

(def separator
  [mui/typography {:component "span"
                   :color "secondary"
                   :variant "title"
                   :style {:display "inline"
                           :font-weight "bold"
                           :font-size "1em"
                           :margin "0.5em"}}
   "|"])

(defn set-translator [locale]
  (let [translator (i18n/->tr-fn locale)]
    (re-frame/dispatch [::events/set-translator translator])))

(defn ->lang-btn [locale]
  [mui/button {:style {:min-width "0px"
                       :padding 0
                       :font-size "1em"}
               :on-click #(set-translator locale)}
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

(defn create-drawer [tr logged-in?]
  (let [hide-and-navigate! (comp toggle-drawer navigate!)]
    [mui/swipeable-drawer {:open true
                           :on-open #()
                           :on-close toggle-drawer}
     lang-selector

     [mui/list

      [mui/list-item {:button true
                      :on-click #(hide-and-navigate! "/#/")}
       [mui/list-item-icon
        [mui/icon "home"]]
       [mui/list-item-text {:primary (tr :menu/frontpage)}]]

      [mui/divider]

      [mui/list-item {:button true
                      :on-click #(hide-and-navigate! "/#/liikuntapaikat")}
       [mui/list-item-icon
        [mui/icon "place"]]
       [mui/list-item-text {:primary (tr :sport/headline)}]]

      [mui/list-item {:button true
                      :on-click #(hide-and-navigate! "/#/jaahalliportaali")}
       [mui/list-item-icon
        [mui/icon "ac_unit"]]
       [mui/list-item-text {:primary (tr :ice/headline)}]]

      [mui/list-item {:button true
                      :on-click #(hide-and-navigate! "/#/uimahalliportaali")}
       [mui/list-item-icon
        [mui/icon "pool"]]
       [mui/list-item-text {:primary (tr :swim/headline)}]]
      [mui/divider]

      [mui/list-item {:button true
                      :on-click #(hide-and-navigate! "/#/avoin-data")}
       [mui/list-item-icon
        [mui/icon "build"]]
       [mui/list-item-text {:primary (tr :open-data/headline)}]]

      [mui/list-item {:button true
                      :on-click #(hide-and-navigate! "/#/ohjeet")}
       [mui/list-item-icon
        [mui/icon "help"]]
       [mui/list-item-text {:primary (tr :help/headline)}]]
      [mui/divider]

      (when logged-in?
        [mui/list-item {:button true
                        :on-click #(hide-and-navigate! "/#/profiili")}
         [mui/list-item-icon
          [mui/icon "account_circle"]]
         [mui/list-item-text {:primary (tr :user/headline)}]])

      (when logged-in?
        [mui/list-item {:button true
                        :on-click logout!}
         [mui/list-item-icon
          [mui/icon "exit_to_app"]]
         [mui/list-item-text {:primary (tr :login/logout)}]])

      (when (not logged-in?)
        [mui/list-item {:button true
                        :on-click #(hide-and-navigate! "/#/kirjaudu")}
         [mui/list-item-icon
          [mui/icon "lock"]]
         [mui/list-item-text {:primary (tr :login/headline)}]])

      (when (not logged-in?)
        [mui/list-item {:button true
                        :on-click #(hide-and-navigate! "/#/rekisteroidy")}
         [mui/list-item-icon
          [mui/icon "group_add"]]
         [mui/list-item-text {:primary (tr :register/headline)}]])]]))

(defn nav [tr menu-anchor drawer-open? active-panel logged-in?]
  [mui/app-bar {:position "static"
                :color "primary"
                :style {:border-box "1px solid black"}}
   [mui/tool-bar {:disable-gutters true}
    (when menu-anchor
      (create-menu tr menu-anchor logged-in?))
    (when drawer-open?
      (create-drawer tr logged-in?))
    [:a {:href "/#/"}
     [mui/svg-icon {:view-box "0 0 132.54 301.95"
                    :style {:height "2em"
                            :margin "0.45em"}}
      svg/jyu-logo]]
    [mui/typography {:variant "title"
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
     [mui/typography {:component "a"
                      :variant "title"
                      :href "/#/"
                      :style {:display "inline"
                              :font-weight "bold"
                              :font-size "1em"
                              :text-decoration "none"}}
      (tr :menu/headline)]
     separator
     (case active-panel
       :home-panel (tr :home-page/headline)
       :sports-panel (tr :sport/headline)
       :ice-panel (tr :ice/headline)
       :swim-panel (tr :swim/headline)
       :open-data-panel (tr :open-data/headline)
       :help-panel (tr :help/headline)
       :login-panel ""
       :register-panel ""
       :user-panel (tr :user/headline)
       "")]
    [mui/hidden {:sm-down true}
     lang-selector]
    [mui/icon-button
     [mui/icon "search"]]
    ;;[mui/text-field {:placeholder "Haku"}]
    [mui/icon-button {:on-click show-menu}
     (if logged-in?
       [avatar]
       [mui/icon "account_circle"])]
    [mui/icon-button {:on-click toggle-drawer}
     [mui/icon {:color "secondary"} "menu"]]]])
