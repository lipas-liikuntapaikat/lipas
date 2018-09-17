(ns lipas.ui.sports-sites.subs
  (:require [lipas.ui.utils :as utils]
            [clojure.spec.alpha :as s]
            [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::sports-sites
 (fn [db _]
   (-> db :sports-sites)))

(re-frame/reg-sub
 ::latest-sports-site-revs
 :<- [::sports-sites]
 (fn [sites _]
   (reduce-kv (fn [m k v]
                (assoc m k (get-in v [:history (:latest v)])))
              {}
              sites)))

(re-frame/reg-sub
 ::latest-rev
 (fn [db [_ lipas-id]]
   (let [latest (get-in db [:sports-sites lipas-id :latest])]
     (get-in db [:sports-sites lipas-id :history latest]))))

(re-frame/reg-sub
 ::editing-rev
 (fn [db [_ lipas-id]]
   (get-in db [:sports-sites lipas-id :editing])))

(re-frame/reg-sub
 ::editing?
 (fn [[_ lipas-id] _]
   (re-frame/subscribe [::editing-rev lipas-id]))
 (fn [edit-data _]
   ((complement empty?) edit-data)))

(re-frame/reg-sub
 ::edits-valid?
 (fn [[_ lipas-id] _]
   (re-frame/subscribe [::editing-rev lipas-id]))
 (fn [edit-data _]
   (let [spec (case (-> edit-data :type :type-code)
                (3110 3120 3130) :lipas.sports-site/swimming-pool
                (2510 2520)      :lipas.sports-site/ice-stadium
                :lipas/sports-site)]
     (as-> edit-data $
       (utils/make-saveable $)
       ;; (do (s/explain spec $) $)
       (s/valid? spec $)))))

(re-frame/reg-sub
 ::cities-by-city-code
 (fn [db _]
   (-> db :cities)))

(re-frame/reg-sub
 ::cities-list
 :<- [::cities-by-city-code]
 (fn [cities _ _]
   (vals cities)))

(re-frame/reg-sub
 ::admins
 (fn [db _]
   (-> db :admins)))

(re-frame/reg-sub
 ::owners
 (fn [db _]
   (-> db :owners)))

(re-frame/reg-sub
 ::all-types
 (fn [db _]
   (-> db :types)))

(re-frame/reg-sub
 ::materials
 (fn [db _]
   (-> db :materials)))

(re-frame/reg-sub
 ::building-materials
 (fn [db _]
   (-> db :building-materials)))

(re-frame/reg-sub
 ::supporting-structures
 (fn [db _]
   (-> db :supporting-structures)))

(re-frame/reg-sub
 ::ceiling-structures
 (fn [db _]
   (-> db :ceiling-structures)))
