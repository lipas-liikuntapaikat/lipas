(ns lipas-ui.subs
  (:require [re-frame.core :as re-frame]
            [clojure.string :refer [upper-case]]))

;; Level 2

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
 ::user-data
 (fn [db _]
   (-> db :user :login :user-data)))

;; Level 3

(re-frame/reg-sub
 ::user-initials
 :<- [::user-data]
 (fn [{:keys [firstname lastname]} _ _]
   (let [initial (comp upper-case #(or (first %) "?"))]
     (str (initial firstname) (initial lastname)))))
