(ns lipas-ui.front-page.views
  (:require [lipas-ui.i18n :as i18n]
            [lipas-ui.mui :as mui]
            [lipas-ui.subs :as global-subs]
            [re-frame.core :as re-frame]))

(defn create-panel [tr]
  (let [card-props {:square true
                    :style {:height "100%"}}
        headline-props {:variant "headline"
                        :color "secondary"}]
    [:div {:style {:padding "1em"}}
     [mui/grid {:container true
                :spacing 16}
      [mui/grid {:item true :xs 12 :md 6 :lg 4}
       [mui/card card-props
        [mui/card-content
         [mui/typography headline-props (tr :sport/headline)]
         [mui/typography (tr :sport/description)]]]]
      [mui/grid {:item true :xs 12 :md 6 :lg 4}
       [mui/card card-props
        [mui/card-content
         [mui/typography headline-props (tr :ice/headline)]
         [mui/typography (tr :ice/description)]]]]
      [mui/grid {:item true :xs 12 :md 6 :lg 4}
       [mui/card card-props
        [mui/card-content
         [mui/typography headline-props (tr :swim/headline)]
         [mui/typography (tr :swim/description)]]]]
      [mui/grid {:item true :xs 12 :md 6 :lg 4}
       [mui/card card-props
        [mui/card-content
         [mui/typography headline-props (tr :open-data/headline)]
         [mui/typography (tr :open-data/description)]]]]
      [mui/grid {:item true :xs 12 :md 6 :lg 4}
       [mui/card card-props
        [mui/card-content
         [mui/typography headline-props (tr :help/headline)]
         [mui/typography (tr :help/description)]]]]]]))

(defn main []
  (let [tr (i18n/->tr-fn @(re-frame/subscribe [::global-subs/locale]))]
    (create-panel tr)))
