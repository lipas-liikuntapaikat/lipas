(ns lipas.ui.register.routes
  (:require [lipas.ui.register.views :as views]
            [lipas.ui.utils :as utils :refer [==>]]))

(def routes
  ["rekisteroidy"
   {:name   :lipas.ui.routes/register
    :tr-key :register/headline
    :view   views/main
    :controllers
    [{:start
      ;; Opened from the emailed link: ?token=<email-verification token>
      (fn [& _params]
        (==> [:lipas.ui.register.events/init-registration
              (utils/parse-token (-> js/window .-location .-href))]))
      :stop
      (fn [& _params]
        (==> [:lipas.ui.register.events/reset-form]))}]}])
