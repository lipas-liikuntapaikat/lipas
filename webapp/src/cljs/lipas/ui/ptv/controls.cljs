(ns lipas.ui.ptv.controls
  (:require
    ["@mui/material/Tab$default" :as Tab]
    ["@mui/material/Tabs$default" :as Tabs]
    ["@mui/material/Typography$default" :as Typography]
    [lipas.ui.components.autocompletes :refer [autocomplete2]]
    [reagent.core :as r]
    [reagent.hooks :as hooks]))

(r/defc info-text [{:keys [children]}]
  [:> Typography
   {:variant "body1"}
   children])

(r/defc services-selector
  [{:keys [disabled options value on-change label value-fn multiple sx]
    :or   {value-fn identity
           label    ""
           multiple true}}]
  (let [options* (hooks/use-memo (fn []
                                   (->> options
                                        (map (fn [x]
                                               {:value (value-fn x)
                                                :label (:label x)}))
                                        (sort-by :label)))
                                 [options value-fn])]
    [autocomplete2
     (cond-> {:disabled  disabled
              :options   options*
              :multiple  multiple
              :label     label
              :value     (if multiple (to-array value) value)
              :onChange (fn [_e v]
                          (if multiple
                            (on-change (vec (map (fn [x]
                                                   (if (map? x)
                                                     (:value x)
                                                     x))
                                                 v)))
                            (on-change (if (map? v) (:value v) v))))}
       sx (assoc :sx sx))]))

(r/defc lang-selector [{:keys [value on-change enabled-languages]}]
  [:> Tabs
   {:value     value
    :on-change (fn [_e v] (on-change (keyword v)))}
   (when (or (nil? enabled-languages) (contains? enabled-languages "fi"))
     [:> Tab {:value "fi" :label "FI"}])
   (when (or (nil? enabled-languages) (contains? enabled-languages "se"))
     [:> Tab {:value "se" :label "SE"}])
   (when (or (nil? enabled-languages) (contains? enabled-languages "en"))
     [:> Tab {:value "en" :label "EN"}])])
