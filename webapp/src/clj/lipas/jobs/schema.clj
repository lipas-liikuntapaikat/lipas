(ns lipas.jobs.schema
  "Malli schemas for job queue admin endpoints.

  Job type and payload schemas live in lipas.jobs.registry."
  (:require
   [lipas.jobs.registry :as registry]
   [malli.core :as m]))

(def job-type-schema registry/job-type-schema)
(def job-status-schema registry/job-status-schema)

;; Request schemas

(def jobs-metrics-request-schema
  "Schema for create-jobs-metrics-report request"
  [:map
   [:from-hours-ago {:optional true} [:int {:min 1 :max 168}]] ; max 1 week back
   [:to-hours-ago {:optional true} [:int {:min 0 :max 167}]]])

(def jobs-health-request-schema
  "Schema for get-jobs-health-status request"
  [:map])

(def search-jobs-request-schema
  "Schema for search-jobs request"
  [:map
   [:statuses [:vector job-status-schema]]
   [:limit {:optional true} [:int {:min 1 :max 500}]]])

;; Response schemas

(def current-stats-entry-schema
  [:map
   [:status {:optional true} :string]
   [:count :int]
   [:oldest_created_at [:maybe [:or :string inst?]]]
   [:oldest_minutes [:maybe number?]]])

(def current-stats-schema
  [:map
   [:pending {:optional true} current-stats-entry-schema]
   [:processing {:optional true} current-stats-entry-schema]
   [:completed {:optional true} current-stats-entry-schema]
   [:total {:optional true} current-stats-entry-schema]])

(def health-schema
  [:map
   [:pending_count :int]
   [:processing_count :int]
   [:retrying_count :int]
   [:dead_count :int]
   [:oldest_pending_minutes [:maybe :int]]
   [:longest_processing_minutes [:maybe :int]]])

(def performance-metric-schema
  ;; :type and :status are open strings: within the retention window the
  ;; jobs table can hold rows of types/statuses that are no longer
  ;; registered, and response coercion must not 500 on them.
  [:map
   [:type :string]
   [:status :string]
   [:job_count :int]
   [:avg_duration_seconds [:maybe number?]]
   [:p50_duration_seconds [:maybe number?]]
   [:p95_duration_seconds [:maybe number?]]
   [:avg_attempts [:maybe number?]]
   [:earliest_job [:maybe :string]]
   [:latest_job [:maybe :string]]])

(def hourly-throughput-entry-schema
  [:map
   [:hour :string]
   [:type :string]
   [:status [:enum "completed" "retried" "dead_lettered"]]
   [:job_count :int]])

(def jobs-metrics-response-schema
  "Schema for create-jobs-metrics-report response"
  [:map
   [:current-stats current-stats-schema]
   [:health health-schema]
   [:performance-metrics [:vector performance-metric-schema]]
   [:hourly-throughput [:vector hourly-throughput-entry-schema]]
   [:fast-job-types [:vector :string]]
   [:slow-job-types [:vector :string]]
   [:generated-at :string]])

(def jobs-health-response-schema
  "Schema for get-jobs-health-status response"
  health-schema)

(def job-row-schema
  "A single row in the search-jobs response.
  :type is an open string because the table can hold rows of job types
  that are no longer registered (retention keeps completed jobs 30 days)."
  [:map
   [:id :int]
   [:type :string]
   [:status job-status-schema]
   [:payload [:maybe map?]]
   [:priority :int]
   [:attempts :int]
   [:max-attempts :int]
   [:created-at inst?]
   [:run-at inst?]
   [:started-at [:maybe inst?]]
   [:completed-at [:maybe inst?]]
   [:last-error [:maybe :string]]])

(def search-jobs-response-schema
  "Schema for search-jobs response"
  [:vector job-row-schema])

;; Validation helpers

(defn valid-metrics-response? [data]
  (m/validate jobs-metrics-response-schema data))

(defn valid-health-response? [data]
  (m/validate jobs-health-response-schema data))

(defn explain-metrics-response [data]
  (m/explain jobs-metrics-response-schema data))

(defn explain-health-response [data]
  (m/explain jobs-health-response-schema data))
