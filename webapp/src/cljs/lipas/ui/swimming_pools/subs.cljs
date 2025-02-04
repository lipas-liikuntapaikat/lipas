(ns lipas.ui.swimming-pools.subs
  (:require [lipas.ui.utils :as utils]
            [re-frame.core :as rf]))

(rf/reg-sub ::active-tab
  (fn [db _]
    (-> db :swimming-pools :active-tab)))

(rf/reg-sub ::latest-swimming-pool-revs
  :<- [:lipas.ui.sports-sites.subs/latest-sports-site-revs]
  (fn [sites _]
    (->> sites
         (into {} (filter (comp #{3110 3120 3130} :type-code :type second)))
         not-empty)))

(rf/reg-sub ::total-counts
  :<- [::latest-swimming-pool-revs]
  (fn [sites _]
    (->> sites
         vals
         (group-by (comp :type-code :type))
         (reduce-kv (fn [m k v] (assoc m k (count v))) {}))))

(rf/reg-sub ::stats-year
  (fn [db _]
    (-> db :swimming-pools :stats-year)))

(rf/reg-sub ::stats
  (fn [[_ year] _]
    [(rf/subscribe [::total-counts])
     (rf/subscribe [:lipas.ui.energy.subs/energy-report year 3110])
     (rf/subscribe [:lipas.ui.energy.subs/energy-report year 3130])])
  (fn [[total-counts stats-3110 stats-3130] _]
    (let [total-count (+ (get total-counts 3110)
                         (get total-counts 3130))
          hof         (sort-by :name (concat
                                       (:hall-of-fame stats-3110)
                                       (:hall-of-fame stats-3130)))]
      {:counts
       {:sites           total-count
        :reported        (count hof)
        :not-reported    (- total-count (count hof))
        :energy-mwh      (+ (->> stats-3110
                                 :data-points
                                 (filter (comp some? :energy-mwh))
                                 count)
                            (->> stats-3130
                                 :data-points
                                 (filter (comp some? :energy-mwh))
                                 count))
        :electricity-mwh (+ (-> stats-3110 :electricity-mwh :count)
                            (-> stats-3130 :electricity-mwh :count))
        :heat-mwh        (+ (-> stats-3110 :heat-mwh :count)
                            (-> stats-3130 :heat-mwh :count))
        :water-m3        (+ (-> stats-3110 :water-m3 :count)
                            (-> stats-3130 :water-m3 :count))}
       :data-points  (sort-by :name (concat
                                      (:data-points stats-3110)
                                      (:data-points stats-3130)))
       :hall-of-fame hof})))

(rf/reg-sub ::sites-to-edit
  :<- [:lipas.ui.user.subs/sports-sites]
  :<- [::latest-swimming-pool-revs]
  (fn [[_ locale] _]
    [(rf/subscribe [:lipas.ui.user.subs/sports-sites locale])
     (rf/subscribe [::latest-ice-stadium-revs])])
  (fn [[users-sites sites] _]
    (not-empty (select-keys sites (map :lipas-id users-sites)))))

(rf/reg-sub ::sites-to-edit-list
  (fn [[_ locale] _]
    (rf/subscribe [:lipas.ui.user.subs/sports-sites locale]))
  (fn [sites-list _]
    (->> sites-list
         (filter (comp #{3110 3130} :type-code))
         not-empty)))

(rf/reg-sub ::dialogs
  (fn [db _]
    (-> db :swimming-pools :dialogs)))

(rf/reg-sub ::pool-form
  (fn [db _]
    (-> db :swimming-pools :dialogs :pool :data)))

(rf/reg-sub ::sauna-form
  (fn [db _]
    (-> db :swimming-pools :dialogs :sauna :data)))

(rf/reg-sub ::slide-form
  (fn [db _]
    (-> db :swimming-pools :dialogs :slide :data)))

(rf/reg-sub ::types-by-type-code
  :<- [:lipas.ui.sports-sites.subs/active-types]
  (fn [types _]
    (select-keys types [3110 3120 3130])))

(rf/reg-sub ::types-list
  :<- [::types-by-type-code]
  (fn [types _ _]
    (vals types)))

(rf/reg-sub ::accessibility
  (fn [db _]
    (-> db :swimming-pools :accessibility)))

(rf/reg-sub ::pool-types
  (fn [db _]
    (-> db :swimming-pools :pool-types)))

(rf/reg-sub ::pool-structures
  (fn [db _]
    (-> db :swimming-pools :pool-structures)))

(rf/reg-sub ::heat-sources
  (fn [db _]
    (-> db :swimming-pools :heat-sources)))

(rf/reg-sub ::filtering-methods
  (fn [db _]
    (-> db :swimming-pools :filtering-methods)))

(rf/reg-sub ::sauna-types
  (fn [db _]
    (-> db :swimming-pools :sauna-types)))

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

(rf/reg-sub ::sites-list
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
      (map (partial ->list-entry data) (vals pools)))))

(rf/reg-sub ::display-site-raw
  (fn [db _]
    (when-let [lipas-id (-> db :swimming-pools :displaying)]
      (get-in db [:sports-sites lipas-id]))))

(rf/reg-sub ::display-site
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
  :<- [::accessibility]
  (fn [[site cities admins owners types materials filtering-methods
        heat-sources pool-types sauna-types accessibility] [_ locale]]
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
            get-sauna-type       #(get-in sauna-types [% locale])
            get-accessibility    #(get-in accessibility [% locale])]

        {:lipas-id       (-> latest :lipas-id)
         :name           (-> latest :name)
         :name-localized (-> latest :name-localized)
         :marketing-name (-> latest :marketing-name)
         :type
         {:name      (-> type :name locale)
          :type-code (-> latest :type :type-code)}
         :owner          (-> owner locale)
         :admin          (-> admin locale)
         :phone-number   (-> latest :phone-number)
         :www            (-> latest :www)
         :email          (-> latest :email)
         :comment        (-> latest :comment)

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
             (update :heat-sources #(map get-heat-source %)))

         :renovations (:renovations latest)
         :conditions  (:conditions latest)

         :water-treatment
         (-> (:water-treatment latest)
             (update :filtering-methods #(map get-filtering-method %)))

         :pools
         (->> (:pools latest)
              (map #(update % :type get-pool-type))
              (map #(update % :structure get-material))
              (map #(update % :accessibility (fn [coll] (map get-accessibility coll)))))

         :slides (:slides latest)

         :saunas
         (->> (:saunas latest)
              (map #(update % :type get-sauna-type)))

         :facilities         (:facilities latest)
         :visitors           (:visitors latest)
         :visitors-history   (sort-by :year visitors-history)
         :energy-consumption (sort-by :year energy-history)
         :properties         (:properties latest)}))))

(rf/reg-sub ::sites-filter
  (fn [db _]
    (-> db :swimming-pools :sites-filter)))
