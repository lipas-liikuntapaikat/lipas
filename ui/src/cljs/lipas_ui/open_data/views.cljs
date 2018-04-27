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

(defn ->grid-card [title content]
  [mui/grid {:item true :xs 12 :md 6 :lg 4}
   [mui/card {:square true
              :style {:height "100%"}}
    [mui/card-header {:title title}]
    [mui/card-content
     content]]])

(defn create-panel [tr]
  [:div {:style {:padding "1em"}}
   [mui/grid {:container true
              :spacing 16}
    (->grid-card (tr :open-data/headline)
                 [mui/typography (tr :open-data/description)])
    (->grid-card (tr :open-data/rest)
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
                   [mui/list-item-text {:primary "GitHub"}]]])
    (->grid-card (tr :open-data/wms-wfs)
                 [mui/typography (tr :open-data/wms-wfs-description)])]])

(defn main []
  (let [tr (i18n/->tr-fn @(re-frame/subscribe [::global-subs/locale]))]
    (create-panel tr)))
