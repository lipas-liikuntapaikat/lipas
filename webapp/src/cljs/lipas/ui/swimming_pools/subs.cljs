(ns lipas.ui.swimming-pools.subs
  (:require [re-frame.core :as re-frame]
            [lipas.ui.utils :refer [resolve-year index-by]]))

(re-frame/reg-sub
 ::active-tab
 (fn [db _]
   (-> db :swimming-pools :active-tab)))

(re-frame/reg-sub
 ::access-to-sports-sites
 (fn [db _]
   (-> db :user :login :permissions :sports-sites)))

(re-frame/reg-sub
 ::sports-sites
 (fn [db _]
   (-> db :sports-sites)))

(re-frame/reg-sub
 ::swimming-pools
 :<- [::sports-sites]
 (fn [sites _]
   (as-> sites $
     (into {} (filter (comp #{3110} :type-code :type :latest second)) $)
     (not-empty $))))

(re-frame/reg-sub
 ::sites-to-edit
 :<- [::access-to-sports-sites]
 :<- [::swimming-pools]
 (fn [[ids pools] _]
   (select-keys pools ids)))

(re-frame/reg-sub
 ::editing-site
 (fn [db _]
   (-> db :swimming-pools :editing :site)))

(re-frame/reg-sub
 ::editing-rev
 (fn [db _]
   (-> db :swimming-pools :editing :rev)))

(defn energy-consumption-history [site]
  (let [entries (map #(assoc (:energy-consumption %)
                             :year (resolve-year (:timestamp %))) (:history site))]
    (->> entries (sort-by :year) reverse)))

(re-frame/reg-sub
 ::energy-consumption-history
 (fn [db _]
   (let [site (-> db :swimming-pools :editing :site)]
     (energy-consumption-history site))))

(re-frame/reg-sub
 ::dialogs
 (fn [db _]
   (-> db :swimming-pools :dialogs)))

(re-frame/reg-sub
 ::renovation-form
 (fn [db _]
   (-> db :swimming-pools :dialogs :renovation :data)))

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
 ::energy-form
 (fn [db _]
   (-> db :swimming-pools :dialogs :energy :data)))

(re-frame/reg-sub
 ::cities-by-city-code
 (fn [db _]
   (-> db :cities)))

(re-frame/reg-sub
 ::cities-list
 :<- [::cities-by-city-code]
 (fn [cities _]
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
 (fn [types _ _]
   (select-keys types [3110])))

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

(defn ->list-entry [{:keys [cities admins owners types locale]} pool]
  (let [latest (:latest pool)
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
 ::swimming-pools-list
 :<- [::swimming-pools]
 :<- [::cities-by-city-code]
 :<- [::admins]
 :<- [::owners]
 :<- [::types-by-type-code]
 (fn [[pools cities admins owners types] [_ locale]]
   (let [data  {:locale locale
                :cities cities
                :admins admins
                :owners owners
                :types types} ]
     (map (partial ->list-entry data) (vals pools)))))

(re-frame/reg-sub
 ::display-site-raw
 (fn [db _]
   (-> db :swimming-pools :display-site)))

(re-frame/reg-sub
 ::display-site
 :<- [::display-site-raw]
 :<- [::cities-by-city-code]
 :<- [::admins]
 :<- [::owners]
 :<- [::types-by-type-code]
 :<- [::materials]
 :<- [::filtering-methods]
 :<- [::heat-sources]
 :<- [::pool-types]
 :<- [::sauna-types]
 (fn [[site cities admins owners types materials filtering-methods
       heat-sources pool-types sauna-types] [_ locale]]
   (when site
     (let [latest               (:latest site)
           type                 (types (-> latest :type :type-code))
           admin                (admins (-> latest :admin))
           owner                (owners (-> latest :owner))
           city                 (get cities (-> latest :location :city :city-code))
           energy-history       (energy-consumption-history site)
           get-material         #(get-in materials [% locale])
           get-filtering-method #(get-in filtering-methods [% locale])
           get-heat-source      #(get-in heat-sources [% locale])
           get-pool-type        #(get-in pool-types [% locale])
           get-sauna-type       #(get-in sauna-types [% locale])]

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

        :building
        (-> (:building latest)
            (update :main-construction-materials #(map get-material %))
            (update :ceiling-structures #(map get-material %))
            (update :supporting-structures #(map get-material %))
            (update :heat-source get-heat-source))

        :renovations (:renovations latest)

        :water-treatment
        (-> (:water-treatment latest)
            (update :filtering-method #(map get-filtering-method %)))

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

        :other-services     (:other-services latest)
        :facilities         (:facilities latest)
        :visitors           (:visitors latest)
        :energy-consumption energy-history}))))
