(ns lipas.ui.feedback.events
  (:require
   [ajax.core :as ajax]
   [re-frame.core :as re-frame]))

(re-frame/reg-event-db
 ::open-modal
 (fn [db _]
   (let [email (-> db :user :login :email)]
     (cond-> db
       true (assoc-in [:feedback :modal-open?] true)
       email (assoc-in [:feedback :form :lipas.feedback/sender] email)))))

(re-frame/reg-event-db
 ::close-modal
 (fn [db _]
   (assoc-in db [:feedback :modal-open?] false)))

(re-frame/reg-event-db
 ::select-feedback-type
 (fn [db [_ v]]
   (let [v (keyword :feedback.type v)]
     (assoc-in db [:feedback :form :lipas.feedback/type] v))))

(re-frame/reg-event-db
 ::set-sender-email
 (fn [db [_ v]]
   (if v
     (assoc-in db [:feedback :form :lipas.feedback/sender] v)
     (update-in db [:feedback :form] dissoc :lipas.feedback/sender))))

(re-frame/reg-event-db
 ::set-text
 (fn [db [_ v]]
   (assoc-in db [:feedback :form :lipas.feedback/text] v)))

(re-frame/reg-event-fx
 ::send
 (fn [{:keys [db]} [_ body]]
   {:http-xhrio
    {:method          :post
     :uri             (str (:backend-url db) "/actions/send-feedback")
     :params          body
     :format          (ajax/transit-request-format)
     :response-format (ajax/transit-response-format)
     :on-success      [::send-success]
     :on-failure      [::send-failure]}
    :db (assoc-in db [:feedback :in-progress?] true)}))

(re-frame/reg-event-fx
 ::send-failure
 (fn [{:keys [db]} [joo jo2]]
   (let [tr           (:translator db)
         notification {:message  (tr :notifications/save-failed)
                       :success? false}]
     {:db       (assoc-in db [:feedback :in-progress?] false)
      :dispatch [:lipas.ui.events/set-active-notification notification]})))

(re-frame/reg-event-fx
 ::send-success
 (fn [{:keys [db]} _]
   (let [tr           (:translator db)
         notification {:message  (tr :notifications/thank-you-for-feedback)
                       :success? true}]
     {:db       (-> db
                    (assoc-in [:feedback :in-progress?] false)
                    (update-in [:feedback :form] dissoc :lipas.feedback/text))
      :dispatch-n [[::close-modal]
                   [:lipas.ui.events/set-active-notification notification]]})))
