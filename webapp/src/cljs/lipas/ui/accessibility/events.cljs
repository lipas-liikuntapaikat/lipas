(ns lipas.ui.accessibility.events
  (:require
   [ajax.core :as ajax]
   [lipas.utils :as cutils]
   [re-frame.core :as re-frame]))

(re-frame/reg-event-fx
 ::get-statements
 (fn [{:keys [db]} [_ lipas-id]]
   {:http-xhrio
    {:method          :post
     :params          {:lipas-id lipas-id}
     :uri             (str (:backend-url db) "/actions/get-accessibility-statements")
     :format          (ajax/json-request-format)
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success      [::get-statements-success lipas-id]
     :on-failure      [::get-statements-failure]}}))

(def index-by-lang (partial cutils/index-by (comp keyword :language) :value))

(re-frame/reg-event-db
 ::get-statements-success
 (fn [db [_  lipas-id data]]
   (let [data (->> data
                   (map #(update % :sentenceGroups index-by-lang))
                   (map #(update % :sentences (fn [coll]
                                                (group-by (comp keyword :language) coll)))))]
     (assoc-in db [:accessibility :statements lipas-id] data))))

(re-frame/reg-event-fx
 ::get-statements-failure
 (fn [_ [_ resp]]
   (let [fatal? false]
     {:ga/exception [(:message resp) fatal?]})))

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
       :on-success      [::get-app-url-success]
       :on-failure      [::get-app-url-failure]}})))

(re-frame/reg-event-fx
 ::get-app-url-success
 (fn [_ [_ data]]
   {:lipas.ui.effects/open-link-in-new-window! (:url data)}))

(re-frame/reg-event-fx
 ::get-app-url-failure
 (fn [_ [_ resp]]
   (let [fatal? false]
     {:ga/exception [(:message resp) fatal?]})))
