(ns lipas.ui.sports-sites.floorball.subs
  (:require [lipas.roles :as roles]
            [re-frame.core :as rf]))

(rf/reg-sub ::floorball
  (fn [db _]
    (-> db :sports-sites :floorball)))

(rf/reg-sub ::visibility
  :<- [:lipas.ui.user.subs/user-data]
  (fn [user [_ role-context]]
    ;; FIXME: Everyone has floorball view so check the floorball/edit privilege?
    (if (roles/check-privilege user role-context :floorball/view)
      :floorball
      :public)))

(rf/reg-sub ::type-codes
  :<- [::floorball]
  (fn [floorball _]
    (:type-codes floorball)))

(rf/reg-sub ::dialog-open?
  :<- [::floorball]
  (fn [floorball [_ dialog]]
    (get-in floorball [:dialogs dialog :open?])))

(rf/reg-sub ::dialog-data
  :<- [::floorball]
  (fn [floorball [_ dialog]]
    (get-in floorball [:dialogs dialog :data])))

(rf/reg-sub ::floor-elasticity
  :<- [::floorball]
  (fn [floorball _]
    (:floor-elasticity floorball)))

(rf/reg-sub ::player-entrance
  :<- [::floorball]
  (fn [floorball _]
    (:player-entrance floorball)))

(rf/reg-sub ::audience-stand-access
  :<- [::floorball]
  (fn [floorball _]
    (:audience-stand-access floorball)))

(rf/reg-sub ::car-parking-economics-model
  :<- [::floorball]
  (fn [floorball _]
    (:car-parking-economics-model floorball)))

(rf/reg-sub ::roof-trussess-operation-model
  :<- [::floorball]
  (fn [floorball _]
    (:roof-trussess-operation-model floorball)))

(rf/reg-sub ::field-surface-materials
  :<- [::floorball]
  (fn [floorball _]
    (:field-surface-materials floorball)))
