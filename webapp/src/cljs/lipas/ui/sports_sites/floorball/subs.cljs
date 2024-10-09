(ns lipas.ui.sports-sites.floorball.subs
  (:require
   [lipas.roles :as roles]
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::floorball
 (fn [db _]
   (-> db :sports-sites :floorball)))

(re-frame/reg-sub ::visibility
  :<- [:lipas.ui.user.subs/user-data]
  (fn [user [_ role-context]]
    (if (roles/check-privilege user role-context :floorball/view)
      :floorball
      :public)))

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

(re-frame/reg-sub
 ::floor-elasticity
 :<- [::floorball]
 (fn [floorball _]
   (:floor-elasticity floorball)))

(re-frame/reg-sub
 ::player-entrance
 :<- [::floorball]
 (fn [floorball _]
   (:player-entrance floorball)))

(re-frame/reg-sub
 ::audience-stand-access
 :<- [::floorball]
 (fn [floorball _]
   (:audience-stand-access floorball)))

(re-frame/reg-sub
 ::car-parking-economics-model
 :<- [::floorball]
 (fn [floorball _]
   (:car-parking-economics-model floorball)))

(re-frame/reg-sub
 ::roof-trussess-operation-model
 :<- [::floorball]
 (fn [floorball _]
   (:roof-trussess-operation-model floorball)))

(re-frame/reg-sub
 ::field-surface-materials
 :<- [::floorball]
 (fn [floorball _]
   (:field-surface-materials floorball)))
