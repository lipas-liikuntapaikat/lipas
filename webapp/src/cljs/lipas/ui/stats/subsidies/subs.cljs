(ns lipas.ui.stats.subsidies.subs
  (:require
   [clojure.string :as string]
   [lipas.data.types :as types]
   [lipas.ui.utils :as utils]
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
                     :year   (string/join ", " years)
                     :group  (get-in group [key :name locale])
                     :count  doc_count
                     :amount (-> amount op)}
                    (when (= :city-code k)
                      {:avi-name      (get-avi key)
                       :province-name (get-province key)}))))
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
   (->>
    [[:group (if (= "type" grouping )
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

(re-frame/reg-sub
 ::labels
 :<- [::headers]
 (fn [headers _]
   (into {} headers)))
