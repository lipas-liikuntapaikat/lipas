(ns lipas.ui.user.events
  (:require [ajax.core :as ajax]
            [lipas.ui.utils :as utils]
            [re-frame.core :as rf]))

(rf/reg-event-fx ::get-users-sports-sites
  (fn [{:keys [db]} _]
    (let [roles (-> db :user :login :permissions :roles)]
      {:dispatch-n (->> roles
                        ;; could be multiple roles with sets of lipas-ids
                        (mapcat :lipas-id)
                        (mapv (fn [lipas-id]
                                [:lipas.ui.sports-sites.events/get lipas-id])))})))

(rf/reg-event-fx ::select-sports-site
  (fn [_ [_ site]]
    {:dispatch-n
     [[:lipas.ui.events/navigate :lipas.ui.routes.map/details-view site]]}))

(rf/reg-event-fx ::update-user-data-success
  (fn [{:keys [db]} [_ resp]]
    (let [tr (-> db :translator)]
      {:db (-> db
               (assoc-in [:user :login :user-data] resp)
               (assoc-in [:analysis :diversity :user-category-presets]
                         (utils/index-by :name (get-in resp [:saved-diversity-settings
                                                             :category-presets]))))
       :dispatch-n
       [[:lipas.ui.login.events/refresh-login]
        [:lipas.ui.events/set-active-notification
         {:message  (tr :notifications/save-success)
          :success? true}]]})))

(rf/reg-event-fx ::update-user-data-failure
  (fn [{:keys [db]} [_ _resp]]
    (let [tr (-> db :translator)]
      {:dispatch [:lipas.ui.events/set-active-notification
                  {:message  (tr :notifications/save-failed)
                   :success? false}]})))

(rf/reg-event-fx ::update-user-data
  (fn [{:keys [db]} [_ user-data]]
    (let [token (-> db :user :login :token)]
      {:http-xhrio
       {:method          :post
        :headers         {:Authorization (str "Token " token)}
        :uri             (str (:backend-url db) "/actions/update-user-data")
        :params          user-data
        :format          (ajax/transit-request-format)
        :response-format (ajax/transit-response-format)
        :on-success      [::update-user-data-success]
        :on-failure      [::update-user-data-failure]}})))

(rf/reg-event-fx ::select-saved-search
  (fn [_ [_ search]]
    {:dispatch-n
     [[:lipas.ui.search.events/select-saved-search search]
      [:lipas.ui.events/navigate :lipas.ui.routes.map/map]]}))

(rf/reg-event-db ::toggle-experimental-features
  (fn [db _]
    (update-in db [:user :experimental-features?] not)))

;;; Email change (self-service) ;;;

(rf/reg-event-db ::open-email-change-dialog
  (fn [db _]
    (assoc-in db [:user :email-change] {:open? true})))

(rf/reg-event-db ::close-email-change-dialog
  (fn [db _]
    (update db :user dissoc :email-change)))

(rf/reg-event-db ::set-new-email
  (fn [db [_ v]]
    (-> db
        (assoc-in [:user :email-change :new-email] v)
        (update-in [:user :email-change] dissoc :error))))

(rf/reg-event-fx ::request-email-change
  (fn [{:keys [db]} [_ new-email]]
    {:db (assoc-in db [:user :email-change :in-progress?] true)
     :http-xhrio
     {:method          :post
      :uri             (str (:backend-url db) "/actions/request-email-change")
      :headers         {:Authorization (str "Token " (-> db :user :login :token))}
      :params          {:new-email   new-email
                        :confirm-url (str (utils/base-url) "/vahvista-sahkoposti")
                        :lang        (name ((:translator db)))}
      :format          (ajax/json-request-format)
      :response-format (ajax/json-response-format {:keywords? true})
      :on-success      [::request-email-change-success new-email]
      :on-failure      [::request-email-change-failure]}}))

(rf/reg-event-db ::request-email-change-success
  (fn [db [_ new-email _]]
    (assoc-in db [:user :email-change] {:open? true :sent-to new-email})))

(rf/reg-event-db ::request-email-change-failure
  (fn [db [_ resp]]
    (-> db
        (assoc-in [:user :email-change :in-progress?] false)
        (assoc-in [:user :email-change :error] (or (-> resp :response :type) "unknown")))))
