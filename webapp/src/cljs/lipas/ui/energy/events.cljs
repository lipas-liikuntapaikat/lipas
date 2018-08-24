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

(defn- calculate-totals [data]
  {:electricity-mwh (reduce + (map :electricity-mwh (vals data)))
   :heat-mwh        (reduce + (map :heat-mwh (vals data)))
   :cold-mwh        (reduce + (map :cold-mwh (vals data)))
   :water-m3        (reduce + (map :water-m3 (vals data)))})

(re-frame/reg-event-db
 ::calculate-total-energy-consumption
 (fn [db [_ lipas-id]]
   (let [base-path    [:sports-sites lipas-id :editing]
         yearly-path  (conj base-path :energy-consumption)
         monthly-path (conj base-path :energy-consumption-monthly)
         monthly-data (get-in db monthly-path)]
     (if monthly-data
       (update-in db yearly-path #(calculate-totals monthly-data))
       db))))

(re-frame/reg-event-fx
 ::set-monthly-energy-consumption
 (fn [{:keys [db]} [_ lipas-id month field value]]
   (let [basepath [:sports-sites lipas-id :editing :energy-consumption-monthly]
         path  (into basepath [month field])]
     {:db (assoc-in db path value)
      :dispatch [::calculate-total-energy-consumption lipas-id]})))

(re-frame/reg-event-fx
 ::commit-energy-consumption
 (fn [_ [_ rev draft?]]
   (let [status (if draft? "draft" (:status rev))
         rev    (-> (utils/make-saveable rev)
                    (assoc :status status))]
     {:dispatch [:lipas.ui.sports-sites.events/commit-rev rev]})))

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
   ;; (prn "Get by type-code!")
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
