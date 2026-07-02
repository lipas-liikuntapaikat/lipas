(ns lipas.ui.admin.jobs.events
  "Events for the jobs admin UI (overview + dead letters tabs).
  App-db state lives under [:admin :jobs ...]."
  (:require [ajax.core :as ajax]
            [cognitect.transit :as t]
            [re-frame.core :as rf]))

(def transit-extra-read-handlers
  {"f" #(js/parseFloat %) ; BigDecimal -> float
   "n" #(js/parseInt % 10) ; BigInteger -> int
   "r" (fn [[n d]] ; Ratio -> decimal
         (/ (js/parseFloat n) (js/parseFloat d)))})

(def transit-reader (t/reader :json {:handlers transit-extra-read-handlers}))

(defn- api-request
  [db {:keys [method uri params on-success on-failure]
       :or {method :post}}]
  {:method method
   :uri (str (:backend-url db) uri)
   :params params
   :format (ajax/transit-request-format)
   :response-format (ajax/transit-response-format {:reader transit-reader})
   :headers {:Authorization (str "Token " (-> db :user :login :token))}
   :on-success on-success
   :on-failure (or on-failure [::jobs-error])})

(defn- error-message [response]
  (or (-> response :response :message)
      (-> response :response :error)
      (-> response :status-text)
      "Request failed"))

(rf/reg-event-db ::select-sub-tab
  (fn [db [_ tab-value]]
    (assoc-in db [:admin :jobs :selected-sub-tab] tab-value)))

(rf/reg-event-db ::jobs-error
  (fn [db [_ response]]
    (-> db
        (assoc-in [:admin :jobs :error] (error-message response))
        (assoc-in [:admin :jobs :loading?] false))))

;; Overview data

(rf/reg-event-fx ::fetch-health
  (fn [{:keys [db]} _]
    {:http-xhrio (api-request db {:uri "/actions/get-jobs-health-status"
                                  :params {}
                                  :on-success [::health-success]})}))

(rf/reg-event-db ::health-success
  (fn [db [_ data]]
    (-> db
        (assoc-in [:admin :jobs :health] data)
        (assoc-in [:admin :jobs :loading?] false))))

(rf/reg-event-fx ::fetch-metrics
  (fn [{:keys [db]} _]
    (let [window (get-in db [:admin :jobs :throughput-window] 24)]
      {:http-xhrio (api-request db {:uri "/actions/create-jobs-metrics-report"
                                    :params {:from-hours-ago window}
                                    :on-success [::metrics-success]})})))

(rf/reg-event-db ::metrics-success
  (fn [db [_ data]]
    (-> db
        (assoc-in [:admin :jobs :metrics] data)
        (assoc-in [:admin :jobs :loading?] false))))

(rf/reg-event-fx ::set-throughput-window
  (fn [{:keys [db]} [_ hours]]
    {:db (assoc-in db [:admin :jobs :throughput-window] hours)
     :dispatch [::fetch-metrics]}))

(rf/reg-event-fx ::fetch-queue
  (fn [{:keys [db]} _]
    {:http-xhrio (api-request db {:uri "/actions/search-jobs"
                                  :params {:statuses ["pending" "processing"]
                                           :limit 100}
                                  :on-success [::queue-success]})}))

(rf/reg-event-db ::queue-success
  (fn [db [_ jobs]]
    (assoc-in db [:admin :jobs :queue] jobs)))

(rf/reg-event-fx ::fetch-recent
  (fn [{:keys [db]} _]
    {:http-xhrio (api-request db {:uri "/actions/search-jobs"
                                  :params {:statuses ["completed"]
                                           :limit 20}
                                  :on-success [::recent-success]})}))

(rf/reg-event-db ::recent-success
  (fn [db [_ jobs]]
    (assoc-in db [:admin :jobs :recent] jobs)))

(rf/reg-event-fx ::refresh-all
  (fn [{:keys [db]} _]
    {:db (-> db
             (assoc-in [:admin :jobs :loading?] true)
             (assoc-in [:admin :jobs :error] nil))
     :fx [[:dispatch [::fetch-health]]
          [:dispatch [::fetch-metrics]]
          [:dispatch [::fetch-queue]]
          [:dispatch [::fetch-recent]]
          [:dispatch [::fetch-dead-letters]]]}))

;; Auto-refresh polling. The loop lives while the jobs view is mounted;
;; :poll-token invalidates stale :dispatch-later ticks after re-mounts.

(def poll-interval-ms 30000)

(rf/reg-event-fx ::start-polling
  (fn [{:keys [db]} _]
    (let [token (inc (get-in db [:admin :jobs :poll-token] 0))]
      {:db (-> db
               (assoc-in [:admin :jobs :polling?] true)
               (assoc-in [:admin :jobs :poll-token] token))
       :fx [[:dispatch [::refresh-all]]
            [:dispatch-later {:ms poll-interval-ms :dispatch [::poll token]}]]})))

(rf/reg-event-fx ::poll
  (fn [{:keys [db]} [_ token]]
    (when (and (get-in db [:admin :jobs :polling?])
               (= token (get-in db [:admin :jobs :poll-token])))
      {:fx (cond-> [[:dispatch-later {:ms poll-interval-ms :dispatch [::poll token]}]]
             (get-in db [:admin :jobs :auto-refresh?] true)
             (conj [:dispatch [::refresh-all]]))})))

