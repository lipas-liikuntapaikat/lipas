(ns lipas.ui.stats.sport.subs
  (:require
   [re-frame.core :as re-frame]
   [lipas.ui.utils :as utils]))

;;; Sports stats ;;;

(re-frame/reg-sub
 ::metrics
 (fn [db _]
   (-> db :stats :sport :metrics)))

(re-frame/reg-sub
 ::selected-metric
 (fn [db _]
   (-> db :stats :sport :selected-metric)))

(re-frame/reg-sub
 ::groupings
 (fn [db _]
   (-> db :stats :sport :groupings)))

(re-frame/reg-sub
 ::selected-grouping
 (fn [db _]
   (-> db :stats :sport :selected-grouping)))

(re-frame/reg-sub
 ::selected-cities
 (fn [db _]
   (-> db :stats :sport :selected-cities)))

(re-frame/reg-sub
 ::selected-types
 (fn [db _]
   (-> db :stats :sport :selected-types)))

(re-frame/reg-sub
 ::data*
 (fn [db _]
   (-> db :stats :sport :data)))

(re-frame/reg-sub
 ::data
 :<- [::data*]
 :<- [:lipas.ui.subs/translator]
 :<- [:lipas.ui.sports-sites.subs/cities-by-city-code]
 :<- [:lipas.ui.sports-sites.subs/all-types]
 :<- [::selected-grouping]
 :<- [::selected-metric]
 :<- [:lipas.ui.sports-sites.subs/provinces]
 :<- [:lipas.ui.sports-sites.subs/avi-areas]
 (fn [[data tr cities types grouping metric provinces avis] _]
   (let [locale       (tr)
         get-avi      (fn [city-code]
                        (-> city-code cities :avi-id avis :name locale))
         get-province (fn [city-code]
                        (-> city-code cities :province-id provinces :name locale))]
     (->> data
          (reduce
           (fn [res [k v]]
             (if (= "location.city.city-code" grouping)
               (let [city-name (get-in cities [k :name locale])]
                 (conj res (assoc v
                                  :city-code k
                                  :city-name city-name
                                  :province-name (get-province k)
                                  :avi-name (get-avi k))))
               (let [type-name (get-in types [k :name locale])]
                 (conj res (assoc v :type-code k :type-name type-name)))))
           [])
          (sort-by (keyword metric) utils/reverse-cmp)))))

(re-frame/reg-sub
 ::labels
 :<- [:lipas.ui.subs/translator]
 :<- [::metrics]
 (fn [[tr metrics] _]
   (let [locale (tr)]
     (into {:population      (tr :stats/population)
            :area-m2-sum     (tr :stats/area-m2-sum)
            :area-m2-avg     (tr :stats/area-m2-avg)
            :area-m2-min     (tr :stats/area-m2-min)
            :area-m2-max     (tr :stats/area-m2-max)
            :area-m2-count   (tr :stats/area-m2-count)
            :length-km-sum   (tr :stats/length-km-sum)
            :length-km-avg   (tr :stats/length-km-avg)
            :length-km-min   (tr :stats/length-km-min)
            :length-km-max   (tr :stats/length-km-max)
            :length-km-count (tr :stats/length-km-count)}
           (map (juxt (comp keyword first) (comp locale second)) metrics)))))

(re-frame/reg-sub
 ::headers
 :<- [:lipas.ui.subs/translator]
 :<- [::metrics]
 :<- [::selected-grouping]
 (fn [[tr metrics grouping] _]
   (let [locale (tr)]
     (remove
      nil?
      (into
       [(when (= "type.type-code" grouping)
          [:type-name (tr :lipas.sports-site/type)])
        (when (= "location.city.city-code" grouping)
          [:city-name (tr :lipas.location/city)])
        (when (= "location.city.city-code" grouping)
          [:province-name (tr :lipas.location/province)])
        (when (= "location.city.city-code" grouping)
          [:avi-name (tr :lipas.location/avi-area)])
        [:population (tr :stats/population)]
        [:area-m2-sum (tr :stats/area-m2-sum)]
        [:area-m2-avg (tr :stats/area-m2-avg)]
        [:area-m2-min (tr :stats/area-m2-min)]
        [:area-m2-max (tr :stats/area-m2-max)]
        [:area-m2-count (tr :stats/area-m2-count)]
        [:length-km-sum (tr :stats/length-km-sum)]
        [:length-km-avg   (tr :stats/length-km-avg)]
        [:length-km-min   (tr :stats/length-km-min)]
        [:length-km-max   (tr :stats/length-km-max)]]
       (map (juxt (comp keyword first) (comp locale second)) metrics))))))

(re-frame/reg-sub
 ::selected-view
 (fn [db _]
   (-> db :stats :sport :selected-view)))
