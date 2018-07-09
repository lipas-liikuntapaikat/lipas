(ns lipas.ui.ice-stadiums.subs
  (:require [lipas.ui.utils :as utils]
            [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::active-tab
 (fn [db _]
   (-> db :ice-stadiums :active-tab)))

(re-frame/reg-sub
 ::editing-site
 (fn [db _]
   (let [lipas-id (-> db :ice-stadiums :editing :site)]
     (get-in db [:sports-sites lipas-id]))))

(re-frame/reg-sub
 ::editing-rev
 (fn [db _]
   (-> db :ice-stadiums :editing :rev)))

(re-frame/reg-sub
 ::editing-year
 (fn [db _]
   (-> db :ice-stadiums :editing :year)))

(re-frame/reg-sub
 ::uncommitted-edits?
 (fn [db [_ lipas-id]]
   ((complement empty?) (get-in db [:sports-sites lipas-id :edits]))))

(re-frame/reg-sub
 ::energy-consumption-data
 :<- [::editing-site]
 :<- [::editing-year]
 (fn [[{:keys [history]} year] _]
   (let [by-year (utils/latest-by-year history)
         rev     (get by-year year)]
     (get history rev))))

(re-frame/reg-sub
 ::energy-consumption-history
 (fn [db _]
   (let [lipas-id (-> db :ice-stadiums :editing :site)
         site (get-in db [:sports-sites lipas-id])
         history (utils/energy-consumption-history site)]
     (->> history (sort-by :year utils/reverse-cmp)))))

(re-frame/reg-sub
 ::energy-consumption-years-list
 :<- [::energy-consumption-history]
 (fn [history _]
   (utils/make-year-list (map :year history))))

(re-frame/reg-sub
 ::access-to-sports-sites
 (fn [db _]
   (-> db :user :login :permissions :sports-sites)))

(re-frame/reg-sub
 ::permission-to-publish?
 :<- [::access-to-sports-sites]
 (fn [ids [_ lipas-id]]
   (boolean (some #{lipas-id} ids))))

(re-frame/reg-sub
 ::sports-sites
 (fn [db _]
   (-> db :sports-sites)))

(re-frame/reg-sub
 ::ice-stadiums
 :<- [::sports-sites]
 (fn [sites _]
   (as-> sites $
     (into {} (filter (comp #{2510 2520} :type-code :type :latest second)) $)
     (not-empty $))))

(re-frame/reg-sub
 ::sites-to-edit
 :<- [::access-to-sports-sites]
 :<- [::ice-stadiums]
 (fn [[ids sites] _]
   (not-empty (select-keys sites ids))))

(re-frame/reg-sub
 ::sites-to-edit-list
 :<- [::sites-to-edit]
 (fn [sites _]
   (not-empty (map :latest (vals sites)))))

(re-frame/reg-sub
 ::dialogs
 (fn [db _]
   (-> db :ice-stadiums :dialogs)))

(re-frame/reg-sub
 ::renovation-form
 (fn [db _]
   (-> db :ice-stadiums :dialogs :renovation :data)))

(re-frame/reg-sub
 ::rink-form
 (fn [db _]
   (-> db :ice-stadiums :dialogs :rink :data)))

(re-frame/reg-sub
 ::energy-form
 (fn [db _]
   (-> db :ice-stadiums :dialogs :energy :data)))

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
 ::types-by-type-code
 :<- [::all-types]
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
 ::materials
 (fn [db _]
   (-> db :materials)))

(re-frame/reg-sub
 ::base-floor-structures
 (fn [db _]
   (-> db :base-floor-structures)))

(defn ->list-entry [{:keys [cities admins owners types locale]} site]
  (let [latest (:latest site)
        type   (types (-> latest :type :type-code))
        admin  (admins (-> latest :admin))
        owner  (owners (-> latest :owner))
        city   (get cities (-> latest :location :city :city-code))]
    {:lipas-id    (-> latest :lipas-id)
     :name        (-> latest :name locale)
     :type        (-> type :name locale)
     :address     (-> latest :location :address)
     :postal-code (-> latest :location :postal-code)
     :city        (-> city :name locale)
     :owner       (-> owner locale)
     :admin       (-> admin locale)}))

(re-frame/reg-sub
 ::sites-list
 :<- [::ice-stadiums]
 :<- [::cities-by-city-code]
 :<- [::admins]
 :<- [::owners]
 :<- [::types-by-type-code]
 (fn [[sites cities admins owners types] [_ locale]]
   (let [data  {:locale locale
                :cities cities
                :admins admins
                :owners owners
                :types types}]
     (map (partial ->list-entry data) (vals sites)))))

(re-frame/reg-sub
 ::display-site-raw
 (fn [db _]
   (let [lipas-id (-> db :ice-stadiums :display-site)]
     (get-in db [:sports-sites lipas-id]))))

(re-frame/reg-sub
 ::display-site
 :<- [::display-site-raw]
 :<- [::cities-by-city-code]
 :<- [::admins]
 :<- [::owners]
 :<- [::all-types]
 :<- [::size-categories]
 :<- [::condensate-energy-targets]
 :<- [::refrigerants]
 :<- [::refrigerant-solutions]
 :<- [::heat-recovery-types]
 :<- [::dryer-types]
 :<- [::dryer-duty-types]
 :<- [::heat-pump-types]
 :<- [::materials]
 (fn [[site
       cities admins owners types size-categories
       condensate-energy-targets refrigerants refrigerant-solutions
       heat-recovery-types dryer-types dryer-duty-types heat-pump-types
       materials] [_ locale]]
   (when site
     (let [latest               (or (utils/latest-edit (:edits site))
                                    (:latest site))
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

       {:lipas-id     (-> latest :lipas-id)
        :name         (-> latest :name locale)
        :type
        {:name          (-> type :name locale)
         :type-code     (-> latest :type :type-code)
         :size-category (-> size-category locale)}
        :owner        (-> owner locale)
        :admin        (-> admin locale)
        :phone-number (-> latest :phone-number)
        :www          (-> latest :www)
        :email        (-> latest :email)

        :location
        {:address       (-> latest :location :address)
         :postal-code   (-> latest :location :postal-code)
         :postal-office (-> latest :location :postal-office)
         :city
         {:name (-> city :name locale)}}

        :building (:building latest)

        :envelope-structure
        (-> (:envelope-structure latest)
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
