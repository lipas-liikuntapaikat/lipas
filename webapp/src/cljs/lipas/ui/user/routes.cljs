(ns lipas.ui.user.routes
  (:require
   [lipas.ui.utils :as utils :refer [==>]]))

(def login-route
  ["kirjaudu"
   {:name :lipas.ui.routes/login
    :controllers
    [{:start
      (fn [& params]
        (==> [:lipas.ui.events/set-active-panel :login-panel]))}]}])

(def forgot-password-route
  ["passu-hukassa"
   {:name :lipas.ui.routes/reset-password
    :controllers
    [{:start
      (fn [& params]
        (==> [:lipas.ui.events/set-active-panel :reset-password-panel]))}]}])

(def register-route
  ["rekisteroidy"
   {:name :lipas.ui.routes/register
    :controllers
    [{:start
      (fn [& params]
        (==> [:lipas.ui.events/set-active-panel :register-panel]))}]}])

(def profile-route
  ["profiili"
   {:name :lipas.ui.routes/user
    :controllers
    [{:start
      (fn [& params]
        (==> [:lipas.ui.events/set-active-panel :user-panel]))}]}])
