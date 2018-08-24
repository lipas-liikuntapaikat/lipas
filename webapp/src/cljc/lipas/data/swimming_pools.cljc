(ns lipas.data.swimming-pools)

(def pool-types
  {"main-pool"         {:fi "Pääallas"
                        :se nil
                        :en "Main pool"}
   "diving-pool"       {:fi "Hyppyallas"
                        :se nil
                        :en "Diving pool"}
   "multipurpose-pool" {:fi "Monitoimiallas"
                        :se nil
                        :en "Multi-purpose pool"}
   "teaching-pool"     {:fi "Opetusallas"
                        :se nil
                        :en "Teaching pool"}
   "paddling-pool"     {:fi "Kahluuallas"
                        :se nil
                        :en "Paddling pool"}
   "childrens-pool"    {:fi "Lastenallas"
                        :se nil
                        :en "Childrens pool"}
   "cold-pool"         {:fi "Kylmäallas"
                        :se nil
                        :en "Cold pool"}
   "whirlpool-bath"    {:fi "Poreallas"
                        :se nil
                        :en "Whirlpool bath"}
   "therapy-pool"      {:fi "Terapia-allas"
                        :se nil
                        :en "Therapy pool"}
   "outdoor-pool"      {:fi "Ulkoallas"
                        :se nil
                        :en "Outdoor pool"}
   "other-pool"        {:fi "Muu allas"
                        :se nil
                        :en "Other pool"}
   "fitness-pool"      {:fi "Kuntouintiallas"
                        :se nil
                        :en "Fitness pool"}})

(def filtering-methods
  {"pressure-suction"      {:fi "Paineimu"
                            :se nil
                            :en "Pressure suction"}
   "pressure-sand"         {:fi "Painehiekka"
                            :se nil
                            :en "Pressure sand"}
   "suction-sand"          {:fi "Imuhiekka"
                            :se nil
                            :en "Suction sand"}
   "open-sand"             {:fi "Avohiekka"
                            :se nil
                            :en "Open sand"}
   "other"                 {:fi "Muu"
                            :se nil
                            :en "Other"}
   "multi-layer-filtering" {:fi "Monikerrossuodatus"
                            :se nil
                            :en "Multi-layer filtering"}
   "coal"                  {:fi "Hiili"
                            :se nil
                            :en "Coal"}
   "precipitation"         {:fi "Saostus"
                            :se nil
                            :en "Precipitation"}
   "sand"                  {:fi "Hiekka"
                            :se nil
                            :en "Sand"}
   "activated-carbon"      {:fi "Aktiivihiili"
                            :se nil
                            :en "Activated carbon"}})

(def sauna-types
  {"steam-sauna"    {:fi "Höyrysauna"
                     :se nil
                     :en "Steam sauna"}
   "sauna"          {:fi "Sauna"
                     :se nil
                     :en "Sauna"}
   "infrared-sauna" {:fi "Infrapunasauna"
                     :se nil
                     :en "Infrared sauna"}})

(def heat-sources
  {"private-power-station" {:fi "Oma voimalaitos"
                            :se nil
                            :en "Private power station"}
   "district-heating"      {:fi "Kaukolämpö"
                            :se nil
                            :en "District heating"}})
