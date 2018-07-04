(ns lipas.ui.swimming-pools.events
  (:require [ajax.core :as ajax]
            [lipas.ui.db :refer [default-db]]
            [lipas.ui.utils :as utils]
            [re-frame.core :as re-frame]))

(defn make-editable [swimming-pool]
  (-> swimming-pool
      (update-in [:pools] utils/->indexed-map)
      (update-in [:saunas] utils/->indexed-map)
      (update-in [:slides] utils/->indexed-map)))

(defn make-saveable [swimming-pool]
  (-> swimming-pool
      (update-in [:pools] vals)
      (update-in [:saunas] vals)
      (update-in [:slides] vals)))

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
                      (utils/make-revision site (utils/->timestamp year)))]
     (-> db
         (assoc-in [:swimming-pools :editing :year] year)
         (assoc-in [:swimming-pools :editing :rev] rev)))))

(re-frame/reg-event-db
 ::commit-energy-consumption
 (fn [db [_ rev]]
   (let [lipas-id  (-> db :swimming-pools :editing :site)
         timestamp (:timestamp rev)]
     (assoc-in db [:sports-sites lipas-id :history timestamp] rev))))

(re-frame/reg-event-db
 ::edit-site
 (fn [db [_ {:keys [lipas-id]}]]
   (let [site (get-in db [:sports-sites lipas-id])
         rev  (utils/make-revision site (utils/timestamp))]
     (assoc-in db [:swimming-pools :editing :rev] (make-editable rev)))))

(re-frame/reg-event-db
 ::set-field
 (fn [db [_ path value]]
   (assoc-in db (into [:swimming-pools] path) value)))

;; TODO do ajax request to backend and move this to success handler
(re-frame/reg-event-db
 ::commit-edits
 (fn [db _]
   (let [rev      (-> db :swimming-pools :editing :rev make-saveable)
         lipas-id (:lipas-id rev)]
     (-> db
         (assoc-in [:sports-sites lipas-id :latest] rev)
         (assoc-in [:sports-sites lipas-id :history (:timestamp rev)] rev)))))

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
   (let [empty-data (-> default-db :swimming-pools :dialogs dialog)]
     (assoc-in db [:swimming-pools :dialogs dialog] empty-data))))

(defn ->auth-token [token]
  (str "Bearer " token))

(re-frame/reg-event-fx
 ::submit
 (fn [{:keys [db]} [_ data]]
   (let [tr (:translator db)
         token (-> db :user :login :token)]
     {:http-xhrio
      {:method          :post
       :uri             "http://localhost:8090/api/v1/"
       :headers         {:Authorization (->auth-token token)}
       :params          data
       :format          (ajax/json-request-format)
       :response-format (ajax/json-response-format {:keywords? true})
       :on-success      [:lipas.ui.events/set-active-notification
                         {:message (tr :notification/save-success)
                          :success? true}]
       :on-failure      [:lipas.ui.events/set-active-notification
                         {:message (tr :notification/save-failed)
                          :success? false}]}})))

(re-frame/reg-event-db
 ::display-site
 (fn [db [_ {:keys [lipas-id]}]]
   (assoc-in db [:swimming-pools :display-site] lipas-id)))
