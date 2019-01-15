(ns lipas.ui.admin.events
  (:require [ajax.core :as ajax]
            [lipas.ui.utils :as utils]
            [re-frame.core :as re-frame]))

(re-frame/reg-event-db
 ::get-users-success
 (fn [db [_ users]]
   (assoc-in db [:admin :users] (utils/index-by :id users))))

(re-frame/reg-event-fx
 ::failure
 (fn [{:keys [db]} [_ resp]]
   (let [tr (:translator db)]
     {:dispatch [:lipas.ui.events/set-active-notification
                 {:message  (or (-> resp :response :message)
                                (-> resp :response :error)
                                (tr :error/unknown))
                  :success? false}]})))

(re-frame/reg-event-fx
 ::get-users
 (fn [{:keys [db]} [_ _]]
   (let [token (-> db :user :login :token)]
     {:http-xhrio
      {:method          :get
       :headers         {:Authorization (str "Token " token)}
       :uri             (str (:backend-url db) "/users")
       :response-format (ajax/json-response-format {:keywords? true})
       :on-success      [::get-users-success]
       :on-failure      [::failure]}})))

(re-frame/reg-event-db
 ::display-user
 (fn [db [_ {:keys [id]}]]
   (assoc-in db [:admin :selected-user] id)))

(re-frame/reg-event-db
 ::set-user-to-edit
 (fn [db [_ {:keys [id]}]]
   (assoc-in db [:admin :editing-user] (get-in db [:admin :users id]))))

(re-frame/reg-event-db
 ::edit-user
 (fn [db [_ path value]]
   (assoc-in db (into [:admin :editing-user] path) value)))

(re-frame/reg-event-fx
 ::save-user-success
 (fn [{:keys [db]} [_ user _]]
   (let [tr (:translator db)]
     {:db       (assoc-in db [:admin :users (:id user)] user)
      :dispatch [:lipas.ui.events/set-active-notification
                 {:message  (tr :notifications/save-success)
                  :success? true}]})))

(re-frame/reg-event-fx
 ::save-user
 (fn [{:keys [db]} [_ user]]
   (let [token (-> db :user :login :token)]
     {:http-xhrio
      {:method          :post
       :uri             (str (:backend-url db) "/actions/update-user-permissions")
       :headers         {:Authorization (str "Token " token)}
       :params          (select-keys user [:id :permissions])
       :format          (ajax/json-request-format)
       :response-format (ajax/json-response-format {:keywords? true})
       :on-success      [::save-user-success user]
       :on-failure      [::failure]}})))

(re-frame/reg-event-fx
 ::send-magic-link
 (fn [{:keys [db]} [_ user variant]]
   (let [token (-> db :user :login :token)]
     {:http-xhrio
      {:method          :post
       :uri             (str (:backend-url db) "/actions/send-magic-link")
       :headers         {:Authorization (str "Token " token)}
       :params          {:user      user
                         :login-url (str (utils/base-url) "/#/kirjaudu")
                         :variant   variant}
       :format          (ajax/json-request-format)
       :response-format (ajax/json-response-format {:keywords? true})
       :on-success      [::save-user-success user]
       :on-failure      [::failure]}
      :dispatch [::close-magic-link-dialog]})))

(re-frame/reg-event-db
 ::open-magic-link-dialog
 (fn [db [_ _]]
   (assoc-in db [:admin :magic-link-dialog-open?] true)))

(re-frame/reg-event-db
 ::close-magic-link-dialog
 (fn [db [_ _]]
   (assoc-in db [:admin :magic-link-dialog-open?] false)))

(re-frame/reg-event-db
 ::select-magic-link-variant
 (fn [db [_ v]]
   (assoc-in db [:admin :selected-magic-link-variant] v)))
