(ns lipas.ui.ice-stadiums.subs
  (:require [lipas.ui.utils :as utils]
            [re-frame.core :as rf]))

(rf/reg-sub ::active-tab
  (fn [db _]
    (-> db :ice-stadiums :active-tab)))

(rf/reg-sub ::latest-ice-stadium-revs
  :<- [:lipas.ui.sports-sites.subs/latest-sports-site-revs]
  (fn [sites _]
    (->> sites
         (into {} (filter (comp #{2510 2520} :type-code :type second)))
         not-empty)))

(rf/reg-sub ::total-counts
  :<- [::latest-ice-stadium-revs]
  (fn [sites _]
    (->> sites
         vals
         (group-by (comp :type-code :type))
         (reduce-kv (fn [m k v] (assoc m k (count v))) {}))))

(rf/reg-sub ::stats-year
  (fn [db _]
    (-> db :ice-stadiums :stats-year)))

(rf/reg-sub ::stats
  (fn [[_ year] _]
    [(rf/subscribe [::total-counts])
     (rf/subscribe [:lipas.ui.energy.subs/energy-report year 2510])
     (rf/subscribe [:lipas.ui.energy.subs/energy-report year 2520])])
  (fn [[total-counts stats-2510 stats-2520] _]
    (let [total-count (+ (get total-counts 2510)
                         (get total-counts 2520))
          hof         (sort-by :name (concat
                                       (:hall-of-fame stats-2510)
                                       (:hall-of-fame stats-2520)))]
      {:counts
       {:sites           total-count
        :not-reported    (- total-count (count hof))
        :reported        (count hof)
        :energy-mwh      (+ (->> stats-2510
                                 :data-points
                                 (filter (comp some? :energy-mwh))
                                 count)
                            (->> stats-2520
                                 :data-points
                                 (filter (comp some? :energy-mwh))
                                 count))
        :electricity-mwh (+ (-> stats-2510 :electricity-mwh :count)
                            (-> stats-2520 :electricity-mwh :count))
        :heat-mwh        (+ (-> stats-2510 :heat-mwh :count)
                            (-> stats-2520 :heat-mwh :count))
        :water-m3        (+ (-> stats-2510 :water-m3 :count)
                            (-> stats-2520 :water-m3 :count))}
       :data-points  (sort-by :name (concat
                                      (:data-points stats-2510)
                                      (:data-points stats-2520)))
       :hall-of-fame hof})))

(rf/reg-sub ::sites-to-edit
  :<- [:lipas.ui.user.subs/sports-sites]
  :<- [::latest-ice-stadium-revs]
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
         (filter (comp #{2510 2520} :type-code))
         not-empty)))

(rf/reg-sub ::dialogs
  (fn [db _]
    (-> db :ice-stadiums :dialogs)))

(rf/reg-sub ::rink-form
  (fn [db _]
    (-> db :ice-stadiums :dialogs :rink :data)))

(rf/reg-sub ::types-by-type-code
  :<- [:lipas.ui.sports-sites.subs/active-types]
  (fn [types _]
    (select-keys types [2510 2520])))

(rf/reg-sub ::types-list
  :<- [::types-by-type-code]
  (fn [types _ _]
    (vals types)))

(rf/reg-sub ::size-categories
  (fn [db _ _]
    (-> db :ice-stadiums :size-categories)))

(rf/reg-sub ::heat-recovery-types
  (fn [db _ _]
    (-> db :ice-stadiums :heat-recovery-types)))

(rf/reg-sub ::dryer-types
  (fn [db _]
    (-> db :ice-stadiums :dryer-types)))

(rf/reg-sub ::dryer-duty-types
  (fn [db _]
    (-> db :ice-stadiums :dryer-duty-types)))

(rf/reg-sub ::heat-pump-types
  (fn [db _]
    (-> db :ice-stadiums :heat-pump-types)))

(rf/reg-sub ::ice-resurfacer-fuels
  (fn [db _]
    (-> db :ice-stadiums :ice-resurfacer-fuels)))

(rf/reg-sub ::condensate-energy-targets
  (fn [db _]
    (-> db :ice-stadiums :condensate-energy-targets)))

(rf/reg-sub ::refrigerants
  (fn [db _]
    (-> db :ice-stadiums :refrigerants)))

(rf/reg-sub ::refrigerant-solutions
  (fn [db _]
    (-> db :ice-stadiums :refrigerant-solutions)))

(rf/reg-sub ::base-floor-structures
  (fn [db _]
    (-> db :base-floor-structures)))

(rf/reg-sub ::display-site-raw
  (fn [db _]
    (when-let [lipas-id (-> db :ice-stadiums :display-site)]
      (get-in db [:sports-sites lipas-id]))))

(rf/reg-sub ::display-site
  :<- [::display-site-raw]
  :<- [:lipas.ui.sports-sites.subs/cities-by-city-code]
  :<- [:lipas.ui.sports-sites.subs/admins]
  :<- [:lipas.ui.sports-sites.subs/owners]
  :<- [:lipas.ui.sports-sites.subs/active-types]
  :<- [::size-categories]
  :<- [::condensate-energy-targets]
  :<- [::refrigerants]
  :<- [::refrigerant-solutions]
  :<- [::heat-recovery-types]
  :<- [::dryer-types]
  :<- [::dryer-duty-types]
  :<- [::heat-pump-types]
  :<- [::ice-resurfacer-fuels]
  :<- [:lipas.ui.sports-sites.subs/materials]
  (fn [[site
        cities admins owners types size-categories
        condensate-energy-targets refrigerants refrigerant-solutions
        heat-recovery-types dryer-types dryer-duty-types heat-pump-types
        ice-resurfacer-fuels materials] [_ locale]]
    (when site
      (let [latest              (or (utils/latest-edit (:edits site))
                                    (get-in site [:history (:latest site)]))
            type                (types (-> latest :type :type-code))
            size-category       (size-categories (-> latest :type :size-category))
            admin               (admins (-> latest :admin))
            owner               (owners (-> latest :owner))
            city                (get cities (-> latest :location :city :city-code))
            visitors-history    (utils/visitors-history site)
            energy-history      (utils/energy-consumption-history site)
            heat-pump-type      (heat-pump-types (-> latest
                                                     :ventilation
                                                     :heat-pump-type))
            ice-resurfacer-fuel (ice-resurfacer-fuels (-> latest
                                                          :conditions
                                                          :ice-resurfacer-fuel))

            dryer-type           (dryer-types (-> latest
                                                  :ventilation
                                                  :dryer-type))
            dryer-duty-type      (dryer-duty-types (-> latest
                                                       :ventilation
                                                       :dryer-duty-type))
            refrigerant          (refrigerants (-> latest
                                                   :refrigeration
                                                   :refrigerant))
            refrigerant-solution (refrigerant-solutions (-> latest
                                                            :refrigeration
                                                            :refrigerant-solution))
            heat-recovery-type   (heat-recovery-types (-> latest
                                                          :ventilation
                                                          :heat-recovery-type))

            get-cet      #(get-in condensate-energy-targets [% locale])
            get-material #(get-in materials [% locale])]

        {:lipas-id       (-> latest :lipas-id)
         :name           (-> latest :name)
         :name-localized (-> latest :name-localized)
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

         :building (:building latest)

         :envelope
         (-> (:envelope latest)
             (update :base-floor-structure get-material))

         :refrigeration
         (-> (:refrigeration latest)
             (update :condensate-energy-main-targets #(map get-cet %))
             (assoc :refrigerant (-> refrigerant locale))
             (assoc :refrigerant-solution (-> refrigerant-solution locale)))

         :ventilation
         (-> (:ventilation latest)
             (assoc :heat-recovery-type (-> heat-recovery-type locale))
             (assoc :dryer-type (-> dryer-type locale))
             (assoc :dryer-duty-type (-> dryer-duty-type locale))
             (assoc :heat-pump-type (-> heat-pump-type locale)))

         :conditions
         (-> (:conditions latest)
             (assoc :ice-resurfacer-fuel (-> ice-resurfacer-fuel locale)))

         :rinks              (:rinks latest)
         :renovations        (:renovations latest)
         :visitors-history   (sort-by :year visitors-history)
         :energy-consumption (sort-by :year energy-history)
         :properties         (:properties latest)}))))

(rf/reg-sub ::sites-filter
  (fn [db _]
    (-> db :ice-stadiums :sites-filter)))
