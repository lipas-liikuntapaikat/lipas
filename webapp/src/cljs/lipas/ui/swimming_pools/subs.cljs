(ns lipas.ui.swimming-pools.subs
  (:require [re-frame.core :as re-frame]
            [lipas.ui.utils :as utils]
            [lipas.utils :as utils2]))

(re-frame/reg-sub
 ::active-tab
 (fn [db _]
   (-> db :swimming-pools :active-tab)))

(re-frame/reg-sub
 ::latest-swimming-pool-revs
 :<- [:lipas.ui.sports-sites.subs/latest-sports-site-revs]
 (fn [sites _]
   (as-> sites $
     (into {} (filter (comp #{3110 3120 3130} :type-code :type second)) $)
     (not-empty $))))

(re-frame/reg-sub
 ::latest-updates
 :<- [::latest-swimming-pool-revs]
 (fn [sites [_ tr]]
   (->> sites
        vals
        (sort-by :event-date utils/reverse-cmp)
        (take 5)
        (map #(select-keys % [:lipas-id :name :event-date]))
        (map #(update % :event-date (comp tr
                                          (partial keyword :time)
                                          utils/pretty-since-kw))))))

(re-frame/reg-sub
 ::did-you-know-stats
 :<- [::latest-swimming-pool-revs]
 (fn [sites _]
   {:total-count       (count sites)
    :count-by-type     (->> (vals sites)
                            (map (comp :type-code :type))
                            frequencies)
    :construction-year (->> sites
                            vals
                            (map :construction-year)
                            (remove nil?)
                            utils2/simple-stats)
    :water-area-sum    (->> sites
                            vals
                            (map (comp :total-water-area-m2 :building))
                            (reduce +))
    :slide-sum (->> sites
                    vals
                    (mapcat (comp #(map :length-m %) :slides))
                    (reduce +))
    :showers-sum (->> sites
                      vals
                      (map #(+ (-> % :facilities :showers-men-count)
                               (-> % :facilities :showers-women-count)))
                      (reduce +))}))

(re-frame/reg-sub
 ::sites-to-edit
 :<- [:lipas.ui.user.subs/access-to-sports-sites]
 :<- [:lipas.ui.user.subs/admin?]
 :<- [::latest-swimming-pool-revs]
 (fn [[ids admin? sites] _]
   (if admin?
     (not-empty sites)
     (not-empty (select-keys sites ids)))))

(re-frame/reg-sub
 ::sites-to-edit-list
 :<- [::sites-to-edit]
 (fn [sites _]
   (not-empty (sort-by :name (vals sites)))))

(re-frame/reg-sub
 ::sites-to-draft-list
 :<- [::latest-swimming-pool-revs]
 (fn [sites _]
   (not-empty (sort-by :name (vals sites)))))

(re-frame/reg-sub
 ::dialogs
 (fn [db _]
   (-> db :swimming-pools :dialogs)))

(re-frame/reg-sub
 ::pool-form
 (fn [db _]
   (-> db :swimming-pools :dialogs :pool :data)))

(re-frame/reg-sub
 ::sauna-form
 (fn [db _]
   (-> db :swimming-pools :dialogs :sauna :data)))

(re-frame/reg-sub
 ::slide-form
 (fn [db _]
   (-> db :swimming-pools :dialogs :slide :data)))

(re-frame/reg-sub
 ::types-by-type-code
 :<- [:lipas.ui.sports-sites.subs/all-types]
 (fn [types _]
   (select-keys types [3110 3120 3130])))

(re-frame/reg-sub
 ::types-list
 :<- [::types-by-type-code]
 (fn [types _ _]
   (vals types)))

(re-frame/reg-sub
 ::pool-types
 (fn [db _]
   (-> db :swimming-pools :pool-types)))

(re-frame/reg-sub
 ::pool-structures
 (fn [db _]
   (-> db :swimming-pools :pool-structures)))

(re-frame/reg-sub
 ::heat-sources
 (fn [db _]
   (-> db :swimming-pools :heat-sources)))

(re-frame/reg-sub
 ::filtering-methods
 (fn [db _]
   (-> db :swimming-pools :filtering-methods)))

(re-frame/reg-sub
 ::sauna-types
 (fn [db _]
   (-> db :swimming-pools :sauna-types)))

(re-frame/reg-sub
 ::slide-structures
 (fn [db _]
   (-> db :swimming-pools :slide-structures)))

(defn ->list-entry [{:keys [cities admins owners types locale]} pool]
  (let [type  (types (-> pool :type :type-code))
        admin (admins (-> pool :admin))
        owner (owners (-> pool :owner))
        city  (get cities (-> pool :location :city :city-code))]
    {:lipas-id          (-> pool :lipas-id)
     :name              (-> pool :name)
     :type              (-> type :name locale)
     :address           (-> pool :location :address)
     :postal-code       (-> pool :location :postal-code)
     :city              (-> city :name locale)
     :construction-year (-> pool :construction-year)
     :renovation-years  (-> pool :renovation-years)
     :owner             (-> owner locale)
     :admin             (-> admin locale)}))

(re-frame/reg-sub
 ::sites-list
 :<- [::latest-swimming-pool-revs]
 :<- [:lipas.ui.sports-sites.subs/cities-by-city-code]
 :<- [:lipas.ui.sports-sites.subs/admins]
 :<- [:lipas.ui.sports-sites.subs/owners]
 :<- [::types-by-type-code]
 (fn [[pools cities admins owners types] [_ locale]]
   (let [data {:locale locale
               :cities cities
               :admins admins
               :owners owners
               :types  types}]
     (sort-by :city (map (partial ->list-entry data) (vals pools))))))

(re-frame/reg-sub
 ::display-site-raw
 (fn [db _]
   (let [lipas-id (-> db :swimming-pools :displaying)]
     (get-in db [:sports-sites lipas-id]))))

(re-frame/reg-sub
 ::display-site
 :<- [::display-site-raw]
 :<- [:lipas.ui.sports-sites.subs/cities-by-city-code]
 :<- [:lipas.ui.sports-sites.subs/admins]
 :<- [:lipas.ui.sports-sites.subs/owners]
 :<- [::types-by-type-code]
 :<- [:lipas.ui.sports-sites.subs/materials]
 :<- [::filtering-methods]
 :<- [::heat-sources]
 :<- [::pool-types]
 :<- [::sauna-types]
 (fn [[site cities admins owners types materials filtering-methods
       heat-sources pool-types sauna-types] [_ locale]]
   (when site
     (let [latest               (or (utils/latest-edit (:edits site))
                                    (get-in site [:history (:latest site)]))
           type                 (types (-> latest :type :type-code))
           admin                (admins (-> latest :admin))
           owner                (owners (-> latest :owner))
           city                 (get cities (-> latest :location :city :city-code))
           energy-history       (utils/energy-consumption-history site)
           visitors-history     (utils/visitors-history site)
           get-material         #(get-in materials [% locale])
           get-filtering-method #(get-in filtering-methods [% locale])
           get-heat-source      #(get-in heat-sources [% locale])
           get-pool-type        #(get-in pool-types [% locale])
           get-sauna-type       #(get-in sauna-types [% locale])]

       {:lipas-id       (-> latest :lipas-id)
        :name           (-> latest :name)
        :marketing-name (-> latest :marketing-name)
        :type
        {:name      (-> type :name locale)
         :type-code (-> latest :type :type-code)}
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

        :building
        (-> (:building latest)
            (update :main-construction-materials #(map get-material %))
            (update :ceiling-structures #(map get-material %))
            (update :supporting-structures #(map get-material %))
            (update :heat-source get-heat-source))

        :renovations (:renovations latest)
        :conditions  (:conditions latest)

        :water-treatment
        (-> (:water-treatment latest)
            (update :filtering-methods #(map get-filtering-method %)))

        :pools
        (->> (:pools latest)
             (map #(update % :type get-pool-type))
             (map #(update % :structure get-material)))

        :slides
        (->> (:slides latest)
             (map #(update % :structure get-material)))

        :saunas
        (->> (:saunas latest)
             (map #(update % :type get-sauna-type)))

        :facilities         (:facilities latest)
        :visitors           (:visitors latest)
        :visitors-history   (sort-by :year utils/reverse-cmp visitors-history)
        :energy-consumption (sort-by :year utils/reverse-cmp energy-history)}))))
