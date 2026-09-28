#!/usr/bin/env bb
;; scripts/api_scroll_check.bb — Walk every page of the public LIPAS API and
;; fail on any response that is not a success.
;;
;; Usage:
;;   bb scripts/api_scroll_check.bb                          # full scroll of V1 + V2 against prod
;;   bb scripts/api_scroll_check.bb --base-url https://…     # point it elsewhere
;;   bb scripts/api_scroll_check.bb --only v2                # one API
;;   bb scripts/api_scroll_check.bb --collections v2/sports-sites
;;   bb scripts/api_scroll_check.bb --pages 1-3,275,580-582  # bounded smoke run
;;   bb scripts/api_scroll_check.bb --report-md report.md --github-issue --dry-run
;;
;; Why this exists: in December 2025 a short-lived schema change made an
;; `:admin`/`:owner` value legal on the way in. Eleven sports sites kept it, and
;; response coercion rejected them on the way out — whichever page happened to
;; hold them answered 500 (page 275 at `page-size=100&statuses=active&
;; statuses=out-of-service-temporarily`, page 326 with every status), and so did
;; `GET /v2/sports-sites/618830`. Nobody noticed for nine months, because
;; nothing walked the whole API. This does, nightly.
;;
;; Everything the run needs is discovered from the API itself — page counts come
;; from `total-pages` / `X-total-count`, and the city-code, type-code and status
;; enumerations come from the published OpenAPI documents. Nothing here is
;; hardcoded against a snapshot of the data, so the check keeps its coverage as
;; LIPAS grows.
;;
;; One coverage caveat is worth knowing about. `/v1/sports-places` is served from
;; the legacy Elasticsearch index, which carries no `max_result_window` override
;; and so keeps Elasticsearch's default of 10000. An unpartitioned scroll of its
;; ~49k sites dies with a 500 at page 101. The scroll therefore partitions V1 by
;; city code (309 of them, largest 3166 sites), which both stays inside the
;; window and covers every site exactly once.
;;
;; V2 has the same shape of limit with more headroom: the sports-site index sets
;; `max_result_window` to 60000 and the collection holds ~58.2k, so the last page
;; of a complete scroll asks for offset 58100. When LIPAS passes ~59.9k sites the
;; tail of this scroll will start failing — as a real 500 that integrators paging
;; to the end hit too, not as a fault in this check.

