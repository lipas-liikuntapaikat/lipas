(ns lipas.data.ice-stadiums)

(def size-categories
  {"small"       {:fi "Pieni kilpahalli > 500 hlö"
                  :en "Small > 500 persons"
                  :se nil}
   "competition" {:fi "Kilpahalli < 3000 hlö"
                  :en "Competition < 3000 persons"
                  :se nil}
   "large"       {:fi "Suurhalli > 3000 hlö"
                  :en "Large > 3000 persons"
                  :se nil}})

;; Lauhdelämmön pääkäyttökohde
(def condensate-energy-targets
  {"hall-heating"              {:fi "Hallin lämmitys"
                                :en "Hall heating"
                                :se nil}
   "other-space-heating"       {:fi "Muun tilan lämmitys"
                                :en "Other heating"
                                :se nil}
   "track-heating"             {:fi "Ratapohjan lämmitys"
                                :en "Track heating"
                                :se nil}
   "snow-melting"              {:fi "Lumensulatus"
                                :en "Snow melting"
                                :se nil}
   "maintenance-water-heating" {:fi "Jäänhoitoveden lämmitys"
                                :en "Maintenance water heating"
                                :se nil}
   "service-water-heating"     {:fi "Käyttöveden lämmitys"
                                :en "Service water heating"
                                :se nil}})

;; Kylmäaine
(def refrigerants
  {"R134A" {:fi "R134A"
            :en "R134A"
            :se "R134A"}
   "R404A" {:fi "R404A"
            :en "R404A"
            :se "R404A"}
   "R407A" {:fi "R407A"
            :en "R407A"
            :se "R407A"}
   "R407C" {:fi "R407C"
            :en "R407C"
            :se "R407C"}
   "R717"  {:fi "R717"
            :en "R717"
            :se "R717"}
   "CO2"   {:fi "CO2 (hiilidioksidi)"
            :en "CO2"
            :se nil}
   "R22"   {:fi "R22"
            :en "R22"
            :se nil}})

;; Kylmäliuos
(def refrigerant-solutions
  {"cacl"          {:fi "Cacl"
                    :en "Cacl"
                    :se "Cacl"}
   "freezium"      {:fi "Freezium"
                    :en "Freezium"
                    :se "Freezium"}
   "water-glycol"  {:fi "Vesi-glykoli"
                    :en "Water-glycol"
                    :se nil}
   "ethanol-water" {:fi "Etanoli-vesi"
                    :en "Ethanol-water"
                    :se nil}
   "CO2"           {:fi "CO2"
                    :en "CO2"
                    :se "CO2"}
   "H2ONH3"        {:fi "H2O/NH3"
                    :en "H2O/NH3"
                    :se "H2O/NH3"}})

;; LTO_tyyppi
(def heat-recovery-types
  {"thermal-wheel"        {:fi "Pyörivä"
                          :en "Thermal wheel"
                          :se nil}
   "liquid-circulation"   {:fi "Nestekierto"
                          :en "Liquid circulation"
                          :se nil}
   "plate-heat-exchanger" {:fi "Levysiirrin"
                          :en "Plate heat exchanger"
                          :se nil}})

;; Ilmankuivaustapa
(def dryer-types
  {"munters"      {:fi "Muntters"
                   :en "Munters"
                   :se nil}
   "cooling-coil" {:fi "Jäähdytyspatteri"
                   :en "Cooling coil"
                   :se nil}
   "none"         {:fi "Ei ilmankuivausta"
                   :en "None"
                   :se nil}})

;; Ilm.kuiv.käyttötapa
(def dryer-duty-types
  {"manual"    {:fi "Manuaali"
                :en "Manual"
                :se nil}
   "automatic" {:fi "Automaattinen"
                :en "Automatic"
                :se nil}})

;; Lämpöpumpputyyppi
(def heat-pump-types
  {"air-source"         {:fi "Ilmalämpöpumppu"
                         :en "Air source"
                         :se nil}
   "air-water-source"   {:fi "Ilma-vesilämpöpumppu"
                         :en "Air-water source"
                         :se nil}
   "ground-source"      {:fi "Maalämpöpumppu"
                         :en "Ground source"
                         :se nil}
   "exhaust-air-source" {:fi "Poistoilmalämpöpumppu"
                         :en "Exhaust air source"
                         :se nil}
   "none"               {:fi "Ei lämpöpumppua"
                         :en "None"
                         :se nil}})

;; Jäänhoitokoneen polttoaine
(def ice-resurfacer-fuels
  {"gasoline"    {:fi "Bensiini"
                  :en "Gasoline"
                  :se nil}
   "natural-gas" {:fi "Maakaasu"
                  :en "Natural gas"
                  :se nil}
   "propane"     {:fi "Propaani"
                  :en "Propane"
                  :se nil}
   "LPG"         {:fi "Nestekaasu"
                  :en "LPG"
                  :se nil}
   "electicity"  {:fi "Sähkö"
                  :en "Electricity"
                  :se nil}})
