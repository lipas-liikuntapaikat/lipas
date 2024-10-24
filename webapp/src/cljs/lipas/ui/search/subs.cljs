(ns lipas.ui.search.subs
  (:require [lipas.roles :as roles]
            [lipas.ui.components :as lui]
            [lipas.ui.search.db :as db]
            [lipas.ui.utils :as utils]
            [re-frame.core :as rf]))

(rf/reg-sub ::filters
  (fn [db _]
    (-> db :search :filters)))

(defn filter-enabled? [x]
  (cond
    (seqable? x) (not-empty x)
    (false? x)   nil
    :else        x))

(rf/reg-sub ::filters-active?
  :<- [::filters]
  :<- [::search-string]
  :<- [:lipas.ui.login.subs/logged-in?]
  (fn [[filters search-str logged-in?] _]
    (let [default-db (if logged-in? db/default-db-logged-in db/default-db)]
      (or (not-empty search-str)
          (some (comp some? filter-enabled? second) (dissoc filters :statuses))
          (not= (-> default-db :filters :statuses) (:statuses filters))))))

(rf/reg-sub ::statuses
  :<- [:lipas.ui.sports-sites.subs/statuses]
  (fn [statuses _]
    (dissoc statuses "incorrect-data")))

(rf/reg-sub ::statuses-filter
  (fn [db _]
    (-> db :search :filters :statuses set)))

(rf/reg-sub ::types-filter
  (fn [db _]
    (-> db :search :filters :type-codes set)))

(rf/reg-sub ::cities-filter
  (fn [db _]
    (-> db :search :filters :city-codes set)))

(rf/reg-sub ::admins-filter
  (fn [db _]
    (-> db :search :filters :admins set)))

(rf/reg-sub ::owners-filter
  (fn [db _]
    (-> db :search :filters :owners set)))

(rf/reg-sub ::area-min-filter
  (fn [db _]
    (-> db :search :filters :area-min)))

(rf/reg-sub ::area-max-filter
  (fn [db _]
    (-> db :search :filters :area-max)))

(rf/reg-sub ::construction-year-min-filter
  (fn [db _]
    (-> db :search :filters :construction-year-min)))

(rf/reg-sub ::construction-year-max-filter
  (fn [db _]
    (-> db :search :filters :construction-year-max)))

(rf/reg-sub ::surface-materials-filter
  (fn [db _]
    (-> db :search :filters :surface-materials)))

(rf/reg-sub ::edit-permission-filter
  (fn [db _]
    (-> db :search :filters :edit-permission?)))

(rf/reg-sub ::retkikartta-filter
  (fn [db _]
    (-> db :search :filters :retkikartta?)))

(rf/reg-sub ::harrastuspassi-filter
  (fn [db _]
    (-> db :search :filters :harrastuspassi?)))

(rf/reg-sub ::school-use-filter
  (fn [db _]
    (-> db :search :filters :school-use?)))

(rf/reg-sub ::bounding-box-filter
  (fn [db _]
    (-> db :search :filters :bounding-box?)))

(rf/reg-sub ::search-string
  (fn [db _]
    (-> db :search :string)))

(rf/reg-sub ::search-results
  (fn [db _]
    (-> db :search :results)))

(rf/reg-sub ::search-results-fast
  (fn [db _]
    (-> db :search :results-fast)))

(rf/reg-sub ::search-results-total-count
  :<- [::search-results-fast]
  (fn [^js results _]
    (if results
      (some-> results .-hits .-total .-value)
      0)))

