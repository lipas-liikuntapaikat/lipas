(ns lipas.ui.admin.events
  (:require [ajax.core :as ajax]
            [clojure.spec.alpha :as s]
            [lipas.roles :as roles]
            [lipas.schema.core]
            [lipas.ui.utils :as utils]
            [re-frame.core :as rf]))

(rf/reg-event-db
 ::filter-users
 (fn [db [_ s]]
   (assoc-in db [:admin :users-filter] s)))

(rf/reg-event-db
 ::select-status
 (fn [db [_ s]]
   (assoc-in db [:admin :users-status] s)))

(rf/reg-event-db
 ::get-users-success
 (fn [db [_ users]]
   (assoc-in db [:admin :users] (utils/index-by :id users))))

(rf/reg-event-fx
 ::failure
 (fn [{:keys [db]} [_ resp]]
   (let [tr (:translator db)]
     {:dispatch [:lipas.ui.events/set-active-notification
                 {:message  (or (-> resp :response :message)
                                (-> resp :response :error)
                                (tr :error/unknown))
                  :success? false}]})))

(rf/reg-event-fx
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

(rf/reg-event-db
 ::display-user
 (fn [db [_ {:keys [id]}]]
   (assoc-in db [:admin :selected-user] id)))

(rf/reg-event-db
 ::set-user-to-edit
 (fn [db [_ {:keys [id]}]]
   (assoc-in db
             [:admin :editing-user]
             (when id
               (update-in (get-in db [:admin :users id]) [:permissions :roles] roles/conform-roles)))))

(rf/reg-event-db
 ::edit-user
 (fn [db [_ path value]]
   (assoc-in db (into [:admin :editing-user] path) value)))

(rf/reg-event-db ::set-new-role
  (fn [db [_ role]]
    (let [allowed-keys (set (concat [:role]
                                    (:required-context-keys (get roles/roles (:value role)))
                                    (:optional-context-keys (get roles/roles (:value role)))))]
      (update-in db [:admin :new-role] (fn [x]
                                         (-> (if role
                                               (assoc x :role role)
                                               (dissoc x :role))
                                             (select-keys allowed-keys)))))))

(rf/reg-event-db ::set-role-context-value
  (fn [db [_ k value]]
    (let [idx (:edit-role (:admin db))
          path (if idx
                 [:admin :editing-user :permissions :roles idx]
                 [:admin :new-role])]
      (if value
        (update-in db path assoc k value)
        (update-in db path dissoc k)))))

(rf/reg-event-db ::add-new-role
  (fn [db _]
    (let [role (:new-role (:admin db))]
      (if (s/valid? :lipas.user.permissions.roles/role role)
        (-> db
            (update-in [:admin :editing-user :permissions :roles] conj role)
            (update-in [:admin] dissoc :new-role))
        db))))

(rf/reg-event-db ::remove-role
  (fn [db [_ role]]
    (update-in db [:admin :editing-user :permissions :roles]
               (fn [roles]
                 (into (empty roles)
                       (remove #(= role %) roles))))))

(rf/reg-event-db ::edit-role
  (fn [db [_ idx]]
    (assoc-in db [:admin :edit-role] idx)))

(rf/reg-event-db ::stop-edit
  (fn [db _]
    (update db :admin dissoc :edit-role)))

(rf/reg-event-db
 ::grant-access-to-activity-types
 (fn [db _]
   (let [activities (-> db :admin :editing-user :permissions :activities)
         types (-> db
                   :sports-sites
                   :activities
                   :data
                   (select-keys activities)
                   vals
                   (->> (mapcat :type-codes)))]
     (assoc-in db [:admin :editing-user :permissions :types] types))))

(rf/reg-event-fx
 ::save-user-success
 (fn [{:keys [db]} [_ user _]]
   (let [tr (:translator db)]
     {:db       (assoc-in db [:admin :users (:id user)] user)
      :dispatch [:lipas.ui.events/set-active-notification
                 {:message  (tr :notifications/save-success)
                  :success? true}]})))

(rf/reg-event-fx
 ::save-user
 (fn [{:keys [db]} [_ user]]
   (let [token (-> db :user :login :token)
         body  (-> user
                   (select-keys [:id :permissions])
                   (assoc :login-url (str (utils/base-url) "/#/kirjaudu")))]
     {:http-xhrio
      {:method          :post
       :uri             (str (:backend-url db) "/actions/update-user-permissions")
       :headers         {:Authorization (str "Token " token)}
       :params          body
       :format          (ajax/json-request-format)
       :response-format (ajax/json-response-format {:keywords? true})
       :on-success      [::save-user-success user]
       :on-failure      [::failure]}})))

(rf/reg-event-fx
 ::update-user-status
 (fn [{:keys [db]} [_ user status]]
   (let [token (-> db :user :login :token)
         body  {:id (:id user) :status status}]
     {:http-xhrio
      {:method          :post
       :uri             (str (:backend-url db) "/actions/update-user-status")
       :headers         {:Authorization (str "Token " token)}
       :params          body
       :format          (ajax/json-request-format)
       :response-format (ajax/json-response-format {:keywords? true})
       :on-success      [::save-user-success (assoc user :status status)]
       :on-failure      [::failure]}})))

(rf/reg-event-fx
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

(rf/reg-event-db
 ::open-magic-link-dialog
 (fn [db [_ _]]
   (assoc-in db [:admin :magic-link-dialog-open?] true)))

(rf/reg-event-db
 ::close-magic-link-dialog
 (fn [db [_ _]]
   (assoc-in db [:admin :magic-link-dialog-open?] false)))

(rf/reg-event-db
 ::select-magic-link-variant
 (fn [db [_ v]]
   (assoc-in db [:admin :selected-magic-link-variant] v)))

(rf/reg-event-db
 ::select-color
 (fn [db [_ type-code k v]]
   (assoc-in db [:admin :color-picker type-code k] v)))

(rf/reg-event-db
 ::select-tab
 (fn [db [_ v]]
   (assoc-in db [:admin :selected-tab] v)))

(rf/reg-event-fx
 ::download-new-colors-excel
 (fn [{:keys [db]} _]
   (let [headers [[:type-code "type-code"]
                  [:symbol "symbol"]
                  [:fill "fill"]
                  [:stroke "stroke"]]
         data    (reduce (fn [res [k v] ]
                           (conj res (assoc v :type-code k)))
                         []
                         (-> db :admin :color-picker))
         config  {:filename "lipas_symbols"
                  :sheet
                  {:data (utils/->excel-data headers data)}}]
     {:lipas.ui.effects/download-excel! config})))

(rf/reg-event-fx
 ::gdpr-remove-user
 (fn [{:keys [db]} [_ user]]
   (let [token (-> db :user :login :token)]
     {:http-xhrio
      {:method          :post
       :uri             (str (:backend-url db) "/actions/gdpr-remove-user")
       :headers         {:Authorization (str "Token " token)}
       :params          user
       :format          (ajax/json-request-format)
       :response-format (ajax/json-response-format {:keywords? true})
       :on-success      [::gdpr-remove-user-success]
       :on-failure      [::failure]}})))

(rf/reg-event-fx
 ::gdpr-remove-user-success
 (fn [{:keys [db]} [_ user]]
   (let [tr (:translator db)]
     {:db (assoc-in db [:admin :users (:id user)] user)
      :fx [[:dispatch [:lipas.ui.events/set-active-notification
                       {:message  (tr :notifications/save-success)
                        :success? true}]]
           [:dispatch [::set-user-to-edit user]]
           [:dispatch [::get-users]]]})))
