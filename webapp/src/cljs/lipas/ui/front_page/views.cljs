(ns lipas.ui.front-page.views
  (:require [lipas.ui.mui :as mui]
            [lipas.ui.mui-icons :as mui-icons]
            [lipas.ui.routes :refer [navigate!]]
            [reagent.core :as r]))

(defn grid-card [{:keys [title style link]} & children]
  [mui/grid {:item true :xs 12 :md 6 :lg 4}
   [mui/card {:square true
              :style (merge style {:height "100%"})}
    [mui/card-header {:title title
                      :action (when link
                                (r/as-element
                                 [mui/icon-button
                                  {:on-click #(navigate! link)
                                   :color "secondary"}
                                  [mui/icon "arrow_forward_ios"]]))}]
    (into [mui/card-content] children)
    [mui/card-actions]]])

(defn create-panel [tr]
  [:div {:style {:padding "1em"}}
   [mui/grid {:container true
              :spacing 16}
    [grid-card {:style {:background-color "#f7ed33"}
                :title (tr :disclaimer/headline)}
     [mui/typography (tr :disclaimer/test-version)]]
    [grid-card {:title (tr :sport/headline)
                :link "/#/liikuntapaikat"}
     [mui/typography (tr :sport/description)]]
    [grid-card {:title (tr :ice/headline)
                :link "/#/jaahalliportaali"}
     [mui/typography (tr :ice/description)]]
    [grid-card {:title (tr :swim/headline)
                :link "/#/uimahalliportaali"}
     [mui/typography (tr :swim/description)]]
    [grid-card {:title (tr :open-data/headline)
                :link "/#/avoin-data"}
     [mui/typography (tr :open-data/description)]]
    [grid-card {:title (tr :help/headline)
                :link "/#/ohjeet"}
     [mui/typography (tr :help/description)]]
    [grid-card {:title (tr :data-users/headline)}
     [mui/typography (tr :data-users/description)]]
    [grid-card {:title (tr :partners/headline)}
     [mui/typography (tr :partners/description)]]
    [grid-card {:title (tr :team/headline)}
     [mui/typography (tr :team/description)]]]])

(defn main [tr]
  (create-panel tr))
