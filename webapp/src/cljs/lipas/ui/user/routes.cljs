(ns lipas.ui.user.routes
  (:require
   [lipas.ui.utils :as utils :refer [==>]]
   [lipas.ui.user.views :as views]))

(def routes
  ["profiili"
   {:name   :lipas.ui.routes/user
    :tr-key :user/headline
    :view   views/main
    :controllers
    [{:start
      (fn [& params]
        (==> [:lipas.ui.user.events/get-users-sports-sites]))}]}])
