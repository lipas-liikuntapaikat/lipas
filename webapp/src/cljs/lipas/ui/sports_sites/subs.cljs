(ns lipas.ui.sports-sites.subs
  (:require [lipas.ui.utils :as utils]
            [clojure.spec.alpha :as s]
            [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::sports-sites
 (fn [db _]
   (-> db :sports-sites)))

(re-frame/reg-sub
 ::sports-site
 (fn [db [_ lipas-id]]
   (get-in db [:sports-sites lipas-id])))

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

(re-frame/reg-sub
 ::display-site
 (fn [[_ lipas-id] _]
   [(re-frame/subscribe [:lipas.ui.sports-sites.subs/sports-site lipas-id])
    (re-frame/subscribe [:lipas.ui.sports-sites.subs/cities-by-city-code])
    (re-frame/subscribe [:lipas.ui.sports-sites.subs/admins])
    (re-frame/subscribe [:lipas.ui.sports-sites.subs/owners])
    (re-frame/subscribe [:lipas.ui.sports-sites.subs/all-types])
    (re-frame/subscribe [:lipas.ui.ice-stadiums.subs/size-categories])
    (re-frame/subscribe [:lipas.ui.sports-sites.subs/materials])
    (re-frame/subscribe [:lipas.ui.subs/translator])])
 (fn [[site cities admins owners types size-categories
       materials translator] _]
   (when site
     (let [locale        (translator)
           latest        (or (utils/latest-edit (:edits site))
                             (get-in site [:history (:latest site)]))
           type          (types (-> latest :type :type-code))
           size-category (size-categories (-> latest :type :size-category))
           admin         (admins (-> latest :admin))
           owner         (owners (-> latest :owner))
           city          (get cities (-> latest :location :city :city-code))

           get-material #(get-in materials [% locale])]

       {:lipas-id       (-> latest :lipas-id)
        :name           (-> latest :name)
        :marketing-name (-> latest :marketing-name)
        :type
        {:name          (-> type :name locale)
         :type-code     (-> latest :type :type-code)
         :size-category (-> size-category locale)}
        :owner          (-> owner locale)
        :admin          (-> admin locale)
        :phone-number   (-> latest :phone-number)
        :www            (-> latest :www)
        :email          (-> latest :email)

        :construction-year (-> latest :construction-year)
        :renovation-years  (-> latest :renovation-years)

        :location
        {:address       (-> latest :location :address)
         :postal-code   (-> latest :location :postal-code)
         :postal-office (-> latest :location :postal-office)
         :city
         {:name         (-> city :name locale)
          :neighborhood (-> latest :location :city :neighborhood)}}

        :building (:building latest)}))))
