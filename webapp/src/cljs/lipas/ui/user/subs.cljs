(ns lipas.ui.user.subs
  (:require [re-frame.core :as re-frame]
            [lipas.permissions :as permissions]))

(re-frame/reg-sub
 ::logged-in?
 (fn [db _]
   (:logged-in? db)))

(re-frame/reg-sub
 ::admin?
 (fn [db _]
   (-> db :user :login :permissions :admin?)))

(re-frame/reg-sub
 ::user-data
 (fn [db _]
   (-> db :user :login)))

(re-frame/reg-sub
 ::access-to-sports-sites
 (fn [db _]
   (-> db :user :login :permissions :sports-sites)))

(re-frame/reg-sub
 ::permissions
 (fn [db _]
   (-> db :user :login :permissions)))

(re-frame/reg-sub
 ::permission-to-cities
 :<- [::permissions]
 :<- [:lipas.ui.sports-sites.subs/cities-by-city-code]
 (fn [[{:keys [all-cities? cities]} all-cities] _]
   (if all-cities?
     all-cities
     (select-keys all-cities cities))))

(re-frame/reg-sub
 ::permission-to-types
 :<- [::permissions]
 :<- [:lipas.ui.sports-sites.subs/all-types]
 (fn [[{:keys [all-types? types]} all-types] _]
   (if all-types?
     all-types
     (select-keys all-types types))))

(re-frame/reg-sub
 ::permission-to-publish?
 (fn [[_ lipas-id]]
   [(re-frame/subscribe [::permissions])
    (re-frame/subscribe [:lipas.ui.sports-sites.subs/latest-rev lipas-id])])
 (fn [[permissions sports-site] _]
   (when (and permissions sports-site)
     (permissions/publish? permissions sports-site))))

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

(re-frame/reg-sub
 ::sports-sites
 :<- [:lipas.ui.sports-sites.subs/latest-sports-site-revs]
 :<- [::permissions]
 :<- [:lipas.ui.sports-sites.subs/cities-by-city-code]
 :<- [:lipas.ui.sports-sites.subs/all-types]
 (fn [[sites permissions cities types] [_ locale]]
   (when (and permissions sites)
     (->> sites
          vals
          (filter (partial permissions/publish? permissions))
          (map (partial ->list-entry locale cities types))))))

(re-frame/reg-sub
 ::selected-sports-site
 (fn [db _]
   (-> db :user :selected-sports-site)))

(re-frame/reg-sub
 ::reset-password-request-error
 (fn [db _]
   (-> db :user :reset-password-request :error)))

(re-frame/reg-sub
 ::reset-password-request-success
 (fn [db _]
   (-> db :user :reset-password-request :success)))
