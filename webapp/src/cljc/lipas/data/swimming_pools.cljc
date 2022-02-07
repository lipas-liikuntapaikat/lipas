(ns lipas.data.swimming-pools)

(def pool-types
  {"main-pool"         {:fi "Pääallas"
                        :se "Huvudbassäng"
                        :en "Main pool"}
   "diving-pool"       {:fi "Hyppyallas"
                        :se "Hoppbassäng"
                        :en "Diving pool"}
   "multipurpose-pool" {:fi "Monitoimiallas"
                        :se "Multifunktionell bassäng"
                        :en "Multi-purpose pool"}
   "teaching-pool"     {:fi "Opetusallas"
                        :se "Undervisningsbassäng"
                        :en "Teaching pool"}
   "paddling-pool"     {:fi "Kahluuallas"
                        :se "Plaskbassäng"
                        :en "Paddling pool"}
   "childrens-pool"    {:fi "Lastenallas"
                        :se "Barnbassäng"
                        :en "Children's pool"}
   "cold-pool"         {:fi "Kylmäallas"
                        :se "Kallvattensbassäng"
                        :en "Cold pool"}
   "whirlpool-bath"    {:fi "Poreallas"
                        :se "Bubbelbad"
                        :en "Whirlpool bath"}
   "therapy-pool"      {:fi "Terapia-allas"
                        :se "Rehabiliteringsbassäng"
                        :en "Therapy pool"}
   "other-pool"        {:fi "Muu allas"
                        :se "Annan bassäng"
                        :en "Other pool"}
   "fitness-pool"      {:fi "Kuntouintiallas"
                        :se "Motionsbassäng"
                        :en "Fitness pool"}})

(def filtering-methods
  {"pressure-sand"         {:fi "Painehiekka"
                            :se nil
                            :en "Pressure sand"}
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
   "activated-carbon"      {:fi "Aktiivihiili"
                            :se nil
                            :en "Activated carbon"}
   "membrane-filtration"   {:fi "Kalvosuodatus"
                            :se nil
                            :en "Membrane filtration"}})

(def sauna-types
  {"steam-sauna"    {:fi "Höyrysauna"
                     :se nil
                     :en "Steam sauna"}
   "sauna"          {:fi "Sauna"
                     :se nil
                     :en "Sauna"}
   "infrared-sauna" {:fi "Infrapunasauna"
                     :se nil
                     :en "Infrared sauna"}
   "other-sauna"    {:fi "Muu sauna"
                     :se nil
                     :en "Other sauna"}})

(def heat-sources
  {"private-power-station" {:fi "Oma voimalaitos"
                            :se nil
                            :en "Private power station"}
   "district-heating"      {:fi "Kaukolämpö"
                            :se nil
                            :en "District heating"}})

(def accessibility
  {"lift"            {:fi "Allasnostin"
                      :en "Pool lift"}
   "mobile-lift"     {:fi "Siirrettävä allasnostin"
                      :en "Mobile pool lift"}
   "slope"           {:fi "Luiska"
                      :en "Slope"}
   "low-rise-stairs" {:fi "Loivat portaat"
                      :en "Low rise stairs"}})
