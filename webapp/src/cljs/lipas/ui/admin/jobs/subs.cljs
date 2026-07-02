(ns lipas.ui.admin.jobs.subs
  "Subscriptions for the jobs admin UI. All triage statistics (grouping,
  trends, superseded detection display) are derived client-side from the
  fetched DLQ list — it is bounded by retention."
  (:require [clojure.string :as str]
            [re-frame.core :as rf]))

(def hour-ms (* 60 60 1000))
(def day-ms (* 24 hour-ms))
(def week-ms (* 7 day-ms))

(rf/reg-sub ::selected-sub-tab
  (fn [db _]
    (get-in db [:admin :jobs :selected-sub-tab] 0)))

(rf/reg-sub ::loading?
  (fn [db _]
    (get-in db [:admin :jobs :loading?] false)))

(rf/reg-sub ::error
  (fn [db _]
    (get-in db [:admin :jobs :error])))

(rf/reg-sub ::auto-refresh?
  (fn [db _]
    (get-in db [:admin :jobs :auto-refresh?] true)))

(rf/reg-sub ::health
  (fn [db _]
    (get-in db [:admin :jobs :health])))

(rf/reg-sub ::metrics
  (fn [db _]
    (get-in db [:admin :jobs :metrics])))

(rf/reg-sub ::throughput-window
  (fn [db _]
    (get-in db [:admin :jobs :throughput-window] 24)))

(rf/reg-sub ::worker-down?
  :<- [::health]
  (fn [health _]
    ;; Heuristic: old pending jobs while nothing is processing means the
    ;; worker is down or stalled.
    (boolean (and health
                  (some-> (:oldest_pending_minutes health) (> 10))
                  (zero? (or (:processing_count health) 0))
                  (pos? (or (:pending_count health) 0))))))

;; Throughput chart

(defn- parse-hour
  "Parse a backend hour string (java.sql.Timestamp str, e.g.
  '2026-07-02 10:00:00.0') into epoch ms."
  [s]
  (let [t (.getTime (js/Date. (str/replace s " " "T")))]
    (when-not (js/isNaN t) t)))

(defn- hour-label [ms window-hours]
  (let [d (js/Date. ms)
        hh (.padStart (str (.getHours d)) 2 "0")]
    (if (> window-hours 24)
      (str (.getDate d) "." (inc (.getMonth d)) ". " hh ":00")
      (str hh ":00"))))

(rf/reg-sub ::throughput-chart-data
  :<- [::metrics]
  :<- [::throughput-window]
  (fn [[metrics window] _]
    (when metrics
      (let [by-hour (reduce (fn [acc {:keys [hour status job_count]}]
                              (if-let [t (parse-hour hour)]
                                (update-in acc [t (keyword status)] (fnil + 0) job_count)
                                acc))
                            {}
                            (:hourly-throughput metrics))
            end (* hour-ms (js/Math.floor (/ (js/Date.now) hour-ms)))
            start (- end (* hour-ms (dec window)))]
        (vec (for [t (range start (inc end) hour-ms)]
               (let [counts (get by-hour t)]
                 {:label (hour-label t window)
                  :completed (get counts :completed 0)
                  :retried (get counts :retried 0)
                  :dead-lettered (get counts :dead_lettered 0)})))))))

