(ns lipas.ui.ice-stadiums.subs
  (:require [re-frame.core :as re-frame]
            [lipas.ui.utils :refer [resolve-year index-by]]))

(re-frame/reg-sub
 ::active-tab
 (fn [db _]
   (-> db :ice-stadiums :active-tab)))

(re-frame/reg-sub
 ::editing-site
 (fn [db _]
   (-> db :ice-stadiums :editing :site)))

(re-frame/reg-sub
 ::editing-rev
 (fn [db _]
   (-> db :ice-stadiums :editing :rev)))

(re-frame/reg-sub
 ::energy-consumption-history
 (fn [db _]
   (let [history (-> db :ice-stadiums :editing :site :history)]
     (map #(assoc (:energy-consumption %)
                  :year (resolve-year (:timestamp %))) history))))

(re-frame/reg-sub
 ::access-to-sports-sites
 (fn [db _]
   (-> db :user :login :permissions :sports-sites)))

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
   (select-keys sites ids)))

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
   (-> db :ice-stadiums :display-site)))

(re-frame/reg-sub
 ::display-site
 :<- [::display-site-raw]
 :<- [::cities-by-city-code]
 :<- [::admins]
 :<- [::owners]
 :<- [::all-types]
 :<- [::materials]
 (fn [[site cities admins owners types materials] [_ locale]]
   (when site
     (let [latest       (:latest site)
           type         (types (-> latest :type :type-code))
           admin        (admins (-> latest :admin))
           owner        (owners (-> latest :owner))
           city         (get cities (-> latest :location :city :city-code))
           get-material #(get-in materials [% locale])]


       {:lipas-id     (-> latest :lipas-id)
        :name         (-> latest :name locale)
        :type
        {:name      (-> type :name locale)
         :type-code (-> latest :type :type-code)}
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

        :rinks              (:rinks latest)
        :refrigeration      (:refrigeration latest)
        :ice-maintenance    (:ice-maintenance latest)
        :renovations        (:renovations latest)
        :ventilation        (:ventilation latest)
        :energy-consumption (:energy-consumption latest)}))))
