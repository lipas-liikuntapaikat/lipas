(ns lipas.ui.admin.routes
  (:require
   [lipas.ui.utils :as utils :refer [==>]]))

(def routes
  ["admin"
   {:name :lipas.ui.routes/admin
    :controllers
    [{:start
      (fn [& params]
        (==> [:lipas.ui.events/set-active-panel :admin-panel])
        (==> [:lipas.ui.admin.events/get-users])
        (==> [:lipas.ui.sports-sites.events/get-by-type-code 3110])
        (==> [:lipas.ui.sports-sites.events/get-by-type-code 3130])
        (==> [:lipas.ui.sports-sites.events/get-by-type-code 2510])
        (==> [:lipas.ui.sports-sites.events/get-by-type-code 2520]))}]}])
