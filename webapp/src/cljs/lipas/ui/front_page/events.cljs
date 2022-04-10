(ns lipas.ui.front-page.events
  (:require
   [ajax.core :as ajax]   
   [re-frame.core :as re-frame]))

(re-frame/reg-event-fx
 ::get-newsletter
 (fn [{:keys [db]}]
   {:db (assoc-in db [:front-page :newsletter :in-progress?] true)
    :http-xhrio
    {:method          :post
     :uri             (str (:backend-url db) "/actions/get-newsletter")
     :format          (ajax/transit-request-format)
     :response-format (ajax/transit-response-format)
     :on-success      [::get-newsletter-success]
     :on-failure      [::get-newsletter-failure]}}))

(re-frame/reg-event-fx
 ::get-newsletter-success
 (fn [{:keys [db]} [_ resp]]   
   {:db (-> db
            (assoc-in [:front-page :newsletter :in-progress?] false)
            (assoc-in [:front-page :newsletter :data] resp))}))

(re-frame/reg-event-fx
 ::get-newsletter-failure
 (fn [{:keys [db]} [_ resp]]
   {:db (-> db
            (assoc-in [:front-page :newsletter :in-progress?] false)
            (assoc-in [:front-page :newsletter :error] resp))}))

(re-frame/reg-event-fx
 ::subscribe-newsletter
 (fn [{:keys [db]} [_ params]]
   { :http-xhrio
    {:method          :post
     :uri             (str (:backend-url db) "/actions/subscribe-newsletter")
     :params          params
     :format          (ajax/transit-request-format)
     :response-format (ajax/transit-response-format)
     :on-success      [::subscribe-newsletter-success]
     :on-failure      [::subscribe-newsletter-failure]}}))

(re-frame/reg-event-fx
 ::subscribe-newsletter-success
 (fn [{:keys [db ]} [_ resp]]
   (let [tr (-> db :translator)]
     {:dispatch-n
      [[:lipas.ui.events/set-active-notification
        {:message  (tr :newsletter/subscription-success)
         :success? true}]]})))

(re-frame/reg-event-fx
 ::subscribe-newsletter-failure
 (fn [{:keys [db ]} [_ resp]]
   (let [tr (-> db :translator)]
     {:dispatch-n
      [[:lipas.ui.events/set-active-notification
        {:message  (tr :newsletter/subscription-failed)
         :success? false}]]})))
