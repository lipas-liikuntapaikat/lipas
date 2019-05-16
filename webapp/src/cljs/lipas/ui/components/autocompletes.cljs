(ns lipas.ui.components.autocompletes
  (:require
   [cljsjs.react-autosuggest]
   [clojure.spec.alpha :as s]
   [clojure.string :refer [trim] :as string]
   [goog.object :as gobj]
   [lipas.ui.mui :as mui]
   [lipas.ui.utils :as utils]
   [react-select :as ReactSelect]
   [reagent.core :as r]))

(def lower-case (fnil string/lower-case ""))

(defn simple-matches [items label-fn s]
  (->> items
       (filter #(string/includes?
                 (lower-case (label-fn %))
                 (lower-case s)))))

(defn js->clj* [x]
  (js->clj x :keywordize-keys true))

(defn hack-input [props]
  (let [props (js->clj* props)
        ref   (:ref props)
        label (:label props)]
    (r/as-element
     [mui/text-field
      {:label      label
       :inputRef   ref
       :InputProps (dissoc props :ref :label)}])))

(defn hack-container [opts]
  (r/as-element
   [mui/paper (merge (js->clj* (gobj/get opts "containerProps")))
    (gobj/get opts "children")]))

(defn hack-item [label-fn label-style-fn item]
  (let [item  (js->clj* item)
        label (label-fn item)]
    (r/as-element
     [mui/menu-item {:component "div"}
      (label-style-fn item label)])))

(defn autocomplete
  [{:keys [label items value value-fn label-fn
           suggestion-fn on-change multi? spacing
           items-label show-all? label-style-fn sort-fn]
    :or   {label-fn       :label
           suggestion-fn  (partial simple-matches items label-fn)
           label-style-fn (fn [item label] label)
           sort-fn        label-fn
           value-fn       :value
           multi?         true
           spacing        0}}]

  (r/with-let [items-m     (utils/index-by value-fn items)
               id          (r/atom (gensym))
               value       (r/atom (or value []))
               input-value (r/atom "")
               suggs       (r/atom (map value-fn items))]

    [mui/grid {:container true :spacing spacing}

     ;; Input field
     [mui/grid {:item true}
      [:> js/Autosuggest
       {:id                 @id
        :suggestions        (sort-by sort-fn @suggs)
        :getSuggestionValue #(label-fn (js->clj* %1))

        :shouldRenderSuggestions (if show-all?
                                   (constantly true)
                                   (comp boolean not-empty))


        :onSuggestionsFetchRequested #(reset! suggs (suggestion-fn
                                                     (gobj/get % "value")))

        :onSuggestionsClearRequested #(reset! suggs [])
        :renderSuggestion            (partial hack-item label-fn label-style-fn)
        :renderSuggestionsContainer  hack-container

        :onSuggestionSelected #(let [v (-> %2
                                           (gobj/get "suggestion")
                                           js->clj*)]
                                 (if multi?
                                   (swap! value conj (value-fn v))
                                   (reset! value [(value-fn v)]))
                                 (on-change @value)
                                 (reset! input-value ""))

        :renderInputComponent hack-input
        :inputProps           {:label (or label "")
                               :value (or @input-value "")

                               :onChange #(reset! input-value
                                                  (gobj/get %2 "newValue"))}

        :theme {:suggestionsList
                {:list-style-type "none"
                 :padding         0
                 :margin          0}}}]]

     ;; Selected values chips
     (into
      [mui/grid {:container true
                 :spacing   spacing
                 :style     {:margin-top :auto}}
       (when (and items-label (not-empty @value))
         [mui/grid {:item true :xs 12}
          [mui/typography {:variant "body2"}
           items-label]])]
      (for [item (sort-by label-fn (vals (select-keys items-m @value)))
            :let [v (value-fn item)]]
        [mui/grid {:item true}
         [mui/chip
          {:label     (label-fn item)
           :on-delete #(do (swap! value (fn [old-value]
                                          (into (empty old-value)
                                                (remove #{v} old-value))))
                           (on-change @value))}]]))]))

(defn hack-item2 [label-fn label-style-fn item item-props]
  (let [item  (js->clj* item)
        label (label-fn item)]
    (r/as-element
     [mui/list-item (js->clj* item-props)
      [mui/list-item-text
       (label-style-fn item label)]])))

(defn error? [spec value required]
  (if (and spec (or value required))
    ((complement s/valid?) spec value)
    false))

(defn ->opt [label-fn value-fn item]
  {:label (label-fn item) :value (value-fn item)})

(defn autocomplete2
  [{:keys [label items value value-fn label-fn on-change spec multi?
           required helper-text]
    :or   {label-fn       :label
           label-style-fn (fn [item label] label)
           sort-fn        label-fn
           value-fn       :value}}]
  (r/with-let [state   (r/atom value)]
    [:> (if multi? js/ReactSelect.MultipleSelect js/ReactSelect.SingleSelect)
     {(if multi?
        :values
        :value)    @state
      :label       label
      :helper-text helper-text
      :on-change   (fn [v]
                     (reset! state v)
                     (on-change (js->clj v)))
      :options     (->> items
                        (map (partial ->opt label-fn value-fn))
                        (sort-by :label))
      :required    required
      :style       {:font-family "Lato"}}]))

(comment
  {:getOptionLabel (fn [opt] (label-fn (js->clj opt :keywordize-keys true)))
   :getOptionValue (fn [opt] (value-fn (js->clj opt :keywordize-keys true)))})
