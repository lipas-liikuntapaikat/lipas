(ns lipas.ui.login.subs
  (:require [re-frame.core :as rf]))

(rf/reg-sub
 ::login-form
 (fn [db _]
   (-> db :user :login-form)))

(rf/reg-sub
 ::magic-link-form
 (fn [db _]
   (-> db :user :magic-link-form)))

(rf/reg-sub
 ::login-mode
 (fn [db _]
   (-> db :user :login-mode)))

(rf/reg-sub
 ::logged-in?
 (fn [db _]
   (:logged-in? db)))

(rf/reg-sub
 ::login-error
 (fn [db _]
   (-> db :user :login-error)))

(rf/reg-sub
 ::comeback-path*
 (fn [db _]
   (:comeback-path db)))

(rf/reg-sub
 ::comeback-path
 :<- [::comeback-path*]
 (fn [path _]
   (when-not (#{"/#/login" :lipas.ui.routes/login} path)
     path)))

(rf/reg-sub
 ::magic-link-ordered?
 (fn [db _]
   (-> db :user :magic-link-ordered?)))
