(ns lipas-ui.db
  (:require [lipas-ui.i18n :as i18n]))

(def vesivelho
  {:building
   {:construction-year 1995
    :main-designer "Ark. Marjatta Hara-Pietilä"
    :total-surface-area-m2 6000
    :total-volume-m3 35350
    :pool-room-total-area-m2 702
    :total-water-area-m2 439
    :water-slides-total-length-m 23
    :heat-sections? false
    :main-construction-materials "Betoni/tiili/laatta"
    :piled? true
    :supporting-structures "Betoni"
    :ceiling-material "Ontelolaatta"}
   :renovations
   [{:year 2016
     :comment "Remontti 2015/06-2016/11"
     :designer "?"}]
   :water-treatment
   {:ozonation? false
    :uv-treatment? true
    :activated-carbon? true
    :filtering-method "Painehiekka"
    :comment "-"}
   :heat-source "Kaukolämpö"
   :ventilation-units-count 2
   :pools
   [{:name "Pääallas"
     :temperature-c 27
     :volume-m3 490
     :area-m2 313
     :length-m 25
     :width-m 12
     :depth-min-m 1.2
     :depth-max-m 2}
    {:name "Opetusallas"
     :temperature-c 30
     :volume-m3 43
     :area-m2 43
     :length-m nil
     :width-m nil
     :depth-min-m 0.6
     :depth-max-m 1}
    {:name "Lastenallas"
     :temperature-c 32
     :volume-m3 2
     :area-m2 7
     :length-m nil
     :width-m nil
     :depth-min-m 0.2
     :depth-max-m 0.2}
    {:name "Monitoimiallas"
     :temperature-c 30
     :volume-m3 67
     :area-m2 77
     :length-m 11
     :width-m 7
     :depth-min-m 1
     :depth-max-m 1.4}
    {:name "Kylmäallas"
     :temperature-c 7
     :volume-m3 1
     :area-m2 0
     :length-m 0
     :width-m 1.12
     :depth-min-m 1.4
     :depth-max-m 1.4}]
   :slides
   [{:name "Liukumäki 1"
     :length-m 23}]
   :saunas-total-count 5
   :saunas
   [{:type "Höyrysauna" :men true :women true}
    {:type "Sauna" :men false :women true}
    {:type "Sauna" :men true :women false}
    {:type "Infrapunasauna" :men nil :women nil}]
   :other-services
   {:hydro-massage-spots-count 7
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

(def default-db
  {:logged-in? false
   :translator (i18n/->tr-fn :fi)
   :ice-stadiums {:active-tab 0}
   :swimming-pools {:active-tab 0
                    :editing vesivelho}
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
