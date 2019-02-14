(ns lipas.ui.search.subs
  (:require
   [lipas.permissions :as permissions]
   [lipas.ui.components :as lui]
   [lipas.ui.utils :as utils]
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::filters
 (fn [db _]
   (-> db :search :filters)))

(defn filter-enabled? [x]
  (cond
    (seqable? x) (not-empty x)
    (false? x)   nil
    :else        x))

(re-frame/reg-sub
 ::filters-active?
 :<- [::filters]
 :<- [::search-string]
 (fn [[filters search-str] _]
   (or (not-empty search-str)
       (some (comp some? filter-enabled? second) filters))))

(re-frame/reg-sub
 ::types-filter
 (fn [db _]
   (-> db :search :filters :type-codes set)))

(re-frame/reg-sub
 ::cities-filter
 (fn [db _]
   (-> db :search :filters :city-codes set)))

(re-frame/reg-sub
 ::admins-filter
 (fn [db _]
   (-> db :search :filters :admins set)))

(re-frame/reg-sub
 ::owners-filter
 (fn [db _]
   (-> db :search :filters :owners set)))

(re-frame/reg-sub
 ::area-min-filter
 (fn [db _]
   (-> db :search :filters :area-min)))

(re-frame/reg-sub
 ::area-max-filter
 (fn [db _]
   (-> db :search :filters :area-max)))

(re-frame/reg-sub
 ::surface-materials-filter
 (fn [db _]
   (-> db :search :filters :surface-materials)))

(re-frame/reg-sub
 ::retkikartta-filter
 (fn [db _]
   (-> db :search :filters :retkikartta?)))

(re-frame/reg-sub
 ::search-string
 (fn [db _]
   (-> db :search :string)))

(re-frame/reg-sub
 ::search-results
 (fn [db _]
   (-> db :search :results)))

(re-frame/reg-sub
 ::search-results-total-count
 :<- [::search-results]
 (fn [results _]
   (-> results :hits :total)))

(defn ->search-result
  [{:keys [locale types cities admins owners logged-in? permissions]} hit]
  (let [site      (:_source hit)
        type-code (-> site :type :type-code)
        city-code (-> site :location :city :city-code)]
    {:lipas-id                (-> site :lipas-id)
     :score                   (-> hit :_score)
     :name                    (-> site :name)
     :event-date              (-> site :event-date utils/->short-date)
     :admin                   (-> site :admin)
     :owner                   (-> site :owner)
     :www                     (-> site :www)
     :email                   (-> site :email)
     :phone-number            (-> site :phone-number)
     :admin.name              (-> site :admin admins locale)
     :owner.name              (-> site :owner owners locale)
     :type.type-code          type-code
     :type.name               (get-in types [type-code :name locale])
     :location.address        (-> site :location :address)
     :location.postal-code    (-> site :location :postal-code)
     :location.postal-office  (-> site :location :postal-office)
     :location.city.city-code city-code
     :location.city.name      (get-in cities [city-code :name locale])
     :permission?             (if logged-in?
                                (permissions/publish? permissions site)
                                false)}))

(re-frame/reg-sub
 ::search-results-list
 :<- [::search-results]
 :<- [:lipas.ui.subs/translator]
 :<- [:lipas.ui.sports-sites.subs/all-types]
 :<- [:lipas.ui.sports-sites.subs/cities-by-city-code]
 :<- [:lipas.ui.sports-sites.subs/admins]
 :<- [:lipas.ui.sports-sites.subs/owners]
 :<- [:lipas.ui.user.subs/logged-in?]
 :<- [:lipas.ui.user.subs/permissions]
 (fn [[results tr types cities admins owners logged-in? permissions] _]
   (let [locale (tr)
         data   {:types       types  :cities cities :locale     locale
                 :admins      admins :owners owners :logged-in? logged-in?
                 :permissions permissions}]
     (->> (-> results :hits :hits)
          (map (partial ->search-result data))
          (sort-by :score utils/reverse-cmp)))))

(re-frame/reg-sub
 ::search-results-view
 (fn [db _]
   (-> db :search :results-view)))

(re-frame/reg-sub
 ::results-table-columns
 :<- [:lipas.ui.subs/translator]
 (fn [tr _]
   [[:name                   {:label (tr :lipas.sports-site/name)}]
    [:type.name              {:label (tr :type/name)}]
    [:admin.name             {:label (tr :lipas.sports-site/admin)}]
    [:owner.name             {:label (tr :lipas.sports-site/owner)}]
    [:location.city.name     {:label (tr :lipas.location/city)}]
    [:location.address       {:label (tr :lipas.location/address)}]
    [:location.postal-code   {:label (tr :lipas.location/postal-code)}]
    [:location.postal-office {:label (tr :lipas.location/postal-office)}]
    [:www                    {:label (tr :lipas.sports-site/www)}]
    [:email                  {:label (tr :lipas.sports-site/email-public)}]
    [:phone-number           {:label (tr :lipas.sports-site/phone-number)}]
    [:event-date             {:label (tr :lipas.sports-site/event-date)}]]))

(re-frame/reg-sub
 ::selected-results-table-columns
 (fn [db _]
   (-> db :search :selected-results-table-columns set)))

(re-frame/reg-sub
 ::results-table-specs
 (fn [_]
   {:email                  {:spec :lipas.sports-site/email}
    :phone-number           {:spec :lipas.sports-site/phone-number}
    :www                    {:spec :lipas.sports-site/www}
    :name                   {:spec :lipas.sports-site/name :required? true}
    :location.postal-office {:spec :lipas.location/postal-office}
    :location.postal-code   {:spec :lipas.location/postal-code :required? true}
    :marketing-name         {:spec :lipas.sports-site/marketing-name}
    :location.address       {:spec :lipas.location/address :required? true}}))

(re-frame/reg-sub
 ::results-table-headers
 :<- [:lipas.ui.subs/translator]
 :<- [::selected-results-table-columns]
 :<- [::results-table-specs]
 (fn [[tr selected-cols specs] _]
   (->>
    [[:score                  {:label "score"}]
     [:name                   {:label (tr :lipas.sports-site/name)
                               :form  {:component lui/text-field}}]
     [:marketing-name         {:label (tr :lipas.sports-site/marketing-name)
                               :form  {:component lui/text-field}}]
     [:type.name              {:label (tr :type/name)
                               :form
                               {:component lui/type-selector-single
                                :value-key :type.type-code}}]
     [:admin.name             {:label (tr :lipas.sports-site/admin)
                               :form
                               {:component lui/admin-selector-single
                                :value-key :admin}}]
     [:owner.name             {:label (tr :lipas.sports-site/owner)
                               :form
                               {:component lui/owner-selector-single
                                :value-key :owner}}]
     [:location.city.name     {:label (tr :lipas.location/city)
                               :form
                               {:component lui/city-selector-single
                                :value-key :location.city.city-code}}]
     [:location.address       {:label (tr :lipas.location/address)
                               :form  {:component lui/text-field}}]
     [:location.postal-code   {:label (tr :lipas.location/postal-code)
                               :form  {:component lui/text-field}}]
     [:location.postal-office {:label (tr :lipas.location/postal-office)
                               :form  {:component lui/text-field}}]
     [:www                    {:label (tr :lipas.sports-site/www)
                               :form  {:component lui/text-field}}]
     [:email                  {:label (tr :lipas.sports-site/email-public)
                               :form  {:component lui/text-field}}]
     [:phone-number           {:label (tr :lipas.sports-site/phone-number)
                               :form  {:component lui/text-field}}]
     [:event-date             {:label (tr :lipas.sports-site/event-date)}]]
    (reduce
     (fn [res [k v]]
       (conj res [k (-> v
                        (assoc :hidden? (not (some? (selected-cols k))))
                        (assoc-in [:form :props :spec] (-> k specs :spec))
                        (assoc-in [:form :props :required] (-> k specs :required?)))]))
     []))))

(re-frame/reg-sub
 ::sort-opts
 (fn [db _]
   (-> db :search :sort)))

(re-frame/reg-sub
 ::pagination
 (fn [db _]
   (-> db :search :pagination)))

(re-frame/reg-sub
 ::in-progress?
 (fn [db _]
   (-> db :search :in-progress?)))
