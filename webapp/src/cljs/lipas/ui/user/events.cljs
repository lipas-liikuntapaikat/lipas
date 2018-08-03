(ns lipas.ui.user.events
  (:require [ajax.core :as ajax]
            [re-frame.core :as re-frame]))

(re-frame/reg-event-fx
 ::reset-password-request-success
 (fn [{:keys [db]} [_ _]]
   (let [tr (:translator db)]
     {:dispatch [:lipas.ui.events/set-active-notification
                 {:message  (tr :reset-password/reset-link-sent)
                  :success? true}]
      :db (assoc-in db [:user :reset-password-request :success]
                    :reset-link-sent)})))

(re-frame/reg-event-fx
 ::failure
 (fn [{:keys [db]} [_ resp]]
   (let [tr    (:translator db)
         error (or (-> resp :response :type keyword)
                   (when (= 401 (:status resp)) :reset-token-expired)
                   :unknown)]
     {:dispatch [:lipas.ui.events/set-active-notification
                 {:message  (tr (keyword :error error))
                  :success? false}]
      :db       (assoc-in db [:user :reset-password-request :error] error)})))

(re-frame/reg-event-db
 ::clear-feedback
 (fn [db _]
   (assoc-in db [:user :reset-password-request] nil)))

(re-frame/reg-event-fx
 ::send-reset-password-request
 (fn [{:keys [db]} [_ email]]
   {:http-xhrio
    {:method          :post
     :uri             (str (:backend-url db) "/actions/request-password-reset")
     :params          {:email email}
     :format          (ajax/json-request-format)
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success      [::reset-password-request-success]
     :on-failure      [::failure]}
    :dispatch    [::clear-feedback]}))

(re-frame/reg-event-fx
 ::reset-password-success
 (fn [{:keys [db]} [_ _]]
   (let [tr (:translator db)]
     {:dispatch-n [[:lipas.ui.events/set-active-notification
                    {:message  (tr :reset-password/reset-success)
                     :success? true}]
                   [:lipas.ui.events/navigate "/#/kirjaudu"]]
      :db         (assoc-in db [:user :reset-password :success]
                            :reset-link-sent)})))


(re-frame/reg-event-fx
 ::reset-password
 (fn [{:keys [db]} [_ password token]]
   {:http-xhrio
    {:method          :post
     :headers         {:Authorization (str "Token " token)}
     :uri             (str (:backend-url db) "/actions/reset-password")
     :params          {:password password}
     :format          (ajax/json-request-format)
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success      [::reset-password-success]
     :on-failure      [::failure]}
    :dispatch    [::clear-feedback]}))
