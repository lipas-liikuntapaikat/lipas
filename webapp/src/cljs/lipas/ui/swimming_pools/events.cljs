(ns lipas.ui.swimming-pools.events
  (:require [ajax.core :as ajax]
            [lipas.ui.utils :as utils]
            [lipas.ui.swimming-pools.utils :as swim-utils]
            [re-frame.core :as re-frame]))

(re-frame/reg-event-db
 ::set-active-tab
 (fn [db [_ active-tab]]
   (assoc-in db [:swimming-pools :active-tab] active-tab)))

(re-frame/reg-event-db
 ::select-energy-consumption-site
 (fn [db [_ {:keys [lipas-id]}]]
   (assoc-in db [:swimming-pools :editing :site] lipas-id)))

(re-frame/reg-event-db
 ::select-energy-consumption-year
 (fn [db [_ year]]
   (let [lipas-id (-> db :swimming-pools :editing :site)
         site     (get-in db [:sports-sites lipas-id])
         rev      (or (utils/find-revision site year)
                      (utils/make-revision site (utils/->timestamp year)))
         rev      (swim-utils/make-editable rev)]
     (-> db
         (assoc-in [:swimming-pools :editing :year] year)
         (assoc-in [:swimming-pools :editing :rev] rev)))))

(re-frame/reg-event-fx
 ::commit-energy-consumption
 (fn [{:keys [db]} [_ rev]]
   (let [tr  (:translator db)
         rev (swim-utils/make-saveable rev)]
     {:db       (utils/commit-energy-consumption db rev)
      :dispatch [:lipas.ui.events/set-active-notification
                 {:message  (tr :notifications/save-success)
                  :success? true}]})))

(re-frame/reg-event-db
 ::edit-site
 (fn [db [_ {:keys [lipas-id]}]]
   (let [site (get-in db [:sports-sites lipas-id])
         rev  (utils/make-revision site (utils/timestamp))]
     (-> db
         (assoc-in [:swimming-pools :editing :rev] (swim-utils/make-editable rev))
         (assoc-in [:swimming-pools :editing?] true)))))

(re-frame/reg-event-db
 ::save-edits
 (fn [db _]
   (let [rev (-> db :swimming-pools :editing :rev swim-utils/make-saveable)]
     (-> db
         (assoc-in [:swimming-pools :editing?] false)
         (utils/save-edits rev)))))

(re-frame/reg-event-db
 ::discard-edits
 (fn [db _]
   (let [lipas-id (-> db :swimming-pools :editing :rev :lipas-id)]
     (utils/discard-edits db lipas-id))))

(re-frame/reg-event-db
 ::set-field
 (fn [db [_ path value]]
   (utils/set-field db (into [:swimming-pools] path) value)))

;; TODO do ajax request to backend and move this to success handler
(re-frame/reg-event-fx
 ::commit-edits
 (fn [{:keys [db]} _]
   (let [lipas-id (-> db :swimming-pools :editing :rev :lipas-id)
         rev      (utils/latest-edit (get-in db [:sports-sites lipas-id :edits]))
         tr       (:translator db)]
     {:db       (utils/commit-edits db rev)
      :dispatch [:lipas.ui.events/set-active-notification
                 {:message  (tr :notifications/save-success)
                  :success? true}]})))

;; TODO do ajax request to backend and move this to success handler
(re-frame/reg-event-fx
 ::commit-draft
 (fn [{:keys [db]} _]
   (let [lipas-id (-> db :swimming-pools :editing :rev :lipas-id)
         rev      (utils/latest-edit (get-in db [:sports-sites lipas-id :edits]))
         draft    (assoc rev :status :draft)
         tr       (:translator db)]
     {:db       (utils/commit-edits db draft)
      :dispatch [:lipas.ui.events/set-active-notification
                 {:message  (tr :notifications/save-success)
                  :success? true}]})))

(re-frame/reg-event-db
 ::toggle-dialog
 (fn [db [_ dialog data]]
   (let [data (or data (-> db :swimming-pools :dialogs dialog :data))]
     (-> db
         (update-in [:swimming-pools :dialogs dialog :open?] not)
         (assoc-in [:swimming-pools :dialogs dialog :data] data)))))

(re-frame/reg-event-db
 ::save-sauna
 (fn [db [_ value]]
   (let [path [:swimming-pools :editing :rev :saunas]]
     (utils/save-entity db path value))))

(re-frame/reg-event-db
 ::save-pool
 (fn [db [_ value]]
   (let [path [:swimming-pools :editing :rev :pools]]
     (utils/save-entity db path value))))

(re-frame/reg-event-db
 ::save-slide
 (fn [db [_ value]]
   (let [path [:swimming-pools :editing :rev :slides]]
     (utils/save-entity db path value))))

(re-frame/reg-event-db
 ::remove-sauna
 (fn [db [_ {:keys [id]}]]
   (update-in db [:swimming-pools :editing :rev :saunas] dissoc id)))

(re-frame/reg-event-db
 ::remove-pool
 (fn [db [_ {:keys [id]}]]
   (update-in db [:swimming-pools :editing :rev :pools] dissoc id)))

(re-frame/reg-event-db
 ::remove-slide
 (fn [db [_ {:keys [id]}]]
   (update-in db [:swimming-pools :editing :rev :slides] dissoc id)))

(re-frame/reg-event-db
 ::reset-dialog
 (fn [db [_ dialog]]
   (assoc-in db [:swimming-pools :dialogs dialog] {})))

(defn ->auth-token [token]
  (str "Bearer " token))

(re-frame/reg-event-fx
 ::submit
 (fn [{:keys [db]} [_ data]]
   (let [tr    (:translator db)
         token (-> db :user :login :token)]
     {:http-xhrio
      {:method          :post
       :uri             "http://localhost:8090/api/v1/"
       :headers         {:Authorization (->auth-token token)}
       :params          data
       :format          (ajax/json-request-format)
       :response-format (ajax/json-response-format {:keywords? true})
       :on-success      [:lipas.ui.events/set-active-notification
                         {:message  (tr :notifications/save-success)
                          :success? true}]
       :on-failure      [:lipas.ui.events/set-active-notification
                         {:message  (tr :notifications/save-failed)
                          :success? false}]}})))

(re-frame/reg-event-db
 ::display-site
 (fn [db [_ {:keys [lipas-id]}]]
   (assoc-in db [:swimming-pools :display-site] lipas-id)))
