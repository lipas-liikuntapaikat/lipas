(ns lipas.ui.org.events
  (:require [ajax.core :as ajax]
            [lipas.roles :as roles]
            [lipas.ui.bulk-operations.events :as bulk-ops-events]
            [re-frame.core :as rf]
            [reitit.frontend.easy :as rfe]))

(rf/reg-event-db ::get-user-orgs-success
  (fn [db [_ resp]]
    (assoc-in db [:user :orgs] resp)))

(rf/reg-event-fx ::get-user-orgs
  (fn [{:keys [db]} _]
    (let [token (-> db :user :login :token)]
      {:http-xhrio
       {:method :get
        :uri (str (:backend-url db) "/current-user-orgs")
        :headers {:Authorization (str "Token " token)}
        :format (ajax/json-request-format)
        :response-format (ajax/json-response-format {:keywords? true})
        :on-success [::get-user-orgs-success]
        :on-failure [::todo]}})))

(rf/reg-event-db ::get-org-users-success
  (fn [db [_ resp]]
    (assoc-in db [:org :users] resp)))

(rf/reg-event-fx ::get-org-users
  (fn [{:keys [db]} [_ org-id]]
    (let [token (-> db :user :login :token)]
      {:http-xhrio
       {:method :get
        :uri (str (:backend-url db) "/orgs/" org-id "/users")
        :headers {:Authorization (str "Token " token)}
        :format (ajax/json-request-format)
        :response-format (ajax/json-response-format {:keywords? true})
        :on-success [::get-org-users-success]
        :on-failure [::todo]}})))

(rf/reg-event-db ::get-all-users-success
  (fn [db [_ users]]
    (assoc-in db [:org :all-users] users)))

(rf/reg-event-fx ::get-all-users
  (fn [{:keys [db]} [_ _]]
    (let [token (-> db :user :login :token)]
      {:http-xhrio
       {:method :get
        :headers {:Authorization (str "Token " token)}
        :uri (str (:backend-url db) "/users")
        :response-format (ajax/json-response-format {:keywords? true})
        :on-success [::get-all-users-success]
        :on-failure [::failure]}})))

(rf/reg-event-fx ::init-view
  (fn [{:keys [db]} [_ org-id]]
    (let [user-data (get-in db [:user :login])
          is-lipas-admin? (roles/check-privilege user-data {} :users/manage)
          user-orgs (-> db :user :orgs)
          is-new? (= "new" org-id)
          current-org (if is-new?
                        {:name ""
                         :data {:primary-contact {}}
                         :ptv-data {}}
                        (when user-orgs
                          (some (fn [o]
                                  (when (= org-id (str (:id o)))
                                    o))
                                user-orgs)))
          fx (cond-> []
               (not is-new?) (conj [:dispatch [::get-org-users org-id]])
               is-lipas-admin? (conj [:dispatch [::get-all-users]])
               (and (nil? user-orgs) (not is-new?)) (conj [:dispatch [::get-user-orgs-then-init org-id]]))]
      {:fx fx
       :db (assoc db :org {:org-id org-id
                           :editing-org current-org})})))

(rf/reg-event-fx ::get-user-orgs-then-init
  (fn [{:keys [db]} [_ org-id]]
    {:dispatch-n [[::get-user-orgs]
                  [::wait-for-orgs-then-init org-id]]}))

(rf/reg-event-fx ::wait-for-orgs-then-init
  (fn [{:keys [db]} [_ org-id retry-count]]
    (let [user-orgs (-> db :user :orgs)
          retry-count (or retry-count 0)]
      (if (or user-orgs (> retry-count 10))
        ;; If we have orgs or exceeded retries, find the org
        (let [current-org (some (fn [o]
                                  (when (= org-id (str (:id o)))
                                    o))
                                user-orgs)]
          {:db (assoc-in db [:org :editing-org] current-org)})
        ;; Otherwise wait and retry
        {:dispatch-later [{:ms 100
                           :dispatch [::wait-for-orgs-then-init org-id (inc retry-count)]}]}))))

(rf/reg-event-db ::set-add-user-form
  (fn [db [_ path value]]
    (assoc-in db (into [:org :add-user-form] path) value)))

(rf/reg-event-db ::clear-add-user-form
  (fn [db _]
    (assoc-in db [:org :add-user-form] {})))

(rf/reg-event-fx ::add-user-to-org
  (fn [{:keys [db]} [_ org-id]]
    (let [form (get-in db [:org :add-user-form])
          user-id (:user-id form)
          role (:role form)]
      (if (and user-id role)
        {:fx [[:dispatch [::org-user-update org-id user-id role "add"]]
              [:dispatch [::clear-add-user-form]]]}
        {:fx [[:dispatch [:lipas.ui.events/set-active-notification
                          {:message "Valitse käyttäjä ja rooli"
                           :success? false}]]]}))))

(rf/reg-event-db ::set-add-user-email-form
  (fn [db [_ path value]]
    (assoc-in db (into [:org :add-user-email-form] path) value)))

(rf/reg-event-db ::clear-add-user-email-form
  (fn [db _]
    (assoc-in db [:org :add-user-email-form] {})))

