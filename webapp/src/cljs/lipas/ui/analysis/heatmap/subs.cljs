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

(rf/reg-sub
 ::type-codes-with-labels
 :<- [::facets]
 (fn [facets _]
   (when facets
     (map (fn [{:keys [value count]}]
            {:value value
             :label (str "Type " value " (" count ")")
             :count count})
          (:type_codes facets)))))

(rf/reg-sub
 ::admins-with-counts
 :<- [::facets]
 (fn [facets _]
   (when facets
     (map (fn [{:keys [value count]}]
            {:value value
             :label (str value " (" count ")")
             :count count})
          (:admins facets)))))

(rf/reg-sub
 ::owners-with-counts
 :<- [::facets]
 (fn [facets _]
   (when facets
     (map (fn [{:keys [value count]}]
            {:value value
             :label (str value " (" count ")")
             :count count})
          (:owners facets)))))

(rf/reg-sub
 ::year-range
 :<- [::facets]
 (fn [facets _]
   (when facets
     (:year_range facets))))
