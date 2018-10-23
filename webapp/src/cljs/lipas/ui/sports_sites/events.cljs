(ns lipas.ui.sports-sites.events
  (:require [ajax.core :as ajax]
            [lipas.ui.utils :as utils]
            [re-frame.core :as re-frame]))

(re-frame/reg-event-db
 ::edit-site
 (fn [db [_ lipas-id]]
   (let [site (get-in db [:sports-sites lipas-id])
         rev  (-> (utils/make-revision site (utils/timestamp))
                  (utils/make-editable))]
     (-> db
         (assoc-in [:sports-sites lipas-id :editing] rev)))))

(re-frame/reg-event-db
 ::edit-field
 (fn [db [_ lipas-id path value]]
   (utils/set-field db (into [:sports-sites lipas-id :editing] path) value)))

(re-frame/reg-event-db
 ::discard-edits
 (fn [db [_ lipas-id]]
   (assoc-in db [:sports-sites lipas-id :editing] nil)))

(defn- commit-ajax [db data draft?]
  (let [token  (-> db :user :login :token)
        params (when draft? "?draft=true")]
    {:http-xhrio
     {:method          :post
      :headers         {:Authorization (str "Token " token)}
      :uri             (str (:backend-url db) (str "/sports-sites" params))
      :params          data
      :format          (ajax/json-request-format)
      :response-format (ajax/json-response-format {:keywords? true})
      :on-success      [::save-success]
      :on-failure      [::save-failure]}}))

(defn- dirty? [db rev]
  (let [lipas-id  (:lipas-id rev)
        site      (get-in db [:sports-sites lipas-id])
        year      (utils/resolve-year (:event-date rev))
        timestamp (if (utils/this-year? year)
                    (:latest site)
                    (-> (utils/latest-by-year (:history site))
                        (get year)))
        latest    (get-in site [:history timestamp])]
    (utils/different? rev latest)))

(re-frame/reg-event-fx
 ::commit-rev
 (fn [{:keys [db]} [_ rev draft?]]
   (if (dirty? db rev)
     (commit-ajax db rev draft?)
     {:dispatch [::save-success rev]})))

(re-frame/reg-event-fx
 ::save-edits
 (fn [{:keys [db]} [_ lipas-id]]
   (let [rev    (-> (get-in db [:sports-sites lipas-id :editing])
                    utils/make-saveable)
         draft? false]
     (if (dirty? db rev)
       (commit-ajax db rev draft?)
       {:dispatch [::save-success rev]}))))

(re-frame/reg-event-fx
 ::save-draft
 (fn [{:keys [db]} [_ lipas-id]]
   (let [rev    (-> (get-in db [:sports-sites lipas-id :editing])
                    utils/make-saveable)
         draft? true]
     (if (dirty? db rev)
       (commit-ajax db rev draft?)
       {:dispatch [::save-success rev]}))))

(defn- add-to-db [db {:keys [lipas-id event-date] :as rev}]
  (let [new-db (assoc-in db [:sports-sites lipas-id :history event-date] rev)]
    (if (utils/latest? rev (get-in db [:sports-sites lipas-id :history]))
      (assoc-in new-db [:sports-sites lipas-id :latest] event-date)
      new-db)))

(re-frame/reg-event-fx
 ::save-success
 (fn [{:keys [db]} [_ result]]
   (let [tr       (:translator db)
         status   (:status result)
         type     (-> result :type :type-code)
         lipas-id (:lipas-id result)
         year     (dec utils/this-year)]
     {:db         (-> db
                      (add-to-db result)
                      (assoc-in [:sports-sites lipas-id :editing] nil))
      :dispatch-n [[:lipas.ui.events/set-active-notification
                    {:message  (tr :notifications/save-success)
                     :success? true}]
                   (when (#{2510 2520 3110 3130} type)
                     [:lipas.ui.energy.events/fetch-energy-report year type])]
      :ga/event   ["save-sports-site" status type]})))

(re-frame/reg-event-fx
 ::save-failure
 (fn [{:keys [db]} [_ error]]
   (let [tr     (:translator db)
         fatal? false]
     {:db           (assoc-in db [:sports-sites :errors (utils/timestamp)] error)
      :dispatch     [:lipas.ui.events/set-active-notification
                     {:message  (tr :notifications/save-failed)
                      :success? false}]
      :ga/exception [(:message error) fatal?]})))

(re-frame/reg-event-fx
 ::get-success
 (fn [{:keys [db]} [_ sites]]
   {:db (reduce add-to-db db sites)}))

(re-frame/reg-event-fx
 ::get-failure
 (fn [{:keys [db]} [_ error]]
   (let [tr (:translator db)]
     {:db       (assoc-in db [:errors :sports-sites (utils/timestamp)] error)
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
     :uri             (str (:backend-url db) "/sports-sites/history/" lipas-id)
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success      [::get-success]
     :on-failure      [::get-failure]}}))

(re-frame/reg-event-fx
 ::download-contacts-report
 (fn [{:keys [db]} [_ data headers]]
   (let [tr     (:translator db)
         config {:filename (tr :reports/contacts)
                 :sheet
                 {:data (utils/->excel-data headers data)}}]
     {:lipas.ui.effects/download-excel! config})))
