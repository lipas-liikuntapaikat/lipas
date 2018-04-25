(ns lipas-ui.open-data.views
  (:require [lipas-ui.i18n :as i18n]
            [lipas-ui.mui :as mui]
            [lipas-ui.mui-icons :as mui-icons]
            [lipas-ui.routes :refer [navigate!]]
            [lipas-ui.subs :as global-subs]
            [lipas-ui.svg :as svg]
            [re-frame.core :as re-frame]))

(def links {:github "https://github.com/lipas-liikuntapaikat/lipas-api"
            :lipas "http://lipas.cc.jyu.fi/api/index.html"})

(defn create-panel [tr]
  (let [card-props {:square true}]
    [mui/grid {:container true}
     [mui/grid {:item true :xs 12}
      [mui/card card-props
       [mui/card-content
        [mui/typography {:variant "headline"} (tr :open-data/headline)]
        [mui/typography (tr :open-data/description)]]]]
     [mui/grid {:item true :xs 12 :md 6 :lg 4}
      [mui/card card-props
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
      [mui/card card-props
       [mui/card-content
        [mui/typography {:variant "headline"} "WMS"]]]]
     [mui/grid {:item true :xs 12 :md 6 :lg 4}
      [mui/card card-props
       [mui/card-content
        [mui/typography {:variant "headline"} "WFS"]]]]]))

(defn main []
  (let [tr (i18n/->tr-fn @(re-frame/subscribe [::global-subs/locale]))]
    (create-panel tr)))
