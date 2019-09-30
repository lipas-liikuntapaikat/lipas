(ns lipas.ui.stats.finance.db
  (:require
   [lipas.reports :as reports]
   [lipas.ui.utils :as utils]))

(def default-db
  {:selected-view           "chart"
   :selected-cities         [] ; whole country
   :selected-year           (dec utils/this-year)
   :units                   reports/stats-units
   :selected-unit           "euros-per-capita"
   :city-services           reports/city-services
   :selected-city-service   "sports-services"
   :groupings               reports/finance-stats-groupings
   :selected-grouping       "avi"
   :metrics                 reports/stats-metrics
   :selected-metrics        ["net-costs" "investments"]
   :selected-ranking-metric "net-costs"
   :chart-type              "comparison"})
