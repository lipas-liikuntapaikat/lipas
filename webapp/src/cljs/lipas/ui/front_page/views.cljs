(ns lipas.ui.front-page.views
  (:require [lipas.ui.mui :as mui]))

(defn grid-card [{:keys [title]} & children]
  [mui/grid {:item true :xs 12 :md 6 :lg 4}
   [mui/card {:square true
              :style {:height "100%"}}
    [mui/card-header {:title title}]
    (into [mui/card-content]
          children)]])

(defn create-panel [tr]
  [:div {:style {:padding "1em"}}
   [mui/grid {:container true
              :spacing 16}
    [grid-card {:title (tr :sport/headline)}
     [mui/typography (tr :sport/description)]
     ;; http://lipas.cc.jyu.fi/lipas/VAADIN/themes/lipas/img/lipaskuva.jpg
     ;; [mui/card-media {:image "img/lipas_kuva_2018.jpg"}]
     ]
    [grid-card {:title (tr :ice/headline)}
     [mui/typography (tr :ice/description)]]
    [grid-card {:title (tr :swim/headline)}
     [mui/typography (tr :swim/description)]]
    [grid-card {:title (tr :open-data/headline)}
     [mui/typography (tr :open-data/description)]]
    [grid-card {:title (tr :help/headline)}
     [mui/typography (tr :help/description)]]]])

(defn main [tr]
  (create-panel tr))
