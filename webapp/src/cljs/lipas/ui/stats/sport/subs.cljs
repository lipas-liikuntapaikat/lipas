(ns lipas.ui.stats.sport.subs
  (:require [lipas.ui.utils :as utils]
            [re-frame.core :as rf]))

;;; Sports stats ;;;

(rf/reg-sub
 ::sports-stats
 (fn [db _]
   (-> db :stats :sport)))

(rf/reg-sub
 ::metrics
 :<- [::sports-stats]
 (fn [m _]
   (:metrics m)))

(rf/reg-sub
 ::selected-metric
 :<- [::sports-stats]
 (fn [m _]
   (:selected-metric m)))

(rf/reg-sub
 ::groupings
 :<- [::sports-stats]
 (fn [m _]
   (:groupings m)))

(rf/reg-sub
 ::selected-grouping
 :<- [::sports-stats]
 (fn [m _]
   (:selected-grouping m)))

(rf/reg-sub
 ::selected-cities
 :<- [::sports-stats]
 (fn [m _]
   (:selected-cities m)))

(rf/reg-sub
 ::selected-types
 :<- [::sports-stats]
 (fn [m _]
   (:selected-types m)))

(rf/reg-sub
 ::population-year
 :<- [::sports-stats]
 (fn [m _]
   (:population-year m)))

(rf/reg-sub
 ::data*
 :<- [::sports-stats]
 (fn [m _]
   (:data m)))

(rf/reg-sub
 ::data
 :<- [::data*]
 :<- [:lipas.ui.subs/translator]
 :<- [:lipas.ui.sports-sites.subs/cities-by-city-code]
 :<- [:lipas.ui.sports-sites.subs/active-types]
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

(rf/reg-sub
 ::labels
 :<- [:lipas.ui.subs/translator]
 :<- [::metrics]
 :<- [::population-year]
 (fn [[tr metrics pop-year] _]
   (let [locale (tr)]
     (into {:population      (str (tr :stats/population) " " pop-year)
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

(rf/reg-sub
 ::headers
 :<- [:lipas.ui.subs/translator]
 :<- [::metrics]
 :<- [::selected-grouping]
 :<- [::population-year]
 (fn [[tr metrics grouping pop-year] _]
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
        [:population (str (tr :stats/population) " " pop-year)]
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

(rf/reg-sub
 ::selected-view
 :<- [::sports-stats]
 (fn [m _]
   (:selected-view m)))
