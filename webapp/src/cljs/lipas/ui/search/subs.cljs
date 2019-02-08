(ns lipas.ui.search.subs
  (:require
   [re-frame.core :as re-frame]
   [lipas.ui.utils :as utils]))

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

(defn ->search-result [{:keys [locale types cities admins owners]} hit]
  (let [site      (:_source hit)
        type-code (-> site :type :type-code)
        city-code (-> site :location :city :city-code)]
    {:lipas-id           (-> site :lipas-id)
     :score              (-> hit :_score)
     :name               (-> site :name)
     :event-date         (-> site :event-date utils/->short-date)
     :admin              (-> site :admin admins locale)
     :owner              (-> site :owner owners locale)
     :type.name          (get-in types [type-code :name locale])
     :location.city.name (get-in cities [city-code :name locale])}))

(re-frame/reg-sub
 ::search-results-list
 :<- [::search-results]
 :<- [:lipas.ui.subs/translator]
 :<- [:lipas.ui.sports-sites.subs/all-types]
 :<- [:lipas.ui.sports-sites.subs/cities-by-city-code]
 :<- [:lipas.ui.sports-sites.subs/admins]
 :<- [:lipas.ui.sports-sites.subs/owners]
 (fn [[results tr types cities admins owners] _]
   (let [locale (tr)
         data   {:types  types  :cities cities :locale locale
                 :admins admins :owners owners}]
     (->> (-> results :hits :hits)
          (map (partial ->search-result data))
          (sort-by :score utils/reverse-cmp)))))

(re-frame/reg-sub
 ::search-results-view
 (fn [db _]
   (-> db :search :results-view)))

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
