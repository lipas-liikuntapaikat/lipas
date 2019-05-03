(ns lipas.ui.stats.subs
  (:require
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::selected-tab
 (fn [db _]
   (-> db :stats :selected-tab)))

(re-frame/reg-sub
 ::selected-cities
 (fn [db _]
   (-> db :stats :selected-cities)))
