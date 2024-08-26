(ns lipas.ui.subs
  (:require
   [clojure.string :refer [upper-case]]
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::current-route
 (fn [db _]
   (:current-route db)))

(re-frame/reg-sub
 ::current-view
 :<- [::current-route]
 (fn [route _]
   (-> route :data :view)))

(re-frame/reg-sub
 ::account-menu-anchor
 (fn [db _]
   (:account-menu-anchor db)))

(re-frame/reg-sub
 ::drawer-open?
 (fn [db _]
   (:drawer-open? db)))

(re-frame/reg-sub
 ::translator
 (fn [db _]
   (:translator db)))

(re-frame/reg-sub
 ::locale
 :<- [::translator]
 (fn [tr _]
   (tr)))

(re-frame/reg-sub
 ::active-notification
 (fn [db _]
   (:active-notification db)))

(re-frame/reg-sub
 ::active-confirmation
 (fn [db _]
   (:active-confirmation db)))

(re-frame/reg-sub
 ::active-disclaimer
 (fn [db _]
   (:active-disclaimer db)))

(re-frame/reg-sub
 ::logged-in?
 (fn [db _]
   (:logged-in? db)))

(re-frame/reg-sub
 ::user-data
 (fn [db _]
   (-> db :user :login :user-data)))

(comment ((comp (fnil upper-case "?") first) ""))
(comment ((comp (fnil upper-case "?") first) "kis"))
(re-frame/reg-sub
 ::user-initials
 :<- [::user-data]
 (fn [{:keys [firstname lastname]} _ _]
   (let [initial (comp (fnil upper-case "?") first)]
     (str (initial firstname) (initial lastname)))))

(re-frame/reg-sub
 ::show-nav?
 :<- [::current-route]
 (fn [current-route _]
   (-> current-route :data :hide-nav? not)))

(re-frame/reg-sub
 ::screen-size
 (fn [db _]
   (-> db :screen-size)))
