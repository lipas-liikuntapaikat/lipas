(ns lipas.ui.db
  (:require [lipas.ui.i18n :as i18n]))

(def jaahalli

  ;;; Yleiset ;;;
  {:sports-place-id 89839
   :last-modified "2016-11-14 10:13:20.103"

   :name
   {:fi "Jyväskylän kilpajäähalli"
    :se nil
    :en nil}

   :www "www.jyvaskyla.fi"
   :email nil
   :phone-number nil
   :admin "Kunta / liikuntatoimi"
   :owner "Kunta"
   :info-fi ""

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
    :location-id 8979
    :postal-code "40700"
    :postal-office "Jyväskylä"
    :city
    {:name "Jyväskylä"
     :city-code 179}}

   ;;; Tyyppiluokitus ;;;
   :type
   {:type-code 2520,
    :name "Kilpajäähalli"
    :size-category :competition}

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
   :envelope-structure
   {:base-floor-structure "concrete" ; betoni / asfaltti / hiekka
    :insulated-exterior? true        ; Onko ulkoseinä lämpöeristetty
    :insulated-ceiling? true         ; Onko yläpohja lämpöeristetty
    :low-emissivity-coating? false}  ; Yläpohjassa matalaemissiiviteettipinnote

   ;;; Kentät ;;;
   :rinks
   [{:length-m 56
     :width-m 26}
    {:length-m 44
     :width-m 5}]

   ;;; Peruskorjaukset ;;;
   :renovations
   [{:year 2013                            ; Peruskorjausvuosi
     :comment "Asennettiin uusi ilmanvaihto."
     :main-designers "?"}]                 ; varmistetaan Erjalta

   ;; Kylmätekniikka
   :refrigeration
   {:original? true                        ; Alkuperäinen
    :individual-metering? true             ; Alamittaroitu
    :power-kw 212                          ; Kylmäkoneen teho (kW)
    :condensate-energy-recycling? true     ; Lauhde-energia hyötykäytetty
    :condensate-energy-main-target         ; Lauhdelämmön pääkäyttökohde
    "Jäänhoito/käyttöveden lämmitys"
    :refrigerant "R404A"                   ; Kylmäaine
    :refrigerant-amount-kg 100             ; Kylmäaineen määrä
    :refrigerant-solution "Vesi-glykoli"   ; Kylmäliuos
    :refrigerant-solution-amount-l 7000 }  ; Kylmäliuos 0-30000

   ;; Olosuhteet
   :conditions
   {:air-humidity                         ; Ilman suhteellisen kosteuden
    {:min 50                              ;  vaihteluväli  50-70
     :max 60}
    :ice-surface-temperature-c -3         ; Jään pinnan lämpötila -3 -6
    :skating-area-temperature-c 5         ; Luistelualueen lämpötila 1m kork.
                                          ;  harjoitushalleissa  +5 +9
                                          ;  kilpahalleissa +9 +12
    :stand-temperature-c 10}              ; Katsomon lämpötila (0-50)
                                          ;  ottelun aikana tavoiteltu
                                          ;  keskilämpötila

   ;; Jäänhoito (vuosittain muuttuvat)
   :ice-maintenance
   {:daily-maintenance-count-week-days 8  ; Jäähoitokerrat arkipäivinä
    :daily-maintenance-count-weekends 12  ; Jäähoitokerrat viikonlppuina
    :average-water-consumption-l 300      ; Keskimääräinen jäänhoitoon käytetty
                                          ;  vesimäärä/jäänajo (ltr)
    :maintenance-water-temperature-c 35   ; Jäähoitoveden lämpötila (tavoite +40)
    :ice-average-thickness-mm 20          ; Jään keskipaksuus mm

    ;; --> energiankulutukseen liittyvää lisätietoa kerätään vuositasolla
    :daily-open-hours 15                  ; Aukiolo päivässä (tuntia/pv) ???
    :open-months 12}                      ; Aukiolo vuodessa (kk:a/vuosi) ???

   ;; Hallin ilmanvaihto                          ; LTO=lämmöntalteenotto?
   :ventilation
   {:heat-recovery-type "Levysiirrin"             ; LTO_tyyppi
    :heat-recovery-thermal-efficiency-percent 10  ; LTO_hyötysuhde
    :dryer-type "Jäähdytyspatteri"                ; Ilmankuivaustapa
    :dryer-duty-type "??"                         ; Ilm.kuiv.käyttötapa
    :heat-pump-type "None"}                       ; Lämpöpumpputyyppi
   })

