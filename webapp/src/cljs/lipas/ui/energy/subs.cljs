(ns lipas.ui.energy.subs
  (:require [lipas.ui.utils :as utils]
            [re-frame.core :as rf]))

(rf/reg-sub
 ::energy-consumption-site
 (fn [db _]
   (let [lipas-id (-> db :energy-consumption :lipas-id)]
     (get-in db [:sports-sites lipas-id]))))

(rf/reg-sub
 ::energy-consumption-rev
 (fn [db _]
   (let [lipas-id (-> db :energy-consumption :lipas-id)]
     (get-in db [:energy-consumption :editing lipas-id]))))

(rf/reg-sub
 ::energy-consumption-year
 (fn [db _]
   (-> db  :energy-consumption :year)))

(rf/reg-sub
 ::monthly-data-exists?
 :<- [::energy-consumption-rev]
 (fn [rev _]
   (-> rev
       :energy-consumption-monthly
       not-empty
       boolean)))

(rf/reg-sub
 ::edits-valid?
 (fn [[_ lipas-id] _]
   (rf/subscribe [::energy-consumption-rev lipas-id]))
 (fn [edit-data _]
   (-> edit-data
       utils/make-saveable
       utils/valid?)))

(rf/reg-sub
 ::energy-consumption-history
 (fn [db _]
   (let [lipas-id (-> db :energy-consumption :lipas-id)
         site (get-in db [:sports-sites lipas-id])
         history (utils/energy-consumption-history site)]
     (->> history (sort-by :year utils/reverse-cmp)))))

(rf/reg-sub
 ::energy-consumption-years-list
 :<- [::energy-consumption-history]
 (fn [history _]
   (utils/make-energy-consumption-year-list history)))

(rf/reg-sub
 ::energy-report
 (fn [db [_ year type-code]]
   (get-in db [:energy year type-code])))

(rf/reg-sub
 ::chart-energy-type
 (fn [db _]
   (-> db :energy :chart-energy-type)))

(def months [:jan :feb :mar :apr :may :jun :jul :aug :sep :oct :nov :dec])

(rf/reg-sub
 ::monthly-chart-data
 (fn [db [_ lipas-id year]]
   (let [site   (get-in db [:sports-sites lipas-id])
         rev    (utils/find-revision site year)]
     (->> rev
          :energy-consumption-monthly
          (reduce-kv
           (fn [res k v]
             (conj res (merge v {:month k})))
           [])
          (sort-by (comp #(.indexOf months %) :month))))))

(rf/reg-sub
 ::monthly-visitors-chart-data
 (fn [db [_ lipas-id year]]
   (let [site   (get-in db [:sports-sites lipas-id])
         rev    (utils/find-revision site year)]
     (->> rev
          :visitors-monthly
          (reduce-kv
           (fn [res k v]
             (conj res (merge v {:month k})))
           [])
          (sort-by (comp #(.indexOf months %) :month))))))
