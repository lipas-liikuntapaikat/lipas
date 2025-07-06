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
            [lipas.ui.components :as lui]
            [lipas.ui.components.autocompletes :as ac]
            [lipas.ui.components.selects :as selects]
            [lipas.ui.components.text-fields :as text-fields]
            [lipas.ui.mui :as mui]
            [lipas.ui.subs :as ui-subs]
            [re-frame.core :as rf]
            [uix.core :refer [$]]))

(defn filters-section [tr filters]
  [lui/form-card {:title (tr :actions/filter) :md 12}
   [mui/grid {:container true :spacing 2}
    [mui/grid {:item true :xs 12 :md 4}
     [text-fields/text-field-controlled
      {:label (tr :search/search)
       :value (:search-text filters)
       :on-change #(rf/dispatch [::events/set-sites-filter :search-text %])}]]

    [mui/grid {:item true :xs 12 :md 2}
     [selects/type-selector-single
      {:value (:type-code filters)
       :label (tr :type/name)
       :on-change (fn [v] (rf/dispatch [::events/set-sites-filter :type-code v]))}]]

    [mui/grid {:item true :xs 12 :md 3}
     [selects/admin-selector
      {:value (:admin filters)
       :label (tr :lipas.sports-site/admin)
       :on-change (fn [v] (rf/dispatch [::events/set-sites-filter :admin v]))}]]

    [mui/grid {:item true :xs 12 :md 3}
     [selects/owner-selector
      {:value (:owner filters)
       :label (tr :lipas.sports-site/owner)
       :on-change (fn [v] (rf/dispatch [::events/set-sites-filter :owner v]))}]]]])

(defn sites-table [tr editable-sites selected-sites all-selected?]
  (let [admins @(rf/subscribe [:lipas.ui.sports-sites.subs/admins])
        owners @(rf/subscribe [:lipas.ui.sports-sites.subs/owners])
        locale (tr)]
    [mui/paper {:sx {:p 2}}
     [:> Box {:sx {:mb 2 :display "flex" :justify-content "space-between" :align-items "center"}}
      [mui/typography {:variant "h6"}
       (tr :lipas.bulk-operations/select-sites-to-update)]
      [:> Box
       [:> Button {:variant "outlined"
                   :sx {:mr 1}
                   :on-click #(rf/dispatch [::events/select-all-sites (map :lipas-id editable-sites)])}
        (tr :actions/select-all)]
       [:> Button {:variant "outlined"
                   :on-click #(rf/dispatch [::events/deselect-all-sites])}
        (tr :actions/deselect-all)]]]

     [:> TableContainer
      [:> Table
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
           [:> TableCell (get-in site [:type :name :fi] (get-in site [:type :type-code]))]
           #_[:> TableCell (tr (keyword "lipas.sports-site.admin" (:admin site)))]
           #_[:> TableCell (tr (keyword "lipas.sports-site.owner" (:owner site)))]
           [:> TableCell (get-in admins [(:admin site) locale])]
           [:> TableCell (get-in owners [(:owner site) locale])]
           [:> TableCell (:email site)]
           [:> TableCell (:phone-number site)]
           [:> TableCell (:www site)]
           [:> TableCell (:reservations-link site)]])]]]]))

(defn update-form-section [tr update-form selected-count on-submit on-cancel]
  [lui/form-card {:title (tr :lipas.org/contact-info-update) :md 12}
   [:> Alert {:severity "info" :sx {:mb 2}}
    (tr :lipas.org/bulk-update-info)]

   [mui/grid {:container true :spacing 2}
    [mui/grid {:item true :xs 12 :md 6}
     [text-fields/text-field-controlled
      {:label (tr :lipas.sports-site/email-public)
       :value (:email update-form)
       :spec sites-schema/email
       :helper-text (tr :lipas.org/empty-field-clears)
       :on-change #(rf/dispatch [::events/set-bulk-update-field :email %])}]]

    [mui/grid {:item true :xs 12 :md 6}
     [text-fields/text-field-controlled
      {:label (tr :lipas.sports-site/phone-number)
       :value (:phone-number update-form)
       :spec sites-schema/phone-number
       :helper-text (tr :lipas.org/empty-field-clears)
       :on-change #(rf/dispatch [::events/set-bulk-update-field :phone-number %])}]]

    [mui/grid {:item true :xs 12 :md 6}
     [text-fields/text-field-controlled
      {:label (tr :lipas.sports-site/www)
       :value (:www update-form)
       :spec sites-schema/www
       :helper-text (tr :lipas.org/empty-field-clears)
       :on-change #(rf/dispatch [::events/set-bulk-update-field :www %])}]]

    [mui/grid {:item true :xs 12 :md 6}
     [text-fields/text-field-controlled
      {:label (tr :lipas.sports-site/reservations-link)
       :value (:reservation-link update-form)
       :spec sites-schema/reservation-link
       :helper-text (tr :lipas.org/empty-field-clears)
       :on-change #(rf/dispatch [::events/set-bulk-update-field :reservation-link %])}]]]

   [:> Box {:sx {:mt 2 :display "flex" :gap 2}}
    [:> Button {:variant "contained"
                :color "primary"
                :disabled (zero? selected-count)
                :on-click on-submit}
     (str (tr :actions/update-n-sports-sites selected-count))]

    [:> Button {:variant "outlined"
                :on-click on-cancel}
     (tr :actions/cancel)]]])

(defn main
  [{:keys [title description on-submit on-cancel]}]
  (let [tr @(rf/subscribe [:lipas.ui.subs/translator])
        editable-sites @(rf/subscribe [::subs/filtered-editable-sites])
        selected-sites @(rf/subscribe [::subs/selected-sites])
        selected-count @(rf/subscribe [::subs/selected-sites-count])
        all-selected? @(rf/subscribe [::subs/all-sites-selected?])
        update-form @(rf/subscribe [::subs/bulk-update-form])
        filters @(rf/subscribe [::subs/sites-filters])
        loading? @(rf/subscribe [::subs/loading?])
        error @(rf/subscribe [::subs/error])]

    (if loading?
      [:> Box {:sx {:display "flex" :justify-content "center" :p 4}}
       [:> CircularProgress]]

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

       ;; Update form
       [mui/grid {:item true :xs 12}
        [update-form-section tr update-form selected-count
         (or on-submit #(rf/dispatch [::events/execute-bulk-update {}]))
         on-cancel]]

       ;; Filters
       [mui/grid {:item true :xs 12}
        [filters-section tr filters]]

       ;; Sites table
       [mui/grid {:item true :xs 12}
        [sites-table tr editable-sites selected-sites all-selected?]]])))
