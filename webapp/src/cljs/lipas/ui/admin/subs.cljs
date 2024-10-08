(ns lipas.ui.admin.subs
  (:require
   [clojure.string :as string]
   [lipas.utils :as cutils]
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::users
 (fn [db _]
   (-> db :admin :users)))

(re-frame/reg-sub
 ::users-status
 (fn [db _]
   (-> db :admin :users-status)))

(re-frame/reg-sub
 ::users-filter
 (fn [db _]
   (-> db :admin :users-filter)))

(defn- ->names-list [ks coll]
  (->> (select-keys coll ks)
       vals
       (map (comp :fi :name))
       (string/join ",")))

(defn ->users-list-entry
  [cities types activities user]
  {:id           (-> user :id)
   :email        (-> user :email)
   :firstname    (-> user :user-data :firstname)
   :lastname     (-> user :user-data :lastname)
   :admin?       (-> user :permissions :admin?)
   :sports-sites (-> user :permissions :sports-sites)
   :cities       (-> user :permissions :cities (->names-list cities))
   :types        (-> user :permissions :types (->names-list types))
   :activities   (->> user :permissions :activities
                      (map (fn [s] (get-in activities [s :label :fi]))))})

(re-frame/reg-sub
 ::users-list
 :<- [::users]
 :<- [::users-status]
 :<- [::users-filter]
 :<- [:lipas.ui.sports-sites.subs/cities-by-city-code]
 :<- [:lipas.ui.sports-sites.subs/active-types]
 :<- [:lipas.ui.sports-sites.activities.subs/data]
 (fn [[users status filter-text cities types activities] _]
   (let [users (->> users
                    vals
                    (filter (comp #{status} :status))
                    (map (partial ->users-list-entry cities types activities)))]
     (if (seq filter-text)
       (filter
        #(-> %
             str
             string/lower-case
             (string/includes? (string/lower-case filter-text))) users)
       users))))

(re-frame/reg-sub
 ::archived-users-list
 :<- [::archived-users]
 :<- [::users-filter]
 :<- [:lipas.ui.sports-sites.subs/cities-by-city-code]
 :<- [:lipas.ui.sports-sites.subs/active-types]
 (fn [[users filter-text cities types] _]
   (let [users (->> users (map (partial ->users-list-entry cities types)))]
     (if (not-empty filter-text)
       (filter
        #(-> %
             str
             string/lower-case
             (string/includes? (string/lower-case filter-text))) users)
       users))))

(re-frame/reg-sub
 ::selected-user
 (fn [db _]
   (get-in db [:admin :users (-> db :admin :selected-user)])))

(re-frame/reg-sub
 ::editing-user
 (fn [db _]
   (get-in db [:admin :editing-user])))

(re-frame/reg-sub
 ::new-role
 (fn [db _]
   (get-in db [:admin :new-role])))

(defn prettify-timestamp [s]
  (-> s
      (string/replace "T" " ")
      (string/split ".")
      first))

(re-frame/reg-sub
 ::user-history
 :<- [::editing-user]
 (fn [user _]
   (->> user :history :events
        (map #(update % :event-date prettify-timestamp))
        (sort-by :event-date cutils/reverse-cmp))))

(defn ->list-entry
  [locale [k v]]
  {:value k
   :label (str (get-in v [:name locale])
               " "
               k
               (when (not= "active" (:status v))
                 " POISTUNUT"))})

(re-frame/reg-sub
 ::types-list
 :<- [:lipas.ui.sports-sites.subs/all-types]
 (fn [types [_ locale]]
   (->> types
        (map (partial ->list-entry locale))
        (sort-by :label))))

(re-frame/reg-sub
 ::cities-list
 :<- [:lipas.ui.sports-sites.subs/cities-by-city-code]
 (fn [cities [_ locale]]
   (->> cities
        (map (partial ->list-entry locale))
        (sort-by :label))))

(re-frame/reg-sub
 ::sites-list
 :<- [:lipas.ui.sports-sites.subs/latest-sports-site-revs]
 (fn [sites _]
   (->> sites
        (map (fn [[lipas-id s]] {:value lipas-id :label (:name s)}))
        (sort-by :label))))

(re-frame/reg-sub
 ::activities-list
 :<- [:lipas.ui.sports-sites.activities.subs/data]
 (fn [activities [_ locale]]
   (->> activities
        (map (fn [[k m]] {:value k :label (get-in m [:label locale])}))
        (sort-by :label))))

(re-frame/reg-sub
 ::magic-link-dialog-open?
 (fn [db _]
   (-> db :admin :magic-link-dialog-open?)))

(re-frame/reg-sub
 ::magic-link-variants
 (fn [db _]
   (-> db :admin :magic-link-variants)))

(re-frame/reg-sub
 ::selected-magic-link-variant
 (fn [db _]
   (-> db :admin :selected-magic-link-variant)))

(re-frame/reg-sub
 ::selected-colors
 (fn [db _]
   (-> db :admin :color-picker)))

(re-frame/reg-sub
 ::selected-tab
 (fn [db _]
   (-> db :admin :selected-tab)))
