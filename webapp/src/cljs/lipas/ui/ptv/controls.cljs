(ns lipas.ui.ptv.controls
  (:require
    ["@mui/material/Tab$default" :as Tab]
    ["@mui/material/Tabs$default" :as Tabs]
    ["@mui/material/Typography$default" :as Typography]
    [lipas.data.ptv :as ptv-data]
    [lipas.ui.components.autocompletes :refer [autocomplete2]]
    [reagent.core :as r]
    [reagent.hooks :as hooks]))

(defn text-length-hint
  "Helper text for a PTV text field holding `v`: its length against PTV's
  maximum, and what PTV's minimum is while the text is under it."
  [tr v max-length]
  (cond-> (str (count v) "/" max-length)
    (ptv-data/text-too-short? v)
    (str " – " (tr :ptv/text-min-length ptv-data/min-text-length))))

(defn text-length-error?
  "Is `v` a text PTV rejects for its length? A blank one isn't."
  [v max-length]
  (or (ptv-data/text-too-short? v)
      (> (count v) max-length)))

(r/defc info-text [{:keys [children]}]
  [:> Typography
   {:variant "body1"}
   children])

(def ^:private label-collator
  ;; Finnish alphabetical order: case-insensitive, Å Ä Ö after Z, and
  ;; "Kenttä 2" before "Kenttä 10".
  (js/Intl.Collator. "fi" #js {:sensitivity "base" :numeric true}))

(defn sort-by-label
  "Selector options in alphabetical order of their :label."
  [options]
  (sort-by #(or (:label %) "") #(.compare label-collator %1 %2) options))

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
                                        (sort-by-label)))
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
