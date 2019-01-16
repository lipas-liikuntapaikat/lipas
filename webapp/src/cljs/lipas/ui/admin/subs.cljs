(ns lipas.ui.admin.subs
  (:require
   [clojure.string :as string]
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::users
 (fn [db _]
   (-> db :admin :users)))

(re-frame/reg-sub
 ::users-filter
 (fn [db _]
   (-> db :admin :users-filter)))

(defn- ->names-list [ks coll]
  (->> (select-keys coll ks)
       vals
       (map (comp :fi :name))
       (string/join ",")))

(defn ->users-list-entry [cities types user]
  {:id           (-> user :id)
   :email        (-> user :email)
   :firstname    (-> user :user-data :firstname)
   :lastname     (-> user :user-data :lastname)
   :admin?       (-> user :permissions :admin?)
   :sports-sites (-> user :permissions :sports-sites)
   :cities       (-> user :permissions :cities (->names-list cities))
   :types        (-> user :permissions :types (->names-list types))})

(re-frame/reg-sub
 ::users-list
 :<- [::users]
 :<- [::users-filter]
 :<- [:lipas.ui.sports-sites.subs/cities-by-city-code]
 :<- [:lipas.ui.sports-sites.subs/all-types]
 (fn [[users filter-text cities types] _]
   (let [users (->> users vals (map (partial ->users-list-entry cities types)))]
     (if (not-empty filter-text)
       (filter #(string/includes? (str %) filter-text) users)
       users))))

(re-frame/reg-sub
 ::selected-user
 (fn [db _]
   (get-in db [:admin :users (-> db :admin :selected-user)])))

(re-frame/reg-sub
 ::editing-user
 (fn [db _]
   (get-in db [:admin :editing-user])))

(defn ->list-entry [locale [k v]]
  {:value k :label (str (get-in v [:name locale]) " " k)})

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
