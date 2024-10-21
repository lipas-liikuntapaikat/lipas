(ns lipas.ui.analysis.diversity.subs
  (:require [clojure.spec.alpha :as s]
            [lipas.ui.utils :as utils]
            [re-frame.core :as rf]))

(rf/reg-sub ::diversity
  (fn [db _]
    (-> db :analysis :diversity)))

(rf/reg-sub ::selected-analysis-tab
  :<- [::diversity]
  (fn [analysis _]
    (:selected-tab analysis)))

(rf/reg-sub ::settings
  :<- [::diversity]
  (fn [analysis _]
    (:settings analysis)))

(rf/reg-sub ::categories
  :<- [::settings]
  (fn [settings _]
    (:categories settings)))

(rf/reg-sub ::category-presets
  :<- [::diversity]
  (fn [{:keys [category-presets user-category-presets]} _]
    (->> (merge user-category-presets category-presets)
         (map (fn [[k v]] {:label (:name v) :value k})))))

(rf/reg-sub ::seasonality-enabled?
  :<- [::diversity]
  (fn [diversity [_ s]]
    (->  diversity
         :selected-seasonalities
         (contains? s))))

(rf/reg-sub ::selected-category-preset
  :<- [::diversity]
  (fn [diversity _]
    (:selected-category-preset diversity)))

(rf/reg-sub ::max-distance-m
  :<- [::settings]
  (fn [settings _]
    (:max-distance-m settings)))

(rf/reg-sub ::analysis-candidates
  :<- [::diversity]
  (fn [diversity _]
    (diversity :areas)))

(rf/reg-sub ::selected-export-format
  :<- [::diversity]
  (fn [diversity _]
    (:selected-export-format diversity)))

(rf/reg-sub ::analysis-candidates-table-rows
  :<- [::analysis-candidates]
  (fn [m _]
    (->> (vals m)
         (map :properties))))

(rf/reg-sub ::analysis-candidates-table-headers
  :<- [::analysis-candidates-table-rows]
  (fn [candidates _]
    (-> candidates first keys
        (->> (reduce (fn [m k] (assoc m k {:label (name k)})) {})))))

(rf/reg-sub ::loading?
  (fn [db _]
    (-> db :analysis :diversity :loading?)))

(rf/reg-sub ::analysis-results
  :<- [::diversity]
  (fn [diversity _]
    (:results diversity)))

(rf/reg-sub ::chart-data
  :<- [::analysis-candidates]
  :<- [::analysis-results]
  (fn [[areas results] _]
    (into {}
          (for [[k v] results]
            [k (assoc v :area (get areas k))]))))

(rf/reg-sub ::grid-chart-data
  :<- [::chart-data]
  :<- [::selected-result-areas]
  (fn [[chart-data selected-areas]  _]
    (->> (for [[area-id m] chart-data
               :when (some #{area-id} selected-areas)
               f (:features (:grid m))]
           {:area-id area-id
            :population (get-in f [:properties :population] 0)
            :diversity-idx (get-in f [:properties :diversity_idx] 0)})
         (reduce
           (fn [res {:keys [diversity-idx population]}]
             (update res diversity-idx + population))
           {})
         (reduce-kv
           (fn [res k v]
             (conj res {:diversity-idx k :population v}))
           []))))

(defn guess-name
  [id f]
  (let [props (:properties f)
        {:keys [nimi name namn label]} props]
    (str (some identity (into [nimi name namn label] (conj (vals props) id))))))

(rf/reg-sub ::area-chart-data
  :<- [::chart-data]
  :<- [::selected-result-areas]
  (fn [[chart-data selected-areas]  _]
    (->>
      (for [[area-id m] chart-data
            :when       (some #{area-id} selected-areas)]
        {:name                 (guess-name area-id (:area m))
         :population           (-> m :aggs :population)
         :anonymized-count     (-> m :aggs :anonymized-count)
         :population-age-0-14  (-> m :aggs :population-age-0-14)
         :population-age-15-64 (-> m :aggs :population-age-15-64)
         :population-age-65-   (-> m :aggs :population-age-65-)
         :pwm                  (-> m :aggs :population-weighted-mean utils/round-safe)})
      (sort-by :population utils/reverse-cmp))))

(rf/reg-sub ::result-area-options
  :<- [::chart-data]
  (fn [results _]
    (map
      (fn [[area-id m]]
        {:value area-id :label (guess-name
                                 area-id (:area m))})
      results)))

(rf/reg-sub ::selected-result-areas
  :<- [::diversity]
  (fn [diversity _]
    (:selected-result-areas diversity)))

(rf/reg-sub ::any-analysis-done?
  :<- [::analysis-results]
  (fn [results _]
    (some? results)))

(rf/reg-sub ::selected-analysis-chart-tab
  :<- [::diversity]
  (fn [diversity _]
    (:selected-chart-tab diversity)))

(rf/reg-sub ::category-save-dialog-open?
  :<- [::diversity]
  (fn [diversity _]
    (:category-save-dialog-open? diversity)))

(rf/reg-sub ::new-preset-name
  :<- [::diversity]
  (fn [diversity _]
    (:new-preset-name diversity)))

(rf/reg-sub ::new-preset-name-valid?
  :<- [::new-preset-name]
  (fn [s _]
    (and (s/valid? :lipas.diversity.settings.categories/name s))))
