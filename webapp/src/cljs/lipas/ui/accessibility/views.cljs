(ns lipas.ui.accessibility.views
  (:require [lipas.ui.accessibility.events :as events]
            [lipas.ui.accessibility.subs :as subs]
            [lipas.ui.mui :as mui]
            [lipas.ui.utils :refer [<== ==>] :as utils]))

(defn view
  [{:keys [lipas-id]}]
  (let [statements (<== [::subs/statements lipas-id])
        logged-in? (<== [:lipas.ui.user.subs/logged-in?])
        can-edit?  (<== [:lipas.ui.user.subs/permission-to-publish? lipas-id])
        loading?   (<== [::subs/loading?])]

    [mui/grid {:container true}
     (into
       [:<>]
       (for [[group sentences] statements]
         [mui/grid {:item true :xs 12}
          [mui/typography {:variant "body2"}
           group]
          (into [:ul]
                (for [s sentences]
                  [:li s]))]))

     (when (and logged-in? can-edit?)
       [mui/grid {:item true :xs 12}
        [mui/grid {:container true}
         [mui/grid {:item true}
          [mui/button
           {:variant  "contained"
            :color    "secondary"
            :on-click #(==> [::events/get-app-url lipas-id])}
           "Täytä esteettömyyssovelluksessa"]]

         [mui/grid {:item true}
          (if loading?
            [mui/circular-progress {:style {:margin-left "0.5em"}}]
            [mui/button
             {:style    {:margin-left "0.5em"}

              :color    "secondary"
              :on-click #(==> [::events/get-statements lipas-id])}
             [mui/icon "refresh"]])]]])]))
