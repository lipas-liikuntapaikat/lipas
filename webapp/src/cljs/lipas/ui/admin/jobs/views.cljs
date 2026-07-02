(ns lipas.ui.admin.jobs.views
  "Jobs admin UI: Overview (are jobs flowing?) and Dead letters
  (what broke, and one-click triage). English-only admin tool."
  (:require ["@mui/material/Alert$default" :as Alert]
            ["@mui/material/Box$default" :as Box]
            ["@mui/material/Button$default" :as Button]
            ["@mui/material/Card$default" :as Card]
            ["@mui/material/CardContent$default" :as CardContent]
            ["@mui/material/CardHeader$default" :as CardHeader]
            ["@mui/material/Chip$default" :as Chip]
            ["@mui/material/CircularProgress$default" :as CircularProgress]
            ["@mui/material/Dialog$default" :as Dialog]
            ["@mui/material/DialogActions$default" :as DialogActions]
            ["@mui/material/DialogContent$default" :as DialogContent]
            ["@mui/material/DialogTitle$default" :as DialogTitle]
            ["@mui/material/FormControlLabel$default" :as FormControlLabel]
            ["@mui/material/Grid$default" :as Grid]
            ["@mui/material/Icon$default" :as Icon]
            ["@mui/material/IconButton$default" :as IconButton]
            ["@mui/material/LinearProgress$default" :as LinearProgress]
            ["@mui/material/Link$default" :as Link]
            ["@mui/material/Paper$default" :as Paper]
            ["@mui/material/Stack$default" :as Stack]
            ["@mui/material/Switch$default" :as Switch]
            ["@mui/material/Tab$default" :as Tab]
            ["@mui/material/Table$default" :as Table]
            ["@mui/material/TableBody$default" :as TableBody]
            ["@mui/material/TableCell$default" :as TableCell]
            ["@mui/material/TableHead$default" :as TableHead]
            ["@mui/material/TableRow$default" :as TableRow]
            ["@mui/material/Tabs$default" :as Tabs]
            ["@mui/material/TextField$default" :as TextField]
            ["@mui/material/ToggleButton$default" :as ToggleButton]
            ["@mui/material/ToggleButtonGroup$default" :as ToggleButtonGroup]
            ["@mui/material/Tooltip$default" :as Tooltip]
            ["@mui/material/Typography$default" :as Typography]
            ["recharts/es6/cartesian/Bar" :refer [Bar]]
            ["recharts/es6/cartesian/CartesianGrid" :refer [CartesianGrid]]
            ["recharts/es6/cartesian/XAxis" :refer [XAxis]]
            ["recharts/es6/cartesian/YAxis" :refer [YAxis]]
            ["recharts/es6/chart/BarChart" :refer [BarChart]]
            ["recharts/es6/component/Legend" :refer [Legend]]
            ["recharts/es6/component/ResponsiveContainer" :refer [ResponsiveContainer]]
            ["recharts/es6/component/Tooltip" :refer [Tooltip] :rename {Tooltip ChartTooltip}]
            [lipas.ui.admin.jobs.events :as events]
            [lipas.ui.admin.jobs.subs :as subs]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [reagent.hooks :as hooks]))

;; Formatting helpers

(defn- ->date [x]
  (cond
    (inst? x) x
    (number? x) (js/Date. x)
    (string? x) (let [d (js/Date. x)]
                  (when-not (js/isNaN (.getTime d)) d))
    :else nil))

(defn- fmt-ts [x]
  (if-let [d (->date x)]
    (.toLocaleString d "fi-FI")
    "-"))

(defn- fmt-ago [x]
  (if-let [d (->date x)]
    (let [mins (js/Math.round (/ (- (js/Date.now) (.getTime d)) 60000))]
      (cond
        (< mins 1) "just now"
        (< mins 60) (str mins " min ago")
        (< mins 1440) (str (js/Math.round (/ mins 60)) " h ago")
        :else (str (js/Math.round (/ mins 1440)) " d ago")))
    "-"))

