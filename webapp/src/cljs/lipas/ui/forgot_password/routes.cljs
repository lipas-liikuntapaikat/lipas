(ns lipas.ui.forgot-password.routes
  (:require
   [lipas.ui.utils :refer [==>]]
   [lipas.ui.forgot-password.views :as views]))

(def routes
  ["passu-hukassa"
   {:name   :lipas.ui.routes/reset-password
    :tr-key :reset-password/headline
    :view   views/main
    :controllers
    [{:start
      (fn [& params]
        (==> [:lipas.ui.forgot-password.events/clear-feedback]))}]}])
