(ns lipas.ui.accessibility.events
  (:require
   [ajax.core :as ajax]
   [lipas.utils :as cutils]
   [re-frame.core :as re-frame]))

(re-frame/reg-event-fx
 ::get-statements
 (fn [{:keys [db]} [_ lipas-id]]
   {:db (assoc-in db [:accessibility :loading?] true)
    :http-xhrio
    {:method          :post
     :params          {:lipas-id lipas-id}
     :uri             (str (:backend-url db) "/actions/get-accessibility-statements")
     :format          (ajax/json-request-format)
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success      [::get-statements-success lipas-id]
     :on-failure      [::get-statements-failure]}}))

(def index-by-lang (partial cutils/index-by (comp keyword :language) :value))

(re-frame/reg-event-fx
 ::get-statements-success
 (fn [{:keys [db]} [_  lipas-id data]]
   (let [data (->> data
                   (map #(update % :sentenceGroups index-by-lang))
                   (map #(update % :sentences (fn [coll]
                                                (group-by (comp keyword :language) coll)))))

         event (if (empty? data) "empty-response" "data-found")]

     {:db             (-> db
                         (assoc-in [:accessibility :statements lipas-id] data)
                         (assoc-in [:accessibility :loading?] false))
      :ga/event       ["accessibility" event "lipas-id" lipas-id]
      :tracker/event! ["accessibility" event "lipas-id" lipas-id]})))

(re-frame/reg-event-fx
 ::get-statements-failure
 (fn [{:keys [db]} [_ resp]]
   (let [fatal? false]
     {:db           (assoc-in db [:accessibility :loading?] false)
      :ga/exception [(:message resp) fatal?]})))

(re-frame/reg-event-fx
 ::get-app-url
 (fn [{:keys [db]} [_ lipas-id]]
   (let [token (-> db :user :login :token)]
     {:http-xhrio
      {:method          :post
       :params          {:lipas-id lipas-id}
       :uri             (str (:backend-url db) "/actions/get-accessibility-app-url")
       :headers         {:Authorization (str "Token " token)}
       :format          (ajax/json-request-format)
       :response-format (ajax/json-response-format {:keywords? true})
       :on-success      [::get-app-url-success lipas-id]
       :on-failure      [::get-app-url-failure]}})))

(re-frame/reg-event-fx
 ::get-app-url-success
 (fn [_ [_ lipas-id data]]
   {:lipas.ui.effects/open-link-in-new-window! (:url data)

    :ga/event       ["accessibility" "app-opened" "lipas-id" lipas-id]
    :tracker/event! ["accessibility" "app-opened" "lipas-id" lipas-id]}))

(re-frame/reg-event-fx
 ::get-app-url-failure
 (fn [_ [_ resp]]
   (let [fatal? false]
     {:ga/exception [(:message resp) fatal?]})))
