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
                      {:method :post
                       :uri (str (:backend-url db) "/actions/get-current-user-orgs")
                       :headers {:Authorization (str "Token " token)}
                       :params {}
                       :format (ajax/json-request-format)
                       :response-format (ajax/json-response-format {:keywords? true})
                       :on-success [::get-user-orgs-success]
                       :on-failure [::failure]}})))

(rf/reg-event-db ::get-org-users-success
                 (fn [db [_ resp]]
                   (assoc-in db [:org :users] resp)))

(rf/reg-event-fx ::get-org-users
                 (fn [{:keys [db]} [_ org-id]]
                   (let [token (-> db :user :login :token)]
                     {:http-xhrio
                      {:method :post
                       :uri (str (:backend-url db) "/actions/get-org-members")
                       :headers {:Authorization (str "Token " token)}
                       :params {:org-id org-id}
                       :format (ajax/json-request-format)
                       :response-format (ajax/json-response-format {:keywords? true})
                       :on-success [::get-org-users-success]
                       :on-failure [::failure]}})))

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
                              ;; Land on the default "our-sites" tab and load its
                              ;; data via the normal tab path (also fetches the
                              ;; owned-site count for the setup-checklist step ③).
                              (not is-new?) (conj [:dispatch [::set-current-tab "our-sites"]])
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
                       ;; The Our-sites list is driven by bulk-ops' editable-sites
                       ;; (owned ∪ granted, with :owned?); the "owned" fetch stays
                       ;; only for the card / setup-checklist counts.
                       (= tab "our-sites")
                       (assoc :fx [[:dispatch [::bulk-ops-events/init {:org-id org-id}]]
                                   [:dispatch [::get-org-sites org-id "owned"]]])

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
                                  (update :data #(dissoc % :phone))
                   ;; org-schema/org requires :id as a uuid; the loaded org carries
                   ;; it as a string (JSON), so coerce before sending (transit
                   ;; round-trips a real uuid).
                                  (update :id #(if (string? %) (uuid %) %)))
          ;; Check if we're on the "new" route
                         org-id (get-in db [:org :org-id])
                         new? (= "new" org-id)]
                     {:http-xhrio
                      {:method :post
                       :uri (if new?
                              (str (:backend-url db) "/actions/create-org")
                              (str (:backend-url db) "/actions/update-org"))
                       :headers {:Authorization (str "Token " token)}
                       :params body
                       :format (ajax/transit-request-format)
                       :response-format (ajax/transit-response-format)
                       :on-success [::save-org-success org new?]
                       :on-failure [::failure]}})))

