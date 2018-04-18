(ns lipas-ui.front-page.views
  (:require [lipas-ui.mui :as mui]))

(defn create-panel []
  (let [card-props {:square true}]
    [mui/grid {:container true}
     [mui/grid {:item true :xs 12 :md 6 :lg 4}
      [mui/card card-props
       [mui/card-content
        [mui/typography {:variant "headline"} "Liikuntapaikat"]
        [mui/typography "LIPAS on suomalaisten liikuntapaikkojen tietokanta."]]]]
     [mui/grid {:item true :xs 12 :md 6 :lg 4}
      [mui/card card-props
       [mui/card-content
        [mui/typography {:variant "headline"} "Jäähalliportaali"]
        [mui/typography (str "Jäähalliportaali sisältää hallien perus- ja "
                             "energiankulutustietoja, sekä ohjeita "
                             "energiatehokkuuden parantamiseen")]]]]
     [mui/grid {:item true :xs 12 :md 6 :lg 4}
      [mui/card card-props
       [mui/card-content
        [mui/typography {:variant "headline"} "Uimahalliportaali"]
        [mui/typography "Hieno on myös tämä toinen portaali."]]]]
     [mui/grid {:item true :xs 12 :md 6 :lg 4}
      [mui/card card-props
       [mui/card-content
        [mui/typography {:variant "headline"} "Avoin data"]
        [mui/typography "Kaikki data on avointa blabalba."]]]]
     [mui/grid {:item true :xs 12 :md 6 :lg 4}
      [mui/card card-props
       [mui/card-content
        [mui/typography {:variant "headline"} "Ohjeet"]
        [mui/typography "Täältä löytyvät ohjeet"]]]]]))

(defn main []
  (create-panel))
