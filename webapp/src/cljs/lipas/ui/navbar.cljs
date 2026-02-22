(ns lipas.ui.navbar
  (:require [clojure.string :as string]
            [lipas.ui.feedback.views :as feedback]
            [lipas.ui.help.views :as help]
            ["@mui/material/AppBar$default" :as AppBar]
            ["@mui/material/Avatar$default" :as Avatar]
            ["@mui/material/Divider$default" :as Divider]
            ["@mui/material/GridLegacy$default" :as Grid]
            ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/IconButton$default" :as IconButton]
            ["@mui/material/List$default" :as List]
            ["@mui/material/ListItem$default" :as ListItem]
            ["@mui/material/ListItemButton$default" :as ListItemButton]
            ["@mui/material/ListItemIcon$default" :as ListItemIcon]
            ["@mui/material/ListItemSecondaryAction$default" :as ListItemSecondaryAction]
            ["@mui/material/ListItemText$default" :as ListItemText]
            ["@mui/material/Menu$default" :as Menu]
            ["@mui/material/MenuItem$default" :as MenuItem]
            ["@mui/material/SvgIcon$default" :as SvgIcon]
            ["@mui/material/SwipeableDrawer$default" :as SwipeableDrawer]
            ["@mui/material/Toolbar$default" :as Toolbar]
            ["@mui/material/Typography$default" :as Typography]
            [lipas.ui.mui :as mui]
            [lipas.ui.subs :as subs]
            [lipas.ui.svg :as svg]
            [lipas.ui.utils :refer [<== ==> navigate!] :as utils]
            [re-frame.core :as rf]
            [reitit.frontend.easy :as rfe]))

(def links
  {:help "https://www.jyu.fi/sport/fi/yhteistyo/lipas-liikuntapaikat.fi"
   :privacy-policy "https://lipas.fi/pdf/tietosuojailmoitus_lipas.pdf"})

(defn logout! []
  (==> [:lipas.ui.login.events/logout]))

(defn avatar []
  (let [initials (<== [::subs/user-initials])]
    [:> Avatar {:style {:font-size "0.65em" :color "#fff"}}
     initials]))

(defn account-menu-button
  [{:keys [tr logged-in?]}]
  [:> IconButton
   {:on-click #(==> [:lipas.ui.events/show-account-menu (.-currentTarget %)])
    :id "account-btn"
    :aria-label (tr :actions/open-account-menu)}
   (if logged-in?
     [avatar]
     [:> Icon "account_circle"])])

