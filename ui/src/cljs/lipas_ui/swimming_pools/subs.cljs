(ns lipas-ui.swimming-pools.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::active-tab
 (fn [db _]
   (-> db :swimming-pools :active-tab)))

(re-frame/reg-sub
 ::editing
 (fn [db _]
   (-> db :swimming-pools :editing)))

(re-frame/reg-sub
 ::dialogs
 (fn [db _]
   (-> db :swimming-pools :dialogs)))

(re-frame/reg-sub
 ::renovation-form
 (fn [db _]
   (-> db :swimming-pools :dialogs :renovation :data)))

(re-frame/reg-sub
 ::pool-form
 (fn [db _]
   (-> db :swimming-pools :dialogs :pool :data)))

(re-frame/reg-sub
 ::sauna-form
 (fn [db _]
   (-> db :swimming-pools :dialogs :sauna :data)))

(re-frame/reg-sub
 ::slide-form
 (fn [db _]
   (-> db :swimming-pools :dialogs :slide :data)))
