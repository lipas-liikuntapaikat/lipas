(ns lipas.ui.map.routes
  (:require
   [lipas.ui.utils :as utils :refer [==>]]))

(def routes
  ["liikuntapaikat"
   {:name :lipas.ui.routes/map
    :controllers
    [{:start
      (fn [& params]
        (==> [:lipas.ui.events/set-active-panel :map-panel]))}]}])
