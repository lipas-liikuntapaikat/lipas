(ns lipas.ui.loi.events
  (:require [ajax.core :as ajax]
            [lipas.roles :as roles]
            [lipas.ui.utils :as utils :refer [==>]]
            [re-frame.core :as rf]))

(rf/reg-event-db
  ::select-loi
  (fn [db [_ loi-id]]
    (let [loi (get-in db [:lois loi-id])]
      (-> db
          (assoc-in [:loi :selected-loi] loi)
          (assoc-in [:loi :editing] loi)
          (assoc-in [:loi :view-mode] :display)))))

(rf/reg-event-db
  ::start-adding-new-loi
  (fn [db _]
    (-> db
        (assoc-in [:loi :editing] {:id           (str (random-uuid))
                                   :status       "active"
                                   :loi-category "outdoor-recreation-facilities"})
        (assoc-in [:loi :view-mode] :adding))))

(rf/reg-event-fx
  ::start-editing
  (fn [{:keys [db]} _]
    (let [geoms (-> db :loi :selected-loi :geometries)]
      {:db (assoc-in db [:loi :view-mode] :editing)
       :fx [[:dispatch [:lipas.ui.map.events/new-geom-drawn geoms]]]})))

(rf/reg-event-db
  ::stop-editing
  (fn [db _]
    (assoc-in db [:loi :view-mode] :display)))

(rf/reg-event-db
  ::edit-loi-field
  (fn [db [_ & args]]
    (let [path (into [:loi :editing] (butlast args))
          v    (last args)]
      (assoc-in db path v))))

(rf/reg-event-fx
  ::discard-edits
  (fn [{:keys [db]} _]
    (let [tr (-> db :translator)]
      {:dispatch
       [:lipas.ui.events/confirm
        (tr :confirm/discard-changes?)
        (fn []
          (==> [:lipas.ui.map.events/discard-drawing])
          (==> [:lipas.ui.loi.events/stop-editing]))]})))

(rf/reg-event-fx
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

(rf/reg-event-fx
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

(rf/reg-event-fx
  ::save-success
  (fn [{:keys [db]} [_  loi _resp]]
    (let [tr (:translator db)]
      {:db (assoc-in db [:lois (:id loi)] loi)
       :fx [[:dispatch [:lipas.ui.events/set-active-notification
                        {:message  (tr :notifications/save-success)
                         :success? true}]]
            [:dispatch [::select-loi (:id loi)]]
            [:dispatch [::stop-editing]]
            [:dispatch [:lipas.ui.map.events/discard-drawing]]
            [:dispatch [::search]]]})))

(rf/reg-event-fx
  ::save-failure
  (fn [{:keys [db]} [_ _resp]]
    (let [tr (:translator db)]
      {:fx [[:dispatch [:lipas.ui.events/set-active-notification
                        {:message  (tr :notifications/save-failed)
                         :success? false}]]]
       :tracker/event! ["error" "save-loi-failure"]})))

(rf/reg-event-fx
  ::search
  (fn [{:keys [db]} _]
   ;; Currently users with activities-manager roles should see/edit LOI data
   ;; The activitier-manager role has context with activity/type-code etc.
   ;; but ignore role-context here because LOI don't even have (site-)type-code or city-code.
   ;; NOTE: This doesn't apply the dev/overrides
    (if (roles/check-privilege (:login (:user db))
                               {:city-code ::roles/any
                                :type-code ::roles/any
                                :activity ::roles/any}
                               :loi/view)
      {:http-xhrio
       {:method          :post
        :params          {:location {:lat (get-in db [:map :center-wgs84 :lat])
                                     :lon (get-in db [:map :center-wgs84 :lon])
                                     :distance (max (-> db :map :width)
                                                    (-> db :map :height))}
                          :loi-statuses (get-in db [:search :filters :statuses])}
        :uri             (str (:backend-url db) "/actions/search-lois")
        #_#_:headers     {:Authorization (str "Token " token)}
        :format          (ajax/json-request-format)
        :response-format (ajax/json-response-format {:keywords? true})
        :on-success      [::search-success]
        :on-failure      [::search-failure]}}
      {})))

(rf/reg-event-fx
  ::search-success
  (fn [{:keys [db]} [_  data]]
    {:db (assoc-in db [:loi :search-results] (map :_source data))}))

(rf/reg-event-fx
  ::search-failure
  (fn [{:keys [db]} [_ _resp]]
    (let [tr (:translator db)]
      {:fx [[:dispatch [:lipas.ui.events/set-active-notification
                        {:message  (tr :notifications/get-failed)
                         :success? false}]]]
       :tracker/event! ["error" "search-loi-failure"]})))

(rf/reg-event-fx
  ::get
  (fn [{:keys [db]} [_ loi-id on-success]]
    {:http-xhrio
     {:method          :get
      :uri             (str (:backend-url db) "/lois/" loi-id)
      :response-format (ajax/transit-response-format)
      :on-success      [::get-success on-success]
      :on-failure      [::get-failure]}}))

(rf/reg-event-fx
  ::get-success
  (fn [{:keys [db]} [_ on-success loi]]
    {:db (assoc-in db [:lois (:id loi)] loi)
     :fx [(when on-success [:dispatch on-success])]}))

(rf/reg-event-fx
  ::get-failure
  (fn [{:keys [db]} [_ error]]
    (let [tr (:translator db)]
      {:db       (assoc-in db [:errors :lois (utils/timestamp)] error)
       :dispatch [:lipas.ui.events/set-active-notification
                  {:message  (tr :notifications/get-failed)
                   :success? false}]})))
