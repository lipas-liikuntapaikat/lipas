(ns lipas.ui.login.events
  (:require [ajax.core :as ajax]
            [lipas.roles :as roles]
            [lipas.ui.db :as db]
            [lipas.ui.local-storage :as local-storage]
            [lipas.ui.login.session-expiry :as session-expiry]
            [lipas.ui.org.events]
            [lipas.ui.utils :as utils]
            [re-frame.core :as rf]))

(rf/reg-event-db ::set-field
  (fn [db [_ path value]]
    (let [path (into [:user :login-form] path)]
      (assoc-in db path value))))

(rf/reg-event-db ::select-login-mode
  (fn [db [_ login-mode]]
    (assoc-in db [:user :login-mode] login-mode)))

(rf/reg-event-db ::set-comeback-path
  (fn [db [_ path]]
    (if (= "/kirjaudu" path)
      (assoc db :comeback-path "/profiili")
      (assoc db :comeback-path path))))

(rf/reg-event-fx ::login-success
  (fn [{:keys [db]} [_ login-type body]]
    (let [;; 15 minutes
          refresh-interval-s 900
          body (update-in body [:permissions :roles] roles/conform-roles)]
      (merge
        {:db (-> db
                 (assoc-in [:logged-in?] true)
                 (assoc-in [:user :login] body)
                 (assoc-in [:analysis :diversity :user-category-presets]
                           (utils/index-by :name
                                           (get-in body [:user-data
                                                         :saved-diversity-settings
                                                         :category-presets]))))

         ::local-storage/set! [:login-data body]

         :dispatch-later
         [{:ms (* 1000 refresh-interval-s) :dispatch [::refresh-login]}]

         :dispatch-n
         [(when (= :magic-link login-type) [:lipas.ui.events/navigate "/profiili"])
          (when (not= :refresh login-type)
            [:lipas.ui.search.events/set-logged-in-filters])
          [:lipas.ui.org.events/get-user-orgs]]}

        (when (not= :refresh login-type)
          {:tracker/set-dimension! ["user-type" (if (roles/check-role body :admin)
                                                  "admin"
                                                  "user")]
           :tracker/event!         ["user" "login-success"]})))))

(rf/reg-event-fx ::login-failure
  (fn [{:keys [db]} [_ result]]
    {:db             (assoc-in db [:user :login-error] result)
     :tracker/event! ["user" "login-failed"]}))

(rf/reg-event-db ::clear-errors
  (fn [db [_ _]]
    (update-in db [:user] dissoc :login-error :magic-link-ordered?)))

(rf/reg-event-fx ::submit-login-form
  (fn [{:keys [db]} [_ form-data]]
    {:http-xhrio
     {:method          :post
      :uri             (str (:backend-url db) "/actions/login")
      :headers         {:Authorization (utils/->basic-auth form-data)}
      :format          (ajax/json-request-format)
      :response-format (ajax/json-response-format {:keywords? true})
      :on-success      [::login-success :login]
      :on-failure      [::login-failure]}
     :dispatch    [::clear-errors]}))

