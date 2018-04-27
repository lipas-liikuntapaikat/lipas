(ns lipas-ui.login.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::login-form
 (fn [db _]
   (-> db :user :login-form)))

(re-frame/reg-sub
 ::logged-in?
 (fn [db _]
   (-> db :user :logged-in?)))
