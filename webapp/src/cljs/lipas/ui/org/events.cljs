(ns lipas.ui.org.events
  (:require [ajax.core :as ajax]
            [clojure.string :as str]
            [cognitect.transit :as t]
            [lipas.roles :as roles]
            [lipas.ui.bulk-operations.events :as bulk-ops-events]
            [lipas.ui.utils :as ui-utils]
            [lipas.utils :as utils]
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

(defn- init-ptv-data
  "Ensure ptv-data has explicit defaults for fields the strict
  `ptv-config-update` schema requires. Without these defaults the
  Save button stays disabled — either because legacy rows lack
  :sync-enabled, or because selectors render a default value
  (:owners) that never gets written to app-db until the user
  actively interacts with them."
  [ptv-data]
  (merge {:sync-enabled false
          :owners ["city" "city-main-owner"]}
         ptv-data))

(defn- init-org [org]
  (when org
    (update org :ptv-data init-ptv-data)))

(rf/reg-event-fx ::init-view
                 (fn [{:keys [db]} [_ org-id]]
                   (let [user-data (get-in db [:user :login])
                         is-lipas-admin? (roles/check-privilege user-data {} :users/manage)
                         user-orgs (-> db :user :orgs)
                         is-new? (= "new" org-id)
                         current-org (if is-new?
                                       {:id (random-uuid)
                                        :name ""
                                        :data {:primary-contact {}}
                                        :ptv-data (init-ptv-data {})}
                                       (when user-orgs
                                         (some (fn [o]
                                                 (when (= org-id (str (:id o)))
                                                   (init-org o)))
                                               user-orgs)))
                         fx (cond-> []
                              (not is-new?) (conj [:dispatch [::get-org-users org-id]])
                              is-lipas-admin? (conj [:dispatch [::get-all-users]])
                              is-lipas-admin? (conj [:dispatch [::get-takeover-requests "requested"]])
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
                                                   (init-org o)))
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
                         role (or (:role form) "org-user")]
                     (if user-id
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
                         role (or (:role form) "org-user")]
                     (if email
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
                                       (get-in resp [:response :error])
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
                       (assoc :dispatch [::bulk-ops-events/init {}])

                       ;; Lazily load data for the new org-management tabs
                       (= tab "our-sites")
                       (assoc :fx [[:dispatch [::bulk-ops-events/init {}]]
                                   [:dispatch [::get-org-sites org-id "owned"]]
                                   [:dispatch [::get-org-sites org-id "editable"]]])

                       (= tab "history")
                       (assoc :dispatch [::get-org-history org-id])

                       ;; Seed the lipas-admin catalog editor from the org's catalog
                       (= tab "roles-templates")
                       (assoc-in [:db :org :catalog-editor]
                                 (get-in db [:org :editing-org :role-templates] {}))))))

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
          ;; Check if we're on the "new" route
                         org-id (get-in db [:org :org-id])
                         new? (= "new" org-id)]
                     {:http-xhrio
                      {:method (if new? :post :put)
                       :uri (if new?
                              (str (:backend-url db) "/orgs")
                              (str (:backend-url db) "/orgs/" (:id org)))
                       :headers {:Authorization (str "Token " token)}
                       :params body
                       :format (ajax/transit-request-format)
                       :response-format (ajax/transit-response-format)
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
                 (fn [{:keys [db]} [_ org-id ptv-config resp]]
                   {:db (update-in db [:user :orgs]
                                   (fn [orgs]
                                     (mapv (fn [org]
                                             (if (= (:id org) org-id)
                                               (assoc org :ptv-data ptv-config)
                                               org))
                                           orgs)))
                    :fx [[:dispatch [:lipas.ui.events/set-active-notification
                                     {:message "PTV configuration saved successfully"
                                      :success? true}]]]}))

(rf/reg-event-fx ::save-ptv-config
                 (fn [{:keys [db]} [_]]
                   (let [token (-> db :user :login :token)
                         org (get-in db [:org :editing-org])
                         org-id (:id org)
                         ptv-config (utils/clean (:ptv-data org))]
                     {:http-xhrio
                      {:method :put
                       :uri (str (:backend-url db) "/orgs/" org-id "/ptv-config")
                       :headers {:Authorization (str "Token " token)}
                       :params ptv-config
                       :format (ajax/json-request-format)
                       :response-format (ajax/json-response-format {:keywords? true})
                       :on-success [::save-ptv-config-success org-id ptv-config]
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

;; =====================================================================
;; Org-management self-service (UX-P1 / Phase A)
;; =====================================================================

(defn- token [db] (-> db :user :login :token))

(defn- auth-headers [db] {:Authorization (str "Token " (token db))})

(rf/reg-event-db ::set-invite-member-form
                 (fn [db [_ path value]]
                   (assoc-in db (into [:org :invite-member-form] path) value)))

(rf/reg-event-db ::clear-invite-member-form
                 (fn [db _]
                   (assoc-in db [:org :invite-member-form] {})))

(rf/reg-event-fx ::invite-member
                 (fn [{:keys [db]} [_ org-id]]
                   (let [form (get-in db [:org :invite-member-form])
                         email (some-> (:email form) str/trim)
                         user-id (:user-id form) ;; lipas-admin convenience (existing account)
                         org-role (or (:org-role form) "member")
                         templates (vec (:templates form))]
                     (if (or (seq email) user-id)
                       {:http-xhrio
                        {:method :post
                         :uri (str (:backend-url db) "/orgs/" org-id "/invite")
                         :headers (auth-headers db)
                         :params (cond-> {:org-role org-role
                                          :templates templates
                                          :login-url (str (ui-utils/base-url) "/#/kirjaudu")}
                                   (seq email) (assoc :email email)
                                   ;; lipas-admin picked an existing account by id;
                                   ;; resolve its email from the loaded all-users list
                                   (and user-id (not (seq email)))
                                   (assoc :email (->> (get-in db [:org :all-users])
                                                      (some (fn [u] (when (= (:id u) user-id) (:email u)))))))
                         :format (ajax/json-request-format)
                         :response-format (ajax/json-response-format {:keywords? true})
                         :on-success [::invite-member-success org-id]
                         :on-failure [::failure]}}
                       {:fx [[:dispatch [:lipas.ui.events/set-active-notification
                                         {:message "Anna sähköpostiosoite" :success? false}]]]}))))

(rf/reg-event-fx ::invite-member-success
                 (fn [{:keys [db]} [_ org-id _resp]]
                   {:fx [[:dispatch [::get-org-users org-id]]
                         [:dispatch [::clear-invite-member-form]]
                         [:dispatch [:lipas.ui.events/set-active-notification
                                     {:message "Jäsen kutsuttu" :success? true}]]]}))

(rf/reg-event-fx ::set-member-org-role
                 (fn [{:keys [db]} [_ org-id user-id org-role]]
                   {:http-xhrio
                    {:method :put
                     :uri (str (:backend-url db) "/orgs/" org-id "/members/" user-id "/role")
                     :headers (auth-headers db)
                     :params {:org-role org-role}
                     :format (ajax/json-request-format)
                     :response-format (ajax/json-response-format {:keywords? true})
                     :on-success [::get-org-users org-id]
                     :on-failure [::failure]}}))

(rf/reg-event-fx ::set-member-templates
                 (fn [{:keys [db]} [_ org-id user-id templates]]
                   {:http-xhrio
                    {:method :put
                     :uri (str (:backend-url db) "/orgs/" org-id "/members/" user-id "/templates")
                     :headers (auth-headers db)
                     :params {:templates (vec templates)}
                     :format (ajax/json-request-format)
                     :response-format (ajax/json-response-format {:keywords? true})
                     :on-success [::get-org-users org-id]
                     :on-failure [::failure]}}))

(rf/reg-event-fx ::remove-member
                 (fn [{:keys [db]} [_ org-id user-id]]
                   {:http-xhrio
                    {:method :delete
                     :uri (str (:backend-url db) "/orgs/" org-id "/members/" user-id)
                     :headers (auth-headers db)
                     :format (ajax/json-request-format)
                     :response-format (ajax/json-response-format {:keywords? true})
                     :on-success [::get-org-users org-id]
                     :on-failure [::failure]}}))

;; --- Catalog editor (lipas-admin) ---
(rf/reg-event-db ::init-catalog-editor
                 (fn [db [_ catalog]]
                   (assoc-in db [:org :catalog-editor] (or catalog {}))))

(rf/reg-event-db ::set-catalog-editor
                 (fn [db [_ catalog]]
                   (assoc-in db [:org :catalog-editor] catalog)))

(rf/reg-event-fx ::edit-template-catalog
                 (fn [{:keys [db]} [_ org-id role-templates]]
                   {:http-xhrio
                    {:method :put
                     :uri (str (:backend-url db) "/orgs/" org-id "/role-templates")
                     :headers (auth-headers db)
                     :params {:role-templates role-templates}
                     :format (ajax/transit-request-format)
                     :response-format (ajax/transit-response-format)
                     :on-success [::edit-template-catalog-success org-id role-templates]
                     :on-failure [::failure]}}))

(rf/reg-event-fx ::edit-template-catalog-success
                 (fn [{:keys [db]} [_ org-id role-templates _resp]]
                   {:db (assoc-in db [:org :editing-org :role-templates] role-templates)
                    :fx [[:dispatch [::get-user-orgs]]
                         [:dispatch [:lipas.ui.events/set-active-notification
                                     {:message "Roolimallit tallennettu" :success? true}]]]}))

;; =====================================================================
;; Our sites (UX-P2 / Phase D)
;; =====================================================================

(rf/reg-event-db ::set-our-sites-filter
                 (fn [db [_ flt]]
                   (assoc-in db [:org :sites :filter] flt)))

(rf/reg-event-db ::get-org-sites-success
                 (fn [db [_ flt resp]]
                   (assoc-in db [:org :sites (keyword flt)] resp)))

(rf/reg-event-fx ::get-org-sites
                 (fn [{:keys [db]} [_ org-id flt]]
                   {:http-xhrio
                    {:method :get
                     :uri (str (:backend-url db) "/orgs/" org-id "/sites?filter=" flt)
                     :headers (auth-headers db)
                     :response-format (ajax/json-response-format {:keywords? true})
                     :on-success [::get-org-sites-success flt]
                     :on-failure [::failure]}}))

(rf/reg-event-db ::get-site-editors-success
                 (fn [db [_ lipas-id resp]]
                   (assoc-in db [:org :site-editors lipas-id] resp)))

(rf/reg-event-fx ::get-site-editors
                 (fn [{:keys [db]} [_ lipas-id]]
                   {:http-xhrio
                    {:method :get
                     :uri (str (:backend-url db) "/sites/" lipas-id "/editors")
                     :headers (auth-headers db)
                     :response-format (ajax/json-response-format {:keywords? true})
                     :on-success [::get-site-editors-success lipas-id]
                     :on-failure [::failure]}}))

(rf/reg-event-fx ::grant-site-edit
                 (fn [{:keys [db]} [_ org-id lipas-id grantee-org-id]]
                   {:http-xhrio
                    {:method :post
                     :uri (str (:backend-url db) "/orgs/" org-id "/site-edit-grants")
                     :headers (auth-headers db)
                     :params {:lipas-id lipas-id :grantee-org-id grantee-org-id}
                     :format (ajax/transit-request-format)
                     :response-format (ajax/transit-response-format)
                     :on-success [::site-edit-grant-success org-id lipas-id]
                     :on-failure [::failure]}}))

(rf/reg-event-fx ::revoke-site-edit
                 (fn [{:keys [db]} [_ org-id lipas-id grantee-org-id]]
                   {:http-xhrio
                    {:method :delete
                     :uri (str (:backend-url db) "/orgs/" org-id "/site-edit-grants")
                     :headers (auth-headers db)
                     :params {:lipas-id lipas-id :grantee-org-id grantee-org-id}
                     :format (ajax/transit-request-format)
                     :response-format (ajax/transit-response-format)
                     :on-success [::site-edit-grant-success org-id lipas-id]
                     :on-failure [::failure]}}))

(rf/reg-event-fx ::site-edit-grant-success
                 (fn [{:keys [db]} [_ _org-id lipas-id _resp]]
                   {:fx [[:dispatch [::get-site-editors lipas-id]]
                         [:dispatch [:lipas.ui.events/set-active-notification
                                     {:message "Muokkausoikeus päivitetty" :success? true}]]]}))

(rf/reg-event-fx ::request-takeover
                 (fn [{:keys [db]} [_ org-id]]
                   {:http-xhrio
                    {:method :post
                     :uri (str (:backend-url db) "/orgs/" org-id "/takeover-request")
                     :headers (auth-headers db)
                     :format (ajax/transit-request-format)
                     :response-format (ajax/transit-response-format)
                     :on-success [::request-takeover-success]
                     :on-failure [::failure]}}))

(rf/reg-event-fx ::request-takeover-success
                 (fn [_ [_ _resp]]
                   {:fx [[:dispatch [:lipas.ui.events/set-active-notification
                                     {:message "Lunastuspyyntö lähetetty" :success? true}]]]}))

;; =====================================================================
;; History + take-over approvals (UX-P3 / Phase E)
;; =====================================================================

(rf/reg-event-db ::get-org-history-success
                 (fn [db [_ resp]]
                   (assoc-in db [:org :history] resp)))

(rf/reg-event-fx ::get-org-history
                 (fn [{:keys [db]} [_ org-id]]
                   {:http-xhrio
                    {:method :get
                     :uri (str (:backend-url db) "/orgs/" org-id "/history")
                     :headers (auth-headers db)
                     :response-format (ajax/json-response-format {:keywords? true})
                     :on-success [::get-org-history-success]
                     :on-failure [::failure]}}))

(rf/reg-event-db ::get-takeover-requests-success
                 (fn [db [_ resp]]
                   (assoc-in db [:org :takeover-requests] resp)))

(rf/reg-event-fx ::get-takeover-requests
                 (fn [{:keys [db]} [_ status]]
                   {:http-xhrio
                    {:method :get
                     :uri (str (:backend-url db) "/actions/org-takeover-requests"
                               (when status (str "?status=" status)))
                     :headers (auth-headers db)
                     :response-format (ajax/json-response-format {:keywords? true})
                     :on-success [::get-takeover-requests-success]
                     :on-failure [::failure]}}))

(rf/reg-event-fx ::decide-takeover
                 (fn [{:keys [db]} [_ request-id decision]]
                   {:http-xhrio
                    {:method :post
                     :uri (str (:backend-url db) "/actions/org-takeover-requests/"
                               request-id "/" decision)
                     :headers (auth-headers db)
                     :format (ajax/transit-request-format)
                     :response-format (ajax/transit-response-format)
                     :on-success [::decide-takeover-success]
                     :on-failure [::failure]}}))

(rf/reg-event-fx ::decide-takeover-success
                 (fn [_ [_ _resp]]
                   {:fx [[:dispatch [::get-takeover-requests "requested"]]
                         [:dispatch [:lipas.ui.events/set-active-notification
                                     {:message "Pyyntö käsitelty" :success? true}]]]}))
