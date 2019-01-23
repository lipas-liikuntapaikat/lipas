(ns lipas.ui.register.routes
  (:require
   [lipas.ui.utils :as utils :refer [==>]]))

(def routes
  ["rekisteroidy"
   {:name :lipas.ui.routes/register
    :controllers
    [{:start
      (fn [& params]
        (==> [:lipas.ui.events/set-active-panel :register-panel]))}]}])
