(ns lipas.ui.reports.events
  (:require [ajax.core :as ajax]
            [ajax.protocols :as ajaxp]
            [clojure.set :as cset]
            [lipas.utils :as cutils]
            [re-frame.core :as rf]))

(rf/reg-event-db ::toggle-dialog
  (fn [db _]
    (update-in db [:reports :dialog-open?] not)))

(rf/reg-event-db ::set-selected-fields
  (fn [db [_ v append?]]
    (if append?
      (update-in db [:reports :selected-fields] (comp set into) v)
      (assoc-in db [:reports :selected-fields] v))))

(rf/reg-event-db ::set-selected-format
  (fn [db [_ v]]
    (assoc-in db [:reports :selected-format] v)))

(def basic-fields
  ["lipas-id"
   "name"
   "marketing-name"
   "type.type-code"
   "type.type-name"
   "location.city.city-code"
   "location.city.city-name"
   "construction-year"
   "admin"
   "owner"
   "renovation-years"
   "www"
   "phone-number"
   "email"
   "location.address"
   "location.city.neighborhood"
   "location.postal-code"
   "location.postal-office"
   "comment"])

(defn sort-headers
  "Returns vector of headers where basic-fields are in predefined order
  and rest in natural order."
  [fields]
  (let [basic (filterv #(some #{%} fields) basic-fields)
        others (cset/difference (set fields) (set basic))]
    (into [] cat
          [basic
           (sort others)])))

(rf/reg-event-fx ::create-report
  (fn [{:keys [db]} [_ query fields fmt]]
    (let [fields       (sort-headers fields)
          content-type (condp = fmt
                         "xlsx"    (:xlsx cutils/content-type)
                         "geojson" (:json cutils/content-type)
                         "csv"     (:csv cutils/content-type))
          filename (str "lipas_raportti." fmt)]
      {:http-xhrio
       {:method          :post
        :uri             (str (:backend-url db) "/actions/create-sports-sites-report")
        :params          {:search-query query
                          :format       fmt
                          :fields       fields
                          :locale       (-> db :translator (apply []))}
        :format          (ajax/transit-request-format)
        :response-format {:type         :blob
                          :content-type content-type
                          :description  content-type
                          :read         ajaxp/-body}
        :on-success      [::report-success filename content-type]
        :on-failure      [::report-failure]}
       :db (assoc-in db [:reports :downloading?] true)})))

(rf/reg-event-fx ::report-success
  (fn [{:keys [db]} [_ filename content-type blob]]
    {:lipas.ui.effects/save-as! {:blob         blob
                                 :filename     filename
                                 :content-type content-type}
     :db (assoc-in db [:reports :downloading?] false)}))

(rf/reg-event-fx ::report-failure
  (fn [{:keys [db]} [_ _error]]
    (let [tr     (-> db :translator)]
      {:db             (assoc-in db [:reports :downloading?] false)
       :tracker/event! ["error" "create-report-failure"]
       :dispatch       [:lipas.ui.events/set-active-notification
                        {:message  (tr :notifications/get-failed)
                         :success? false}]})))

(rf/reg-event-fx ::save-current-report
  (fn [{:keys [db]} [_ name]]
    (let [m         {:name name :fields (-> db :reports :selected-fields)}
          user-data (-> db
                        :user
                        :login
                        :user-data
                        (update :saved-reports conj m))]
      {:dispatch-n
       [[:lipas.ui.user.events/update-user-data user-data]
        [::toggle-save-dialog]]
       :tracker/event! ["user" "save-my-report"]})))

(rf/reg-event-fx ::open-saved-report
  (fn [_ [_ fields]]
    {:dispatch       [::set-selected-fields fields]
     :tracker/event! ["user" "open-my-report"]}))

(rf/reg-event-db ::toggle-save-dialog
  (fn [db _]
    (update-in db [:reports :save-dialog-open?] not)))

(rf/reg-event-fx ::create-data-model-report
  (fn [{:keys [db]} [_ _]]
    (let [content-type (:xlsx cutils/content-type)
          fmt "xlsx"]
      {:http-xhrio
       {:method          :post
        :uri             (str (:backend-url db) "/actions/create-data-model-report")
        :params          {}
        :format          (ajax/transit-request-format)
        :response-format {:type         :blob
                          :content-type content-type
                          :description  content-type
                          :read         ajaxp/-body}
        :on-success      [::report-success "lipas_tietomalli.xlsx" content-type]
        :on-failure      [::report-failure]}
       :db (assoc-in db [:reports :downloading?] true)})))
