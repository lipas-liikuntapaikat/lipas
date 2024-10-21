(ns lipas.ui.forgot-password.subs
  (:require [re-frame.core :as rf]))

(rf/reg-sub ::error
  (fn [db _]
    (-> db :reset-password :error)))

(rf/reg-sub ::success
  (fn [db _]
    (-> db :reset-password :success)))
