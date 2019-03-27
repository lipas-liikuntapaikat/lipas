(ns lipas.ui.user.routes
  (:require
   [lipas.ui.utils :as utils :refer [==>]]))

(def routes
  ["profiili"
   {:name :lipas.ui.routes/user
    :controllers
    [{:start
      (fn [& params]
        (==> [:lipas.ui.events/set-active-panel :user-panel])
        (==> [:lipas.ui.user.events/get-users-sports-sites]))}]}])
