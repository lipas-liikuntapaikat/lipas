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
