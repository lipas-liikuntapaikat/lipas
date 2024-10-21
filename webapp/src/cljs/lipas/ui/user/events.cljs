(ns lipas.ui.user.events
  (:require [ajax.core :as ajax]
            [lipas.ui.utils :as utils]
            [re-frame.core :as rf]))

(rf/reg-event-fx
  ::get-users-sports-sites
  (fn [{:keys [db]} _]
    (let [roles (-> db :user :login :permissions :roles)]
      {:dispatch-n (->> roles
                        ;; could be multiple roles with sets of lipas-ids
                        (mapcat :lipas-id)
                        (mapv (fn [lipas-id]
                                [:lipas.ui.sports-sites.events/get lipas-id])))})))

(rf/reg-event-fx
  ::select-sports-site
  (fn [_ [_ site]]
    {:dispatch-n
     [[:lipas.ui.events/navigate :lipas.ui.routes.map/details-view site]]}))

(rf/reg-event-fx
  ::update-user-data-success
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

(rf/reg-event-fx
  ::update-user-data-failure
  (fn [{:keys [db]} [_ resp]]
    (let [tr (-> db :translator)]
      {:dispatch [:lipas.ui.events/set-active-notification
                  {:message  (tr :notifications/save-failed)
                   :success? false}]})))

(rf/reg-event-fx
  ::update-user-data
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

(rf/reg-event-fx
  ::select-saved-search
  (fn [_ [_ search]]
    {:dispatch-n
     [[:lipas.ui.search.events/select-saved-search search]
      [:lipas.ui.events/navigate :lipas.ui.routes.map/map]]}))

(rf/reg-event-db
  ::toggle-experimental-features
  (fn [db _]
    (update-in db [:user :experimental-features?] not)))
