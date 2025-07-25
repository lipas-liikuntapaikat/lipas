(ns lipas.ui.analysis.views
  (:require ["@mui/material/Tooltip$default" :as Tooltip]
            [lipas.ui.analysis.diversity.view :as diversity]
            [lipas.ui.analysis.events :as events]
            [lipas.ui.analysis.heatmap.views :as heatmap]
            [lipas.ui.analysis.reachability.views :as reachability]
            [lipas.ui.analysis.subs :as subs]
            [lipas.ui.map.events :as map-events]
            [lipas.ui.mui :as mui]
            [lipas.ui.utils :refer [<== ==>] :as utils]))

(defn view [{:keys [tr]}]
  (let [selected-tool (<== [::subs/selected-tool])
        experimental? (<== [::subs/privilege-to-experimental-tools?])]
    [mui/grid
     {:container true
      :spacing 2
      :style {:padding "1em"}}

     ;; Header and close button
     [mui/grid
      {:item true
       :container true
       :justify-content "space-between"}
      [mui/grid {:item true :xs 10}
       [mui/tabs
        {:value selected-tool
         :on-change #(==> [::events/select-tool %2])
         :variant "fullWidth"
         :centered true
         :text-color "secondary"
         :indicator-color "secondary"}
        [mui/tab {:value "reachability" :label (tr :analysis/reachability)}]
        [mui/tab {:value "diversity" :label (tr :analysis/diversity)}]
        (when experimental?
          [mui/tab {:value "heatmap" :label (tr :analysis/heatmap)}])]]
      [mui/grid {:item true}
       [:> Tooltip
        {:title (tr :analysis/close)}
        [mui/icon-button {:on-click #(==> [::map-events/hide-analysis])}
         [mui/icon "close"]]]]]

     [mui/grid {:item true :xs 12}
      (condp = selected-tool
        "reachability" [reachability/analysis-view]
        "diversity" [diversity/view]
        "heatmap" [heatmap/heatmap-controls])]]))
