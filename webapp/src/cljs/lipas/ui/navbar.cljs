(ns lipas.ui.navbar
  (:require [lipas.ui.events :as events]
            [lipas.ui.subs :as subs]
            [lipas.ui.mui :as mui]
            [lipas.ui.routes :refer [navigate!]]
            [lipas.ui.svg :as svg]
            [lipas.ui.utils :refer [<== ==>] :as utils]))

(def links
  {:help "https://www.jyu.fi/sport/fi/yhteistyo/lipas-liikuntapaikat.fi"})

(defn logout! []
  (==> [:lipas.ui.login.events/logout]))

(defn avatar []
  (let [initials (<== [::subs/user-initials])]
    [mui/avatar {:style {:font-size "0.65em"
                         :color     "#fff"}}
     initials]))

(defn account-menu-button [{:keys [tr logged-in?]}]
  [mui/icon-button
   {:on-click   #(==> [::events/show-account-menu (.-currentTarget %)])
    :id         "account-btn"
    :aria-label (tr :actions/open-account-menu)}
   (if logged-in?
     [avatar]
     [mui/icon "account_circle"])])

(defn create-menu [tr anchor logged-in?]
  (let [close #(==> [::events/show-account-menu nil])]
    [mui/menu {:anchor-el anchor
               :open      true
               :on-close  close}

     ;; Login
     (when (not logged-in?)
       [mui/menu-item
        {:id       "account-menu-item-login"
         :on-click (comp close #(navigate! "/#/kirjaudu" :comeback? true))}
        [mui/list-item-icon
         [mui/icon "lock"]]
        [mui/list-item-text {:primary (tr :login/headline)}]])

     ;; Register
     (when (not logged-in?)
       [mui/menu-item {:id       "account-menu-item-register"
                       :on-click (comp close #(navigate! "/#/rekisteroidy"))}
        [mui/list-item-icon
         [mui/icon "group_add"]]
        [mui/list-item-text {:primary (tr :register/headline)}]])

     ;; Profile
     (when logged-in?
       [mui/menu-item {:id       "account-menu-item-profile"
                       :on-click (comp close #(navigate! "/#/profiili"))}
        [mui/list-item-icon
         [mui/icon "account_circle"]]
        [mui/list-item-text {:primary (tr :user/headline)}]])

     ;; Help
     [mui/menu-item {:id       "account-menu-item-help"
                     :on-click (comp close #(navigate! (:help links)))}
      [mui/list-item-icon
       [mui/icon "help"]]
      [mui/list-item-text {:primary (tr :help/headline)}]]

     ;; Logout
     (when logged-in?
       [mui/menu-item {:id       "account-menu-item-logout"
                       :on-click (comp close logout!)}
        [mui/list-item-icon
         [mui/icon "exit_to_app"]]
        [mui/list-item-text {:primary (tr :login/logout)}]])]))

(defn separator []
  [mui/typography {:component "span"
                   :color "secondary"
                   :variant "title"
                   :style
                   {:display "inline"
                    :font-weight "bold"
                    :font-size "1em"
                    :margin "0.5em"}}
   "|"])

(defn lang-btn [locale]
  [mui/button {:style
               {:min-width "0px"
                :padding   0
                :font-size "1em"}
               :on-click #(==> [::events/set-translator locale])}
   (name locale)])

(defn lang-selector []
  [mui/grid {:item true :style {:margin "1em"}}
   [lang-btn :fi]
   [separator]
   ;; [lang-btn :se]
   ;; [separator]
   [lang-btn :en]])

(defn toggle-drawer [_]
  (==> [::events/toggle-drawer]))

(defn create-drawer [tr logged-in?]
  (let [hide-and-navigate! (comp toggle-drawer navigate!)]
    [mui/swipeable-drawer {:open     true
                           :anchor   :top
                           :on-open  #()
                           :on-close toggle-drawer}

     [mui/list

      ;; Close btn
      [mui/list-item {:button   true
                      :on-click toggle-drawer}
       [mui/typography {:variant :title}
        (tr :menu/headline)]
       [mui/list-item-secondary-action
        [mui/icon-button {:on-click toggle-drawer}
         [mui/icon {:color "secondary"} "close"]]]]

      ;; Lang-selector
      [mui/list-item
       [lang-selector]]

      ;; Home
      [mui/list-item {:button   true
                      :on-click #(hide-and-navigate! "/#/")}
       [mui/list-item-icon
        [mui/icon "home"]]
       [mui/list-item-text {:primary (tr :menu/frontpage)}]]

      [mui/divider]

      ;; Sports sites
      [mui/list-item {:button   true
                      :on-click #(hide-and-navigate! "/#/liikuntapaikat")}
       [mui/list-item-icon
        [mui/icon "place"]]
       [mui/list-item-text {:primary (tr :sport/headline)}]]

      ;; Ice stadiums
      [mui/list-item {:button   true
                      :on-click #(hide-and-navigate! "/#/jaahalliportaali")}
       [mui/list-item-icon
        [mui/icon "ac_unit"]]
       [mui/list-item-text {:primary (tr :ice/headline)}]]

      ;; Swiming pools
      [mui/list-item {:button   true
                      :on-click #(hide-and-navigate! "/#/uimahalliportaali")}
       [mui/list-item-icon
        [mui/icon "pool"]]
       [mui/list-item-text {:primary (tr :swim/headline)}]]
      [mui/divider]

      ;; Help
      [mui/list-item {:button   true
                      :on-click #(hide-and-navigate! (:help links))}
       [mui/list-item-icon
        [mui/icon "help"]]
       [mui/list-item-text {:primary (tr :help/headline)}]]
      [mui/divider]

      ;; Profile
      (when logged-in?
        [mui/list-item {:button   true
                        :on-click #(hide-and-navigate! "/#/profiili")}
         [mui/list-item-icon
          [mui/icon "account_circle"]]
         [mui/list-item-text {:primary (tr :user/headline)}]])

      ;; Logout
      (when logged-in?
        [mui/list-item {:button   true
                        :on-click logout!}
         [mui/list-item-icon
          [mui/icon "exit_to_app"]]
         [mui/list-item-text {:primary (tr :login/logout)}]])

      ;; Login
      (when (not logged-in?)
        [mui/list-item {:button   true
                        :on-click #(hide-and-navigate! "/#/kirjaudu")}
         [mui/list-item-icon
          [mui/icon "lock"]]
         [mui/list-item-text {:primary (tr :login/headline)}]])

      ;; Register
      (when (not logged-in?)
        [mui/list-item {:button   true
                        :on-click #(hide-and-navigate! "/#/rekisteroidy")}
         [mui/list-item-icon
          [mui/icon "group_add"]]
         [mui/list-item-text {:primary (tr :register/headline)}]])]]))

(defn nav [tr menu-anchor drawer-open? active-panel logged-in?]
  [mui/app-bar {:position "static"
                :color    "primary"
                :style    {:border-box "1px solid black"}}

   [mui/tool-bar {:disable-gutters true}

    (when menu-anchor
      (create-menu tr menu-anchor logged-in?))

    (when drawer-open?
      (create-drawer tr logged-in?))

    ;;; JYU logo
    [:a {:href "/#/"}
     [mui/svg-icon {:view-box "0 0 132.54 301.95"
                    :style    {:height "2em"
                               :margin "0.45em"}}
      [svg/jyu-logo]]]

    ;;; Header text
    [mui/typography {:variant "title"
                     :style   {:flex        1
                               :font-size   "1em"
                               :font-weight "bold"}}

     ;; University of Jyväskylä
     [mui/hidden {:sm-down true}
      [mui/typography {:component "a"
                       :variant   "title"
                       :href      "/#/"
                       :style
                       (merge mui/headline-aleo
                              {:display         :inline
                               :font-size       "1em"
                               :text-transform  :none
                               :text-decoration :none})}
       (tr :menu/jyu)]

      [separator]]

     ;; LIPAS
     [mui/typography {:component "a"
                      :variant   "title"
                      :href      "/#/"
                      :style
                      (merge mui/headline-aleo
                             {:display         :inline
                              :font-size       "1em"
                              :text-transform  :none
                              :text-decoration :none})}

      (tr :menu/headline)

      [separator]

      ;; Sub page header
      (case active-panel
        :home-panel           (tr :home-page/headline)
        :admin-panel          (tr :lipas.admin/headline)
        :sports-panel         (tr :sport/headline)
        :ice-panel            (tr :ice/headline)
        :swim-panel           (tr :swim/headline)
        :login-panel          (tr :login/headline)
        :register-panel       (tr :register/headline)
        :user-panel           (tr :user/headline)
        :reset-password-panel (tr :reset-password/headline)
        "")]]

    [mui/hidden {:sm-down true}
      [lang-selector]]

    ;;; Search button
    ;; [mui/icon-button {:id         "search-btn"
    ;;                   :aria-label (tr :actions/open-search)}
    ;;  [mui/icon "search"]]

    ;;; Account menu button
    [account-menu-button {:tr tr :logged-in? logged-in?}]

    ;;; Main menu (drawer) button
    [mui/icon-button {:id         "main-menu-btn"
                      :aria-label (tr :actions/open-main-menu)
                      :on-click   toggle-drawer}
     [mui/icon {:color "secondary"} "menu"]]]])
