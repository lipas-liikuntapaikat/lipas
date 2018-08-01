(ns lipas.ui.ice-stadiums.subs
  (:require [lipas.ui.utils :as utils]
            [clojure.string :as string]
            [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::active-tab
 (fn [db _]
   (-> db :ice-stadiums :active-tab)))

(re-frame/reg-sub
 ::latest-ice-stadium-revs
 :<- [:lipas.ui.sports-sites.subs/latest-sports-site-revs]
 (fn [sites _]
   (as-> sites $
     (into {} (filter (comp #{2510 2520} :type-code :type second)) $)
     (not-empty $))))

(re-frame/reg-sub
 ::sites-to-edit
 :<- [:lipas.ui.user.subs/access-to-sports-sites]
 :<- [:lipas.ui.user.subs/admin?]
 :<- [::latest-ice-stadium-revs]
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
 :<- [::latest-ice-stadium-revs]
 (fn [sites _]
   (not-empty (sort-by :name (vals sites)))))

(re-frame/reg-sub
 ::dialogs
 (fn [db _]
   (-> db :ice-stadiums :dialogs)))

(re-frame/reg-sub
 ::rink-form
 (fn [db _]
   (-> db :ice-stadiums :dialogs :rink :data)))

(re-frame/reg-sub
 ::types-by-type-code
 :<- [:lipas.ui.sports-sites.subs/all-types]
 (fn [types _]
   (select-keys types [2510 2520])))

(re-frame/reg-sub
 ::types-list
 :<- [::types-by-type-code]
 (fn [types _ _]
   (vals types)))

(re-frame/reg-sub
 ::size-categories
 (fn [db _ _]
   (-> db :ice-stadiums :size-categories)))

(re-frame/reg-sub
 ::heat-recovery-types
 (fn [db _ _]
   (-> db :ice-stadiums :heat-recovery-types)))

(re-frame/reg-sub
 ::dryer-types
 (fn [db _]
   (-> db :ice-stadiums :dryer-types)))

(re-frame/reg-sub
 ::dryer-duty-types
 (fn [db _]
   (-> db :ice-stadiums :dryer-duty-types)))

(re-frame/reg-sub
 ::heat-pump-types
 (fn [db _]
   (-> db :ice-stadiums :heat-pump-types)))

(re-frame/reg-sub
 ::condensate-energy-targets
 (fn [db _]
   (-> db :ice-stadiums :condensate-energy-targets)))

(re-frame/reg-sub
 ::refrigerants
 (fn [db _]
   (-> db :ice-stadiums :refrigerants)))

(re-frame/reg-sub
 ::refrigerant-solutions
 (fn [db _]
   (-> db :ice-stadiums :refrigerant-solutions)))

(re-frame/reg-sub
 ::base-floor-structures
 (fn [db _]
   (-> db :base-floor-structures)))

(defn- truncate [s]
  (when (string? s)
    (-> s
        (string/split #"(<|>)")
        first
        (string/trim))))

(defn ->list-entry [{:keys [cities admins owners types locale size-categories]}
                    ice-stadium]
  (let [type          (types (-> ice-stadium :type :type-code))
        size-category (-> ice-stadium :type :size-category size-categories locale)
        admin         (admins (-> ice-stadium :admin))
        owner         (owners (-> ice-stadium :owner))
        city          (get cities (-> ice-stadium :location :city :city-code))]
    {:lipas-id          (-> ice-stadium :lipas-id)
     :name              (-> ice-stadium :name)
     :type              (or (truncate size-category)
                            (-> type :name locale))
     :address           (-> ice-stadium :location :address)
     :postal-code       (-> ice-stadium :location :postal-code)
     :city              (-> city :name locale)
     :construction-year (-> ice-stadium :construction-year)
     :renovation-years  (-> ice-stadium :renovation-years)
     :owner             (-> owner locale)
     :admin             (-> admin locale)}))

(re-frame/reg-sub
 ::sites-list
 :<- [::latest-ice-stadium-revs]
 :<- [:lipas.ui.sports-sites.subs/cities-by-city-code]
 :<- [:lipas.ui.sports-sites.subs/admins]
 :<- [:lipas.ui.sports-sites.subs/owners]
 :<- [::types-by-type-code]
 :<- [::size-categories]
 (fn [[sites cities admins owners types size-categories] [_ locale]]
   (let [data {:locale          locale
               :cities          cities
               :admins          admins
               :owners          owners
               :types           types
               :size-categories size-categories}]
     (sort-by :city (map (partial ->list-entry data) (vals sites))))))

(re-frame/reg-sub
 ::display-site-raw
 (fn [db _]
   (let [lipas-id (-> db :ice-stadiums :display-site)]
     (get-in db [:sports-sites lipas-id]))))

(re-frame/reg-sub
 ::display-site
 :<- [::display-site-raw]
 :<- [:lipas.ui.sports-sites.subs/cities-by-city-code]
 :<- [:lipas.ui.sports-sites.subs/admins]
 :<- [:lipas.ui.sports-sites.subs/owners]
 :<- [:lipas.ui.sports-sites.subs/all-types]
 :<- [::size-categories]
 :<- [::condensate-energy-targets]
 :<- [::refrigerants]
 :<- [::refrigerant-solutions]
 :<- [::heat-recovery-types]
 :<- [::dryer-types]
 :<- [::dryer-duty-types]
 :<- [::heat-pump-types]
 :<- [:lipas.ui.sports-sites.subs/materials]
 (fn [[site
       cities admins owners types size-categories
       condensate-energy-targets refrigerants refrigerant-solutions
       heat-recovery-types dryer-types dryer-duty-types heat-pump-types
       materials] [_ locale]]
   (when site
     (let [latest               (or (utils/latest-edit (:edits site))
                                    (get-in site [:history (:latest site)]))
           type                 (types (-> latest :type :type-code))
           size-category        (size-categories (-> latest :type :size-category))
           admin                (admins (-> latest :admin))
           owner                (owners (-> latest :owner))
           city                 (get cities (-> latest :location :city :city-code))
           energy-history       (utils/energy-consumption-history site)
           heat-pump-type       (heat-pump-types (-> latest
                                                     :ventilation
                                                     :heat-pump-type))
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

        :rinks              (:rinks latest)
        :renovations        (:renovations latest)
        :conditions         (:conditions latest)
        :energy-consumption (sort-by :year utils/reverse-cmp energy-history)}))))
