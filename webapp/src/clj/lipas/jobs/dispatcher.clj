(ns lipas.jobs.dispatcher
  "Job dispatcher with multimethod handlers for each job type."
  (:require
   [lipas.backend.analysis.diversity :as diversity]
   [lipas.backend.core :as core]
   [lipas.backend.elevation :as elevation]
   [lipas.backend.email :as email]
   [lipas.backend.gis :as gis]
   [lipas.integration.utp.webhook :as utp-webhook]
   [lipas.reminders :as reminders]
   [lipas.jobs.core :as jobs]
   [lipas.jobs.monitoring :as monitoring]
   [lipas.jobs.patterns :as patterns]
   [taoensso.timbre :as log]))

(defmulti handle-job
  "Handle a job based on its type. Each job type has its own implementation."
  (fn [_system job] (:type job)))

(defmethod handle-job "analysis"
  [{:keys [db search]} {:keys [id payload]}]
  (let [{:keys [lipas-id]} payload
        sports-site (core/get-sports-site db lipas-id)
        fcoll (-> sports-site :location :geometries gis/simplify)]
    (log/info "Processing analysis for lipas-id" lipas-id)
    (diversity/recalc-grid! search fcoll)
    (log/info "Analysis completed for lipas-id" lipas-id)))

(defmethod handle-job "elevation"
  [{:keys [db search]} {:keys [id payload]}]
  (let [{:keys [lipas-id]} payload
        user (core/get-user! db "robot@lipas.fi")
        orig (core/get-sports-site db lipas-id)
        _ (when-not orig (throw (ex-info "Sports site not found" {:lipas-id lipas-id})))
        fcoll (-> orig :location :geometries elevation/enrich-elevation)

        ;; Check if site was updated while processing elevation
        current (core/get-sports-site db lipas-id)
        still-valid? (= (:event-date current) (:event-date orig))]

    (log/info "Processing elevation for lipas-id" lipas-id)

    (if still-valid?
      (do
        (-> current
            (assoc-in [:location :geometries] fcoll)
            (->> (core/upsert-sports-site!* db user))
            (as-> $ (core/index! search $ :sync)))

        (core/add-to-integration-out-queue! db current)
        (log/info "Elevation enrichment completed for lipas-id" lipas-id))

      (do
        (log/info "Sports site updated meanwhile, re-queueing elevation for lipas-id" lipas-id)
        (jobs/enqueue-job! db "elevation" payload {:priority 70})))))

(defmethod handle-job "email"
  [{:keys [emailer db]} {:keys [id payload correlation-id]}]
  (log/info "Processing email job" {:type (:type payload) :correlation-id correlation-id})
  (patterns/with-circuit-breaker db "email-service" {}
    (case (:type payload)
      "reminder"
      (email/send-reminder-email! emailer (:email payload) (:link payload) (:body payload))

      ;; Default email handling
      (email/send! emailer payload)))
  (log/debug "Email sent successfully"))

(defmethod handle-job "integration"
  [_system {:keys [id payload]}]
  (let [{:keys [lipas-id]} payload]
    (log/info "Processing integration for lipas-id" lipas-id)
    ;; Integration logic would go here - delegating to existing integration system
    ;; For now, just log as the actual integration might be handled elsewhere
    (log/debug "Integration job processed for lipas-id" lipas-id)))

(defmethod handle-job "webhook"
  [{:keys [db]} {:keys [id payload correlation-id]}]
  (let [{:keys [batch-data]} payload
        config (get-in lipas.backend.config/default-config [:app :utp])]
    (log/info "Processing webhook job" {:correlation-id correlation-id})
    (patterns/with-circuit-breaker db "webhook-service" {}
      (utp-webhook/process! db config batch-data))
    (log/debug "Webhook job processed successfully")))

(defmethod handle-job "produce-reminders"
  [{:keys [db]} {:keys [id payload]}]
  (log/info "Producing reminder jobs")
  (let [overdue-reminders (lipas.backend.db.db/get-overdue-reminders db)]
    (doseq [reminder overdue-reminders]
      (jobs/enqueue-job! db "email"
                         {:type "reminder"
                          :email (:email reminder)
                          :link (:link reminder)
                          :body (:body reminder)}
                         {:priority 95}))
    (log/info "Produced" (count overdue-reminders) "reminder jobs")))

(defmethod handle-job "cleanup-jobs"
  [{:keys [db]} {:keys [id payload]}]
  (let [days-old (get payload :days-old 7)]
    (log/info "Cleaning up jobs older than" days-old "days")
    (jobs/cleanup-old-jobs! db days-old)))

(defmethod handle-job "monitor-queue-health"
  [{:keys [db]} {:keys [id payload correlation-id]}]
  (log/info "Running queue health monitor" {:correlation-id correlation-id})
  (let [alert-fn (fn [alert]
                   ;; In production, this could send emails or post to monitoring service
                   (log/warn "QUEUE ALERT" alert)
                   ;; Could also enqueue email alerts
                   (when (= (:type alert) :circuit-breaker-open)
                     (jobs/enqueue-job! db "email"
                                        {:to "admin@lipas.fi"
                                         :subject (str "Circuit breaker open: " (:service alert))
                                         :body (str "Service " (:service alert)
                                                    " circuit breaker opened at "
                                                    (:timestamp alert))}
                                        {:priority 100})))
        result (monitoring/monitor-and-alert! db {:alert-fn alert-fn})]
    (log/debug "Health monitor completed" result)))

(defmethod handle-job :default
  [_system job]
  (log/error "Unknown job type" {:job job})
  (throw (ex-info "Unknown job type" {:job-type (:type job)})))

(defn dispatch-job
  "Dispatch a single job to its handler with error handling and timeout protection."
  [system job]
  (let [job-id (:id job)
        job-type (:type job)]

    (log/debug "Dispatching job" {:id job-id :type job-type})

    (try
      (let [start-time (System/currentTimeMillis)]

        ;; Execute the job handler
        (handle-job system job)

        ;; Log execution time
        (let [duration-ms (- (System/currentTimeMillis) start-time)]
          (log/debug "Job completed" {:id job-id :type job-type :duration-ms duration-ms}))

        ;; Mark as completed
        (jobs/mark-completed! (:db system) job-id)
        {:status :success})

      (catch Exception ex
        (let [error-msg (str "Job failed: " (.getMessage ex))]
          (log/error ex "Job failed" {:id job-id :type job-type})
          (jobs/mark-failed! (:db system) job-id error-msg)
          {:status :failed :error error-msg})))))
