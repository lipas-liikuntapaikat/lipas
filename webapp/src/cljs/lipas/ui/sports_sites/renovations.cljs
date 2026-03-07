(ns lipas.ui.sports-sites.renovations
  (:require ["@mui/material/Box$default" :as Box]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/Card$default" :as Card]
            ["@mui/material/CardContent$default" :as CardContent]
            ["@mui/material/Divider$default" :as Divider]
            ["@mui/material/GridLegacy$default" :as Grid]
            ["@mui/material/IconButton$default" :as IconButton]
            ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/InputLabel$default" :as InputLabel]
            ["@mui/material/Popover$default" :as Popover]
            ["@mui/material/Stack$default" :as Stack]
            ["@mui/material/TextField$default" :as TextField]
            ["@mui/material/Typography$default" :as Typography]
            [lipas.data.sports-sites.renovations :as renovations-data]
            [lipas.ui.components.autocompletes :as autocompletes]
            [lipas.ui.components.dialogs :as dialogs]
            [lipas.ui.components.selects :as selects]
            [lipas.ui.components.text-fields :as text-fields]
            [clojure.string :as str]
            [reagent.core :as r]))

;; ---------------------------------------------------------------------------
;; Single-entry dialog (Add / Edit)
;; ---------------------------------------------------------------------------

