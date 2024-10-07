(ns lipas.ui.user.views
  (:require
   ["@mui/material/Icon$default" :as Icon]
   ["@mui/material/Stack$default" :as Stack]
   [lipas.ui.components :as lui]
   [lipas.ui.mui :as mui]
   [lipas.ui.user.events :as events]
   [lipas.ui.user.subs :as subs]
   [lipas.ui.utils :refer [<== ==> navigate!]]
   [re-frame.core :as rf]
   ["@mui/material/Typography$default" :as Typography]
   ["@mui/material/ListItemText$default" :as ListItemText]
   ["@mui/material/ListItem$default" :as ListItem]
   ["@mui/material/List$default" :as List]))

(defn user-form [tr data]
  [mui/form-group

   ;; Email
   [lui/text-field
    {:label    (tr :lipas.user/email)
     :value    (:email data)
     :disabled true}]

   ;; Username
   [lui/text-field
    {:label    (tr :lipas.user/username)
     :value    (:username data)
     :disabled true}]

   ;; Firstname
   [lui/text-field
    {:label    (tr :lipas.user/firstname)
     :value    (-> data :user-data :firstname)
     :disabled true}]

   ;; Lastname
   [lui/text-field
    {:label    (tr :lipas.user/lastname)
     :value    (-> data :user-data :lastname)
     :disabled true}]

   ;; Permissions request
   [lui/text-field
    {:label    (tr :lipas.user/requested-permissions)
     :value    (-> data :user-data :permissions-request)
     :disabled true}]])

