(ns lipas.ui.reports.subs
  (:require
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::selected-fields
 (fn [db _]
   (-> db :reports :selected-fields)))

(re-frame/reg-sub
 ::fields
 (fn [db _]
   (-> db :reports :fields)))

(re-frame/reg-sub
 ::downloading?
 (fn [db _]
   (-> db :reports :downloading?)))
