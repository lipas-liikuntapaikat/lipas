(ns lipas.backend.control-chars
  "Rejects requests that carry control characters in user input.

  Postgres can't store NUL in text or jsonb, so a NUL that got past validation
  used to fail the save with a 500. Rather than adding a check to every
  `:string` schema, this middleware walks the decoded request once, before
  coercion, and answers 400 with the paths of the offending values.

  It runs on decoded data, so one check covers JSON, EDN and Transit bodies
  alike. EDN and Transit can also carry keywords, symbols and characters, and
  any of them as map keys, so those are checked too. Open maps are covered as
  well: keys a schema doesn't know about are still walked.

  \\t \\n \\r are allowed; multiline text legitimately contains them.

  Not covered: multipart params, which are decoded by route-level middleware
  that runs after this one."
  (:require [lipas.schema.common :as common]))

(def ^:private max-paths
  "Enough to show the caller where the problem is without echoing a whole
  payload back."
  10)

(defn- bad-text? [x]
  (when-let [s (cond
                 (string? x) x
                 (or (keyword? x) (symbol? x)) (str x)
                 (char? x) (str x))]
    (boolean (re-find common/control-chars-except-whitespace s))))

(defn offending-paths
  "`get-in` style paths to the values in `x` that contain control characters
  other than \\t \\n \\r. A map key that contains one is reported at the path
  of its entry; a set member at the path of the set. At most `max-paths`."
  [x]
  (letfn [(walk [path x]
            (cond
              (bad-text? x) [path]
              (map? x) (mapcat (fn [[k v]]
                                 (let [path (conj path k)]
                                   (if (bad-text? k) [path] (walk path v))))
                               x)
              (sequential? x) (mapcat #(walk (conj path %1) %2) (range) x)
              (set? x) (when (some #(seq (walk path %)) x) [path])))]
    (take max-paths (walk [] x))))

(def ^:private request-parts
  "Request keys holding user input, and the name each gets in reported paths.
  :body-params is set by muuntaja, the others by ring params and reitit."
  [[:body-params :body]
   [:query-params :query]
   [:form-params :form]
   [:path-params :path]])

(defn- request-offending-paths [request]
  (into []
        (comp (mapcat (fn [[k part]]
                        (map #(into [part] %) (offending-paths (get request k)))))
              (take max-paths))
        request-parts))

(def middleware
  "Throws `:type :control-characters` (an HTTP 400, see
  `lipas.backend.handler/exception-handlers`) when any user input in the
  request contains control characters. Mount it after muuntaja's
  format-request-middleware, so the body is already decoded."
  {:name ::reject-control-characters
   :wrap (fn [handler]
           (fn [request]
             (if-let [paths (not-empty (request-offending-paths request))]
               (throw (ex-info "Request contains control characters"
                               {:type :control-characters :paths paths}))
               (handler request))))})