(def vesivelho
  {:building
   {:construction-year 1995
    :main-designers "Ark. Marjatta Hara-Pietilä"
    :total-surface-area-m2 6000
    :total-volume-m3 35350
    :pool-room-total-area-m2 702
    :total-water-area-m2 439
    :heat-sections? false
    :main-construction-materials "Betoni/tiili/laatta"
    :piled? true
    :supporting-structures-description "Betoni"
    :ceiling-description "Ontelolaatta"
    :staff-count 0
    :seating-capacity 0
    :heat-source "Kaukolämpö"
    :ventilation-units-count 2}
   :renovations
   [{:year 2016
     :comment "Remontti 2015/06-2016/11"
     :main-designers "?"}]
   :water-treatment
   {:ozonation? false
    :uv-treatment? true
    :activated-carbon? true
    :filtering-method "Painehiekka"
    :comment "-"}
   :pools
   [{:type "Pääallas"
     :temperature-c 27
     :volume-m3 490
     :area-m2 313
     :length-m 25
     :width-m 12
     :depth-min-m 1.2
     :depth-max-m 2}
    {:type "Opetusallas"
     :temperature-c 30
     :volume-m3 43
     :area-m2 43
     :length-m nil
     :width-m nil
     :depth-min-m 0.6
     :depth-max-m 1}
    {:type "Lastenallas"
     :temperature-c 32
     :volume-m3 2
     :area-m2 7
     :length-m nil
     :width-m nil
     :depth-min-m 0.2
     :depth-max-m 0.2}
    {:type "Monitoimiallas"
     :temperature-c 30
     :volume-m3 67
     :area-m2 77
     :length-m 11
     :width-m 7
     :depth-min-m 1
     :depth-max-m 1.4}
    {:type "Kylmäallas"
     :temperature-c 7
     :volume-m3 1
     :area-m2 0
     :length-m 0
     :width-m 1.12
     :depth-min-m 1.4
     :depth-max-m 1.4}]
   :slides
   [{:length-m 23
     :structure "Lujitemuovi"}]
   :saunas
   [{:type "Höyrysauna" :men true :women true}
    {:type "Sauna" :men false :women true}
    {:type "Sauna" :men true :women false}
    {:type "Infrapunasauna" :men nil :women nil}]
   :other-services
   {:hydro-massage-spots-count 7
    :hydro-neck-massage-spots-count 0
    :platforms-1m-count 0
    :platforms-3m-count 0
    :platforms-5m-count 0
    :platforms-7.5m-count 0
    :platforms-10m-count 0
    :kiosk? true}
   :facilities
   {:showers-men-count 12
    :showers-women-count 12
    :lockers-men-count 94
    :lockers-women-count 100}
   :info-fi "1=kunto- ja kilpauintiallas, 2=lämminvesiallas,
   3=monitoimiallas, 4=lastenallas. Monitoimialtaissa porealtaat."
   :email "uima[at]aanekoski.fi"
   :admin "Kunta / liikuntatoimi"
   :www "www.aanekoski.fi"
   :name {:fi "Äänekosken uimahalli VesiVelho"}
   :type
   {:typeCode 3110,
    :name "Uimahalli"}
   :last-modified "2016-11-14 10:13:20.103"
   :sports-place-id 506032
   :phone-number "020 632 3212"
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
    {:name "Äänekoski"
     :city-code 992}},
   :owner "Kunta"})

(defn ->indexed-map [coll]
  (into {} (map-indexed (fn [idx item]
                          [idx
                           (if (map? item)
                             (assoc item :id idx)
                             item)])
                        coll)))

(def default-db
  {:logged-in? false
   :translator (i18n/->tr-fn :fi)

   ;; Ice stadiums
   :ice-stadiums
   {:active-tab 0
    :editing (-> jaahalli
                 (update-in [:renovations] ->indexed-map)
                 (update-in [:rinks] ->indexed-map))
    :dialogs
    {:renovation
     {:open? false
      :data {:year (.getFullYear (js/Date.))
             :comment ""
             :designer ""}}
     :rink
     {:open? false
      :data {:width-m ""
             :length-m ""}}}}

   ;; Swimming pools
   :swimming-pools
   {:active-tab 0
    :editing (-> vesivelho
                 (update-in [:pools] ->indexed-map)
                 (update-in [:renovations] ->indexed-map)
                 (update-in [:saunas] ->indexed-map)
                 (update-in [:slides] ->indexed-map))
    :dialogs
    {:renovation
     {:open? false
      :data {:year (.getFullYear (js/Date.))
             :comment ""
             :designer ""}}
     :pool
     {:open? false
      :data {:name ""
             :temperature-c nil
             :volume-m3 nil
             :area-m2 nil
             :length-m nil
             :width-m nil
             :depth-min-m nil
             :depth-max-m nil
             :structure ""}}
     :slide
     {:open? false}
     :sauna
     {:open? false
      :data
      {:name ""
       :women false
       :men false}}}}

   ;; User
   :user
   {:login-form
    {:username ""
     :password ""}
    :registration-form
    {:email ""
     :password ""
     :username ""
     :user-data
     {:firstname ""
      :lastname ""
      :permissions-request ""}}}})
