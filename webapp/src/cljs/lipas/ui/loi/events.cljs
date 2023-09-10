(ns lipas.ui.loi.events
  (:require
   [ajax.core :as ajax]
   [lipas.ui.utils :as utils]
   [re-frame.core :as re-frame]))

(re-frame/reg-event-db
 ::select-loi-type
 (fn [db [_ v]]
   (assoc-in db [:loi :selected-type] v)))

(re-frame/reg-event-fx
 ::save
 (fn [{:keys [db]} [_ category loi-type props geoms]]
   (let [token (-> db :user :login :token)]
     {:http-xhrio
      {:method          :post
       :params          (merge
                         {:id           (str (random-uuid))
                          :event-date   (utils/timestamp)
                          :status       "active"
                          :loi-category category
                          :loi-type     loi-type
                          :geometries   geoms}
                         props)
       :uri             (str (:backend-url db) "/actions/save-loi")
       :headers         {:Authorization (str "Token " token)}
       :format          (ajax/json-request-format)
       :response-format (ajax/json-response-format {:keywords? true})
       :on-success      [::save-success]
       :on-failure      [::save-failure]}})))

(re-frame/reg-event-fx
 ::save-success
 (fn [{:keys [db]} [_  data]]
   (let [tr (:translator db)]
     {:db (assoc-in db [:reminders :data] data)
      :fx [[:dispatch [:lipas.ui.events/set-active-notification
                       {:message  (tr :notifications/save-success)
                        :success? true}]]
           [:dispatch [:lipas.ui.map.events/discard-drawing]]
           [:dispatch [::search]]]})))

(re-frame/reg-event-fx
 ::save-failure
 (fn [{:keys [db]} [_ _resp]]
   (let [tr (:translator db)]
     {:fx [[:dispatch [:lipas.ui.events/set-active-notification
                       {:message  (tr :notifications/save-failed)
                        :success? false}]]]
      :tracker/event! ["error" "save-loi-failure"]})))

(re-frame/reg-event-fx
 ::search
 (fn [{:keys [db]} _]
   {:http-xhrio
    {:method          :post
     :params          {:size  1000
                       :query {:match_all {}}}
     :uri             (str (:backend-url db) "/actions/search-lois")
     #_#_:headers     {:Authorization (str "Token " token)}
     :format          (ajax/json-request-format)
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success      [::search-success]
     :on-failure      [::search-failure]}}))

(re-frame/reg-event-fx
 ::search-success
 (fn [{:keys [db]} [_  data]]
   {:db (assoc-in db [:loi :search-results] data)}))

(re-frame/reg-event-fx
 ::search-failure
 (fn [{:keys [db]} [_ _resp]]
   (let [tr (:translator db)]
     {:fx [[:dispatch [:lipas.ui.events/set-active-notification
                       {:message  (tr :notifications/get-failed)
                        :success? false}]]]
      :tracker/event! ["error" "search-loi-failure"]})))
