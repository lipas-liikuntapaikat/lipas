(ns lipas.ui.org.events 
  (:require [ajax.core :as ajax]
            [re-frame.core :as rf]))

(rf/reg-event-db ::get-user-orgs-success
  (fn [db [_ resp]]
    (assoc-in db [:user :orgs] resp)))

(rf/reg-event-fx ::get-user-orgs
  (fn [{:keys [db]} _]
    (let [token (-> db :user :login :token)]
      {:http-xhrio
       {:method          :get
        :uri             (str (:backend-url db) "/current-user-orgs")
        :headers         {:Authorization (str "Token " token)}
        :format          (ajax/json-request-format)
        :response-format (ajax/json-response-format {:keywords? true})
        :on-success      [::get-user-orgs-success]
        :on-failure      [::todo]}})))


(rf/reg-event-db ::get-org-users-success
  (fn [db [_ resp]]
    (assoc-in db [:org :users] resp)))

(rf/reg-event-fx ::get-org-users
  (fn [{:keys [db]} [_ org-id]]
    (let [token (-> db :user :login :token)]
      {:http-xhrio
       {:method          :get
        :uri             (str (:backend-url db) "/orgs/" org-id "/users")
        :headers         {:Authorization (str "Token " token)}
        :format          (ajax/json-request-format)
        :response-format (ajax/json-response-format {:keywords? true})
        :on-success      [::get-org-users-success]
        :on-failure      [::todo]}})))

(rf/reg-event-db ::get-all-users-success
  (fn [db [_ users]]
    (assoc-in db [:org :all-users] users)))

(rf/reg-event-fx ::get-all-users
  (fn [{:keys [db]} [_ _]]
    (let [token (-> db :user :login :token)]
      {:http-xhrio
       {:method          :get
        :headers         {:Authorization (str "Token " token)}
        :uri             (str (:backend-url db) "/users")
        :response-format (ajax/json-response-format {:keywords? true})
        :on-success      [::get-all-users-success]
        :on-failure      [::failure]}})))

(rf/reg-event-fx ::init-view
  (fn [{:keys [db]} [_ org-id]]
    {:fx [[:dispatch [::get-org-users org-id]]
          [:dispatch [::get-all-users]]]
     :db (assoc db :org {:org-id org-id
                         :editing-org (some (fn [o]
                                              (when (= org-id (:id o))
                                                o))
                                            (-> db :user :orgs))})}))

(rf/reg-event-db ::edit-org
  (fn [db [_ path value]]
    (assoc-in db (into [:admin :editing-org] path) value)))

(rf/reg-event-fx ::save-org-success
  (fn [{:keys [db]} [_ _org resp]]
    (let [tr (:translator db)]
      {:fx [[:dispatch [::get-orgs]]
            [:dispatch [:lipas.ui.events/set-active-notification
                        {:message  (tr :notifications/save-success)
                         :success? true}]]]})))

;; Nearly same as in ui.admin.events
(rf/reg-event-fx ::save-org
  (fn [{:keys [db]} [_ org]]
    (let [token (-> db :user :login :token)
          body  (-> org)
          new?  false]
      {:http-xhrio
       {:method          (if new? :post :put)
        :uri             (if new?
                           (str (:backend-url db) "/orgs")
                           (str (:backend-url db) "/orgs/" (:id org)))
        :headers         {:Authorization (str "Token " token)}
        :params          body
        :format          (ajax/json-request-format)
        :response-format (ajax/json-response-format {:keywords? true})
        :on-success      [::save-org-success org]
        :on-failure      [::failure]}})))




(rf/reg-event-fx ::org-user-update
  (fn [{:keys [db]} [_ org-id user-id role change]]
    (let [token (-> db :user :login :token)]
      {:http-xhrio
       {:method          :post
        :uri             (str (:backend-url db) "/orgs/" org-id "/users")
        :headers         {:Authorization (str "Token " token)}
        :params          {:changes [{:user-id user-id
                                     :change change
                                     :role role}]}
        :format          (ajax/json-request-format)
        :response-format (ajax/json-response-format {:keywords? true})
        :on-success      [::get-org-users org-id]
        :on-failure      [::TODO]}})))
