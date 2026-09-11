(ns lipas.ui.register.events
  "Self-registration, email first:

    1. The user enters an email; the backend mails a link carrying a signed
       email-verification token (/actions/request-registration).
    2. The link opens this page with ?token=…; the user fills in the rest and
       the account is created (/actions/register). The email comes from the
       token, so the form never sends one.

  The token is decoded here only to show the address and spot an expired link
  early — the backend is what verifies it."
  (:require [ajax.core :as ajax]
            [clojure.string :as str]
            [lipas.ui.db :refer [default-db]]
            [lipas.ui.utils :as utils]
            [re-frame.core :as rf]))

(rf/reg-event-db ::clear-errors
  (fn [db [_ _]]
    (update-in db [:user] dissoc :registration-error)))

;;; Step 1: ask for a link ;;;

(rf/reg-event-fx ::set-request-email
  (fn [{:keys [db]} [_ email]]
    {:db       (assoc-in db [:user :registration-request :email] email)
     :dispatch [::clear-errors]}))

(rf/reg-event-fx ::submit-registration-request
  (fn [{:keys [db]} [_ email]]
    {:db (assoc-in db [:user :registration-request :in-progress?] true)
     :http-xhrio
     {:method          :post
      :uri             (str (:backend-url db) "/actions/request-registration")
      :params          {:email        email
                        :register-url (str (utils/base-url) "/rekisteroidy")
                        ;; The mail goes out in the language the user is using.
                        :lang         (name ((:translator db)))}
      :format          (ajax/json-request-format)
      :response-format (ajax/json-response-format {:keywords? true})
      :on-success      [::registration-request-success email]
      :on-failure      [::registration-request-failure]}}))

(rf/reg-event-fx ::registration-request-success
  (fn [{:keys [db]} [_ email _]]
    {:db             (assoc-in db [:user :registration-request] {:email email :sent-to email})
     :tracker/event! ["user" "registration-link-requested"]}))

(rf/reg-event-db ::registration-request-failure
  (fn [db [_ result]]
    (-> db
        (assoc-in [:user :registration-request :in-progress?] false)
        (assoc-in [:user :registration-error] result))))

(rf/reg-event-db ::edit-request-email
  (fn [db _]
    (update-in db [:user :registration-request] dissoc :sent-to :in-progress?)))

;;; Step 2: the emailed link was opened ;;;

(defn- token-info [token]
  (let [{:keys [email exp purpose]} (try (utils/decode-jwt-payload token)
                                         (catch :default _ nil))]
    (if (and (string? email) (= "email-verification" purpose) (number? exp))
      {:token    token
       :email    email
       :expired? (> (/ (.getTime (js/Date.)) 1000) exp)}
      {:token token :invalid? true})))

(rf/reg-event-db ::init-registration
  (fn [db [_ token]]
    (if (str/blank? token)
      db
      (let [{:keys [email] :as info} (token-info token)]
        (cond-> (assoc-in db [:user :registration-token] info)
          ;; Pre-fill the username from the address, as the old form did.
          email (assoc-in [:user :registration-form :username]
                          (first (str/split email #"@"))))))))

(rf/reg-event-fx ::set-registration-form-field
  (fn [{:keys [db]} [_ path value]]
    (let [path (into [:user :registration-form] path)]
      ;; A cleared field is absent, not "", so an emptied optional field (the
      ;; permissions request) doesn't fail its min-length check.
      {:db       (if (str/blank? value)
                   (update-in db (butlast path) dissoc (last path))
                   (assoc-in db path value))
       :dispatch [::clear-errors]})))

(rf/reg-event-fx ::registration-success
  (fn [{:keys [db]} [_ result]]
    (let [empty-form (-> default-db :user :registration-form)]
      {:db             (-> db
                           (assoc-in [:user :registration] result)
                           (assoc-in [:user :registration-form] empty-form))
       :tracker/event! ["user" "registered"]})))

(rf/reg-event-db ::registration-failure
  (fn [db [_ result]]
    (if (= "invalid-registration-token" (-> result :response :type))
      (assoc-in db [:user :registration-token :invalid?] true)
      (assoc-in db [:user :registration-error] result))))

(rf/reg-event-fx ::submit-registration-form
  (fn [{:keys [db]} [_ form-data]]
    {:http-xhrio
     {:method          :post
      :uri             (str (:backend-url db) "/actions/register")
      :params          (assoc form-data :token (-> db :user :registration-token :token))
      :format          (ajax/json-request-format)
      :response-format (ajax/json-response-format {:keywords? true})
      :on-success      [::registration-success]
      :on-failure      [::registration-failure]}}))

(rf/reg-event-db ::reset-form
  (fn [db _]
    (-> db
        (update-in [:user] dissoc :registration :registration-error
                   :registration-request :registration-token)
        (assoc-in [:user :registration-form] (-> default-db :user :registration-form)))))

(rf/reg-event-fx ::start-over
  (fn [_ _]
    {:dispatch-n [[::reset-form]
                  ;; Drops the dead ?token= from the address bar.
                  [:lipas.ui.events/navigate "/rekisteroidy"]]}))
