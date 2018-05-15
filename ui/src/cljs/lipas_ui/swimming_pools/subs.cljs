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
 ::add-renovation-form
 (fn [db _]
   (-> db :swimming-pools :dialogs :add-renovation :data)))

(re-frame/reg-sub
 ::add-pool-form
 (fn [db _]
   (-> db :swimming-pools :dialogs :add-pool :data)))
