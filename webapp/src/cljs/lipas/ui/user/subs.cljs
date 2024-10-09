(ns lipas.ui.user.subs
  (:require
   [lipas.permissions :as permissions]
   [lipas.roles :as roles]
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::logged-in?
 (fn [db _]
   (:logged-in? db)))

(re-frame/reg-sub
 ::user
 (fn [db _]
   (:user db)))

(re-frame/reg-sub
 ::admin?
 ;; TODO: Check for role / replace most uses of this sub.
 :<- [::check-privilege {} :user-management]
 (fn [x _]
   x))

(re-frame/reg-sub
 ::user-data
  :<- [::user]
 (fn [user _]
   (:login user)))

;; TODO: Remove uses
(re-frame/reg-sub
 ::permissions
 :<- [::user]
 (fn [user _]
   (-> user :login :permissions)))

(re-frame/reg-sub ::permission-to-cities
  :<- [::user-data]
  :<- [:lipas.ui.sports-sites.subs/cities-by-city-code]
  (fn [[user all-cities] _]
    (into {} (filter (fn [[city-code _v]]
                       ;; NOTE: Calling check-privilege directly skips the UI dev-tools override
                       (roles/check-privilege user
                                              {:city-code city-code
                                               :type-code ::roles/any}
                                              :create))
                     all-cities))))

(re-frame/reg-sub ::permission-to-types
  :<- [::user-data]
  :<- [:lipas.ui.sports-sites.subs/active-types]
  (fn [[user all-types] _]
    (into {} (filter (fn [[type-code _v]]
                       ;; NOTE: Calling check-privilege directly skips the UI dev-tools override
                       (roles/check-privilege user
                                              {:type-code type-code
                                               :city-code ::roles/any}
                                              :create))
                     all-types))))

(re-frame/reg-sub
 ::can-add-sports-sites?
 :<- [::check-privilege
      {:type-code ::roles/any
       :city-code ::roles/any}
      :create]
 (fn [x _]
   x))

(re-frame/reg-sub ::permission-to-activities
  :<- [::user-data]
  :<- [:lipas.ui.sports-sites.activities.subs/data]
  (fn [[user all-activities] _]
    (into {} (filter (fn [[activity-name _v]]
                       (roles/check-privilege user
                                              {:activity activity-name}
                                              :edit-activity))
                     all-activities))))

(re-frame/reg-sub ::can-add-lois?
  :<- [::check-privilege {:city-code ::roles/any} :create-loi]
  (fn [x _]
    x))

(re-frame/reg-sub ::can-add-lois-only?
  :<- [::can-add-sports-sites?]
  :<- [::can-add-lois?]
  (fn [[can-add-sports-sites? can-add-lois?] _]
    (and can-add-lois? (not can-add-sports-sites?))))

(re-frame/reg-sub ::permission-to-publish?
  (fn [[_ lipas-id]]
    [(re-frame/subscribe [::user-data])
     (re-frame/subscribe [:lipas.ui.sports-sites.subs/latest-rev lipas-id])])
  (fn [[user sports-site] _]
    (when (and user sports-site)
      (roles/check-privilege user (roles/site-roles-context sports-site) :edit))))

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
    (roles/check-privilege user (roles/site-roles-context sports-site) :edit)
    (#{"planned" "active" "out-of-service-temporarily"} status)))

;; This is used in ice-stadiums and swimming-pools views list
;; which sites does the user have access to modify to report the
;; energy use values.
(re-frame/reg-sub
 ::sports-sites
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

(re-frame/reg-sub
 ::selected-sports-site
 :<- [::user]
 (fn [user _]
   (-> user :selected-sports-site)))

(re-frame/reg-sub
 ::saved-reports
 :<- [::user-data]
 (fn [user]
   (-> user :user-data :saved-reports)))

(re-frame/reg-sub
 ::saved-searches
 :<- [::user-data]
 (fn [user _]
   (-> user :user-data :saved-searches)))

(re-frame/reg-sub
 ::saved-diversity-settings
 :<- [::user-data]
 (fn [user _]
   (-> user :user-data :saved-diversity-settings)))

;; Role basic permissions

(re-frame/reg-sub
  ::roles
  :<- [::user-data]
  (fn [user _]
    (:roles (:permissions user))))

(re-frame/reg-sub
  ::dev-overrides
  (fn [db _]
    ;; This value is only ever set from dev tools, which is only enabled on local builds
    (:project-devtools/privilege-override db)))

(re-frame/reg-sub
  ::check-privilege
  :<- [::user-data]
  :<- [::dev-overrides]
  (fn [[user overrides] [_ role-context k disable-overrides?]]
    (let [has-override? (when-not (true? disable-overrides?)
                          (contains? overrides k))]
      (if has-override?
        (get overrides k)
        (roles/check-privilege user role-context k)))))

(re-frame/reg-sub ::context-value-name
  (fn [[_ context-key v _locale]]
    (case context-key
      :city-code (re-frame/subscribe [:lipas.ui.sports-sites.subs/city v])
      :type-code (re-frame/subscribe [:lipas.ui.sports-sites.subs/type-by-type-code v])
      :activity (re-frame/subscribe [:lipas.ui.sports-sites.activities.subs/activity-by-value v])
      :lipas-id (re-frame/subscribe [:lipas.ui.sports-sites.subs/latest-rev v])))
  (fn [x [_ context-key _v locale]]
    (case context-key
      :lipas-id (:name x)
      :activity (get (:label x) locale)
      (get (:name x) locale))))
