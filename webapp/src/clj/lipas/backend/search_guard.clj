(ns lipas.backend.search-guard
  "Validation of Elasticsearch request bodies that arrive from untrusted clients.

  `POST /api/actions/search` and its siblings are unauthenticated and forward
  the request body verbatim to Elasticsearch's `_search` API. The indexed data
  is public open data, so disclosure is not the concern. The two real risks are

    1. anonymous arbitrary Painless execution inside the ES cluster, and
    2. unbounded CPU/heap consumption (huge `size`, huge aggregations) against
       the cluster the whole site depends on.

  This namespace closes both. It is applied at the public HTTP boundary only —
  never inside `lipas.backend.core/search` or `lipas.backend.search/search` —
  so backend-internal callers that legitimately build large queries
  (`core/org-sites` uses `:size 2000`, `core/calculate-stats` uses an
  aggregation `:size 400`) stay unaffected.

  Violations throw; they never rewrite the query. Silently stripping a script
  or clamping an oversized `size` would hide misuse from us and hand the caller
  a quietly-wrong answer, so instead `check-query!` throws an ex-info that
  `lipas.backend.handler` maps to HTTP 400. The caps below are set above every
  query shape any real LIPAS client sends, so a 400 always means misuse."
  (:require [clojure.string :as str]))

(def max-size
  "Cap for the top-level `:size`.

  Largest value any real client sends is 5000: the map search sends
  `{:from 0 :size 5000}` in analysis mode (`lipas.ui.search.events/
  resolve-pagination`) and logged-in users can pick a 5000-row page size
  (`lipas.ui.search.subs/pagination`). The report flow
  (`::create-report-from-current-search`) sends `:size 1000`.

  10000 gives 2x headroom over that and is exactly Elasticsearch's own default
  `index.max_result_window`: ES rejects `from + size > 10000` for a plain
  search anyway, so this cap never rejects a query ES would have accepted — it
  only turns what would be a spandex 500 into a clean 400."
  10000)

(def max-from
  "Cap for the top-level `:from`.

  Paging in the map's result table sends `:from (* page page-size)`. It is
  bounded in practice by the same `index.max_result_window` of 10000 that
  bounds `from + size`, so nothing legitimate ever exceeds this."
  10000)

(def max-agg-size
  "Cap for every `:size` below the top level — aggregation sizes, `top_hits`
  sizes, `inner_hits` sizes.

  Largest value any real client sends is 1000: the age-structure report's
  `composite` aggregation (`lipas.ui.stats.age-structure.events/->query`).
  Everything else is smaller — finance and subsidies use `terms` `:size 400`
  and a nested `terms` `:size 20`. 2000 gives 2x headroom over the real
  maximum while keeping a hostile 100k-bucket aggregation out of the cluster."
  2000)

(defn- key-name
  "The name of a map key as a string, or nil for keys that cannot carry an ES
  parameter name. Muuntaja decodes JSON bodies to keywords, but transit
  clients (the finance, subsidies and report flows all use transit) can send
  string keys, so both are handled."
  [k]
  (cond
    (keyword? k) (name k)
    (string? k) k
    (symbol? k) (name k)
    :else nil))

(defn scripting-key?
  "True if `k` names an Elasticsearch parameter that can carry executable code.

  Matching is a lower-cased SUBSTRING test for \"script\" rather than a set of
  known parameter names. ES spells scripting into a long and growing list of
  parameters, and one missed name is a full bypass:

    script, script_fields, script_score, scripted_metric and its
    init_script / map_script / combine_script / reduce_script, bucket_script,
    bucket_selector's script, moving_fn's script, script_query, the script
    inside a sort, a runtime field, a terms aggregation's script, ...

  A substring test covers all of them plus anything ES adds later. It is
  deliberately over-broad — it also rejects an innocent word like
  \"description\" — which is the right trade for an unauthenticated endpoint:
  no key name in the mappings of any index this guard protects contains the
  substring (checked against `lipas.backend.search/mappings`), so field-name
  keys are never hit. Note that `function_score` — which the frontend does
  use — contains \"score\", not \"script\", and is unaffected.

  `runtime_mappings` is matched exactly because it defines runtime fields whose
  scripts are evaluated once per document, i.e. it is scripting under a name
  that does not contain \"script\"."
  [k]
  (boolean
    (when-let [s (some-> (key-name k) str/lower-case)]
      (or (str/includes? s "script")
          (= "runtime_mappings" (str/replace s "-" "_"))))))

(defn- find-violation
  "Depth-first walk of `x` returning the first rule violation as a map, or nil.

  `root?` is true only for the outermost map, which is where `:size` and
  `:from` mean result-window paging; every `:size` below that is an
  aggregation-ish size and gets the tighter cap."
  [x path root?]
  (cond
    (map? x)
    (some (fn [[k v]]
            (let [kn (key-name k)
                  path* (conj path (or kn (str k)))]
              (cond
                (scripting-key? k)
                {:rule :scripting :path path* :key (or kn (str k))}

                ;; Only cap numeric values. A field genuinely named "size"
                ;; would appear as e.g. {:range {:size {:gte 1}}}, where the
                ;; value is a map, not a limit.
                (and (= "size" kn) (number? v))
                (let [limit (if root? max-size max-agg-size)]
                  (when (> v limit)
                    {:rule :size :path path* :value v :limit limit}))

                (and root? (= "from" kn) (number? v) (> v max-from))
                {:rule :from :path path* :value v :limit max-from}

                :else
                (find-violation v path* false))))
          x)

    (sequential? x)
    (some (fn [[i v]] (find-violation v (conj path (str i)) false))
          (map-indexed vector x))

    :else nil))

(defn- violation-message
  [{:keys [rule path key value limit]}]
  (let [at (str/join "." path)]
    (case rule
      :scripting (str "Scripting is not allowed in search queries. "
                      "Offending key \"" key "\" at " at ".")
      :size (str "Query size " value " at " at " exceeds the maximum of " limit ".")
      :from (str "Query from " value " at " at " exceeds the maximum of " limit "."))))

(defn violation
  "Returns the first rule violation in ES query body `q` as a map
  `{:rule :scripting|:size|:from :path [...] ...}`, or nil when `q` is safe.
  Pure — useful for testing and for callers that want to inspect rather than
  throw."
  [q]
  (find-violation q [] true))

(defn check-query!
  "Validates an untrusted Elasticsearch query body. Returns `q` unchanged when
  it is safe, otherwise throws an ex-info with `:type :invalid-search-query`,
  which `lipas.backend.handler` turns into an HTTP 400.

  Call this at the public handler boundary, never inside the search functions
  themselves — internal callers build their own queries and must not be
  constrained by these caps."
  [q]
  (when-let [v (violation q)]
    (throw (ex-info (violation-message v)
                    (assoc v :type :invalid-search-query))))
  q)
