(ns lipas.ui.admin.subs
  (:require [clojure.string :as str]
            [lipas.utils :as cutils]
            [re-frame.core :as rf]))

(rf/reg-sub ::users
            (fn [db _]
              (-> db :admin :users)))

(rf/reg-sub ::users-status
            (fn [db _]
              (-> db :admin :users-status)))

(rf/reg-sub ::users-filter
            (fn [db _]
              (-> db :admin :users-filter)))

(defn ->users-list-entry
  [tr user]
  {:id (-> user :id)
   :email (-> user :email)
   :firstname (-> user :user-data :firstname)
   :lastname (-> user :user-data :lastname)
   :roles (->> user :permissions :roles
               (map (fn [x]
                      (tr (keyword :lipas.user.permissions.roles.role-names (:role x)))))
               (str/join ", "))})

(rf/reg-sub ::users-list
            :<- [::users]
            :<- [::users-status]
            :<- [::users-filter]
            :<- [:lipas.ui.subs/translator]
            (fn [[users status filter-text tr] _]
              (let [users (->> users
                               vals
                               (filter (comp #{status} :status))
                               (map (partial ->users-list-entry tr)))]
                (if (seq filter-text)
                  (filter
                   #(-> %
                        str
                        str/lower-case
                        (str/includes? (str/lower-case filter-text))) users)
                  users))))

(rf/reg-sub ::archived-users-list
            :<- [::archived-users]
            :<- [::users-filter]
            :<- [:lipas.ui.sports-sites.subs/cities-by-city-code]
            :<- [:lipas.ui.sports-sites.subs/active-types]
            (fn [[users filter-text cities types] _]
              (let [users (->> users (map (partial ->users-list-entry cities types)))]
                (if (not-empty filter-text)
                  (filter
                   #(-> %
                        str
                        str/lower-case
                        (str/includes? (str/lower-case filter-text))) users)
                  users))))

(rf/reg-sub ::selected-user
            (fn [db _]
              (get-in db [:admin :users (-> db :admin :selected-user)])))

(rf/reg-sub ::editing-user
            (fn [db _]
              (get-in db [:admin :editing-user])))

(rf/reg-sub ::edit-role
            (fn [db _]
              (if-let [idx (:edit-role (:admin db))]
                (assoc (get-in db [:admin :editing-user :permissions :roles idx]) :editing? true)
                (get-in db [:admin :new-role]))))

(defn prettify-timestamp [s]
  (-> s
      (str/replace "T" " ")
      (str/split ".")
      first))

