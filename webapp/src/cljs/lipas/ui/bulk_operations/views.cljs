(ns lipas.ui.bulk-operations.views
  (:require ["@mui/material/Alert$default" :as Alert]
            ["@mui/material/Box$default" :as Box]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/Checkbox$default" :as Checkbox]
            ["@mui/material/CircularProgress$default" :as CircularProgress]
            ["@mui/material/Table$default" :as Table]
            ["@mui/material/TableBody$default" :as TableBody]
            ["@mui/material/TableCell$default" :as TableCell]
            ["@mui/material/TableContainer$default" :as TableContainer]
            ["@mui/material/TableHead$default" :as TableHead]
            ["@mui/material/TableRow$default" :as TableRow]
            [lipas.schema.sports-sites :as sites-schema]
            [lipas.ui.bulk-operations.events :as events]
            [lipas.ui.bulk-operations.subs :as subs]
            [lipas.ui.components.autocompletes :as ac]
            [lipas.ui.components.text-fields :as text-fields]
            [lipas.ui.mui :as mui]
            [re-frame.core :as rf]
            [reagent.core :as r]))

;; Navigation buttons component
(defn navigation-buttons [tr current-step selected-count selected-fields-count on-cancel]
  [:> Box {:sx {:display "flex" :justify-content "space-between"}}
   [:> Box
    (when (pos? current-step)
      [:> Button {:variant "outlined"
                  :on-click #(rf/dispatch [::events/set-current-step (dec current-step)])}
       (tr :actions/back)])]

   [:> Box {:sx {:display "flex" :gap 2}}
    [:> Button {:variant "outlined"
                :on-click on-cancel}
     (tr :actions/cancel)]

    (case current-step
      0 [:> Button {:variant "contained"
                    :color "primary"
                    :disabled (zero? selected-count)
                    :on-click #(rf/dispatch [::events/set-current-step 1])}
         (tr :actions/next)]

      1 [:> Button {:variant "contained"
                    :color "primary"
                    :disabled (zero? selected-fields-count)
                    :on-click #(rf/dispatch [::events/execute-bulk-update
                                             {:on-success (fn [_]
                                                            (rf/dispatch [::events/get-editable-sites]))
                                              :on-failure nil}])}
         (str (tr :lipas.bulk-operations/update-n-sites selected-count))]

      nil)]])

