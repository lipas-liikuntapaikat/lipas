(ns lipas.ui.stats.age-structure.subs
  (:require
   [re-frame.core :as re-frame]))

;;; Age structure ;;;

(re-frame/reg-sub
 ::selected-cities
 (fn [db _]
   (-> db :stats :age-structure :selected-cities)))

(re-frame/reg-sub
 ::selected-types
 (fn [db _]
   (-> db :stats :age-structure :selected-types)))

(re-frame/reg-sub
 ::groupings
 (fn [db _]
   (-> db :stats :age-structure :groupings)))

(re-frame/reg-sub
 ::selected-grouping
 (fn [db _]
   (-> db :stats :age-structure :selected-grouping)))

(re-frame/reg-sub
 ::selected-interval
 (fn [db _]
   (-> db :stats :age-structure :selected-interval)))

(re-frame/reg-sub
 ::data*
 (fn [db _]
   (-> db :stats :age-structure :data)))

(re-frame/reg-sub
 ::data
 :<- [::data*]
 (fn [data _]
   (let [data (-> data :years :buckets)]
     (->> data
          (reduce
           (fn [m v]
             (let [year  (-> v :key :construction-year)
                   owner (-> v :key :owner)
                   count (-> v :doc_count)]
               (-> m
                   (assoc-in [year owner] count)
                   (assoc-in [year :construction-year] year))))
           {})
          vals
          (sort-by :construction-year)))))

(re-frame/reg-sub
 ::labels
 :<- [:lipas.ui.subs/translator]
 :<- [:lipas.ui.sports-sites.subs/admins]
 :<- [:lipas.ui.sports-sites.subs/owners]
 (fn [[tr admins owners] _]
   (let [locale (tr)]
     (merge
      (into {} (map (juxt first (comp locale second)) admins))
      (into {} (map (juxt first (comp locale second)) owners))
      {:y-axis (tr :stats/sports-sites-count)}))))

(re-frame/reg-sub
 ::headers
 :<- [:lipas.ui.subs/translator]
 :<- [:lipas.ui.sports-sites.subs/admins]
 :<- [:lipas.ui.sports-sites.subs/owners]
 :<- [::selected-grouping]
 (fn [[tr admins owners grouping] _]
   (let [locale (tr)
         coll   (case grouping "admin" admins owners)]
     (into [[:construction-year (tr :lipas.sports-site/construction-year)]]
           (for [[k v] (sort-by (comp locale second) coll)]
             [k (locale v)])))))

(re-frame/reg-sub
 ::selected-view
 (fn [db _]
   (-> db :stats :age-structure :selected-view)))
