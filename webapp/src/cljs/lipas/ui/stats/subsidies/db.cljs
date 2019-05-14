(ns lipas.ui.stats.subsidies.db
  (:require
   [lipas.reports :as reports]
   [lipas.ui.utils :as utils]))

(def defaults
  {:selected-view     "chart"
   :selected-cities   [] ; whole country
   :selected-types    [] ; all types
   :selected-issuers  ["AVI" "OKM"]
   :selected-years    (range 2002 utils/this-year)
   :groupings         reports/subsidies-groupings
   :issuers           reports/subsidies-issuers
   :selected-grouping "avi"
   :chart-type        "ranking"})
