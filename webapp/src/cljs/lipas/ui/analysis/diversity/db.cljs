(ns lipas.ui.analysis.diversity.db)

(def default-category
  {:name "Oletus"
   :categories
   (vec
     (sort-by
       :name
       [{:name "Biljardi", :factor 1, :type-codes [2620]}
        {:name "Pulkkailu", :factor 1, :type-codes [1190]}
        {:name "Rullahiihto", :factor 1, :type-codes [4407]}
        {:name "Ilmailu", :factor 1, :type-codes [5210]}
        {:name "Luistelu", :factor 1, :type-codes [1520 1550 1510]}
        {:name "Lentopallo", :factor 1, :type-codes [1320 1330]}
        {:name "Liikuntasali", :factor 2, :type-codes [2150]}
        {:name "Ampumaurheilu",
         :factor 1,
         :type-codes [4610 4620 4810 4820 4830 2360 4840]}
        {:name "Rullakiekko", :factor 1, :type-codes [1380]}
        {:name "Lähi / ulkoilupuisto", :factor 1, :type-codes [101]}
        {:name "Taajamien kuntoradat ja kävelyreitit",
         :factor 1,
         :type-codes [4401 4403]}
        {:name "Jalkapallo", :factor 2, :type-codes [1350 2230]}
        {:name "Monitoimihalli / areena", :factor 3, :type-codes [2220]}
        {:name "Liikuntapuisto", :factor 0, :type-codes [1110]}
        {:name "Petanque", :factor 1, :type-codes [2290]}
        {:name "Pika- ja alamäkiluistelu",
         :factor 1,
         :type-codes [1540 1560 2530]}
        {:name "Golf", :factor 1, :type-codes [1610 1650 1630]}
        {:name "BMX-pyöräily", :factor 1, :type-codes [1160]}
        {:name "Liikuntahalli", :factor 3, :type-codes [2210]}
        {:name "Pesäpallo", :factor 1, :type-codes [1360]}
        {:name "Lähiliikuntapaikka", :factor 3, :type-codes [1120]}
        {:name "Telinevoimistelu", :factor 1, :type-codes [2320]}
        {:name "Padel", :factor 1, :type-codes [1390 2295]}
        {:name "Moottorikelkkailu", :factor 1, :type-codes [4421 4422]}
        {:name "Uinti",
         :factor 1,
         :type-codes [3220 3230 3240 3210 3110 3130]}
        {:name "Keilailu", :factor 1, :type-codes [2610]}
        {:name "Yleisurheilu", :factor 1, :type-codes [1210 2310]}
        {:name "Ulkokuntoilupaikka", :factor 1, :type-codes [1130]}
        {:name "Mäkihyppy", :factor 1, :type-codes [4320]}
        {:name "Kuntokeskus ja -Sali", :factor 2, :type-codes [2110 2120]}
        {:name "Vesiurheilu (reitit ja alueet)",
         :factor 1,
         :type-codes [4451 4452 5140 5120 5130 3250]}
        {:name "Tanssi", :factor 1, :type-codes [2350]}
        {:name "Parkour", :factor 1, :type-codes [1140 2380]}
        {:name "Pyöräilyreitit", :factor 1, :type-codes [4411 4412]}
        {:name "Tennis", :factor 1, :type-codes [1370 2280]}
        {:name "Ratagolf", :factor 1, :type-codes [1640]}
        {:name "Rullalautailu", :factor 1, :type-codes [1150 2250]}
        {:name "Luontoliikunta",
         :factor 2,
         :type-codes [111,110,109,103,112,4404,4405]}
        {:name "Moottoriurheilu",
         :factor 1,
         :type-codes [5310 5320 5330 5340 5360 5370 5350]}
        {:name "Pallokenttä", :factor 2, :type-codes [1340]}
        {:name "Voimailusali", :factor 1, :type-codes [2130]}
        {:name "Frisbeegolf", :factor 1, :type-codes [1180]}
        {:name "Miekkailu", :factor 1, :type-codes [2340]}
        {:name "Hiihto", :factor 1, :type-codes [4220 4640 4402 4630]}
        {:name "Sulkapallo", :factor 1, :type-codes [2260]}
        {:name "Kiipeily", :factor 1, :type-codes [2370 4710 4720]}
        {:name "Ratapyöräily", :factor 1, :type-codes [1170]}
        {:name "Suunnistus", :factor 1, :type-codes [4510]}
        {:name "Yleisurheilukenttä / stadion",
         :factor 3,
         :type-codes [1220]}
        {:name "Ratsastus", :factor 1, :type-codes [6110 6120 4430 6130 6150]}
        {:name "Curling", :factor 1, :type-codes [4210]}
        {:name "Pöytätennis", :factor 1, :type-codes [1395 2330]}
        {:name "Kamppailulajit", :factor 1, :type-codes [2140]}
        {:name "Salibandy", :factor 1, :type-codes [2240]}
        {:name "Koripallo", :factor 1, :type-codes [1310]}
        {:name "Soutu- ja melontakeskus",
         :factor 1,
         :type-codes [5110 5160 5150]}
        {:name "Laskettelu", :factor 1, :type-codes [4240 4230 4110]}
        {:name "Retkeilyn palvelut",
         :factor 1,
         :type-codes [204 201 113 203 206 202 301 302 304]}
        {:name "Koiraurheilu", :factor 1, :type-codes [6210 4440 6220 4441]}
        {:name "Jäähallit ja kaukalot",
         :factor 1,
         :type-codes [1530 2510 2520]}
        {:name "Squash", :factor 1, :type-codes [2270]}]))})

