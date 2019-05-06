(ns lipas.ui.stats.finance.views
  (:require
   [lipas.ui.components :as lui]
   [lipas.ui.mui :as mui]
   [lipas.ui.stats.common :as common]
   [lipas.ui.stats.finance.events :as events]
   [lipas.ui.stats.finance.subs :as subs]
   [lipas.ui.utils :refer [<== ==>] :as utils]))

(defn view []
  (let [tr       (<== [:lipas.ui.subs/translator])
        cities   (<== [::subs/selected-cities])
        data     (<== [::subs/data])
        labels   (<== [::subs/labels])
        headers  (<== [::subs/headers])]

    [mui/grid {:container true :spacing 16}

     ;; Headline
     [mui/grid {:item true :xs 12 :style {:margin-top "1.5em" :margin-bottom "1em"}}
      [mui/typography {:variant "h4"}
       (tr :stats/finance-stats)]]

     [mui/grid {:item true :xs 12}

      [mui/grid {:container true :spacing 16 :style {:margin-bottom "1em"}}

       ;; Region selector
       [mui/grid {:item true :xs 12}
        [mui/typography {:variant "body2"} (tr :stats/filter-cities)]
        [lui/region-selector
         {:value     cities
          :on-change #(==> [::events/select-cities %])}]]

       ;; Clear filters button
       (when (not-empty cities)
         [mui/grid {:item true :xs 12}
          [mui/button
           {:color    "secondary"
            :on-click #(==> [::events/clear-filters])}
           (tr :search/clear-filters)]])]]

     ;; Table
     [mui/grid {:item true :xs 12}
      [lui/table
       {:headers headers :items data}]]

     ;; Download Excel button
     [common/download-excel-button
      {:tr       tr
       :on-click #(==> [::events/download-excel data headers])}]]))
