(ns lipas.ui.ptv.controls
  (:require ["@mui/material/FormControl$default" :as FormControl]
            ["@mui/material/FormControlLabel$default" :as FormControlLabel]
            ["@mui/material/FormLabel$default" :as FormLabel]
            ["@mui/material/Radio$default" :as Radio]
            ["@mui/material/RadioGroup$default" :as RadioGroup]
            ["@mui/material/Tab$default" :as Tab]
            ["@mui/material/Tabs$default" :as Tabs]
            ["@mui/material/Typography$default" :as Typography]
            [lipas.ui.components.autocompletes :refer [autocomplete2]]
            [uix.core :as uix :refer [$ defui]]))

(defui info-text [{:keys [children]}]
  ($ Typography
     {:variant "body1"}
     children))

(defui services-selector
  [{:keys [options value on-change label value-fn]
    :or   {value-fn identity
           label    ""}}]
  (let [options* (uix/use-memo (fn []
                                 (map (fn [x]
                                        {:value (value-fn x)
                                         :label (:label x)})
                                      options))
                               [options value-fn])]
    ($ autocomplete2
       {:options   options*
        :multiple  true
        :label     label
        :value     (to-array value)
        :on-change (fn [_e v]
                     (on-change (:value v)))})))

(defui lang-selector [{:keys [value on-change enabled-languages]}]
  ($ Tabs
     {:value     value
      :on-change (fn [_e v] (on-change (keyword v)))}
     (when (or (nil? enabled-languages) (contains? enabled-languages "fi"))
       ($ Tab {:value "fi" :label "FI"}))
     (when (or (nil? enabled-languages) (contains? enabled-languages "se"))
       ($ Tab {:value "se" :label "SE"}))
     (when (or (nil? enabled-languages) (contains? enabled-languages "en"))
       ($ Tab {:value "en" :label "EN"}))))
