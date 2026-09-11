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
;; JWT as an identity, so a registration link signed with the plain key would
;; authenticate as {:email ...} on every token-auth route. With its own key it
;; can't: it fails signature verification there, and conversely a login token
;; fails it here. The :purpose claim is checked too, so two purposes can never
;; share a key by accident.

(def email-verification-valid-seconds
  "Life of an emailed registration link. Requested moments before use, so it can
  be short; long enough to survive a slow mail relay or a lunch break."
  (* 24 3600))

(defn- purpose-key [purpose]
  (str (env :auth-key) "|" purpose))

(defn create-email-verification-token
  "Token proving that whoever holds it received mail at `email`."
  [email]
  (let [now (java.time.Instant/now)]
    (jwt/sign {:email   email
               :purpose "email-verification"
               :iat     (.getEpochSecond now)
               :exp     (.plusSeconds now email-verification-valid-seconds)}
              (purpose-key "email-verification")
              {:alg :hs512})))

(defn unsign-email-verification-token
  "The email address `token` was issued for, or nil when the token is expired,
  malformed, forged, or minted for another purpose (e.g. a login token)."
  [token]
  (try
    (let [claims (jwt/unsign token (purpose-key "email-verification") {:alg :hs512})]
      (when (= "email-verification" (:purpose claims))
        (:email claims)))
    (catch Exception _ nil)))
