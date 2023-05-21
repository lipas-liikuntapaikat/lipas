(ns lipas.ui.sports-sites.floorball.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::floorball
 (fn [db _]
   (-> db :sports-sites :floorball)))

(re-frame/reg-sub
 ::type-codes
 :<- [::floorball]
 (fn [floorball _]
   (:type-codes floorball)))

(re-frame/reg-sub
 ::dialog-open?
 :<- [::floorball]
 (fn [floorball [_ dialog]]
   (get-in floorball [:dialogs dialog :open?])))

(re-frame/reg-sub
 ::dialog-data
 :<- [::floorball]
 (fn [floorball [_ dialog]]
   (get-in floorball [:dialogs dialog :data])))
