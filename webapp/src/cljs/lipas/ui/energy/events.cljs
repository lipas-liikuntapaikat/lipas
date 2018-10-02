(ns lipas.ui.energy.events
  (:require [ajax.core :as ajax]
            [lipas.ui.utils :as utils]
            [re-frame.core :as re-frame]))

(re-frame/reg-event-fx
 ::select-energy-consumption-site
 (fn [{:keys [db]} [_ lipas-id]]
   {:db       (-> db
                  (assoc-in [:energy-consumption :lipas-id] lipas-id)
                  (assoc-in [:energy-consumption :year] nil))
    :dispatch [:lipas.ui.sports-sites.events/get-history lipas-id]}))

(re-frame/reg-event-db
 ::select-energy-consumption-year
 (fn [db [_ year]]
   (let [lipas-id   (-> db :energy-consumption :lipas-id)
         site       (get-in db [:sports-sites lipas-id])
         event-date (if (utils/this-year? year)
                      (utils/timestamp)
                      (utils/->end-of-year year))
         rev        (or (utils/find-revision site year)
                        (utils/make-revision site event-date))
         rev        (assoc rev :event-date event-date)
         rev        (utils/make-editable rev)]
     (-> db
         (assoc-in [:energy-consumption :year] year)
         (assoc-in [:sports-sites lipas-id :editing] rev)))))

(defn- calculate-totals [yearly-data monthly-data]
  (prn (into #{} (mapcat keys (vals monthly-data))))
  (merge
   yearly-data
   (into {} (for [k (into #{} (mapcat keys (vals monthly-data)))]
              [k (reduce + (map k (vals monthly-data)))]))))

(re-frame/reg-event-db
 ::calculate-total-energy-consumption
 (fn [db [_ lipas-id]]
   (let [base-path    [:sports-sites lipas-id :editing]
         yearly-path  (conj base-path :energy-consumption)
         monthly-path (conj base-path :energy-consumption-monthly)
         monthly-data (get-in db monthly-path)]
     (if monthly-data
       (update-in db yearly-path #(calculate-totals % monthly-data))
       db))))

(re-frame/reg-event-db
 ::calculate-total-visitors
 (fn [db [_ lipas-id]]
   (let [base-path    [:sports-sites lipas-id :editing]
         yearly-path  (conj base-path :visitors)
         monthly-path (conj base-path :visitors-monthly)
         monthly-data (get-in db monthly-path)]
     (if monthly-data
       (update-in db yearly-path #(calculate-totals % monthly-data))
       db))))

(re-frame/reg-event-fx
 ::set-monthly-value
 (fn [{:keys [db]} [_ lipas-id path value]]
   (let [basepath [:sports-sites lipas-id :editing]
         path     (into basepath path)]
     {:db         (assoc-in db path value)
      :dispatch-n [(when (some #{:energy-consumption-monthly} path)
                     [::calculate-total-energy-consumption lipas-id])
                   (when (some #{:visitors-monthly} path)
                     [::calculate-total-visitors lipas-id])]})))

(re-frame/reg-event-fx
 ::commit-energy-consumption
 (fn [_ [_ rev draft?]]
   (let [status (if draft? "draft" (:status rev))
         rev    (-> (utils/make-saveable rev)
                    (assoc :status status))
         year (utils/resolve-year (:event-date rev))]
     {:dispatch [:lipas.ui.sports-sites.events/commit-rev rev]
      :dispatch-later ;; TODO super hacky, please figure out something else
      [{:ms 100 :dispatch [::select-energy-consumption-year year]}]})))

(re-frame/reg-event-db
 ::fetch-energy-report-success
 (fn [db [_ year type-code data]]
   (assoc-in db [:energy-stats year type-code] data)))

(re-frame/reg-event-fx
 ::fetch-energy-report-failure
 (fn [_ [_ resp]]
   (let [fatal? false]
     {:ga/exception [(:message resp) fatal?]})))

(re-frame/reg-event-fx
 ::fetch-energy-report
 (fn [{:keys [db]} [_ year type-code]]
   {:http-xhrio
    {:method          :post
     :params          {:type-code type-code
                       :year      year}
     :uri             (str (:backend-url db) "/actions/create-energy-report")
     :format          (ajax/json-request-format)
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success      [::fetch-energy-report-success year type-code]
     :on-failure      [::fetch-energy-report-failure]}}))

(re-frame/reg-event-db
 ::select-energy-type
 (fn [db [_ energy-type]]
   (assoc-in db [:energy-stats :chart-energy-type] energy-type)))