(rf/reg-event-fx ::add-user-by-email
  (fn [{:keys [db]} [_ org-id]]
    (let [form (get-in db [:org :add-user-email-form])
          email (:email form)
          role (:role form)]
      (if (and email role)
        (let [token (-> db :user :login :token)]
          {:http-xhrio
           {:method :post
            :uri (str (:backend-url db) "/orgs/" org-id "/add-user-by-email")
            :headers {:Authorization (str "Token " token)}
            :params {:email email :role role}
            :format (ajax/json-request-format)
            :response-format (ajax/json-response-format {:keywords? true})
            :on-success [::add-user-by-email-success org-id]
            :on-failure [::add-user-by-email-failure]}})
        {:fx [[:dispatch [:lipas.ui.events/set-active-notification
                          {:message "Valitse sähköposti ja rooli"
                           :success? false}]]]}))))

(rf/reg-event-fx ::add-user-by-email-success
  (fn [{:keys [db]} [_ org-id resp]]
    (let [tr (:translator db)]
      {:fx [[:dispatch [::get-org-users org-id]]
            [:dispatch [::clear-add-user-email-form]]
            [:dispatch [:lipas.ui.events/set-active-notification
                        {:message (or (:message resp) "Käyttäjä lisätty organisaatioon")
                         :success? true}]]]})))

(rf/reg-event-fx ::add-user-by-email-failure
  (fn [{:keys [db]} [_ resp]]
    (let [error-msg (or (get-in resp [:response :message])
                        "Virhe käyttäjän lisäämisessä")]
      {:fx [[:dispatch [:lipas.ui.events/set-active-notification
                        {:message error-msg
                         :success? false}]]]})))

(rf/reg-event-db ::edit-org
  (fn [db [_ path value]]
    (assoc-in db (into [:org :editing-org] path) value)))

(rf/reg-event-fx ::set-current-tab
  (fn [{:keys [db]} [_ tab]]
    (let [org-id (get-in db [:org :org-id])]
      (cond-> {:db (assoc-in db [:org :current-tab] tab)}
                       ;; Initialize bulk operations when switching to that tab
        (= tab "bulk-operations")
        (assoc :dispatch [::bulk-ops-events/init {}])))))

(rf/reg-event-fx ::save-org-success
  (fn [{:keys [db]} [_ _org new? resp]]
    (let [tr (:translator db)
          base-fx [[:dispatch [::get-user-orgs]]
                   [:dispatch [:lipas.ui.events/set-active-notification
                               {:message (tr :notifications/save-success)
                                :success? true}]]]]
      {:fx (if new?
             (conj base-fx
                   [:dispatch [:lipas.ui.events/navigate :lipas.ui.routes/orgs]])
             base-fx)})))

;; Nearly same as in ui.admin.events
(rf/reg-event-fx ::save-org
  (fn [{:keys [db]} [_ org]]
    (let [token (-> db :user :login :token)
                         ;; Clean up the org data before sending
          body (-> org
                                  ;; Remove old phone field if it exists in data
                   (update :data #(dissoc % :phone)))
          new? (nil? (:id org))]
      {:http-xhrio
       {:method (if new? :post :put)
        :uri (if new?
               (str (:backend-url db) "/orgs")
               (str (:backend-url db) "/orgs/" (:id org)))
        :headers {:Authorization (str "Token " token)}
        :params body
        :format (ajax/json-request-format)
        :response-format (ajax/json-response-format {:keywords? true})
        :on-success [::save-org-success org new?]
        :on-failure [::failure]}})))

(rf/reg-event-fx ::org-user-update
  (fn [{:keys [db]} [_ org-id user-id role change]]
    (let [token (-> db :user :login :token)]
      {:http-xhrio
       {:method :post
        :uri (str (:backend-url db) "/orgs/" org-id "/users")
        :headers {:Authorization (str "Token " token)}
        :params {:changes [{:user-id user-id
                            :change change
                            :role role}]}
        :format (ajax/json-request-format)
        :response-format (ajax/json-response-format {:keywords? true})
        :on-success [::get-org-users org-id]
        :on-failure [::TODO]}})))

;; Bulk operations integration
(rf/reg-event-fx ::init-bulk-operations
  (fn [{:keys [db]} [_ org-id]]
    {:db (assoc-in db [:org :current-org-id] org-id)
     :dispatch [::bulk-ops-events/init {}]}))

 ;; PTV configuration events
(rf/reg-event-fx ::save-ptv-config-success
  (fn [{:keys [db]} [_ resp]]
    (let [tr (:translator db)]
      {:fx [[:dispatch [:lipas.ui.events/set-active-notification
                        {:message "PTV configuration saved successfully"
                         :success? true}]]]})))

(rf/reg-event-fx ::save-ptv-config
  (fn [{:keys [db]} [_]]
    (let [token (-> db :user :login :token)
          org (get-in db [:org :editing-org])
          org-id (:id org)
          ptv-config (:ptv-data org)]
      {:http-xhrio
       {:method :put
        :uri (str (:backend-url db) "/orgs/" org-id "/ptv-config")
        :headers {:Authorization (str "Token " token)}
        :params ptv-config
        :format (ajax/json-request-format)
        :response-format (ajax/json-response-format {:keywords? true})
        :on-success [::save-ptv-config-success]
        :on-failure [::failure]}})))

 ;; Generic failure handler
(rf/reg-event-fx ::failure
  (fn [{:keys [db]} [_ resp]]
    (let [tr (:translator db)]
      {:dispatch [:lipas.ui.events/set-active-notification
                  {:message (or (-> resp :response :message)
                                (-> resp :response :error)
                                (tr :error/unknown))
                   :success? false}]})))

 ;; TODO placeholder handler - logs error for now
(rf/reg-event-fx ::todo
  (fn [_ [_ resp]]
    (js/console.error "Unhandled error response:" resp)
    {}))

(rf/reg-event-fx ::TODO ::todo) ; Alias for consistency
