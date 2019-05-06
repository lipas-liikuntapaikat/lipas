(ns lipas.ui.stats.views
  (:require
   [lipas.ui.mui :as mui]
   [lipas.ui.stats.age-structure.views :as age-structure-stats]
   [lipas.ui.stats.city.views :as city-stats]
   [lipas.ui.stats.events :as events]
   [lipas.ui.stats.sport.views :as sport-stats]
   ;;[lipas.ui.stats.finance.views :as finance-stats]
   [lipas.ui.stats.subs :as subs]
   [lipas.ui.utils :refer [<== ==>] :as utils]))

(defn main []
  (let [tr  (<== [:lipas.ui.subs/translator])
        tab (<== [::subs/selected-tab])]

    [mui/grid {:container true}
     [mui/grid {:item true :xs 12}
      [mui/paper {:square true :style {:padding "1em"}}

       ;; Tabs for choosing between different stats pages
       [mui/grid {:item true}
        [mui/tabs {:value tab :variant "scrollable" :on-change #(==> [::events/navigate %2])}
         [mui/tab {:value "sport" :label (tr :stats/sports-stats)}]
         [mui/tab {:value "age-structure" :label (tr :stats/age-structure-stats)}]
         [mui/tab {:value "city" :label (tr :stats/city-stats)}]
         ;;[mui/tab {:value "finance" :label (tr :stats/finance-stats)}]
         ]]

       [mui/grid {:item true}
        (condp = tab
          "sport"         [sport-stats/view]
          "age-structure" [age-structure-stats/view]
          "city"          [city-stats/view]
          ;; "finance"       [finance-stats/view]
          )]]]]))
