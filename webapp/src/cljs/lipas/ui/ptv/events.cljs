(ns lipas.ui.ptv.events
  (:require
   [re-frame.core :as re-frame]
   [ajax.core :as ajax]
   [lipas.ui.utils :as utils]))

(re-frame/reg-event-db
 ::open-dialog
 (fn [db [_ _]]
   (assoc-in db [:ptv :dialog :open?] true)))

(re-frame/reg-event-db
 ::close-dialog
 (fn [db [_ _]]
   (assoc-in db [:ptv :dialog :open?] false)))

(re-frame/reg-event-fx
 ::select-org
 (fn [{:keys [db]} [_ org]]
   {:db (assoc-in db [:ptv :selected-org] org)
    :fx [[:dispatch [::fetch-org-data org]]
         [:dispatch [::fetch-integration-candidates org]]]}))

(comment
  (re-frame/dispatch [::select-org nil])
  )

(re-frame/reg-event-db
 ::select-tab
 (fn [db [_ v]]
   (assoc-in db [:ptv :selected-tab] v)))

(def org-id->params
  {"7b83257d-06ad-4e3b-985d-16a5c9d3fced" ;; Utajärvi
   {:city-codes [889]
    :owners     ["city" "city-main-owner"]}})

(re-frame/reg-event-fx
 ::fetch-integration-candidates
 (fn [{:keys [db]} [_ org]]
   (when org
     (let [token (-> db :user :login :token)]
       {:db (assoc-in db [:ptv :loading-from-lipas :candidates] true)
        :fx [[:http-xhrio
              {:method  :post
               :headers {:Authorization (str "Token " token)}
               :uri     (str (:backend-url db) "/actions/get-ptv-integration-candidates")

               :params          (get org-id->params (:id org))
               :format          (ajax/transit-request-format)
               :response-format (ajax/transit-response-format)
               :on-success      [::fetch-integration-candidates-success (:id org)]
               :on-failure      [::fetch-integration-candidates-failure]}]]}))))

(re-frame/reg-event-fx
 ::fetch-integration-candidates-success
 (fn [{:keys [db]} [_ org-id resp]]
   {:db (-> db
            (assoc-in [:ptv :loading-from-lipas :candidates] false)
            (assoc-in [:ptv :org org-id :data :sports-sites] (utils/index-by :lipas-id resp)))}))

(re-frame/reg-event-fx
 ::fetch-integration-candidates-failure
 (fn [{:keys [db]} [_ resp]]
   (let [tr           (:translator db)
         notification {:message  (tr :notifications/get-failed)
                       :success? false}]
     {:db (-> db
              (assoc-in [:ptv :loading-from-lipas :candidates] false)
              (assoc-in [:ptv :errors :candidates] resp))
      :fx [[:dispatch [:lipas.ui.events/set-active-notification notification]]]})))

(re-frame/reg-event-fx
 ::fetch-org-data
 (fn [{:keys [_db]} [_ org]]
   (when org
     {:fx [[:dispatch [::fetch-integration-candidates org]]
           [:dispatch [::fetch-services org]]
           [:dispatch [::fetch-service-channels org]]
           [:dispatch [::fetch-service-collections org]]]})))


(re-frame/reg-event-fx
 ::fetch-services
 (fn [{:keys [db]} [_ org]]
   (when org
    {:db (assoc-in db [:ptv :loading-from-ptv :services] true)
     :fx [[:http-xhrio
           {:method          :get
            :uri             (str "https://api.palvelutietovaranto.suomi.fi"
                                  "/api/v11/Service/list/organization"
                                  "?organizationId=" (:id org))

            :response-format (ajax/json-response-format {:keywords? true})
            :on-success      [::fetch-services-success (:id org)]
            :on-failure      [::fetch-services-failure]}]]})))

(re-frame/reg-event-fx
 ::fetch-services-success
 (fn [{:keys [db]} [_ org-id resp]]
   {:db (-> db
            (assoc-in [:ptv :loading-from-ptv :services] false)
            (assoc-in [:ptv :org org-id :data :services] (:itemList resp)))}))

(re-frame/reg-event-fx
 ::fetch-services-failure
 (fn [{:keys [db]} [_ resp]]
   (let [tr           (:translator db)
         notification {:message  (tr :notifications/get-failed)
                       :success? false}]
     {:db (-> db
              (assoc-in [:ptv :loading-from-ptv :services] false)
              (assoc-in [:ptv :errors :services] resp))
      :fx [[:dispatch [:lipas.ui.events/set-active-notification notification]]]})))

(re-frame/reg-event-fx
 ::fetch-service-channels
 (fn [{:keys [db]} [_ org]]
   (when org
    {:db (assoc-in db [:ptv :loading-from-ptv :service-channels] true)
     :fx [[:http-xhrio
           {:method          :get
            :uri             (str "https://api.palvelutietovaranto.suomi.fi"
                                  "/api/v11/ServiceChannel/organization/"
                                  (:id org))

            :response-format (ajax/json-response-format {:keywords? true})
            :on-success      [::fetch-service-channels-success (:id org)]
            :on-failure      [::fetch-service-channels-failure]}]]})))

(re-frame/reg-event-fx
 ::fetch-service-channels-success
 (fn [{:keys [db]} [_ org-id resp]]
   {:db (-> db
            (assoc-in [:ptv :loading-from-ptv :service-channels] false)
            (assoc-in [:ptv :org org-id :data :service-channels] (:itemList resp)))}))

