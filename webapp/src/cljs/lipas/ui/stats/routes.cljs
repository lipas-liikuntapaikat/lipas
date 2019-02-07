(ns lipas.ui.stats.routes
  (:require
   [lipas.ui.utils :as utils :refer [==>]]))

(def routes
  ["tilastot"
   {:name :lipas.ui.routes.reports/front-page
    :controllers
    [{:start
      (fn []
        (==> [:lipas.ui.events/set-active-panel :stats-panel])
        (==> [:lipas.ui.stats.events/select-finance-cities [179]])
        (==> [:lipas.ui.stats.events/create-age-structure-report]))}]}])
