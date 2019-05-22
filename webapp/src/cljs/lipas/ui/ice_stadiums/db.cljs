(ns lipas.ui.ice-stadiums.db
  (:require
   [lipas.data.ice-stadiums :as data]))

(def default-db
  {:active-tab                0
   :editing                   nil
   :editing?                  false
   :dialogs
   {:rink
    {:open? false}}
   :size-categories           data/size-categories
   :condensate-energy-targets data/condensate-energy-targets
   :refrigerants              data/refrigerants
   :refrigerant-solutions     data/refrigerant-solutions
   :heat-recovery-types       data/heat-recovery-types
   :dryer-types               data/dryer-types
   :dryer-duty-types          data/dryer-duty-types
   :heat-pump-types           data/heat-pump-types
   :ice-resurfacer-fuels      data/ice-resurfacer-fuels})
