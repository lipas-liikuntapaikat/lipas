(ns lipas.ui.org.views
  (:require [lipas.ui.components :as lui]
            [lipas.ui.mui :as mui]
            [lipas.ui.org.events :as events]
            [lipas.ui.org.subs :as subs]
            [lipas.ui.subs :as ui-subs]
            [re-frame.core :as rf]))

(defn org-view []
  (let [tr @(rf/subscribe [:lipas.ui.subs/translator])

        {:keys [org-id]} (:path @(rf/subscribe [::ui-subs/parameters]))

        org @(rf/subscribe [:lipas.ui.org.subs/user-org-by-id org-id])
        org-users @(rf/subscribe [::subs/org-users])]
    (js/console.log org-id org)
    [mui/grid {:container true
               :spacing 2
               :sx {:p 1}}
     [mui/grid {:item true :container true :xs 12 :spacing 1}
      [lui/form-card
       {:title "Organisaatio"
        :md 12}
       [mui/form-group
        [lui/text-field
         {:label     (tr :lipas.org/name)
          :value     (:name org)
          :on-change #(rf/dispatch [::events/edit-org [:name] %])}]
        [lui/text-field
         {:label     (tr :lipas.org/phone)
          :value (:phone (:data org))
          :on-change (fn [x] (rf/dispatch [::events/edit-org [:data :phone] x]))}]]]

      [lui/form-card
       {:title "Käyttäjät"
        :md 12}
       [lui/table
        {:headers
         [[:username (tr :lipas.user/username)]
          [:role (tr :lipas.org/org-role)]]
         :sort-fn   :username
         :items     org-users
         :on-select (fn [x] nil)}]]]]))
