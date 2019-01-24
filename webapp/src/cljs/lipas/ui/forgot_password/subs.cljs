(ns lipas.ui.forgot-password.subs
  (:require
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::error
 (fn [db _]
   (-> db :reset-password :error)))

(re-frame/reg-sub
 ::success
 (fn [db _]
   (-> db :reset-password :success)))