(rf/reg-event-db ::stop-polling
  (fn [db _]
    (assoc-in db [:admin :jobs :polling?] false)))

(rf/reg-event-db ::toggle-auto-refresh
  (fn [db _]
    (update-in db [:admin :jobs :auto-refresh?] (fnil not true))))

;; Dead letter queue
;;
;; The whole DLQ is fetched (bounded by retention) and filtered
;; client-side so group trend stats always see the full history.

(rf/reg-event-fx ::fetch-dead-letters
  (fn [{:keys [db]} _]
    {:db (-> db
             (assoc-in [:admin :jobs :dead-letter :loading?] true)
             (assoc-in [:admin :jobs :dead-letter :error] nil))
     :http-xhrio (api-request db {:method :get
                                  :uri "/actions/get-dead-letter-jobs"
                                  :on-success [::dead-letters-success]
                                  :on-failure [::dead-letters-error]})}))

(rf/reg-event-db ::dead-letters-success
  (fn [db [_ jobs]]
    (-> db
        (assoc-in [:admin :jobs :dead-letter :jobs] jobs)
        (assoc-in [:admin :jobs :dead-letter :loading?] false))))

(rf/reg-event-db ::dead-letters-error
  (fn [db [_ response]]
    (-> db
        (assoc-in [:admin :jobs :dead-letter :error] (error-message response))
        (assoc-in [:admin :jobs :dead-letter :loading?] false))))

(rf/reg-event-db ::set-dead-letter-filter
  (fn [db [_ filter-value]]
    (assoc-in db [:admin :jobs :dead-letter :filter] filter-value)))

(rf/reg-event-db ::set-site-filter
  (fn [db [_ lipas-id]]
    (assoc-in db [:admin :jobs :dead-letter :site-filter] lipas-id)))

(rf/reg-event-db ::open-job-details-dialog
  (fn [db [_ job-id]]
    (assoc-in db [:admin :jobs :dead-letter :selected-job-id] job-id)))

(rf/reg-event-db ::close-job-details-dialog
  (fn [db _]
    (assoc-in db [:admin :jobs :dead-letter :selected-job-id] nil)))

(rf/reg-event-fx ::reprocess-jobs
  (fn [{:keys [db]} [_ dead-letter-ids max-attempts]]
    {:db (assoc-in db [:admin :jobs :dead-letter :reprocessing?] true)
     :http-xhrio (api-request db {:uri "/actions/reprocess-dead-letter-jobs"
                                  :params (cond-> {:dead-letter-ids (vec dead-letter-ids)}
                                            max-attempts (assoc :max-attempts max-attempts))
                                  :on-success [::reprocess-success]
                                  :on-failure [::reprocess-error]})}))

(rf/reg-event-fx ::reprocess-success
  (fn [{:keys [db]} [_ result]]
    (let [succeeded (count (:succeeded result))
          failed (:failed result)]
      {:db (assoc-in db [:admin :jobs :dead-letter :reprocessing?] false)
       :fx (cond-> [[:dispatch [:lipas.ui.events/set-active-notification
                                {:message (str succeeded " job(s) requeued")
                                 :success? true}]]
                    [:dispatch [::close-job-details-dialog]]
                    [:dispatch [::fetch-dead-letters]]
                    [:dispatch [::fetch-health]]
                    [:dispatch [::fetch-queue]]]
             (seq failed)
             (conj [:dispatch [:lipas.ui.events/set-active-notification
                               {:message (str (count failed) " job(s) failed to requeue: "
                                              (pr-str (map :error failed)))
                                :success? false}]]))})))

(rf/reg-event-fx ::reprocess-error
  (fn [{:keys [db]} [_ response]]
    {:db (assoc-in db [:admin :jobs :dead-letter :reprocessing?] false)
     :dispatch [:lipas.ui.events/set-active-notification
                {:message (error-message response)
                 :success? false}]}))

(rf/reg-event-fx ::acknowledge-jobs
  (fn [{:keys [db]} [_ dead-letter-ids]]
    {:db (assoc-in db [:admin :jobs :dead-letter :acknowledging?] true)
     :http-xhrio (api-request db {:uri "/actions/acknowledge-dead-letter-jobs"
                                  :params {:dead-letter-ids (vec dead-letter-ids)}
                                  :on-success [::acknowledge-success]
                                  :on-failure [::acknowledge-error]})}))

(rf/reg-event-fx ::acknowledge-success
  (fn [{:keys [db]} [_ result]]
    {:db (assoc-in db [:admin :jobs :dead-letter :acknowledging?] false)
     :fx [[:dispatch [:lipas.ui.events/set-active-notification
                      {:message (str (:acknowledged result 0) " job(s) acknowledged")
                       :success? true}]]
          [:dispatch [::close-job-details-dialog]]
          [:dispatch [::fetch-dead-letters]]
          [:dispatch [::fetch-health]]]}))

(rf/reg-event-fx ::acknowledge-error
  (fn [{:keys [db]} [_ response]]
    {:db (assoc-in db [:admin :jobs :dead-letter :acknowledging?] false)
     :dispatch [:lipas.ui.events/set-active-notification
                {:message (error-message response)
                 :success? false}]}))
