(ns lipas.data.materials)

;; Contains also structures

(def all
  {"asphalt"          {:fi "Asfaltti"
                       :se nil
                       :en "Asphalt"}
   "aluminum"         {:fi "Alumiini"
                       :en "Aluminum"
                       :se nil}
   "stone"            {:fi "Kivi"
                       :en "Stone"
                       :se nil}
   "sand"             {:fi "Hiekka"
                       :se nil
                       :en "Sand"}
   "concrete"         {:fi "Betoni"
                       :se nil
                       :en "Concrete"}
   "brick"            {:fi "Tiili"
                       :se nil
                       :en "Brick"}
   "tile"             {:fi "Laatta"
                       :se nil
                       :en "Tile"}
   "steel"            {:fi "Teräs"
                       :se nil
                       :en "Steel"}
   "wood"             {:fi "Puu"
                       :se nil
                       :en "Wood"}
   "glass"            {:fi "Lasi"
                       :se nil
                       :en "Glass"}
   "hardened-plastic" {:fi "Lujitemuovi"
                       :se nil
                       :en "Hardened plastic"}
   "sheet-metal"      {:fi "Pelti"
                       :se nil
                       :en "Sheet metal"}
   "glulam"           {:fi "Liimapuu"
                       :se nil
                       :en "Glulam"}
   "hollow-core-slab" {:fi "Ontelolaatta"
                       :se nil
                       :en "Hollow-core slab"}
   "concrete-pillar"  {:fi "Betonipilari"
                       :se nil
                       :en "Concrete pillar"}
   "concrete-beam"    {:fi "Betonipalkki"
                       :se nil
                       :en "Concrete beam"}
   "double-t-beam"    {:fi "TT-laatta" ; TT-palkki/TT-laatta, betonia??
                       :se nil
                       :en "Double-T"}
   "precast-concrete" {:fi "Betonielementti"
                       :se nil
                       :en "Precast concrete"}
   "pent-roof"        {:fi "Pulpettikatto"
                       :se nil
                       :en "Pent roof"}
   "felt"             {:fi "Huopa"
                       :se nil
                       :en "Felt"}
   "solid-rock"       {:fi "Kallio"
                       :se nil
                       :en "Solid rock"}
   "insulator"        {:fi "Eriste"
                       :se nil
                       :en "Insulator"}
   "composite-beam"   {:fi "Liittopalkki"
                       :se nil
                       :en "Composite beam"}})

(def building-materials
  (select-keys all ["concrete" "brick""steel" "wood" "solid-rock"]))

(def slide-structures
  (select-keys all ["concrete" "steel" "hardened-plastic"]))

(def pool-structures
  (select-keys all ["concrete" "steel" "hardened-plastic"]))

(def supporting-structures
  (select-keys all ["concrete" "wood" "concrete-pillar" "concrete-beam" "steel"
                    "solid-rock" "brick"]))

(def ceiling-structures
  (select-keys all ["wood" "hollow-core-slab" "steel" "double-t-beam"
                    "concrete" "glass" "solid-rock" "polyurethane-panel"]))

(def base-floor-structures
  (select-keys all ["concrete" "asphalt" "sand"]))
