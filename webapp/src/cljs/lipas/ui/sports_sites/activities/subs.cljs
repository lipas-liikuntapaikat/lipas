(ns lipas.ui.sports-sites.activities.subs
  (:require
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::activities
 (fn [db _]
   (->> db :sports-sites :activities)))

(re-frame/reg-sub
 ::activities-by-type-code
 :<- [::activities]
 (fn [activities _]
   (:by-type-code activities)))

(re-frame/reg-sub
 ::activities-for-type
 :<- [::activities-by-type-code]
 (fn [activities [_ type-code]]
   (get activities type-code)))

(re-frame/reg-sub
 ::activity-type?
 (fn [[_ type-code]]
   (re-frame/subscribe [::activities-for-type type-code]))
 (fn [activities _]
   (some? activities)))
