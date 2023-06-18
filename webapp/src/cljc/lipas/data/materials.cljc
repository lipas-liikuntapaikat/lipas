(ns lipas.data.materials)

;; Contains also structures

(def all
  {"asphalt"                       {:fi "Asfaltti"
                                    :se "Asfalt"
                                    :en "Asphalt"}
   "aluminum"                      {:fi "Alumiini"
                                    :en "Aluminum"
                                    :se nil}
   "stone"                         {:fi "Kivi"
                                    :en "Stone"
                                    :se "Sten"}
   "sand"                          {:fi "Hiekka"
                                    :se "Sand"
                                    :en "Sand"}
   "concrete"                      {:fi "Betoni"
                                    :se "Betong"
                                    :en "Concrete"}
   "ceramic"                       {:fi "Keraaminen"
                                    :se "Keramisk"
                                    :en "Ceramic"}
   "brick"                         {:fi "Tiili"
                                    :se nil
                                    :en "Brick"}
   "brick-crush"                   {:fi "Tiilimurska"
                                    :se "Tergel småbitar"
                                    :en "Brick crush"}
   "water"                         {:fi "Vesi"
                                    :se "Vatten"
                                    :en "Water"}
   "artificial-turf"               {:fi "Tekonurmi"
                                    :se "Konstgräs"
                                    :en "Turf"}
   "sand-infilled-artificial-turf" {:fi "Hiekkatekonurmi"
                                    :se "Konstgräs med sand"
                                    :en "Sand infilled artificial turf"}
   "tile"                          {:fi "Laatta"
                                    :se nil
                                    :en "Tile"}
   "steel"                         {:fi "Teräs"
                                    :se nil
                                    :en "Steel"}
   "fiberglass"                    {:fi "Lasikuitu"
                                    :se "Glasfiber"
                                    :en "Fiberglass"}
   "soil"                          {:fi "Maa"
                                    :se "Jordet"
                                    :en "Soil"}
   "wood"                          {:fi "Puu"
                                    :se "Träd"
                                    :en "Wood"}
   "glass"                         {:fi "Lasi"
                                    :se nil
                                    :en "Glass"}
   "synthetic"                     {:fi "Muovi / synteettinen"
                                    :se "Plast / syntetisk"
                                    :en "Plastic / synthetic"}
   "grass"                         {:fi "Nurmi"
                                    :se "Gräs"
                                    :en "Grass"}
   "hardened-plastic"              {:fi "Lujitemuovi"
                                    :se nil
                                    :en "Hardened plastic"}
   "metal"                         {:fi "Metalli"
                                    :en "Metal"
                                    :se "Metall"}
   "sheet-metal"                   {:fi "Pelti"
                                    :se nil
                                    :en "Sheet metal"}
   "glulam"                        {:fi "Liimapuu"
                                    :se nil
                                    :en "Glulam"}
   "hollow-core-slab"              {:fi "Ontelolaatta"
                                    :se nil
                                    :en "Hollow-core slab"}
   "concrete-pillar"               {:fi "Betonipilari"
                                    :se nil
                                    :en "Concrete pillar"}
   "concrete-beam"                 {:fi "Betonipalkki"
                                    :se nil
                                    :en "Concrete beam"}
   "double-t-beam"                 {:fi "TT-laatta" ; TT-palkki/TT-laatta, betonia??
                                    :se nil
                                    :en "Double-T"}
   "precast-concrete"              {:fi "Betonielementti"
                                    :se nil
                                    :en "Precast concrete"}
   "pent-roof"                     {:fi "Pulpettikatto"
                                    :se nil
                                    :en "Pent roof"}
   "felt"                          {:fi "Huopa"
                                    :se nil
                                    :en "Felt"}
   "solid-rock"                    {:fi "Kallio"
                                    :se nil
                                    :en "Solid rock"}
   "rock-dust"                     {:fi "Kivituhka"
                                    :se "Sten småbitar"
                                    :en "Rock dust"}
   "gravel"                        {:fi "Sora"
                                    :se "Grus"
                                    :en "Gravel"}
   "textile"                       {:fi "Tekstiili"
                                    :se "Textil"
                                    :en "Textile"}
   "insulator"                     {:fi "Eriste"
                                    :se nil
                                    :en "Insulator"}
   "composite-beam"                {:fi "Liittopalkki"
                                    :se nil
                                    :en "Composite beam"}
   "sawdust"                       {:fi "Sahanpuru"
                                    :se "Sågspan"
                                    :en "Sawdust"}
   "deinked-pulp"                  {:fi "Siistausmassa"
                                    :se "Returfibermassa"
                                    :en "Deinked pulp"}
   "woodchips"                     {:fi "Hake"
                                    :se "Fils"
                                    :en "Woodchips"}
   "resin"                         {:fi "Massa"
                                    :se "Kåda"
                                    :en "Resin"}})

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
                    "concrete" "glass" "solid-rock"]))

(def base-floor-structures
  (select-keys all ["concrete" "asphalt" "sand" "rock-dust" "gravel"]))

(def surface-materials
  (select-keys all ["asphalt" "concrete" "woodchips" "sand" "ceramic" "stone"
                    "rock-dust" "fiberglass" "soil" "metal" "synthetic" "grass"
                    "wood" "sawdust" "deinked-pulp" "gravel" "textile"
                    "brick-crush" "water" "artificial-turf"
                    "sand-infilled-artificial-turf" "resin"]))

(def field-surface-materials
  (merge (select-keys surface-materials ["resin" "wood" "synthetic"])
         {"carpet" {:fi "Matto"
                    :se "Matta"
                    :en "Carpet"}}))
