(ns lipas.ui.swimming-pools.subs
  (:require [re-frame.core :as re-frame]
            [lipas.ui.utils :as utils]
            [lipas.ui.swimming-pools.utils :as swim-utils]
            [clojure.spec.alpha :as s]))

(re-frame/reg-sub
 ::active-tab
 (fn [db _]
   (-> db :swimming-pools :active-tab)))

(re-frame/reg-sub
 ::latest-swimming-pool-revs
 :<- [:lipas.ui.sports-sites.subs/latest-sports-site-revs]
 (fn [sites _]
   (as-> sites $
     (into {} (filter (comp #{3110} :type-code :type second)) $)
     (not-empty $))))

(re-frame/reg-sub
 ::sites-to-edit
 :<- [:lipas.ui.user.subs/access-to-sports-sites]
 :<- [::latest-swimming-pool-revs]
 (fn [[ids pools] _]
   (not-empty (select-keys pools ids))))

(re-frame/reg-sub
 ::sites-to-edit-list
 :<- [::sites-to-edit]
 (fn [sites _]
   (not-empty (vals sites))))

(re-frame/reg-sub
 ::editing?
 (fn [db _]
   (-> db :swimming-pools :editing?)))

(re-frame/reg-sub
 ::editing-site
 (fn [db _]
   (let [lipas-id (-> db :swimming-pools :editing :lipas-id)]
     (get-in db [:sports-sites lipas-id]))))

(re-frame/reg-sub
 ::editing-rev
 (fn [db _]
   (let [lipas-id (-> db :swimming-pools :editing :lipas-id)]
     (get-in db [:sports-sites lipas-id :editing]))))

(re-frame/reg-sub
 ::edits-valid?
 :<- [::editing-rev]
 (fn [rev _]
   (let [rev (swim-utils/make-saveable rev)]
     ;; (s/explain :lipas.sports-site/swimming-pool rev)
     (s/valid? :lipas.sports-site/swimming-pool rev))))

(re-frame/reg-sub
 ::editing-year
 (fn [db _]
   (-> db :swimming-pools :editing :year)))

(re-frame/reg-sub
 ::energy-consumption-history
 (fn [db _]
   (let [lipas-id (-> db :swimming-pools :editing :lipas-id)
         site (get-in db [:sports-sites lipas-id])
         history (utils/energy-consumption-history site)]
     (->> history (sort-by :year utils/reverse-cmp)))))

(re-frame/reg-sub
 ::energy-consumption-years-list
 :<- [::energy-consumption-history]
 (fn [history _]
   (utils/make-energy-consumption-year-list history)))

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
 ::types-by-type-code
 :<- [:lipas.ui.sports-sites.subs/all-types]
 (fn [types _]
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

(defn ->list-entry [{:keys [cities admins owners types locale]} pool]
  (let [type   (types (-> pool :type :type-code))
        admin  (admins (-> pool :admin))
        owner  (owners (-> pool :owner))
        city   (get cities (-> pool :location :city :city-code))]
    {:lipas-id    (-> pool :lipas-id)
     :name        (-> pool :name)
     :type        (-> type :name locale)
     :address     (-> pool :location :address)
     :postal-code (-> pool :location :postal-code)
     :city        (-> city :name locale)
     :owner       (-> owner locale)
     :admin       (-> admin locale)}))

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
     (map (partial ->list-entry data) (vals pools)))))

(re-frame/reg-sub
 ::display-site-raw
 (fn [db _]
   (let [lipas-id (-> db :swimming-pools :display-site)]
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
        :energy-consumption (sort-by :year utils/reverse-cmp energy-history)}))))
