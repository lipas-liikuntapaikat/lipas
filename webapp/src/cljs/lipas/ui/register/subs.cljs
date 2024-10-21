(ns lipas.ui.register.subs
  (:require [re-frame.core :as rf]))

(rf/reg-sub ::registration-form
  (fn [db _]
    (-> db :user :registration-form)))

(rf/reg-sub ::logged-in?
  (fn [db _]
    (-> db :logged-in?)))

(rf/reg-sub ::registration-success?
  (fn [db _]
    (= "OK" (-> db :user :registration :status))))

(rf/reg-sub ::registration-error
  (fn [db _]
    (-> db :user :registration-error)))
