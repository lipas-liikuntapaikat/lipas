(ns lipas.ui.admin.ptv-adoption.events
  "PTV adoption admin tab. App-db state lives under [:admin :ptv-adoption ...]."
  (:require [ajax.core :as ajax]
            [re-frame.core :as rf]))

(rf/reg-event-fx ::fetch
  (fn [{:keys [db]} _]
    {:db (-> db
             (assoc-in [:admin :ptv-adoption :loading?] true)
             (assoc-in [:admin :ptv-adoption :error] nil))
     :http-xhrio {:method :post
                  :uri (str (:backend-url db) "/actions/get-ptv-adoption-stats")
                  :params {}
                  :format (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :headers {:Authorization (str "Token " (-> db :user :login :token))}
                  :on-success [::fetch-success]
                  :on-failure [::fetch-failure]}}))

(rf/reg-event-db ::fetch-success
  (fn [db [_ data]]
    (update-in db [:admin :ptv-adoption] assoc :stats data :loading? false)))

(rf/reg-event-db ::fetch-failure
  (fn [db [_ resp]]
    (update-in db [:admin :ptv-adoption] assoc
               :loading? false
               :error (or (-> resp :response :error) (:status-text resp) "Request failed"))))
