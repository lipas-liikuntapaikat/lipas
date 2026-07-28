(ns lipas.ui.forgot-password.routes
  (:require [lipas.ui.forgot-password.views :as views]
            [lipas.ui.utils :refer [==>]]))

(def routes
  ["passu-hukassa"
   {:name   :lipas.ui.routes/reset-password
    :tr-key :reset-password/headline
    :view   views/main
    :controllers
    [{:start
      (fn [& _params]
        (==> [:lipas.ui.forgot-password.events/clear-feedback]))}]}])
