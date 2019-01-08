(ns lipas.ui.reports.events
  (:require
   [ajax.core :as ajax]
   [ajax.protocols :as ajaxp]
   [lipas.utils :as cutils]
   [re-frame.core :as re-frame]))

(re-frame/reg-event-db
 ::toggle-dialog
 (fn [db _]
   (update-in db [:reports :dialog-open?] not)))

(re-frame/reg-event-db
 ::set-selected-fields
 (fn [db [_ v append?]]
   (if append?
     (update-in db [:reports :selected-fields] (comp set into) v)
     (assoc-in db [:reports :selected-fields] v))))

(re-frame/reg-event-fx
 ::create-report
 (fn [{:keys [db]} [_ query fields]]
   {:http-xhrio
    {:method          :post
     :uri             (str (:backend-url db) "/actions/create-sports-sites-report")
     :params          {:search-query query
                       :fields       fields}
     :format          (ajax/json-request-format)
     :response-format {:type         :blob
                       :content-type (-> cutils/content-type :xlsx)
                       :description  (-> cutils/content-type :xlsx)
                       :read         ajaxp/-body}
     :on-success      [::report-success]
     :on-failure      [::report-failure]}
    :db (assoc-in db [:reports :downloading?] true)}))

(re-frame/reg-event-fx
 ::report-success
 (fn [{:keys [db ]} [_ blob]]
   {:lipas.ui.effects/save-as! {:blob         blob
                                :filename     "lipas.xlsx"
                                :content-type (-> cutils/content-type :xlsx)}
    :db (assoc-in db [:reports :downloading?] false)}))

(re-frame/reg-event-fx
 ::report-failure
 (fn [{:keys [db]} [_ error]]
   ;; TODO display error msg
   {:db (assoc-in db [:reports :downloading?] false)}))
