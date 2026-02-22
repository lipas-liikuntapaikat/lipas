(ns lipas.ui.analysis.views
  (:require ["@mui/material/Tooltip$default" :as Tooltip]
            [lipas.ui.analysis.diversity.view :as diversity]
            [lipas.ui.analysis.events :as events]
            [lipas.ui.analysis.heatmap.views :as heatmap]
            [lipas.ui.analysis.reachability.views :as reachability]
            [lipas.ui.analysis.subs :as subs]
            [lipas.ui.map.events :as map-events]
            ["@mui/material/GridLegacy$default" :as Grid]
            ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/IconButton$default" :as IconButton]
            ["@mui/material/Tab$default" :as Tab]
            ["@mui/material/Tabs$default" :as Tabs]
            [lipas.ui.utils :refer [<== ==>] :as utils]))

(defn view [{:keys [tr]}]
  (let [selected-tool (<== [::subs/selected-tool])
        #_#_experimental? (<== [::subs/privilege-to-experimental-tools?])]
    [:> Grid
     {:container true
      :spacing 2
      :style {:padding "1em"}}

     ;; Header and close button
     [:> Grid
      {:item true
       :container true
       :justify-content "space-between"}
      [:> Grid {:item true :xs 10}
       [:> Tabs
        {:value selected-tool
         :on-change #(==> [::events/select-tool %2])
         :variant "fullWidth"
         :centered true
         :text-color "secondary"
         :indicator-color "secondary"}
        [:> Tab {:value "reachability" :label (tr :analysis/reachability)}]
        [:> Tab {:value "diversity" :label (tr :analysis/diversity)}]
        [:> Tab {:value "heatmap" :label (tr :analysis/heatmap)}]]]
      [:> Grid {:item true}
       [:> Tooltip
        {:title (tr :analysis/close)}
        [:> IconButton {:on-click #(==> [::map-events/hide-analysis])}
         [:> Icon "close"]]]]]

     [:> Grid {:item true :xs 12}
      (condp = selected-tool
        "reachability" [reachability/analysis-view]
        "diversity" [diversity/view]
        "heatmap" [heatmap/heatmap-controls])]]))
