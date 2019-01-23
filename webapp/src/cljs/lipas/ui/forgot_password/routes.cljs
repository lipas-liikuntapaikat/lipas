(ns lipas.ui.forgot-password.routes
  (:require
   [lipas.ui.utils :refer [==>]]))

(def routes
  ["passu-hukassa"
   {:name :lipas.ui.routes/reset-password
    :controllers
    [{:start
      (fn [& params]
        (==> [:lipas.ui.events/set-active-panel :reset-password-panel]))}]}])
