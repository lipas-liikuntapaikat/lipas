(ns lipas.ui.sports-sites.floorball.db
  (:require
   [lipas.data.floorball :as floorball]))

(def default-db
  {:type-codes                    #{2240}
   :floor-elasticity              floorball/floor-elasticity
   :player-entrance               floorball/player-entrance
   :audience-stand-access         floorball/audience-stand-access
   :car-parking-economics-model   floorball/car-parking-economics-model
   :roof-trussess-operation-model floorball/roof-trussess-operation-model
   :field-surface-materials       floorball/field-surface-materials
   :dialogs
   {:field       {:open? false :data {}}
    :locker-room {:open? false :data {}}}})
