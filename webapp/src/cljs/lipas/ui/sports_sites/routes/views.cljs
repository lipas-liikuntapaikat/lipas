(ns lipas.ui.sports-sites.routes.views
  (:require [lipas.ui.mui :as mui]
            [lipas.ui.utils :refer [<== ==>] :as utils]
            [reagent.core :as r]))

(defn routes-tab
  "Main component for the REITIT tab"
  [{:keys [facility tr lipas-id type-code display-data edit-data can-edit?]}]
  (r/with-let [simple-mode? (r/atom true)]
    (let [has-multiple-routes? false] ;; TODO: Check actual route count

      [mui/box {:style {:padding "1em"}}
       ;; Mode toggle (Simple/Advanced)
       [mui/form-control-label
        {:control (r/as-element
                   [mui/switch
                    {:checked (not @simple-mode?)
                     :disabled has-multiple-routes?
                     :on-change #(reset! simple-mode? (not %2))}])
         :label "Kehittynyt tila"
         :label-placement "start"}]

       [mui/typography {:variant "caption" :color "textSecondary"}
        (if @simple-mode?
          "Yksinkertainen tila: Yksi reitti käyttää kaikkia osia automaattisesti"
          "Kehittynyt tila: Voit luoda useita reittejä ja määrittää osien järjestyksen")]

       ;; Content based on mode
       [mui/box {:style {:margin-top "2em"}}
        (if @simple-mode?
          ;; Simple mode content
          [mui/grid {:container true :spacing 2}
           [mui/grid {:item true :xs 12}
            [mui/typography {:variant "h6"} "Yksinkertainen reitti"]
            [mui/typography {:variant "body2" :color "textSecondary"}
             "Kaikki geometrian osat muodostavat automaattisesti yhden reitin."]]
           [mui/grid {:item true :xs 12}
            [mui/typography {:variant "body1"}
             "Toiminnallisuus tulossa pian..."]]]

          ;; Advanced mode content  
          [mui/grid {:container true :spacing 2}
           [mui/grid {:item true :xs 12}
            [mui/typography {:variant "h6"} "Kehittynyt reittien hallinta"]
            [mui/typography {:variant "body2" :color "textSecondary"}
             "Voit luoda useita reittejä ja määrittää niiden osien järjestyksen."]]
           [mui/grid {:item true :xs 12}
            [mui/typography {:variant "body1"}
             "Toiminnallisuus tulossa pian..."]]])]])))