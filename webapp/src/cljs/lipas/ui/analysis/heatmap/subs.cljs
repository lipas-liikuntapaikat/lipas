(ns lipas.ui.analysis.heatmap.subs
  (:require [re-frame.core :as rf]))

(rf/reg-sub
  ::heatmap
  (fn [db _]
    (:heatmap db)))

(rf/reg-sub
  ::dimension
  :<- [::heatmap]
  (fn [heatmap _]
    (:dimension heatmap)))

(rf/reg-sub
  ::weight-by
  :<- [::heatmap]
  (fn [heatmap _]
    (:weight-by heatmap)))

(rf/reg-sub
  ::precision
  :<- [::heatmap]
  (fn [heatmap _]
    (:precision heatmap)))

(rf/reg-sub
  ::use-bbox-filter?
  :<- [::heatmap]
  (fn [heatmap _]
    (:use-bbox-filter? heatmap)))

(rf/reg-sub
  ::filters
  :<- [::heatmap]
  (fn [heatmap _]
    (:filters heatmap)))

(rf/reg-sub
  ::visual-params
  :<- [::heatmap]
  (fn [heatmap _]
    (:visual heatmap)))

(rf/reg-sub
  ::loading?
  :<- [::heatmap]
  (fn [heatmap _]
    (:loading? heatmap)))

(rf/reg-sub
  ::error
  :<- [::heatmap]
  (fn [heatmap _]
    (:error heatmap)))

(rf/reg-sub
  ::heatmap-data
  :<- [::heatmap]
  (fn [heatmap _]
    (:heatmap-data heatmap)))

(rf/reg-sub
  ::facets
  :<- [::heatmap]
  (fn [heatmap _]
    (:facets heatmap)))
