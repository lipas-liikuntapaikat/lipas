(ns lipas-ui.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::name
 (fn [db]
   (:name db)))

(re-frame/reg-sub
 ::active-panel
 (fn [db _]
   (:active-panel db)))

(re-frame/reg-sub
 ::menu-anchor
 (fn [db _]
   (:menu-anchor db)))

(re-frame/reg-sub
 ::drawer-open?
 (fn [db _]
   (:drawer-open? db)))

(re-frame/reg-sub
 ::translator
 (fn [db _]
   (:translator db)))

(re-frame/reg-sub
 ::logged-in?
 (fn [db _]
   (:logged-in? db)))

(re-frame/reg-sub
 ::user-initials
 (fn [db _]
   (let [fname (-> db :user :login :user-data :firstname)
         lname (-> db :user :login :user-data :lastname)]
     (str (or (first fname) "?") (or (first lname) "?")))))
