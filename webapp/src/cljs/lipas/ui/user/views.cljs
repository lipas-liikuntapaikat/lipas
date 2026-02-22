(ns lipas.ui.user.views
  (:require ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/Link$default" :as Link]
            ["@mui/material/Stack$default" :as Stack]
            ["@mui/material/Typography$default" :as Typography]
            [lipas.roles :as roles]
            [lipas.ui.components.checkboxes :as checkboxes]
            [lipas.ui.components.selects :as selects]
            [lipas.ui.components.text-fields :as text-fields]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/Card$default" :as Card]
            ["@mui/material/CardActions$default" :as CardActions]
            ["@mui/material/CardContent$default" :as CardContent]
            ["@mui/material/CardHeader$default" :as CardHeader]
            ["@mui/material/Dialog$default" :as Dialog]
            ["@mui/material/DialogActions$default" :as DialogActions]
            ["@mui/material/DialogContent$default" :as DialogContent]
            ["@mui/material/DialogTitle$default" :as DialogTitle]
            ["@mui/material/FormGroup$default" :as FormGroup]
            ["@mui/material/GridLegacy$default" :as Grid]
            ["@mui/material/List$default" :as List]
            ["@mui/material/ListItemButton$default" :as ListItemButton]
            ["@mui/material/ListItemIcon$default" :as ListItemIcon]
            [lipas.ui.org.subs :as org-subs]
            [lipas.ui.user.events :as events]
            [lipas.ui.user.subs :as subs]
            [lipas.ui.utils :refer [<== ==> navigate!]]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [reitit.frontend.easy :as rfe]))

(defn user-form [tr data]
  [:> FormGroup

   ;; Email
   [text-fields/text-field
    {:label    (tr :lipas.user/email)
     :value    (:email data)
     :disabled true}]

   ;; Username
   [text-fields/text-field
    {:label    (tr :lipas.user/username)
     :value    (:username data)
     :disabled true}]

   ;; Firstname
   [text-fields/text-field
    {:label    (tr :lipas.user/firstname)
     :value    (-> data :user-data :firstname)
     :disabled true}]

   ;; Lastname
   [text-fields/text-field
    {:label    (tr :lipas.user/lastname)
     :value    (-> data :user-data :lastname)
     :disabled true}]

   ;; Permissions request
   [text-fields/text-field
    {:label    (tr :lipas.user/requested-permissions)
     :value    (-> data :user-data :permissions-request)
     :disabled true}]])

(defn actions-dialog [tr]
  (let [site     (<== [::subs/selected-sports-site])
        close    #(==> [::events/select-sports-site nil])
        lipas-id (:lipas-id site)]
    [:> Dialog {:open     (some? (seq site))
                 :on-close close}

     [:> DialogTitle
      (or (:name site) "")]

     [:> DialogContent
      [:> List

       ;; View basic info button
       [:> ListItemButton
        {:on-click (comp
                     close
                     #(==> [:lipas.ui.events/display lipas-id]))}
        [:> ListItemIcon
         [:> Icon "keyboard_arrow_right"]]
        [:> Typography {:variant "body2"}
         (tr :lipas.user/view-basic-info)]]]]

     [:> DialogActions
      [:> Button {:on-click close}
       (tr :actions/cancel)]]]))

(def teaviisari-types #{1110, 1120, 1130 1310, 1320, 1330, 1340, 1370,
                        1380 1510, 1520, 1530, 1550 2120 2150, 2210, 2220, 2230, 2240 3110,
                        3130, 3210 4401, 4402, 4403, 4404, 4405})

(r/defc role-context [{:keys [tr k v]}]
  (let [locale (tr)
        localized @(rf/subscribe [::subs/context-value-name k v locale])
        link? (= :lipas-id k)]
    [:> Typography
     {:key k
      :component (if link?
                   "a"
                   "span")
      :sx #js [#js {:mr 1}
               (when link?
                 #js {:cursor "pointer"
                      :textDecoration "underline"})]
      :onClick (when link?
                 (fn [_e]
                   (rf/dispatch [::events/select-sports-site {:lipas-id v}])))}
     ;; Role context key name
     (tr (keyword :lipas.user.permissions.roles.context-keys k))
     ": "
     (if (= :all v)
       [:i (tr :lipas.user.permissions.roles/context-value-all)]
       localized)]))

(r/defc explain-roles [{:keys [tr]}]
  (let [roles @(rf/subscribe [::subs/roles])
        roles (sort-by roles/role-sort-fn roles)]
    (if (empty? roles)
      [:> Stack
       {:direction "row"
        :sx #js {:alignItems "center"
                 :p 1}}
       [:> Icon "lock"]
       [:> Typography
        {:variant "body2"
         :sx #js {:ml 1
                  :mr 2}}
        (tr :lipas.user/no-permissions)]]
      [:<>
       (for [[i {:keys [role] :as x}] (map-indexed vector roles)]
         [:<>
          {:key i}
          [:> Stack
           {:direction "row"
            :sx #js {:alignItems "center"
                     :p 1}}
           [:> Icon "lock_open"]
           [:> Typography
            {:variant "body2"
             :sx #js {:ml 1
                      :mr 2}}
            (tr (keyword :lipas.user.permissions.roles.role-names role))]
           (for [[k vs] (dissoc x :role)]
             [:<>
              {:key k}
              (for [v vs]
                [role-context
                 {:key v
                  :k k
                  :v v
                  :tr tr}])])]])])))

(r/defc explain-orgs []
  (let [orgs @(rf/subscribe [::org-subs/user-orgs])]
    [:<>
     (for [{:keys [id name]} orgs]
       [:<>
        {:key id}
        [:> Stack
         {:direction "row"
          :sx #js {:alignItems "center"
                   :p 1}}
         [:> Link
          {:variant "body2"
           :href (rfe/href :lipas.ui.routes/org
                           {:org-id id})}
          (or name "-")]]])]))

