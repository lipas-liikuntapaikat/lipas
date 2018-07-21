(ns lipas.seed
  (:require [lipas.backend.system :as backend]
            [lipas.backend.core :as core]
            [environ.core :refer [env]]
            [lipas.schema.core]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [taoensso.timbre :as log]))

(def jh-demo
  {:email    "jh@lipas.fi"
   :username "jhdemo"
   :password "jaahalli"
   :permissions
   {:sports-sites [89839]}
   :user-data
   {:firstname           "Jää"
    :lastname            "Halli"
    :permissions-request "Haluan oikeudet päivittää Jyväskylän kilpajäähallin tietoja."}})

(def uh-demo
  {:email    "uh@lipas.fi"
   :username "uhdemo"
   :password "uimahalli"
   :permissions
   {:sports-sites [506032]}
   :user-data
   {:firstname           "Uima"
    :lastname            "Halli"
    :permissions-request "Haluan oikeudet päivittää Äänekosken Vesivelhon tietoja."}})

(def admin
  {:email "admin@lipas.fi"
   :username "admin"
   :password (:admin-password env)
   :permissions
   {:admin? true}
   :user-data
   {:firstname "Lipas"
    :lastname "Admin"}})

(def jaahalli-2016

  ;;; Yleiset ;;;
  {:lipas-id 89839
   :event-date "2016-11-14T10:13:20.103Z"
   :status "active"

   :name "Jyväskylän kilpajäähalli"
   :marketing-name "Synergia areena"

   :admin "city-sports"
   :owner "city"

   :www "www.jyvaskyla.fi"
   ;; :email nil
   ;; :phone-number nil

   ;;; Sijainti ;;;
   :location
   {:address "Rautpohjankatu 10"
    :geometries
    {:type "FeatureCollection",
     :features
     [{:type "Feature"
       :geometry
       {:coordinates
        [25.7221976798387 62.2373183577408]
        :type "Point"}
       :properties {:point-id 61503}}]}
    :location-id 89839
    :postal-code "40700"
    :postal-office "Jyväskylä"
    :city
    {:city-code 179
     :neighborhood "Hippos"}}

   ;;; Tyyppiluokitus ;;;
   :type
   {:type-code 2520
    :size-category "competition"}

         ; PORTAALI                      LIPAS
         ; kilpahallit < 3000            --> 2520 kilpajäählli
         ; pienet kilpahallit < 500      --> 2520 kilpajäähalli
         ; harjoitushallit               --> 2510 harjoitusjäähalli
         ; suurhallit > 3000             --> 2520 kilpajäähalli
         ; katetut tekojäähallit (pois?) --> 2510 harjoistusjäähalli

   ;;; Rakennus ;;;
   :building
   {:construction-year 2008          ; Rakennusvuosi
    :total-volume-m3 2000            ; Hallin koko tilavuus (m3)
    :total-surface-area-m2 1500      ; Hallin koko pinta-ala (m2)
    :total-ice-surface-area-m2 800   ; Jään pinta-ala yhteensä (m2)
    :seating-capacity 300            ; Katsomopaikat (kpl)
    }

   ;;; Vaipan rakenne ;;;
   :envelope
   {:base-floor-structure "concrete"  ; betoni / asfaltti / hiekka
    :insulated-exterior? true        ; Onko ulkoseinä lämpöeristetty
    :insulated-ceiling? true         ; Onko yläpohja lämpöeristetty
    :low-emissivity-coating? false}  ; Yläpohjassa matalaemissiiviteettipinnote

   ;;; Kentät ;;;
   :rinks
   [{:length-m 56
     :width-m 26}
    {:length-m 44
     :width-m 5}]

   ;;; Kylmätekniikka
   :refrigeration
   {:original? true                        ; Alkuperäinen
    :individual-metering? true             ; Alamittaroitu
    :power-kw 212                          ; Kylmäkoneen teho (kW)
    :condensate-energy-recycling? true     ; Lauhde-energia hyötykäytetty
    :condensate-energy-main-targets        ; Lauhdelämmön pääkäyttökohde
    ["maintenance-water-heating"
     "service-water-heating"]
    :refrigerant "R404A"                    ; Kylmäaine
    :refrigerant-amount-kg 100             ; Kylmäaineen määrä
    :refrigerant-solution "water-glycol"    ; Kylmäliuos
    :refrigerant-solution-amount-l 7000}   ; Kylmäliuos 0-30000

   ;;; Olosuhteet
   :conditions
   {;; --> energiankulutukseen liittyvää lisätietoa kerätään vuositasolla
    :daily-open-hours 15                  ; Aukiolo päivässä (tuntia/pv) ???
    :open-months 12                       ; Aukiolo vuodessa (kk/vuosi) ???
    :air-humidity-min 50                         ; Ilman suhteellisen kosteuden
    :air-humidity-max 60
    :ice-surface-temperature-c -3         ; Jään pinnan lämpötila -3 -6
    :skating-area-temperature-c 5         ; Luistelualueen lämpötila 1m kork.
                                          ;  harjoitushalleissa  +5 +9
                                          ;  kilpahalleissa +9 +12
    :stand-temperature-c 10             ; Katsomon lämpötila (0-50)
                                        ;  ottelun aikana tavoiteltu
                                        ;  keskilämpötila

    ;;; Jäänhoito (vuosittain muuttuvat)

    :daily-maintenances-week-days 8  ; Jäähoitokerrat arkipäivinä
    :daily-maintenances-weekends 12  ; Jäähoitokerrat viikonlppuina
    :average-water-consumption-l 300      ; Keskimääräinen jäänhoitoon käytetty
                                        ;  vesimäärä/jäänajo (ltr)
    :maintenance-water-temperature-c 35   ; Jäähoitoveden lämpötila (tavoite +40)
    :ice-average-thickness-mm 20          ; Jään keskipaksuus mm
    }



   ;;; Hallin ilmanvaihto                          ; LTO=lämmöntalteenotto?
   :ventilation
   {:heat-recovery-type "plate-heat-exchanger"     ; LTO_tyyppi
    :heat-recovery-efficiency 10                  ; LTO_hyötysuhde
    :dryer-type "cooling-coil"                     ; Ilmankuivaustapa
    :dryer-duty-type "manual"                      ; Ilm.kuiv.käyttötapa
    :heat-pump-type "none"}                        ; Lämpöpumpputyyppi

   ;; Energiankulutus
   :energy-consumption
   {:electricity-mwh 1795
    :heat-mwh 1159
    :water-m3 8338}})