(def public-health
  {:name "Kansanterveydellinen painotus"
   :categories
   (vec
     (sort-by
       :name
       [{:name "Golf", :factor 1, :type-codes [1610 1650 1630]}
        {:name "Pulkkailu", :factor 1, :type-codes [1190]}
        {:name "Rullahiihto", :factor 1, :type-codes [4407]}
        {:name "Luistelu", :factor 1, :type-codes [1520 1550 1510]}
        {:name "Lentopallo", :factor 1, :type-codes [1320 1330]}
        {:name "Liikuntasali", :factor 2, :type-codes [2150]}
        {:name "Rullakiekko", :factor 1, :type-codes [1380]}
        {:name "Lähi / ulkoilupuisto", :factor 1, :type-codes [101]}
        {:name "Taajamien kuntoradat ja kävelyreitit",
         :factor 2,
         :type-codes [4401 4403]}
        {:name "Monitoimihalli / areena", :factor 2, :type-codes [2220]}
        {:name "Petanque", :factor 1, :type-codes [2290]}
        {:name "BMX-pyöräily", :factor 1, :type-codes [1160]}
        {:name "Liikuntahalli", :factor 2, :type-codes [2210]}
        {:name "Pesäpallo", :factor 1, :type-codes [1360]}
        {:name "Lähiliikuntapaikka", :factor 2, :type-codes [1120]}
        {:name "Telinevoimistelu", :factor 1, :type-codes [2320]}
        {:name "Padel", :factor 1, :type-codes [1390 2295]}
        {:name "Moottorikelkkailu", :factor 1, :type-codes [4421 4422]}
        {:name "Uinti", :factor 1, :type-codes [3220 3230 3240 3210 3110]}
        {:name "Keilailu", :factor 1, :type-codes [2610]}
        {:name "Yleisurheilu", :factor 1, :type-codes [1210 2310]}
        {:name "Ulkokuntoilupaikka", :factor 1, :type-codes [1130]}
        {:name "Kuntokeskus ja -Sali", :factor 2, :type-codes [2110 2120]}
        {:name "Vesiurheilu (reitit ja alueet)",
         :factor 1,
         :type-codes [4451 4452 5140 5120 5130 3250]}
        {:name "Tanssi", :factor 1, :type-codes [2350]}
        {:name "Parkour", :factor 1, :type-codes [1140 2380]}
        {:name "Pyöräilyreitit", :factor 1, :type-codes [4411 4412]}
        {:name "Tennis", :factor 1, :type-codes [1370 2280]}
        {:name "Ratagolf", :factor 1, :type-codes [1640]}
        {:name "Rullalautailu", :factor 1, :type-codes [1150 2250]}
        {:name "Luontoliikunta",
         :factor 3,
         :type-codes [111 110 109 103 112 4404 4405]}
        {:name "Pallokenttä", :factor 1, :type-codes [1340]}
        {:name "Voimailusali", :factor 1, :type-codes [2130]}
        {:name "Frisbeegolf", :factor 1, :type-codes [1180]}
        {:name "Hiihto", :factor 1, :type-codes [4220 4640 4402]}
        {:name "Sulkapallo", :factor 1, :type-codes [2260]}
        {:name "Kiipeily", :factor 1, :type-codes [2370]}
        {:name "Suunnistus", :factor 1, :type-codes [4510]}
        {:name "Yleisurheilukenttä / stadion",
         :factor 1,
         :type-codes [1220]}
        {:name "Ratsastus", :factor 1, :type-codes [6110 6120 4430 6130 6150]}
        {:name "Pöytätennis", :factor 1, :type-codes [1395 2330]}
        {:name "Kamppailulajit", :factor 1, :type-codes [2140]}
        {:name "Salibandy", :factor 1, :type-codes [2240]}
        {:name "Koripallo", :factor 1, :type-codes [1310]}
        {:name "Soutu- ja melontakeskus",
         :factor 1,
         :type-codes [5110 5160 5150]}
        {:name "Retkeilyn palvelut",
         :factor 2,
         :type-codes [204 201 113 203 206 202 301 302 304]}
        {:name "Jäähallit ja kaukalot", :factor 1, :type-codes [1530 2510]}
        {:name "Squash", :factor 1, :type-codes [2270]}]))})

