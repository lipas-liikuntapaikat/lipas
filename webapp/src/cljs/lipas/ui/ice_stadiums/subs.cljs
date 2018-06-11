(ns lipas.ui.ice-stadiums.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::active-tab
 (fn [db _]
   (-> db :ice-stadiums :active-tab)))

(re-frame/reg-sub
 ::editing
 (fn [db _]
   (-> db :ice-stadiums :editing)))

(re-frame/reg-sub
 ::access-to-sports-sites
 (fn [db _]
   (-> db :user :login :permissions :sports-sites)))

(re-frame/reg-sub
 ::sports-sites
 (fn [db _]
   (-> db :sports-sites)))

(re-frame/reg-sub
 ::sites-to-edit
 :<- [::access-to-sports-sites]
 :<- [::sports-sites]
 (fn [[ids sites] _]
   (as-> sites $
     (select-keys $ ids)
     (into {} (filter (comp #{2510 2520} :type-code :type second)) $)
     (not-empty $))))

(re-frame/reg-sub
 ::dialogs
 (fn [db _]
   (-> db :ice-stadiums :dialogs)))

(re-frame/reg-sub
 ::renovation-form
 (fn [db _]
   (-> db :ice-stadiums :dialogs :renovation :data)))

(re-frame/reg-sub
 ::rink-form
 (fn [db _]
   (-> db :ice-stadiums :dialogs :rink :data)))

(re-frame/reg-sub
 ::energy-form
 (fn [db _]
   (-> db :ice-stadiums :dialogs :energy :data)))

(re-frame/reg-sub
 ::cities
 (fn [db _]
   (-> db :cities)))

(re-frame/reg-sub
 ::admins
 (fn [db _]
   (-> db :admins)))

(re-frame/reg-sub
 ::owners
 (fn [db _]
   (-> db :owners)))

(re-frame/reg-sub
 ::all-types
 (fn [db _]
   (-> db :types)))

(re-frame/reg-sub
 ::types
 :<- [::all-types]
 (fn [types _ _]
   (filter (comp #{2510 2520} :type-code) types)))