(def jaahalli-2017
  (assoc jaahalli-2016
         :event-date          "2017-11-14T10:13:20.103Z"
         :marketing-name     "Lähi-Tapoola areena"
         :owner "unknown"
         :energy-consumption {:electricity-mwh 1500
                              :heat-mwh        1164
                              :water-m3        11032}))

(def vesivelho-2012
  {
   ;;; Yleiset ;;;
   :lipas-id 506032
   :event-date "2012-11-14T10:13:20.103Z"
   :status "active"

   :name "Äänekosken uimahalli"
   :marketing-name "Vesivelho"

   :admin "city-sports"
   :owner "city"

   :www "www.aanekoski.fi"
   :email "uima@aanekoski.fi"
   :phone-number "020 632 3212"
   :info-fi "1=kunto- ja kilpauintiallas, 2=lämminvesiallas,
   3=monitoimiallas, 4=lastenallas. Monitoimialtaissa porealtaat."

   ;;; Sijainti ;;;
   :location
   {:address "Koulumäenkatu 2"
    :geometries
    {:type "FeatureCollection",
     :features
     [{:type "Feature"
       :geometry
       {:coordinates
        [25.7237585961864 62.6078965384038]
        :type "Point"}
       :properties {:point-id 61503}}]}
    :location-id 8979
    :postal-code "44100"
    :postal-office "ÄÄNEKOSKI"
    :city
    {:city-code 992}}

   ;;; Tyyppiluokitus ;;;
   :type
   {:type-code 3110}

   ;;; Rakennus ;;;
   :building
   {:construction-year 1995
    :main-designers "Ark. Marjatta Hara-Pietilä"
    :total-surface-area-m2 6000
    :total-volume-m3 35350
    :pool-room-total-area-m2 702
    :total-water-area-m2 439
    :heat-sections? false
    :main-construction-materials ["concrete" "brick" "tile"]
    :piled? true
    :supporting-structures ["concrete"]
    :ceiling-structures ["hollow-core-slab"]
    :staff-count 0
    :seating-capacity 0
    :heat-source "district-heating"
    :ventilation-units-count 2}

   ;;; Vedekäsittely ;;;
   :water-treatment
   {:ozonation? false
    :uv-treatment? true
    :activated-carbon? true
    :filtering-methods ["pressure-sand"]
    :comment "-"}

   ;;; Altaat ;;;
   :pools
   [{:type "main-pool"
     :temperature-c 27
     :volume-m3 490
     :area-m2 313
     :length-m 25
     :width-m 12
     :depth-min-m 1.2
     :depth-max-m 2}
    {:type "teaching-pool"
     :temperature-c 30
     :volume-m3 43
     :area-m2 43
     ;; :length-m nil
     ;; :width-m nil
     :depth-min-m 0.6
     :depth-max-m 1}
    {:type "childrens-pool"
     :temperature-c 32
     :volume-m3 2
     :area-m2 7
     ;; :length-m nil
     ;; :width-m nil
     :depth-min-m 0.2
     :depth-max-m 0.2}
    {:type "multipurpose-pool"
     :temperature-c 30
     :volume-m3 67
     :area-m2 77
     :length-m 11
     :width-m 7
     :depth-min-m 1
     :depth-max-m 1.4}
    {:type "cold-pool"
     :structure "hardened-plastic"
     :temperature-c 7
     :volume-m3 1
     :area-m2 0
     :length-m 0
     :width-m 1.12
     :depth-min-m 1.4
     :depth-max-m 1.4}]

   ;;; Liukumäet ;;;
   :slides
   [{:length-m 23
     :structure "hardened-plastic"}]

   ;;; Saunat ;;;
   :saunas
   [{:type "steam-sauna" :men? true :women? true}
    {:type "sauna" :men? false :women? true}
    {:type "sauna" :men? true :women? false}
    {:type "infrared-sauna"}]

   ;;; Muut palvelut ;;;
   :facilities
   {:hydro-massage-spots-count 7
    :hydro-neck-massage-spots-count 0
    :platforms-1m-count 0
    :platforms-3m-count 0
    :platforms-5m-count 0
    :platforms-7.5m-count 0
    :platforms-10m-count 0
    :kiosk? true
    :showers-men-count 12
    :showers-women-count 12
    :lockers-men-count 94
    :lockers-women-count 100}

   ;; Kävijämäärät
   :visitors
   {:total-count 69835}

   ;; Energiankulutus
   :energy-consumption
   {:electricity-mwh 643
    :heat-mwh 1298
    :water-m3 9221}})

