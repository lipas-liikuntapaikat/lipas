(ns lipas.ui.user.subs
  (:require [lipas.roles :as roles]
            [re-frame.core :as rf]))

;; TODO: Likely not very useful now. Checks should mainly be done using
;; check-privilege which indirectly also handles if the user is logged in.
(rf/reg-sub ::logged-in?
  (fn [db _]
    (:logged-in? db)))

(rf/reg-sub ::user
  (fn [db _]
    (:user db)))

(rf/reg-sub ::user-data
  :<- [::user]
  :<- [::dev-overrides]
  (fn [[user overrides] _]
    (assoc (:login user) :dev/overrides overrides)))

(defn user-data
  "Same as ::user-data, but for use in effects"
  [db]
  (assoc (:login (:user db)) :dev/overrides (:lipas.ui.project-devtools/privilege-override db)))

(rf/reg-sub ::permission-to-cities
  :<- [::user-data]
  :<- [:lipas.ui.sports-sites.subs/cities-by-city-code]
  (fn [[user all-cities] _]
    (into {} (filter (fn [[city-code _v]]
                       (roles/check-privilege user
                                              {:city-code city-code
                                               :type-code ::roles/any}
                                              :site/create-edit))
                     all-cities))))

(rf/reg-sub ::permission-to-types
  :<- [::user-data]
  :<- [:lipas.ui.sports-sites.subs/active-types]
  (fn [[user all-types] _]
    (into {} (filter (fn [[type-code _v]]
                       (roles/check-privilege user
                                              {:type-code type-code
                                               :city-code ::roles/any}
                                              :site/create-edit))
                     all-types))))

(rf/reg-sub ::can-add-sports-sites?
  :<- [::check-privilege
       {:type-code ::roles/any
        :city-code ::roles/any}
       :site/create-edit]
  (fn [x _]
    x))

(rf/reg-sub ::can-add-lois?
  :<- [::check-privilege
       ;; Usually given with activities-manager, but should ignore role-context
       {:city-code ::roles/any
        :type-code ::roles/any
        :activity ::roles/any}
       :loi/create-edit]
  (fn [x _]
    x))

(rf/reg-sub ::can-add-lois-only?
  :<- [::can-add-sports-sites?]
  :<- [::can-add-lois?]
  (fn [[can-add-sports-sites? can-add-lois?] _]
    (and can-add-lois? (not can-add-sports-sites?))))

(rf/reg-sub ::permission-to-publish?
  (fn [[_ lipas-id]]
    [(rf/subscribe [::user-data])
     (rf/subscribe [:lipas.ui.sports-sites.subs/latest-rev lipas-id])])
  (fn [[user sports-site] _]
    (when (and user sports-site)
      (roles/check-privilege user (roles/site-roles-context sports-site) :site/create-edit))))

(defn ->list-entry [locale cities types sports-site]
  (let [city-code (-> sports-site :location :city :city-code)
        type-code (-> sports-site :type :type-code)]
    {:lipas-id  (:lipas-id sports-site)
     :name      (:name sports-site)
     :city      (get-in cities [city-code :name locale])
     :city-code city-code
     :type      (get-in types [type-code :name locale])
     :type-code type-code}))

(defn show?
  [user {:keys [status] :as sports-site}]
  (and
    (roles/check-privilege user (roles/site-roles-context sports-site) :site/create-edit)
    (#{"planned" "active" "out-of-service-temporarily"} status)))

;; This is used in ice-stadiums and swimming-pools views list
;; which sites does the user have access to modify to report the
;; energy use values.
(rf/reg-sub ::sports-sites
  :<- [:lipas.ui.sports-sites.subs/latest-sports-site-revs]
  :<- [::user-data]
  :<- [:lipas.ui.sports-sites.subs/cities-by-city-code]
  :<- [:lipas.ui.sports-sites.subs/active-types]
  (fn [[sites user cities types] [_ locale]]
    (when (and user sites)
      (->> sites
           vals
           (filter (partial show? user))
           (map (partial ->list-entry locale cities types))))))

(rf/reg-sub ::selected-sports-site
  :<- [::user]
  (fn [user _]
    (-> user :selected-sports-site)))

(rf/reg-sub ::saved-reports
  :<- [::user-data]
  (fn [user]
    (-> user :user-data :saved-reports)))

(rf/reg-sub ::saved-searches
  :<- [::user-data]
  (fn [user _]
    (-> user :user-data :saved-searches)))

(rf/reg-sub ::saved-diversity-settings
  :<- [::user-data]
  (fn [user _]
    (-> user :user-data :saved-diversity-settings)))

;; Role basic permissions

(rf/reg-sub ::roles
  :<- [::user-data]
  (fn [user _]
    (:roles (:permissions user))))

(rf/reg-sub ::dev-overrides
  (fn [db _]
    ;; This value is only ever set from dev tools
    (:lipas.ui.project-devtools/privilege-override db)))

(rf/reg-sub ::check-privilege
  :<- [::user-data]
  (fn [user [_ role-context k disable-overrides]]
    (let [user (if disable-overrides
                 (dissoc user :dev/overrides)
                 user)]
      (roles/check-privilege user role-context k))))

(rf/reg-sub ::user-orgs
  (fn [db _]
    (:orgs (:user db))))

(rf/reg-sub ::user-orgs-by-id
  :<- [::user-orgs]
  (fn [orgs _]
    (into {} (map (juxt :org/id identity) orgs))))

(rf/reg-sub ::user-org-by-id
  :<- [::user-orgs-by-id]
  (fn [orgs [_ id]]
    (get orgs id)))

(rf/reg-sub ::context-value-name
  (fn [[_ context-key v _locale]]
    (case context-key
      :city-code (rf/subscribe [:lipas.ui.sports-sites.subs/city v])
      :type-code (rf/subscribe [:lipas.ui.sports-sites.subs/type-by-type-code v])
      :activity (rf/subscribe [:lipas.ui.sports-sites.activities.subs/activity-by-value v])
      :lipas-id (rf/subscribe [:lipas.ui.sports-sites.subs/latest-rev v])
      :org-id [;; Session user or org admin, managing their own orgs
               (rf/subscribe [::user-org-by-id v])
               ;; Admin view
               (rf/subscribe [:lipas.ui.admin.subs/org v])]))
  (fn [x [_ context-key _v locale]]
    (case context-key
      :lipas-id (:name x)
      :org-id (or (:org/name (first x))
                  (:name (second x)))
      :activity (get (:label x) locale)
      (get (:name x) locale))))
