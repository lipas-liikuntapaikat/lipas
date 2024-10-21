(ns lipas.ui.stats.subsidies.subs
  (:require [clojure.string :as str]
            [lipas.data.types :as types]
            [lipas.ui.utils :as utils]
            [re-frame.core :as rf]))

(rf/reg-sub
  ::selected-view
  (fn [db _]
    (-> db :stats :subsidies :selected-view)))

(rf/reg-sub
  ::selected-cities
  (fn [db _]
    (-> db :stats :subsidies :selected-cities)))

(rf/reg-sub
  ::selected-types
  (fn [db _]
    (-> db :stats :subsidies :selected-types)))

(rf/reg-sub
  ::selected-issuers
  (fn [db _]
    (-> db :stats :subsidies :selected-issuers)))

(rf/reg-sub
  ::selected-years
  (fn [db _]
    (-> db :stats :subsidies :selected-years)))

(rf/reg-sub
  ::selected-grouping
  (fn [db _]
    (-> db :stats :subsidies :selected-grouping)))

(rf/reg-sub
  ::groupings
  (fn [db _]
    (-> db :stats :subsidies :groupings)))

(rf/reg-sub
  ::chart-type
  (fn [db _]
    (-> db :stats :subsidies :chart-type)))

(rf/reg-sub
  ::issuers
  (fn [db _]
    (-> db :stats :subsidies :issuers)))

(rf/reg-sub
  ::types
  :<- [:lipas.ui.sports-sites.subs/active-types]
  (fn [types _]
    (assoc types (:type-code types/unknown) types/unknown)))

(rf/reg-sub
  ::grouping-data
  :<- [::selected-grouping]
  :<- [:lipas.ui.sports-sites.subs/avi-areas]
  :<- [:lipas.ui.sports-sites.subs/provinces]
  :<- [:lipas.ui.stats.subs/cities]
  :<- [::types]
  (fn [[grouping avis provinces cities types] _]
    (condp = grouping
      "avi"      [:avi-id avis]
      "province" [:province-id provinces]
      "city"     [:city-code cities]
      "type"     [:type-code types])))

(rf/reg-sub
  ::data*
  (fn [db _]
    (-> db :stats :subsidies :data)))

(rf/reg-sub
  ::data
  :<- [:lipas.ui.subs/translator]
  :<- [::data*]
  :<- [::grouping-data]
  :<- [::selected-years]
  :<- [:lipas.ui.sports-sites.subs/avi-areas]
  :<- [:lipas.ui.sports-sites.subs/provinces]
  :<- [:lipas.ui.stats.subs/cities]
  (fn [[tr data [k group] years avis provinces cities] _]
    (let [locale       (tr)
          op           :sum
          get-avi      (fn [city-code]
                         (-> city-code cities :avi-id avis :name locale))
          get-province (fn [city-code]
                         (-> city-code cities :province-id provinces :name locale))]
      (->> data
           :aggregations
           :by_grouping
           :buckets
           (reduce
             (fn [res {:keys [key doc_count amount]}]
               (conj res
                     (merge
                       {k       key
                        :year   (str/join ", " years)
                        :group  (get-in group [key :name locale])
                        :count  doc_count
                        :amount (-> amount op)}
                       (when (= :city-code k)
                         {:avi-name      (get-avi key)
                          :province-name (get-province key)}))))
             [])
           (sort-by k)))))

(rf/reg-sub
  ::ranking-data
  :<- [::data]
  (fn [data _]
    (sort-by :amount utils/reverse-cmp data)))

(rf/reg-sub
  ::headers
  :<- [:lipas.ui.subs/translator]
  :<- [::selected-grouping]
  (fn [[tr grouping] _]
    (->>
      [[:group (if (= "type" grouping)
                 (tr :lipas.sports-site/type)
                 (tr :stats/region))]
       (when (= "city" grouping)
         [:province-name (tr :lipas.location/province)])
       (when (= "city" grouping)
         [:avi-name (tr :lipas.location/avi-area)])
       [:year (tr :time/year)]
       [:amount (tr :stats/total-amount-1000e)]
       [:count (tr :stats/subsidies-count)]]
      (remove nil?))))

(rf/reg-sub
  ::labels
  :<- [::headers]
  (fn [headers _]
    (into {} headers)))
