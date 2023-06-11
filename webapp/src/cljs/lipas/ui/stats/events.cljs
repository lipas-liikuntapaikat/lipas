(ns lipas.ui.stats.events
  (:require
   [re-frame.core :as re-frame]))

(re-frame/reg-event-fx
 ::navigate
 (fn [_ [_ v]]
   (let [route (keyword :lipas.ui.routes.stats v)]
     {:dispatch [:lipas.ui.events/navigate route]})))

(re-frame/reg-event-db
 ::select-tab
 (fn [db [_ v]]
   (assoc-in db [:stats :selected-tab] v)))

(re-frame/reg-event-fx
 ::report-failure
 (fn [{:keys [db]} [_ _error]]
   (let [tr     (-> db :translator)]
     {:tracker/event! ["error" "stats-report-failed"]
      :dispatch       [:lipas.ui.events/set-active-notification
                       {:message  (tr :notifications/get-failed)
                        :success? false}]})))
