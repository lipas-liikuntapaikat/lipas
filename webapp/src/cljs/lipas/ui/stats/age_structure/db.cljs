(ns lipas.ui.stats.age-structure.db
  (:require [lipas.reports :as reports]))

(def default-db
  {:groupings         reports/age-structure-groupings
   :selected-grouping "owner"
   :selected-interval 10
   :selected-view     "chart"})
