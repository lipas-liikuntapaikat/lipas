(ns lipas.ui.stats.db
  (:require
   [lipas.ui.stats.age-structure.db :as age-structure]
   [lipas.ui.stats.city.db :as city]
   [lipas.ui.stats.finance.db :as finance]
   [lipas.ui.stats.sport.db :as sport]
   [lipas.ui.stats.subsidies.db :as subsidies]))

(def default-db
  {:selected-tab  "sport"
   :age-structure age-structure/default-db
   :city          city/default-db
   :finance       finance/default-db
   :sport         sport/default-db
   :subsidies     subsidies/defaults})
