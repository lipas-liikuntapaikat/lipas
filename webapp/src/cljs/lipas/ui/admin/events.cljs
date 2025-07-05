(ns lipas.ui.admin.events
  (:require [ajax.core :as ajax]
            [clojure.spec.alpha :as s]
            [cognitect.transit :as t]
            [lipas.roles :as roles]
            [lipas.schema.core]
            [lipas.ui.utils :as utils]
            [re-frame.core :as rf]
            [reitit.frontend.easy :as rfe]))

(def transit-extra-read-handlers
  {"f" #(js/parseFloat %) ; BigDecimal -> float
   "n" #(js/parseInt % 10) ; BigInteger -> int
   "r" (fn [[n d]] ; Ratio -> decimal
         (/ (js/parseFloat n) (js/parseFloat d)))})

(def transit-reader (t/reader :json {:handlers transit-extra-read-handlers}))

(rf/reg-event-db ::filter-users
                 (fn [db [_ s]]
                   (assoc-in db [:admin :users-filter] s)))

(rf/reg-event-db ::select-status
                 (fn [db [_ s]]
                   (assoc-in db [:admin :users-status] s)))

(rf/reg-event-db ::get-users-success
                 (fn [db [_ users]]
                   (assoc-in db [:admin :users] (utils/index-by :id users))))

(rf/reg-event-fx ::failure
                 (fn [{:keys [db]} [_ resp]]
                   (let [tr (:translator db)]
                     {:dispatch [:lipas.ui.events/set-active-notification
                                 {:message (or (-> resp :response :message)
                                               (-> resp :response :error)
                                               (tr :error/unknown))
                                  :success? false}]})))

(rf/reg-event-fx ::get-users
                 (fn [{:keys [db]} [_ _]]
                   (let [token (-> db :user :login :token)]
                     {:http-xhrio
                      {:method :get
                       :headers {:Authorization (str "Token " token)}
                       :uri (str (:backend-url db) "/users")
                       :response-format (ajax/json-response-format {:keywords? true})
                       :on-success [::get-users-success]
                       :on-failure [::failure]}})))

(rf/reg-event-db ::display-user
                 (fn [db [_ {:keys [id]}]]
                   (assoc-in db [:admin :selected-user] id)))

(rf/reg-event-db ::set-user-to-edit
                 (fn [db [_ {:keys [id]}]]
                   (assoc-in db
                             [:admin :editing-user]
                             (when id
                               (update-in (get-in db [:admin :users id]) [:permissions :roles] roles/conform-roles)))))

