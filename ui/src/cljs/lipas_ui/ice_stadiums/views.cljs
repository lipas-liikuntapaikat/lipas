(ns lipas-ui.ice-stadiums.views
  (:require [lipas-ui.mui :as mui]
            [lipas-ui.ice-stadiums.subs :as subs]
            [lipas-ui.ice-stadiums.events :as events]
            [lipas-ui.mui-icons :as mui-icons]
            [re-frame.core :as re-frame]
            [reagent.core :as r]))

(def sample {:time-period {:start "2018-01-01"
                           :end "2018-31-12"}
             :author {:name "Kissi Kissinen"
                      :email "kissa@koira.fi"}
             :stadium {:name "Kissalan jäähalli"
                       :construction-year 2008
                       ;; Hallin koko tilavuus (m3)
                       :total-volume-m3 2000
                       ;; Hallin koko pinta-ala (m2)
                       :total-surface-area-m2 1500
                       ;; Jään pinta-ala yhteensä (m2)
                       :total-ice-surface-area-m2 800
                       ;; Katsomopaikat (kpl)
                       :stand-capacity-person 300
                       ;; Aukiolo päivässä (tuntia/pv)
                       :daily-open-hours 15
                       ;; Aukiolo vuodessa (kk:a/vuosi)
                       :open-months 12}
             :rinks [{:name "Kenttä 1"
                      :length-m 56
                      :width-m 26}
                     {:name "Kenttä 2"
                      :length-m 44
                      :width-m 5}]
             :renovations [{:year 2013
                            :comment "Asennettiin uusi ilmanvaihto."}]
             :envelope-structure {;;Onko ulkoseinä lämpöeristetty
                                  :insulated-exterior? true
                                  ;; Onko yläpohja lämpöeristetty
                                  :insulated-ceiling? true
                                  ;; Onko yläpohjassa
                                  ;; matalaemissiiviteettipinnote
                                  :low-emissivity-coating? false}
             :refrigeration {;; Alkuperäinen
                             :original? true
                             ;; Alamittaroitu
                             :individual-metering? true
                             ;; Kylmäkoneen teho (kW)
                             :power-kw 212
                             ;; Lauhde-energia hyötykäytetty
                             :condensate-energy-recycling? true
                             ;; Lauhdelämmön pääkäyttökohde
                             :condensate-energy-main-target "Pukukopin lämmitys"
                             ;; Kylmäaine
                             :refrigerant "R404A"
                             ;; Kylmäliuos
                             :refrigerant-solution "Vesi-glykoli"}

             :conditions {;; Ilman suhteellisen kosteuden vaihteluväli
                          :air-relative-humidity-percentage {:min 50
                                                             :max 60}
                          ;; Jään pinnan lämpötila
                          :ice-surface-temperature-c -3
                          ;; Luistelualueen lämpötila
                          ;; 1 m:n korkeudessa jään pinnasta
                          :skating-area-temperature-c 5
                          ;; Katsomon lämpötila
                          :stand-temperature-c 10}
             :ice-maintenance {;; Jäähoitokerrat arkipäivinä
                               :daily-maintenance-count-week-days 8
                               ;; Jäähoitokerrat viikonlppuina
                               :daily-maintenance-count-weekends 12
                               ;; Keskimääräinen jäänhoitoon käytetty
                               ;; vesimäärä/jäänajo (ltr)
                               :average-water-consumption-l 300
                               ;; Jäähoitoveden lämpötila (tavoite +40)
                               :maintenance-water-temperature-c 35
                               ;; Jään keskipaksuus mm
                               :ice-average-thickness-mm 20}
             :ventilation {;; LTO_tyyppi (lämmöntalteenotto)
                           :heat-recovery-type "Levysiirrin"
                           ;; LTO_hyötysuhde
                           :heat-recovery-thermal-efficiency-percent 10
                           ;; Ilmankuivaustapa
                           :dryer-type "Jäähdytyspatteri"
                           ;; Ilm.kuiv.käyttötapa
                           :dryer-duty-type "??"
                           ;; Lämpöpumpputyyppi
                           :heat-pump-type "None"}
             :energy-consumption {;; Sähkön kulutus (kWh)
                                  :electricity-kwh 930320
                                  ;; Lämmön kulutus (kWh)
                                  :heat-kwh 310470
                                  ;; Veden kulutus (m3)
                                  :water-m3 2600
                                  :comment "Paljon meni"}
             :usage {;; Katsojat (hlö)
                     :spectators-person 12000
                     ;; Jään käyttäjät (hlö)
                     :skaters-person 1400}})

(defn info-tab [url]
  [mui/grid {:container true}
   [mui/grid {:item true :xs 12}
    [:iframe {:src url
              :style {:min-height "800px" :width "100%"}}]]])

(defn energy-tab []
  [mui/grid {:container true}
   [mui/grid {:item true :xs 12}
    [mui/typography "Tänne .pdf dokumentti"]]])

(defn form-tab []
  [mui/form-control
   [mui/form-label {:component "legend"} "Kulutustiedot"]
   [mui/form-group
    [mui/text-field {:select true
                     :label "Valitse halli"
                     :value "Halli 1"}
     (for [hall ["Halli 1" "Halli 2" "Halli 3"]]
       [mui/menu-item {:key hall :value hall} hall])]
    [mui/text-field {:label "Vuosi"
                     :type "number"
                     :select true
                     :value 2018}
     (for [year (range 2000 2019)]
       [mui/menu-item {:key year :value year} year])]
    [mui/text-field {:label "Sähkö"
                     :type "number"
                     :Input-props
                     {:end-adornment
                      (r/as-element
                       [mui/input-adornment "MWh"])}}]
    [mui/text-field {:label "Lämpö (ostettu)"
                     :type "number"
                     :Input-props
                     {:end-adornment
                      (r/as-element
                       [mui/input-adornment "MWh"])}}]
    [mui/text-field {:label "Vesi"
                     :type "number"
                     :Input-props
                     {:end-adornment
                      (r/as-element
                       [mui/input-adornment "m³"])}}]
    [mui/button {:color "secondary" :size "large"} "Tallenna"]]])

(defn change-tab [_ value]
  (re-frame/dispatch [::events/set-active-tab value]))

(defn create-panel [{:keys [url]}]
  (let [active-tab (re-frame/subscribe [::subs/active-tab])
        card-props {:square true}]
    [mui/grid {:container true}
     [mui/grid {:item true :xs 12}
      [mui/card card-props
       [mui/card-content
        [mui/tabs {:scrollable true
                   :full-width false
                   :text-color "secondary"
                   :on-change change-tab
                   :value @active-tab}
         [mui/tab {:label "Hallien tiedot"
                   :icon (r/as-element [mui-icons/info])}]
         [mui/tab {:label "Vinkkejä energiatehokkuuteen"
                   :icon (r/as-element [mui-icons/flash-on])}]
         [mui/tab {:label "Ilmoita kulutustiedot"
                   :icon (r/as-element [mui-icons/add])}]]]]]
     [mui/grid {:item true :xs 12}
      [mui/card card-props
       [mui/card-content
        (case @active-tab
          0 (info-tab url)
          1 (energy-tab)
          2 (form-tab))]]]]))

(defn main []
  (create-panel {:url "https://liikuntaportaalit.sportvenue.net/Jaahalli"}))
