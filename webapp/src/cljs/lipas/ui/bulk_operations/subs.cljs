(ns lipas.ui.bulk-operations.subs
  (:require [clojure.string :as str]
            [lipas.data.bulk-operations :as bulk-fields]
            [re-frame.core :as rf]))

(rf/reg-sub ::editable-sites
  (fn [db _]
    (get-in db [:bulk-operations :editable-sites] [])))

(rf/reg-sub ::selected-sites
  (fn [db _]
    (get-in db [:bulk-operations :selected-sites] #{})))

(rf/reg-sub ::bulk-update-form
  (fn [db _]
    (get-in db [:bulk-operations :update-form] {})))

(rf/reg-sub ::selected-fields
  (fn [db _]
    (get-in db [:bulk-operations :selected-fields] #{})))

(rf/reg-sub ::sites-filters
  (fn [db _]
    (get-in db [:bulk-operations :filters] {})))

(rf/reg-sub ::loading?
  (fn [db _]
    (get-in db [:bulk-operations :loading?] false)))

(rf/reg-sub ::error
  (fn [db _]
    (get-in db [:bulk-operations :error])))

(rf/reg-sub ::filtered-editable-sites
  :<- [::editable-sites]
  :<- [::sites-filters]
  (fn [[sites filters] _]
    ;; Multi-value filters (type-codes / admins / statuses) mirror the map-view
    ;; search filters: an empty selection means "no filter", any selection keeps
    ;; rows whose value is in the chosen set.
    (let [{:keys [type-codes admins statuses search-text ownership]} filters
          type-set   (set type-codes)
          admin-set  (set admins)
          status-set (set statuses)]
      (cond->> sites
        ;; org-specific: owned vs granted (cross-org edit grant)
        (= ownership "owned")
        (filter :owned?)

        (= ownership "granted")
        (filter (complement :owned?))

        (seq type-set)
        (filter #(contains? type-set (get-in % [:type :type-code])))

        (seq admin-set)
        (filter #(contains? admin-set (:admin %)))

        (seq status-set)
        (filter #(contains? status-set (:status %)))

        (not (str/blank? search-text))
        (filter (fn [site]
                  (let [search-lower (str/lower-case search-text)]
                    (or (and (:name site)
                             (str/includes? (str/lower-case (:name site)) search-lower))
                        (and (:email site)
                             (str/includes? (str/lower-case (:email site)) search-lower))
                        (and (:phone-number site)
                             (str/includes? (:phone-number site) search-lower))
                        (and (:www site)
                             (str/includes? (str/lower-case (:www site)) search-lower))
                        (and (:reservations-link site)
                             (str/includes? (str/lower-case (:reservations-link site)) search-lower))))))))))

(rf/reg-sub ::selected-sites-count
  :<- [::selected-sites]
  (fn [selected _]
    (count selected)))

(rf/reg-sub ::all-sites-selected?
  :<- [::filtered-editable-sites]
  :<- [::selected-sites]
  (fn [[sites selected] _]
    (and (seq sites)
         (= (count selected) (count sites)))))

(rf/reg-sub ::current-step
  (fn [db _]
    (get-in db [:bulk-operations :current-step] 0)))

(rf/reg-sub ::update-results
  (fn [db _]
    (get-in db [:bulk-operations :update-results])))

(rf/reg-sub ::selected-sites-data
  :<- [::editable-sites]
  :<- [::selected-sites]
  (fn [[sites selected] _]
    (filter #(contains? selected (:lipas-id %)) sites)))

(rf/reg-sub ::selected-sites-type-codes
  :<- [::selected-sites-data]
  (fn [sites _]
    (->> sites
         (map #(get-in % [:type :type-code]))
         (remove nil?)
         distinct)))

;; The type-specific property fields offered for bulk edit: the properties
;; COMMON to every selected site's type (their intersection). Empty when the
;; selection is heterogeneous — the view then shows a "no common properties"
;; note instead of property inputs.
(rf/reg-sub ::common-property-fields
  :<- [::selected-sites-type-codes]
  (fn [type-codes _]
    (bulk-fields/property-fields type-codes)))
