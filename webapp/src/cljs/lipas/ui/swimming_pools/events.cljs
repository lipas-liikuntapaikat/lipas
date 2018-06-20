(ns lipas.ui.swimming-pools.events
  (:require [ajax.core :as ajax]
            [re-frame.core :as re-frame]
            [lipas.ui.db :refer [default-db]]
            [lipas.ui.utils :refer [save-entity ->indexed-map]]))

(defn make-editable [swimming-pool]
  (-> swimming-pool
      (update-in [:pools] ->indexed-map)
      (update-in [:renovations] ->indexed-map)
      (update-in [:saunas] ->indexed-map)
      (update-in [:slides] ->indexed-map)))

(re-frame/reg-event-db
 ::set-active-tab
 (fn [db [_ active-tab]]
   (assoc-in db [:swimming-pools :active-tab] active-tab)))

(re-frame/reg-event-db
 ::set-edit-site
 (fn [db [_ site]]
   (-> db
       (assoc-in [:swimming-pools :editing :site] site)
       (assoc-in [:swimming-pools :editing :lipas-id] (:lipas-id site)))))

(re-frame/reg-event-db
 ::set-edit-rev
 (fn [db [_ rev]]
   (assoc-in db [:swimming-pools :editing :rev] (make-editable rev))))

(re-frame/reg-event-db
 ::set-field
 (fn [db [_ path value]]
   (assoc-in db (into [:swimming-pools] path) value)))

(re-frame/reg-event-db
 ::toggle-dialog
 (fn [db [_ dialog data]]
   (let [data (or data (-> db :swimming-pools :dialogs dialog :data))]
     (-> db
         (update-in [:swimming-pools :dialogs dialog :open?] not)
         (assoc-in [:swimming-pools :dialogs dialog :data] data)))))

(re-frame/reg-event-db
 ::save-renovation
 (fn [db [_ value]]
   (let [path [:swimming-pools :editing :rev :renovations]]
     (save-entity db path value))))

(re-frame/reg-event-db
 ::save-sauna
 (fn [db [_ value]]
   (let [path [:swimming-pools :editing :rev :saunas]]
     (save-entity db path value))))

(re-frame/reg-event-db
 ::save-pool
 (fn [db [_ value]]
   (let [path [:swimming-pools :editing :rev :pools]]
     (save-entity db path value))))

(re-frame/reg-event-db
 ::save-slide
 (fn [db [_ value]]
   (let [path [:swimming-pools :editing :rev :slides]]
     (save-entity db path value))))

(re-frame/reg-event-db
 ::remove-renovation
 (fn [db [_ {:keys [id]}]]
   (update-in db [:swimming-pools :editing :rev :renovations] dissoc id)))

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
