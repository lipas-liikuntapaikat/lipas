(ns lipas.jobs.schema
  "Malli schemas for job queue admin endpoints."
  (:require [malli.core :as m]))

;; Request schemas
(def jobs-metrics-request-schema
  "Schema for create-jobs-metrics-report request"
  [:map
   [:from-hours-ago {:optional true} [:int {:min 1 :max 168}]] ; max 1 week back
   [:to-hours-ago {:optional true} [:int {:min 0 :max 167}]]]) ; can't be >= from-hours-ago

(def jobs-health-request-schema
  "Schema for get-jobs-health-status request"
  [:map])

;; Job type schemas
(def job-type-schema
  [:enum "analysis" "elevation" "email" "integration" "webhook"
   "produce-reminders" "cleanup-jobs"])

(def job-status-schema
  [:enum "pending" "processing" "completed" "failed" "dead"])

;; Response schemas
(def current-stats-entry-schema
  "Schema for individual current stats entry"
  [:map
   [:status {:optional true} :string] ; Changed from keyword? to :string
   [:count :int]
   [:oldest_created_at [:maybe [:or :string inst?]]]
   [:oldest_minutes [:maybe number?]]])

(def current-stats-schema
  "Schema for current-stats section"
  [:map
   [:pending {:optional true} current-stats-entry-schema]
   [:processing {:optional true} current-stats-entry-schema]
   [:completed {:optional true} current-stats-entry-schema]
   [:failed {:optional true} current-stats-entry-schema]
   [:dead {:optional true} current-stats-entry-schema]
   [:total {:optional true} current-stats-entry-schema]])

(def health-schema
  "Schema for health metrics"
  [:map
   [:pending_count :int]
   [:processing_count :int]
   [:failed_count :int]
   [:dead_count :int]
   [:oldest_pending_minutes [:maybe :int]]
   [:longest_processing_minutes [:maybe :int]]])

(def performance-metric-schema
  "Schema for individual performance metric"
  [:map
   [:type job-type-schema]
   [:status job-status-schema]
   [:job_count :int]
   [:avg_duration_seconds [:maybe number?]]
   [:p50_duration_seconds [:maybe number?]]
   [:p95_duration_seconds [:maybe number?]]
   [:avg_attempts [:maybe number?]]
   [:earliest_job [:maybe :string]]
   [:latest_job [:maybe :string]]])

(def hourly-throughput-entry-schema
  "Schema for hourly throughput entry"
  [:map
   [:hour :string]
   [:type job-type-schema]
   [:status job-status-schema]
   [:job_count :int]])

(def jobs-metrics-response-schema
  "Schema for create-jobs-metrics-report response"
  [:map
   [:current-stats current-stats-schema]
   [:health health-schema]
   [:performance-metrics [:vector performance-metric-schema]]
   [:hourly-throughput [:vector hourly-throughput-entry-schema]]
   [:fast-job-types [:vector :string]] ; Changed from [:set :string] to [:vector :string]
   [:slow-job-types [:vector :string]] ; Changed from [:set :string] to [:vector :string]
   [:generated-at :string]])

(def jobs-health-response-schema
  "Schema for get-jobs-health-status response"
  health-schema)

;; Validation helpers
(defn valid-metrics-request? [data]
  (m/validate jobs-metrics-request-schema data))

(defn valid-health-request? [data]
  (m/validate jobs-health-request-schema data))

(defn valid-metrics-response? [data]
  (m/validate jobs-metrics-response-schema data))

(defn valid-health-response? [data]
  (m/validate jobs-health-response-schema data))

(defn explain-metrics-response [data]
  (m/explain jobs-metrics-response-schema data))

(defn explain-health-response [data]
  (m/explain jobs-health-response-schema data))
