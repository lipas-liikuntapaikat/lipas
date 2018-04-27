(ns lipas-ui.front-page.views
  (:require [lipas-ui.i18n :as i18n]
            [lipas-ui.mui :as mui]
            [lipas-ui.subs :as global-subs]
            [re-frame.core :as re-frame]))

(defn ->grid-card [title content-text]
  [mui/grid {:item true :xs 12 :md 6 :lg 4}
   [mui/card {:square true
              :style {:height "100%"}}
    [mui/card-header {:title title}]
    [mui/card-content
     [mui/typography content-text]]]])

(defn create-panel [tr]
  [:div {:style {:padding "1em"}}
   [mui/grid {:container true
              :spacing 16}
    (->grid-card (tr :sport/headline) (tr :sport/description))
    (->grid-card (tr :ice/headline) (tr :ice/description))
    (->grid-card (tr :swim/headline) (tr :swim/description))
    (->grid-card (tr :open-data/headline) (tr :open-data/description))
    (->grid-card (tr :help/headline) (tr :help/description))]])

(defn main []
  (let [tr (i18n/->tr-fn @(re-frame/subscribe [::global-subs/locale]))]
    (create-panel tr)))
