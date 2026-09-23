(ns lipas.ui.email-change.events
  "Email change, step 2: the page the confirmation link opens. The token in
  the link is the credential, so this works in any browser, logged in or not."
  (:require [ajax.core :as ajax]
            [clojure.string :as str]
            [re-frame.core :as rf]))

(rf/reg-event-fx ::confirm
  (fn [{:keys [db]} [_ token]]
    (if (str/blank? token)
      {:db (assoc db :email-change-confirmation {:status :error})}
      {:db (assoc db :email-change-confirmation {:status :pending})
       :http-xhrio
       {:method          :post
        :uri             (str (:backend-url db) "/actions/confirm-email-change")
        :params          {:token token}
        :format          (ajax/json-request-format)
        :response-format (ajax/json-response-format {:keywords? true})
        :on-success      [::confirm-success]
        :on-failure      [::confirm-failure]}})))

(rf/reg-event-fx ::confirm-success
  (fn [{:keys [db]} _]
    {:db             (assoc db :email-change-confirmation {:status :done})
     :tracker/event! ["user" "email-changed"]}))

(rf/reg-event-db ::confirm-failure
  (fn [db [_ resp]]
    (assoc db :email-change-confirmation {:status :error
                                          :type   (-> resp :response :type)})))

(rf/reg-event-fx ::go-to-login
  (fn [_ _]
    ;; Confirming revoked every session of the account, this browser's
    ;; included, so start from a clean login.
    {:dispatch [:lipas.ui.login.events/logout]}))
