(ns lipas-ui.sports-places.views
  (:require [lipas-ui.i18n :as i18n]
            [lipas-ui.mui :as mui]
            [lipas-ui.subs :as global-subs]
            [re-frame.core :as re-frame]))

(defn create-panel [tr]
  [:div {:style {:padding "1em"}}
   [mui/grid {:container true
              :spacing 16}
    [mui/grid {:item true :xs 12}
     [mui/card {:square true}
      [mui/card-header {:title (tr :sport/headline)}]
      [mui/card-content
       [mui/typography (tr :sport/description)]]]]]])

(defn main []
  (let [tr (i18n/->tr-fn @(re-frame/subscribe [::global-subs/locale]))]
    (create-panel tr)))
