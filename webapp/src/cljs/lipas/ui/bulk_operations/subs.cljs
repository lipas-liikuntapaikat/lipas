(ns lipas.ui.bulk-operations.subs
  (:require [re-frame.core :as rf]))

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

        (not (clojure.string/blank? search-text))
        (filter (fn [site]
                  (let [search-lower (clojure.string/lower-case search-text)]
                    (or (and (:name site)
                             (clojure.string/includes? (clojure.string/lower-case (:name site)) search-lower))
                        (and (:email site)
                             (clojure.string/includes? (clojure.string/lower-case (:email site)) search-lower))
                        (and (:phone-number site)
                             (clojure.string/includes? (:phone-number site) search-lower))
                        (and (:www site)
                             (clojure.string/includes? (clojure.string/lower-case (:www site)) search-lower))
                        (and (:reservations-link site)
                             (clojure.string/includes? (clojure.string/lower-case (:reservations-link site)) search-lower))))))))))

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
