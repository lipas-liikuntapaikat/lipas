(ns lipas.ui.org.events 
  (:require [ajax.core :as ajax]
            [re-frame.core :as rf]))

(rf/reg-event-db ::get-user-orgs-success
  (fn [db [_ resp]]
    (assoc-in db [:user :orgs] resp)))

(rf/reg-event-fx ::get-user-orgs
  (fn [{:keys [db]} _]
    (let [token (-> db :user :login :token)]
      {:http-xhrio
       {:method          :get
        :uri             (str (:backend-url db) "/current-user-orgs")
        :headers         {:Authorization (str "Token " token)}
        :format          (ajax/json-request-format)
        :response-format (ajax/json-response-format {:keywords? true})
        :on-success      [::get-user-orgs-success]
        :on-failure      [::todo]}})))
