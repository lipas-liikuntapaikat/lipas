(ns lipas-ui.register.events
  (:require [re-frame.core :as re-frame]
            [ajax.core :as ajax]))

(re-frame/reg-event-db
 ::set-registration-form-field
 (fn [db [_ path value]]
   (let [path (into [:user :registration-form] path)]
     (assoc-in db path value))))

(re-frame/reg-event-db
 ::registration-success
 (fn [db [_ result]]
   (assoc-in db [:user :registration] result)))

(re-frame/reg-event-db
 ::registration-failure
 (fn [db [_ result]]
   (assoc-in db [:user :registration-error] result)))

(re-frame/reg-event-fx
 ::submit-registration-form
 (fn [{:keys [db]} [_ form-data]]
   {:http-xhrio {:method          :post
                 :uri             "http://localhost:8090/api/v1/user"
                 :params          form-data
                 :format          (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success      [::registration-success]
                 :on-failure      [::registration-failure]}

    :db         (assoc db :spinner true)}))
