(ns lipas.ui.login.routes
  (:require
   [lipas.ui.utils :as utils :refer [==>]]))

(def routes
  ["kirjaudu"
   {:name :lipas.ui.routes/login
    :controllers
    [{:start
      (fn [& params]
        (==> [:lipas.ui.events/set-active-panel :login-panel]))}]}])
