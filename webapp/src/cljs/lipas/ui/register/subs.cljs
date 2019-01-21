(ns lipas.ui.register.subs
  (:require
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::registration-form
 (fn [db _]
   (-> db :user :registration-form)))

(re-frame/reg-sub
 ::logged-in?
 (fn [db _]
   (-> db :logged-in?)))

(re-frame/reg-sub
 ::registration-error
 (fn [db _]
   (-> db :user :registration-error)))