(def vesivelho-2013
  (assoc vesivelho-2012
         :event-date "2013-01-01T00:00:00.000Z"
         :visitors {:total-count 67216}
         :energy-consumption {:electricity-mwh 0
                              :heat-mwh 0
                              :water-m3 0}))

(def vesivelho-2014
  (assoc vesivelho-2013
         :event-date "2014-01-01T00:00:00.000Z"
         :visitors {:total-count 66529}
         :energy-consumption {:electricity-mwh 664
                              :heat-mwh 0
                              :water-m3 0}))

(def vesivelho-2015
  (assoc vesivelho-2014
         :event-date "2015-01-01T00:00:00.000Z"
         :visitors {:total-count 34002}
         :energy-consumption {:electricity-mwh 0
                              :heat-mwh 0
                              :water-m3 0}))

(def vesivelho-2016
  (assoc vesivelho-2015
         :event-date "2016-01-01T00:00:00.000Z"
         :visitors {:total-count 8793}
         :energy-consumption {:electricity-mwh 0
                              :heat-mwh 0
                              :water-m3 0}))

(def vesivelho-2017
  (assoc vesivelho-2016
         :event-date "2017-01-01T00:00:00.000Z"
         :visitors {:total-count 55648}
         :energy-consumption {:electricity-mwh 818
                              :heat-mwh 0
                              :water-m3 8573}))

(defn seed-demo-users! [db]
  (log/info "Seeding demo users 'admin', 'jhdemo' and 'uhdemo'")
  (core/add-user! db admin)
  (core/add-user! db jh-demo)
  (core/add-user! db uh-demo)
  (log/info "Seeding done!"))

(defn seed-demo-sites! [db user]
  (log/info "Seeding demo sites 'Jyväskylän Jäähalli' and 'Vesivelho'")
  (core/upsert-sports-site! db user jaahalli-2016)
  (core/upsert-sports-site! db user jaahalli-2017)
  (core/upsert-sports-site! db user vesivelho-2012)
  (core/upsert-sports-site! db user vesivelho-2013)
  (core/upsert-sports-site! db user vesivelho-2014)
  (core/upsert-sports-site! db user vesivelho-2015)
  (core/upsert-sports-site! db user vesivelho-2016)
  (core/upsert-sports-site! db user vesivelho-2017)
  (log/info "Seeding done!"))

(defn seed-sports-sites! [db user spec n]
  (log/info "Seeding" n "generated" spec)
  (doseq [_ (range n)]
    (core/upsert-sports-site! db user (gen/generate (s/gen spec))))
  (log/info "Seeding done!"))

(defn -main [& args]
  (let [config (select-keys backend/default-config [:db])
        system (backend/start-system! config)
        db     (:db system)]
    (try
      (seed-demo-users! db)
      (let [admin (core/get-user db "admin@lipas.fi")]
        (seed-demo-sites! db admin)
        (seed-sports-sites! db admin :lipas.sports-site/ice-stadium 5)
        (seed-sports-sites! db admin :lipas.sports-site/swimming-pool 5))
      (finally (backend/stop-system! system)))))
