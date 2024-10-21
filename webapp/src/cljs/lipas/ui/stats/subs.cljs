(ns lipas.ui.stats.subs
  (:require [re-frame.core :as rf]))

(rf/reg-sub ::selected-tab
  (fn [db _]
    (-> db :stats :selected-tab)))

(rf/reg-sub ::abolished-cities
  (fn [db _]
    (-> db :abolished-cities)))

;; Includes also historical cities that no-more exist
(rf/reg-sub ::cities
  :<- [:lipas.ui.sports-sites.subs/cities-by-city-code]
  :<- [:lipas.ui.stats.subs/abolished-cities]
  (fn [[active abolished] _]
    (merge active abolished)))

;; Includes also historical cities that no-more exist
(rf/reg-sub ::regions
  :<- [::cities]
  :<- [:lipas.ui.sports-sites.subs/avi-areas]
  :<- [:lipas.ui.sports-sites.subs/provinces]
  (fn [[cities avis provinces] _]
    (concat
      (for [[k v] cities]
        (assoc v :region-id (str "city-" k)))
      (for [[k v] avis]
        (assoc v :region-id (str "avi-" k)))
      (for [[k v] provinces]
        (assoc v :region-id (str "province-" k))))))
