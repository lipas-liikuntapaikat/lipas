(ns lipas.data.ice-stadiums)

(def size-categories
  {:small       {:fi "Pieni kilpahalli > 500 hlö"
                 :en nil
                 :se nil}
   :competition {:fi "Kilpahalli < 3000 hlö"
                 :en nil
                 :se nil}
   :large       {:fi "Suurhalli > 3000 hlö"
                 :en nil
                 :se nil}})

;; Lauhdelämmön pääkäyttökohde
(def condensate-energy-targets
  {:hall-heating              {:fi "Hallin lämmitys"
                               :en nil
                               :se nil}
   :other-space-heating       {:fi "Muun tilan lämmitys"
                               :en nil
                               :se nil}
   :track-heating             {:fi "Ratapohjan lämmitys"
                               :en nil
                               :se nil}
   :snow-melting              {:fi "Lumensulatus"
                               :en nil
                               :se nil}
   :maintenance-water-heating {:fi "Jäänhoitoveden lämmitys"
                               :en nil
                               :se nil}
   :service-water-heating     {:fi "Käyttöveden lämmitys"
                               :en nil
                               :se nil}})

;; Kylmäaine
(def refrigerants
  {:R134A {:fi "R134A"
           :en "R134A"
           :se "R134A"}
   :R404A {:fi "R404A"
           :en "R404A"
           :se "R404A"}
   :R407A {:fi "R407A"
           :en "R407A"
           :se "R407A"}
   :R407C {:fi "R407C"
           :en "R407C"
           :se "R407C"}
   :R717  {:fi "R717"
           :en "R717"
           :se "R717"}
   :CO2   {:fi "CO2 (hiilidioksidi)"
           :en nil
           :se nil}})

;; Kylmäliuos
(def refrigerant-solutions
  {:cacl          {:fi "Cacl"
                   :en "Cacl"
                   :se "Cacl"}
   :freezium      {:fi "Freezium"
                   :en "Freezium"
                   :se "Freezium"}
   :water-glycol  {:fi "Vesi-glykoli"
                   :en nil
                   :se nil}
   :ethanol-water {:fi "Etanoli-vesi"
                   :en nil
                   :se nil}
   :CO2           {:fi "CO2"
                   :en "CO2"
                   :se "CO2"}
   :H2ONH3        {:fi "H2O/NH3"
                   :en "H2O/NH3"
                   :se "H2O/NH3"}})

;; LTO_tyyppi
(def heat-recovery-types
  {:thermal-wheel        {:fi "Pyörivä"
                          :en nil
                          :se nil}
   :liquid-circulation   {:fi "Nestekierto"
                          :en nil
                          :se nil}
   :plate-heat-exchanger {:fi "Levysiirrin"
                          :en nil
                          :se nil}})

;; Ilmankuivaustapa
(def dryer-types
  {:munters      {:fi "Muntters"
                  :en nil
                  :se nil}
   :cooling-coil {:fi "Jäähdytyspatteri"
                  :en nil
                  :se nil}
   :none         {:fi "Ei ilmankuivausta"
                  :en nil
                  :se nil}})

;; Ilm.kuiv.käyttötapa
(def dryer-duty-types
  {:manual    {:fi "Manuaali"
               :en nil
               :se nil}
   :automatic {:fi "Automaattinen"
               :en nil
               :se nil}})

;; Lämpöpumpputyyppi
(def heat-pump-types
  {:air-source         {:fi "Ilmalämpöpumppu"
                        :en nil
                        :se nil}
   :air-water-source   {:fi "Ilma-vesilämpöpumppu"
                        :en nil
                        :se nil}
   :ground-source      {:fi "Maalämpöpumppu"
                        :en nil
                        :se nil}
   :exhaust-air-source {:fi "Poistoilmalämpöpumppu"
                        :en nil
                        :se nil}
   :none               {:fi "Ei lämpöpumppua"
                        :en nil
                        :se nil}})
