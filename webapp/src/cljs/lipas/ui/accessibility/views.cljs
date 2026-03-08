(ns lipas.ui.accessibility.views
  (:require [lipas.ui.accessibility.events :as events]
            [lipas.ui.accessibility.subs :as subs]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/CircularProgress$default" :as CircularProgress]
            ["@mui/material/GridLegacy$default" :as Grid]
            ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/Typography$default" :as Typography]
            [lipas.ui.utils :refer [<== ==>] :as utils]))

(defn view
  [{:keys [lipas-id]}]
  (let [statements (<== [::subs/statements lipas-id])
        logged-in? (<== [:lipas.ui.user.subs/logged-in?])
        can-edit?  (<== [:lipas.ui.user.subs/permission-to-publish? lipas-id])
        loading?   (<== [::subs/loading?])]

    [:> Grid {:container true}
     (into
       [:<>]
       (for [[group sentences] statements]
         [:> Grid {:item true :xs 12}
          [:> Typography {:variant "body2"}
           group]
          (into [:ul]
                (for [s sentences]
                  [:li s]))]))

     (when (and logged-in? can-edit?)
       [:> Grid {:item true :xs 12}
        [:> Grid {:container true}
         [:> Grid {:item true}
          [:> Button
           {:variant  "contained"
            :color    "secondary"
            :on-click #(==> [::events/get-app-url lipas-id])}
           "Täytä esteettömyyssovelluksessa"]]

         [:> Grid {:item true}
          (if loading?
            [:> CircularProgress {:style {:margin-left "0.5em"}}]
            [:> Button
             {:style    {:margin-left "0.5em"}

              :color    "secondary"
              :on-click #(==> [::events/get-statements lipas-id])}
             [:> Icon "refresh"]])]]])]))
