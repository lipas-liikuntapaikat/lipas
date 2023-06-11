(ns lipas.ui.forgot-password.events
  (:require
   [ajax.core :as ajax]
   [lipas.ui.utils :as utils]
   [re-frame.core :as re-frame]))

(re-frame/reg-event-db
 ::clear-feedback
 (fn [db _]
   (assoc-in db [:reset-password] nil)))

(re-frame/reg-event-fx
 ::request-success
 (fn [{:keys [db]} [_ _]]
   (let [tr (:translator db)]
     {:dispatch       [:lipas.ui.events/set-active-notification
                       {:message  (tr :reset-password/reset-link-sent)
                        :success? true}]
      :db             (assoc-in db [:reset-password :success] :reset-link-sent)
      :ga/event       ["user" "reset-password-request"]
      :tracker/event! ["user" "reset-password-request"]})))


(re-frame/reg-event-fx
 ::request-password-reset
 (fn [{:keys [db]} [_ email]]
   {:http-xhrio
    {:method          :post
     :uri             (str (:backend-url db) "/actions/request-password-reset")
     :params          {:email     email
                       :reset-url (str (utils/base-url) "/passu-hukassa")}
     :format          (ajax/json-request-format)
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success      [::request-success]
     :on-failure      [::failure]}
    :dispatch [::clear-feedback]
    :ga/event ["user" "reset-password-request"]}))

(re-frame/reg-event-fx
 ::reset-success
 (fn [{:keys [db]} [_ _]]
   (let [tr (:translator db)]
     {:dispatch-n     [[:lipas.ui.events/set-active-notification
                        {:message  (tr :reset-password/reset-success)
                         :success? true}]
                   [:lipas.ui.events/navigate "/kirjaudu"]]
      :db             (assoc-in db [:reset-password :success] :reset-link-sent)
      :ga/event       ["user" "reset-password-success"]
      :tracker/event! ["user" "reset-password-success"]})))


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
     :on-success      [::reset-success]
     :on-failure      [::failure]}
    :dispatch [::clear-feedback]}))

(re-frame/reg-event-fx
 ::failure
 (fn [{:keys [db]} [_ resp]]
   (let [tr     (:translator db)
         error  (or (-> resp :response :type keyword)
                    (when (= 401 (:status resp)) :reset-token-expired)
                    :unknown)
         fatal? (= error :unknown)]
     {:dispatch       [:lipas.ui.events/set-active-notification
                       {:message  (tr (keyword :error error))
                        :success? false}]
      :db             (assoc-in db [:reset-password :error] error)
      :ga/exception   [(:message resp) fatal?]
      :tracker/event! ["user" "reset-password-failure"]})))
