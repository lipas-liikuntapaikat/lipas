(ns lipas.ui.sports-sites.floorball.db
  (:require
   [lipas.data.floorball :as data]))

(def default-db
  {:type-codes                    #{2240}
   :floor-elasticity              data/floor-elasticity
   :player-entrance               data/player-entrance
   :audience-stand-access         data/audience-stand-access
   :car-parking-economics-model   data/car-parking-economics-model
   :roof-trussess-operation-model data/roof-trussess-operation-model
   :dialogs
   {:field       {:open? false :data {}}
    :locker-room {:open? false :data {}}}})
