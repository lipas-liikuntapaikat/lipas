(ns lipas.ui.loi.events
  (:require
   [ajax.core :as ajax]
   [lipas.permissions :as permissions]
   [lipas.ui.utils :as utils :refer [==>]]
   [re-frame.core :as re-frame]))

(defn fcoll->loi
  [fcoll]
  (when-let [f1 (-> fcoll :features first)]
    (-> f1
        :properties
        (assoc :geometries fcoll)
        (assoc :id (:id f1)))))

(re-frame/reg-event-db
 ::select-loi
 (fn [db [_ loi-fcoll]]
   (let [loi (fcoll->loi loi-fcoll)]
     (-> db
         (assoc-in [:loi :selected-loi] loi)
         (assoc-in [:loi :editing] loi)
         (assoc-in [:loi :view-mode] :display)))))

(re-frame/reg-event-db
 ::start-adding-new-loi
 (fn [db _]
   (-> db
       (assoc-in [:loi :editing] {:id           (str (random-uuid))
                                  :status       "active"
                                  :loi-category "outdoor-recreation-facilities"})
       (assoc-in [:loi :view-mode] :adding))))

(re-frame/reg-event-fx
 ::start-editing
 (fn [{:keys [db]} _]
   (let [geoms (-> db :loi :selected-loi :geometries)]
     {:db (assoc-in db [:loi :view-mode] :editing)
      :fx [[:dispatch [:lipas.ui.map.events/new-geom-drawn geoms]]]})))

(re-frame/reg-event-db
 ::stop-editing
 (fn [db _]
   (assoc-in db [:loi :view-mode] :display)))

(re-frame/reg-event-db
 ::edit-loi-field
 (fn [db [_ & args]]
   (let [path (into [:loi :editing] (butlast args))
         v    (last args)]
     (assoc-in db path v))))

(re-frame/reg-event-fx
 ::discard-edits
 (fn [{:keys [db]} _]
   (let [tr (-> db :translator)]
     {:dispatch
      [:lipas.ui.events/confirm
       (tr :confirm/discard-changes?)
       (fn []
          (==> [:lipas.ui.map.events/discard-drawing])
         (==> [:lipas.ui.loi.events/stop-editing]))]})))

(re-frame/reg-event-fx
 ::delete
 (fn [{:keys [db]} [_ loi status year]]
   (let [event-date (when (= status "out-of-service-permanently")
                      (if (utils/this-year? year)
                        (utils/timestamp)
                        (utils/->end-of-year year)))
         loi        (assoc loi :status status)]
     {:fx [[:dispatch [::save loi
                       (:geometries loi)
                       event-date]]]})))

(re-frame/reg-event-fx
 ::save
 (fn [{:keys [db]} [_ loi geoms event-date]]
   (let [token (-> db :user :login :token)
         loi   (-> loi
                   (assoc :geometries (or geoms (:geometries loi)))
                   (assoc :event-date (or event-date (utils/timestamp)))
                   (utils/clean))]
     {:http-xhrio
      {:method          :post
       :params          loi
       :uri             (str (:backend-url db) "/actions/save-loi")
       :headers         {:Authorization (str "Token " token)}
       :format          (ajax/json-request-format)
       :response-format (ajax/json-response-format {:keywords? true})
       :on-success      [::save-success loi]
       :on-failure      [::save-failure]}})))

(re-frame/reg-event-fx
 ::save-success
 (fn [{:keys [db]} [_  loi _resp]]
   (let [tr (:translator db)]
     {:fx [[:dispatch [:lipas.ui.events/set-active-notification
                       {:message  (tr :notifications/save-success)
                        :success? true}]]
           [:dispatch [::select-loi loi]]
           [:dispatch [::stop-editing]]
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
   (if (-> db :user :login :permissions permissions/activities?)
     {:http-xhrio
      {:method          :post
       :params          {:location {:lat (get-in db [:map :center-wgs84 :lat])
                                    :lon (get-in db [:map :center-wgs84 :lon])
                                    :distance (get-in db [:map :width])}}
       :uri             (str (:backend-url db) "/actions/search-lois")
       #_#_:headers     {:Authorization (str "Token " token)}
       :format          (ajax/json-request-format)
       :response-format (ajax/json-response-format {:keywords? true})
       :on-success      [::search-success]
       :on-failure      [::search-failure]}}
     {})))

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
