(ns lipas.ui.stats.city.db
  (:require [lipas.reports :as reports]
            [lipas.ui.utils :as utils]))

(def default-db
  {:selected-cities #{179}
   :finance
   {:metrics               reports/stats-metrics
    :selected-metrics      ["net-costs" "investments"]
    :city-services         reports/city-services
    :selected-city-service "sports-services"
    :units                 reports/stats-units
    :selected-unit         "1000-euros"
    :selected-years        (range 2000 utils/this-year)
    :selected-view         "chart"}})
