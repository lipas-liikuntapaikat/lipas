(ns lipas.ui.login.session-expiry
  "The rule for deciding whether a re-frame event means the session has died.

  Split out of `lipas.ui.login.events` (and written as .cljc) so the policy can
  be tested on the JVM: that namespace is .cljs and registers a global
  interceptor as a load-time side effect.")

(defn ajax-failure-401?
  "True when `x` looks like a cljs-ajax failure response carrying a 401.

  Nothing marks an event argument as an HTTP response, so this matches on the
  shape cljs-ajax produces (`ajax.interceptors/fail`): a map with both
  `:status` and `:failure`. Requiring `:failure` keeps it from firing on some
  unrelated domain map that happens to have a `:status` of 401."
  [x]
  (and (map? x)
       (= 401 (:status x))
       (contains? x :failure)))

(defn expired?
  "True when `event` (a re-frame event vector) is an authentication failure
  meaning this session is over.

  - `logged-in?` — when nobody is logged in there is no session to expire, so
    the login form's own 401 is excluded without having to inspect the request.
    The failure map carries neither the URI nor the Authorization header, so
    this is the only signal available — and the better one.
  - `ignored-event-ids` — events that own their 401 already and must be left
    alone. Passed in rather than listed here so the caller can name them with
    `::` and a rename can't silently leave a stale keyword behind."
  [logged-in? ignored-event-ids event]
  (let [[event-id & args] event]
    (boolean
      (and logged-in?
           (not (contains? (set ignored-event-ids) event-id))
           (some ajax-failure-401? args)))))
