(ns lipas.ui.help.events
  (:require [ajax.core :as ajax]
            [re-frame.core :as rf]))

(rf/reg-event-db ::open-dialog
  (fn [db _]
    (assoc-in db [:help :dialog :open?] true)))

(rf/reg-event-db ::close-dialog
  (fn [db]
    (assoc-in db [:help :dialog :open?] false)))

(rf/reg-event-db ::select-section
  (fn [db [_ v]]
    (-> db
        (assoc-in [:help :dialog :selected-section] v)
        (assoc-in [:help :dialog :selected-page] nil))))

(rf/reg-event-db ::select-page
  (fn [db [_ v]]
    (assoc-in db [:help :dialog :selected-page] v)))

(rf/reg-event-fx ::open-edit-mode
  (fn [{:keys [db]} _]
    {:db (assoc-in db [:help :dialog :mode] :edit)
     :fx [[:dispatch [:lipas.ui.help.manage/initialize-editor]]]}))

(rf/reg-event-db ::close-edit-mode
  (fn [db _]
    (assoc-in db [:help :dialog :mode] :read)))

(rf/reg-event-fx ::get-help-data
  (fn [{:keys [db]} _]
    {:db (assoc-in db [:help :save-in-progress] true)
     :fx [[:http-xhrio
           {:method          :post
            :uri             (str (:backend-url db) "/actions/get-help-data")
            :format          (ajax/transit-request-format)
            :response-format (ajax/transit-response-format)
            :on-success      [::get-success]
            :on-failure      [::get-failure]}]]}))

(rf/reg-event-fx ::get-success
  (fn [{:keys [db]} [_ help-data]]
    (prn help-data)
    {:db (assoc-in db [:help :data] help-data)}))

(rf/reg-event-fx ::get-failure
  (fn [{:keys [db]} [_ resp]]
    (let [tr           (:translator db)
          notification {:message  (tr :notifications/save-failed)
                        :success? false}]
      {:db (assoc-in db [:help :errors :get] resp)
       :fx [[:dispatch [:lipas.ui.events/set-active-notification notification]]]})))