(re-frame/reg-event-fx
 ::fetch-service-channels-failure
 (fn [{:keys [db]} [_ resp]]
   (let [tr           (:translator db)
         notification {:message  (tr :notifications/get-failed)
                       :success? false}]
     {:db (-> db
              (assoc-in [:ptv :loading-from-ptv :service-channels] false)
              (assoc-in [:ptv :errors :service-channels] resp))
      :fx [[:dispatch [:lipas.ui.events/set-active-notification notification]]]})))

(re-frame/reg-event-fx
 ::fetch-service-collections
 (fn [{:keys [db]} [_ org]]
   (when org
     {:db (assoc-in db [:ptv :loading-from-ptv :service-collections] true)
      :fx [[:http-xhrio
            {:method :get
             :uri    (str "https://api.palvelutietovaranto.suomi.fi"
                                   "/api/v11/ServiceCollection/organization"
                                   "?organizationId=" (:id org))

             :response-format (ajax/json-response-format {:keywords? true})
             :on-success      [::fetch-service-collections-success (:id org)]
             :on-failure      [::fetch-service-collections-failure]}]]})))

(re-frame/reg-event-fx
 ::fetch-service-collections-success
 (fn [{:keys [db]} [_ org-id resp]]
   {:db (-> db
            (assoc-in [:ptv :loading-from-ptv :service-collections] false)
            (assoc-in [:ptv :org org-id :data :service-collections] (:itemList resp)))}))

(re-frame/reg-event-fx
 ::fetch-service-collections-failure
 (fn [{:keys [db]} [_ resp]]
   (let [tr           (:translator db)
         notification {:message  (tr :notifications/get-failed)
                       :success? false}]
     {:db (-> db
              (assoc-in [:ptv :loading-from-ptv :service-collections] false)
              (assoc-in [:ptv :errors :service-collections] resp))
      :fx [[:dispatch [:lipas.ui.events/set-active-notification notification]]]})))


(re-frame/reg-event-db
 ::toggle-sync-enabled
 (fn [db [_ {:keys [lipas-id]} v]]
   (let [org-id (get-in db [:ptv :selected-org :id])]
     (assoc-in db [:ptv :org org-id :data :sports-sites lipas-id :ptv :sync-enabled] v))))

(re-frame/reg-event-db
 ::select-service
 (fn [db [_ {:keys [lipas-id]} v]]
   (let [org-id (get-in db [:ptv :selected-org :id])]
     (assoc-in db [:ptv :org org-id :data :sports-sites lipas-id :ptv :service-id] v))))

(re-frame/reg-event-db
 ::select-service-channel
 (fn [db [_ {:keys [lipas-id]} v]]
   (let [org-id (get-in db [:ptv :selected-org :id])]
     (assoc-in db [:ptv :org org-id :data :sports-sites lipas-id :ptv :service-channel-id] v))))

(re-frame/reg-event-db
 ::select-service-integration
 (fn [db [_ {:keys [lipas-id]} v]]
   (let [org-id (get-in db [:ptv :selected-org :id])]
     (assoc-in db [:ptv :org org-id :data :sports-sites lipas-id :ptv :service-integration] v))))

(re-frame/reg-event-db
 ::select-service-channel-integration
 (fn [db [_ {:keys [lipas-id]} v]]
   (let [org-id (get-in db [:ptv :selected-org :id])]
     (assoc-in db [:ptv :org org-id :data :sports-sites lipas-id :ptv :service-channel-integration] v))))

(re-frame/reg-event-db
 ::select-descriptions-integration
 (fn [db [_ {:keys [lipas-id]} v]]
   (let [org-id (get-in db [:ptv :selected-org :id])]
     (assoc-in db [:ptv :org org-id :data :sports-sites lipas-id :ptv :descriptions-integration] v))))

(re-frame/reg-event-db
 ::select-service-integration-default
 (fn [db [_ v]]
   (let [org-id (get-in db [:ptv :selected-org :id])]
     (assoc-in db [:ptv :org org-id :default-settings :service-integration] v))))

(re-frame/reg-event-db
 ::select-service-channel-integration-default
 (fn [db [_ v]]
   (let [org-id (get-in db [:ptv :selected-org :id])]
     (assoc-in db [:ptv :org org-id :default-settings :service-channel-integration] v))))

(re-frame/reg-event-db
 ::select-descriptions-integration-default
 (fn [db [_ v]]
   (let [org-id (get-in db [:ptv :selected-org :id])]
     (assoc-in db [:ptv :org org-id :default-settings :descriptions-integration] v))))

(re-frame/reg-event-db
 ::select-integration-interval
 (fn [db [_ v]]
   (let [org-id (get-in db [:ptv :selected-org :id])]
     (assoc-in db [:ptv :org org-id :default-settings :integration-interval] v))))

(re-frame/reg-event-db
 ::set-summary
 (fn [db [_ {:keys [lipas-id]} locale v]]
   (let [org-id (get-in db [:ptv :selected-org :id])]
     (assoc-in db [:ptv :org org-id :data :sports-sites lipas-id :ptv :summary locale] v))))

(re-frame/reg-event-db
 ::set-description
 (fn [db [_ {:keys [lipas-id]} locale v]]
   (let [org-id (get-in db [:ptv :selected-org :id])]
     (assoc-in db [:ptv :org org-id :data :sports-sites lipas-id :ptv :description locale] v))))
