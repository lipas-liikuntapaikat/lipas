(ns lipas.ui.sports-sites.views
  (:require [lipas.ui.mui :as mui]
            [lipas.ui.sports-sites.subs :as subs]
            [lipas.ui.sports-sites.events :as events]
            [lipas.ui.map.map :as map]))

(defn create-panel [tr]
  [map/map-inner])

(defn main [tr]
  (create-panel tr))