(rf/reg-sub ::activity-table
  :<- [::metrics]
  (fn [metrics _]
    (->> (:performance-metrics metrics)
         (filter #(= "completed" (:status %)))
         (map (fn [m]
                {:type (:type m)
                 :count (:job_count m)
                 :avg-s (:avg_duration_seconds m)
                 :p95-s (:p95_duration_seconds m)
                 :avg-attempts (:avg_attempts m)}))
         (sort-by :count >)
         vec)))

;; Queue now / recent

(rf/reg-sub ::queue
  (fn [db _]
    (get-in db [:admin :jobs :queue] [])))

(rf/reg-sub ::recent
  (fn [db _]
    (get-in db [:admin :jobs :recent] [])))

(rf/reg-sub ::queue-now
  :<- [::queue]
  (fn [jobs _]
    (->> jobs
         (sort-by (fn [j]
                    [(if (= "processing" (:status j)) 0 1)
                     (or (some-> ^js (:started-at j) .getTime)
                         (some-> ^js (:run-at j) .getTime)
                         0)]))
         vec)))

(rf/reg-sub ::recent-jobs
  :<- [::recent]
  (fn [jobs _]
    (->> jobs
         (map (fn [{:keys [started-at completed-at created-at] :as j}]
                (assoc j
                       :duration-s (when (and started-at completed-at)
                                     (/ (- (.getTime ^js completed-at)
                                           (.getTime ^js started-at))
                                        1000))
                       :queue-s (when (and created-at started-at)
                                  (/ (- (.getTime ^js started-at)
                                        (.getTime ^js created-at))
                                     1000)))))
         (sort-by #(some-> ^js (:completed-at %) .getTime) >)
         vec)))

;; Dead letter queue

(rf/reg-sub ::dead-letter-jobs
  (fn [db _]
    (get-in db [:admin :jobs :dead-letter :jobs] [])))

(rf/reg-sub ::dead-letter-filter
  (fn [db _]
    (get-in db [:admin :jobs :dead-letter :filter] :unacknowledged)))

(rf/reg-sub ::site-filter
  (fn [db _]
    (get-in db [:admin :jobs :dead-letter :site-filter])))

(rf/reg-sub ::dead-letter-loading?
  (fn [db _]
    (get-in db [:admin :jobs :dead-letter :loading?] false)))

(rf/reg-sub ::dead-letter-error
  (fn [db _]
    (get-in db [:admin :jobs :dead-letter :error])))

(rf/reg-sub ::reprocessing?
  (fn [db _]
    (get-in db [:admin :jobs :dead-letter :reprocessing?] false)))

(rf/reg-sub ::acknowledging?
  (fn [db _]
    (get-in db [:admin :jobs :dead-letter :acknowledging?] false)))

(rf/reg-sub ::dead-letter-stats
  :<- [::dead-letter-jobs]
  (fn [jobs _]
    {:total (count jobs)
     :unacknowledged (count (remove :acknowledged jobs))
     :acknowledged (count (filter :acknowledged jobs))}))

(defn dlq-lipas-id [entry]
  (get-in entry [:original-job :payload :lipas-id]))

(rf/reg-sub ::visible-dead-letters
  :<- [::dead-letter-jobs]
  :<- [::dead-letter-filter]
  :<- [::site-filter]
  (fn [[jobs filter-value site] _]
    (cond->> jobs
      (= :unacknowledged filter-value) (remove :acknowledged)
      (= :acknowledged filter-value) (filter :acknowledged)
      site (filter #(= site (dlq-lipas-id %))))))

(rf/reg-sub ::superseded-unack-ids
  :<- [::dead-letter-jobs]
  (fn [jobs _]
    (->> jobs
         (filter #(and (:superseded-by %) (not (:acknowledged %))))
         (mapv :id))))

(defn- died-ms [entry]
  (some-> ^js (:died-at entry) .getTime))

(defn- message-key
  "Collapse an error message to a grouping key: first line, truncated."
  [msg]
  (let [m (or msg "")
        m (first (str/split-lines m))]
    (if (> (count m) 120) (str (subs m 0 120) "…") m)))

(defn- weekly-sparkline
  "Occurrence counts for the last 8 weeks, oldest week first."
  [now times]
  (vec (for [i (range 8)]
         (let [from (- now (* (- 8 i) week-ms))
               to (- now (* (- 7 i) week-ms))]
           (count (filter #(and (>= % from) (< % to)) times))))))

(defn- ->group [now visible-by-class [error-class entries]]
  (let [times (keep died-ms entries)
        unack (remove :acknowledged entries)
        last-7d (count (filter #(> % (- now week-ms)) times))
        site-freqs (frequencies (keep dlq-lipas-id entries))
        msg-freqs (frequencies (map (comp message-key :error-message) entries))]
    {:error-class error-class
     :entries (->> (get visible-by-class error-class [])
                   (sort-by died-ms >)
                   vec)
     :total (count entries)
     :unack-count (count unack)
     :unack-ids (mapv :id unack)
     :superseded-unack-ids (mapv :id (filter :superseded-by unack))
     :by-type (->> entries
                   (map #(get-in % [:original-job :type] "unknown"))
                   frequencies
                   (sort-by val >))
     :first-seen (when (seq times) (apply min times))
     :last-seen (when (seq times) (apply max times))
     :last-7d last-7d
     :sparkline (weekly-sparkline now times)
     :active? (pos? last-7d)
     :stale? (not-any? #(> % (- now (* 30 day-ms))) times)
     :distinct-sites (count site-freqs)
     :top-sites (->> site-freqs (sort-by val >) (take 3) vec)
     :top-messages (->> msg-freqs (sort-by val >) (take 3) vec)}))

(rf/reg-sub ::dead-letter-groups
  :<- [::dead-letter-jobs]
  :<- [::visible-dead-letters]
  (fn [[all visible] _]
    ;; Group stats come from the full DLQ so trends stay truthful; the
    ;; expanded entry list respects the ack/site filters.
    (let [now (js/Date.now)
          visible-by-class (group-by :error-class visible)]
      (->> (group-by :error-class all)
           (map (partial ->group now visible-by-class))
           (sort-by (fn [g] [(if (:active? g) 0 1) (- (:unack-count g))]))
           vec))))

(rf/reg-sub ::selected-job-id
  (fn [db _]
    (get-in db [:admin :jobs :dead-letter :selected-job-id])))

(rf/reg-sub ::selected-job-details
  :<- [::dead-letter-jobs]
  :<- [::selected-job-id]
  (fn [[jobs job-id] _]
    (when job-id
      (first (filter #(= (:id %) job-id) jobs)))))

(rf/reg-sub ::site-failure-count
  :<- [::dead-letter-jobs]
  (fn [jobs [_ lipas-id]]
    (when lipas-id
      (count (filter #(= lipas-id (dlq-lipas-id %)) jobs)))))