(defn ->table-entry
  [{:keys [locale types cities admins owners user]} hit]
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
     :construction-year       (-> site :construction-year)
     :renovation-years        (-> site :renovation-years)
     :phone-number            (-> site :phone-number)
     :admin.name              (-> site :admin admins locale)
     :owner.name              (-> site :owner owners locale)
     :type.type-code          type-code
     :type.name               (get-in types [type-code :name locale])
     :type.main-category      (-> site :search-meta :type :main-category :name locale)
     :type.sub-category       (-> site :search-meta :type :sub-category :name locale)
     :location.address        (-> site :location :address)
     :location.postal-code    (-> site :location :postal-code)
     :location.postal-office  (-> site :location :postal-office)
     :location.city.city-code city-code
     :location.city.name      (get-in cities [city-code :name locale])
     :permission?             (roles/check-privilege user (roles/site-roles-context site) :site/create-edit)}))

(defn ->table-entry2 [m hit]
  (->table-entry m (js->clj hit :keywordize-keys true)))

(rf/reg-sub ::search-results-table-data
  :<- [::search-results-fast]
  :<- [:lipas.ui.subs/translator]
  :<- [:lipas.ui.sports-sites.subs/active-types]
  :<- [:lipas.ui.sports-sites.subs/cities-by-city-code]
  :<- [:lipas.ui.sports-sites.subs/admins]
  :<- [:lipas.ui.sports-sites.subs/owners]
  :<- [:lipas.ui.user.subs/user-data]
  (fn [[results tr types cities admins owners user] _]
    (let [locale (tr)
          data   {:types  types
                  :cities cities
                  :locale locale
                  :admins admins
                  :owners owners
                  :user   user}
          hits   (.-hits results)]
      (when hits
        (->> (.-hits hits)
             (map (partial ->table-entry2 data))
             (sort-by :score utils/reverse-cmp))))))

(defn ->list-entry
  [{:keys [locale types cities]} hit]
  (let [site      (:_source hit)
        type-code (-> site :type :type-code)
        city-code (-> site :location :city :city-code)]
    {:lipas-id                (-> site :lipas-id)
     :score                   (-> hit :_score)
     :name                    (-> site :name)
     :type.type-code          type-code
     :type.name               (get-in types [type-code :name locale])
     :location.city.city-code city-code
     :location.city.name      (get-in cities [city-code :name locale])}))

(rf/reg-sub ::search-results-list-data
  :<- [::search-results-fast]
  :<- [:lipas.ui.subs/translator]
  :<- [:lipas.ui.sports-sites.subs/active-types]
  :<- [:lipas.ui.sports-sites.subs/cities-by-city-code]
  (fn [[results tr types cities] _]
    (let [locale (tr)
          data   {:types types :cities cities :locale locale}]
      (->> (-> results (js->clj :keywordize-keys true) :hits :hits)
           (map (partial ->list-entry data))
           (sort-by :score utils/reverse-cmp)
           vec))))

(rf/reg-sub ::search-results-view
  (fn [db _]
    (-> db :search :results-view)))

(rf/reg-sub ::results-table-columns
  :<- [:lipas.ui.subs/translator]
  (fn [tr _]
    [[:name                   {:label (tr :lipas.sports-site/name)}]
     [:type.name              {:label (tr :type/name)}]
     [:type.main-category     {:label (tr :type/main-category)}]
     [:type.sub-category      {:label (tr :type/sub-category)}]
     [:admin.name             {:label (tr :lipas.sports-site/admin)}]
     [:owner.name             {:label (tr :lipas.sports-site/owner)}]
     [:construction-year      {:label (tr :lipas.sports-site/construction-year)}]
     [:renovation-years       {:label (tr :lipas.sports-site/renovation-years)}]
     [:location.city.name     {:label (tr :lipas.location/city)}]
     [:location.address       {:label (tr :lipas.location/address)}]
     [:location.postal-code   {:label (tr :lipas.location/postal-code)}]
     [:location.postal-office {:label (tr :lipas.location/postal-office)}]
     [:www                    {:label (tr :lipas.sports-site/www)}]
     [:email                  {:label (tr :lipas.sports-site/email-public)}]
     [:phone-number           {:label (tr :lipas.sports-site/phone-number)}]
     [:event-date             {:label (tr :lipas.sports-site/event-date)}]
     [:lipas-id               {:label "Lipas-id"}]]))

