(ns lipas.ui.user.subs
  (:require
   [re-frame.core :as re-frame]
   [lipas.permissions :as permissions]))

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
 :<- [::user]
 (fn [user _]
   (-> user :login :permissions :admin?)))

(re-frame/reg-sub
 ::user-data
  :<- [::user]
 (fn [user _]
   (:login user)))

(re-frame/reg-sub
 ::access-to-sports-sites
 :<- [::user]
 (fn [user _]
   (-> user :login :permissions :sports-sites)))

(re-frame/reg-sub
 ::permissions
 :<- [::user]
 (fn [user _]
   (-> user :login :permissions)))

(re-frame/reg-sub
 ::utp-user?
 :<- [::permissions]
 (fn [permissions _]
   (permissions/activities? permissions)))

(re-frame/reg-sub
 ::permission-to-cities
 :<- [::permissions]
 :<- [:lipas.ui.sports-sites.subs/cities-by-city-code]
 (fn [[{:keys [admin? all-cities? cities]} all-cities] _]
   (if (or admin? all-cities?)
     all-cities
     (select-keys all-cities cities))))

(re-frame/reg-sub
 ::permission-to-types
 :<- [::permissions]
 :<- [:lipas.ui.sports-sites.subs/active-types]
 (fn [[{:keys [admin? all-types? types]} all-types] _]
   (if (or admin? all-types?)
     all-types
     (select-keys all-types types))))

(re-frame/reg-sub
 ::can-add-sports-sites?
 :<- [::permissions]
 (fn [{:keys [admin? types all-types? cities all-cities?]} _]
   (or admin?
       (and
        (or all-cities? (seq cities))
        (or all-types? (seq types))))))

(re-frame/reg-sub
 ::permission-to-activities
 :<- [::permissions]
 :<- [:lipas.ui.sports-sites.activities.subs/data]
 (fn [[{:keys [admin? activities]} all-activities] _]
   (if admin?
     all-activities
     (select-keys all-activities activities))))

(re-frame/reg-sub
 ::can-add-lois?
 :<- [::permissions]
 (fn [permissions _]
   (permissions/activities? permissions)))

(re-frame/reg-sub
 ::can-add-lois-only?
 :<- [::can-add-sports-sites?]
 :<- [::can-add-lois?]
 (fn [[can-add-sports-sites? can-add-lois?] _]
   (and can-add-lois? (not can-add-sports-sites?))))

(re-frame/reg-sub
 ::permission-to-publish?
 (fn [[_ lipas-id]]
   [(re-frame/subscribe [::permissions])
    (re-frame/subscribe [:lipas.ui.sports-sites.subs/latest-rev lipas-id])])
 (fn [[permissions sports-site] _]
   (when (and permissions sports-site)
     (permissions/modify-sports-site? permissions sports-site))))

(re-frame/reg-sub
 ::permission-to-publish-site?
 :<- [::permissions]
 (fn [permissions [_ sports-site]]
   (when (and permissions sports-site)
     (permissions/publish? permissions sports-site))))

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
  [permissions {:keys [status] :as sports-site}]
  (and
   (permissions/publish? permissions sports-site)
   (#{"planned" "active" "out-of-service-temporarily"} status)))

(re-frame/reg-sub
 ::sports-sites
 :<- [:lipas.ui.sports-sites.subs/latest-sports-site-revs]
 :<- [::permissions]
 :<- [:lipas.ui.sports-sites.subs/cities-by-city-code]
 :<- [:lipas.ui.sports-sites.subs/active-types]
 (fn [[sites permissions cities types] [_ locale]]
   (when (and permissions sites)
     (->> sites
          vals
          (filter (partial show? permissions))
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

(re-frame/reg-sub
 ::experimental-features?
 :<- [::data]
 (fn [user _]
   (-> user :experimental-features?)))
