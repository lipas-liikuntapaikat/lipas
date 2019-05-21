(ns lipas.ui.reports.subs
  (:require
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::selected-tab
 (fn [db _]
   (-> db :reports :selected-tab)))

(re-frame/reg-sub
 ::dialog-open?
 (fn [db _]
   (-> db :reports :dialog-open?)))

(re-frame/reg-sub
 ::downloading?
 (fn [db _]
   (-> db :reports :downloading?)))

(re-frame/reg-sub
 ::fields
 (fn [db _]
   (-> db :reports :fields)))

(re-frame/reg-sub
 ::selected-fields
 (fn [db _]
   (-> db :reports :selected-fields)))
