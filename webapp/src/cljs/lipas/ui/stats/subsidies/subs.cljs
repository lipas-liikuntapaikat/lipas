(ns lipas.ui.stats.subsidies.subs
  (:require
   [lipas.ui.utils :as utils]
   [lipas.data.types :as types]
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::selected-view
 (fn [db _]
   (-> db :stats :subsidies :selected-view)))

(re-frame/reg-sub
 ::selected-cities
 (fn [db _]
   (-> db :stats :subsidies :selected-cities)))

(re-frame/reg-sub
 ::selected-types
 (fn [db _]
   (-> db :stats :subsidies :selected-types)))

(re-frame/reg-sub
 ::selected-issuers
 (fn [db _]
   (-> db :stats :subsidies :selected-issuers)))

(re-frame/reg-sub
 ::selected-years
 (fn [db _]
   (-> db :stats :subsidies :selected-years)))

(re-frame/reg-sub
 ::selected-grouping
 (fn [db _]
   (-> db :stats :subsidies :selected-grouping)))

(re-frame/reg-sub
 ::groupings
 (fn [db _]
   (-> db :stats :subsidies :groupings)))

(re-frame/reg-sub
 ::chart-type
 (fn [db _]
   (-> db :stats :subsidies :chart-type)))

(re-frame/reg-sub
 ::issuers
 (fn [db _]
   (-> db :stats :subsidies :issuers)))

(re-frame/reg-sub
 ::types
 :<- [:lipas.ui.sports-sites.subs/all-types]
 (fn [types _]
   (assoc types (:type-code types/unknown) types/unknown)))

(re-frame/reg-sub
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

(re-frame/reg-sub
 ::data*
 (fn [db _]
   (-> db :stats :subsidies :data)))

(re-frame/reg-sub
 ::data
 :<- [:lipas.ui.subs/translator]
 :<- [::data*]
 :<- [::grouping-data]
 (fn [[tr data [k group]] _]
   (let [locale (tr)
         op     :sum]
     (->> data
          :aggregations
          :by_grouping
          :buckets
          (reduce
           (fn [res {:keys [key by_year]}]
             (into res
                   (for [b (:buckets by_year)]
                     {k       key
                      :year   (:key b)
                      :group  (get-in group [key :name locale])
                      :count  (-> b :doc_count)
                      :amount (-> b :amount op)})))
           [])
          (sort-by k)))))

(re-frame/reg-sub
 ::ranking-data
 :<- [::data]
 (fn [data _]
   (sort-by :amount utils/reverse-cmp data)))

(re-frame/reg-sub
 ::headers
 :<- [:lipas.ui.subs/translator]
 :<- [::selected-grouping]
 (fn [[tr grouping] _]
   [[:group (if (= "type" grouping )
              (tr :lipas.sports-site/type)
              (tr :stats/region))]
    [:year (tr :time/year)]
    [:amount (tr :stats/total-amount-1000e)]
    [:count (tr :stats/subsidies-count)]]))

(re-frame/reg-sub
 ::labels
 :<- [::headers]
 (fn [headers _]
   (into {} headers)))