(defn- fmt-due [x]
  (if-let [d (->date x)]
    (let [mins (js/Math.round (/ (- (.getTime d) (js/Date.now)) 60000))]
      (cond
        (<= mins 0) "due now"
        (< mins 60) (str "in " mins " min")
        :else (str "in " (js/Math.round (/ mins 60)) " h")))
    "-"))

(defn- fmt-duration-s [s]
  (if (number? s)
    (cond
      (< s 1) "<1 s"
      (< s 60) (str (js/Math.round s) " s")
      (< s 3600) (str (js/Math.round (/ s 60)) " min")
      :else (str (.toFixed (/ s 3600) 1) " h"))
    "-"))

(defn- fmt-num [x]
  (if (number? x)
    (if (= x (js/Math.round x)) (str x) (.toFixed x 1))
    "-"))

(defn- truncate [s n]
  (let [s (or s "")]
    (if (> (count s) n) (str (subs s 0 (- n 1)) "…") s)))

(def error-class-labels
  {:timeout "Timeout"
   :mml-api "MML API error"
   :site-not-found "Site not found"
   :search "Search (Elasticsearch)"
   :oom "Out of memory"
   :other "Other"})

(defn- error-class-label [error-class]
  (get error-class-labels error-class (str error-class)))

(r/defc site-link [{:keys [lipas-id]}]
  (if lipas-id
    [:> Link {:href (str "/liikuntapaikat/" lipas-id)
              :target "_blank"
              :rel "noopener"}
     (str lipas-id)]
    "-"))

;;; Overview tab ;;;

