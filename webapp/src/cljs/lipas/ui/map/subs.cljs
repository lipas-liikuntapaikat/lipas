(ns lipas.ui.map.subs
  (:require [re-frame.core :as re-frame]))

(defn ->feature [site]
  (-> site
      :location
      :geometries
      (update-in [:features]
                 #(map (fn [f]
                         (assoc-in f [:properties :name] (:name site)))
                       %))))

(re-frame/reg-sub
 ::filters
 (fn [db _]
   (-> db :map :filters)))

(re-frame/reg-sub
 ::basemap
 (fn [db _]
   (-> db :map :basemap)))

(re-frame/reg-sub
 ::center
 (fn [db _]
   (-> db :map :center)))

(re-frame/reg-sub
 ::zoom
 (fn [db _]
   (-> db :map :zoom)))

(re-frame/reg-sub
 ::geometries
 :<- [:lipas.ui.sports-sites.subs/latest-sports-site-revs]
 :<- [::filters]
 (fn [[sites filters] _]
   (let [type-codes (cond-> #{}
                      (:ice-stadium filters) (into #{2510 2520})
                      (:swimming-pool filters) (into #{3110 3130}))]
     (->> sites
          vals
          (filter (comp type-codes :type-code :type))
          (map ->feature)))))
