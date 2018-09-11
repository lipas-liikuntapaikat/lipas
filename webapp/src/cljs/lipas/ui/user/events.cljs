(ns lipas.ui.user.events
  (:require [ajax.core :as ajax]
            [lipas.ui.utils :as utils]
            [re-frame.core :as re-frame]))

(re-frame/reg-event-fx
 ::reset-password-request-success
 (fn [{:keys [db]} [_ _]]
   (let [tr (:translator db)]
     {:dispatch [:lipas.ui.events/set-active-notification
                 {:message  (tr :reset-password/reset-link-sent)
                  :success? true}]
      :db (assoc-in db [:user :reset-password-request :success]
                    :reset-link-sent)
      :ga/event ["user" "reset-password-request"]})))

(re-frame/reg-event-fx
 ::failure
 (fn [{:keys [db]} [_ resp]]
   (let [tr     (:translator db)
         error  (or (-> resp :response :type keyword)
                    (when (= 401 (:status resp)) :reset-token-expired)
                    :unknown)
         fatal? (= error :unknown)]
     {:dispatch     [:lipas.ui.events/set-active-notification
                     {:message  (tr (keyword :error error))
                      :success? false}]
      :db           (assoc-in db [:user :reset-password-request :error] error)
      :ga/exception [(:message resp) fatal?]})))

(re-frame/reg-event-db
 ::clear-feedback
 (fn [db _]
   (assoc-in db [:user :reset-password-request] nil)))

(re-frame/reg-event-fx
 ::send-reset-password-request
 (fn [{:keys [db]} [_ email]]
   {:http-xhrio
    {:method          :post
     :uri             (str (:backend-url db) "/actions/request-password-reset")
     :params          {:email     email
                       :reset-url (str (utils/base-url) "/#/passu-hukassa")}
     :format          (ajax/json-request-format)
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success      [::reset-password-request-success]
     :on-failure      [::failure]}
    :dispatch [::clear-feedback]
    :ga/event ["user" "reset-password-request"]}))

(re-frame/reg-event-fx
 ::reset-password-success
 (fn [{:keys [db]} [_ _]]
   (let [tr (:translator db)]
     {:dispatch-n [[:lipas.ui.events/set-active-notification
                    {:message  (tr :reset-password/reset-success)
                     :success? true}]
                   [:lipas.ui.events/navigate "/#/kirjaudu"]]
      :db         (assoc-in db [:user :reset-password :success]
                            :reset-link-sent)
      :ga/event   ["user" "reset-password-success"]})))


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
     :on-success      [::reset-password-success]
     :on-failure      [::failure]}
    :dispatch [::clear-feedback]}))

(re-frame/reg-event-fx
 ::get-users-sports-sites
 (fn [_ [_ {:keys [permissions]}]]
   ;; TODO Create proper endpoint for fetching sites based on
   ;; permissions. Current implementation fetches all sites from
   ;; backend.
   {:dispatch-n
    [[:lipas.ui.sports-sites.events/get-by-type-code 3110]
     [:lipas.ui.sports-sites.events/get-by-type-code 3130]
     [:lipas.ui.sports-sites.events/get-by-type-code 2510]
     [:lipas.ui.sports-sites.events/get-by-type-code 2520]]}))

(re-frame/reg-event-db
 ::select-sports-site
 (fn [db [_ site]]
   (assoc-in db [:user :selected-sports-site] site)))
