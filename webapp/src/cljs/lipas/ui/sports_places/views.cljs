(ns lipas.ui.sports-places.views
  (:require [lipas.ui.mui :as mui]))

(defn create-panel [tr]
  [:div {:style {:padding "1em"}}
   [mui/grid {:container true
              :spacing 16}
    [mui/grid {:item true :xs 12}
     [mui/card {:square true}
      [mui/card-header {:title (tr :sport/headline)}]
      [mui/card-content
       [mui/typography (tr :sport/description)]]]]]])

(defn main [tr]
  (create-panel tr))
