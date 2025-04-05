(ns lipas.ui.subs
  (:require [clojure.string :refer [upper-case]]
            [lipas.data.types :as types]
            [re-frame.core :as rf]))

(rf/reg-sub ::current-route
  (fn [db _]
    (:current-route db)))

(rf/reg-sub ::current-view
  :<- [::current-route]
  (fn [route _]
    (-> route :data :view)))

(rf/reg-sub ::account-menu-anchor
  (fn [db _]
    (:account-menu-anchor db)))

(rf/reg-sub ::drawer-open?
  (fn [db _]
    (:drawer-open? db)))

(rf/reg-sub ::translator
  (fn [db _]
    (:translator db)))

(rf/reg-sub ::locale
  :<- [::translator]
  (fn [tr _]
    (tr)))

(rf/reg-sub ::active-notification
  (fn [db _]
    (:active-notification db)))

(rf/reg-sub ::active-confirmation
  (fn [db _]
    (:active-confirmation db)))

(rf/reg-sub ::active-disclaimer
  (fn [db _]
    (:active-disclaimer db)))

(rf/reg-sub ::logged-in?
  (fn [db _]
    (:logged-in? db)))

(rf/reg-sub ::user-data
  (fn [db _]
    (-> db :user :login :user-data)))

(rf/reg-sub ::sports-site-types
  (fn [_]
    [(rf/subscribe [:lipas.ui.sports-sites.subs/active-types])])
  (fn [[active-types] _]
    (for [[type-code type-data] active-types]
      (types/->type (assoc type-data :type-code type-code)))))

(comment ((comp (fnil upper-case "?") first) ""))
(comment ((comp (fnil upper-case "?") first) "kis"))
(rf/reg-sub ::user-initials
  :<- [::user-data]
  (fn [{:keys [firstname lastname]} _ _]
    (let [initial (comp (fnil upper-case "?") first)]
      (str (initial firstname) (initial lastname)))))

(rf/reg-sub ::show-nav?
  :<- [::current-route]
  (fn [current-route _]
    (-> current-route :data :hide-nav? not)))

(rf/reg-sub ::screen-size
  (fn [db _]
    (-> db :screen-size)))
