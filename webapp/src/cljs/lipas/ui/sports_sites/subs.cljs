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
   (not-empty
    (reduce-kv (fn [m k v]
                 (assoc m k (get-in v [:history (:latest v)])))
               {}
               sites))))

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

(defn ->list-entry [{:keys [cities admins owners types locale size-categories]}
                    sports-site]
  (let [type          (types (-> sports-site :type :type-code))
        size-category (and
                       (= 2520 (-> sports-site :type :type-code))
                       (-> sports-site :type :size-category size-categories locale))
        admin         (admins (-> sports-site :admin))
        owner         (owners (-> sports-site :owner))
        city          (get cities (-> sports-site :location :city :city-code))]
    {:lipas-id          (-> sports-site :lipas-id)
     :name              (-> sports-site :name)
     :type              (or (utils/truncate-size-category size-category)
                            (-> type :name locale))
     :address           (-> sports-site :location :address)
     :postal-code       (-> sports-site :location :postal-code)
     :postal-office     (-> sports-site :location :postal-office)
     :city              (-> city :name locale)
     :construction-year (-> sports-site :construction-year)
     :renovation-years  (-> sports-site :renovation-years)
     :owner             (-> owner locale)
     :admin             (-> admin locale)
     :www               (-> sports-site :www)
     :email             (-> sports-site :email)
     :phone-number      (-> sports-site :phone-number)}))

(re-frame/reg-sub
 ::sites-list
 :<- [::latest-sports-site-revs]
 :<- [::cities-by-city-code]
 :<- [::admins]
 :<- [::owners]
 :<- [::all-types]
 :<- [:lipas.ui.ice-stadiums.subs/size-categories]
 (fn [[sites cities admins owners types size-categories] [_ locale type-codes]]
   (let [data {:locale          locale
               :cities          cities
               :admins          admins
               :owners          owners
               :types           types
               :size-categories size-categories}]
     (->> sites
          vals
          (filter (comp type-codes :type-code :type))
          (map (partial ->list-entry data))))))
