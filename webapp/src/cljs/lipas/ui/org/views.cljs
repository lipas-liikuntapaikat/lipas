(ns lipas.ui.org.views
  (:require ["@mui/icons-material/Delete$default" :as DeleteIcon]
            ["@mui/material/Box$default" :as Box]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/MenuItem$default" :as MenuItem]
            ["@mui/material/Select$default" :as Select]
            ["@mui/material/Table$default" :as Table]
            ["@mui/material/TableBody$default" :as TableBody]
            ["@mui/material/TableCell$default" :as TableCell]
            ["@mui/material/TableHead$default" :as TableHead]
            ["@mui/material/TableRow$default" :as TableRow]
            [lipas.ui.components :as lui]
            [lipas.ui.components.autocompletes :as ac]
            [lipas.ui.mui :as mui]
            [lipas.ui.org.events :as events]
            [lipas.ui.org.subs :as subs]
            [lipas.ui.subs :as ui-subs]
            [re-frame.core :as rf]
            [uix.core :refer [$]]
            ["@mui/material/FormControl$default" :as FormControl]
            ["@mui/material/InputLabel$default" :as InputLabel]))

(defn org-view []
  (let [tr @(rf/subscribe [:lipas.ui.subs/translator])

        {:keys [org-id]} (:path @(rf/subscribe [::ui-subs/parameters]))

        org @(rf/subscribe [:lipas.ui.org.subs/user-org-by-id org-id])
        org-users @(rf/subscribe [::subs/org-users])]

    [mui/grid {:container true
               :spacing 2
               :sx {:p 1}}
     [mui/grid {:item true :container true :xs 12 :spacing 1}
      [lui/form-card
       {:title "Organisaatio"
        :md 12}
       [mui/form-group
        {:sx {:gap 1}}
        [lui/text-field
         {:label     (tr :lipas.org/name)
          :value     (:name org)
          :on-change #(rf/dispatch [::events/edit-org [:name] %])}]
        [lui/text-field
         {:label     (tr :lipas.org/phone)
          :value (:phone (:data org))
          :on-change (fn [x] (rf/dispatch [::events/edit-org [:data :phone] x]))}]

        [mui/grid {:item true}
         [mui/button
          {:variant  "contained"
           :color    "secondary"
           ;; TODO:
           :on-click #(rf/dispatch [::events/save-org org])}
          [mui/icon {:sx {:mr 1}} "save"]
          (tr :actions/save)]]]]

      [lui/form-card
       {:title "Käyttäjät"
        :md 12}
       [:> Box
        {:sx {:display "flex"
              :flex-direction "row"}}
        ($ ac/autocomplete2
           {:label (tr :lipas.user/username)
            :options @(rf/subscribe [::subs/all-users-options])})
        [:> FormControl
         [:> InputLabel
          {:id "add-org-user"}
          (tr :lipas.org/org-role)]
         [:> Select
          {:value "org-user"
           :labelId "add-org-user"}
          [:> MenuItem {:value "org-user"} (tr (keyword :lipas.user.permissions.roles.role-names :org-user))]
          [:> MenuItem {:value "org-admin"} (tr (keyword :lipas.user.permissions.roles.role-names :org-admin))]]]
        [:> Button
         {}
         "Lisää"]]
       [:> Table
        [:> TableHead
         [:> TableRow
          [:> TableCell (tr :lipas.user/username)]
          [:> TableCell (tr :lipas.org/org-role)]]]
        [:> TableBody
         (for [item org-users]
           [:> TableRow
            {:key (:id item)}
            [:> TableCell (:username item)]
            [:> TableCell
             (for [{:keys [role] :as x} (-> item :permissions :roles)
                   ;; These users HAVE role for this org, but this value contains all the user roles so filter to find the
                   ;; current org roles:
                   :when (contains? (set (:org-id x)) org-id)]
               [:span (tr (keyword :lipas.user.permissions.roles.role-names role))
                [:> Button
                 {:size "small"
                  :color "error"
                  :on-click (fn [_e]
                              (rf/dispatch [::events/org-user-update org-id (:id item) role "remove"]))}
                 [:> DeleteIcon]]])]])]]]]]))
