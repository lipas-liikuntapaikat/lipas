(ns lipas.ui.views
  (:require [lipas.ui.components :as lui]
            [lipas.ui.events :as events]
            [lipas.ui.front-page.views :as front-page]
            [lipas.ui.help.views :as help]
            [lipas.ui.ice-stadiums.views :as ice-stadiums]
            [lipas.ui.login.views :as login]
            [lipas.ui.mui :as mui]
            [lipas.ui.navbar :as nav]
            [lipas.ui.open-data.views :as open-data]
            [lipas.ui.register.views :as register]
            [lipas.ui.sports-places.views :as sports-places]
            [lipas.ui.subs :as subs]
            [lipas.ui.swimming-pools.views :as swimming-pools]
            [lipas.ui.user.views :as user]
            [re-frame.core :as re-frame]))

(defn- panels [panel-name tr]
  (case panel-name
    :home-panel [front-page/main tr]
    :sports-panel [sports-places/main tr]
    :ice-panel [ice-stadiums/main tr]
    :swim-panel [swimming-pools/main tr]
    :open-data-panel [open-data/main tr]
    :help-panel [help/main tr]
    :login-panel [login/main tr]
    :register-panel [register/main tr]
    :user-panel [user/main tr]
    [:div "Unknown page :/"]))

(defn show-panel [panel-name tr]
  [panels panel-name tr])

(defn main-panel []
  (let [active-panel (re-frame/subscribe [::subs/active-panel])
        menu-anchor  (re-frame/subscribe [::subs/menu-anchor])
        drawer-open? (re-frame/subscribe [::subs/drawer-open?])
        logged-in?   (re-frame/subscribe [::subs/logged-in?])
        notification (re-frame/subscribe [::subs/active-notification])
        tr           (re-frame/subscribe [::subs/translator])]
    [mui/css-baseline
     [mui/mui-theme-provider {:theme mui/jyu-theme-dark}
      [nav/nav @tr @menu-anchor @drawer-open? @active-panel @logged-in?]]
     [mui/mui-theme-provider {:theme mui/jyu-theme-light}
      [show-panel @active-panel @tr]
      (when @notification
        [lui/notification {:notification @notification
                           :on-close #(re-frame/dispatch
                                       [::events/set-active-notification nil])}])]]))
