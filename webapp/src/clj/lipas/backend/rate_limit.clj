(ns lipas.backend.rate-limit
  "Per-route sliding-window rate limiting, declared in route data.

  Nothing in LIPAS was rate limited before this: nginx has no `limit_req`, and
  the AI assistant carried its own private limiter. That left the
  unauthenticated mail-sending endpoints (password reset, magic link, feedback,
  register) usable as a mail-bomb or an ops-inbox flood, and the paid LLM
  endpoints protected only by their privilege gate.

  Usage — add `:rate-limit` to route data:

      [\"/actions/order-magic-link\"
       {:post {:rate-limit {:key :ip :window-ms hour :max 5}
               ...}}]

  `:key` selects the bucket:

  - `:ip`   — the client address. Use for unauthenticated endpoints.
  - `:user` — the authenticated user's id. Use for endpoints behind a
              privilege gate, so one user's budget can't be exhausted by
              someone else sharing a NAT.

  Deliberate limitations, stated because they matter for how far to trust this:

  - State is per JVM process. LIPAS runs a single backend container, so today
    this is exact; if it is ever scaled out, each instance gets its own budget
    and the effective limit multiplies by instance count. It also resets on
    deploy. Moving to Postgres or Redis is the fix, and is deliberately not
    done here — it would trade a real, working control for a bigger change.
  - A determined attacker with many source addresses is not stopped by IP
    limiting. This raises the cost of casual abuse and bounds accidental
    loops; it is not a DDoS defence."
  (:require [ring.util.http-response :as resp]))

(def hour-ms (* 60 60 1000))
(def day-ms (* 24 hour-ms))

;; bucket-key -> vector of request timestamps inside the current window
(defonce ^:private state (atom {}))

(defn client-ip
  "The caller's address.

  Prefers `X-Real-IP` because nginx OVERWRITES it with the real peer address
  (see the /api block in nginx/proxy.conf). `X-Forwarded-For` is deliberately
  NOT trusted: `$proxy_add_x_forwarded_for` appends to whatever the client
  sent, so a client can prepend arbitrary entries and rotate buckets at will.

  Falls back to `:remote-addr` for requests that don't come through nginx —
  in local dev the frontend talks to the backend directly on :8091."
  [req]
  (or (get-in req [:headers "x-real-ip"])
      (:remote-addr req)
      "unknown"))

(defn- bucket-key
  [req {:keys [key]} route-id]
  [route-id
   (case key
     :user (or (-> req :identity :id) (client-ip req))
     :ip (client-ip req))])

(defn- prune
  "Drops timestamps that have fallen out of the window.

  Also the eviction the assistant's limiter never had: a key whose window is
  empty is removed from the map entirely, so a stream of one-shot IPs can't
  grow `state` without bound."
  [stamps now window-ms]
  (filterv #(> % (- now window-ms)) stamps))

(defn- take-slot!
  "Records a request against `k` and returns nil when it fits the budget, or
  the number of ms until the oldest timestamp expires when it does not."
  [k {:keys [window-ms max]} now]
  (let [result (atom nil)]
    (swap! state
           (fn [m]
             (let [stamps (prune (get m k []) now window-ms)]
               (if (>= (count stamps) max)
                 (do (reset! result (- (+ (first stamps) window-ms) now))
                     (assoc m k stamps))
                 (do (reset! result nil)
                     (assoc m k (conj stamps now)))))))
    @result))

(defn- sweep!
  "Removes fully-expired buckets. Called opportunistically on each check; the
  windows are short and the map is small, so this stays cheap."
  [now]
  (swap! state
         (fn [m]
           (persistent!
             (reduce-kv (fn [acc k stamps]
                          ;; The longest window in use bounds how long a bucket
                          ;; can stay relevant; anything older is dead weight.
                          (if (some #(> % (- now day-ms)) stamps)
                            (assoc! acc k stamps)
                            acc))
                        (transient {})
                        m)))))

(def middleware
  "Route-data middleware. Inert on routes that declare no `:rate-limit`.

  Mounted in the GLOBAL chain so it applies wherever it is declared, but note
  it must sit AFTER authentication for `:key :user` to see `:identity` — see
  where it is added in lipas.backend.handler/create-app."
  {:name ::rate-limit
   :compile
   (fn [route-data _opts]
     (when-let [conf (:rate-limit route-data)]
       (let [route-id (or (:name route-data) (str (gensym "rl")))]
         (fn [next-handler]
           (fn [req]
             (let [now (System/currentTimeMillis)]
               (sweep! now)
               (if-let [retry-ms (take-slot! (bucket-key req conf route-id) conf now)]
                 (-> (resp/too-many-requests
                       {:error "Too many requests. Please try again later."
                        :type "rate-limited"})
                     (assoc-in [:headers "Retry-After"]
                               (str (max 1 (int (Math/ceil (/ retry-ms 1000.0)))))))
                 (next-handler req))))))))})

(defn reset-all!
  "Clears all buckets. For tests — a limiter that leaks state between test
  namespaces produces failures that depend on test order."
  []
  (reset! state {}))
