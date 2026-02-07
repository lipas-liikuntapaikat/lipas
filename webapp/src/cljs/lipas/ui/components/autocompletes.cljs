(ns lipas.ui.components.autocompletes
  (:require ["@mui/material/Autocomplete$default" :as Autocomplete]
            ["@mui/material/TextField$default" :as TextField]
            [lipas.ui.mui :as mui]
            [lipas.ui.utils :as utils]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [reagent.hooks :as hooks]))

(defn- merge-props
  "Merges Clojure maps and JS objects into a single JS object."
  [& props]
  (apply js/Object.assign #js {}
         (map #(if (map? %) (clj->js %) %) props)))

;; TODO: Deprecate/replace this, this has bad choices:
;; 1. :value is going through clj->js
;; 2. :options goes through pr-str
;; 3. multiple "helpful" options with different names than the MUI component
;;    which magically effect multiple things
;; 4. items-by-vals ???
(defn autocomplete
  [{:keys [label items value value-fn label-fn key-fn on-change sort-fn spec multi?
           required helper-text deselect? sort-cmp render-option-fn disabled variant]
    :or {label-fn :label
         disabled false
         sort-fn label-fn
         sort-cmp compare
         value-fn :value
         variant "standard"}}]
  (let [items-by-vals (utils/index-by (comp pr-str value-fn) items)]
    (r/with-let [state (r/atom "")]
      [:<>
       [mui/autocomplete
        (merge
         {:multiple multi?
          :value (if multi?
                   (clj->js (map pr-str value))
                   (pr-str value))
          :disabled disabled
          :label label
          :disableCloseOnSelect multi?
          :disableClearable (not deselect?)
          :on-change (fn [_evt v]
                       (on-change
                        (if multi?
                          (->> v
                               (select-keys items-by-vals)
                               vals
                               (map value-fn))
                          (-> v items-by-vals value-fn))))
          :on-input-change (fn [_evt v] (reset! state v))
          :renderInput (fn [^js params]
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
          :options (->> items
                        (sort-by sort-fn sort-cmp)
                        (map (comp pr-str value-fn)))}
         (when render-option-fn
           {:renderOption render-option-fn})
         (when key-fn
           {:getOptionKey (fn [opt]
                            (-> opt items-by-vals key-fn))}))]
       (when helper-text
         [mui/form-helper-text helper-text])])))

(defn year-selector [{:keys [label value on-change required years]
                      :as props}]
  (let [years (or years (range 1850 (inc (.getFullYear (js/Date.)))))]
    [autocomplete
     (merge props
            {:label label
             :items (map #(hash-map :label % :value %) years)
             :on-change on-change
             :sort-cmp utils/reverse-cmp
             :value value
             :required required})]))

(defn safe-name [x]
  (if (keyword? x)
    (name x)
    x))

(defn safe-value [x]
  (if (map? x)
    (:value x)
    x))

(r/defc autocomplete2
  "Helper for version of autocomplete where:

  :options should be a cljs sequential collection with {:value ... :label ...}"
  [{:keys [options input-props label] :as props}]
  (let [value->label (hooks/use-memo (fn []
                                       (into {} (map (juxt (comp safe-name :value) :label)
                                                     options)))
                                     [options])
        js-options (hooks/use-memo (fn [] (to-array options))
                                   [options])]
    [:> Autocomplete
     (merge-props {:options js-options
                   :renderInput (fn [^js props]
                                  (r/create-element TextField
                                                    (merge-props #js {:label label
                                                                      :variant "standard"}
                                                                 props
                                                                 (clj->js input-props))))
                   :getOptionKey (fn [item] (:value item))
                   :getOptionLabel (fn [item]
                                    ;; This fn is called for both :value and :options
                                     (if (map? item)
                                       (:label item)
                                       (or (get value->label item)
                                           (str item))))
                   :isOptionEqualToValue (fn [option value]
                                           (= (safe-name (:value option))
                                              value))}
                  (clj->js (dissoc props :input-props :label :options)))]))

(r/defc type-selector
  "Type selector using autocomplete2 with all active types"
  [props]
  (let [types @(rf/subscribe [:lipas.ui.sports-sites.subs/active-types])
        locale @(rf/subscribe [:lipas.ui.subs/locale])
        options (hooks/use-memo
                 (fn []
                   (->> types
                        (map (fn [[code type-data]]
                               {:value code
                                :label (get-in type-data [:name locale])}))
                        (sort-by :label)))
                 [types locale])]
    [autocomplete2 (assoc props :options options)]))

(r/defc admin-selector
  "Admin selector using autocomplete2"
  [props]
  (let [admins @(rf/subscribe [:lipas.ui.sports-sites.subs/admins])
        locale @(rf/subscribe [:lipas.ui.subs/locale])
        options (hooks/use-memo
                 (fn []
                   (->> admins
                        (map (fn [[code admin-data]]
                               {:value (name code)
                                :label (get admin-data locale)}))
                        (sort-by :label)))
                 [admins locale])]
    [autocomplete2 (assoc props :options options)]))

(r/defc owner-selector
  "Owner selector using autocomplete2"
  [props]
  (let [owners @(rf/subscribe [:lipas.ui.sports-sites.subs/owners])
        locale @(rf/subscribe [:lipas.ui.subs/locale])
        options (hooks/use-memo
                 (fn []
                   (->> owners
                        (map (fn [[code owner-data]]
                               {:value (name code)
                                :label (get owner-data locale)}))
                        (sort-by :label)))
                 [owners locale])]
    [autocomplete2 (assoc props :options options)]))