(rf/reg-sub ::user-history
            :<- [::editing-user]
            (fn [user _]
              (->> user :history :events
                   (map #(update % :event-date prettify-timestamp))
                   (sort-by :event-date cutils/reverse-cmp))))

(defn ->list-entry
  [locale [k v]]
  {:value k
   :label (str (get-in v [:name locale])
               " "
               k
               (when (not= "active" (:status v))
                 " POISTUNUT"))})

(rf/reg-sub ::types-list
            :<- [:lipas.ui.sports-sites.subs/all-types]
            (fn [types [_ locale]]
              (->> types
                   (map (partial ->list-entry locale))
                   (sort-by :label))))

(rf/reg-sub ::cities-list
            :<- [:lipas.ui.sports-sites.subs/cities-by-city-code]
            (fn [cities [_ locale]]
              (->> cities
                   (map (partial ->list-entry locale))
                   (sort-by :label))))

(rf/reg-sub ::sites-list
            :<- [:lipas.ui.sports-sites.subs/latest-sports-site-revs]
            (fn [sites _]
              (->> sites
                   (map (fn [[lipas-id s]] {:value lipas-id :label (:name s)}))
                   (sort-by :label))))

(rf/reg-sub ::activities-list
            :<- [:lipas.ui.sports-sites.activities.subs/data]
            (fn [activities [_ locale]]
              (->> activities
                   (map (fn [[k m]] {:value k :label (get-in m [:label locale])}))
                   (sort-by :label))))

(rf/reg-sub ::magic-link-dialog-open?
            (fn [db _]
              (-> db :admin :magic-link-dialog-open?)))

(rf/reg-sub ::magic-link-variants
            (fn [db _]
              (-> db :admin :magic-link-variants)))

(rf/reg-sub ::selected-magic-link-variant
            (fn [db _]
              (-> db :admin :selected-magic-link-variant)))

(rf/reg-sub ::selected-colors
            (fn [db _]
              (-> db :admin :color-picker)))

(rf/reg-sub ::selected-tab
            (fn [db _]
              (-> db :admin :selected-tab)))

;; Jobs monitoring subscriptions

(rf/reg-sub ::jobs-health
            (fn [db _]
              (get-in db [:admin :jobs :health])))

(rf/reg-sub ::jobs-metrics
            (fn [db _]
              (get-in db [:admin :jobs :metrics])))

(rf/reg-sub ::jobs-metrics-table-data
            :<- [::jobs-metrics]
            (fn [metrics-data _]
              (when-let [metrics (:performance-metrics metrics-data)]
                (map (fn [m]
                       {:type (get m :type "-")
                        :status (get m :status "-")
                        :job_count (str (get m :job_count "-"))
                        :avg_duration_seconds (let [v (get m :avg_duration_seconds)]
                                                (if (number? v)
                                                  (.toFixed (js/Number v) 2)
                                                  "-"))
                        :p95_duration_seconds (let [v (get m :p95_duration_seconds)]
                                                (if (number? v)
                                                  (.toFixed (js/Number v) 2)
                                                  "-"))
                        :avg_attempts (let [v (get m :avg_attempts)]
                                        (if (some? v)
                                          (.toFixed (double v) 2)
                                          "-"))
                        :avg_execution_seconds (let [v (get m :avg_execution_seconds)]
                                                 (if (some? v)
                                                   (.toFixed (double v) 2)
                                                   "-"))
                        :p95_execution_seconds (let [v (get m :p95_execution_seconds)]
                                                 (if (some? v)
                                                   (.toFixed (double v) 2)
                                                   "-"))
                        :avg_queue_seconds (let [v (get m :avg_queue_seconds)]
                                             (if (some? v)
                                               (.toFixed (double v) 2)
                                               "-"))
                        :p95_queue_seconds (let [v (get m :p95_queue_seconds)]
                                             (if (some? v)
                                               (.toFixed (double v) 2)
                                               "-"))
                        :earliest_job (get m :earliest_job "-")
                        :latest_job (get m :latest_job "-")})
                     metrics))))

(rf/reg-sub ::jobs-loading?
            (fn [db _]
              (get-in db [:admin :jobs :loading?])))

(rf/reg-sub ::jobs-error
            (fn [db _]
              (get-in db [:admin :jobs :error])))

 ;; Dead Letter Queue subscriptions

(rf/reg-sub ::dead-letter-jobs
            (fn [db _]
              (get-in db [:admin :jobs :dead-letter :jobs] [])))

(rf/reg-sub ::dead-letter-filter
            (fn [db _]
              (get-in db [:admin :jobs :dead-letter :filter] :unacknowledged)))

(rf/reg-sub ::dead-letter-loading?
            (fn [db _]
              (get-in db [:admin :jobs :dead-letter :loading?] false)))

(rf/reg-sub ::dead-letter-error
            (fn [db _]
              (get-in db [:admin :jobs :dead-letter :error])))

(rf/reg-sub ::dead-letter-stats
            :<- [::dead-letter-jobs]
            (fn [jobs _]
              (let [total (count jobs)
                    unacknowledged (count (remove :acknowledged jobs))
                    acknowledged (count (filter :acknowledged jobs))]
                {:total total
                 :unacknowledged unacknowledged
                 :acknowledged acknowledged})))

(rf/reg-sub ::filtered-dead-letter-jobs
            :<- [::dead-letter-jobs]
            :<- [::dead-letter-filter]
            (fn [[jobs filter-value] _]
              (case filter-value
                :all jobs
                :unacknowledged (remove :acknowledged jobs)
                :acknowledged (filter :acknowledged jobs)
                jobs)))

(rf/reg-sub ::jobs-selected-sub-tab
            (fn [db _]
              (get-in db [:admin :jobs :selected-sub-tab] 0)))
