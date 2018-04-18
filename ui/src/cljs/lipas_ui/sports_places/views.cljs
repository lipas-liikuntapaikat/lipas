(ns lipas-ui.sports-places.views
  (:require [lipas-ui.mui :as mui]))

(defn create-panel []
  [mui/grid {:container true}
   [mui/grid {:item true :xs 12}
    [mui/card
     [mui/card-content
      [mui/typography {:variant "headline"} "Liikuntapaikat"]]]]])

(defn main []
  (create-panel))
