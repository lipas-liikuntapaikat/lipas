(ns lipas.ui.accessibility.views
  (:require
   [cljs.pprint :as pprint]
   [lipas.ui.mui :as mui]
   [lipas.ui.accessibility.events :as events]
   [lipas.ui.accessibility.subs :as subs]
   [lipas.ui.utils :refer [<== ==>] :as utils]))

(defn view [{:keys [lipas-id]}]
  (let [statements (<== [::subs/statements lipas-id])
        logged-in? (<== [:lipas.ui.user.subs/logged-in?])
        can-edit?  (<== [:lipas.ui.user.subs/permission-to-publish? lipas-id])]

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
       [mui/button
        {:variant  "contained"
         :color    "secondary"
         :on-click #(==> [::events/get-app-url lipas-id])}
        "Täytä esteettömyyssovelluksessa"])]))
