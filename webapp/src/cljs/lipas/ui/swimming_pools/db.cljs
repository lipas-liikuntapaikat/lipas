(ns lipas.ui.swimming-pools.db
  (:require [lipas.data.materials :as materials]
            [lipas.data.swimming-pools :as data]))

(def default-db
  {:active-tab        0
   :pool-types        data/pool-types
   :sauna-types       data/sauna-types
   :filtering-methods data/filtering-methods
   :heat-sources      data/heat-sources
   :accessibility     data/accessibility
   :pool-structures   materials/pool-structures
   :editing           nil
   :editing?          false
   :dialogs
   {:pool   {:open? false}
    :slide  {:open? false}
    :energy {:open? false}
    :sauna  {:open? false}}})
