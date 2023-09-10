(ns lipas.ui.loi.views
  (:require
   [lipas.ui.components :as lui]
   [lipas.ui.loi.events :as events]
   [lipas.ui.loi.subs :as subs]
   [lipas.ui.mui :as mui]
   [lipas.ui.utils :refer [<== ==>]]
   [reagent.core :as r]))

(defn view
  []
  (r/with-let [props-form (r/atom {})]
    (let [tr        (<== [:lipas.ui.subs/translator])
          locale    (tr)
          loi-cats  (<== [::subs/loi-categories])
          loi-cat   (<== [::subs/selected-loi-category])
          loi-type  (<== [::subs/selected-loi-type])
          loi-props (<== [::subs/props])
          zoomed?   (<== [:lipas.ui.map.subs/zoomed-for-drawing?])
          geom-type "Point"
          geoms     (<== [::subs/geoms])
          ]

      [mui/grid {:container true :spacing 2 :style {:padding "1em"}}

       [mui/grid {:item true :xs 12}
        [mui/typography {:variant "h6"}
         "Lisää muu kohde"]]

       [mui/grid {:item true :xs 12}
        [lui/select
         {:items    loi-cats
          :label    "Valitse kategoria"
          :value-fn first
          :label-fn (comp locale :label second)
          :value    loi-cat}]]

       [mui/grid {:item true :xs 12}
        [lui/autocomplete
         {:items     (vals (get-in loi-cats [loi-cat :types]))
          :label     "Valitse tyyppi"
          :value-fn  :value
          :label-fn  (comp locale :label)
          :on-change #(==> [::events/select-loi-type %])
          :value     loi-type}]]

       (when (not zoomed?)
         [mui/grid {:item true :xs 12}
          [mui/typography {:variant "body2" :color :error}
           (tr :map/zoom-closer)]])

       (when-not geoms
         [mui/grid {:item true}
          [mui/button
           {:disabled (not zoomed?)
            :color    "secondary"
            :variant  "contained"
            :on-click #(==> [:lipas.ui.map.events/start-adding-geom geom-type])}
           [mui/icon "add_location"]
           (tr :map/add-to-map)]])

       (when loi-type
         (into [:<>]
               (for [[k v] loi-props]
                 [mui/grid {:item true :xs 12}
                  [lui/text-field
                   {:fullWidth true
                    :multiline true
                    :rows      5
                    :value     (get @props-form k)
                    :on-change #(swap! props-form assoc k %)
                    :label     (get-in v [:field :label locale])}]])))

       (when (and geoms loi-cat loi-type)
         [mui/grid {:item true :xs 12}
          [mui/button {:variant "contained"
                       :color "secondary"
                       :on-click (fn []
                                   (==> [::events/save loi-cat loi-type @props-form geoms]))}
           (tr :actions/save)]])
       ])))
