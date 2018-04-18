(ns lipas-ui.ice-stadiums.views
  (:require [lipas-ui.mui :as mui]
            [lipas-ui.swimming-pools.subs :as subs]
            [lipas-ui.swimming-pools.events :as events]
            [lipas-ui.mui-icons :as mui-icons]
            [re-frame.core :as re-frame]
            [reagent.core :as r]))

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
    [mui/button {:color "primary" :size "large"} "Tallenna"]]])

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
