(ns lipas-ui.swimming-pools.views
  (:require [lipas-ui.mui :as mui]
            [lipas-ui.mui-icons :as mui-icons]
            [lipas-ui.swimming-pools.events :as events]
            [lipas-ui.swimming-pools.subs :as subs]
            [re-frame.core :as re-frame]
            [reagent.core :as r]))

(defn info-tab [url]
  [mui/grid {:container true}
   [mui/grid {:item true :xs 12}
    [:iframe {:src url
              :style {:min-height "800px" :width "100%"}}]]])

(defn energy-tab [tr]
  [mui/grid {:container true}
   [mui/grid {:item true :xs 12}
    [mui/typography (tr :ice-energy/description)]]])

(defn form-tab [tr]
  [mui/form-label {:component "legend"} (tr :energy/consumption-info)]
  [mui/form-group
   [mui/text-field {:select true
                    :label (tr :ice-form/select-rink)
                    :value "Halli 1"}
    (for [hall ["Halli 1" "Halli 2" "Halli 3"]]
      [mui/menu-item {:key hall :value hall} hall])]
   [mui/text-field {:label (tr :time/year)
                    :type "number"
                    :select true
                    :value 2018}
    (for [year (range 2000 2019)]
      [mui/menu-item {:key year :value year} year])]
   [mui/text-field {:label (tr :energy/electricity)
                    :type "number"
                    :Input-props
                    {:end-adornment
                     (r/as-element
                      [mui/input-adornment (tr :physical-units/mwh)])}}]
   [mui/text-field {:label (tr :energy/heat)
                    :type "number"
                    :Input-props
                    {:end-adornment
                     (r/as-element
                      [mui/input-adornment (tr :physical-units/mwh)])}}]
   [mui/text-field {:label (tr :energy/water)
                    :type "number"
                    :Input-props
                    {:end-adornment
                     (r/as-element
                      [mui/input-adornment (tr :physical-units/m3)])}}]
   [mui/button {:color "secondary" :size "large"}
    (tr :actions/save)]])

(defn change-tab [_ value]
  (re-frame/dispatch [::events/set-active-tab value]))

(defn create-panel [{:keys [url tr]}]
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
         [mui/tab {:label (tr :ice-rinks/headline)
                   :icon (r/as-element [mui-icons/info])}]
         [mui/tab {:label (tr :ice-energy/headline)
                   :icon (r/as-element [mui-icons/flash-on])}]
         [mui/tab {:label (tr :ice-form/headline)
                   :icon (r/as-element [mui-icons/add])}]]]]]
     [mui/grid {:item true :xs 12}
      [mui/card card-props
       [mui/card-content
        (case @active-tab
          0 (info-tab url)
          1 (energy-tab tr)
          2 (form-tab tr))]]]]))

(defn main [tr]
  (create-panel {:tr tr
                 :url "https://liikuntaportaalit.sportvenue.net/Uimahalli"}))
