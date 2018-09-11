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
 ::permission-to-publish?
 (fn [[_ lipas-id]]
   [(re-frame/subscribe [::permissions])
    (re-frame/subscribe [:lipas.ui.sports-sites.subs/latest-rev lipas-id])])
 (fn [[permissions sports-site] _]
   (when (and permissions sports-site)
     (permissions/publish? permissions sports-site))))

(re-frame/reg-sub
 ::reset-password-request-error
 (fn [db _]
   (-> db :user :reset-password-request :error)))

(re-frame/reg-sub
 ::reset-password-request-success
 (fn [db _]
   (-> db :user :reset-password-request :success)))
