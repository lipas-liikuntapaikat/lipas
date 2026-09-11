(ns lipas.backend.jwt
  (:require
    [buddy.sign.jwt :as jwt]
    [environ.core :refer [env]]))

(def sign #(jwt/sign % (env :auth-key) {:alg :hs512}))
(def unsign #(jwt/unsign % (env :auth-key) {:alg :hs512}))

(defn create-token
  "Creates signed jwt-token with user data as payload.

  `valid-seconds` sets the expiration span
  `terse?` include only users :id in payload (fits in URL)
  `extra-claims` map merged into the payload (e.g. :impersonator)"
  [user & {:keys [terse? valid-seconds extra-claims]
           :or   {terse?        false
                  valid-seconds (* 6 3600)}}] ;; 6 hours
  (let [fields  (if terse?
                  [:id]
                  [:id :email :username :permissions])
        now     (java.time.Instant/now)
        payload (-> user
                    (select-keys fields)
                    (merge extra-claims)
                    ;; `:iat` is what makes per-user revocation possible: a
                    ;; token whose :iat predates the account's
                    ;; `tokens_valid_from` is rejected (see
                    ;; lipas.backend.token-revocation). Epoch SECONDS, matching
                    ;; how buddy serialises the `:exp` Instant on the next line
                    ;; — buddy normalises registered date claims to integer
                    ;; NumericDate, so both end up as seconds in the payload and
                    ;; the revocation comparison is apples to apples.
                    (assoc :iat (.getEpochSecond now)
                           :exp (.plusSeconds now valid-seconds)))]
    (sign payload)))

;;; Single-purpose tokens ;;;
;;
;; Signed with a key DERIVED from :auth-key, never with :auth-key itself. The
;; login backend (lipas.backend.auth/token-backend) accepts any :auth-key-signed
;; JWT as an identity, so a registration or email-change link signed with the
;; plain key would authenticate on every token-auth route. With a per-purpose
;; key it can't: it fails signature verification there, a login token fails it
;; here, and one purpose's token fails another's. The :purpose claim is checked
;; too, so two purposes can never share a key by accident.

(defn- purpose-key [purpose]
  (str (env :auth-key) "|" purpose))

(defn- create-purpose-token [purpose claims valid-seconds]
  (let [now (java.time.Instant/now)]
    (jwt/sign (assoc claims
                     :purpose purpose
                     :iat     (.getEpochSecond now)
                     :exp     (.plusSeconds now valid-seconds))
              (purpose-key purpose)
              {:alg :hs512})))

(defn- unsign-purpose-token
  "The claims of `token`, or nil when it is expired, malformed, forged, or
  minted for another purpose (e.g. a login token)."
  [purpose token]
  (try
    (let [claims (jwt/unsign token (purpose-key purpose) {:alg :hs512})]
      (when (= purpose (:purpose claims))
        claims))
    (catch Exception _ nil)))

;; Registration: proves the requester reads the address.

(def email-verification-valid-seconds
  "Life of an emailed registration link. Requested moments before use, so it can
  be short; long enough to survive a slow mail relay or a lunch break."
  (* 24 3600))

(defn create-email-verification-token
  "Token proving that whoever holds it received mail at `email`."
  [email]
  (create-purpose-token "email-verification" {:email email}
                        email-verification-valid-seconds))

(defn unsign-email-verification-token
  "The email address `token` was issued for, or nil (see unsign-purpose-token)."
  [token]
  (:email (unsign-purpose-token "email-verification" token)))

;; Email change: proves the new address and names the change it confirms.

(def email-change-valid-seconds (* 24 3600))

(defn create-email-change-token
  "`claims`: {:account-id :old-email :new-email :by :lang}. :old-email is what
  makes a link single-use and supersedable: confirming checks the account still
  has it (lipas.backend.core/confirm-email-change!)."
  [claims]
  (create-purpose-token "email-change" claims email-change-valid-seconds))

(defn unsign-email-change-token
  "The claims of an email-change `token`, or nil (see unsign-purpose-token)."
  [token]
  (unsign-purpose-token "email-change" token))