(rf/reg-event-fx ::login-refresh-failure
  (fn [_ [_ {:keys [status]}]]
    ;; Delegated to ::session-expired, which owns the whole teardown: the
    ;; impersonation fallback that used to live here, plus the notification.
    ;; The periodic refresh is the path that discovers a dead session most
    ;; often, and it used to log the user out without saying anything.
    (if (#{401 403} status)
      {:dispatch [::session-expired]}
      {})))

(rf/reg-event-fx ::refresh-login
  [(rf/inject-cofx ::local-storage/get :login-data)]
  (fn [{local-storage :local-storage db :db} _]
    (let [login-data (:login-data local-storage)]
      (if (or (empty? login-data) (not (:logged-in? db)))
        {}
        (let [token (-> login-data :token)]
          (if (utils/jwt-expired? token)
            {:dispatch [::logout]}
            {:http-xhrio
             {:method          :get
              :uri             (str (:backend-url db) "/actions/refresh-login")
              :headers         {:Authorization (str "Token " token)}
              :format          (ajax/json-request-format)
              :response-format (ajax/json-response-format {:keywords? true})
              :on-success      [::login-success :refresh]
              :on-failure      [::login-refresh-failure]}}))))))

(rf/reg-event-fx ::login-with-magic-link
  (fn [{db :db} [_ token]]
    {:http-xhrio
     {:method          :get
      :uri             (str (:backend-url db) "/actions/refresh-login")
      :headers         {:Authorization (str "Token " token)}
      :format          (ajax/json-request-format)
      :response-format (ajax/json-response-format {:keywords? true})
      :on-success      [::login-success :magic-link]
      :on-failure      [::logout]}
     :tracker/event! ["user" "magic-link-opened"]}))

(rf/reg-event-fx ::logout
  (fn [{:keys [db]}  _]
    {:db (assoc db/default-db :backend-url (:backend-url db))

     ::local-storage/remove-many! [:login-data :admin-login-data]

     :dispatch               [:lipas.ui.events/navigate "/kirjaudu"]
     :tracker/set-dimension! ["user-type" "guest"]}))

;;; Session expiry ;;;

;; A token can now die mid-session: the backend rejects one minted before the
;; account's `tokens_valid_from`, which an admin sets by changing the user's
;; roles or archiving them. Only `::refresh-login` used to notice, and it runs
;; every 15 minutes — until then every other request answered its 401 with its
;; own generic "epäonnistui" message and the user was left clicking a dead
;; session.
;;
;; A global interceptor rather than re-registering the `:http-xhrio` effect:
;; that key belongs to day8.re-frame.http-fx, so wrapping it depends on load
;; order and would fail silently if anything claimed it later. This also
;; catches a 401 that arrives through some other effect, and it can see the
;; event id, which is how the two events that own their own 401 opt out.

(def ^:private handles-own-401
  "Events the interceptor must not act on."
  #{::login-failure ; a wrong password, not a dead session
    ::login-refresh-failure}) ; dispatches ::session-expired itself

(rf/reg-global-interceptor
  (rf/->interceptor
    :id ::session-expiry
    :after
    (fn [ctx]
      (cond-> ctx
        ;; `:db` from the coeffects is the value BEFORE the handler ran, so a
        ;; handler that resets state on failure can't hide that we were logged
        ;; in. The decision itself lives in session-expiry/expired? so it can be
        ;; tested without a browser.
        (session-expiry/expired? (:logged-in? (rf/get-coeffect ctx :db))
                                 handles-own-401
                                 (rf/get-coeffect ctx :event))
        (update-in [:effects :fx] (fnil conj []) [:dispatch [::session-expired]])))))

(rf/reg-event-fx ::session-expired
  [(rf/inject-cofx ::local-storage/get :admin-login-data)]
  (fn [{:keys [db local-storage]} _]
    (let [tr (:translator db)]
      ;; A page typically has several requests in flight, so a dead token
      ;; produces several failures and several of these events — all of them
      ;; queued before the first one's `::logout` gets to run, which is why
      ;; `:logged-in?` alone is not enough to deduplicate on. `:session-expiring?`
      ;; is set as this handler's own :db effect, so the copies that follow see
      ;; it and stand down. It disappears with the db reset below.
      ;;
      ;; This matters most while impersonating: two ::exit-impersonation runs
      ;; would restore the admin session and consume the stash on the first, then
      ;; find it empty on the second and log the admin out entirely.
      (if (or (not (:logged-in? db)) (:session-expiring? db))
        {}
        ;; The message is resolved HERE, while the user's own locale is still in
        ;; db, but dispatched AFTER the logout — both ::logout and
        ;; ::exit-impersonation reset db to `default-db`, which would wipe an
        ;; already-set :active-notification (and reset the translator to :fi).
        {:db (assoc db :session-expiring? true)
         :fx [[:dispatch (if (seq (:admin-login-data local-storage))
                           ;; Same fallback as ::login-refresh-failure: when an
                           ;; impersonation session dies, return to the admin's
                           ;; own session instead of logging out completely.
                           [::exit-impersonation]
                           [::logout])]
              [:dispatch [:lipas.ui.events/set-active-notification
                          {:message  (tr :login/session-expired)
                           :success? false}]]]}))))

