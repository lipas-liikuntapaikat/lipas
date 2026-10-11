(ns lipas.ui.admin.ptv-adoption.events
  "PTV adoption admin tab. App-db state lives under [:admin :ptv-adoption ...]."
  (:require [ajax.core :as ajax]
            [re-frame.core :as rf]))

(rf/reg-event-fx ::fetch
  (fn [{:keys [db]} _]
    {:db (-> db
             (assoc-in [:admin :ptv-adoption :loading?] true)
             (assoc-in [:admin :ptv-adoption :error] nil))
     :http-xhrio {:method :post
                  :uri (str (:backend-url db) "/actions/get-ptv-adoption-stats")
                  :params {}
                  :format (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :headers {:Authorization (str "Token " (-> db :user :login :token))}
                  :on-success [::fetch-success]
                  :on-failure [::fetch-failure]}}))

(rf/reg-event-db ::fetch-success
  (fn [db [_ data]]
    (update-in db [:admin :ptv-adoption] assoc :stats data :loading? false)))

(rf/reg-event-db ::fetch-failure
  (fn [db [_ resp]]
    (update-in db [:admin :ptv-adoption] assoc
               :loading? false
               :error (or (-> resp :response :error) (:status-text resp) "Request failed"))))

;;; PTV users (Käyttäjät tab) ;;;

;; The rows are derived from the admin user list (/users, which carries org
;; memberships and org-granted roles) and the PTV orgs.
(rf/reg-event-fx ::fetch-users
  (fn [_ _]
    {:fx [[:dispatch [:lipas.ui.admin.events/get-users]]
          [:dispatch [::fetch-ptv-orgs]]]}))

;; Admins get every PTV-configured org (lipas.backend.ptv.handler/ptv-orgs-for-user).
(rf/reg-event-fx ::fetch-ptv-orgs
  (fn [{:keys [db]} _]
    {:http-xhrio {:method :post
                  :uri (str (:backend-url db) "/actions/get-ptv-orgs")
                  :params {}
                  :format (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :headers {:Authorization (str "Token " (-> db :user :login :token))}
                  :on-success [::fetch-ptv-orgs-success]
                  :on-failure [::fetch-ptv-orgs-success []]}}))

(rf/reg-event-db ::fetch-ptv-orgs-success
  (fn [db [_ orgs]]
    (assoc-in db [:admin :ptv-adoption :ptv-orgs] (vec orgs))))

;; Same column flips the direction; a new column starts ascending.
(rf/reg-event-db ::sort-users
  (fn [db [_ column]]
    (update-in db [:admin :ptv-adoption :users-filter]
               (fn [{:keys [sort-by sort-dir] :as f}]
                 (assoc f
                        :sort-by column
                        :sort-dir (if (and (= column (or sort-by "user"))
                                           (not= "desc" sort-dir))
                                    "desc"
                                    "asc"))))))

(rf/reg-event-db ::set-attention-filter
  (fn [db [_ k v]]
    (assoc-in db [:admin :ptv-adoption :attention-filter k] v)))

(rf/reg-event-db ::set-users-filter
  (fn [db [_ k v]]
    (assoc-in db [:admin :ptv-adoption :users-filter k] v)))

;; Plain membership, no roles: which roles a member gets is the org-admin's
;; call (org Members tab). Silent — no email: for PTV the org only holds the
;; municipality's PTV config and these users never see org views
;; (lipas.backend.ptv.core/add-suggested-ptv-member!).
(rf/reg-event-fx ::add-to-org
  (fn [{:keys [db]} [_ {:keys [user-id org]}]]
    {:db (assoc-in db [:admin :ptv-adoption :adding user-id] true)
     :http-xhrio {:method :post
                  :uri (str (:backend-url db) "/actions/add-suggested-ptv-member")
                  :params {:org-id (:id org)
                           :user-id user-id}
                  :format (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :headers {:Authorization (str "Token " (-> db :user :login :token))}
                  :on-success [::add-to-org-done user-id true]
                  :on-failure [::add-to-org-done user-id false]}}))

(rf/reg-event-fx ::add-to-org-done
  (fn [{:keys [db]} [_ user-id ok? _resp]]
    (let [tr (:translator db)]
      {:db (update-in db [:admin :ptv-adoption :adding] dissoc user-id)
       :fx [[:dispatch [:lipas.ui.events/set-active-notification
                        {:message (tr (if ok? :notifications/save-success :notifications/save-failed))
                         :success? ok?}]]
            [:dispatch [:lipas.ui.admin.events/get-users]]]})))
