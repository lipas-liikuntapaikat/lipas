(ns lipas.ui.login.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::login-form
 (fn [db _]
   (-> db :user :login-form)))

(re-frame/reg-sub
 ::logged-in?
 (fn [db _]
   (:logged-in? db)))

(re-frame/reg-sub
 ::login-error
 (fn [db _]
   (-> db :user :login-error)))

(re-frame/reg-sub
 ::comeback-path
 (fn [db _]
   (:comeback-path db)))
