(ns lipas.ui.register.routes
  (:require
   [lipas.ui.utils :as utils :refer [==>]]
   [lipas.ui.register.views :as views]))

(def routes
  ["rekisteroidy"
   {:name   :lipas.ui.routes/register
    :tr-key :register/headline
    :view   views/main
    :controllers
    [{:stop
      (fn [& params]
        (==> [:lipas.ui..register.events/reset]))}]}])
