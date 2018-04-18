(ns lipas-ui.open-data.views
  (:require [lipas-ui.mui :as mui]
            [lipas-ui.mui-icons :as mui-icons]
            [lipas-ui.routes :refer [navigate!]]
            [lipas-ui.svg :as svg]))

(def links {:github "https://github.com/lipas-liikuntapaikat/lipas-api"
            :lipas "http://lipas.cc.jyu.fi/api/index.html"})

(defn create-panel []
  [mui/grid {:container true}
   [mui/grid {:item true :xs 12}
    [mui/card
     [mui/card-content
      [mui/typography {:variant "headline"} "Rajapinnat"]
      [mui/typography "Kaikki data on avointa blabalba."]]]]
   [mui/grid {:item true :xs 12 :md 6 :lg 4}
    [mui/card
     [mui/card-content
      [mui/typography {:variant "headline"} "REST"]
      [mui/list
       [mui/list-item {:button true
                       :on-click #(navigate! {:lipas links})}
        [mui/list-item-icon
         [mui-icons/build]]
        [mui/list-item-text {:primary "Swagger"}]]
       [mui/list-item {:button true
                       :on-click #(navigate! (:github links))}
        [mui/list-item-icon
         [mui/svg-icon
          svg/github-icon]]
        [mui/list-item-text {:primary "GitHub"}]]]]]]
   [mui/grid {:item true :xs 12 :md 6 :lg 4}
    [mui/card
     [mui/card-content
      [mui/typography {:variant "headline"} "WMS"]]]]
   [mui/grid {:item true :xs 12 :md 6 :lg 4}
    [mui/card
     [mui/card-content
      [mui/typography {:variant "headline"} "WFS"]]]]])

(defn main []
  (create-panel))
