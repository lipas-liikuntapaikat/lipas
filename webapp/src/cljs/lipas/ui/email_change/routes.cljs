(ns lipas.ui.email-change.routes
  (:require [lipas.ui.email-change.views :as views]
            [lipas.ui.utils :as utils :refer [==>]]))

(def routes
  ["vahvista-sahkoposti"
   {:name   :lipas.ui.routes/confirm-email-change
    :tr-key :lipas.user/email-change-headline
    :view   views/main
    :controllers
    [{:start
      ;; Opened from the confirmation link: ?token=<email-change token>
      (fn [& _params]
        (==> [:lipas.ui.email-change.events/confirm
              (utils/parse-token (-> js/window .-location .-href))]))}]}])