(defn user-panel [tr user]
  (let [card-props {:square true}

        firstname (-> user :user-data :firstname)
        lastname  (-> user :user-data :lastname)

        saved-searches (<== [::subs/saved-searches])]

    [:> Grid {:container true :spacing 2 :style {:padding "1em"}}

     [:> Grid {:item true :xs 12 :lg 6}
      [:> Grid {:container true :spacing 1}

       ;; Profile card
       [:> Grid {:item true :xs 12}
        [:> Card card-props
         [:> CardHeader {:title (tr :user/greeting firstname lastname)}]
         [:> CardContent
          [user-form tr user]]
         [:> CardActions
          [:> Button {:href  "/etusivu"
                       :color :secondary}
           (str "> " (tr :user/front-page-link))]
          [:> Button {:href  "/passu-hukassa"
                       :color :primary}
           (str "> " (tr :reset-password/change-password))]
          (when @(rf/subscribe [::subs/check-privilege nil :users/manage])
            [:> Button {:href  "/admin"
                         :color :primary}
             (str "> " (tr :user/admin-page-link))])]]]

;; Saved searches
       (when saved-searches
         [:> Grid {:item true :xs 12}
          [:> Card card-props
           [:> CardHeader {:title (tr :lipas.user/saved-searches)}]
           [:> CardContent
            [selects/select
             {:label     (tr :actions/select)
              :style     {:width "170px"}
              :items     saved-searches
              :label-fn  :name
              :value-fn  identity
              :on-change #(==> [::events/select-saved-search %])}]]]])]]

     [:> Grid {:item true :xs 12 :lg 6}
      [:> Grid {:container true :spacing 1}

       ;; Permissions
       [:> Grid {:item true :xs 12}

        [actions-dialog tr]

        [:> Card (merge card-props)
         [:> CardHeader {:title (tr :lipas.user/permissions)}]
         [:> CardContent

          [explain-roles
           {:tr tr}]]

         [:> CardActions
          [:> Button {:href  "/liikuntapaikat"
                       :color :secondary}
           (str "> " (tr :sport/headline))]

          ;; (when (some #{2510 2520} (map :type-code sites))
          ;;   [:> Button {:href  "/jaahalliportaali"
          ;;                :color :secondary}
          ;;    (str "> " (tr :user/ice-stadiums-link))])
          ;; (when (some #{3110 3130} (map :type-code sites))
          ;;   [:> Button {:href  "/uimahalliportaali"
          ;;                :color :secondary}
          ;;    (str "> " (tr :user/swimming-pools-link))])
          ]]]

       [:> Grid {:item true :xs 12}
        [:> Card (merge card-props)
         [:> CardHeader {:title (tr :lipas.user/organizations)}]
         [:> CardContent

          [explain-orgs]]]]

;; Promo card
       [:> Grid {:item true :xs 12}
        [:> Card card-props
         [:> CardHeader {:title (tr :user/promo-headline)}]
         [:> CardContent
          [:> Button
           {:variant "contained"
            :color   "secondary"
            :style   {:margin-top "1em"}
            :href    (str "/pdf/lipas-vuosikatsaus-2023.pdf")
            :target  "_blank"}
           "LIPAS vuosikatsaus 2023 (.pdf)"]

          #_[:ul {:dense true}
             [:li "Liikuntapaikat, kaikki yhteensä"]
             [:li "Liikuntapuistot, lähiliikuntapaikat, ulkokuntoilupaikat (1110, 1120, 1130)"]
             [:li "Pallokentät (1310, 1320, 1330, 1340, 1370, 1380)"]
             [:li "Jääurheilualueet (1510, 1520, 1530, 1550)"]
             [:li "Kuntosalit (2120)"]
             [:li "Liikuntasalit ja -hallit (2150, 2210, 2220, 2230, 2240)"]
             [:li "Uimahallit, kylpylät ja maauimalat (3110, 3130, 3210)"]
             [:li "Polut, ladut ja reitit (4401, 4402, 4403, 4404, 4405)"]]
          #_[:> Typography {:style {:margin-top "1em" :margin-bottom "1em"}}
             "Varmistattehan, että tietonne on päivitetty ajan tasalle 31.8.2020 mennessä."]
          #_[:> Button
             {:variant  "contained"
              :color    "secondary"
              :on-click (fn []
                          (==> [:lipas.ui.search.events/clear-filters])
                          (==> [:lipas.ui.search.events/set-filters-by-permissions])
                          (==> [:lipas.ui.search.events/set-type-filter teaviisari-types])
                          (==> [:lipas.ui.events/navigate :lipas.ui.routes.map/map]))}
             (tr :user/promo1-link)]]]]

       [:> Grid {:item true :xs 12}
        [:> Card card-props
         [:> CardHeader {:title (tr :user/data-ownership)}]
         [:> CardContent
          [:> Typography (tr :disclaimer/data-ownership)]]]]]]

;; Experimental features
     #_[:> Grid {:item true :xs 12}
        [:> Card card-props
         [:> CardHeader {:title "Experimental features"}]
         [:> CardContent
          [checkboxes/checkbox
           {:label     "Enable experimental features"
            :value     experimental-features?
            :on-change #(==> [::events/toggle-experimental-features])}]]]]]))

(defn main []
  (let [tr         (<== [:lipas.ui.subs/translator])
        logged-in? (<== [::subs/logged-in?])
        user       (<== [::subs/user-data])]

    (if logged-in?
      [user-panel tr user]
      (navigate! "/kirjaudu"))))
