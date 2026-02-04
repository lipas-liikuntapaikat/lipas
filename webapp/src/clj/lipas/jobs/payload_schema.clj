(ns lipas.jobs.payload-schema
  "Malli schemas for job payloads providing validation and documentation."
  (:require
   [malli.core :as m]
   [malli.error :as me]
   [malli.transform :as mt]))

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
    [:link
     [:map
      [:link :string]
      [:valid-days :int]]]
    [:body
     [:map
      [:message :string]]]]
   ;; General email
   [:map
    [:to :string]
    [:subject :string]
    [:body :string]]])

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

;; Single source of truth for job type to schema mapping
(def schemas-by-type
  {"analysis" analysis-payload-schema
   "elevation" elevation-payload-schema
   "email" email-payload-schema
   "webhook" webhook-payload-schema
   "produce-reminders" produce-reminders-payload-schema
   "cleanup-jobs" cleanup-jobs-payload-schema})

(defn validate-payload-for-type
  "Validate just a payload for a specific job type.
   Returns {:valid? boolean :errors [...] :value ...}"
  [job-type payload]
  (if-let [schema (get schemas-by-type job-type)]
    (try
      (if (m/validate schema payload)
        {:valid? true :value payload}
        {:valid? false
         :errors (-> schema
                     (m/explain payload)
                     (me/humanize))})
      (catch Exception e
        {:valid? false
         :errors {:exception (.getMessage e)}}))
    (throw (ex-info "Unknown job type" {:job-type job-type}))))

(defn transform-payload
  "Apply default values and transformations to a payload."
  [job-type payload]
  (if-let [schema (get schemas-by-type job-type)]
    (let [transformer (mt/transformer mt/default-value-transformer)]
      (m/decode schema payload transformer))
    (throw (ex-info "Unknown job type" {:job-type job-type}))))