(defn- renovation-dialog
  "Dialog for adding or editing a single renovation entry."
  [{:keys [tr locale dialog-state on-save]}]
  (let [{:keys [open? entry editing?]} @dialog-state
        type-label (if editing?
                     (tr :lipas.sports-site/edit-renovation)
                     (tr :lipas.sports-site/add-renovation))
        char-count (count (get-in entry [:description locale] ""))
        update-entry (fn [field value]
                       (swap! dialog-state assoc-in [:entry field] value))
        save-enabled? (and (:year entry) (:type entry))]
    [dialogs/dialog
     {:title type-label
      :open? open?
      :max-width "sm"
      :on-save #(when save-enabled? (on-save))
      :on-close #(swap! dialog-state assoc :open? false)
      :save-enabled? save-enabled?
      :save-label "Ok"
      :cancel-label (tr :actions/cancel)}

     [:> Stack {:spacing 3 :sx {:mt 1}}

      ;; Year
      [autocompletes/year-selector
       {:label (tr :lipas.sports-site/renovation-year)
        :value (:year entry)
        :on-change #(update-entry :year %)
        :deselect? true}]

      ;; Type
      [selects/select
       {:label (tr :lipas.sports-site/renovation-type)
        :value (:type entry)
        :items (seq renovations-data/types)
        :value-fn first
        :label-fn (comp locale second)
        :on-change #(update-entry :type %)}]

      ;; Description
      [text-fields/text-field
       {:label (tr :lipas.sports-site/renovation-description)
        :value (get-in entry [:description locale] "")
        :multiline true
        :rows 4
        :helper-text (str char-count " / 2000")
        :inputProps {:maxLength 2000}
        :on-change #(update-entry :description
                                  (assoc (:description entry) locale %))}]]]))

;; ---------------------------------------------------------------------------
;; Read-only card for a single entry
;; ---------------------------------------------------------------------------

(defn- renovation-card
  "Read-only card for a single renovation entry, with optional edit/delete actions."
  [{:keys [locale entry on-edit on-delete]}]
  (r/with-let [anchor-el (r/atom nil)]
    (let [type-label (get-in renovations-data/types [(:type entry) locale])
          desc (get-in entry [:description locale])
          popover-open? (some? @anchor-el)]
      [:<>
       [:> Card {:variant "outlined" :sx {:mb 1}
                 :on-mouse-enter #(when (not (str/blank? desc))
                                    (reset! anchor-el (.-currentTarget %)))
                 :on-mouse-leave #(reset! anchor-el nil)}
        [:> CardContent {:sx {:py 1 "&:last-child" {:pb 1}}}
         [:> Stack {:direction "row" :justify-content "space-between"
                    :align-items "flex-start"}

          ;; Text content
          [:> Box {:sx {:min-width 0 :flex 1}}
           [:> Typography {:variant "subtitle2"}
            (str (:year entry) " \u2013 " (or type-label ""))]
           (when-not (str/blank? desc)
             [:> Typography {:variant "body2"
                             :sx {:color "text.secondary"
                                  :overflow "hidden"
                                  :text-overflow "ellipsis"
                                  :white-space "nowrap"
                                  :mt 0.5}}
              desc])]

          ;; Action buttons (edit mode only)
          (when (or on-edit on-delete)
            [:> Stack {:direction "row" :sx {:ml 1 :flex-shrink 0}}
             (when on-edit
               [:> IconButton {:size "small" :on-click on-edit}
                [:> Icon {:fontSize "small" :color "primary"} "edit"]])
             (when on-delete
               [:> IconButton {:size "small" :on-click on-delete}
                [:> Icon {:fontSize "small" :color "error"} "delete"]])])]]]

       ;; Description popover
       (when popover-open?
         [:> Popover
          {:open true
           :anchor-el @anchor-el
           :on-close #(reset! anchor-el nil)
           :disable-restore-focus true
           :sx {:pointer-events "none"}
           :anchor-origin {:vertical "bottom" :horizontal "left"}
           :transform-origin {:vertical "top" :horizontal "left"}}
          [:> Box {:sx {:p 2 :max-width 400}}
           [:> Typography {:variant "subtitle2" :sx {:mb 0.5}}
            (str (:year entry) " \u2013 " (or type-label ""))]
           [:> Typography {:variant "body2"
                           :sx {:color "text.secondary"
                                :white-space "pre-wrap"}}
            desc]]])])))

;; ---------------------------------------------------------------------------
;; Main component
;; ---------------------------------------------------------------------------

(defn format-summary
  "Format a summary string for display."
  [entries locale]
  (when (seq entries)
    (let [grouped (group-by :type entries)]
      (->> grouped
           (map (fn [[type-key items]]
                  (let [type-label (get-in renovations-data/types [type-key locale] type-key)
                        years (->> items (map :year) (filter some?) sort (map str))]
                    (when (seq years)
                      (str type-label ": " (str/join ", " years))))))
           (filter some?)
           (str/join " | ")))))

(defn renovations-field
  "Component showing renovations with read-only card list and single-entry add/edit dialog."
  [{:keys [tr read-only? value on-change]}]
  (r/with-let [dialog-state (r/atom {:open? false :entry {} :editing? false :edit-idx nil})]
    (let [locale (tr)
          label (tr :lipas.sports-site/renovations)
          ;; Pair each entry with its original index, then sort by year desc
          indexed-entries (->> (or value [])
                               (map-indexed (fn [i e] (assoc e ::idx i)))
                               (sort-by :year (comp - compare)))
          open-add (fn []
                     (reset! dialog-state
                             {:open? true
                              :editing? false
                              :edit-idx nil
                              :entry {:year nil :type nil :description {}}}))
          open-edit (fn [idx entry]
                      (reset! dialog-state
                              {:open? true
                               :editing? true
                               :edit-idx idx
                               :entry entry}))
          delete-entry (fn [idx]
                         (let [updated (into [] (concat (subvec (or value []) 0 idx)
                                                        (subvec (or value []) (inc idx))))]
                           (on-change updated)))
          save-entry (fn []
                       (let [{:keys [editing? edit-idx entry]} @dialog-state
                             clean-entry (select-keys entry [:year :type :description])]
                         (if editing?
                           (on-change (assoc (vec (or value [])) edit-idx clean-entry))
                           (on-change (conj (vec (or value [])) clean-entry))))
                       (swap! dialog-state assoc :open? false))]

      [:<>
       ;; Dialog (edit mode only)
       (when (and (not read-only?) (:open? @dialog-state))
         [renovation-dialog
          {:tr tr
           :locale locale
           :dialog-state dialog-state
           :on-save save-entry}])

       ;; Label
       [:> InputLabel
        {:shrink true
         :margin "dense"
         :style {:transform "translate(0px, -1.5px) scale(0.75)"
                 :color "rgba(0, 0, 0, 0.88)"}}
        label]

       ;; Cards list
       (if (seq indexed-entries)
         [:> Box {:sx {:mt 2.5 :mb 1}}
          (doall
            (for [entry indexed-entries]
              (let [idx (::idx entry)
                    clean (dissoc entry ::idx)]
                ^{:key idx}
                [renovation-card
                 (cond-> {:locale locale :entry clean}
                   (not read-only?) (assoc :on-edit #(open-edit idx clean)
                                           :on-delete #(delete-entry idx)))])))]
         [:> Typography {:variant "body1"
                         :sx {:line-height "1.1876em"
                              :padding-top "1.1876em"
                              :padding-bottom "6px"}}
          "-"])

       ;; Divider + Add button (edit mode)
       (if read-only?
         [:> Divider {:style {:border-top "1px dotted" :color "rgba(0, 0, 0, 0.12)"}}]
         [:> Box {:sx {:mt 1}}
          [:> Button
           {:size "small"
            :start-icon (r/as-element [:> Icon "add"])
            :on-click open-add}
           (tr :lipas.sports-site/add-renovation)]])])))
