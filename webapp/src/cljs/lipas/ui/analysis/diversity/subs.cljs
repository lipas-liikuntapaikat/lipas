(ns lipas.ui.analysis.diversity.subs
  (:require [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::diversity
 (fn [db _]
   (-> db :analysis :diversity)))

(re-frame/reg-sub
 ::selected-analysis-tab
 :<- [::diversity]
 (fn [analysis _]
   (:selected-tab analysis)))

(re-frame/reg-sub
 ::settings
 :<- [::diversity]
 (fn [analysis _]
   (:settings analysis)))

(re-frame/reg-sub
 ::categories
 :<- [::settings]
 (fn [settings _]
   (:categories settings)))

(re-frame/reg-sub
 ::category-presets
 :<- [::diversity]
 (fn [diversity _]
   (->> (:category-presets diversity)
        (map (fn [[k v]] {:label (:name v) :value k})))))

(re-frame/reg-sub
 ::selected-category-preset
 :<- [::diversity]
 (fn [diversity _]
   (:selected-category-preset diversity)))

(re-frame/reg-sub
 ::analysis-area-fcoll
 :<- [::settings]
 (fn [settings _]
   (:analysis-area-fcoll settings)))

(re-frame/reg-sub
 ::max-distance-m
 :<- [::settings]
 (fn [settings _]
   (:max-distance-m settings)))

(re-frame/reg-sub
 ::analysis-candidates
 :<- [::diversity]
 (fn [diversity _]
   (diversity :areas)))

(re-frame/reg-sub
 ::analysis-candidates-table-rows
 :<- [::analysis-candidates]
 (fn [m _]
   (->> (vals m)
        (map :properties))))

(re-frame/reg-sub
 ::analysis-candidates-table-headers
 :<- [::analysis-candidates-table-rows]
 (fn [candidates _]
   (-> candidates first keys
       (->> (reduce (fn [m k] (assoc m k {:label (name k)})) {})))))

(re-frame/reg-sub
 ::loading?
 (fn [db _]
   (-> db :analysis :diversity :loading?)))
