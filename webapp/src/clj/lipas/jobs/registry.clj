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
  :trigger-fn      optional (fn [old-site new-site] -> boolean) consulted by
                   save-sports-site! (see should-enqueue?): the job is only
                   enqueued when the inputs it depends on changed in the
                   save. Absent means always enqueue.

  Job handlers are defmethods on lipas.jobs.dispatcher/handle-job. The worker
  asserts at startup that every type registered here has a handler."
  (:require
   [lipas.data.types :as types]
   [lipas.schema.sports-sites :as sports-sites-schema]
   [malli.core :as m]
   [malli.error :as me]))

;; Save triggers
;;
;; Analysis and elevation are expensive (minutes to tens of minutes), so a
;; save enqueues them only when the inputs they actually consume changed.
;; The comparisons are conservative by construction: any doubt (missing old
;; revision, unrecognized shape) means enqueue - a false positive costs one
;; redundant deduplicated job, a false negative loses an enrichment.

(defn- coords-2d
  "Nested GeoJSON coordinates with each position truncated to [x y].
  Elevation enrichment writes z back into the stored geometries, so
  comparing in 2D keeps enrichment output from masking or faking edits."
  [coords]
  (if (and (sequential? coords) (number? (first coords)))
    (vec (take 2 coords))
    (mapv coords-2d coords)))

(defn- geoms-2d
  "Feature geometries of a site as comparable 2D [type coordinates] pairs.
  Feature properties are ignored: renaming a route segment is not a
  geometry edit."
  [site]
  (some->> site :location :geometries :features
           (mapv (fn [f]
                   [(-> f :geometry :type)
                    (some-> f :geometry :coordinates coords-2d)]))))

(defn- any-leaf-3d? [coords]
  (if (and (sequential? coords) (number? (first coords)))
    (> (count coords) 2)
    (boolean (some any-leaf-3d? coords))))

(defn- site-has-z? [site]
  (boolean
   (some #(some-> % :geometry :coordinates any-leaf-3d?)
         (-> site :location :geometries :features))))

(defn- route?
  "Sites whose geometry type is LineString get elevation enrichment."
  [site]
  (= "LineString" (-> site :type :type-code types/all :geometry-type)))

(defn- elevation-trigger?
  [old new]
  (and (route? new)
       (or (nil? old)
           (not= (geoms-2d old) (geoms-2d new))
           ;; The new revision lost the enriched z coordinates (e.g. a
           ;; client submitted 2D geometries) - re-enrich
           (and (site-has-z? old) (not (site-has-z? new))))))

(defn- analysis-trigger?
  [old new]
  (or (nil? old)
      (not= [(-> old :type :type-code) (:status old) (geoms-2d old)]
            [(-> new :type :type-code) (:status new) (geoms-2d new)])))

(def job-types
  {"analysis"
   {:doc "Recalculate diversity grids around a sports site."
    :payload-schema [:map [:lipas-id #'sports-sites-schema/lipas-id]]
    :lane :slow
    :timeout-min 60
    :priority 80
    :max-attempts 3
    :dedup-key-fn (fn [payload] (str "analysis:" (:lipas-id payload)))
    :debounce-sec 30
    :trigger-fn analysis-trigger?}

   "elevation"
   {:doc "Enrich route geometries with elevation data from MML."
    :payload-schema [:map [:lipas-id #'sports-sites-schema/lipas-id]]
    :lane :slow
    :timeout-min 30
    :priority 70
    :max-attempts 3
    :dedup-key-fn (fn [payload] (str "elevation:" (:lipas-id payload)))
    :debounce-sec 30
    :trigger-fn elevation-trigger?}

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

(defn should-enqueue?
  "Decide whether a save of a sports site requires enqueueing job-type by
  diffing the previous and new revisions with the type's :trigger-fn.
  Types without a trigger always enqueue; a nil/empty old revision always
  enqueues (conservative default)."
  [job-type old-site new-site]
  (if-let [f (:trigger-fn (get-def job-type))]
    (boolean (f (not-empty old-site) new-site))
    true))

(defn validate-payload
  "Validate a payload against the job type's schema.
  Returns {:valid? true} or {:valid? false :errors <humanized>}."
  [job-type payload]
  (let [schema (:payload-schema (get-def job-type))]
    (if (m/validate schema payload)
      {:valid? true}
      {:valid? false
       :errors (me/humanize (m/explain schema payload))})))
