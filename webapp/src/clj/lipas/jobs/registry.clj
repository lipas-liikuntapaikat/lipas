(ns lipas.jobs.registry
  "Single source of truth for all job types in the LIPAS job queue.

  Each entry defines everything the queue needs to know about a job type:

  :payload-schema  Malli schema validated at enqueue time
  :lane            :fast or :slow - worker thread lane
  :timeout-min     hard execution timeout enforced by the worker watchdog
  :priority        default priority (higher runs first)
  :max-attempts    default retry attempts before dead-lettering
  :dedup-key-fn    optional (fn [payload] -> string); when present, enqueue
                   is a no-op if a pending job with the same key exists
  :debounce-sec    optional delay before the job becomes runnable; combined
                   with :dedup-key-fn this coalesces bursts of edits

  Job handlers are defmethods on lipas.jobs.dispatcher/handle-job. The worker
  asserts at startup that every type registered here has a handler."
  (:require
   [lipas.schema.sports-sites :as sports-sites-schema]
   [malli.core :as m]
   [malli.error :as me]))

(def job-types
  {"analysis"
   {:doc "Recalculate diversity grids around a sports site."
    :payload-schema [:map [:lipas-id #'sports-sites-schema/lipas-id]]
    :lane :slow
    :timeout-min 60
    :priority 80
    :max-attempts 3
    :dedup-key-fn (fn [payload] (str "analysis:" (:lipas-id payload)))
    :debounce-sec 30}

   "elevation"
   {:doc "Enrich route geometries with elevation data from MML."
    :payload-schema [:map [:lipas-id #'sports-sites-schema/lipas-id]]
    :lane :slow
    :timeout-min 15
    :priority 70
    :max-attempts 3
    :dedup-key-fn (fn [payload] (str "elevation:" (:lipas-id payload)))
    :debounce-sec 30}

   "email"
   {:doc "Send a single email (reminders and general mail)."
    :payload-schema [:or
                     ;; Reminder email
                     [:map
                      [:type [:= "reminder"]]
                      [:email :string]
                      [:link [:map
                              [:link :string]
                              [:valid-days :int]]]
                      [:body [:map
                              [:message :string]]]]
                     ;; General email
                     [:map
                      [:to :string]
                      [:subject :string]
                      [:body :string]]]
    :lane :fast
    :timeout-min 1
    :priority 95
    :max-attempts 3}

   ;; NOTE: webhook has no live producers. The enqueue sites are kept
   ;; commented out in lipas.backend.core until UTP (or someone else)
   ;; starts consuming webhooks again. The type stays registered so the
   ;; queue-side integration point is ready when that happens.
   "webhook"
   {:doc "Notify external systems (UTP) about changed sites/LOIs."
    :payload-schema [:map
                     [:lipas-ids {:optional true} [:vector #'sports-sites-schema/lipas-id]]
                     [:loi-ids {:optional true} [:vector pos-int?]]
                     [:operation-type {:optional true} :string]
                     [:initiated-by {:optional true} :string]
                     [:site-count {:optional true} pos-int?]]
    :lane :fast
    :timeout-min 2
    :priority 100
    :max-attempts 3}})

(def job-type-schema
  "Malli enum of all registered job types."
  (into [:enum] (sort (keys job-types))))

(def job-status-schema
  [:enum "pending" "processing" "completed"])

(defn get-def
  "Get the registry entry for a job type. Throws on unknown type."
  [job-type]
  (or (get job-types job-type)
      (throw (ex-info "Unknown job type" {:job-type job-type}))))

(def fast-job-types
  (into #{} (for [[t d] job-types :when (= :fast (:lane d))] t)))

(def slow-job-types
  (into #{} (for [[t d] job-types :when (= :slow (:lane d))] t)))

(defn fast-job? [job-type]
  (contains? fast-job-types job-type))

(defn timeout-ms [job-type]
  (* 60000 (:timeout-min (get-def job-type))))

(defn validate-payload
  "Validate a payload against the job type's schema.
  Returns {:valid? true} or {:valid? false :errors <humanized>}."
  [job-type payload]
  (let [schema (:payload-schema (get-def job-type))]
    (if (m/validate schema payload)
      {:valid? true}
      {:valid? false
       :errors (me/humanize (m/explain schema payload))})))
