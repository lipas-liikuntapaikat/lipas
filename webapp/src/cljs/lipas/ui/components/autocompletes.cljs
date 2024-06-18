(ns lipas.ui.components.autocompletes
  (:require
   ["@mui/material/TextField$default" :as TextField]
   [lipas.ui.mui :as mui]
   [lipas.ui.utils :as utils]
   [reagent.core :as r]))

(defn autocomplete
  [{:keys [label items value value-fn label-fn on-change sort-fn spec multi?
           required helper-text deselect? sort-cmp render-option-fn disabled variant]
    :or   {label-fn :label
           disabled false
           sort-fn  label-fn
           sort-cmp compare
           value-fn :value
           variant  "standard"}}]
  (let [items-by-vals (utils/index-by (comp pr-str value-fn) items)]
    (r/with-let [state   (r/atom "")]
      [mui/autocomplete
       (merge
        {:multiple             multi?
         :value                (if multi?
                                 (clj->js (map pr-str value))
                                 (pr-str value))
         :disabled             disabled
         :label                label
         :disableCloseOnSelect multi?
         :disableClearable     (not deselect?)
         :on-change            (fn [_evt v]
                                 (on-change
                                  (if multi?
                                    (->> v
                                         (select-keys items-by-vals)
                                         vals
                                         (map value-fn))
                                    (-> v items-by-vals value-fn))))
         :on-input-change      (fn [_evt v] (reset! state v))
         :renderInput          (fn [^js params]
                                 (set! (.-variant params) variant)
                                 (set! (.-label params) label)
                                 (set! (.-required params) (boolean required))
                                 #_(set! (.-shrink (.-InputLabelProps params))
                                         (boolean (or (and (coll? value) (seq value))
                                                      (seq @state))))
                                 (when required
                                   (set! (.-required (.-InputLabelProps params)) true))

                                 (when (and required (not value))
                                   (set! (.-error (.-InputLabelProps params)) true))
                                 (r/create-element TextField params))
         :getOptionLabel (fn [opt]
                           (-> opt items-by-vals label-fn str))
         :options        (->> items
                              (sort-by sort-fn sort-cmp)
                              (map (comp pr-str value-fn)))}
        (when render-option-fn
          {:renderOption render-option-fn}))])))

(defn year-selector [{:keys [label value on-change required years]
                      :as   props}]
  (let [years (or years (range 1850 (inc (.getFullYear (js/Date.)))))]
    [autocomplete
     (merge props
            {:label     label
             :items     (map #(hash-map :label % :value %) years)
             :on-change on-change
             :sort-cmp  utils/reverse-cmp
             :value     value
             :required  required})]))