(rf/reg-sub ::selected-results-table-columns
  (fn [db _]
   ;; (conj :score) for debugging search results
    (-> db :search :selected-results-table-columns set)))

(rf/reg-sub ::results-table-specs
  (fn [_]
    {:email                  {:spec :lipas.sports-site/email}
     :phone-number           {:spec :lipas.sports-site/phone-number}
     :www                    {:spec :lipas.sports-site/www}
     :construction-year      {:spec :lipas.sports-site/construction-year}
     :renovation-years       {:spec :lipas.sports-site/renovation-years}
     :name                   {:spec :lipas.sports-site/name :required? true}
     :location.postal-office {:spec :lipas.location/postal-office}
     :location.postal-code   {:spec :lipas.location/postal-code :required? true}
     :marketing-name         {:spec :lipas.sports-site/marketing-name}
     :location.address       {:spec :lipas.location/address :required? true}}))

(rf/reg-sub ::results-table-headers
  :<- [:lipas.ui.subs/translator]
  :<- [::selected-results-table-columns]
  :<- [::results-table-specs]
  :<- [:lipas.ui.user.subs/permission-to-types]
  :<- [:lipas.ui.user.subs/permission-to-cities]
  (fn [[tr selected-cols specs types cities] _]
    (->>
      [[:score                  {:label "score"}]
       [:name                   {:label (tr :lipas.sports-site/name)
                                 :form  {:component lui/text-field}}]
       [:marketing-name         {:label (tr :lipas.sports-site/marketing-name)
                                 :form  {:component lui/text-field}}]
       [:type.name              {:label (tr :type/name)
                                 :form
                                 {:component lui/type-selector-single
                                  :value-key :type.type-code
                                  :props     {:types types}}}]
       [:type.main-category     {:label (tr :type/main-category)}]
       [:type.sub-category      {:label (tr :type/sub-category)}]
       [:owner.name             {:label (tr :lipas.sports-site/owner)
                                 :form
                                 {:component lui/owner-selector-single
                                  :value-key :owner}}]
       [:admin.name             {:label (tr :lipas.sports-site/admin)
                                 :form
                                 {:component lui/admin-selector-single
                                  :value-key :admin}}]
       [:construction-year      {:label (tr :lipas.sports-site/construction-year)
                                 :form  {:component lui/year-selector}}]
       [:renovation-years       {:label (tr :lipas.sports-site/renovation-years)
                                 :form
                                 {:component lui/year-selector
                                  :props     {:multi? true}}}]
       [:location.city.name     {:label (tr :lipas.location/city)
                                 :form
                                 {:component lui/city-selector-single
                                  :props     {:cities cities}
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
       [:event-date             {:label (tr :lipas.sports-site/event-date)}]
       [:lipas-id               {:label "Lipas-id"}]]
      (reduce
        (fn [res [k v]]
          (conj res [k (-> v
                           (assoc :hidden? (not (some? (selected-cols k))))
                           (assoc-in [:form :props :spec] (-> k specs :spec))
                           (assoc-in [:form :props :required] (-> k specs :required?)))]))
        []))))

(rf/reg-sub ::sort-opts
  (fn [db _]
    (-> db :search :sort)))

(rf/reg-sub ::pagination*
  (fn [db _]
    (-> db :search :pagination)))

(rf/reg-sub ::pagination
  :<- [::pagination*]
  :<- [:lipas.ui.subs/logged-in?]
  (fn [[pagination logged-in?] _]
    (if logged-in?
      (update pagination :page-sizes conj 5000)
      pagination)))

(rf/reg-sub ::in-progress?
  (fn [db _]
    (-> db :search :in-progress?)))

(rf/reg-sub ::allow-changing-bounding-box-filter?
  :<- [::pagination]
  (fn [{:keys [page-size]}]
   ;;(>= 500 page-size)
    true))

(rf/reg-sub ::save-dialog-open?
  (fn [db _]
    (-> db :search :save-dialog-open?)))