(require '[babashka.cli :as cli]
         '[babashka.http-client :as http]
         '[babashka.process :as process]
         '[cheshire.core :as json]
         '[clojure.string :as str])

;; === Configuration ===

(def default-base-url "https://api.lipas.fi")

(def ok-statuses
  "V1 answers every non-final page with 206 plus Link and X-total-count headers.
  That is the documented contract, not a failure."
  #{200 206})

(def issue-label "api-health")

(def issue-title
  "Stable across runs on purpose: it is half of the deduplication key that
  keeps a nine-month-old bug from filing 270 issues."
  "Public API scroll check is failing")

;; GitHub rejects an issue body over 65536 characters. Stop well short and let
;; the workflow run hold the full detail.
(def max-issue-body-chars 60000)
(def max-failures-detailed 20)
(def max-records-per-failure 25)
(def max-message-chars 200)

(def cli-spec
  {:base-url {:desc "API root to check" :default default-base-url}
   :only {:desc "Restrict to one API: v1 or v2" :coerce :keyword}
   :collections {:desc "Comma-separated collection ids, e.g. v2/sports-sites" :coerce []}
   :extra-urls {:desc "Extra URLs or paths to spot-check, comma-separated" :coerce []}
   :pages {:desc "Bounded page spec applied to every scroll, e.g. 1-3,275,580-582"
           :coerce :string}
   :page-size {:desc "Items per page (API maximum is 100)" :default 100 :coerce :long}
   :samples {:desc "Single-item fetches per collection" :default 200 :coerce :long}
   :seed {:desc "Sampling offset; defaults to the day of the year" :coerce :long}
   :concurrency {:desc "Parallel in-flight requests" :default 6 :coerce :long}
   :retries {:desc "Extra attempts after a failed request" :default 2 :coerce :long}
   :timeout-ms {:desc "Per-request timeout" :default 60000 :coerce :long}
   :report-json {:desc "Write the machine-readable report here"}
   :report-md {:desc "Write the Markdown issue body here"}
   :github-issue {:desc "File/update/close a GitHub issue via gh" :coerce :boolean}
   :dry-run {:desc "Print the gh commands instead of running them" :coerce :boolean}
   :help {:coerce :boolean}})

;; === HTTP ===

(defn- causes
  "The exception and everything it wraps. The JDK HTTP client buries the useful
  exception — UnknownHostException, say — under a generic ConnectException, so
  both the classification and the message have to look down the chain."
  [^Throwable e]
  (take-while some? (iterate #(.getCause ^Throwable %) e)))

(defn- error-kind
  "Network failures are not the same thing as a broken endpoint, and the report
  says which is which so a flaky runner never reads as a data bug. Matched on
  class names because babashka does not expose every JDK exception class as a
  resolvable symbol."
  [e]
  (let [names (map #(.getName (class %)) (causes e))
        has? (fn [n] (some #(str/includes? % n) names))]
    (cond
      (has? "HttpConnectTimeoutException") :connect-timeout
      (has? "HttpTimeoutException") :timeout
      (has? "UnknownHostException") :dns-error
      (has? "SSLException") :tls-error
      (has? "ConnectException") :connect-error
      (has? "IOException") :network-error
      :else :unknown-error)))

(defn- error-message
  [e]
  (or (some->> (causes e) (keep ex-message) (str/join ": ") not-empty)
      (.getName (class e))))

(defn- attempt
  [url {:keys [timeout-ms]}]
  (let [started (System/currentTimeMillis)]
    (try
      (let [{:keys [status body headers]}
            (http/get url {:throw false
                           :timeout timeout-ms
                           :headers {"accept" "application/json"
                                     "user-agent" "lipas-api-scroll-check"}})]
        {:status status
         :body body
         :headers headers
         :ms (- (System/currentTimeMillis) started)})
      (catch Exception e
        {:error (error-kind e)
         :error-message (error-message e)
         :ms (- (System/currentTimeMillis) started)}))))

(defn- ok? [{:keys [status error]}]
  (and (nil? error) (contains? ok-statuses status)))

(defn- failure-kind [{:keys [status error]}]
  (cond
    error error
    (<= 500 status 599) :server-error
    :else :unexpected-status))

(defn fetch
  "GETs `url`, retrying a failed attempt `:retries` times with a short backoff.
  Returns the last response annotated with how many attempts it took."
  [url {:keys [retries] :as opts}]
  (loop [n 1
         resp (attempt url opts)]
    (if (or (ok? resp) (> n retries))
      (assoc resp :attempts n :url url)
      (do (Thread/sleep (* 1000 n))
          (recur (inc n) (attempt url opts))))))

(defn- parse-json [{:keys [body]}]
  (try
    (json/parse-string body)
    (catch Exception _ nil)))

(defn- url-encode [v]
  (java.net.URLEncoder/encode (str v) "UTF-8"))

(defn- url
  [base path params]
  (let [qs (->> params
                (mapcat (fn [[k v]] (if (coll? v) (map #(vector k %) v) [[k v]])))
                (map (fn [[k v]] (str (name k) "=" (url-encode v))))
                (str/join "&"))]
    (str base path (when (seq qs) (str "?" qs)))))

;; === Bounded parallelism ===

(defn- run-parallel!
  "Runs `f` over `xs` with at most `concurrency` requests in flight, printing a
  progress line so a stalled run is visible in the job log."
  [label concurrency f xs]
  (let [total (count xs)
        done (atom 0)
        pool (java.util.concurrent.Executors/newFixedThreadPool concurrency)]
    (println (format "  %s: %d requests" label total))
    (try
      (->> xs
           (map (fn [x]
                  (fn []
                    (let [r (f x)
                          n (swap! done inc)]
                      (when (or (zero? (mod n 100)) (= n total))
                        (println (format "    %s %d/%d" label n total)))
                      r))))
           (.invokeAll pool)
           (mapv #(.get ^java.util.concurrent.Future %)))
      (finally (.shutdown pool)))))

;; === Coercion error extraction ===
;;
;; A reitit response-coercion failure carries both `humanized` (what is wrong)
;; and `value` (the payload that was rejected) with matching shapes. Pairing the
;; two is what turns "page 275 is 500" into "these eleven lipas-ids", which is
;; the single most useful thing this job produces.

(def ^:private id-keys
  ["lipas-id" "loi-id" "sportsPlaceId" "id"])

(defn- leaf-errors
  "Walks a humanized error tree into [path messages] pairs. A leaf is the vector
  of message strings malli puts at the offending field."
  [node path]
  (cond
    (and (vector? node) (every? string? node)) [[path node]]
    (string? node) [[path [node]]]
    (map? node) (mapcat (fn [[k v]] (leaf-errors v (conj path k))) node)
    (sequential? node) (mapcat (fn [[i v]] (leaf-errors v (conj path i)))
                               (map-indexed vector node))
    :else []))

(defn- truncate [s n]
  (if (<= (count s) n) s (str (subs s 0 n) "…")))

(defn- describe-record
  "Identifying fields plus the actual rejected values, read out of `value` at the
  paths `humanized` flagged."
  [humanized value index]
  (let [leaves (leaf-errors humanized [])]
    {:index index
     :id (some #(get value %) id-keys)
     :id-key (some #(when (get value %) %) id-keys)
     :name (let [n (get value "name")] (if (map? n) (get n "fi") n))
     :status (get value "status")
     :fields (for [[path messages] leaves]
               {:path (str/join "." (map str path))
                :value (let [v (get-in value path)]
                         (truncate (if (string? v) v (pr-str v)) 80))
                :message (truncate (first messages) max-message-chars)})}))

(defn- coercion-detail
  "Nil unless the body is a reitit coercion error. Handles both shapes the API
  produces: a list response, where `humanized.items` and `value.items` line up
  by index, and a single-item response, where both are the record itself."
  [body]
  (when-let [parsed (try (json/parse-string body) (catch Exception _ nil))]
    (when (and (map? parsed) (str/includes? (str (get parsed "type")) "coercion"))
      (let [humanized (get parsed "humanized")
            value (get parsed "value")
            h-items (get humanized "items")
            v-items (get value "items")]
        {:type (get parsed "type")
         :records
         (if (sequential? h-items)
           (keep-indexed (fn [i h]
                           (when (seq h)
                             (describe-record h (nth v-items i nil) i)))
                         h-items)
           [(describe-record humanized value nil)])}))))

;; === Discovery ===

(defn- openapi-enum
  "Pulls an enumeration out of a published OpenAPI document, following one $ref
  level. Keeps the run self-describing: city codes, type codes and statuses all
  come from the API rather than a copy in this file."
  [doc ref-name]
  (let [schema (get-in doc ["components" "schemas" ref-name])
        items (get schema "items")
        items (if-let [r (get items "$ref")]
                (get-in doc ["components" "schemas" (last (str/split r #"/"))])
                items)]
    (get items "enum")))

(defn- fetch-openapi!
  "The OpenAPI documents are both a checked endpoint and the source of the run's
  plan, so a failure here is reported like any other rather than thrown: an
  unreachable API is the most important thing this job can tell anyone, and it
  has to survive long enough to reach the issue."
  [base path opts]
  (let [resp (fetch (str base path) opts)]
    (if (ok? resp)
      {:doc (parse-json resp)}
      {:failure (assoc (select-keys resp [:status :attempts :ms])
                       :outcome :fail
                       :url (str base path)
                       :collection "discovery"
                       :kind (failure-kind resp)
                       :error-message (:error-message resp)
                       :body-excerpt (truncate (str (:body resp)) 400))})))

;; === Page specs ===

(defn parse-page-spec
  "\"1-3,275,580-582\" => #{1 2 3 275 580 581 582}. Used for bounded smoke runs;
  a nightly run leaves it unset and follows the API's own page counts."
  [s]
  (when (seq (str s))
    (into (sorted-set)
          (mapcat (fn [part]
                    (if-let [[_ a b] (re-matches #"(\d+)-(\d+)" part)]
                      (range (parse-long a) (inc (parse-long b)))
                      [(parse-long part)]))
                  (str/split (str/trim (str s)) #",")))))

(defn- pages
  "Page numbers to walk for a partition of `total` items, honouring a bounded
  `--pages` spec when one was given. Page 1 is always included: it is the probe
  that produced `total` in the first place."
  [total page-size page-spec]
  (let [n (max 1 (long (Math/ceil (/ (double total) page-size))))
        all (range 1 (inc n))]
    (if page-spec
      (filter page-spec all)
      all)))

;; === Sampling ===

(defn- sample
  "A systematic sample over sorted ids: deterministic given the data and the
  seed, spread across the whole id range rather than clustered, and — because
  the seed defaults to the day of the year — walking to a different offset each
  night, so the single-item endpoints see the whole corpus over time. Pass
  --seed to reproduce a specific night."
  [ids n seed]
  (let [ids (vec (sort ids))
        total (count ids)]
    (cond
      (not (pos? n)) []
      (<= total n) ids
      :else (let [stride (max 1 (long (Math/floor (/ (double total) n))))
                  offset (mod seed stride)]
              (vec (take n (map #(nth ids %) (range offset total stride))))))))

;; === Plans ===
;;
;; A plan is built in three phases because the page counts are not known up
;; front: probe (page 1 of every collection and every partition), then the
;; remaining pages those probes revealed, then single items sampled from the ids
;; the scroll collected.

(defn- v2-collections
  [base statuses]
  [{:id "v2/sports-sites"
    :url-fn (fn [page size] (url base "/v2/sports-sites"
                                 {:page-size size :page page :statuses statuses}))
    :total-fn (fn [parsed] (get-in parsed ["pagination" "total-items"]))
    :ids-fn (fn [parsed] (keep #(get % "lipas-id") (get parsed "items")))
    :item-url-fn (fn [id] (url base (str "/v2/sports-sites/" id) nil))}
   {:id "v2/lois"
    :url-fn (fn [page size] (url base "/v2/lois"
                                 {:page-size size :page page :statuses statuses}))
    :total-fn (fn [parsed] (get-in parsed ["pagination" "total-items"]))
    ;; A LOI carries its uuid as "id"; the path parameter is named loi-id.
    :ids-fn (fn [parsed] (keep #(get % "id") (get parsed "items")))
    :item-url-fn (fn [id] (url base (str "/v2/lois/" id) nil))}])

(defn- v1-collections
  "One partition per city code. Every site has a city, the partitions do not
  overlap, and the largest is far inside the legacy index's 10000-result window
  that an unpartitioned scroll walks straight off."
  [base city-codes fields]
  (for [city city-codes]
    {:id "v1/sports-places"
     :partition city
     :url-fn (fn [page size] (url base "/v1/sports-places"
                                  {:pageSize size :page page
                                   :cityCodes city :fields fields}))
     :total-fn (fn [_] nil)
     :total-header "x-total-count"
     :ids-fn (fn [parsed] (keep #(get % "sportsPlaceId") parsed))
     :item-url-fn (fn [id] (url base (str "/v1/sports-places/" id) nil))}))

(defn- singleton-urls
  "Endpoints that are one request each, plus the per-type-code and per-category
  reads that coerce independently of any list route."
  [base api oa2 opts]
  (case api
    :v2 (into [(str base "/v2/openapi.json")
               (str base "/v2/sports-site-categories")]
              (map #(str base "/v2/sports-site-categories/" %))
              (openapi-enum oa2 "lipas.schema.sports-sites.types.active-type-codes"))
    ;; V1's own type list, not V2's: it still advertises eight deprecated type
    ;; codes that the V2 categories no longer carry, and those single-type reads
    ;; are exactly the kind of thing that rots unnoticed.
    :v1 (into [(str base "/v1/openapi.json")
               (str base "/v1/categories")
               (str base "/v1/sports-place-types")
               (str base "/v1/deleted-sports-places")]
              (map #(str base "/v1/sports-place-types/" (get % "typeCode")))
              (some-> (fetch (str base "/v1/sports-place-types") opts)
                      parse-json))))

;; === Running ===

(defn- check!
  "One request, reduced to what the report needs. Successful bodies are parsed
  (page counts and ids come from them) but never retained past this function."
  [{:keys [url collection parse?]} opts]
  (let [resp (fetch url opts)]
    (if (ok? resp)
      (cond-> {:outcome :ok :url url :collection collection
               :status (:status resp) :ms (:ms resp) :attempts (:attempts resp)
               :headers (:headers resp)}
        parse? (assoc :parsed (parse-json resp)))
      {:outcome :fail :url url :collection collection
       :status (:status resp) :ms (:ms resp) :attempts (:attempts resp)
       :kind (failure-kind resp)
       :error-message (:error-message resp)
       :coercion (some-> (:body resp) coercion-detail)
       :body-excerpt (truncate (str (:body resp)) 400)})))

(defn- probe-total
  "How many items the partition holds, straight from the response. V2 says so in
  `pagination`; V1 says so in `X-total-count`, but only on a 206 — a partition
  that fits in one page answers 200 with no header, and then the array length is
  the total."
  [coll {:keys [parsed headers]}]
  (or (some-> (:total-header coll) (as-> h (get headers h)) parse-long)
      ((:total-fn coll) parsed)
      (when (sequential? parsed) (count parsed))
      0))

(defn- probe!
  "Page 1 of a partition, which is both a real check and the only way to learn
  how many pages follow.

  When it fails, fall back to a one-item probe. A page that 500s on a coercion
  error takes the page count down with it, and losing the other ~580 pages
  because of one bad record is exactly the blind spot this job exists to remove.
  A single item is far less likely to be the offending one, so the walk usually
  continues. The failed page stays in the report either way."
  [coll {:keys [page-size] :as opts}]
  (let [task {:url ((:url-fn coll) 1 page-size) :collection (:id coll) :parse? true}
        r (check! task opts)]
    (if (= :ok (:outcome r))
      {:result r :total (probe-total coll r)}
      (let [fallback (check! {:url ((:url-fn coll) 1 1)
                              :collection (:id coll)
                              :parse? true}
                             opts)]
        {:result r
         :total (when (= :ok (:outcome fallback)) (probe-total coll fallback))
         :blind? (not= :ok (:outcome fallback))}))))

(defn- scroll!
  "Probe every partition, follow the page counts it reports, then sample single
  items from the ids the walk collected."
  [colls {:keys [concurrency samples seed page-size] :as opts} page-spec]
  (let [probes (run-parallel! "probe" concurrency #(probe! % opts) colls)
        probed (map vector colls probes)
        ;; Every partition of a collection shares its id, ids-fn and
        ;; item-url-fn — only the URL differs — so one lookup table by
        ;; collection id serves the page results too.
        by-id (into {} (map (juxt :id identity)) colls)
        rest-tasks (for [[coll {:keys [total]}] probed
                         :when total
                         page (rest (pages total page-size page-spec))]
                     {:url ((:url-fn coll) page page-size)
                      :collection (:id coll)
                      :parse? true})
        rests (run-parallel! "pages" concurrency #(check! % opts) rest-tasks)
        results (concat (map (comp :result second) probed) rests)
        ids-by-coll (reduce (fn [acc r]
                              (if (= :ok (:outcome r))
                                (update acc (:collection r) (fnil into #{})
                                        ((:ids-fn (by-id (:collection r))) (:parsed r)))
                                acc))
                            {}
                            results)
        item-tasks (for [[coll-id ids] ids-by-coll
                         id (sample ids samples seed)]
                     {:url ((:item-url-fn (by-id coll-id)) id)
                      :collection (str coll-id "/{id}")})
        items (run-parallel! "items" concurrency #(check! % opts) item-tasks)]
    {:results (map #(dissoc % :parsed :headers) (concat results items))
     :totals (reduce (fn [acc [coll {:keys [total]}]]
                       (cond-> acc total (update (:id coll) (fnil + 0) total)))
                     {}
                     probed)
     ;; Partitions whose page count could not be established at all: their
     ;; remaining pages went unchecked, and the report must say so rather than
     ;; quietly reporting a smaller, greener run.
     :blind (for [[coll {:keys [blind?]}] probed
                  :when blind?]
              (str (:id coll) (when-let [p (:partition coll)] (str " " p))))}))

(defn- csv-set [xs]
  (when (seq xs)
    (into #{} (mapcat #(str/split (str %) #",")) xs)))

(defn run-check!
  [{:keys [base-url only collections extra-urls pages page-size] :as opts}]
  (let [page-spec (parse-page-spec pages)
        apis (case only :v1 [:v1] :v2 [:v2] [:v2 :v1])
        wanted (csv-set collections)
        keep-coll? (fn [id] (or (nil? wanted) (contains? wanted id)))
        oa2* (fetch-openapi! base-url "/v2/openapi.json" opts)
        oa1* (fetch-openapi! base-url "/v1/openapi.json" opts)
        discovery-failures (keep :failure [oa2* oa1*])
        oa2 (:doc oa2*)
        oa1 (:doc oa1*)
        statuses (openapi-enum oa2 "lipas.schema.common.statuses")
        city-codes (openapi-enum oa1 "lipas.schema.sports-sites.location.city-codes")
        ;; Ask V1 for every field it documents: without `fields` the list route
        ;; answers with bare ids and coerces almost nothing.
        fields (some->> (get-in oa1 ["paths" "/v1/sports-places" "get" "parameters"])
                        (some #(when (= "fields" (get % "name")) %))
                        (#(get-in % ["schema" "anyOf" 0 "items" "enum"])))
        colls (cond-> []
                (some #{:v2} apis) (into (v2-collections base-url statuses))
                (some #{:v1} apis) (into (v1-collections base-url city-codes fields)))
        colls (filter (comp keep-coll? :id) colls)
        singles (concat
                  (when-not wanted
                    (for [api apis
                          u (singleton-urls base-url api oa2 opts)]
                      {:url u :collection (str (name api) "/singletons")}))
                  (for [u (csv-set extra-urls)]
                    {:url (if (str/starts-with? u "http") u (str base-url u))
                     :collection "extra"}))
        _ (if (seq discovery-failures)
            (println (str "::error::Cannot read the OpenAPI documents at " base-url
                          " — nothing can be planned, reporting the failure only"))
            (println (format "Scrolling %s at page-size %d (%d partitions)"
                             base-url page-size (count colls))))
        singles (if (seq discovery-failures) [] singles)
        colls (if (seq discovery-failures) [] colls)
        single-results (when (seq singles)
                         (run-parallel! "singletons" (:concurrency opts)
                                        #(check! % opts) singles))
        {:keys [results totals blind]} (scroll! colls opts page-spec)
        all (concat discovery-failures
                    (map #(dissoc % :parsed :headers) single-results)
                    results)]
    {:base-url base-url
     :started-at (str (java.time.Instant/now))
     :full-scroll? (and (nil? page-spec) (nil? wanted) (nil? only)
                        (empty? discovery-failures))
     :page-size page-size
     :totals totals
     :blind-partitions (vec blind)
     :requests (count all)
     :failures (filter #(= :fail (:outcome %)) all)
     :by-collection (->> all
                         (group-by :collection)
                         (reduce-kv (fn [m k v]
                                      (assoc m k {:requests (count v)
                                                  :failures (count (filter #(= :fail (:outcome %)) v))}))
                                    {}))}))

;; === Reporting ===

(defn- failure-line [{:keys [url status kind attempts error-message]}]
  (format "`%s` — %s (%s, %d attempts)%s"
          url
          (if status (str "HTTP " status) "no response")
          (name kind)
          attempts
          (if error-message (str " — " error-message) "")))

(defn- records-table [records]
  (let [shown (take max-records-per-failure records)]
    (str/join
      "\n"
      (concat
        ["" "| item | id | name | offending field | rejected value |"
         "|---|---|---|---|---|"]
        (for [r shown
              f (:fields r)]
          (format "| %s | %s | %s | `%s` | `%s` |"
                  (or (:index r) "-")
                  (str (:id-key r) " " (:id r))
                  (truncate (str (:name r)) 40)
                  (:path f)
                  (:value f)))
        (when (> (count records) max-records-per-failure)
          ["" (format "…and %d more affected records — see the workflow run log."
                      (- (count records) max-records-per-failure))])))))

(defn- failure-section [idx {:keys [coercion body-excerpt] :as failure}]
  (str/join
    "\n"
    (remove nil?
            [(format "### %d. %s" idx (failure-line failure))
             ""
             (when coercion
               (format "Response coercion rejected the payload (`%s`). %d record(s) affected."
                       (:type coercion) (count (:records coercion))))
             (when coercion
               (str "\nWhat is wrong:\n"
                    (str/join "\n"
                              (->> (:records coercion)
                                   (mapcat :fields)
                                   (map (juxt :path :message))
                                   distinct
                                   (take 5)
                                   (map (fn [[p m]] (format "- `%s`: %s" p m)))))))
             (when coercion (records-table (:records coercion)))
             (when-not coercion
               (format "\nResponse body:\n\n```\n%s\n```" body-excerpt))
             ""])))

(defn ->markdown
  [{:keys [base-url started-at requests failures full-scroll? by-collection
           blind-partitions]}
   run-url]
  (let [grouped (group-by :kind failures)
        body
        (str/join
          "\n"
          (concat
            (remove nil?
                    [(format "%s requests against `%s` — **%d failing**."
                             requests base-url (count failures))
                     ""
                     (format "- run started: %s" started-at)
                     (format "- scope: %s" (if full-scroll? "complete scroll" "bounded run"))
                     (when run-url (format "- workflow run: %s" run-url))
                     (when (seq blind-partitions)
                       (format "- **%d partition(s) could not be paged at all**, so their remaining pages went unchecked: %s"
                               (count blind-partitions)
                               (str/join ", " (take 10 blind-partitions))))])
            [""
             "| failure kind | count |"
             "|---|---|"]
            (for [[k v] (sort-by key grouped)]
              (format "| %s | %d |" (name k) (count v)))
            [""
             "| collection | requests | failures |"
             "|---|---|---|"]
            (for [[k stats] (sort-by key by-collection)
                  :when (pos? (:failures stats))]
              (format "| %s | %d | %d |" k (:requests stats) (:failures stats)))
            [""
             (format "## Failures (showing %d of %d)"
                     (min max-failures-detailed (count failures)) (count failures))
             ""]
            (map-indexed (fn [i f] (failure-section (inc i) f))
                         (take max-failures-detailed failures))
            (when (> (count failures) max-failures-detailed)
              [(format "…and %d further failing requests. The workflow run log lists them all."
                       (- (count failures) max-failures-detailed))])))]
    ;; Last-resort guard: GitHub rejects an oversized body outright, and a
    ;; truncated report still names the first failures and links the run.
    (if (<= (count body) max-issue-body-chars)
      body
      (str (subs body 0 max-issue-body-chars)
           "\n\n**Report truncated to fit GitHub's issue size limit.** "
           "The workflow run artifact holds the whole thing."))))

(defn- print-summary! [{:keys [requests failures by-collection totals blind-partitions]}]
  (println)
  (println "=== Summary ===")
  (doseq [[k v] (sort-by key totals)]
    (println (format "  %-24s %d items" k v)))
  (doseq [[k stats] (sort-by key by-collection)]
    (println (format "  %-24s %5d requests, %d failures"
                     k (:requests stats) (:failures stats))))
  (println (format "  TOTAL                    %5d requests, %d failures"
                   requests (count failures)))
  (when (seq blind-partitions)
    (println (format "  %d partition(s) unpageable, remaining pages unchecked: %s"
                     (count blind-partitions) (str/join ", " (take 20 blind-partitions)))))
  (doseq [f (take 50 failures)]
    (println (str "  FAIL " (failure-line f)))
    (doseq [r (take 10 (get-in f [:coercion :records]))
            fl (:fields r)]
      (println (format "        %s %s  %s=%s" (:id-key r) (:id r) (:path fl) (:value fl))))))

;; === GitHub issue ===

(defn- gh-read!
  "Read-only gh calls run even in a dry run: `--dry-run` exists to keep test runs
  from touching issues, and listing them touches nothing."
  [args]
  (let [{:keys [out err exit]} (apply process/sh "gh" args)]
    (when-not (zero? exit)
      (println (str "::warning::gh " (str/join " " args) " failed: " (str/trim (str err)))))
    {:out out :exit exit}))

(defn- gh-write!
  [dry-run? {:keys [quiet?]} args]
  (if dry-run?
    (do (println (str "  [dry-run] gh " (str/join " " args)))
        {:exit 0})
    (let [{:keys [err exit]} (apply process/sh "gh" args)]
      (when (and (not (zero? exit)) (not quiet?))
        (println (str "::warning::gh " (str/join " " args) " failed: " (str/trim (str err)))))
      {:exit exit})))

(defn- open-issue-number
  "The deduplication key: the first open issue carrying the `api-health` label
  whose title starts with the stable prefix. A persistent failure comments on
  this one instead of filing a fresh issue every night."
  []
  (let [{:keys [out]} (gh-read! ["issue" "list" "--label" issue-label
                                 "--state" "open" "--json" "number,title"
                                 "--limit" "50"])]
    (->> (try (json/parse-string (str out) true) (catch Exception _ nil))
         (filter #(str/starts-with? (str (:title %)) issue-title))
         first
         :number)))

(defn manage-issue!
  "Deduplicates on an open `api-health` issue with the stable title: a persistent
  failure comments on the existing one rather than filing a new issue every
  night. A clean full scroll closes it — every request is retried before being
  called broken, so a clean walk of every page is trustworthy enough to act on,
  and a regression simply files a fresh issue rather than reviving a stale one.
  A clean *bounded* run never closes anything: a manual dispatch against another
  environment must not resolve a production report.

  Returns true when everything it attempted succeeded."
  [{:keys [failures full-scroll?]} body-file run-url {:keys [dry-run]}]
  (let [existing (open-issue-number)
        write! (fn [opts args] (zero? (:exit (gh-write! dry-run opts args))))]
    (cond
      (seq failures)
      (if existing
        (do (println (format "Commenting on existing issue #%d" existing))
            (write! {} ["issue" "comment" (str existing) "--body-file" body-file]))
        (do (println "Filing a new issue")
            ;; Already-exists is the normal case, so this one stays quiet.
            (gh-write! dry-run {:quiet? true}
                       ["label" "create" issue-label
                        "--color" "B60205"
                        "--description" "Automated public API health checks"])
            (write! {} ["issue" "create" "--title" issue-title
                        "--label" issue-label "--body-file" body-file])))

      (and existing full-scroll?)
      (do (println (format "Check is clean — closing issue #%d" existing))
          (and (write! {} ["issue" "comment" (str existing)
                           "--body" (format "A complete scroll of V1 and V2 passed clean%s. Closing; a regression will file a fresh issue."
                                            (if run-url (str " ([run](" run-url "))") ""))])
               (write! {} ["issue" "close" (str existing) "--reason" "completed"])))

      existing
      (do (println (format "Check is clean, but this was a bounded run — leaving #%d open"
                           existing))
          true)

      :else
      (do (println "Check is clean, no open issue.") true))))

;; === Entry point ===

(defn -main [args]
  (let [opts (cli/parse-opts args {:spec cli-spec})]
    (if (:help opts)
      (println (cli/format-opts {:spec cli-spec}))
      (let [opts (update opts :seed #(or % (.getDayOfYear (java.time.LocalDate/now))))
            run-url (System/getenv "GITHUB_RUN_URL")
            report (run-check! opts)
            md (->markdown report run-url)
            md-file (or (:report-md opts)
                        (str (java.io.File/createTempFile "api-scroll-" ".md")))]
        (print-summary! report)
        (spit md-file md)
        (when-let [f (:report-json opts)]
          (spit f (json/generate-string report {:pretty true})))
        (let [issue-ok? (or (not (:github-issue opts))
                            (manage-issue! report md-file run-url opts))]
          ;; A failed scroll is red, and so is a scroll whose report never made
          ;; it to an issue — silently failing to file is how this stops working.
          (System/exit (if (and (empty? (:failures report)) issue-ok?) 0 1)))))))

(-main *command-line-args*)
