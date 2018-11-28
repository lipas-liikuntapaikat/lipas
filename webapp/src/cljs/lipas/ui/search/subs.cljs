(ns lipas.ui.search.subs
  (:require [re-frame.core :as re-frame]
            [lipas.ui.utils :as utils]))

(re-frame/reg-sub
 ::filters
 (fn [db _]
   (-> db :search :filters)))

(re-frame/reg-sub
 ::types-filter
 (fn [db _]
   (-> db :search :filters :type-codes set)))

(re-frame/reg-sub
 ::cities-filter
 (fn [db _]
   (-> db :search :filters :city-codes set)))

(re-frame/reg-sub
 ::area-min-filter
 (fn [db _]
   (-> db :search :filters :area-min)))

(re-frame/reg-sub
 ::area-max-filter
 (fn [db _]
   (-> db :search :filters :area-max)))

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

(defn ->search-result [{:keys [locale types cities]} hit]
  (let [site      (:_source hit)
        type-code (-> site :type :type-code)
        city-code (-> site :location :city :city-code)]
    {:lipas-id (-> site :lipas-id)
     :score    (-> hit :_score)
     :name     (-> site :name)
     :type     (get-in types [type-code :name locale])
     :city     (get-in cities [city-code :name locale])}))

(re-frame/reg-sub
 ::search-results-list
 :<- [::search-results]
 :<- [:lipas.ui.subs/translator]
 :<- [:lipas.ui.sports-sites.subs/all-types]
 :<- [:lipas.ui.sports-sites.subs/cities-by-city-code]
 (fn [[results tr types cities] _]
   (let [locale (tr)
         data   {:types types :cities cities :locale locale}]
     (->> (-> results :hits :hits)
          (map (partial ->search-result data))
          (sort-by :score utils/reverse-cmp)))))
