(ns lipas.data.swimming-pools)

(def pool-types
  {:main-pool         {:fi "Pääallas"
                       :se nil
                       :en nil}
   :diving-pool       {:fi "Hyppyallas"
                       :se nil
                       :en nil}
   :multipurpose-pool {:fi "Monitoimiallas"
                       :se nil
                       :en nil}
   :teaching-pool     {:fi "Opetusallas"
                       :se nil
                       :en nil}
   :paddling-pool     {:fi "Kahluuallas"
                       :se nil
                       :en nil}
   :childrens-pool    {:fi "Lastenallas"
                       :se nil
                       :en nil}
   :cold-pool         {:fi "Kylmäallas"
                       :se nil
                       :en nil}
   :whirlpool-bath    {:fi "Poreallas"
                       :se nil
                       :en nil}
   :therapy-pool      {:fi "Terapia-allas"
                       :se nil
                       :en nil}})

(def filtering-methods
  {:pressure-suction      {:fi "Paineimu"
                           :se nil
                           :en nil}
   :pressure-sand         {:fi "Painehiekka "
                           :se nil
                           :en nil}
   :suction-sand          {:fi "Imuhiekka"
                           :se nil
                           :en nil}
   :open-sand             {:fi "Avohiekka"
                           :se nil
                           :en nil}
   :other                 {:fi "Muu"
                           :se nil
                           :en nil}
   :multi-layer-filtering {:fi "Monikerrossuodatus"
                           :se nil
                           :en nil}
   :coal                  {:fi "Hiili"
                           :se nil
                           :en nil}
   :precipitation         {:fi "Saostus"
                           :se nil
                           :en nil}
   :activated-carbon      {:fi "Aktiivihiili"
                           :se nil
                           :en nil}})

(def sauna-types
  {:steam-sauna    {:fi "Höyrysauna"
                    :se nil
                    :en nil}
   :sauna          {:fi "Sauna"
                    :se nil
                    :en nil}
   :infrared-sauna {:fi "Infrapunasauna"
                    :se nil
                    :en nil}})

(def heat-sources
  {:private-power-station {:fi "Oma voimalaitos"
                           :se nil
                           :en nil}
   :district-heating      {:fi "Kaukolämpö"
                           :se nil
                           :en nil}})