(defn account-menu
  [{:keys [tr logged-in?]}]
  (let [anchor (<== [::subs/account-menu-anchor])
        close #(==> [:lipas.ui.events/show-account-menu nil])
        admin? @(rf/subscribe [:lipas.ui.user.subs/check-privilege nil :users/manage])
        org? @(rf/subscribe [:lipas.ui.user.subs/can-access-some-org?])]

    [:> Menu {:anchor-el anchor
               :open (some? anchor)
               :on-close close}

     ;; Login
     (when (not logged-in?)
       [:> MenuItem
        {:id "account-menu-item-login"
         :on-click (comp close #(navigate! "/kirjaudu" :comeback? true))}
        [:> ListItemIcon
         [:> Icon "lock"]]
        [:> ListItemText {:primary (tr :login/headline)}]])

     ;; Register
     (when (not logged-in?)
       [:> MenuItem {:id "account-menu-item-register"
                       :on-click (comp close #(navigate! "/rekisteroidy"))}
        [:> ListItemIcon
         [:> Icon "group_add"]]
        [:> ListItemText {:primary (tr :register/headline)}]])

     ;; Profile
     (when logged-in?
       [:> MenuItem {:id "account-menu-item-profile"
                       :on-click (comp close #(navigate! "/profiili"))}
        [:> ListItemIcon
         [:> Icon "account_circle"]]
        [:> ListItemText {:primary (tr :user/headline)}]])

     ;; Organizations
     (when (and logged-in?
                (or admin?
                    org?))
       [:> MenuItem {:id "account-menu-item-organizations"
                       :on-click (comp close #(navigate! "/organisaatiot"))}
        [:> ListItemIcon
         [:> Icon "corporate_fare"]]
        [:> ListItemText {:primary (tr :lipas.admin/organizations)}]])

     ;; Admin
     (when admin?
       [:> MenuItem {:id "account-menu-item-admin"
                       :on-click (comp close #(navigate! "/admin"))}
        [:> ListItemIcon
         [:> Icon "settings"]]
        [:> ListItemText {:primary (tr :lipas.admin/headline)}]])

     ;; Help
     [:> MenuItem {:id "account-menu-item-help"
                     :on-click (comp close #(navigate! (:help links)))}
      [:> ListItemIcon
       [:> Icon "help"]]
      [:> ListItemText {:primary (tr :help/headline)}]]

     ;; Privacy policy
     [:> MenuItem {:id "account-menu-item-privacy-policy"
                     :on-click (comp close #(navigate! (:privacy-policy links)))}
      [:> ListItemIcon
       [:> Icon "privacy_tip"]]
      [:> ListItemText {:primary (tr :help/privacy-policy)}]]

     ;; Logout
     (when logged-in?
       [:> MenuItem {:id "account-menu-item-logout"
                       :on-click (comp close logout!)}
        [:> ListItemIcon
         [:> Icon "exit_to_app"]]
        [:> ListItemText {:primary (tr :login/logout)}]])]))

(defn separator [props]
  [:> Typography
   (merge {:component "span"
           :color "secondary"
           :variant "h6"
           :sx
           (merge {:display "inline"
                   :font-weight "bold"
                   :font-size "1em"
                   :margin "0.5em"}
                  (:sx props))}
          (dissoc props :sx))
   "|"])

(defn lang-btn [locale]
  [:> IconButton
   {:style {:font-size "1em"}
    :on-click #(==> [:lipas.ui.events/set-translator locale])}
   [:> Typography {:variant "body2"}
    (-> locale name string/upper-case)]])

(defn lang-selector [props]
  [:> Grid
   (merge {:item true :style {:margin "1em"}}
          props)
   [lang-btn :fi]
   [separator]
   [lang-btn :se]
   [separator]
   [lang-btn :en]])

(defn close-drawer [_]
  (==> [:lipas.ui.events/close-drawer]))

(defn drawer [{:keys [tr logged-in?]}]
  (let [open? (<== [::subs/drawer-open?])
        hide-and-navigate! (comp close-drawer navigate!)]
    [:> SwipeableDrawer {:open open?
                           :anchor :top
                           :on-open #(==> [:lipas.ui.events/open-drawer])
                           :on-close close-drawer}

     [:> List

      ;; Close btn
      [:> ListItemButton {:on-click close-drawer}
       [:> Typography {:variant "h6"}
        (tr :menu/headline)]
       [:> ListItemSecondaryAction
        [:> IconButton {:on-click close-drawer}
         [:> Icon {:color "secondary"} "close"]]]]

      ;; Lang-selector
      [:> ListItem
       [lang-selector]]

      ;; Home
      [:> ListItemButton {:on-click #(hide-and-navigate! "/etusivu")}
       [:> ListItemIcon
        [:> Icon "home"]]
       [:> ListItemText {:primary (tr :menu/frontpage)}]]

      [:> Divider]

      ;; Sports sites
      [:> ListItemButton {:on-click #(hide-and-navigate! "/liikuntapaikat")}
       [:> ListItemIcon
        [:> Icon "place"]]
       [:> ListItemText {:primary (tr :sport/headline)}]]

      ;; Stats
      [:> ListItemButton {:on-click #(hide-and-navigate! "/tilastot")}
       [:> ListItemIcon
        [:> Icon "insert_chart_outlined"]]
       [:> ListItemText {:primary (tr :stats/headline)}]]

      [:> Divider]

      ;; Admin
      (when @(rf/subscribe [:lipas.ui.user.subs/check-privilege nil :users/manage])
        [:> ListItemButton {:on-click #(hide-and-navigate! "/admin")}
         [:> ListItemIcon
          [:> Icon "settings"]]
         [:> ListItemText {:primary (tr :lipas.admin/headline)}]])

      ;; Help
      [:> ListItemButton {:on-click #(hide-and-navigate! (:help links))}
       [:> ListItemIcon
        [:> Icon "help"]]
       [:> ListItemText {:primary (tr :help/headline)}]]

      [:> Divider]

      ;; Profile
      (when logged-in?
        [:> ListItemButton {:on-click #(hide-and-navigate! "/profiili")}
         [:> ListItemIcon
          [:> Icon "account_circle"]]
         [:> ListItemText {:primary (tr :user/headline)}]])

      ;; Logout
      (when logged-in?
        [:> ListItemButton {:on-click logout!}
         [:> ListItemIcon
          [:> Icon "exit_to_app"]]
         [:> ListItemText {:primary (tr :login/logout)}]])

      ;; Login
      (when (not logged-in?)
        [:> ListItemButton {:on-click #(hide-and-navigate! "/kirjaudu")}
         [:> ListItemIcon
          [:> Icon "lock"]]
         [:> ListItemText {:primary (tr :login/headline)}]])

      ;; Register
      (when (not logged-in?)
        [:> ListItemButton {:on-click #(hide-and-navigate! "/rekisteroidy")}
         [:> ListItemIcon
          [:> Icon "group_add"]]
         [:> ListItemText {:primary (tr :register/headline)}]])]]))

(defn get-sub-page [route tr]
  (let [name (-> route :data :name)
        tr-key (-> route :data :tr-key)]
    (when name
      {:text (tr tr-key)
       :href (when (not (-> route :data :no-navbar-link?))
               (rfe/href name))})))

(defn menu-button [{:keys [tr]}]
  [:> IconButton
   {:id "main-menu-btn"
    :aria-label (tr :actions/open-main-menu)
    :on-click #(==> [:lipas.ui.events/open-drawer])}
   [:> Icon
    {:color "secondary"
     :style {:font-weight :bold}} "menu"]])

(defn nav [{:keys [tr logged-in?]}]
  (let [current-route (<== [:lipas.ui.subs/current-route])]
    [:> AppBar
     {:position "static"
      :color "primary"
      :sx {:border-box "1px solid black"
           :backgroundColor "primary.main"}
      :enableColorOnDark true
      :class-name :no-print}

     [:> Toolbar {:disable-gutters true}

      ;;; JYU logo
      [:a {:href "https://www.jyu.fi"}
       [:> SvgIcon
        {:view-box "0 0 132.54 301.95"
         :style {:height "2em" :margin "0.45em"}}
        [svg/jyu-logo]]]

      ;;; Header text
      [:> Typography
       {:variant "h6"
        :style
        {:flex 1
         :font-size "1em"
         :font-weight "bold"}}

       ;; University of Jyväskylä
       [:> Typography
        {:component "a"
         :variant "h6"
         :href "https://www.jyu.fi"
         :sx
         (merge
           mui/headline-aleo
           {:font-size "1em"
            :color "#ffffff"
            :text-transform "none"
            :text-decoration "none"
            :display {:xs "none"
                      :md "inline"}})}
        (tr :menu/jyu)]

       [separator
        {:sx {:display {:xs "none"
                        :md "inline"}}}]

       ;; LIPAS
       [:> Typography
        {:component "a"
         :variant "h6"
         :href "/etusivu"
         :style
         (merge
           mui/headline-aleo
           {:display "inline"
            :font-size "1em"
            :color "#ffffff"
            :text-transform "none"
            :text-decoration "none"})}
        (tr :menu/headline)]

       [separator]

       ;; Sub page header
       (let [sub-page (get-sub-page current-route tr)]
         [:> Typography
          {:component "a"
           :variant "h6"
           :href (:href sub-page)
           :style
           (merge
             mui/headline-aleo
             {:display "inline"
              :font-size "1em"
              :color "#ffffff"
              :text-transform "none"
              :text-decoration "none"})}
          (:text sub-page)])]

      ;; Lang selector
      [lang-selector
       {:sx {:display {:xs "none"
                       :md "block"}}}]

    ;;; Account menu button
      [account-menu-button {:tr tr :logged-in? logged-in?}]

    ;;; Main menu (drawer) button
      [menu-button {:tr tr}]]]))

(defn mini-nav [{:keys [tr logged-in?]}]
  [:> Toolbar {:disable-gutters true :style {:padding "0px 8px 0px 0px"}}
   [help/view]
   [feedback/feedback-btn]
   [account-menu-button {:tr tr :logged-in? logged-in?}]
   [menu-button {:tr tr}]])
