(ns lipas.ui.reports.subs
  (:require
   [re-frame.core :as re-frame]))

(re-frame/reg-sub
 ::reports
 (fn [db _]
   (:reports db)))

(re-frame/reg-sub
 ::selected-tab
 :<- [::reports]
 (fn [reports _]
   (:selected-tab reports)))

(re-frame/reg-sub
 ::dialog-open?
 :<- [::reports]
 (fn [reports _]
   (:dialog-open? reports)))

(re-frame/reg-sub
 ::downloading?
 :<- [::reports]
 (fn [reports _]
   (:downloading? reports)))

(re-frame/reg-sub
 ::fields
 :<- [::reports]
 (fn [reports _]
   (:fields reports)))

(re-frame/reg-sub
 ::selected-fields
 :<- [::reports]
 (fn [reports _]
   (:selected-fields reports)))

(re-frame/reg-sub
 ::selected-format
 :<- [::reports]
 (fn [reports _]
   (:selected-format reports)))

(re-frame/reg-sub
 ::save-dialog-open?
 :<- [::reports]
 (fn [reports _]
   (:save-dialog-open? reports)))

(defn- make-quick-selects
  [tr]
  [{:label  (tr :lipas.sports-site/basic-data)
    :fields ["lipas-id" "name" "marketing-name" "comment"
             "construction-year" "renovation-years"]}
   {:label  (tr :lipas.sports-site/ownership)
    :fields ["admin" "owner"]}
   {:label  (tr :lipas.sports-site/contact)
    :fields ["email" "phone-number" "www"]}
   {:label  (tr :lipas.sports-site/address)
    :fields ["location.address" "location.postal-code"
             "location.postal-office"
             "location.city.city-name"
             "location.city.neighborhood"]}
   {:label  (tr :general/measures)
    :fields ["properties.field-length-m"
             "properties.field-width-m"
             "properties.area-m2"]}
   {:label  (tr :lipas.sports-site/surface-materials)
    :fields ["properties.surface-material"
             "properties.surface-material-info"]}
   {:label  (tr :type/name)
    :fields ["type.type-name"
             "search-meta.type.main-category.name.fi"
             "search-meta.type.sub-category.name.fi"]}
   {:label  (tr :lipas.location/city)
    :fields ["location.city.city-name"
             "search-meta.location.province.name.fi"
             "search-meta.location.avi-area.name.fi"]}
   {:label  (tr :general/last-modified)
    :fields ["event-date"]}])

(defn- make-select-all
  [fields tr]
  {:fields (keys fields)
   :label  (tr :actions/select-all)})

(re-frame/reg-sub
 ::quick-selects
 :<- [::fields]
 :<- [:lipas.ui.subs/translator]
 :<- [:lipas.ui.subs/logged-in?]
 (fn [[fields tr logged-in?] _]
   (let [quick-selects (make-quick-selects tr)]
     (if logged-in?
       (conj quick-selects (make-select-all fields tr))
       quick-selects))))

;; Excel generation halts after certain threshold. Not sure why.
;; CSV and GeoJSON can be streamed
(re-frame/reg-sub
 ::limits-exceeded?
 :<- [::selected-fields]
 :<- [:lipas.ui.search.subs/search-results-total-count]
 :<- [::selected-format]
 (fn [[fields results-count fmt] _]
   (and (#{"xlsx"} fmt)
        (> results-count 10000)
        (> (count fields) 20))))
