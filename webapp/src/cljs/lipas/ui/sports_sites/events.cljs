(ns lipas.ui.sports-sites.events
  (:require cljsjs.filesaverjs
            [ajax.core :as ajax]
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
   (utils/discard-edits db lipas-id)))

(defn- commit-ajax [db data]
  (let [token  (-> db :user :login :token)]
    {:http-xhrio
     {:method          :post
      :headers         {:Authorization (str "Token " token)}
      :uri             (str (:backend-url db) "/sports-sites")
      :params          data
      :format          (ajax/json-request-format)
      :response-format (ajax/json-response-format {:keywords? true})
      :on-success      [::save-success]
      :on-failure      [::save-failure]}}))

(re-frame/reg-event-fx
 ::commit-rev
 (fn [{:keys [db]} [_ rev]]
   (merge
    (commit-ajax db rev)
    {:db (utils/save-edits db rev)})))

(defn- save-with-status [db lipas-id status]
  (let [rev    (-> (get-in db [:sports-sites lipas-id :editing])
                   utils/make-saveable
                   (assoc :status status))
        db     (utils/save-edits db rev) ;; Store temp state client side
        dirty? (some? (get-in db [:sports-sites lipas-id :edits]))]
    (merge
     {:db db}
     (if dirty?
       (commit-ajax db rev) ;; Attempt to save server side
       {:dispatch [::save-success rev]}))))

(re-frame/reg-event-fx
 ::save-edits
 (fn [{:keys [db]} [_ lipas-id]]
   (save-with-status db lipas-id "active")))

(re-frame/reg-event-fx
 ::save-draft
 (fn [{:keys [db]} [_ lipas-id]]
   (merge
    (save-with-status db lipas-id "draft"))))

(re-frame/reg-event-fx
 ::save-success
 (fn [{:keys [db]} [_ result]]
   (let [tr     (:translator db)
         status (:status result)
         type   (-> result :type :type-code)
         year   (dec utils/this-year)]
     {:db         (utils/commit-edits db result) ;; Clear client side temp state
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
     :uri             (str (:backend-url db) "/sports-sites/history/" lipas-id)
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success      [::get-success]
     :on-failure      [::get-failure]}}))

(re-frame/reg-event-fx
 ::download-contacts-report
 (fn [{:keys [db]} [_ data headers]]
   (let [tr       (:translator db)
         filename (str (tr :reports/contacts) ".csv")
         mime     (str "text/plain;charset=" (.-characterSet js/document))
         blob     (new js/Blob
                       [(utils/->csv data headers)]
                       (clj->js {:type mime}))
         _        (js/saveAs blob filename)]
     {})))
