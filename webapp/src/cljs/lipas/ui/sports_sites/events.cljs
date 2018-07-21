(ns lipas.ui.sports-sites.events
  (:require [ajax.core :as ajax]
            [lipas.ui.utils :as utils]
            [re-frame.core :as re-frame]))

(re-frame/reg-event-db
 ::edit-field
 (fn [db [_ lipas-id path value]]
   (utils/set-field db (into [:sports-sites lipas-id :editing] path) value)))

(re-frame/reg-event-db
 ::discard-edits
 (fn [db [_ lipas-id]]
   (utils/discard-edits db lipas-id)))

(defn- commit-ajax [db token data]
  {:http-xhrio {:method          :post
                :headers         {:Authorization (str "Token " token)}
                :uri             (str (:backend-url db) "/sports-sites")
                :params          data
                :format          (ajax/json-request-format)
                :response-format (ajax/json-response-format {:keywords? true})
                :on-success      [::commit-success]
                :on-failure      [::commit-failure]}})

(re-frame/reg-event-fx
 ::commit-rev
 (fn [{:keys [db]} [_ rev]]
   (let [token (-> db :user :login :token)]
     (commit-ajax db token rev))))

(re-frame/reg-event-fx
 ::commit-edits
 (fn [{:keys [db]} [_ lipas-id]]
   (let [rev   (utils/latest-edit (get-in db [:sports-sites lipas-id :edits]))
         data  (assoc rev :status "active")
         token (-> db :user :login :token)]
     (commit-ajax db token data))))

(re-frame/reg-event-fx
 ::commit-draft
 (fn [{:keys [db]} [_ lipas-id]]
   (let [rev   (utils/latest-edit (get-in db [:sports-sites lipas-id :edits]))
         token (-> db :user :login :token)
         data  (assoc rev :status "draft")]
     (commit-ajax db token data))))

(re-frame/reg-event-fx
 ::commit-success
 (fn [{:keys [db]} [_ result]]
   (let [tr       (:translator db)]
     {:db       (utils/commit-edits db result)
      :dispatch [:lipas.ui.events/set-active-notification
                 {:message  (tr :notifications/save-success)
                  :success? true}]})))

(re-frame/reg-event-fx
 ::commit-failure
 (fn [{:keys [db]} [_ error]]
   (let [tr       (:translator db)]
     {:db       (assoc-in db [:sports-sites :errors (utils/timestamp)] error)
      :dispatch [:lipas.ui.events/set-active-notification
                 {:message  (tr :notifications/save-failed)
                  :success? false}]})))

(re-frame/reg-event-fx
 ::get-success
 (fn [{:keys [db]} [_ sites]]
   {:db (reduce utils/add-to-db db sites)}))

(re-frame/reg-event-fx
 ::get-failure
 (fn [{:keys [db]} [_ error]]
   (let [tr (:translator db)]
     {:db       (assoc-in db [:sports-sites :errors (utils/timestamp)] error)
      :dispatch [:lipas.ui.events/set-active-notification
                 {:message  (tr :notifications/get-failed)
                  :success? false}]})))

(re-frame/reg-event-fx
 ::get-by-type-code
 (fn [{:keys [db]} [_ type-code]]
   ;; (prn "Get by type-code!")
   {:http-xhrio
    {:method          :get
     :uri             (str (:backend-url db) "/sports-sites/type/" type-code)
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success      [::get-success]
     :on-failure      [::get-failure]}}))

(re-frame/reg-event-fx
 ::get-history
 (fn [{:keys [db]} [_ lipas-id]]
   ;; (prn "Get history!")
   {:http-xhrio
    {:method          :get
     :uri             (str (:backend-url db) "/sports-sites/" lipas-id "/history")
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success      [::get-success]
     :on-failure      [::get-failure]}}))
