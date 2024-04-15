(ns lipas.data.floorball
  (:require
   [lipas.data.materials :as materials]))

(def audit-type "floorball-circumstances-audit")

(def floor-elasticity
  {"point" {:fi "Piste"
            :se "Punkt"
            :en "Point"}
   "area"  {:fi "Alue"
            :se "Areal"
            :en "Area"}
   "unknown" {:fi "Ei tietoa"
              :se "Okänt"
              :en "Unknown"}})

(def player-entrance
  {"private-entrance" {:fi "Oma sisäänkäynti"
                       :sv ""
                       :en "Private entrance"}
   "audience-entrance" {:fi "Katsojien kanssa samasta"
                        :sv ""
                        :en "Same as audience entrance"}})

(def audience-stand-access
  {"from-field-level" {:fi "Kenttätasolta"
                       :se ""
                       :en "From field level"}
   "from-upper-level" {:fi "Yläkautta"
                       :se ""
                       :en "From upper level"}})

(def car-parking-economics-model
  {"paid" {:fi "Maksullinen"
           :se ""
           :en "Paid"}
   "free" {:fi "Maksuton"
           :se ""
           :en "Free"}})

(def roof-trussess-operation-model
  {"can-be-lowered" {:fi "Saa laskettua alas"
                     :se ""
                     :en "Can be lowered"}
   "lift-required"  {:fi "Tarvitsee nostimen"
                     :se ""
                     :en "Lift is required"}})

(def field-surface-materials
  (select-keys materials/field-surface-materials ["resin" "wood" "carpet"]))
