(ns lipas.ui.sports-sites.renovations
  (:require ["@mui/material/Button$default" :as Button]
            ["@mui/material/GridLegacy$default" :as Grid]
            ["@mui/material/IconButton$default" :as IconButton]
            ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/Typography$default" :as Typography]
            [lipas.data.sports-sites.renovations :as renovations-data]
            [lipas.ui.components.autocompletes :as autocompletes]
            [lipas.ui.components.dialogs :as dialogs]
            [lipas.ui.components.selects :as selects]
            [clojure.string :as str]
            [lipas.ui.components.text-fields :as text-fields]
            [reagent.core :as r]))

(defn- renovation-entry-form
  "Form for a single renovation entry inside the dialog."
  [{:keys [tr locale entry on-change on-delete]}]
  [:> Grid {:container true :spacing 2 :align-items "center"
            :sx {:mb 2 :pb 2 :border-bottom "1px solid #eee"}}

   ;; Year
   [:> Grid {:item true :xs 3}
    [autocompletes/year-selector
     {:label (tr :lipas.sports-site/renovation-year)
      :value (:year entry)
      :on-change #(on-change :year %)
      :deselect? true}]]

   ;; Type
   [:> Grid {:item true :xs 4}
    [selects/select
     {:label (tr :lipas.sports-site/renovation-type)
      :value (:type entry)
      :items (seq renovations-data/types)
      :value-fn first
      :label-fn (comp locale second)
      :on-change #(on-change :type %)}]]

   ;; Description
   [:> Grid {:item true :xs 4}
    [text-fields/text-field
     {:label (tr :lipas.sports-site/renovation-description)
      :value (get-in entry [:description locale])
      :multiline true
      :rows 1
      :on-change #(on-change :description (assoc (:description entry) locale %))}]]

   ;; Delete button
   [:> Grid {:item true :xs 1}
    [:> IconButton
     {:on-click on-delete
      :size "small"}
     [:> Icon {:color "error"} "delete"]]]])

(defn- renovations-dialog
  "Dialog for editing renovations list."
  [{:keys [tr locale dialog-state on-save]}]
  (let [{:keys [open? entries]} @dialog-state]
    [dialogs/dialog
     {:title (tr :lipas.sports-site/renovations)
      :open? open?
      :max-width "md"
      :on-save on-save
      :on-close #(swap! dialog-state assoc :open? false)
      :save-enabled? true
      :save-label "Ok"
      :cancel-label (tr :actions/cancel)}

     [:> Grid {:container true :spacing 2}

      ;; Existing entries
      [:> Grid {:item true :xs 12}
       (doall
         (for [[idx entry] (map-indexed vector
                                        (sort-by :year (comp - compare) entries))]
           ^{:key (:id entry)}
           [renovation-entry-form
            {:tr tr
             :locale locale
             :entry entry
             :on-change (fn [field value]
                          (swap! dialog-state update :entries
                                 (fn [es]
                                   (mapv (fn [e]
                                           (if (= (:id e) (:id entry))
                                             (assoc e field value)
                                             e))
                                         es))))
             :on-delete (fn []
                          (swap! dialog-state update :entries
                                 (fn [es]
                                   (vec (remove #(= (:id %) (:id entry)) es)))))}]))]

      ;; Add button
      [:> Grid {:item true :xs 12}
       [:> Button
        {:start-icon (r/as-element [:> Icon "add"])
         :on-click (fn []
                     (swap! dialog-state update :entries
                            (fnil conj [])
                            {:id (gensym)
                             :year nil
                             :type nil
                             :description {}}))}
        (tr :lipas.sports-site/add-renovation)]]]]))

(defn format-summary
  "Format a summary string for display in the form row."
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
  "Component that shows a summary of renovations and opens a dialog for editing."
  [{:keys [tr read-only? value on-change]}]
  (r/with-let [dialog-state (r/atom {:open? false :entries []})]
    (let [locale (tr)
          summary (format-summary value locale)]

      [:<>
       ;; Dialog
       (when (:open? @dialog-state)
         [renovations-dialog
          {:tr tr
           :locale locale
           :dialog-state dialog-state
           :on-save (fn []
                      (let [entries (->> (:entries @dialog-state)
                                         (filter #(and (:year %) (:type %)))
                                         (mapv #(dissoc % :id)))]
                        (on-change entries))
                      (swap! dialog-state assoc :open? false))}])

       ;; Display / trigger
       (if read-only?
         [:> Typography {:variant "body2"}
          (or summary "-")]
         [:> Button
          {:variant "text"
           :size "small"
           :start-icon (r/as-element [:> Icon "edit"])
           :on-click (fn []
                       (reset! dialog-state
                               {:open? true
                                :entries (mapv #(assoc % :id (gensym))
                                               (or value []))}))}
          (or summary (tr :lipas.sports-site/add-renovation))])])))