(def tea-viisari
  {:name "TEA-viisari"
   :categories
   (vec
     (sort-by
       :name
       [{:name "Luistelu", :factor 1, :type-codes [1520 1550 1510]}
        {:name "Lentopallo", :factor 1, :type-codes [1320 1330]}
        {:name "Liikuntasali", :factor 1, :type-codes [2150]}
        {:name "Rullakiekko", :factor 1, :type-codes [1380]}
        {:name "Taajamien kuntoradat ja kävelyreitit",
         :factor 1,
         :type-codes [4401 4403]}
        {:name "Monitoimihalli / areena", :factor 1, :type-codes [2220]}
        {:name "Liikuntapuisto", :factor 1, :type-codes [1110]}
        {:name "Liikuntahalli", :factor 1, :type-codes [2210]}
        {:name "Lähiliikuntapaikka", :factor 1, :type-codes [1120]}
        {:name "Ulkokuntoilupaikka", :factor 1, :type-codes [1130]}
        {:name "Tennis", :factor 1, :type-codes [1370]}
        {:name "Pallokenttä", :factor 1, :type-codes [1340]}
        {:name "Salibandy", :factor 1, :type-codes [2240]}
        {:name "Koripallo", :factor 1, :type-codes [1310]}
        {:name "Jäähallit ja kaukalot", :factor 1, :type-codes [1530]}]))})

(def categories
  {:default       default-category
   :public-health public-health
   :tea-viisari   tea-viisari})

(def default-db
  {:selected-tab               "analysis-area"
   :category-presets           categories
   :category-save-dialog-open? false
   :selected-category-preset   :default
   :selected-seasonalities     #{"all-year" "summer" "winter"}
   :selected-chart-tab         "area"
   :selected-export-format     "excel"
   :settings
   {:max-distance-m      800,
    :analysis-radius-km  5,
    :distance-mode       "route",
    :analysis-area-fcoll nil
    :categories          (-> categories :default :categories)}})

(comment
  (-> default-db :settings :categories count))
