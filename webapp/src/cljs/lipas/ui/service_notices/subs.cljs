(ns lipas.ui.service-notices.subs
  (:require [lipas.roles :as roles]
            [lipas.ui.lazy :as lazy]
            [re-frame.core :as rf]))

(rf/reg-sub ::dismissed
  (fn [db _]
    (get-in db [:service-notices :dismissed] #{})))

(rf/reg-sub ::map-view?
  :<- [:lipas.ui.subs/current-route]
  (fn [route _]
    (= lazy/map-view (-> route :data :view))))

(rf/reg-sub ::ptv-user?
  :<- [:lipas.ui.user.subs/user-data]
  (fn [user _]
    (or (roles/check-privilege user {:city-code ::roles/any} :ptv/manage)
        (roles/check-privilege user {:city-code ::roles/any} :ptv/audit))))

(rf/reg-sub ::visible
  :<- [::dismissed]
  :<- [::map-view?]
  :<- [::ptv-user?]
  (fn [[dismissed map-view? ptv-user?] _]
    (->> [(when map-view? :mml-maps)
          (when ptv-user? :ptv-sync)]
         (remove nil?)
         (remove dismissed)
         vec)))

(rf/reg-sub ::visible?
  :<- [::visible]
  (fn [visible [_ id]]
    (boolean (some #{id} visible))))
