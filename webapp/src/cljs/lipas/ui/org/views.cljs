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
            ["@mui/material/Container$default" :as Container]
            ["@mui/material/Card$default" :as Card]
            ["@mui/material/CardContent$default" :as CardContent]
            ["@mui/material/Paper$default" :as Paper]
            ["@mui/material/Typography$default" :as Typography]
            [lipas.ui.components :as lui]
            [lipas.ui.components.autocompletes :as ac]
            [lipas.ui.mui :as mui]
            [lipas.ui.org.events :as events]
            [lipas.ui.org.subs :as subs]
            [lipas.ui.subs :as ui-subs]
            [re-frame.core :as rf]
            [uix.core :refer [$]]
            ["@mui/material/FormControl$default" :as FormControl]
            ["@mui/material/InputLabel$default" :as InputLabel]
            [reitit.frontend.easy :as rfe]))

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
         {:label (tr :lipas.org/name)
          :value (:name org)
          :on-change #(rf/dispatch [::events/edit-org [:name] %])}]
        [lui/text-field
         {:label (tr :lipas.org/phone)
          :value (:phone (:data org))
          :on-change (fn [x] (rf/dispatch [::events/edit-org [:data :phone] x]))}]

        [mui/grid {:item true}
         [mui/button
          {:variant "contained"
           :color "secondary"
           ;; TODO:
           :on-click #(rf/dispatch [::events/save-org org])}
          [mui/icon {:sx {:mr 1}} "save"]
          (tr :actions/save)]]]]

      [lui/form-card
       {:title "Käyttäjät"
        :md 12}
       (let [add-form @(rf/subscribe [::subs/add-user-form])]
         [:> Box
          {:sx {:display "flex"
                :flex-direction "row"
                :gap 1
                :align-items "center"}}
          ($ ac/autocomplete2
             {:label (tr :lipas.user/username)
              :options @(rf/subscribe [::subs/all-users-options])
              :value (:user-id add-form)
              :onChange (fn [_e v] (rf/dispatch [::events/set-add-user-form [:user-id] (ac/safe-value v)]))})
          [:> FormControl
           {:sx {:min-width 120}}
           [:> InputLabel
            {:id "add-org-user"}
            (tr :lipas.org/org-role)]
           [:> Select
            {:value (or (:role add-form) "org-user")
             :labelId "add-org-user"
             :onChange (fn [e] (rf/dispatch [::events/set-add-user-form [:role] (.-value (.-target e))]))}
            [:> MenuItem {:value "org-user"} (tr (keyword :lipas.user.permissions.roles.role-names :org-user))]
            [:> MenuItem {:value "org-admin"} (tr (keyword :lipas.user.permissions.roles.role-names :org-admin))]]]
          [:> Button
           {:variant "contained"
            :color "primary"
            :on-click #(rf/dispatch [::events/add-user-to-org org-id])}
           "Lisää"]])
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

(defn orgs-list-view []
  (let [tr @(rf/subscribe [:lipas.ui.subs/translator])
        user-orgs @(rf/subscribe [::subs/user-orgs])]
    [mui/paper {:sx {:p 3 :m 2}}
     [mui/typography {:variant "h4" :sx {:mb 3}}
      (tr :lipas.admin/organizations)]

     (if (seq user-orgs)
       [mui/grid {:container true :spacing 2}
        (for [org user-orgs]
          [mui/grid {:item true :xs 12 :md 6 :key (:id org)}
           [mui/paper {:sx {:p 2 :cursor "pointer"}
                       :on-click #(rfe/navigate :lipas.ui.routes/org {:org-id (:id org)})}
            [mui/typography {:variant "h6"}
             (:name org)]
            (when-let [phone (get-in org [:data :phone])]
              [mui/typography {:variant "body2" :color "text.secondary"}
               phone])]])]

       [mui/paper {:sx {:p 3 :text-align "center"}}
        [mui/typography {:variant "h6" :color "text.secondary"}
         "Et kuulu mihinkään organisaatioon"]
        [mui/typography {:variant "body2" :color "text.secondary" :sx {:mt 1}}
         "Ota yhteyttä järjestelmän ylläpitäjään organisaatiojäsenyyden lisäämiseksi"]])]))