;; Step 1: Select sites
(defn step-select-sites [tr selected-count on-cancel]
  (let [editable-sites @(rf/subscribe [::subs/filtered-editable-sites])
        selected-sites @(rf/subscribe [::subs/selected-sites])
        all-selected? @(rf/subscribe [::subs/all-sites-selected?])
        filters @(rf/subscribe [::subs/sites-filters])
        types @(rf/subscribe [:lipas.ui.sports-sites.subs/active-types])
        admins @(rf/subscribe [:lipas.ui.sports-sites.subs/admins])
        owners @(rf/subscribe [:lipas.ui.sports-sites.subs/owners])
        locale (tr)]
    [:> Box
     [:> Box {:sx {:mb 3}}
      [navigation-buttons tr 0 selected-count 0 on-cancel]]

     [:> Box {:sx {:mb 2 :display "flex" :justify-content "space-between" :align-items "center"}}
      [mui/typography {:variant "h6"}
       (if (pos? selected-count)
         (str (tr :lipas.bulk-operations/n-sites-selected selected-count))
         (tr :lipas.bulk-operations/select-sites-to-update))]
      [:> Box
       [:> Button {:variant "outlined"
                   :sx {:mr 1}
                   :on-click #(rf/dispatch [::events/select-all-sites (map :lipas-id editable-sites)])}
        (tr :actions/select-all)]
       [:> Button {:variant "outlined"
                   :on-click #(rf/dispatch [::events/deselect-all-sites])}
        (tr :actions/deselect-all)]]]

     [mui/expansion-panel {:sx {:mb 2}}
      [mui/expansion-panel-summary
       {:expandIcon (r/as-element [mui/icon "expand_more"])}
       [mui/typography (tr :actions/filter)]]
      [mui/expansion-panel-details
       [mui/grid {:container true :spacing 2}
        [mui/grid {:item true :xs 12 :md 4}
         [text-fields/text-field-controlled
          {:label (tr :search/search)
           :value (:search-text filters)
           :on-change #(rf/dispatch [::events/set-sites-filter :search-text %])}]]

        [mui/grid {:item true :xs 12 :md 2}
         (r/as-element
          [ac/type-selector
           {:value (:type-code filters)
            :label (tr :type/name)
            :onChange (fn [_ {:keys [value]}]
                        (rf/dispatch [::events/set-sites-filter :type-code value]))}])]

        [mui/grid {:item true :xs 12 :md 3}
         (r/as-element
          [ac/admin-selector
           {:value (:admin filters)
            :label (tr :lipas.sports-site/admin)
            :onChange (fn [_ {:keys [value]}]
                        (rf/dispatch [::events/set-sites-filter :admin value]))}])]

        [mui/grid {:item true :xs 12 :md 3}
         (r/as-element
          [ac/owner-selector
           {:value (:owner filters)
            :label (tr :lipas.sports-site/owner)
            :onChange (fn [_ {:keys [value]}]
                        (rf/dispatch [::events/set-sites-filter :owner value]))}])]]]]

     ;; Table container with its own horizontal scroll
     [:> TableContainer {:sx {:overflow-x "auto" :width "100%"}}
      [:> Table {:size "small"}
       [:> TableHead
        [:> TableRow
         [:> TableCell {:padding "checkbox"}
          [:> Checkbox {:checked all-selected?
                        :on-change #(if all-selected?
                                      (rf/dispatch [::events/deselect-all-sites])
                                      (rf/dispatch [::events/select-all-sites (map :lipas-id editable-sites)]))}]]
         [:> TableCell (tr :lipas.sports-site/name)]
         [:> TableCell (tr :type/name)]
         [:> TableCell (tr :lipas.sports-site/admin)]
         [:> TableCell (tr :lipas.sports-site/owner)]
         [:> TableCell (tr :lipas.sports-site/email-public)]
         [:> TableCell (tr :lipas.sports-site/phone-number)]
         [:> TableCell (tr :lipas.sports-site/www)]
         [:> TableCell (tr :lipas.sports-site/reservations-link)]]]

       [:> TableBody
        (for [site editable-sites]
          [:> TableRow {:key (:lipas-id site)
                        :selected (contains? selected-sites (:lipas-id site))}
           [:> TableCell {:padding "checkbox"}
            [:> Checkbox {:checked (contains? selected-sites (:lipas-id site))
                          :on-change #(rf/dispatch [::events/toggle-site-selection (:lipas-id site)])}]]
           [:> TableCell (:name site)]
           [:> TableCell (get-in types [(get-in site [:type :type-code]) :name locale])]
           [:> TableCell (get-in admins [(:admin site) locale])]
           [:> TableCell (get-in owners [(:owner site) locale])]
           [:> TableCell (:email site)]
           [:> TableCell (:phone-number site)]
           [:> TableCell (:www site)]
           [:> TableCell (:reservations-link site)]])]]]

     [:> Box {:sx {:mt 3}}
      [navigation-buttons tr 0 selected-count 0 on-cancel]]]))

;; Step 2: Enter contact information
(defn step-enter-info [tr selected-count on-cancel]
  (let [update-form @(rf/subscribe [::subs/bulk-update-form])
        selected-fields @(rf/subscribe [::subs/selected-fields])
        all-fields #{:email :phone-number :www :reservations-link}
        all-fields-selected? (= selected-fields all-fields)]
    [:> Box
     [:> Box {:sx {:mb 3}}
      [navigation-buttons tr 1 selected-count (count selected-fields) on-cancel]]

     [:> Alert {:severity "info" :sx {:mb 3}}
      (tr :lipas.bulk-operations/selective-update-info)]

     ;; Header with select all/none buttons
     [:> Box {:sx {:display "flex" :justify-content "space-between" :align-items "center" :mb 2}}
      [mui/typography {:variant "body1"}
       (tr :lipas.bulk-operations/select-fields-to-update)]
      [:> Box {:sx {:display "flex" :gap 1}}
       [:> Button {:variant "text"
                   :size "small"
                   :on-click #(rf/dispatch [::events/set-selected-fields all-fields])}
        (tr :actions/select-all)]
       [:> Button {:variant "text"
                   :size "small"
                   :on-click #(rf/dispatch [::events/set-selected-fields #{}])}
        (tr :actions/deselect-all)]]]

     [mui/grid {:container true :spacing 2}
      [mui/grid {:item true :xs 12 :md 6}
       [mui/paper {:sx {:p 2 :border (if (contains? selected-fields :email) 2 1)
                        :border-color (if (contains? selected-fields :email) "primary.main" "divider")
                        :background-color (when-not (contains? selected-fields :email) "action.disabledBackground")}}
        [:> Box {:sx {:display "flex" :align-items "flex-start" :gap 1}}
         [:> Box {:sx {:pt 1}}
          [mui/tooltip {:title (tr :lipas.bulk-operations/check-to-update-field)}
           [:> Checkbox {:checked (contains? selected-fields :email)
                         :color "primary"
                         :on-change #(rf/dispatch [::events/toggle-field-selection :email])}]]]
         [:> Box {:sx {:flex 1}}
          [text-fields/text-field-controlled
           {:label (tr :lipas.sports-site/email-public)
            :value (:email update-form)
            :spec sites-schema/email
            :disabled (not (contains? selected-fields :email))
            :helper-text (if (contains? selected-fields :email)
                           (if (seq (:email update-form))
                             (str (tr :lipas.bulk-operations/will-update-to) " " (:email update-form))
                             (tr :lipas.bulk-operations/will-clear-field))
                           (tr :lipas.bulk-operations/field-will-not-change))
            :on-change #(rf/dispatch [::events/set-bulk-update-field :email %])}]]]]]

      [mui/grid {:item true :xs 12 :md 6}
       [mui/paper {:sx {:p 2 :border (if (contains? selected-fields :phone-number) 2 1)
                        :border-color (if (contains? selected-fields :phone-number) "primary.main" "divider")
                        :background-color (when-not (contains? selected-fields :phone-number) "action.disabledBackground")}}
        [:> Box {:sx {:display "flex" :align-items "flex-start" :gap 1}}
         [:> Box {:sx {:pt 1}}
          [mui/tooltip {:title (tr :lipas.bulk-operations/check-to-update-field)}
           [:> Checkbox {:checked (contains? selected-fields :phone-number)
                         :color "primary"
                         :on-change #(rf/dispatch [::events/toggle-field-selection :phone-number])}]]]
         [:> Box {:sx {:flex 1}}
          [text-fields/text-field-controlled
           {:label (tr :lipas.sports-site/phone-number)
            :value (:phone-number update-form)
            :spec sites-schema/phone-number
            :disabled (not (contains? selected-fields :phone-number))
            :helper-text (if (contains? selected-fields :phone-number)
                           (if (seq (:phone-number update-form))
                             (str (tr :lipas.bulk-operations/will-update-to) " " (:phone-number update-form))
                             (tr :lipas.bulk-operations/will-clear-field))
                           (tr :lipas.bulk-operations/field-will-not-change))
            :on-change #(rf/dispatch [::events/set-bulk-update-field :phone-number %])}]]]]]

      [mui/grid {:item true :xs 12 :md 6}
       [mui/paper {:sx {:p 2 :border (if (contains? selected-fields :www) 2 1)
                        :border-color (if (contains? selected-fields :www) "primary.main" "divider")
                        :background-color (when-not (contains? selected-fields :www) "action.disabledBackground")}}
        [:> Box {:sx {:display "flex" :align-items "flex-start" :gap 1}}
         [:> Box {:sx {:pt 1}}
          [mui/tooltip {:title (tr :lipas.bulk-operations/check-to-update-field)}
           [:> Checkbox {:checked (contains? selected-fields :www)
                         :color "primary"
                         :on-change #(rf/dispatch [::events/toggle-field-selection :www])}]]]
         [:> Box {:sx {:flex 1}}
          [text-fields/text-field-controlled
           {:label (tr :lipas.sports-site/www)
            :value (:www update-form)
            :spec sites-schema/www
            :disabled (not (contains? selected-fields :www))
            :helper-text (if (contains? selected-fields :www)
                           (if (seq (:www update-form))
                             (str (tr :lipas.bulk-operations/will-update-to) " " (:www update-form))
                             (tr :lipas.bulk-operations/will-clear-field))
                           (tr :lipas.bulk-operations/field-will-not-change))
            :on-change #(rf/dispatch [::events/set-bulk-update-field :www %])}]]]]]

      [mui/grid {:item true :xs 12 :md 6}
       [mui/paper {:sx {:p 2 :border (if (contains? selected-fields :reservations-link) 2 1)
                        :border-color (if (contains? selected-fields :reservations-link) "primary.main" "divider")
                        :background-color (when-not (contains? selected-fields :reservations-link) "action.disabledBackground")}}
        [:> Box {:sx {:display "flex" :align-items "flex-start" :gap 1}}
         [:> Box {:sx {:pt 1}}
          [mui/tooltip {:title (tr :lipas.bulk-operations/check-to-update-field)}
           [:> Checkbox {:checked (contains? selected-fields :reservations-link)
                         :color "primary"
                         :on-change #(rf/dispatch [::events/toggle-field-selection :reservations-link])}]]]
         [:> Box {:sx {:flex 1}}
          [text-fields/text-field-controlled
           {:label (tr :lipas.sports-site/reservations-link)
            :value (:reservations-link update-form)
            :spec sites-schema/reservations-link
            :disabled (not (contains? selected-fields :reservations-link))
            :helper-text (if (contains? selected-fields :reservations-link)
                           (if (seq (:reservations-link update-form))
                             (str (tr :lipas.bulk-operations/will-update-to) " " (:reservations-link update-form))
                             (tr :lipas.bulk-operations/will-clear-field))
                           (tr :lipas.bulk-operations/field-will-not-change))
            :on-change #(rf/dispatch [::events/set-bulk-update-field :reservations-link %])}]]]]]]

     [:> Box {:sx {:mt 3}}
      [mui/typography {:variant "body2" :color "text.secondary"}
       (str (tr :lipas.bulk-operations/will-update-n-sites selected-count) " "
            (tr :lipas.bulk-operations/selected-fields-count (count selected-fields)))]]

     [:> Box {:sx {:mt 3}}
      [navigation-buttons tr 1 selected-count (count selected-fields) on-cancel]]]))

;; Step 3: Summary
(defn step-summary [tr on-cancel]
  (let [update-results @(rf/subscribe [::subs/update-results])
        update-form @(rf/subscribe [::subs/bulk-update-form])
        selected-fields @(rf/subscribe [::subs/selected-fields])
        editable-sites @(rf/subscribe [::subs/editable-sites])
        updated-site-ids (set (:updated-sites update-results))
        updated-sites (filter #(contains? updated-site-ids (:lipas-id %)) editable-sites)]
    [:> Box
     [:> Alert {:severity "success" :sx {:mb 3}}
      (tr :lipas.bulk-operations/update-completed)]

     [mui/typography {:variant "h6" :sx {:mb 2}}
      (tr :lipas.bulk-operations/updated-fields)]

     [mui/list
      (when (and (contains? selected-fields :email) (:email update-form))
        [mui/list-item
         [mui/list-item-text
          {:primary (tr :lipas.sports-site/email-public)
           :secondary (:email update-form)}]])

      (when (and (contains? selected-fields :phone-number) (:phone-number update-form))
        [mui/list-item
         [mui/list-item-text
          {:primary (tr :lipas.sports-site/phone-number)
           :secondary (:phone-number update-form)}]])

      (when (and (contains? selected-fields :www) (:www update-form))
        [mui/list-item
         [mui/list-item-text
          {:primary (tr :lipas.sports-site/www)
           :secondary (:www update-form)}]])

      (when (and (contains? selected-fields :reservations-link) (:reservations-link update-form))
        [mui/list-item
         [mui/list-item-text
          {:primary (tr :lipas.sports-site/reservations-link)
           :secondary (:reservations-link update-form)}]])]

     ;; Updated sites list
     [mui/typography {:variant "h6" :sx {:mt 3 :mb 2}}
      (str (tr :lipas.bulk-operations/updated-sites-list) " (" (:total-updated update-results) ")")]

     [:> Box {:sx {:max-height 300 :overflow-y "auto" :border 1 :border-color "divider" :border-radius 1 :p 2}}
      [mui/list {:dense true}
       (for [site updated-sites]
         [mui/list-item {:key (:lipas-id site)}
          [mui/list-item-text
           {:primary (:name site)
            :secondary (str "ID: " (:lipas-id site))}]])]]

     [:> Box {:sx {:mt 3 :display "flex" :gap 2}}
      [:> Button {:variant "contained"
                  :color "primary"
                  :on-click on-cancel}
       (tr :actions/done)]
      [:> Button {:variant "outlined"
                  :on-click #(rf/dispatch [::events/reset])}
       (tr :lipas.bulk-operations/update-more-sites)]]]))

;; Main component with stepper
(defn main
  [{:keys [title description on-submit on-cancel]}]
  (let [tr @(rf/subscribe [:lipas.ui.subs/translator])
        current-step @(rf/subscribe [::subs/current-step])
        selected-count @(rf/subscribe [::subs/selected-sites-count])
        loading? @(rf/subscribe [::subs/loading?])
        error @(rf/subscribe [::subs/error])]

    [mui/grid {:container true :spacing 2 :sx {:p 1}}
     ;; Header
     [mui/grid {:item true :xs 12}
      [mui/paper {:sx {:p 2}}
       [mui/typography {:variant "h5" :sx {:mb 2}}
        (or title (tr :lipas.org/bulk-operations))]
       [mui/typography {:variant "body1"}
        (or description (tr :lipas.org/bulk-operations-description))]]]

     ;; Error display
     (when error
       [mui/grid {:item true :xs 12}
        [:> Alert {:severity "error"}
         (str "Error: " (or (:status-text error) "Failed to load data"))]])

     ;; Stepper
     [mui/grid {:item true :xs 12}
      [mui/paper {:sx {:p 3}}
       [mui/stepper {:active-step current-step :sx {:mb 3}}
        [mui/step
         [mui/step-label (tr :lipas.bulk-operations/step-select-sites)]]
        [mui/step
         [mui/step-label (tr :lipas.bulk-operations/step-enter-info)]]
        [mui/step
         [mui/step-label (tr :lipas.bulk-operations/step-summary)]]]

       ;; Step content
       (if loading?
         [:> Box {:sx {:display "flex" :justify-content "center" :p 4}}
          [:> CircularProgress]]

         (case current-step
           0 [step-select-sites tr selected-count on-cancel]
           1 [step-enter-info tr selected-count on-cancel]
           2 [step-summary tr on-cancel]
           nil))]]]))