;;; Impersonation ;;;

;; Admins (privilege :users/impersonate) can log in as another user for
;; testing and support purposes. The admin's own session is stashed in
;; local storage so it can be restored without re-login. All enforcement
;; and audit logging happens server-side.

(rf/reg-event-fx ::impersonate
  (fn [{:keys [db]} [_ user-id]]
    {:http-xhrio
     {:method          :post
      :uri             (str (:backend-url db) "/actions/impersonate")
      :headers         {:Authorization (str "Token " (get-in db [:user :login :token]))}
      :params          {:id (str user-id)}
      :format          (ajax/json-request-format)
      :response-format (ajax/json-response-format {:keywords? true})
      :on-success      [::impersonate-success]
      :on-failure      [::impersonate-failure]}}))

(rf/reg-event-fx ::impersonate-success
  (fn [{:keys [db]} [_ body]]
    {;; Reset app state so nothing from the admin session leaks into the
     ;; impersonated session.
     :db (assoc db/default-db :backend-url (:backend-url db))
     ::local-storage/set! [:admin-login-data (get-in db [:user :login])]
     :dispatch-n [[::login-success :impersonate body]
                  [:lipas.ui.events/navigate "/profiili"]]}))

(rf/reg-event-fx ::impersonate-failure
  (fn [{:keys [db]} [_ resp]]
    (let [tr (:translator db)]
      {:dispatch [:lipas.ui.events/set-active-notification
                  {:message (or (-> resp :response :message)
                                (-> resp :response :error)
                                (tr :error/unknown))
                   :success? false}]})))

(rf/reg-event-fx ::exit-impersonation
  [(rf/inject-cofx ::local-storage/get :admin-login-data)]
  (fn [{:keys [db local-storage]} _]
    (let [admin-login (:admin-login-data local-storage)]
      (if (or (empty? admin-login)
              (utils/jwt-expired? (:token admin-login)))
        ;; Stash is gone or the admin token expired while impersonating.
        {:dispatch [::logout]}
        {:db (assoc db/default-db :backend-url (:backend-url db))
         ::local-storage/remove! :admin-login-data
         :dispatch-n [[::login-success :exit-impersonation admin-login]
                      ;; Trade the stashed token for a fresh one right away.
                      [::refresh-login]
                      [:lipas.ui.events/navigate "/admin"]]}))))

(rf/reg-event-fx ::order-magic-link
  (fn [{db :db} [_ {:keys [email]}]]
    {:http-xhrio
     {:method          :post
      :uri             (str (:backend-url db) "/actions/order-magic-link")
      :params          {:email     email
                        :variant   :lipas
                        :login-url (str (utils/base-url) "/kirjaudu")}
      :format          (ajax/json-request-format)
      :response-format (ajax/json-response-format {:keywords? true})
      :on-success      [::order-magic-link-success]
      :on-failure      [::order-magic-link-failed]}}))

(rf/reg-event-fx ::order-magic-link-success
  (fn [{:keys [db]} [_ _result]]
    {:db             (assoc-in db [:user :magic-link-ordered?] true)
     :tracker/event! ["user" "magic-link-order-success"]}))

(rf/reg-event-fx ::order-magic-link-failed
  (fn [{:keys [db]} [_ result]]
    {:db             (assoc-in db [:user :login-error] result)
     :tracker/event! ["user" "magic-link-order-failed"]}))