;; Bulk operations integration
(rf/reg-event-fx ::init-bulk-operations
                 (fn [{:keys [db]} [_ org-id]]
                   {:db (assoc-in db [:org :current-org-id] org-id)
                    :dispatch [::bulk-ops-events/init {:org-id org-id}]}))

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
                      {:method :post
                       :uri (str (:backend-url db) "/actions/update-org-ptv-config")
                       :headers {:Authorization (str "Token " token)}
                       :params {:org-id org-id :ptv-config ptv-config}
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

;; =====================================================================
;; Org-management self-service (UX-P1 / Phase A)
;; =====================================================================

(defn- token [db] (-> db :user :login :token))

(defn- auth-headers [db] {:Authorization (str "Token " (token db))})

(defn- ->uuid
  "Org ids loaded from JSON are strings; transit bodies preserve types and the
  backend :uuid coercion won't string→uuid, so coerce before sending."
  [x]
  (if (string? x) (uuid x) x))

(rf/reg-event-db ::set-invite-member-form
                 (fn [db [_ path value]]
                   (assoc-in db (into [:org :invite-member-form] path) value)))

;; Email is the single source of truth (no separate user-id path). Editing it
;; invalidates any prior existence verdict so stale status never lingers.
(rf/reg-event-db ::set-invite-email
                 (fn [db [_ email]]
                   (update-in db [:org :invite-member-form]
                              assoc :email email :existing? nil :checking? false)))

(rf/reg-event-db ::clear-invite-member-form
                 (fn [db _]
                   (assoc-in db [:org :invite-member-form] {})))

(def ^:private email-re #".+@.+\..+")

;; Live "is this already a LIPAS account?" check (org-scoped, GDPR-safe — see
;; the backend endpoint). Drives the invite form's inline status + button label.
(rf/reg-event-fx ::check-existing-user
                 (fn [{:keys [db]} [_ org-id]]
                   (let [email (some-> (get-in db [:org :invite-member-form :email]) str/trim)]
                     (if (and email (re-matches email-re email))
                       {:db (assoc-in db [:org :invite-member-form :checking?] true)
                        :http-xhrio
                        {:method :post
                         :uri (str (:backend-url db) "/actions/check-is-existing-user")
                         :headers (auth-headers db)
                         :params {:org-id org-id :email email}
                         :format (ajax/json-request-format)
                         :response-format (ajax/json-response-format {:keywords? true})
                         :on-success [::check-existing-user-success]
                         :on-failure [::failure]}}
                       {}))))

(rf/reg-event-db ::check-existing-user-success
                 (fn [db [_ resp]]
                   (update-in db [:org :invite-member-form]
                              assoc :checking? false :existing? (:exists resp))))

(rf/reg-event-fx ::invite-member
                 (fn [{:keys [db]} [_ org-id]]
                   (let [form (get-in db [:org :invite-member-form])
                         email (some-> (:email form) str/trim)
                         roles (vec (:roles form))]
                     (if (seq email)
                       {:http-xhrio
                        {:method :post
                         :uri (str (:backend-url db) "/actions/invite-org-member")
                         :headers (auth-headers db)
                         :params {:org-id org-id
                                  :email email
                                  :roles roles
                                  :login-url (str (ui-utils/base-url) "/#/kirjaudu")}
                         :format (ajax/json-request-format)
                         :response-format (ajax/json-response-format {:keywords? true})
                         :on-success [::invite-member-success org-id email]
                         :on-failure [::failure]}}
                       {:fx [[:dispatch [:lipas.ui.events/set-active-notification
                                         {:message "Anna sähköpostiosoite" :success? false}]]]}))))

(rf/reg-event-fx ::invite-member-success
                 ;; The response's :new-account? tells us what actually happened,
                 ;; so the toast is accurate whether we invited or added.
                 (fn [{:keys [db]} [_ org-id email resp]]
                   {:fx [[:dispatch [::get-org-users org-id]]
                         [:dispatch [::clear-invite-member-form]]
                         [:dispatch [:lipas.ui.events/set-active-notification
                                     {:message (if (:new-account? resp)
                                                 (str "Kutsu lähetettiin osoitteeseen " email)
                                                 (str "Käyttäjä " email " lisättiin organisaatioon ja hänelle ilmoitettiin sähköpostitse"))
                                      :success? true}]]]}))

(rf/reg-event-fx ::set-member-roles
                 (fn [{:keys [db]} [_ org-id user-id roles]]
                   {:http-xhrio
                    {:method :post
                     :uri (str (:backend-url db) "/actions/set-org-member-roles")
                     :headers (auth-headers db)
                     :params {:org-id org-id :user-id user-id :roles (vec roles)}
                     :format (ajax/json-request-format)
                     :response-format (ajax/json-response-format {:keywords? true})
                     :on-success [::get-org-users org-id]
                     :on-failure [::failure]}}))

(rf/reg-event-fx ::remove-member
                 (fn [{:keys [db]} [_ org-id user-id]]
                   {:http-xhrio
                    {:method :post
                     :uri (str (:backend-url db) "/actions/remove-org-member")
                     :headers (auth-headers db)
                     :params {:org-id org-id :user-id user-id}
                     :format (ajax/json-request-format)
                     :response-format (ajax/json-response-format {:keywords? true})
                     :on-success [::get-org-users org-id]
                     :on-failure [::failure]}}))

;; --- Catalog editor (lipas-admin): a local map {key {:label :roles [specs]}}
;; seeded on tab switch (::set-current-tab), saved via ::edit-template-catalog ---
(rf/reg-event-db ::init-catalog-editor
                 (fn [db [_ catalog]]
                   (assoc-in db [:org :catalog-editor] (or catalog {}))))

(defn- gen-template-key
  "A unique catalog key. Prefers `preferred` when free, else rooli-N."
  [catalog preferred]
  (if (and preferred (not (contains? catalog preferred)))
    preferred
    (->> (range 1 1000)
         (map #(keyword (str "rooli-" %)))
         (remove (set (keys catalog)))
         first)))

(rf/reg-event-db ::add-template
                 (fn [db [_ {:keys [key label roles]}]]
                   (let [catalog (get-in db [:org :catalog-editor] {})
                         k (gen-template-key catalog key)]
                     (assoc-in db [:org :catalog-editor k]
                               {:label (or label "") :roles (or roles [{}])}))))

(rf/reg-event-db ::remove-template
                 (fn [db [_ tkey]]
                   (update-in db [:org :catalog-editor] dissoc tkey)))

(rf/reg-event-db ::set-template-label
                 (fn [db [_ tkey label]]
                   (assoc-in db [:org :catalog-editor tkey :label] label)))

(rf/reg-event-db ::add-template-role
                 (fn [db [_ tkey]]
                   (update-in db [:org :catalog-editor tkey :roles] (fnil conj []) {})))

(rf/reg-event-db ::remove-template-role
                 (fn [db [_ tkey idx]]
                   (update-in db [:org :catalog-editor tkey :roles]
                              (fn [roles] (vec (concat (subvec roles 0 idx)
                                                       (subvec roles (inc idx))))))))

(rf/reg-event-db ::set-template-role
                 ;; role changed → reset to {:role <name>} (drop stale context keys)
                 (fn [db [_ tkey idx role-kw]]
                   (assoc-in db [:org :catalog-editor tkey :roles idx]
                             (if role-kw {:role (name role-kw)} {}))))

(rf/reg-event-db ::set-template-role-context
                 (fn [db [_ tkey idx k vals]]
                   (let [path [:org :catalog-editor tkey :roles idx]]
                     (if (seq vals)
                       (update-in db path assoc k (vec vals))
                       (update-in db path dissoc k)))))

(defn- sanitize-catalog
  "Drop half-filled role-specs (no `:role` yet) before saving so the working
  editor copy — which seeds new rows as empty `{}` — never fails the strict
  catalog schema on the backend."
  [catalog]
  (into {} (map (fn [[k entry]]
                  [k (update entry :roles #(vec (filter :role %)))]))
        catalog))

(rf/reg-event-fx ::edit-template-catalog
                 (fn [{:keys [db]} [_ org-id role-templates]]
                   (let [role-templates (sanitize-catalog role-templates)]
                     {:http-xhrio
                      {:method :post
                       :uri (str (:backend-url db) "/actions/update-org-role-templates")
                       :headers (auth-headers db)
                       :params {:org-id (->uuid org-id) :role-templates role-templates}
                       :format (ajax/transit-request-format)
                       :response-format (ajax/transit-response-format)
                       :on-success [::edit-template-catalog-success org-id role-templates]
                       :on-failure [::failure]}})))

(rf/reg-event-fx ::edit-template-catalog-success
                 (fn [{:keys [db]} [_ org-id role-templates _resp]]
                   {:db (assoc-in db [:org :editing-org :role-templates] role-templates)
                    :fx [[:dispatch [::get-user-orgs]]
                         [:dispatch [:lipas.ui.events/set-active-notification
                                     {:message "Roolimallit tallennettu" :success? true}]]]}))

;; =====================================================================
;; Our sites (UX-P2 / Phase D)
;; =====================================================================

(rf/reg-event-db ::get-org-sites-success
                 (fn [db [_ flt resp]]
                   (assoc-in db [:org :sites (keyword flt)] resp)))

(rf/reg-event-fx ::get-org-sites
                 (fn [{:keys [db]} [_ org-id flt]]
                   {:http-xhrio
                    {:method :post
                     :uri (str (:backend-url db) "/actions/get-org-sites")
                     :headers (auth-headers db)
                     :params {:org-id org-id :filter flt}
                     :format (ajax/json-request-format)
                     :response-format (ajax/json-response-format {:keywords? true})
                     :on-success [::get-org-sites-success flt]
                     :on-failure [::failure]}}))

(rf/reg-event-db ::get-site-editors-success
                 (fn [db [_ lipas-id resp]]
                   (assoc-in db [:org :site-editors lipas-id] resp)))

(rf/reg-event-fx ::get-site-editors
                 (fn [{:keys [db]} [_ lipas-id]]
                   {:http-xhrio
                    {:method :post
                     :uri (str (:backend-url db) "/actions/get-site-editors")
                     :headers (auth-headers db)
                     :params {:lipas-id lipas-id}
                     :format (ajax/json-request-format)
                     :response-format (ajax/json-response-format {:keywords? true})
                     :on-success [::get-site-editors-success lipas-id]
                     :on-failure [::failure]}}))

(rf/reg-event-db ::get-site-edit-history-success
                 (fn [db [_ lipas-id resp]]
                   (assoc-in db [:org :site-edit-history lipas-id] resp)))

(rf/reg-event-fx ::get-site-edit-history
                 (fn [{:keys [db]} [_ lipas-id]]
                   {:http-xhrio
                    {:method :post
                     :uri (str (:backend-url db) "/actions/get-site-edit-history")
                     :headers (auth-headers db)
                     :params {:lipas-id lipas-id}
                     :format (ajax/json-request-format)
                     :response-format (ajax/json-response-format {:keywords? true})
                     :on-success [::get-site-edit-history-success lipas-id]
                     :on-failure [::failure]}}))

(rf/reg-event-fx ::grant-site-edit
                 (fn [{:keys [db]} [_ org-id lipas-id grantee-org-id]]
                   {:http-xhrio
                    {:method :post
                     :uri (str (:backend-url db) "/actions/grant-site-edit")
                     :headers (auth-headers db)
                     :params {:org-id (->uuid org-id) :lipas-id lipas-id :grantee-org-id (->uuid grantee-org-id)}
                     :format (ajax/transit-request-format)
                     :response-format (ajax/transit-response-format)
                     :on-success [::site-edit-grant-success org-id lipas-id]
                     :on-failure [::failure]}}))

(rf/reg-event-fx ::revoke-site-edit
                 (fn [{:keys [db]} [_ org-id lipas-id grantee-org-id]]
                   {:http-xhrio
                    {:method :post
                     :uri (str (:backend-url db) "/actions/revoke-site-edit")
                     :headers (auth-headers db)
                     :params {:org-id (->uuid org-id) :lipas-id lipas-id :grantee-org-id (->uuid grantee-org-id)}
                     :format (ajax/transit-request-format)
                     :response-format (ajax/transit-response-format)
                     :on-success [::site-edit-grant-success org-id lipas-id]
                     :on-failure [::failure]}}))

(rf/reg-event-fx ::site-edit-grant-success
                 (fn [{:keys [db]} [_ _org-id lipas-id _resp]]
                   {:fx [[:dispatch [::get-site-editors lipas-id]]
                         [:dispatch [:lipas.ui.events/set-active-notification
                                     {:message "Muokkausoikeus päivitetty" :success? true}]]]}))

;; --- Claim impact warning: fetch preview, show dialog, confirm ---
(rf/reg-event-db ::get-takeover-preview-success
                 (fn [db [_ resp]]
                   (assoc-in db [:org :takeover-preview] resp)))

(rf/reg-event-fx ::get-takeover-preview
                 (fn [{:keys [db]} [_ org-id]]
                   {:http-xhrio
                    {:method :post
                     :uri (str (:backend-url db) "/actions/preview-org-takeover")
                     :headers (auth-headers db)
                     :params {:org-id org-id}
                     :format (ajax/json-request-format)
                     :response-format (ajax/json-response-format {:keywords? true})
                     :on-success [::get-takeover-preview-success]
                     :on-failure [::failure]}}))

(rf/reg-event-fx ::open-claim-dialog
                 ;; dialog = {:mode "request"|"approve" :org-id .. :request-id ..}
                 (fn [{:keys [db]} [_ dialog]]
                   {:db (-> db
                            (assoc-in [:org :claim-dialog] dialog)
                            (update :org dissoc :takeover-preview))
                    :dispatch [::get-takeover-preview (:org-id dialog)]}))

(rf/reg-event-db ::close-claim-dialog
                 (fn [db _]
                   (update db :org dissoc :claim-dialog :takeover-preview :reclaiming?)))

;; reclaim/approve can take tens of seconds (≈350ms/site, synchronous); keep the
;; dialog open with a spinner until the server responds, rather than firing and
;; forgetting. nginx /api and Jetty both allow 300s — match that on the client.
(def ^:private reclaim-timeout-ms 300000)

(rf/reg-event-fx ::confirm-claim
                 ;; approve  → decide an existing request (lipas-admin queue)
                 ;; reclaim  → lipas-admin applies directly, no queue round-trip
                 ;; request  → org-admin self-service → goes to the approval queue
                 (fn [{:keys [db]} _]
                   (let [{:keys [mode org-id request-id]} (get-in db [:org :claim-dialog])
                         lipas-admin? (roles/check-privilege (get-in db [:user :login]) {} :users/manage)]
                     ;; mark in-flight (drives the dialog spinner); the success
                     ;; handler closes the dialog, a failure clears the flag
                     {:db (assoc-in db [:org :reclaiming?] true)
                      :dispatch (cond
                                  (= mode "approve") [::decide-takeover request-id "approve"]
                                  lipas-admin?       [::reclaim-now org-id]
                                  :else              [::request-takeover org-id])})))

;; clears the in-flight flag (leaving the dialog open so the error is visible)
(rf/reg-event-fx ::claim-failure
                 (fn [{:keys [db]} [_ resp]]
                   {:db (assoc-in db [:org :reclaiming?] false)
                    :dispatch [::failure resp]}))

(rf/reg-event-fx ::request-takeover
                 (fn [{:keys [db]} [_ org-id]]
                   {:http-xhrio
                    {:method :post
                     :uri (str (:backend-url db) "/actions/request-org-takeover")
                     :headers (auth-headers db)
                     :params {:org-id (->uuid org-id)}
                     :format (ajax/transit-request-format)
                     :response-format (ajax/transit-response-format)
                     :on-success [::request-takeover-success]
                     :on-failure [::claim-failure]}}))

(rf/reg-event-fx ::reclaim-now
                 (fn [{:keys [db]} [_ org-id]]
                   {:http-xhrio
                    {:method :post
                     :uri (str (:backend-url db) "/actions/reclaim-org-sites")
                     :headers (auth-headers db)
                     :params {:org-id (->uuid org-id)}
                     :timeout reclaim-timeout-ms
                     :format (ajax/transit-request-format)
                     :response-format (ajax/transit-response-format)
                     :on-success [::reclaim-now-success org-id]
                     :on-failure [::claim-failure]}}))

(rf/reg-event-fx ::reclaim-now-success
                 (fn [_ [_ org-id resp]]
                   {:fx [[:dispatch [::close-claim-dialog]]
                         [:dispatch [::get-org-sites org-id "owned"]]
                         [:dispatch [:lipas.ui.events/set-active-notification
                                     {:message (str "Siirretty " (:sites-claimed resp) " kohdetta omistukseen")
                                      :success? true}]]]}))

(rf/reg-event-fx ::request-takeover-success
                 (fn [_ [_ _resp]]
                   {:fx [[:dispatch [::close-claim-dialog]]
                         [:dispatch [:lipas.ui.events/set-active-notification
                                     {:message "Siirtopyyntö lähetetty" :success? true}]]]}))

;; =====================================================================
;; History + take-over approvals (UX-P3 / Phase E)
;; =====================================================================

(rf/reg-event-db ::get-org-history-success
                 (fn [db [_ resp]]
                   (assoc-in db [:org :history] resp)))

(rf/reg-event-fx ::get-org-history
                 (fn [{:keys [db]} [_ org-id]]
                   {:http-xhrio
                    {:method :post
                     :uri (str (:backend-url db) "/actions/get-org-history")
                     :headers (auth-headers db)
                     :params {:org-id org-id}
                     :format (ajax/json-request-format)
                     :response-format (ajax/json-response-format {:keywords? true})
                     :on-success [::get-org-history-success]
                     :on-failure [::failure]}}))

(rf/reg-event-db ::get-takeover-requests-success
                 (fn [db [_ resp]]
                   (assoc-in db [:org :takeover-requests] resp)))

(rf/reg-event-fx ::get-takeover-requests
                 (fn [{:keys [db]} [_ status]]
                   {:http-xhrio
                    {:method :post
                     :uri (str (:backend-url db) "/actions/list-org-takeover-requests")
                     :headers (auth-headers db)
                     :params (cond-> {} status (assoc :status status))
                     :format (ajax/json-request-format)
                     :response-format (ajax/json-response-format {:keywords? true})
                     :on-success [::get-takeover-requests-success]
                     :on-failure [::failure]}}))

(rf/reg-event-fx ::decide-takeover
                 ;; decision is "approve" | "deny" → /actions/approve-org-takeover etc.
                 (fn [{:keys [db]} [_ request-id decision]]
                   {:http-xhrio
                    {:method :post
                     :uri (str (:backend-url db) "/actions/" decision "-org-takeover")
                     :headers (auth-headers db)
                     :params {:request-id (->uuid request-id)}
                     ;; approve applies ownership site-by-site → can be slow
                     :timeout reclaim-timeout-ms
                     :format (ajax/transit-request-format)
                     :response-format (ajax/transit-response-format)
                     :on-success [::decide-takeover-success]
                     :on-failure [::claim-failure]}}))

(rf/reg-event-fx ::decide-takeover-success
                 (fn [_ [_ _resp]]
                   {:fx [[:dispatch [::close-claim-dialog]]
                         [:dispatch [::get-takeover-requests "requested"]]
                         [:dispatch [:lipas.ui.events/set-active-notification
                                     {:message "Pyyntö käsitelty" :success? true}]]]}))
