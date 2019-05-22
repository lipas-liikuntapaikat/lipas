(ns lipas.ui.stats.sport.db
  (:require
   [lipas.reports :as reports]))

(def default-db
  {:groupings         reports/sports-stats-groupings
   :selected-grouping "location.city.city-code"
   :metrics           reports/sports-stats-metrics
   :selected-metric   "sites-count"
   :selected-view     "chart"})
