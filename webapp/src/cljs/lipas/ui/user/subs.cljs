(ns lipas.ui.user.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::logged-in?
 (fn [db _]
   (:logged-in? db)))

(re-frame/reg-sub
 ::user-data
 (fn [db _]
   (-> db :user :login)))

(re-frame/reg-sub
 ::access-to-sports-sites
 (fn [db _]
   (-> db :user :login :permissions :sports-sites)))

(re-frame/reg-sub
 ::permission-to-publish?
 :<- [::access-to-sports-sites]
 (fn [ids [_ lipas-id]]
   (boolean (some #{lipas-id} ids))))
