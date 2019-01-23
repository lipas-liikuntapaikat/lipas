(ns lipas.ui.admin.routes
  (:require
   [lipas.ui.utils :as utils :refer [==>]]))

(def routes
  ["admin"
   {:name :lipas.ui.routes/admin
    :controllers
    [{:start
      (fn [& params]
        (==> [:lipas.ui.events/set-active-panel :admin-panel]))}]}])
