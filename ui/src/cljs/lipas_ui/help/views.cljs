(ns lipas-ui.help.views
  (:require [lipas-ui.mui :as mui]))

(defn create-panel []
  [mui/grid {:container true}
   [mui/grid {:item true :xs 12}
    [mui/card
     [mui/card-content
      [mui/typography {:variant "headline"} "Ohjeet"]
      [mui/typography "Tänne tulevat ohjeet"]]]]])

(defn main []
  (create-panel))
