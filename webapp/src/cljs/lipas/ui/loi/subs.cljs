(ns lipas.ui.loi.subs
  (:require [clojure.spec.alpha :as s]
            [lipas.ui.utils :as utils]
            [re-frame.core :as rf]))

(rf/reg-sub
  ::loi
  (fn [db _]
    (:loi db)))

(rf/reg-sub
  ::statuses
  :<- [::loi]
  (fn [loi _]
    (:statuses loi)))

(rf/reg-sub
  ::delete-statuses
  :<- [::statuses]
  (fn [statuses _]
    (select-keys statuses ["out-of-service-temporarily"
                           "out-of-service-permanently"
                           "incorrect-data"])))

(rf/reg-sub
  ::view-mode
  :<- [::loi]
  (fn [loi _]
    (:view-mode loi)))

(rf/reg-sub
  ::selected-loi
  :<- [::loi]
  (fn [loi _]
    (:selected-loi loi)))

(rf/reg-sub
  ::editing-loi
  :<- [::loi]
  (fn [loi _]
    (:editing loi)))

(rf/reg-sub
  ::edits-valid?
  :<- [::editing-loi]
  :<- [::geoms]
  (fn [[loi geoms] _]
    (let [data (-> loi
                   (assoc :geometries geoms)
                   (assoc :event-date (utils/timestamp)))]
      (boolean
       (and geoms
            (s/valid? :lipas.loi/document data))))))

(rf/reg-sub
  ::loi-categories
  :<- [::loi]
  (fn [loi _]
    (:categories loi)))

(rf/reg-sub
  ::props
  :<- [::loi-categories]
  (fn [categories [_ loi-category loi-type]]
    (get-in categories [loi-category :types (keyword loi-type) :props])))

(rf/reg-sub
  ::geoms
  :<- [:lipas.ui.map.subs/new-geom]
  (fn [geoms _]
    geoms))

(rf/reg-sub
  ::geom-type
  :<- [::loi-categories]
  :<- [::editing-loi]
  (fn [[cats edit-data] _]
    (let [category (:loi-category edit-data)
          type     (:loi-type edit-data)]
      (get-in cats [category :types (keyword type) :geom-type] "Point"))))

(rf/reg-sub
  ::search-results
  :<- [::loi]
  (fn [loi _]
    (:search-results loi)))

(rf/reg-sub
  ::popup-localized
  :<- [::loi-categories]
  :<- [:lipas.ui.subs/translator]
  (fn [[cats tr] [_ loi-type loi-category]]
    (let [locale (tr)]
      {:loi-category (get-in cats [loi-category :label locale])
       :loi-type (get-in cats [loi-category :types (keyword loi-type) :label locale])})))
