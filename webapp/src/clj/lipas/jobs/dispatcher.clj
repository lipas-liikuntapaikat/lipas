(ns lipas.jobs.dispatcher
  "Job handlers for each registered job type.

  Every type in lipas.jobs.registry/job-types must have a handle-job
  defmethod here; the worker asserts this at startup. Handlers either
  return normally (job completed) or throw (job retried / dead-lettered
  by the worker)."
  (:require
   [lipas.backend.analysis.diversity :as diversity]
   [lipas.backend.config :as config]
   [lipas.backend.core :as core]
   [lipas.backend.elevation :as elevation]
   [lipas.backend.email :as email]
   [lipas.backend.gis :as gis]
   [lipas.backend.kb :as kb]
   [lipas.integration.utp.webhook :as utp-webhook]
   [lipas.jobs.core :as jobs]
   [lipas.jobs.patterns :as patterns]
   [taoensso.timbre :as log]))

(defmulti handle-job
  "Handle a job based on its type."
  (fn [_system job] (:type job)))

(def analysis-statuses
  #{"active" "planned" "out-of-service-temporarily"})

(defmethod handle-job "analysis"
  [{:keys [db search]} {:keys [payload]}]
  (let [{:keys [lipas-id]} payload
        sports-site (core/get-sports-site db lipas-id)]

    (when-not sports-site
      (throw (ex-info (str "Sports site not found with lipas-id " lipas-id)
                      {:lipas-id lipas-id
                       :job-type "analysis"})))

    (if (contains? analysis-statuses (:status sports-site))
      (let [fcoll (-> sports-site :location :geometries gis/simplify-safe)]
        (log/info "Processing analysis for lipas-id" lipas-id)
        (diversity/recalc-grid! search fcoll)
        (log/info "Analysis completed for lipas-id" lipas-id))
      (log/info "Skipping analysis for lipas-id" lipas-id
                "due to" (:status sports-site) "status"))))

(defmethod handle-job "elevation"
  [{:keys [db search]} {:keys [payload]}]
  (let [{:keys [lipas-id]} payload
        user (core/get-user! db "robot@lipas.fi")
        orig (core/get-sports-site db lipas-id)
        _ (when-not orig (throw (ex-info "Sports site not found" {:lipas-id lipas-id})))

        ;; Circuit breaker protects against MML API outages: after repeated
        ;; failures, jobs fail fast (and retry later) instead of hammering
        ;; the API with long-running requests
        fcoll (patterns/with-circuit-breaker "mml-elevation-service"
                {:failure-threshold 5
                 :open-duration-ms 120000}
                (-> orig :location :geometries elevation/enrich-elevation))

        ;; Check if the site was updated while processing elevation
        current (core/get-sports-site db lipas-id)
        still-valid? (= (:event-date current) (:event-date orig))]

    (log/info "Processing elevation for lipas-id" lipas-id)

    (if still-valid?
      (do
        ;; Deliberately writes back via the low-level upsert + direct
        ;; index instead of core/save-sports-site!: the robot's enrichment
        ;; revision must not enqueue new analysis/elevation jobs, or every
        ;; completed elevation job would trigger another save-jobs cycle.
        (-> current
            (assoc-in [:location :geometries] fcoll)
            (->> (core/upsert-sports-site!* db user))
            (as-> $ (core/index! search $ :sync (core/org-names db))))
        (log/info "Elevation enrichment completed for lipas-id" lipas-id))

      (do
        (log/info "Sports site updated meanwhile, re-queueing elevation for lipas-id" lipas-id)
        (jobs/enqueue-job! db "elevation" payload)))))

(defmethod handle-job "email"
  [{:keys [emailer]} {:keys [payload]}]
  (log/info "Processing email job" {:type (:type payload)})
  (case (:type payload)
    "reminder"
    (email/send-reminder-email! emailer (:email payload) (:link payload) (:body payload))

    ;; Default email handling
    (email/send! emailer payload)))

(defmethod handle-job "help-kb-sync"
  [{:keys [db search]} _job]
  (kb/sync! db search))

(defmethod handle-job "webhook"
  [{:keys [db]} {:keys [id payload]}]
  (let [utp-config (get-in config/default-config [:app :utp])]
    (log/info "Processing webhook job"
              {:job-id id
               :lipas-ids-count (count (:lipas-ids payload []))
               :loi-ids-count (count (:loi-ids payload []))
               :operation-type (:operation-type payload)})
    (utp-webhook/process-v2! db utp-config payload)))

(defmethod handle-job :default
  [_system job]
  (log/error "Unknown job type" {:job job})
  (throw (ex-info "Unknown job type" {:job-type (:type job)})))
