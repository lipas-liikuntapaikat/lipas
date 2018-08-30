(ns lipas.ui.views
  (:require [lipas.ui.components :as lui]
            [lipas.ui.events :as events]
            [lipas.ui.front-page.views :as front-page]
            [lipas.ui.ice-stadiums.views :as ice-stadiums]
            [lipas.ui.login.views :as login]
            [lipas.ui.mui :as mui]
            [lipas.ui.navbar :as nav]
            [lipas.ui.register.views :as register]
            [lipas.ui.sports-sites.views :as sports-sites]
            [lipas.ui.subs :as subs]
            [lipas.ui.swimming-pools.views :as swimming-pools]
            [lipas.ui.user.reset-password :as reset-password]
            [lipas.ui.user.views :as user]
            [lipas.ui.utils :refer [<== ==>] :as utils]))

(defn- panels [panel-name tr logged-in?]
  (case panel-name
    :home-panel           [front-page/main tr]
    :sports-panel         [sports-sites/main tr]
    :ice-panel            [ice-stadiums/main]
    :swim-panel           [swimming-pools/main]
    :login-panel          [login/main tr]
    :reset-password-panel [reset-password/main tr]
    :register-panel       [register/main tr]
    :user-panel           [user/main tr]
    [front-page/main tr]))

(defn show-panel [panel-name tr logged-in?]
  [panels panel-name tr logged-in?])

(defn main-panel []
  (let [active-panel (<== [::subs/active-panel])
        menu-anchor  (<== [::subs/account-menu-anchor])
        drawer-open? (<== [::subs/drawer-open?])
        logged-in?   (<== [::subs/logged-in?])
        notification (<== [::subs/active-notification])
        confirmation (<== [::subs/active-confirmation])
        tr           (<== [::subs/translator])]
    [mui/css-baseline

     [mui/mui-theme-provider {:theme mui/jyu-theme-dark}

      ;; Navbar and drawer
      [nav/nav tr menu-anchor drawer-open? active-panel logged-in?]

      ;; Dev-env disclaimer
      (when-not (utils/prod?)
        [mui/grid {:item true :xs 12 :md 12 :lg 12}
         [mui/card {:square true
                    :style  {:background-color mui/secondary}}
          [mui/card-content
           [mui/typography {:variant :headline
                            :display :inline}
            (tr :disclaimer/headline)]
           [mui/typography {:variant :body2}
            (tr :disclaimer/test-version)]]]])
      ]

     [mui/mui-theme-provider {:theme mui/jyu-theme-light}

      ;; Main panel
      [show-panel active-panel tr logged-in?]

      ;; Global UI-blocking confirmation dialog
      (when confirmation
        [lui/confirmation-dialog confirmation])

      ;; Global ephmeral notifications
      (when notification
        [lui/notification
         {:notification notification
          :on-close     #(==> [::events/set-active-notification nil])}])]]))