(r/defc stat-tile [{:keys [value label caption highlight?]}]
  [:> Grid {:size {:xs 12 :sm 6 :md 3}}
   [:> Paper {:sx #js{:p 2 :bgcolor (if highlight? "#ffebee" "#f5f5f5")}}
    [:> Typography {:variant "h4"} (str value)]
    [:> Typography {:color "textSecondary"} label]
    [:> Typography {:variant "caption" :color "textSecondary"}
     (or caption " ")]]])

(r/defc health-tiles []
  (let [health @(rf/subscribe [::subs/health])]
    (when health
      [:> Grid {:container true :spacing 2 :sx #js{:mb 2}}
       [stat-tile {:value (or (:pending_count health) 0)
                   :label "Pending"
                   :caption (when-let [m (:oldest_pending_minutes health)]
                              (str "oldest " m " min"))
                   :highlight? (> (or (:pending_count health) 0) 100)}]
       [stat-tile {:value (or (:processing_count health) 0)
                   :label "Processing"
                   :caption (when-let [m (:longest_processing_minutes health)]
                              (str "longest " m " min"))}]
       [stat-tile {:value (or (:retrying_count health) 0)
                   :label "Retrying"
                   :highlight? (pos? (or (:retrying_count health) 0))}]
       [stat-tile {:value (or (:dead_count health) 0)
                   :label "Unacknowledged dead letters"
                   :highlight? (pos? (or (:dead_count health) 0))}]])))

;; Outcome colors: blue = completed first try, yellow = completed after
;; retries, red = dead-lettered. Validated CVD-safe (dataviz skill).
(def throughput-colors
  {:completed "#2a78d6"
   :retried "#eda100"
   :dead-lettered "#d03b3b"})

(def chart-font
  {:fontFamily "lato" :fontSize 12})

(r/defc throughput-chart [{:keys [data]}]
  (if (every? #(and (zero? (:completed %))
                    (zero? (:retried %))
                    (zero? (:dead-lettered %)))
              data)
    [:> Typography {:color "textSecondary" :sx #js{:py 4 :textAlign "center"}}
     "No job activity in the selected window"]
    [:> ResponsiveContainer {:width "100%" :height 260}
     [:> BarChart {:data data :margin #js{:top 8 :right 8 :left 0 :bottom 0}}
      [:> CartesianGrid {:vertical false :stroke "#e1e0d9"}]
      [:> XAxis {:dataKey :label :tick chart-font :interval "preserveStartEnd"}]
      [:> YAxis {:tick chart-font :allowDecimals false}]
      [:> ChartTooltip {:itemStyle chart-font :labelStyle chart-font}]
      [:> Legend {:wrapperStyle chart-font}]
      [:> Bar {:dataKey :completed :stackId "a" :name "Completed"
               :fill (:completed throughput-colors)
               :stroke "#fff" :strokeWidth 1 :maxBarSize 24}]
      [:> Bar {:dataKey :retried :stackId "a" :name "Completed after retry"
               :fill (:retried throughput-colors)
               :stroke "#fff" :strokeWidth 1 :maxBarSize 24}]
      [:> Bar {:dataKey :dead-lettered :stackId "a" :name "Dead-lettered"
               :fill (:dead-lettered throughput-colors)
               :stroke "#fff" :strokeWidth 1 :maxBarSize 24}]]]))

(r/defc activity-card []
  (let [window @(rf/subscribe [::subs/throughput-window])
        data @(rf/subscribe [::subs/throughput-chart-data])
        table @(rf/subscribe [::subs/activity-table])]
    [:> Card {:sx #js{:mb 2}}
     [:> CardHeader
      {:title "Activity"
       :action (r/as-element
                [:> ToggleButtonGroup
                 {:value window
                  :exclusive true
                  :size "small"
                  :onChange (fn [_ v] (when v (rf/dispatch [::events/set-throughput-window v])))}
                 [:> ToggleButton {:value 24} "24 h"]
                 [:> ToggleButton {:value 168} "7 d"]])}]
     [:> CardContent
      (when data
        [throughput-chart {:data data}])
      (when (seq table)
        [:> Table {:size "small" :sx #js{:mt 2}}
         [:> TableHead
          [:> TableRow
           [:> TableCell "Job type"]
           [:> TableCell {:align "right"} "Completed"]
           [:> TableCell {:align "right"} "Avg duration"]
           [:> TableCell {:align "right"} "P95 duration"]
           [:> TableCell {:align "right"} "Avg attempts"]]]
         [:> TableBody
          (for [row table]
            [:> TableRow {:key (:type row)}
             [:> TableCell (:type row)]
             [:> TableCell {:align "right"} (str (:count row))]
             [:> TableCell {:align "right"} (fmt-duration-s (:avg-s row))]
             [:> TableCell {:align "right"} (fmt-duration-s (:p95-s row))]
             [:> TableCell {:align "right"} (fmt-num (:avg-attempts row))]])]])]]))

(r/defc queue-now-card []
  (let [jobs @(rf/subscribe [::subs/queue-now])]
    [:> Card {:sx #js{:mb 2}}
     [:> CardHeader {:title (str "Queue now (" (count jobs) ")")}]
     [:> CardContent
      (if (empty? jobs)
        [:> Typography {:color "textSecondary"} "Queue is empty"]
        [:> Table {:size "small"}
         [:> TableHead
          [:> TableRow
           [:> TableCell "ID"]
           [:> TableCell "Type"]
           [:> TableCell "Site"]
           [:> TableCell "Status"]
           [:> TableCell "Attempt"]
           [:> TableCell "Age"]
           [:> TableCell "Started / due"]
           [:> TableCell "Last error"]]]
         [:> TableBody
          (for [job jobs]
            [:> TableRow {:key (:id job)}
             [:> TableCell (str (:id job))]
             [:> TableCell (:type job)]
             [:> TableCell [site-link {:lipas-id (get-in job [:payload :lipas-id])}]]
             [:> TableCell
              [:> Chip {:label (:status job)
                        :size "small"
                        :color (if (= "processing" (:status job)) "info" "default")}]]
             [:> TableCell (str (:attempts job) "/" (:max-attempts job))]
             [:> TableCell
              [:> Tooltip {:title (fmt-ts (:created-at job))}
               [:span (fmt-ago (:created-at job))]]]
             [:> TableCell (if (= "processing" (:status job))
                             (fmt-ago (:started-at job))
                             (fmt-due (:run-at job)))]
             [:> TableCell
              (when-let [err (:last-error job)]
                [:> Tooltip {:title err}
                 [:span (truncate err 40)]])]])]])]]))

(r/defc recent-card []
  (let [jobs @(rf/subscribe [::subs/recent-jobs])]
    [:> Card {:sx #js{:mb 2}}
     [:> CardHeader {:title "Recently completed"}]
     [:> CardContent
      (if (empty? jobs)
        [:> Typography {:color "textSecondary"} "Nothing completed yet"]
        [:> Table {:size "small"}
         [:> TableHead
          [:> TableRow
           [:> TableCell "ID"]
           [:> TableCell "Type"]
           [:> TableCell "Site"]
           [:> TableCell "Attempts"]
           [:> TableCell "Queued"]
           [:> TableCell "Duration"]
           [:> TableCell "Completed"]]]
         [:> TableBody
          (for [job jobs]
            [:> TableRow {:key (:id job)}
             [:> TableCell (str (:id job))]
             [:> TableCell (:type job)]
             [:> TableCell [site-link {:lipas-id (get-in job [:payload :lipas-id])}]]
             [:> TableCell (str (:attempts job) "/" (:max-attempts job))]
             [:> TableCell (fmt-duration-s (:queue-s job))]
             [:> TableCell (fmt-duration-s (:duration-s job))]
             [:> TableCell
              [:> Tooltip {:title (fmt-ts (:completed-at job))}
               [:span (fmt-ago (:completed-at job))]]]])]])]]))

(r/defc overview-tab []
  (let [error @(rf/subscribe [::subs/error])
        loading? @(rf/subscribe [::subs/loading?])
        worker-down? @(rf/subscribe [::subs/worker-down?])]
    [:<>
     (when error
       [:> Alert {:severity "error" :sx #js{:mb 2}} error])
     (when loading?
       [:> LinearProgress {:sx #js{:mb 2}}])
     (when worker-down?
       [:> Alert {:severity "warning" :sx #js{:mb 2}}
        "Worker appears to be down or stalled: pending jobs are waiting but nothing is processing."])
     [health-tiles]
     [activity-card]
     [queue-now-card]
     [recent-card]]))

;;; Dead letters tab ;;;

(defn- dlq-lipas-id [entry]
  (get-in entry [:original-job :payload :lipas-id]))

(defn- copy-edn! [job]
  (.writeText (.-clipboard js/navigator) (pr-str job)))

(r/defc sparkline
  "Tiny weekly-occurrence bars (8 weeks, oldest first)."
  [{:keys [values]}]
  (let [mx (max 1 (apply max values))]
    [:> Stack {:direction "row" :spacing "2px" :alignItems "flex-end"
               :sx #js{:height 20}}
     (for [[i v] (map-indexed vector values)]
       [:> Tooltip {:key i :title (str v " failure(s) " (- 8 i) " week(s) ago")}
        [:> Box {:sx #js{:width 7
                         :height (max 3 (js/Math.round (* 18 (/ v mx))))
                         :bgcolor (if (pos? v) "#2a78d6" "#e1e0d9")
                         :borderRadius "1px"}}]])]))

(r/defc superseded-chip []
  [:> Tooltip {:title "A newer job of the same type for the same site has completed — this failure is obsolete"}
   [:> Chip {:label "superseded" :size "small" :color "success" :variant "outlined"}]])

(r/defc group-entries-table [{:keys [entries]}]
  (if (empty? entries)
    [:> Typography {:color "textSecondary" :sx #js{:p 1}}
     "No entries match the current filter"]
    [:> Table {:size "small"}
     [:> TableHead
      [:> TableRow
       [:> TableCell "Died"]
       [:> TableCell "ID"]
       [:> TableCell "Type"]
       [:> TableCell "Site"]
       [:> TableCell "Attempts"]
       [:> TableCell "Error"]
       [:> TableCell "Status"]
       [:> TableCell {:align "right"} "Actions"]]]
     [:> TableBody
      (for [entry entries]
        [:> TableRow {:key (:id entry)}
         [:> TableCell
          [:> Tooltip {:title (fmt-ts (:died-at entry))}
           [:span (fmt-ago (:died-at entry))]]]
         [:> TableCell (str (:id entry))]
         [:> TableCell (get-in entry [:original-job :type] "?")]
         [:> TableCell [site-link {:lipas-id (dlq-lipas-id entry)}]]
         [:> TableCell (str (get-in entry [:original-job :attempts] "?")
                            "/"
                            (get-in entry [:original-job :max-attempts] "?"))]
         [:> TableCell
          [:> Tooltip {:title (or (:error-message entry) "")}
           [:span (truncate (:error-message entry) 60)]]]
         [:> TableCell
          [:> Stack {:direction "row" :spacing 0.5}
           (if (:acknowledged entry)
             [:> Chip {:label "acked" :size "small"}]
             [:> Chip {:label "unack" :size "small" :color "warning"}])
           (when (:superseded-by entry)
             [superseded-chip])]]
         [:> TableCell {:align "right"}
          [:> Stack {:direction "row" :spacing 1 :justifyContent "flex-end"}
           [:> Button {:size "small"
                       :on-click #(rf/dispatch [::events/open-job-details-dialog (:id entry)])}
            "View"]
           [:> Button {:size "small"
                       :on-click #(when (js/confirm "Requeue this job?")
                                    (rf/dispatch [::events/reprocess-jobs [(:id entry)]]))}
            "Reprocess"]
           (when-not (:acknowledged entry)
             [:> Button {:size "small"
                         :on-click #(rf/dispatch [::events/acknowledge-jobs [(:id entry)]])}
              "Ack"])]]])]]))

(r/defc group-card [{:keys [group]}]
  (let [{:keys [error-class entries total unack-count unack-ids
                superseded-unack-ids by-type first-seen last-seen last-7d
                active? stale? distinct-sites top-sites top-messages]} group
        [expanded? set-expanded] (hooks/use-state false)
        busy? (or @(rf/subscribe [::subs/reprocessing?])
                  @(rf/subscribe [::subs/acknowledging?]))]
    [:> Paper {:variant "outlined" :sx #js{:mb 1}}
     ;; Header row: identity + priority signals + one-click actions
     [:> Stack {:direction "row" :spacing 2 :alignItems "center"
                :sx #js{:p 1 :cursor "pointer" :flexWrap "wrap"}
                :onClick (fn [_] (set-expanded (not expanded?)))}
      [:> Icon (if expanded? "expand_more" "chevron_right")]
      [:> Typography {:variant "subtitle1" :sx #js{:fontWeight "bold"}}
       (error-class-label error-class)]
      (cond
        active? [:> Chip {:label "ACTIVE" :size "small" :color "error"}]
        stale? [:> Chip {:label "STALE" :size "small"}]
        :else nil)
      [:> Typography {:variant "body2"}
       (str unack-count " unack / " total " total")]
      [:> Typography {:variant "body2" :color "textSecondary"}
       (->> by-type
            (map (fn [[t n]] (str t " ×" n)))
            (interpose ", ")
            (apply str))]
      [:> Typography {:variant "body2" :color "textSecondary"}
       (str "last " (fmt-ago last-seen) " · " last-7d " this week")]
      [sparkline {:values (:sparkline group)}]
      [:> Box {:sx #js{:flexGrow 1}}]
      [:> Stack {:direction "row" :spacing 1
                 :onClick (fn [^js e] (.stopPropagation e))}
       (when (seq superseded-unack-ids)
         [:> Button {:size "small"
                     :disabled busy?
                     :on-click #(rf/dispatch [::events/acknowledge-jobs superseded-unack-ids])}
          (str "Ack superseded (" (count superseded-unack-ids) ")")])
       (when (seq unack-ids)
         [:> Button {:size "small"
                     :disabled busy?
                     :on-click #(when (js/confirm (str "Requeue " (count unack-ids)
                                                       " unacknowledged job(s)?"))
                                  (rf/dispatch [::events/reprocess-jobs unack-ids]))}
          "Reprocess all"])
       (when (seq unack-ids)
         [:> Button {:size "small"
                     :disabled busy?
                     :on-click #(when (js/confirm (str "Acknowledge " (count unack-ids)
                                                       " job(s) without reprocessing?"))
                                  (rf/dispatch [::events/acknowledge-jobs unack-ids]))}
          (if stale? "Ack stale" "Acknowledge all")])]]
     (when expanded?
       [:> Box {:sx #js{:px 2 :pb 1}}
        [:> Typography {:variant "body2" :color "textSecondary"}
         (str "first " (fmt-ts first-seen) " · last " (fmt-ts last-seen)
              " · " distinct-sites " distinct site(s)")
         (when (seq top-sites) " · top: ")
         (for [[lipas-id n] top-sites]
           ^{:key (str lipas-id)}
           [:<> [site-link {:lipas-id lipas-id}] (str " ×" n " ")])]
        (when (seq top-messages)
          [:> Typography {:variant "body2" :color "textSecondary"}
           (str "top messages: "
                (->> top-messages
                     (map (fn [[msg n]] (str "\"" (truncate msg 80) "\" (" n ")")))
                     (interpose " · ")
                     (apply str)))])
        [group-entries-table {:entries entries}]])]))

(r/defc job-timeline [{:keys [job]}]
  (let [created (->date (get-in job [:original-job :created-at]))
        started (->date (get-in job [:original-job :started-at]))
        died (->date (:died-at job))
        queue-s (when (and created started)
                  (/ (- (.getTime started) (.getTime created)) 1000))
        run-s (when (and started died)
                (/ (- (.getTime died) (.getTime started)) 1000))]
    [:> Stack {:direction "row" :spacing 2 :sx #js{:mb 2} :flexWrap "wrap"}
     [:> Chip {:size "small" :label (str "created " (fmt-ts created))}]
     [:> Icon {:fontSize "small"} "arrow_forward"]
     [:> Chip {:size "small"
               :label (str "started " (fmt-ts started)
                           (when queue-s (str " (queued " (fmt-duration-s queue-s) ")")))}]
     [:> Icon {:fontSize "small"} "arrow_forward"]
     [:> Chip {:size "small" :color "error"
               :label (str "died " (fmt-ts died)
                           (when run-s (str " (last attempt ran " (fmt-duration-s run-s) ")")))}]]))

(r/defc job-details-dialog []
  (let [job @(rf/subscribe [::subs/selected-job-details])
        reprocessing? @(rf/subscribe [::subs/reprocessing?])
        lipas-id (when job (dlq-lipas-id job))
        site-failures @(rf/subscribe [::subs/site-failure-count lipas-id])
        [max-attempts set-max-attempts] (hooks/use-state "")]
    ;; Clear the max-attempts override when another entry is opened
    (hooks/use-effect
     (fn [] (set-max-attempts ""))
     [(:id job)])
    [:> Dialog {:open (some? job)
                :onClose #(rf/dispatch [::events/close-job-details-dialog])
                :maxWidth "md"
                :fullWidth true}
     [:> DialogTitle "Dead letter details"
      [:> IconButton {:on-click #(rf/dispatch [::events/close-job-details-dialog])
                      :sx #js{:position "absolute" :right 8 :top 8}}
       [:> Icon "close"]]]
     (when job
       [:> DialogContent
        [job-timeline {:job job}]

        (when-let [{:keys [job-id completed-at]} (:superseded-by job)]
          [:> Alert {:severity "info"
                     :sx #js{:mb 2}
                     :action (when-not (:acknowledged job)
                               (r/as-element
                                [:> Button {:size "small"
                                            :color "inherit"
                                            :on-click #(rf/dispatch [::events/acknowledge-jobs [(:id job)]])}
                                 "Acknowledge"]))}
           (str "A newer " (get-in job [:original-job :type]) " job (#" job-id ")"
                (when lipas-id (str " for site " lipas-id))
                " completed at " (fmt-ts completed-at)
                " — this failure is obsolete.")])

        [:> Grid {:container true :spacing 2 :sx #js{:mb 2}}
         [:> Grid {:size 3}
          [:> Typography {:color "textSecondary" :variant "caption"} "Type"]
          [:> Typography (get-in job [:original-job :type] "?")]]
         [:> Grid {:size 3}
          [:> Typography {:color "textSecondary" :variant "caption"} "Site"]
          [:> Typography [site-link {:lipas-id lipas-id}]]]
         [:> Grid {:size 3}
          [:> Typography {:color "textSecondary" :variant "caption"} "Priority"]
          [:> Typography (str (get-in job [:original-job :priority] "-"))]]
         [:> Grid {:size 3}
          [:> Typography {:color "textSecondary" :variant "caption"} "Attempts"]
          [:> Typography (str (get-in job [:original-job :attempts] "?")
                              "/"
                              (get-in job [:original-job :max-attempts] "?"))]]
         [:> Grid {:size 3}
          [:> Typography {:color "textSecondary" :variant "caption"} "Created by"]
          [:> Typography (or (get-in job [:original-job :created-by]) "-")]]
         [:> Grid {:size 3}
          [:> Typography {:color "textSecondary" :variant "caption"} "Dedup key"]
          [:> Typography (or (get-in job [:original-job :dedup-key]) "-")]]
         [:> Grid {:size 3}
          [:> Typography {:color "textSecondary" :variant "caption"} "Error class"]
          [:> Typography (error-class-label (:error-class job))]]
         [:> Grid {:size 3}
          [:> Typography {:color "textSecondary" :variant "caption" :display "block"} "Status"]
          [:> Chip {:label (if (:acknowledged job) "Acknowledged" "Unacknowledged")
                    :color (if (:acknowledged job) "default" "warning")
                    :size "small"}]]]

        [:> Typography {:variant "h6" :gutterBottom true} "Error"]
        [:> Paper {:sx #js{:p 2 :mb 2 :bgcolor "#f5f5f5"}}
         [:> Typography {:variant "body2" :component "pre"
                         :sx #js{:whiteSpace "pre-wrap" :fontFamily "monospace"}}
          (:error-message job)]]

        [:> Typography {:variant "h6" :gutterBottom true} "Payload"]
        [:> Paper {:sx #js{:p 2 :bgcolor "#f5f5f5" :overflow "auto"}}
         [:> Typography {:variant "body2" :component "pre"
                         :sx #js{:fontFamily "monospace"}}
          (js/JSON.stringify (clj->js (get-in job [:original-job :payload])) nil 2)]]])
     [:> DialogActions
      (when job
        [:<>
         [:> Button {:on-click (fn []
                                 (copy-edn! job)
                                 (rf/dispatch [:lipas.ui.events/set-active-notification
                                               {:message "Job EDN copied to clipboard"
                                                :success? true}]))}
          "Copy EDN"]
         (when (and lipas-id (> (or site-failures 0) 1))
           [:> Button {:on-click (fn []
                                   (rf/dispatch [::events/set-site-filter lipas-id])
                                   (rf/dispatch [::events/close-job-details-dialog]))}
            (str "Other failures for this site (" (dec site-failures) ")")])
         (when-not (:acknowledged job)
           [:> Button {:variant "outlined"
                       :on-click #(rf/dispatch [::events/acknowledge-jobs [(:id job)]])}
            "Acknowledge"])
         [:> TextField {:label "Max attempts"
                        :type "number"
                        :size "small"
                        :sx #js{:width 110}
                        :value max-attempts
                        :onChange (fn [^js e] (set-max-attempts (.. e -target -value)))}]
         [:> Button {:variant "contained"
                     :color "primary"
                     :disabled reprocessing?
                     :start-icon (when reprocessing?
                                   (r/as-element [:> CircularProgress {:size 16}]))
                     :on-click (fn []
                                 (when (js/confirm "Requeue this job?")
                                   (rf/dispatch [::events/reprocess-jobs
                                                 [(:id job)]
                                                 (let [n (js/parseInt max-attempts 10)]
                                                   (when (pos? n) n))])))}
          (if reprocessing? "Reprocessing…" "Reprocess")]])]]))

(r/defc dead-letters-tab []
  (let [groups @(rf/subscribe [::subs/dead-letter-groups])
        stats @(rf/subscribe [::subs/dead-letter-stats])
        filter-value @(rf/subscribe [::subs/dead-letter-filter])
        site-filter @(rf/subscribe [::subs/site-filter])
        superseded-ids @(rf/subscribe [::subs/superseded-unack-ids])
        loading? @(rf/subscribe [::subs/dead-letter-loading?])
        error @(rf/subscribe [::subs/dead-letter-error])
        acknowledging? @(rf/subscribe [::subs/acknowledging?])]
    [:<>
     [job-details-dialog]

     (when error
       [:> Alert {:severity "error" :sx #js{:mb 2}} error])

     [:> Stack {:direction "row" :spacing 2 :alignItems "center"
                :sx #js{:mb 2 :flexWrap "wrap"}}
      [:> Chip {:label (str "Unacknowledged: " (:unacknowledged stats))
                :color (if (pos? (:unacknowledged stats)) "warning" "default")}]
      [:> ToggleButtonGroup
       {:value filter-value
        :exclusive true
        :size "small"
        :onChange (fn [_ v]
                    (when v (rf/dispatch [::events/set-dead-letter-filter (keyword v)])))}
       [:> ToggleButton {:value :unacknowledged} "Unacknowledged"]
       [:> ToggleButton {:value :acknowledged} "Acknowledged"]
       [:> ToggleButton {:value :all} "All"]]
      (when (seq superseded-ids)
        [:> Button {:variant "outlined"
                    :size "small"
                    :disabled acknowledging?
                    :start-icon (when acknowledging?
                                  (r/as-element [:> CircularProgress {:size 16}]))
                    :on-click #(when (js/confirm
                                      (str "Acknowledge " (count superseded-ids)
                                           " superseded job(s)? A newer job has already "
                                           "completed for each of these sites."))
                                 (rf/dispatch [::events/acknowledge-jobs superseded-ids]))}
         (str "Acknowledge all superseded (" (count superseded-ids) ")")])
      (when site-filter
        [:> Chip {:label (str "Site filter: " site-filter)
                  :color "info"
                  :onDelete #(rf/dispatch [::events/set-site-filter nil])}])]

     (when loading?
       [:> LinearProgress {:sx #js{:mb 2}}])

     (if (empty? groups)
       [:> Typography {:color "textSecondary"} "Dead letter queue is empty 🎉"]
       (for [group groups]
         ^{:key (str (:error-class group))}
         [group-card {:group group}]))]))

;;; Main view ;;;

(r/defc jobs-view []
  (let [selected-tab @(rf/subscribe [::subs/selected-sub-tab])
        auto-refresh? @(rf/subscribe [::subs/auto-refresh?])
        stats @(rf/subscribe [::subs/dead-letter-stats])]
    ;; Poll while mounted; ::poll ticks re-check the :polling? flag.
    (hooks/use-effect
     (fn []
       (rf/dispatch [::events/start-polling])
       (fn []
         (rf/dispatch [::events/stop-polling])))
     [])
    [:> Card {:square true}
     [:> CardContent
      [:> Stack {:direction "row" :spacing 2 :alignItems "center" :sx #js{:mb 1}}
       [:> Typography {:variant "h5" :sx #js{:flexGrow 1}} "Jobs"]
       [:> FormControlLabel
        {:control (r/as-element
                   [:> Switch {:checked (boolean auto-refresh?)
                               :size "small"
                               :onChange #(rf/dispatch [::events/toggle-auto-refresh])}])
         :label "Auto-refresh (30 s)"}]
       [:> Button {:variant "contained"
                   :color "primary"
                   :size "small"
                   :on-click #(rf/dispatch [::events/refresh-all])}
        [:> Icon {:sx #js{:mr 1}} "refresh"]
        "Refresh"]]

      [:> Tabs {:value selected-tab
                :on-change #(rf/dispatch [::events/select-sub-tab %2])
                :sx #js{:borderBottom 1 :borderColor "divider" :mb 2}}
       [:> Tab {:label "Overview"}]
       [:> Tab {:label (str "Dead letters"
                            (when (pos? (:unacknowledged stats))
                              (str " (" (:unacknowledged stats) ")")))}]]

      (case selected-tab
        0 [overview-tab]
        1 [dead-letters-tab]
        [overview-tab])]]))
