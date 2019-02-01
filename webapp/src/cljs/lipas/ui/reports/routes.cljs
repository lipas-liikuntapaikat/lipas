(ns lipas.ui.reports.routes
  (:require
   [lipas.ui.utils :as utils :refer [==>]]))

(def routes
  ["tilastot"
   {:name :lipas.ui.routes.reports/front-page
    :controllers
    [{:start
      (fn []
        (==> [:lipas.ui.events/set-active-panel :reports-panel])
        (==> [:lipas.ui.reports.events/select-cities [992]]))}]}])