(rf/reg-event-db ::edit-user
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
                     (if (seq value)
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

(rf/reg-event-db ::grant-access-to-activity-types
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

(rf/reg-event-fx ::save-user-success
                 (fn [{:keys [db]} [_ user _]]
                   (let [tr (:translator db)]
                     {:db (assoc-in db [:admin :users (:id user)] user)
                      :dispatch-n [[::get-users]
                                   [:lipas.ui.events/set-active-notification
                                    {:message (tr :notifications/save-success)
                                     :success? true}]]})))

(rf/reg-event-fx ::save-user
                 (fn [{:keys [db]} [_ user]]
                   (let [token (-> db :user :login :token)
                         body (-> user
                                  (select-keys [:id :permissions])
                                  (assoc :login-url (str (utils/base-url) "/#/kirjaudu")))]
                     {:http-xhrio
                      {:method :post
                       :uri (str (:backend-url db) "/actions/update-user-permissions")
                       :headers {:Authorization (str "Token " token)}
                       :params body
                       :format (ajax/json-request-format)
                       :response-format (ajax/json-response-format {:keywords? true})
                       :on-success [::save-user-success user]
                       :on-failure [::failure]}})))

(rf/reg-event-fx ::update-user-status
                 (fn [{:keys [db]} [_ user status]]
                   (let [token (-> db :user :login :token)
                         body {:id (:id user) :status status}]
                     {:http-xhrio
                      {:method :post
                       :uri (str (:backend-url db) "/actions/update-user-status")
                       :headers {:Authorization (str "Token " token)}
                       :params body
                       :format (ajax/json-request-format)
                       :response-format (ajax/json-response-format {:keywords? true})
                       :on-success [::save-user-success (assoc user :status status)]
                       :on-failure [::failure]}})))

(rf/reg-event-fx ::send-magic-link
                 (fn [{:keys [db]} [_ user variant]]
                   (let [token (-> db :user :login :token)]
                     {:http-xhrio
                      {:method :post
                       :uri (str (:backend-url db) "/actions/send-magic-link")
                       :headers {:Authorization (str "Token " token)}
                       :params {:user user
                                :login-url (str (utils/base-url) "/#/kirjaudu")
                                :variant variant}
                       :format (ajax/json-request-format)
                       :response-format (ajax/json-response-format {:keywords? true})
                       :on-success [::save-user-success user]
                       :on-failure [::failure]}
                      :dispatch [::close-magic-link-dialog]})))

(rf/reg-event-db ::open-magic-link-dialog
                 (fn [db [_ _]]
                   (assoc-in db [:admin :magic-link-dialog-open?] true)))

(rf/reg-event-db ::close-magic-link-dialog
                 (fn [db [_ _]]
                   (assoc-in db [:admin :magic-link-dialog-open?] false)))

(rf/reg-event-db ::select-magic-link-variant
                 (fn [db [_ v]]
                   (assoc-in db [:admin :selected-magic-link-variant] v)))

(rf/reg-event-db ::select-color
                 (fn [db [_ type-code k v]]
                   (assoc-in db [:admin :color-picker type-code k] v)))

(rf/reg-event-fx ::download-new-colors-excel
                 (fn [{:keys [db]} _]
                   (let [headers [[:type-code "type-code"]
                                  [:symbol "symbol"]
                                  [:fill "fill"]
                                  [:stroke "stroke"]]
                         data (reduce (fn [res [k v]]
                                        (conj res (assoc v :type-code k)))
                                      []
                                      (-> db :admin :color-picker))
                         config {:filename "lipas_symbols"
                                 :sheet
                                 {:data (utils/->excel-data headers data)}}]
                     {:lipas.ui.effects/download-excel! config})))

(rf/reg-event-fx ::gdpr-remove-user
                 (fn [{:keys [db]} [_ user]]
                   (let [token (-> db :user :login :token)]
                     {:http-xhrio
                      {:method :post
                       :uri (str (:backend-url db) "/actions/gdpr-remove-user")
                       :headers {:Authorization (str "Token " token)}
                       :params user
                       :format (ajax/json-request-format)
                       :response-format (ajax/json-response-format {:keywords? true})
                       :on-success [::gdpr-remove-user-success]
                       :on-failure [::failure]}})))

(rf/reg-event-fx ::gdpr-remove-user-success
                 (fn [{:keys [db]} [_ user]]
                   (let [tr (:translator db)]
                     {:db (assoc-in db [:admin :users (:id user)] user)
                      :fx [[:dispatch [:lipas.ui.events/set-active-notification
                                       {:message (tr :notifications/save-success)
                                        :success? true}]]
                           [:dispatch [::set-user-to-edit user]]
                           [:dispatch [::get-users]]]})))

;; Jobs monitoring events

(rf/reg-event-db ::jobs-loading
                 (fn [db [_ loading?]]
                   (assoc-in db [:admin :jobs :loading?] loading?)))

(rf/reg-event-fx ::jobs-error
                 (fn [{:keys [db]} [_ response]]
                   {:db (-> db
                            (assoc-in [:admin :jobs :error]
                                      (or (-> response :response :message)
                                          (-> response :response :error)
                                          (-> response :status-text)
                                          "Failed to fetch jobs data"))
                            (assoc-in [:admin :jobs :loading?] false))}))

(rf/reg-event-db ::jobs-health-success
                 (fn [db [_ data]]
                   (-> db
                       (assoc-in [:admin :jobs :health] data)
                       (assoc-in [:admin :jobs :loading?] false))))

(rf/reg-event-db ::jobs-metrics-success
                 (fn [db [_ data]]
                   (-> db
                       (assoc-in [:admin :jobs :metrics] data)
                       (assoc-in [:admin :jobs :loading?] false))))

(rf/reg-event-fx ::fetch-jobs-health
                 (fn [{:keys [db]} _]
                   {:db (-> db
                            (assoc-in [:admin :jobs :loading?] true)
                            (assoc-in [:admin :jobs :error] nil))
                    :http-xhrio
                    {:method :post
                     :uri (str (:backend-url db) "/actions/get-jobs-health-status")
                     :params {} ; Empty map as required by schema
                     :format (ajax/transit-request-format)
                     :response-format (ajax/transit-response-format
                                       {:reader transit-reader})
                     :headers {:Authorization (str "Token " (-> db :user :login :token))}
                     :on-success [::jobs-health-success]
                     :on-failure [::jobs-error]}}))

(rf/reg-event-fx ::fetch-jobs-metrics
                 (fn [{:keys [db]} _]
                   {:db (-> db
                            (assoc-in [:admin :jobs :loading?] true)
                            (assoc-in [:admin :jobs :error] nil))
                    :http-xhrio
                    {:method :post
                     :uri (str (:backend-url db) "/actions/create-jobs-metrics-report")
                     :params {:from-hours-ago 24} ; Last 24 hours
                     :format (ajax/transit-request-format)
                     :response-format (ajax/transit-response-format
                                       {:reader transit-reader})
                     :headers {:Authorization (str "Token " (-> db :user :login :token))}
                     :on-success [::jobs-metrics-success]
                     :on-failure [::jobs-error]}}))

 ;; Dead Letter Queue events

(rf/reg-event-fx ::fetch-dead-letter-jobs
  (fn [{:keys [db]} [_ {:keys [acknowledged]}]]
    {:db (-> db
             (assoc-in [:admin :jobs :dead-letter :loading?] true)
             (assoc-in [:admin :jobs :dead-letter :error] nil))
     :http-xhrio
     {:method :get
      :uri (str (:backend-url db) "/actions/get-dead-letter-jobs")
      :params (when (some? acknowledged) {:acknowledged acknowledged})
      :format (ajax/transit-request-format)
      :response-format (ajax/transit-response-format
                         {:reader transit-reader})
      :headers {:Authorization (str "Token " (-> db :user :login :token))}
      :on-success [::dead-letter-jobs-success]
      :on-failure [::dead-letter-jobs-error]}}))

(rf/reg-event-db ::dead-letter-jobs-success
  (fn [db [_ jobs]]
    (-> db
        (assoc-in [:admin :jobs :dead-letter :jobs] jobs)
        (assoc-in [:admin :jobs :dead-letter :loading?] false))))

(rf/reg-event-fx ::dead-letter-jobs-error
  (fn [{:keys [db]} [_ response]]
    {:db (-> db
             (assoc-in [:admin :jobs :dead-letter :error]
                       (or (-> response :response :message)
                           (-> response :response :error)
                           (-> response :status-text)
                           "Failed to fetch dead letter jobs"))
             (assoc-in [:admin :jobs :dead-letter :loading?] false))}))

(rf/reg-event-db ::toggle-dead-letter-filter
  (fn [db [_ filter-value]]
    (assoc-in db [:admin :jobs :dead-letter :filter] filter-value)))

(rf/reg-event-db ::select-jobs-sub-tab
  (fn [db [_ tab-value]]
    (assoc-in db [:admin :jobs :selected-sub-tab] tab-value)))

(rf/reg-event-db ::open-job-details-dialog
  (fn [db [_ job-id]]
    (-> db
        (assoc-in [:admin :jobs :dead-letter :selected-job-id] job-id)
        (assoc-in [:admin :jobs :dead-letter :details-dialog-open?] true))))

(rf/reg-event-db ::close-job-details-dialog
  (fn [db _]
    (-> db
        (assoc-in [:admin :jobs :dead-letter :selected-job-id] nil)
        (assoc-in [:admin :jobs :dead-letter :details-dialog-open?] false))))

(rf/reg-event-fx ::reprocess-single-job
  (fn [{:keys [db]} [_ job-id max-attempts]]
    {:db (assoc-in db [:admin :jobs :dead-letter :reprocessing?] true)
     :http-xhrio
     {:method :post
      :uri (str (:backend-url db) "/actions/reprocess-dead-letter-jobs")
      :params (cond-> {:dead-letter-ids [job-id]}
                max-attempts (assoc :max-attempts max-attempts))
      :format (ajax/transit-request-format)
      :response-format (ajax/transit-response-format
                         {:reader transit-reader})
      :headers {:Authorization (str "Token " (-> db :user :login :token))}
      :on-success [::reprocess-job-success]
      :on-failure [::reprocess-job-error]}}))

(rf/reg-event-fx ::reprocess-job-success
  (fn [{:keys [db]} [_ result]]
    (let [tr (:translator db)
          processed (:processed result 0)
          errors (:errors result [])]
      {:db (assoc-in db [:admin :jobs :dead-letter :reprocessing?] false)
       :fx (cond-> [[:dispatch [:lipas.ui.events/set-active-notification]
                     {:message (str (tr :notifications/save-success)
                                    " - " processed " job(s) reprocessed")
                      :success? true}]
                    [:dispatch [::close-job-details-dialog]]
                    [:dispatch [::fetch-dead-letter-jobs]
                     {:acknowledged (case (get-in db [:admin :jobs :dead-letter :filter])
                                      :unacknowledged false
                                      :acknowledged true
                                      nil)}]]
             (seq errors)
             (conj [:dispatch [:lipas.ui.events/set-active-notification]
                    {:message (str "Errors: " (pr-str errors))
                     :success? false}]))})))

(rf/reg-event-fx ::reprocess-job-error
  (fn [{:keys [db]} [_ response]]
    (let [tr (:translator db)]
      {:db (assoc-in db [:admin :jobs :dead-letter :reprocessing?] false)
       :dispatch [:lipas.ui.events/set-active-notification
                  {:message (or (-> response :response :message)
                                (-> response :response :error)
                                (-> response :status-text)
                                (tr :error/unknown))
                   :success? false}]})))

(rf/reg-event-db ::toggle-job-selection
  (fn [db [_ job-id]]
    (update-in db [:admin :jobs :dead-letter :selected-job-ids]
               (fnil (fn [ids]
                       (if (contains? ids job-id)
                         (disj ids job-id)
                         (conj ids job-id)))
                     #{}))))

(rf/reg-event-db ::select-all-jobs
  (fn [db [_ job-ids]]
    (assoc-in db [:admin :jobs :dead-letter :selected-job-ids]
              (set job-ids))))

(rf/reg-event-db ::clear-job-selection
  (fn [db _]
    (assoc-in db [:admin :jobs :dead-letter :selected-job-ids] #{})))

(rf/reg-event-fx ::reprocess-selected-jobs
  (fn [{:keys [db]} [_ max-attempts]]
    (let [selected-ids (vec (get-in db [:admin :jobs :dead-letter :selected-job-ids] #{}))]
      (if (empty? selected-ids)
        {:dispatch [:lipas.ui.events/set-active-notification
                    {:message "No jobs selected"
                     :success? false}]}
        {:db (assoc-in db [:admin :jobs :dead-letter :bulk-reprocessing?] true)
         :http-xhrio
         {:method :post
          :uri (str (:backend-url db) "/actions/reprocess-dead-letter-jobs")
          :params (cond-> {:dead-letter-ids selected-ids}
                    max-attempts (assoc :max-attempts max-attempts))
          :format (ajax/transit-request-format)
          :response-format (ajax/transit-response-format
                             {:reader transit-reader})
          :headers {:Authorization (str "Token " (-> db :user :login :token))}
          :on-success [::bulk-reprocess-success]
          :on-failure [::bulk-reprocess-error]}}))))

(rf/reg-event-fx ::bulk-reprocess-success
  (fn [{:keys [db]} [_ result]]
    (let [tr (:translator db)
          processed (:processed result 0)
          errors (:errors result [])]
      {:db (-> db
               (assoc-in [:admin :jobs :dead-letter :bulk-reprocessing?] false)
               (assoc-in [:admin :jobs :dead-letter :selected-job-ids] #{}))
       :fx (cond-> [[:dispatch [:lipas.ui.events/set-active-notification]
                     {:message (str (tr :notifications/save-success)
                                    " - " processed " job(s) reprocessed")
                      :success? true}]
                    [:dispatch [::fetch-dead-letter-jobs]
                     {:acknowledged (case (get-in db [:admin :jobs :dead-letter :filter])
                                      :unacknowledged false
                                      :acknowledged true
                                      nil)}]]
             (seq errors)
             (conj [:dispatch [:lipas.ui.events/set-active-notification]
                    {:message (str "Errors: " (pr-str errors))
                     :success? false}]))})))

(rf/reg-event-fx ::bulk-reprocess-error
  (fn [{:keys [db]} [_ response]]
    (let [tr (:translator db)]
      {:db (assoc-in db [:admin :jobs :dead-letter :bulk-reprocessing?] false)
       :dispatch [:lipas.ui.events/set-active-notification
                  {:message (or (-> response :response :message)
                                (-> response :response :error)
                                (-> response :status-text)
                                (tr :error/unknown))
                   :success? false}]})))

(rf/reg-event-fx ::acknowledge-single-job
  (fn [{:keys [db]} [_ job-id]]
    {:db (assoc-in db [:admin :jobs :dead-letter :acknowledging?] true)
     :http-xhrio
     {:method :post
      :uri (str (:backend-url db) "/actions/acknowledge-dead-letter-jobs")
      :params {:dead-letter-ids [job-id]}
      :format (ajax/transit-request-format)
      :response-format (ajax/transit-response-format
                         {:reader transit-reader})
      :headers {:Authorization (str "Token " (-> db :user :login :token))}
      :on-success [::acknowledge-success]
      :on-failure [::acknowledge-error]}}))

(rf/reg-event-fx ::acknowledge-selected-jobs
  (fn [{:keys [db]} _]
    (let [selected-ids (vec (get-in db [:admin :jobs :dead-letter :selected-job-ids] #{}))]
      (if (empty? selected-ids)
        {:dispatch [:lipas.ui.events/set-active-notification
                    {:message "No jobs selected"
                     :success? false}]}
        {:db (assoc-in db [:admin :jobs :dead-letter :bulk-acknowledging?] true)
         :http-xhrio
         {:method :post
          :uri (str (:backend-url db) "/actions/acknowledge-dead-letter-jobs")
          :params {:dead-letter-ids selected-ids}
          :format (ajax/transit-request-format)
          :response-format (ajax/transit-response-format
                             {:reader transit-reader})
          :headers {:Authorization (str "Token " (-> db :user :login :token))}
          :on-success [::bulk-acknowledge-success]
          :on-failure [::bulk-acknowledge-error]}}))))

(rf/reg-event-fx ::acknowledge-success
  (fn [{:keys [db]} [_ result]]
    (let [tr (:translator db)
          acknowledged (:acknowledged result 0)]
      {:db (-> db
               (assoc-in [:admin :jobs :dead-letter :acknowledging?] false)
               (assoc-in [:admin :jobs :dead-letter :details-dialog-open?] false))
       :fx [[:dispatch [:lipas.ui.events/set-active-notification]
             {:message (str (tr :notifications/save-success)
                            " - " acknowledged " job(s) acknowledged")
              :success? true}]
            [:dispatch [::fetch-dead-letter-jobs]
             {:acknowledged (case (get-in db [:admin :jobs :dead-letter :filter])
                              :unacknowledged false
                              :acknowledged true
                              nil)}]]})))

(rf/reg-event-fx ::acknowledge-error
  (fn [{:keys [db]} [_ response]]
    (let [tr (:translator db)]
      {:db (assoc-in db [:admin :jobs :dead-letter :acknowledging?] false)
       :dispatch [:lipas.ui.events/set-active-notification
                  {:message (or (-> response :response :message)
                                (-> response :response :error)
                                (-> response :status-text)
                                (tr :error/unknown))
                   :success? false}]})))

(rf/reg-event-fx ::bulk-acknowledge-success
  (fn [{:keys [db]} [_ result]]
    (let [tr (:translator db)
          acknowledged (:acknowledged result 0)]
      {:db (-> db
               (assoc-in [:admin :jobs :dead-letter :bulk-acknowledging?] false)
               (assoc-in [:admin :jobs :dead-letter :selected-job-ids] #{}))
       :fx [[:dispatch [:lipas.ui.events/set-active-notification]
             {:message (str (tr :notifications/save-success)
                            " - " acknowledged " job(s) acknowledged")
              :success? true}]
            [:dispatch [::fetch-dead-letter-jobs]
             {:acknowledged (case (get-in db [:admin :jobs :dead-letter :filter])
                              :unacknowledged false
                              :acknowledged true
                              nil)}]]})))

(rf/reg-event-fx ::bulk-acknowledge-error
  (fn [{:keys [db]} [_ response]]
    (let [tr (:translator db)]
      {:db (assoc-in db [:admin :jobs :dead-letter :bulk-acknowledging?] false)
       :dispatch [:lipas.ui.events/set-active-notification
                  {:message (or (-> response :response :message)
                                (-> response :response :error)
                                (-> response :status-text)
                                (tr :error/unknown))
                   :success? false}]})))

;;; Orgs ;;;

(rf/reg-event-db ::get-orgs-success
                 (fn [db [_ orgs]]
                   (assoc-in db [:admin :orgs] (utils/index-by :id orgs))))

(rf/reg-event-fx ::get-orgs
                 (fn [{:keys [db]} [_ _]]
                   (let [token (-> db :user :login :token)]
                     {:http-xhrio
                      {:method :get
                       :headers {:Authorization (str "Token " token)}
                       :uri (str (:backend-url db) "/orgs")
                       :response-format (ajax/json-response-format {:keywords? true})
                       :on-success [::get-orgs-success]
                       :on-failure [::failure]}})))

(rf/reg-event-db ::get-org-users-success
                 (fn [db [_ org-id users]]
                   (assoc-in db [:admin :org-users org-id] (utils/index-by :id users))))

(rf/reg-event-fx ::get-org-users
                 (fn [{:keys [db]} [_ org-id]]
                   (let [token (-> db :user :login :token)]
                     {:http-xhrio
                      {:method :get
                       :headers {:Authorization (str "Token " token)}
                       :uri (str (:backend-url db) "/orgs/" org-id "/users")
                       :response-format (ajax/json-response-format {:keywords? true})
                       :on-success [::get-org-users-success org-id]
                       :on-failure [::failure]}})))

(rf/reg-event-db ::set-org-to-edit
                 (fn [db [_ id]]
                   (assoc-in db
                             [:admin :editing-org]
                             (when id
                               (if (= "new" id)
                                 {:name "fixme"
                                  :data {}
                                  :ptv-data {}}
                                 (get-in db [:admin :orgs id]))))))

(rf/reg-event-db ::edit-org
                 (fn [db [_ path value]]
                   (assoc-in db (into [:admin :editing-org] path) value)))

(rf/reg-event-fx ::save-org-success
                 (fn [{:keys [db]} [_ _org new? resp]]
                   (let [tr (:translator db)]
                     (when new?
                       (rfe/set-query #(assoc % :edit-id (:id resp))))
                     {:fx [[:dispatch [::get-orgs]]
                           [:dispatch [:lipas.ui.events/set-active-notification
                                       {:message (tr :notifications/save-success)
                                        :success? true}]]]})))

(rf/reg-event-db ::open-add-user-to-org-dialog
                 (fn [db [_ org-id]]
                   (-> db
                       (assoc-in [:admin :add-user-to-org :dialog-open?] true)
                       (assoc-in [:admin :add-user-to-org :org-id] org-id)
                       (assoc-in [:admin :add-user-to-org :email] "")
                       (assoc-in [:admin :add-user-to-org :role] nil))))

(rf/reg-event-db ::close-add-user-to-org-dialog
                 (fn [db [_]]
                   (update-in db [:admin] dissoc :add-user-to-org)))

(rf/reg-event-db ::set-add-user-to-org-email
                 (fn [db [_ email]]
                   (assoc-in db [:admin :add-user-to-org :email] email)))

(rf/reg-event-db ::set-add-user-to-org-role
                 (fn [db [_ role]]
                   (assoc-in db [:admin :add-user-to-org :role] role)))

(rf/reg-event-fx ::add-user-to-org
                 (fn [{:keys [db]} [_ email role org-id]]
                   (let [token (-> db :user :login :token)]
                     {:http-xhrio
                      {:method :post
                       :uri (str (:backend-url db) "/orgs/" org-id "/users")
                       :headers {:Authorization (str "Token " token)}
                       :params {:changes [{:email email :change "add" :role role}]}
                       :format (ajax/json-request-format)
                       :response-format (ajax/json-response-format {:keywords? true})
                       :on-success [::add-user-to-org-success org-id]
                       :on-failure [::failure]}})))

(rf/reg-event-fx ::add-user-to-org-success
                 (fn [{:keys [db]} [_ org-id]]
                   (let [tr (:translator db)]
                     {:dispatch-n [[::close-add-user-to-org-dialog]
                                   [::get-org-users org-id]
                                   [:lipas.ui.events/set-active-notification
                                    {:message (tr :notifications/save-success)
                                     :success? true}]]})))

(rf/reg-event-fx ::save-org
                 (fn [{:keys [db]} [_ org]]
                   (let [token (-> db :user :login :token)
                         body (-> org)
                         new? (nil? (:id org))]
                     {:http-xhrio
                      {:method (if new? :post :put)
                       :uri (if new?
                              (str (:backend-url db) "/orgs")
                              (str (:backend-url db) "/orgs/" (:id org)))
                       :headers {:Authorization (str "Token " token)}
                       :params body
                       :format (ajax/json-request-format)
                       :response-format (ajax/json-response-format {:keywords? true})
                       :on-success [::save-org-success org new?]
                       :on-failure [::failure]}})))


