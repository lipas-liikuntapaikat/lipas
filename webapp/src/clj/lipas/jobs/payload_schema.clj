(ns lipas.jobs.payload-schema
  "Malli schemas for job payloads providing validation and documentation."
  (:require
   [malli.core :as m]
   [malli.error :as me]
   [malli.transform :as mt]
   [taoensso.timbre :as log]))

;; Individual job payload schemas
(def analysis-payload-schema
  [:map
   [:lipas-id pos-int?]])

(def elevation-payload-schema
  [:map
   [:lipas-id pos-int?]])

(def email-payload-schema
  [:or
   ;; Reminder email
   [:map
    [:type [:= "reminder"]]
    [:email :string]
    [:link :string]
    [:body :string]]
   ;; General email
   [:map
    [:to :string]
    [:subject :string]
    [:body :string]]])

(def integration-payload-schema
  [:map
   [:lipas-id pos-int?]])

(def webhook-payload-schema
  [:map
   [:lipas-ids {:optional true} [:vector pos-int?]]
   [:loi-ids {:optional true} [:vector pos-int?]]
   [:operation-type {:optional true} :string]
   [:initiated-by {:optional true} :string]
   [:site-count {:optional true} pos-int?]])

(def produce-reminders-payload-schema
  [:map])

(def cleanup-jobs-payload-schema
  [:map
   [:days-old {:optional true :default 7} pos-int?]])

(def monitor-queue-health-payload-schema
  [:map])

;; Multi-schema dispatched on job type
(def job-payload-schema
  [:multi {:dispatch (fn [job] (:type job))}
   ["analysis" [:map
                [:type [:= "analysis"]]
                [:payload analysis-payload-schema]]]
   ["elevation" [:map
                 [:type [:= "elevation"]]
                 [:payload elevation-payload-schema]]]
   ["email" [:map
             [:type [:= "email"]]
             [:payload email-payload-schema]]]
   ["integration" [:map
                   [:type [:= "integration"]]
                   [:payload integration-payload-schema]]]
   ["webhook" [:map
               [:type [:= "webhook"]]
               [:payload webhook-payload-schema]]]
   ["produce-reminders" [:map
                         [:type [:= "produce-reminders"]]
                         [:payload produce-reminders-payload-schema]]]
   ["cleanup-jobs" [:map
                    [:type [:= "cleanup-jobs"]]
                    [:payload cleanup-jobs-payload-schema]]]
   ["monitor-queue-health" [:map
                            [:type [:= "monitor-queue-health"]]
                            [:payload monitor-queue-health-payload-schema]]]])

;; Validation functions
(defn validate-job-payload
  "Validate a complete job with type and payload.
   Returns {:valid? boolean :errors [...] :value ...}"
  [job]
  (try
    (if (m/validate job-payload-schema job)
      {:valid? true :value job}
      {:valid? false
       :errors (-> job-payload-schema
                   (m/explain job)
                   (me/humanize))})
    (catch Exception e
      {:valid? false
       :errors {:exception (.getMessage e)}})))

(defn validate-payload-for-type
  "Validate just a payload for a specific job type.
   Returns {:valid? boolean :errors [...] :value ...}"
  [job-type payload]
  (let [schema (case job-type
                 "analysis" analysis-payload-schema
                 "elevation" elevation-payload-schema
                 "email" email-payload-schema
                 "integration" integration-payload-schema
                 "webhook" webhook-payload-schema
                 "produce-reminders" produce-reminders-payload-schema
                 "cleanup-jobs" cleanup-jobs-payload-schema
                 "monitor-queue-health" monitor-queue-health-payload-schema
                 (throw (ex-info "Unknown job type" {:job-type job-type})))]
    (try
      (if (m/validate schema payload)
        {:valid? true :value payload}
        {:valid? false
         :errors (-> schema
                     (m/explain payload)
                     (me/humanize))})
      (catch Exception e
        {:valid? false
         :errors {:exception (.getMessage e)}}))))

(defn transform-payload
  "Apply default values and transformations to a payload."
  [job-type payload]
  (let [schema (case job-type
                 "analysis" analysis-payload-schema
                 "elevation" elevation-payload-schema
                 "email" email-payload-schema
                 "integration" integration-payload-schema
                 "webhook" webhook-payload-schema
                 "produce-reminders" produce-reminders-payload-schema
                 "cleanup-jobs" cleanup-jobs-payload-schema
                 "monitor-queue-health" monitor-queue-health-payload-schema
                 (throw (ex-info "Unknown job type" {:job-type job-type})))
        transformer (mt/transformer mt/default-value-transformer)]
    (m/decode schema payload transformer)))

(defn explain-job-schema
  "Get human-readable explanation of job schema for documentation."
  []
  (->> job-payload-schema
       (m/schema)
       (m/form)))

;; Validation middleware
(defn validate-enqueue-job!
  "Middleware to validate job payloads before enqueueing."
  [enqueue-fn]
  (fn [db job-type payload & [opts]]
    (let [validation (validate-payload-for-type job-type payload)]
      (if (:valid? validation)
        (let [transformed-payload (transform-payload job-type payload)]
          (enqueue-fn db job-type transformed-payload opts))
        (do
          (log/error "Invalid job payload"
                     {:job-type job-type
                      :payload payload
                      :errors (:errors validation)})
          (throw (ex-info "Invalid job payload"
                          {:job-type job-type
                           :payload payload
                           :errors (:errors validation)})))))))

(defn example-payloads
  "Generate example payloads for each job type for documentation/testing."
  []
  {:analysis {:lipas-id 12345}
   :elevation {:lipas-id 67890}
   :email-reminder {:type "reminder"
                    :email "user@example.com"
                    :link "https://example.com/reminder"
                    :body "Don't forget to update your profile!"}
   :email-general {:to "admin@example.com"
                   :subject "System Alert"
                   :body "Database backup completed successfully."}
   :integration {:lipas-id 11111}
   :webhook {:lipas-ids [1 2 3]
             :loi-ids [4 5]
             :operation-type "bulk-import"
             :initiated-by "admin@lipas.fi"}
   :webhook-single {:lipas-ids [123]}
   :produce-reminders {}
   :cleanup-jobs {:days-old 30}
   :monitor-queue-health {}})
