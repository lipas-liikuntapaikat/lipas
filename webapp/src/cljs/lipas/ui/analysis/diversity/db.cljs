(ns lipas.ui.analysis.diversity.db)

(def default-db
  {:selected-tab "analysis-area"
   :settings
   {:max-distance-m      800,
    :analysis-radius-km  5,
    :distance-mode       "route",
    :analysis-area-fcoll nil
    :categories
    [{:name       "Alamakiluistelurata",
      :factor     1,
      :type-codes [1560]}
     {:name       "Ampumaurheilu",
      :factor     1,
      :type-codes [4810 4840 4610 4620 4820 4830]}
     {:name       "Beachvolleykentta",
      :factor     1,
      :type-codes [1330]}
     {:name "Curlingrata", :factor 1, :type-codes [4210]}
     {:name       "Frisbeegolf_rata",
      :factor     1,
      :type-codes [1180]}
     {:name "Golf", :factor 1, :type-codes [1610 1630 1620]}
     {:name       "Hiihto",
      :factor     1,
      :type-codes [4630 4640 4220]}
     {:name "Jalkapallohalli", :factor 3, :type-codes [2230]}
     {:name       "Jalkapallostadion",
      :factor     1,
      :type-codes [1350]}
     {:name       "Kamppailulajien_sali",
      :factor     1,
      :type-codes [2140]}
     {:name "Keilahalli", :factor 1, :type-codes [2610]}
     {:name       "Koiraurheilu",
      :factor     1,
      :type-codes [6210 6220]}
     {:name "Koripallokentta", :factor 1, :type-codes [1310]}
     {:name "Kuntokeskus", :factor 1, :type-codes [2110]}
     {:name "Kuntosali", :factor 1, :type-codes [2120]}
     {:name "Kylpyla", :factor 1, :type-codes [3130]}
     {:name       "Lahiliikuntapaikka",
      :factor     3,
      :type-codes [1120]}
     {:name       "Laskettelun_suorituspaikka",
      :factor     1,
      :type-codes [4110]}
     {:name       "Lentopallokentta",
      :factor     1,
      :type-codes [1320]}
     {:name "Liikuntahalli", :factor 3, :type-codes [2210]}
     {:name "Liikuntasali", :factor 1, :type-codes [2150]}
     {:name       "Luistelukentta_ja_luistelureitti",
      :factor     1,
      :type-codes [1520 1550]}
     {:name "Makihyppy", :factor 1, :type-codes [4310 4320]}
     {:name "Miekkailutila", :factor 1, :type-codes [2340]}
     {:name       "Moottoriurheilu",
      :factor     1,
      :type-codes [5310 5320 5330 5340 5360]}
     {:name "Padel", :factor 1, :type-codes [1390 2295]}
     {:name "Pallokentta", :factor 1, :type-codes [1340]}
     {:name "Parkour", :factor 1, :type-codes [1140 2380]}
     {:name "Pesapallokentta", :factor 1, :type-codes [1360]}
     {:name "Petanque_halli", :factor 1, :type-codes [2290]}
     {:name       "Pikaluisteluhalli",
      :factor     1,
      :type-codes [2530]}
     {:name       "Pikaluistelurata",
      :factor     1,
      :type-codes [1540]}
     {:name       "Poytatennis",
      :factor     1,
      :type-codes [1395 2330]}
     {:name "Pyorailyalue", :factor 1, :type-codes [1160]}
     {:name "Pyorailyrata", :factor 1, :type-codes [1170]}
     {:name "Ratagolf", :factor 1, :type-codes [1640]}
     {:name       "Ratsastus",
      :factor     1,
      :type-codes [6110 6130 6120]}
     {:name       "Rullakiekkokentta",
      :factor     1,
      :type-codes [1380]}
     {:name "Salibandyhalli", :factor 1, :type-codes [2240]}
     {:name "Sisaampumarata", :factor 1, :type-codes [2360]}
     {:name       "Sisakiipeilyseina",
      :factor     1,
      :type-codes [2370]}
     {:name "Skeitti", :factor 1, :type-codes [1150 2250]}
     {:name "Soutaminen", :factor 1, :type-codes [5110 5160]}
     {:name "Squash_halli", :factor 1, :type-codes [2270]}
     {:name "Sulkapallohalli", :factor 1, :type-codes [2260]}
     {:name       "Talviuintipaikka",
      :factor     1,
      :type-codes [3240]}
     {:name "Tanssitila", :factor 1, :type-codes [2350]}
     {:name       "Tekojaa,_kaukalo,_jaahallit",
      :factor     1,
      :type-codes [2510 2520 1510 1530]}
     {:name       "Telinevoimistelutila",
      :factor     1,
      :type-codes [2320]}
     {:name "Tennis", :factor 1, :type-codes [1370 2280]}
     {:name       "Uimahalli_Maauimala",
      :factor     1,
      :type-codes [3210 3110]}
     {:name "Uimaranta", :factor 1, :type-codes [3220 3230]}
     {:name       "Ulkokiipeilypaikka",
      :factor     1,
      :type-codes [4710]}
     {:name       "Ulkokuntoilupaikka",
      :factor     1,
      :type-codes [1130]}
     {:name "Vesihiihto", :factor 1, :type-codes [5140]}
     {:name "Voimailusali", :factor 1, :type-codes [2130]}
     {:name       "Yleisurheilu_yksittaiset",
      :factor     1,
      :type-codes [1210 2310]}
     {:name       "Yleisurheilukentta",
      :factor     3,
      :type-codes [1220]}]}})

(comment
  (-> default-db :settings :categories count))
