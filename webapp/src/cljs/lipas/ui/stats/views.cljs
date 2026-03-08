(ns lipas.ui.stats.views
  (:require ["@mui/material/GridLegacy$default" :as Grid]
            ["@mui/material/Paper$default" :as Paper]
            ["@mui/material/Tab$default" :as Tab]
            ["@mui/material/Tabs$default" :as Tabs]
            [lipas.ui.stats.age-structure.views :as age-structure-stats]
            [lipas.ui.stats.city.views :as city-stats]
            [lipas.ui.stats.events :as events]
            [lipas.ui.stats.finance.views :as finance-stats]
            [lipas.ui.stats.sport.views :as sport-stats]
            [lipas.ui.stats.subs :as subs]
            [lipas.ui.stats.subsidies.views :as subsidies]
            [lipas.ui.utils :refer [<== ==>] :as utils]))

(defn main []
  (let [tr  (<== [:lipas.ui.subs/translator])
        tab (<== [::subs/selected-tab])]

    [:> Paper {:square true :style {:padding "1em"}}

     [:> Grid {:container true :spacing 4}

      ;; Tabs for choosing between different stats pages
      [:> Grid {:item true :xs 12}
       [:> Tabs
        {:value     tab
         :variant   "fullWidth"
         :on-change #(==> [::events/navigate %2])
         :indicator-color "secondary"
         :text-color "inherit"}
        [:> Tab {:value "sport" :label (tr :stats/sports-stats)}]
        [:> Tab {:value "age-structure" :label (tr :stats/age-structure-stats)}]
        [:> Tab {:value "city" :label (tr :stats/city-stats)}]
        [:> Tab {:value "finance" :label (tr :stats/finance-stats)}]
        [:> Tab {:value "subsidies" :label (tr :stats/subsidies)}]]]

      [:> Grid {:item true :xs 12}
       (condp = tab
         "sport"         [sport-stats/view]
         "age-structure" [age-structure-stats/view]
         "city"          [city-stats/view]
         "finance"       [finance-stats/view]
         "subsidies"     [subsidies/view])]]]))
