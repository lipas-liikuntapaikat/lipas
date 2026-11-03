(ns lipas.ui.stats.sport.subs
  (:require [lipas.ui.utils :as utils]
            [re-frame.core :as rf]))

;;; Sports stats ;;;

(rf/reg-sub ::sports-stats
  (fn [db _]
    (-> db :stats :sport)))

(rf/reg-sub ::metrics
  :<- [::sports-stats]
  (fn [m _]
    (:metrics m)))

(rf/reg-sub ::selected-metric
  :<- [::sports-stats]
  (fn [m _]
    (:selected-metric m)))

(rf/reg-sub ::groupings
  :<- [::sports-stats]
  (fn [m _]
    (:groupings m)))

(rf/reg-sub ::selected-grouping
  :<- [::sports-stats]
  (fn [m _]
    (:selected-grouping m)))

(rf/reg-sub ::selected-cities
  :<- [::sports-stats]
  (fn [m _]
    (:selected-cities m)))

(rf/reg-sub ::selected-types
  :<- [::sports-stats]
  (fn [m _]
    (:selected-types m)))

(rf/reg-sub ::population-year
  :<- [::sports-stats]
  (fn [m _]
    (:population-year m)))

(rf/reg-sub ::data*
  :<- [::sports-stats]
  (fn [m _]
    (:data m)))

(rf/reg-sub ::data
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
               (conj res
                     (case grouping
                       "location.city.city-code"
                       (assoc v
                              :city-code k
                              :city-name (get-in cities [k :name locale])
                              :province-name (get-province k)
                              :avi-name (get-avi k))

                       "search-meta.location.province.id"
                       (assoc v
                              :province-id k
                              :province-name (get-in provinces [k :name locale]))

                       "search-meta.location.avi-area.id"
                       (assoc v
                              :avi-id k
                              :avi-name (get-in avis [k :name locale]))

                       "type.type-code"
                       (assoc v
                              :type-code k
                              :type-name (get-in types [k :name locale])))))
             [])
           (sort-by (keyword metric) utils/reverse-cmp)))))

(rf/reg-sub ::labels
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

(rf/reg-sub ::headers
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
           (when (#{"location.city.city-code"
                    "search-meta.location.province.id"} grouping)
             [:province-name (tr :lipas.location/province)])
           (when (#{"location.city.city-code"
                    "search-meta.location.avi-area.id"} grouping)
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

(rf/reg-sub ::selected-view
  :<- [::sports-stats]
  (fn [m _]
    (:selected-view m)))
