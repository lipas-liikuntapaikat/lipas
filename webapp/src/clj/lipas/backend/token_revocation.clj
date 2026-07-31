(ns lipas.backend.token-revocation
  "Per-user JWT revocation (finding M7).

  LIPAS tokens are stateless and bake the user's roles into the payload, so
  until now nothing could invalidate one before it expired: archiving an account
  or stripping its roles took up to 6h to take effect, 7 days for a magic-link
  token. `lipas.backend.auth/active?` closed the MINTING side; this closes the
  USING side.

  The mechanism is one nullable column, `account.tokens_valid_from`, and one
  comparison — no session table, nothing per-token stored anywhere:

    reject the token when its `:iat` predates the account's `tokens_valid_from`

  Semantics, in the order the check applies them:

  - `tokens_valid_from` NULL ⇒ allow. This is the state every existing account
    is in, so deploying the migration logs nobody out.
  - set, and the token carries no `:iat` (minted before this change shipped) ⇒
    reject. The account has been explicitly revoked since; an `:iat`-less token
    must not slip through the one hole in the scheme.
  - set, and `:iat` >= `tokens_valid_from` ⇒ allow.

  Both sides of the comparison are whole seconds from the SAME clock (the app
  JVM's): `jwt/create-token` writes `:iat` as epoch seconds, and `revoke!`
  truncates the instant it stores to seconds. That matters more than it looks.
  `:iat` is seconds-granular by JWT convention, so if the revocation point kept
  sub-second precision, a token minted in the same second as (but microseconds
  after) a revocation would have `:iat` < `tokens_valid_from` and be rejected on
  arrival — which is exactly the magic link inside the permissions-updated
  email, and exactly the fresh token `refresh-login` hands back. Truncating
  makes the same-second case ALLOW. The cost is that a token minted in the same
  second just BEFORE a revocation also survives; against a 6h token lifetime
  that sub-second window is noise, and erring this way cannot lock anybody out.

  Using the app clock for the stored value (rather than Postgres `now()`) keeps
  the two sides immune to clock skew between the app host and the database."
  (:require
    [lipas.backend.db.db :as db]
    [taoensso.timbre :as log])
  (:import
    (java.sql Timestamp)
    (java.time Instant)
    (java.time.temporal ChronoUnit)))

(def ^:private cache-ttl-ms
  "How long a looked-up `tokens_valid_from` is trusted.

  EVERY authenticated request runs the check, so without a cache this would be
  one extra DB round-trip per request. Revocation is not a real-time
  requirement, and `revoke!` drops the affected entry itself, so this TTL only
  bounds staleness for revocations performed outside the app process (a manual
  UPDATE, or another node in a multi-node deployment)."
  5000)

(def ^:private cache-max-entries
  "Ceiling on cached accounts. Entries are tiny and there are only thousands of
  accounts, but the cache is keyed by a value that arrives in a request, so it
  gets a bound rather than trust."
  10000)

(defonce ^:private cache
  ;; {user-id-string {:valid-from <epoch-seconds-or-nil> :expires-at <ms>}}
  (atom {}))

(defn clear-cache!
  "Drops every cached lookup. For tests and the REPL."
  []
  (reset! cache {}))

(defn forget!
  "Drops the cached lookup for one account, so the next request re-reads it."
  [user-id]
  (swap! cache dissoc (str user-id))
  nil)

(defn- fetch-valid-from
  "`tokens_valid_from` for `user-id` as epoch seconds, or nil.

  nil covers two cases deliberately treated the same: the column is NULL (never
  revoked), and there is no such account row. An absent row is not a revocation
  signal — nothing in LIPAS deletes accounts, they are archived — and a request
  from a token whose account is gone fails on its first real DB read anyway.
  Treating it as revoked would only add a way to lock people out."
  [db-spec user-id]
  (when-let [^Timestamp ts (db/get-user-tokens-valid-from db-spec {:id user-id})]
    (.getEpochSecond (.toInstant ts))))

(defn- valid-from
  "Cached `fetch-valid-from`."
  [db-spec user-id]
  (let [now (System/currentTimeMillis)
        entry (get @cache user-id)]
    (if (and entry (< now (:expires-at entry)))
      (:valid-from entry)
      (let [v (fetch-valid-from db-spec user-id)]
        (swap! cache (fn [m]
                       (-> (if (> (count m) cache-max-entries) {} m)
                           (assoc user-id {:valid-from v
                                           :expires-at (+ now cache-ttl-ms)}))))
        v))))

(defn revoked?
  "True when `claims` (an unsigned JWT payload) must no longer be accepted.

  Called from `lipas.backend.middleware/auth` on every authenticated request.

  FAILS CLOSED: if the lookup throws — database down, `:id` not a uuid, a
  missing `:lipas/db` in the request because the global middleware chain got
  rewired — this answers true and the request gets a 401. That direction is
  deliberate. An auth outage is loud, bounded by the outage, and recoverable; a
  token that silently keeps working after an account was archived is neither
  visible nor recoverable, and is the whole finding this closes."
  [db-spec claims]
  (try
    (if (nil? db-spec)
      (do (log/error "No database in the request: cannot check token revocation."
                     "Rejecting. lipas.backend.middleware/wrap-db must be first"
                     "in the global middleware chain.")
          true)
      (let [user-id (str (:id claims))
            from (valid-from db-spec user-id)
            iat (:iat claims)]
        (cond
          (nil? from) false
          (nil? iat) (do (log/infof "Rejecting a token with no :iat for user %s: account revoked at %s"
                                    user-id from)
                         true)
          (< ^long iat ^long from) (do (log/infof "Rejecting a token issued at %s for user %s: account revoked at %s"
                                                  iat user-id from)
                                       true)
          :else false)))
    (catch Exception e
      (log/error e "Token revocation check failed - rejecting the request.")
      true)))

(defn revoke!
  "Invalidates every token `user` currently holds, by moving the account's
  revocation point to now.

  Safe to call inside a transaction (pass the tx as `db-spec`); the cache drop
  is idempotent, so a rolled-back transaction costs one re-read.

  Does NOT affect tokens minted afterwards — see the namespace docstring on why
  the stored instant is truncated to whole seconds."
  [db-spec user]
  (let [now (.truncatedTo (Instant/now) ChronoUnit/SECONDS)]
    (db/update-user-tokens-valid-from!
      db-spec
      {:id (:id user) :tokens-valid-from (Timestamp/from now)})
    (forget! (:id user))
    nil))
