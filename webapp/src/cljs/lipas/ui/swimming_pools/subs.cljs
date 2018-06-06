(ns lipas.ui.swimming-pools.subs
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

(re-frame/reg-sub
 ::cities
 (fn [db _]
   (-> db :cities)))

(re-frame/reg-sub
 ::admins
 (fn [db _]
   (-> db :admins)))

(re-frame/reg-sub
 ::owners
 (fn [db _]
   (-> db :owners)))

(re-frame/reg-sub
 ::all-types
 (fn [db _]
   (-> db :types)))

(re-frame/reg-sub
 ::types
 :<- [::all-types]
 (fn [types _ _]
   (filter (comp #{3110} :type-code) types)))


(re-frame/reg-sub
 ::pool-types
 (fn [db _]
   (-> db :swimming-pools :pool-types)))

(re-frame/reg-sub
 ::filtering-methods
 (fn [db _]
   (-> db :swimming-pools :filtering-methods)))

(re-frame/reg-sub
 ::sauna-types
 (fn [db _]
   (-> db :swimming-pools :sauna-types)))

(re-frame/reg-sub
 ::building-materials
 (fn [db _]
   (-> db :building-materials)))
