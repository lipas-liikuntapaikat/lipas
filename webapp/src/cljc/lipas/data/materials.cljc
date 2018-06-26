(ns lipas.data.materials)

;;;; Contains also structures

(def all
  {:asphalt          {:fi "Asfaltti"
                      :se nil
                      :en nil}
   :sand             {:fi "Hiekka"
                      :se nil
                      :en nil}
   :concrete         {:fi "Betoni"
                      :se nil
                      :en nil}
   :brick            {:fi "Tiili"
                      :se nil
                      :en nil}
   :tile             {:fi "Laatta"
                      :se nil
                      :en nil}
   :steel            {:fi "Teräs"
                      :se nil
                      :en nil}
   :wood             {:fi "Puu"
                      :se nil
                      :en nil}
   :glass            {:fi "Lasi"
                      :se nil
                      :en nil}
   :hardened-plastic {:fi "Lujitemuovi"
                      :se nil
                      :en nil}
   :sheet-metal      {:fi "Pelti"
                      :se nil
                      :en nil}
   :glulam           {:fi "Liimapuu"
                      :se nil
                      :en nil}
   :hollow-core-slab {:fi "Ontelolaatta"
                      :se nil
                      :en nil}
   :concrete-pillar  {:fi "Betonipilari"
                      :se nil
                      :en nil}
   :concrete-beam    {:fi "Betonipalkki"
                      :se nil
                      :en nil}
   :double-t-beam    {:fi "TT-palkki" ; TT-palkki/TT-laatta, betonia??
                      :se nil
                      :en nil}
   :precast-concrete {:fi "Betonielementti"
                      :se nil
                      :en nil}
   :pent-roof        {:fi "Pulpettikatto"
                      :se nil
                      :en nil}
   :felt             {:fi "Huopa"
                      :se nil
                      :en nil}
   :solid-rock       {:fi "Kallio"
                      :se nil
                      :en nil}
   :insulator        {:fi "Eriste"
                      :se nil
                      :en nil}
   :composite-beam   {:fi "Liittopalkki"
                      :se nil
                      :en nil}})

(def building-materials
  (select-keys all [:concrete :brick :tile :steel :wood :glass :sheet-metal
                    :solid-rock :concrete-pillar]))

(def slide-structures
  (select-keys all [:concrete :steel :hardened-plastic]))

(def pool-structures
  (select-keys all [:concrete :steel :hardened-plastic]))

(def supporting-structures
  (select-keys all [:concrete :wood :concrete-pillar :concrete-beam :solid-rock
                    :precast-concrete]))

(def ceiling-structures
  (select-keys all [:wood :hollow-core-slab :steel :insulator :double-t-beam
                    :concrete :glulam :pent-roof :felt :composite-beam]))

(def base-floor-structures
  (select-keys all [:concrete :asphalt :sand]))