(defn actions-dialog [tr]
  (let [site     (<== [::subs/selected-sports-site])
        close    #(==> [::events/select-sports-site nil])
        lipas-id (:lipas-id site)]
    [mui/dialog {:open     (some? (seq site))
                 :on-close close}

     [mui/dialog-title
      (or (:name site) "")]

     [mui/dialog-content
      [mui/list

       ;; View basic info button
       [mui/list-item
        {:button   true
         :on-click (comp
                    close
                    #(==> [:lipas.ui.events/display lipas-id]))}
        [mui/list-item-icon
         [mui/icon "keyboard_arrow_right"]]
        [mui/typography {:variant "body2"}
         (tr :lipas.user/view-basic-info)]]

       ;; Report energy consumption button
       [mui/list-item
        {:button   true
         :on-click (comp
                    close
                    #(==> [:lipas.ui.events/report-energy-consumption lipas-id]))}
        [mui/list-item-icon
         [mui/icon "keyboard_arrow_right"]]
        [mui/typography {:variant "body2"}
         (tr :lipas.user/report-energy-and-visitors)]]]]

     [mui/dialog-actions
      [mui/button {:on-click close}
       (tr :actions/cancel)]]]))

(def teaviisari-types #{1110, 1120, 1130 1310, 1320, 1330, 1340, 1370,
1380 1510, 1520, 1530, 1550 2120 2150, 2210, 2220, 2230, 2240 3110,
3130, 3210 4401, 4402, 4403, 4404, 4405})

;; TODO: Localization
(def role-context-k->label
  {:city-code (fn [city-code]
                (if city-code
                  (:fi (:name @(rf/subscribe [:lipas.ui.sports-sites.subs/city city-code])))
                  "Kaikki kaupungit"))
   :type-code (fn [type-code]
                (if type-code
                  (:fi (:name @(rf/subscribe [:lipas.ui.sports-sites.subs/type-by-type-code type-code])))
                  "Kaikki tyypit"))
   :activity (fn [activity]
               (str "Activity tyyppi: " activity))
   :lipas-id (fn [lipas-id]
               (str "Paikka: " lipas-id))})

(defn role-context-list
  [{:keys [role-ctx-k
           role-ctx-ks
           roles]}]
  (let [has-ctx (some (fn [role]
                        (contains? role role-ctx-k))
                      roles)
        ctx-vs (group-by role-ctx-k roles)]
    (if has-ctx
      [:> List
       {:dense true
        :sx {:pl 2}}
       (doall
         (for [[v roles] ctx-vs]
           [:<>
            {:key (or v "all")}
            [:> ListItem
             [:> ListItemText
              ((get role-context-k->label role-ctx-k) v)]]
            (when (seq role-ctx-ks)
              [role-context-list
               {:role-ctx-k (first role-ctx-ks)
                :role-ctx-ks (rest role-ctx-ks)
                :roles roles}])]))]
      (when (seq role-ctx-ks)
        [role-context-list
         {:role-ctx-k (first role-ctx-ks)
          :role-ctx-ks (rest role-ctx-ks)
          :roles roles}]))))

(defn explain-roles []
  (let [roles (or [{:role :basic-manager
                    :city-code 91
                    :type-code 104}
                   {:role :basic-manager
                    :city-code 92
                    :type-code 104}
                   {:role :basic-manager
                    :city-code 91
                    :type-code 103}
                   {:role :basic-manager
                    :city-code 92
                    :type-code 103}
                   {:role :basic-manager
                    :city-code 142}
                   {:role :basic-manager
                    :lipas-id 123}
                   {:role :floorball-manager
                    :city-code 837}
                   {:role :floorball-manager
                    :city-code 92
                    :type-code 103}
                   {:role :acitivities-manager
                    :activity "fishing"}]
                  @(rf/subscribe [::subs/roles]))
        role-ctx-ks [:city-code
                     :type-code
                     :activity
                     :lipas-id]
        role-k->roles (group-by :role roles) ]
    [:<>
     (doall
       (for [[role-k roles] role-k->roles]
         [:<>
          {:key role-k}
          [:> Stack
           {:direction "row"
            :sx {:alignItems "center"
                 :p 1}}
           [:> Icon "lock_open"]
           [:> Typography
            {:variant "body2"
             :sx {:ml 1}}
            role-k]]
          [role-context-list
           {:role-ctx-k (first role-ctx-ks)
            :role-ctx-ks (rest role-ctx-ks)
            :roles roles}]]))]))

(defn user-panel [tr user]
  (let [;; TODO: Replaces these with role/privilege checks
        admin?     (<== [::subs/admin?])
        cities     (<== [::subs/permission-to-cities])
        types      (<== [::subs/permission-to-types])
        activities (<== [::subs/permission-to-activities])

        sites (<== [::subs/sports-sites (tr)])

        all-types?      (or admin? (-> user :permissions :all-types?))
        all-cities?     (or admin? (-> user :permissions :all-cities?))

        locale     (tr)
        card-props {:square true}

        firstname (-> user :user-data :firstname)
        lastname  (-> user :user-data :lastname)

        saved-searches (<== [::subs/saved-searches])

        experimental-features? (<== [::subs/experimental-features?])]

    [mui/grid {:container true :spacing 2 :style {:padding "1em"}}

     [mui/grid {:item true :xs 12 :lg 6}
      [mui/grid {:container true :spacing 1}

       ;; Profile card
       [mui/grid {:item true :xs 12}
        [mui/card card-props
         [mui/card-header {:title (tr :user/greeting firstname lastname)}]
         [mui/card-content
          [user-form tr user]]
         [mui/card-actions
          [mui/button {:href  "/etusivu"
                       :color :secondary}
           (str "> " (tr :user/front-page-link))]
          [mui/button {:href  "/passu-hukassa"
                       :color :primary}
           (str "> " (tr :reset-password/change-password))]
          (when @(rf/subscribe [::subs/check-privilege nil :user-management])
            [mui/button {:href  "/admin"
                         :color :primary}
             (str "> " (tr :user/admin-page-link))])]]]


       ;; Saved searches
       (when saved-searches
         [mui/grid {:item true :xs 12}
          [mui/card card-props
           [mui/card-header {:title (tr :lipas.user/saved-searches)}]
           [mui/card-content
            [lui/select
             {:label     (tr :actions/select)
              :style     {:width "170px"}
              :items     saved-searches
              :label-fn  :name
              :value-fn  identity
              :on-change #(==> [::events/select-saved-search %])}]]]])]]

     [mui/grid {:item true :xs 12 :lg 6}
      [mui/grid {:container true :spacing 1}

       ;; Permissions
       [mui/grid {:item true :xs 12}

        [actions-dialog tr]

        [mui/card (merge card-props)
         [mui/card-header {:title (tr :lipas.user/permissions)}]
         [mui/card-content

          [explain-roles]

          ;; TODO: Remove the old version
          (when admin?
            [lui/icon-text
             {:icon "lock_open"
              :text (tr :lipas.admin/access-all-sites)}])

          (when (and all-cities? (not admin?))
            [lui/icon-text
             {:icon "lock_open"
              :text (tr :lipas.user/permission-to-all-cities)}])

          (when (and (not-empty cities) (not all-cities?))
            [:<>
             [lui/icon-text
              {:icon "lock_open"
               :text (tr :lipas.user/permission-to-cities)}]
             (into
              [mui/list {:dense true}]
              (for [s (->> cities (map (comp locale :name second)))]
                [mui/list-item
                 [mui/list-item-text s]]))])

          (when (and all-types? (not admin?))
            [lui/icon-text
             {:icon "lock_open"
              :text (tr :lipas.user/permission-to-all-types)}])

          (when (and (not-empty types) (not all-types?))
            [:<>
             [lui/icon-text
              {:icon "lock_open"
               :text (tr :lipas.user/permission-to-types)}]
             (into
              [mui/list {:dense true}]
              (for [t (->> types (map (comp locale :name second)))]
                [mui/list-item
                 [mui/list-item-text t]]))])

          (when (and (not all-cities?) (not all-types?) (empty? sites)
                     (empty? cities) (empty? types) (empty? activities))
            [mui/grid {:container true}
             [lui/icon-text
              {:icon "lock"
               :text (tr :lipas.user/no-permissions)}]])

          (when (and (not admin?) (not-empty sites))
            [:<>
             [lui/icon-text
              {:icon "lock_open"
               :text (tr :lipas.user/permission-to-portal-sites)}]
             [lui/table
              {:headers   [[:name (tr :lipas.sports-site/name-short)]
                           [:type (tr :lipas.sports-site/type)]
                           [:city (tr :lipas.location/city)]]
               :items     sites
               :on-select #(==> [::events/select-sports-site %])}]])

          (when (and (not admin?) (not-empty activities))
            [:<>
             [lui/icon-text
              {:icon "lock_open"
               :text (tr :lipas.user/permission-to-activities)}]
             (into
              [mui/list {:dense true}]
              (for [s (->> activities vals (map (comp locale :label)))]
                [mui/list-item
                 [mui/list-item-text s]]))])]

         [mui/card-actions
          [mui/button {:href  "/liikuntapaikat"
                       :color :secondary}
           (str "> " (tr :sport/headline))]

          ;; (when (some #{2510 2520} (map :type-code sites))
          ;;   [mui/button {:href  "/jaahalliportaali"
          ;;                :color :secondary}
          ;;    (str "> " (tr :user/ice-stadiums-link))])
          ;; (when (some #{3110 3130} (map :type-code sites))
          ;;   [mui/button {:href  "/uimahalliportaali"
          ;;                :color :secondary}
          ;;    (str "> " (tr :user/swimming-pools-link))])

          ]]]

       ;; Promo card
       [mui/grid {:item true :xs 12}
        [mui/card card-props
         [mui/card-header {:title (tr :user/promo-headline)}]
         [mui/card-content
          [mui/button
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
          #_[mui/typography {:style {:margin-top "1em" :margin-bottom "1em"}}
             "Varmistattehan, että tietonne on päivitetty ajan tasalle 31.8.2020 mennessä."]
          #_[mui/button
             {:variant  "contained"
              :color    "secondary"
              :on-click (fn []
                          (==> [:lipas.ui.search.events/clear-filters])
                          (==> [:lipas.ui.search.events/set-filters-by-permissions])
                          (==> [:lipas.ui.search.events/set-type-filter teaviisari-types])
                          (==> [:lipas.ui.events/navigate :lipas.ui.routes.map/map]))}
             (tr :user/promo1-link)]


          ]]]

       [mui/grid {:item true :xs 12}
        [mui/card card-props
         [mui/card-header {:title (tr :user/data-ownership)}]
         [mui/card-content
          [mui/typography (tr :disclaimer/data-ownership)]



          ]]]]]

     ;; Experimental features
     #_[mui/grid {:item true :xs 12}
        [mui/card card-props
         [mui/card-header {:title "Experimental features"}]
         [mui/card-content
          [lui/checkbox
           {:label     "Enable experimental features"
            :value     experimental-features?
            :on-change #(==> [::events/toggle-experimental-features])}]]]]
     ]))

(defn main []
  (let [tr         (<== [:lipas.ui.subs/translator])
        logged-in? (<== [::subs/logged-in?])
        user       (<== [::subs/user-data])]

    (if logged-in?
      [user-panel tr user]
      (navigate! "/kirjaudu"))))
