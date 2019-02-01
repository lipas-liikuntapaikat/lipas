(ns lipas.ui.navbar
  (:require
   [clojure.string :as string]
   [lipas.ui.events :as events]
   [lipas.ui.mui :as mui]
   [lipas.ui.subs :as subs]
   [lipas.ui.svg :as svg]
   [lipas.ui.utils :refer [<== ==> navigate!] :as utils]))

(def links
  {:help "https://www.jyu.fi/sport/fi/yhteistyo/lipas-liikuntapaikat.fi"})

(defn logout! []
  (==> [:lipas.ui.login.events/logout]))

(defn avatar []
  (let [initials (<== [::subs/user-initials])]
    [mui/avatar {:style {:font-size "0.65em" :color "#fff"}}
     initials]))

(defn account-menu-button [{:keys [tr logged-in?]}]
  [mui/icon-button
   {:on-click   #(==> [::events/show-account-menu (.-currentTarget %)])
    :id         "account-btn"
    :aria-label (tr :actions/open-account-menu)}
   (if logged-in?
     [avatar]
     [mui/icon "account_circle"])])

(defn account-menu [{:keys [tr logged-in?]}]
  (let [anchor     (<== [::subs/account-menu-anchor])
        close      #(==> [::events/show-account-menu nil])]

    [mui/menu {:anchor-el anchor
               :open      (some? anchor)
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
  [mui/typography
   {:component "span"
    :color     "secondary"
    :variant   "h6"
    :style
    {:display     "inline"
     :font-weight "bold"
     :font-size   "1em"
     :margin      "0.5em"}}
   "|"])

(defn lang-btn [locale]
  [mui/icon-button
   {:style {:font-size "1em"}
    :on-click #(==> [::events/set-translator locale])}
   [mui/typography {:variant "body2"}
    (-> locale name string/upper-case)]])

(defn lang-selector []
  [mui/grid {:item true :style {:margin "1em"}}
   [lang-btn :fi]
   [separator]
   ;; [lang-btn :se]
   ;; [separator]
   [lang-btn :en]])

(defn toggle-drawer [_]
  (==> [::events/toggle-drawer]))

(defn drawer [{:keys [tr logged-in?]}]
  (let [open?              (<== [::subs/drawer-open?])
        admin?             (<== [:lipas.ui.user.subs/admin?])
        hide-and-navigate! (comp toggle-drawer navigate!)]
    [mui/swipeable-drawer {:open     open?
                           :anchor   :top
                           :on-open  #()
                           :on-close toggle-drawer}

     [mui/list

      ;; Close btn
      [mui/list-item {:button   true
                      :on-click toggle-drawer}
       [mui/typography {:variant "h6"}
        (tr :menu/headline)]
       [mui/list-item-secondary-action
        [mui/icon-button {:on-click toggle-drawer}
         [mui/icon {:color "secondary"} "close"]]]]

      ;; Lang-selector
      [mui/list-item
       [lang-selector]]

      ;; Home
      [mui/list-item {:button   true
                      :on-click #(hide-and-navigate! "/#/etusivu")}
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

      ;; Reports
      [mui/list-item {:button   true
                      :on-click #(hide-and-navigate! "/#/tilastot")}
       [mui/list-item-icon
        [mui/icon "insert_chart_outlined"]]
       [mui/list-item-text {:primary (tr :reports/headline)}]]

      [mui/divider]

      ;; Admin
      (when admin?
        [mui/list-item {:button   true
                        :on-click #(hide-and-navigate! "/#/admin")}
         [mui/list-item-icon
          [mui/icon "settings"]]
         [mui/list-item-text {:primary (tr :lipas.admin/headline)}]])

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

(defn get-sub-page [panel tr]
  (case panel
    :front-page-panel     {:text (tr :home-page/headline)
                           :href "/#/etusivu"}
    :admin-panel          {:text (tr :lipas.admin/headline)
                           :href "/#/admin"}
    :sports-panel         {:text (tr :sport/headline)
                           :href "/#/liikuntapaikat"}
    :ice-stadiums-panel   {:text (tr :ice/headline)
                           :href "/#/jaahalliportaali"}
    :swimming-pools-panel {:text (tr :swim/headline)
                           :href "/#/uimahalliportaali"}
    :login-panel          {:text (tr :login/headline)
                           :href "/#/kirjaudu"}
    :register-panel       {:text (tr :register/headline)
                           :href "/#/rekisteroidy"}
    :user-panel           {:text (tr :user/headline)
                           :href "/#/profiili"}
    :reset-password-panel {:text (tr :reset-password/headline)
                           :href "/#/passu-hukassa"}
    :reports-panel        {:text (tr :reports/headline)
                           :href "/#/raportit"}
    {:text "" :href ""}))

(defn menu-button [{:keys [tr]}]
  [mui/icon-button {:id         "main-menu-btn"
                    :aria-label (tr :actions/open-main-menu)
                    :on-click   toggle-drawer}
   [mui/icon {:color "secondary"
              :style {:font-weight :bold}} "menu"]])

(defn nav [{:keys [tr active-panel logged-in?]}]
  [mui/app-bar {:position   "static"
                :color      "primary"
                :style      {:border-box "1px solid black"}
                :class-name :no-print}

   [mui/tool-bar {:disable-gutters true}

      ;;; JYU logo
    [:a {:href "https://www.jyu.fi"}
     [mui/svg-icon {:view-box "0 0 132.54 301.95"
                    :style    {:height "2em"
                               :margin "0.45em"}}
      [svg/jyu-logo]]]

      ;;; Header text
    [mui/typography
     {:variant "h6"
      :style
      {:flex        1
       :font-size   "1em"
       :font-weight "bold"}}

     ;; University of Jyväskylä
     [mui/hidden {:sm-down true}
      [mui/typography
       {:component "a"
        :variant   "h6"
        :href      "https://www.jyu.fi"
        :style
        (merge mui/headline-aleo
               {:display         "inline"
                :font-size       "1em"
                :text-transform  "none"
                :text-decoration "none"})}
       (tr :menu/jyu)]

      [separator]]

     ;; LIPAS
     [mui/typography {:component "a"
                      :variant   "h6"
                      :href      "/#/etusivu"
                      :style
                      (merge mui/headline-aleo
                             {:display         "inline"
                              :font-size       "1em"
                              :text-transform  "none"
                              :text-decoration "none"})}

      (tr :menu/headline)]

     [separator]

     [mui/typography {:component "a"
                      :variant   "h6"
                      :href      (:href (get-sub-page active-panel tr))
                      :style
                      (merge mui/headline-aleo
                             {:display         "inline"
                              :font-size       "1em"
                              :text-transform  "none"
                              :text-decoration "none"})}

      ;; Sub page header
      (:text (get-sub-page active-panel tr))]]

    [mui/hidden {:sm-down true}
     [lang-selector]]

    ;;; Search button
    ;; [mui/icon-button {:id         "search-btn"
    ;;                   :aria-label (tr :actions/open-search)}
    ;;  [mui/icon "search"]]

      ;;; Account menu button
    [account-menu-button {:tr tr :logged-in? logged-in?}]

      ;;; Main menu (drawer) button
    [menu-button {:tr tr}]]])

(defn mini-nav [{:keys [tr logged-in?]}]
  [mui/tool-bar {:disable-gutters true}
   [account-menu-button {:tr tr :logged-in? logged-in?}]
   [menu-button {:tr tr}]])
