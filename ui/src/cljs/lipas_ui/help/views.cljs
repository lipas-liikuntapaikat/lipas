(ns lipas-ui.help.views
  (:require [lipas-ui.i18n :as i18n]
            [lipas-ui.mui :as mui]
            [lipas-ui.subs :as global-subs]
            [re-frame.core :as re-frame]))

(defn create-panel [tr]
  (let [card-props {:square true}]
    [mui/grid {:container true}
     [mui/grid {:item true :xs 12}
      [mui/card card-props
       [mui/card-content
        [mui/typography {:variant "headline"} (tr :help/headline)]
        [mui/typography (tr :help/description)]]]]]))

(defn main []
  (let [tr (i18n/->tr-fn @(re-frame/subscribe [::global-subs/locale]))]
    (create-panel tr)))
