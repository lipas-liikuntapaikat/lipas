(ns lipas.ui.admin.ptv-adoption.events
  "PTV adoption admin tab. App-db state lives under [:admin :ptv-adoption ...]."
  (:require [ajax.core :as ajax]
            [lipas.ui.utils :as ui-utils]
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

;;; PTV managers outside their municipality's org ;;;

(rf/reg-event-fx ::fetch-outside-managers
  (fn [{:keys [db]} _]
    {:db (assoc-in db [:admin :ptv-adoption :outside-managers-loading?] true)
     :http-xhrio {:method :post
                  :uri (str (:backend-url db) "/actions/get-ptv-managers-outside-orgs")
                  :params {}
                  :format (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :headers {:Authorization (str "Token " (-> db :user :login :token))}
                  :on-success [::fetch-outside-managers-success]
                  :on-failure [::fetch-outside-managers-failure]}}))

(rf/reg-event-db ::fetch-outside-managers-success
  (fn [db [_ rows]]
    (update-in db [:admin :ptv-adoption] assoc
               :outside-managers rows
               :outside-managers-loading? false)))

(rf/reg-event-db ::fetch-outside-managers-failure
  (fn [db _]
    (update-in db [:admin :ptv-adoption] assoc
               :outside-managers []
               :outside-managers-loading? false)))

;; Plain membership, no roles: which roles a member gets is the org-admin's
;; call (org Members tab). Reuses the org invite endpoint, which adds an
;; existing account directly and emails it a notification.
(rf/reg-event-fx ::add-to-org
  (fn [{:keys [db]} [_ {:keys [email org]}]]
    {:db (assoc-in db [:admin :ptv-adoption :adding email] true)
     :http-xhrio {:method :post
                  :uri (str (:backend-url db) "/actions/invite-org-member")
                  :params {:org-id (:id org)
                           :email email
                           :roles []
                           :login-url (str (ui-utils/base-url) "/#/kirjaudu")}
                  :format (ajax/json-request-format)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :headers {:Authorization (str "Token " (-> db :user :login :token))}
                  :on-success [::add-to-org-done email true]
                  :on-failure [::add-to-org-done email false]}}))

(rf/reg-event-fx ::add-to-org-done
  (fn [{:keys [db]} [_ email ok? _resp]]
    (let [tr (:translator db)]
      {:db (update-in db [:admin :ptv-adoption :adding] dissoc email)
       :fx [[:dispatch [:lipas.ui.events/set-active-notification
                        {:message (tr (if ok? :notifications/save-success :notifications/save-failed))
                         :success? ok?}]]
            [:dispatch [::fetch-outside-managers]]]})))
