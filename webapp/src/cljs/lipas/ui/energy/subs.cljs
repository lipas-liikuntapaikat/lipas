(ns lipas.ui.energy.subs
  (:require [lipas.ui.utils :as utils]
            [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::energy-consumption-site
 (fn [db _]
   (let [lipas-id (-> db :energy-consumption :lipas-id)]
     (get-in db [:sports-sites lipas-id]))))

(re-frame/reg-sub
 ::energy-consumption-rev
 (fn [db _]
   (let [lipas-id (-> db :energy-consumption :lipas-id)]
     (get-in db [:energy-consumption :editing lipas-id]))))

(re-frame/reg-sub
 ::energy-consumption-year
 (fn [db _]
   (-> db  :energy-consumption :year)))

(re-frame/reg-sub
 ::monthly-data-exists?
 :<- [::energy-consumption-rev]
 (fn [rev _]
   (-> rev
       :energy-consumption-monthly
       not-empty
       boolean)))

(re-frame/reg-sub
 ::edits-valid?
 (fn [[_ lipas-id] _]
   (re-frame/subscribe [::energy-consumption-rev lipas-id]))
 (fn [edit-data _]
   (-> edit-data
       utils/make-saveable
       utils/valid?)))

(re-frame/reg-sub
 ::energy-consumption-history
 (fn [db _]
   (let [lipas-id (-> db :energy-consumption :lipas-id)
         site (get-in db [:sports-sites lipas-id])
         history (utils/energy-consumption-history site)]
     (->> history (sort-by :year utils/reverse-cmp)))))

(re-frame/reg-sub
 ::energy-consumption-years-list
 :<- [::energy-consumption-history]
 (fn [history _]
   (utils/make-energy-consumption-year-list history)))

(re-frame/reg-sub
 ::energy-report
 (fn [db [_ year type-code]]
   (get-in db [:energy-stats year type-code])))

(re-frame/reg-sub
 ::chart-energy-type
 (fn [db _]
   (-> db :energy-stats :chart-energy-type)))
