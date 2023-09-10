(ns lipas.ui.loi.subs
  (:require
   [clojure.spec.alpha :as s]
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::loi
 (fn [db _]
   (:loi db)))

(re-frame/reg-sub
 ::loi-categories
 :<- [::loi]
 (fn [loi _]
   (:categories loi)))

(re-frame/reg-sub
 ::selected-loi-category
 :<- [::loi]
 (fn [loi _]
   ;; TODO un-hardcode once more is needed
   "outdoor-recreation-facilities"))

(re-frame/reg-sub
 ::selected-loi-type
 :<- [::loi]
 (fn [loi _]
   (:selected-type loi)))

(re-frame/reg-sub
 ::props
 :<- [::loi-categories]
 :<- [::selected-loi-category]
 :<- [::selected-loi-type]
 (fn [[categories selected-category selected-type] _]
   (get-in categories [selected-category :types (keyword selected-type) :props])))

(re-frame/reg-sub
 ::geoms
 :<- [:lipas.ui.map.subs/new-geom]
 (fn [geoms _]
   geoms))

(re-frame/reg-sub
 ::search-results
 :<- [::loi]
 (fn [loi _]
   (:search-results loi)))

(re-frame/reg-sub
 ::popup-localized
 :<- [::loi-categories]
 :<- [:lipas.ui.subs/translator]
 (fn [[cats tr] [_ loi-type loi-category]]
   (let [locale (tr)]
     {:loi-category (get-in cats [loi-category :label locale])
      :loi-type (get-in cats [loi-category :types (keyword loi-type) :label locale])})))
