(ns lipas.backend.token-revocation-test
  "Per-user token revocation, end to end through the real router (finding M7).

   JWTs are stateless and bake the user's roles into the payload, so nothing
   could invalidate one before it expired: archiving an account or stripping its
   roles took up to 6h to take effect, 7 days for a magic-link token.
   `lipas.backend.auth/active?` blocked MINTING tokens for archived accounts;
   this closes the USING side, via one nullable column
   (`account.tokens_valid_from`) and one comparison in
   `lipas.backend.middleware/auth`. See `lipas.backend.token-revocation`.

   Two things this namespace is careful about, because the failure mode here is
   locking users out rather than letting them in:

   - Every rejection test is paired with a positive control. A bug that rejects
     everything would satisfy all the negative assertions on its own while being
     a total auth outage, so `token-minted-after-revocation-still-works-test`
     and `revocation-does-not-block-password-login-test` are load-bearing.
   - Tokens are backdated with `token-issued-secs-ago` rather than minted and
     revoked in the same instant. Both sides of the comparison are whole seconds
     (`:iat` is seconds by JWT convention), and the check deliberately ALLOWS the
     same-second case so a token minted right after a revocation is never
     rejected — which means a test that minted and revoked microseconds apart
     would pass or fail depending on where a second boundary happened to fall.

   The probe endpoint is POST /api/actions/get-upcoming-reminders: authenticated
   but privilege-free, no request body, and — unlike /actions/refresh-login — it
   does not check `auth/active?`, so a 401 from it can only have come from the
   revocation check. That matters for the archiving test, where both mechanisms
   would otherwise answer 401 and the test would prove nothing."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [lipas.backend.core :as core]
            [lipas.backend.db.db :as db]
            [lipas.backend.email :as email]
            [lipas.backend.jwt :as jwt]
            [lipas.backend.token-revocation :as revocation]
            [lipas.test-utils :as tu]
            [ring.mock.request :as mock]))

;;; Test system setup ;;;

(defonce test-system (atom nil))

(let [{:keys [once each]} (tu/full-system-fixture test-system)]
  (use-fixtures :once once)
  ;; Both :each fixtures in ONE call. `clojure.test/use-fixtures` ASSOCs the
  ;; namespace's fixture list rather than appending to it, so registering the
  ;; cache-clearing one separately would silently drop `each` — and with it the
  ;; prune between tests.
  ;;
  ;; The revocation lookup is cached process-wide for a few seconds, so a
  ;; leftover entry from another test could otherwise decide this one.
  (use-fixtures :each each (fn [f]
                             (revocation/clear-cache!)
                             (f)
                             (revocation/clear-cache!))))

(defn test-db [] (:lipas/db @test-system))
(defn test-app [req] ((:lipas/app @test-system) req))

;;; Helpers ;;;

(def ^:private probe-path
  "See the namespace docstring on why this endpoint."
  "/api/actions/get-upcoming-reminders")

(defn- probe
  "Status of a request to the probe endpoint carrying `token` (nil ⇒ anonymous)."
  [token]
  (:status (test-app (cond-> (mock/request :post probe-path)
                       token (tu/token-header token)))))

(defn- token-issued-secs-ago
  "A token for `user` identical in shape to one `jwt/create-token` would mint,
   except its `:iat` is `secs` seconds in the past.

   Real tokens are minted seconds to hours before the revocation that kills
   them. Backdating reproduces that without a `Thread/sleep`, and keeps the tests
   off the same-second boundary the check deliberately allows."
  [user secs]
  (let [now (java.time.Instant/now)]
    (jwt/sign (-> user
                  (select-keys [:id :email :username :permissions])
                  (assoc :iat (- (.getEpochSecond now) secs)
                         :exp (.plusSeconds now 3600))))))

(defn- token-without-iat
  "A token minted the way `create-token` did BEFORE this change: no `:iat` at
   all. Every token in the wild at deploy time looks like this."
  [user]
  (jwt/sign (-> user
                (select-keys [:id :email :username :permissions])
                (assoc :exp (.plusSeconds (java.time.Instant/now) 3600)))))

(defn- tokens-valid-from
  "The stored revocation point, straight from the database."
  [user]
  (db/get-user-tokens-valid-from (test-db) {:id (:id user)}))

(defn- claims [token]
  (jwt/unsign token))

(defn- token-ttl-seconds
  "How long `token` is valid for, read off its own claims."
  [token]
  (let [{:keys [iat exp]} (claims token)]
    (- exp iat)))

(defrecord CapturingEmailer [sent]
  email/Emailer
  (send! [_ message]
    (swap! sent conj message)
    {:status "OK"}))

(defn- capturing-emailer [] (->CapturingEmailer (atom [])))

(defn- sent-tokens
  "Every magic-link token found in the mail an emailer captured, oldest first."
  [emailer]
  (->> @(:sent emailer)
       (mapcat (juxt :plain :html))
       (remove nil?)
       (mapcat (fn [body] (map second (re-seq #"\?token=([A-Za-z0-9._-]+)" body))))
       distinct))

;;; The check itself ;;;

(deftest probe-endpoint-is-authenticated-test
  ;; Guards every other test here: if the probe path stopped requiring
  ;; authentication — renamed, or moved to the public allowlist — all the 200s
  ;; below would be vacuous and the negatives would be the only thing left.
  (is (= 401 (probe nil))
      (str probe-path " must require authentication")))

(deftest token-issued-before-revocation-is-rejected-test
  (let [user (tu/gen-regular-user :db-component (test-db))
        token (token-issued-secs-ago user 60)]
    (is (= 200 (probe token))
        "control: the token works before anything is revoked")
    (revocation/revoke! (test-db) user)
    (is (some? (tokens-valid-from user))
        "revoke! must actually write the revocation point")
    (is (= 401 (probe token))
        "a token issued before the revocation point must be rejected")))

(deftest token-minted-after-revocation-still-works-test
  ;; THE control for the whole namespace. Without it, an implementation that
  ;; rejected every token would satisfy every negative test here while being a
  ;; complete authentication outage. Deliberately no sleep between the revoke and
  ;; the mint: the token lands in the same whole second as the revocation point,
  ;; which is precisely the case that must not lock anybody out (it is what the
  ;; user gets when they log in again, and what the permissions-updated email's
  ;; magic link is).
  (let [user (tu/gen-regular-user :db-component (test-db))]
    (revocation/revoke! (test-db) user)
    (is (= 200 (probe (jwt/create-token user)))
        (str "a token minted after the revocation point must be accepted, even"
             " when minted within the same second"))))

(deftest never-revoked-user-is-unaffected-test
  ;; The state every existing account is in the moment the migration runs.
  (let [user (tu/gen-regular-user :db-component (test-db))]
    (is (nil? (tokens-valid-from user))
        "a fresh account must have tokens_valid_from NULL")
    (is (= 200 (probe (jwt/create-token user))))
    (is (= 200 (probe (token-issued-secs-ago user (* 5 24 60 60))))
        (str "with tokens_valid_from NULL even a five-day-old token is fine —"
             " NULL means never revoked, not \"reject everything\""))))

(deftest token-without-iat-test
  (let [user (tu/gen-regular-user :db-component (test-db))
        token (token-without-iat user)]
    (testing "allowed while the account has never been revoked"
      (is (= 200 (probe token))
          (str "rejecting :iat-less tokens outright would log out every signed-in"
               " user the moment this deploys")))
    (testing "rejected once the account is revoked"
      (revocation/revoke! (test-db) user)
      (is (= 401 (probe token))
          (str "with a revocation point set, a token that cannot prove when it"
               " was issued must not be given the benefit of the doubt")))))

(deftest revocation-is-per-user-test
  (let [user-a (tu/gen-regular-user :db-component (test-db))
        user-b (tu/gen-regular-user :db-component (test-db))
        token-a (token-issued-secs-ago user-a 60)
        token-b (token-issued-secs-ago user-b 60)]
    (revocation/revoke! (test-db) user-a)
    (is (= 401 (probe token-a)))
    (is (= 200 (probe token-b))
        "revoking one account must not touch anybody else's session")
    (is (nil? (tokens-valid-from user-b))
        "and must not touch anybody else's row")))

(deftest revocation-does-not-block-password-login-test
  ;; An anti-lockout control. `mw/auth` also guards /actions/login, where the
  ;; identity comes from basic auth and carries no :iat — if the check did not
  ;; distinguish the two, revoking an account (which every role change now does)
  ;; would permanently lock its owner out of logging in with their password.
  (let [user (tu/gen-regular-user :db-component (test-db))
        login #(test-app (-> (mock/request :post "/api/actions/login")
                             (mock/content-type "application/json")
                             (tu/auth-header (:username user) (:password user))))]
    (is (= 200 (:status (login))) "control: login works to begin with")
    (revocation/revoke! (test-db) user)
    (let [resp (login)]
      (is (= 200 (:status resp))
          "a revoked account must still be able to log in with its password")
      (is (= 200 (probe (:token (tu/<-json (:body resp)))))
          "and the token that login hands back must work"))))

;;; The paths that revoke ;;;

(deftest archiving-invalidates-existing-tokens-test
  (let [user (tu/gen-regular-user :db-component (test-db))
        token (token-issued-secs-ago user 60)]
    (is (= 200 (probe token)) "control: active account, working token")
    (core/update-user-status! (test-db) {:id (:id user) :status "archived"})
    (is (= 401 (probe token))
        (str "archiving an account must end its sessions, not merely stop it"
             " renewing them — this is the 6h window in finding M7"))
    (is (some? (tokens-valid-from user)))))

(deftest permissions-change-invalidates-existing-tokens-test
  ;; The stale-roles case that motivated the finding: roles live in the token
  ;; payload, so stripping someone's rights used to change nothing for hours.
  (let [user (tu/gen-city-manager-user 91 :db-component (test-db))
        token (token-issued-secs-ago user 60)
        emailer (capturing-emailer)]
    (is (= 200 (probe token)) "control: the token works before the change")
    (core/update-user-permissions! (test-db) emailer
                                   {:id (str (:id user))
                                    :permissions {:roles []}
                                    :login-url "https://liikuntapaikat.lipas.fi/kirjaudu"})
    (is (= 401 (probe token))
        "a token carrying the old roles must stop being accepted")
    (testing "the magic link in the permissions-updated email still works"
      ;; This is the lockout trap in this path: the mail exists to get the user
      ;; back in with their new rights, and its token is minted in the same
      ;; second as the revocation.
      (let [mailed (first (sent-tokens emailer))]
        (is (some? mailed) "the permissions-updated mail must carry a link")
        (is (= 200 (probe mailed))
            (str "the link the user is told to click must not be dead on"
                 " arrival — it is minted after the revocation point"))))))

(deftest gdpr-removal-invalidates-existing-tokens-test
  ;; GDPR removal rewrites :email and :username, both of which are baked into the
  ;; token payload, so tokens carrying the removed identity must stop working.
  (let [user (tu/gen-regular-user :db-component (test-db))
        token (token-issued-secs-ago user 60)]
    (is (= 200 (probe token)) "control")
    (core/gdpr-remove-user! (test-db) user)
    (is (= 401 (probe token)))))

;;; Password reset: the ordering trap ;;;

(deftest password-reset-completes-end-to-end-test
  ;; `reset-password!` revokes, and the caller is holding a token for the very
  ;; account being revoked. If it revoked too eagerly — before the password write,
  ;; or in a way the same request re-checked — the user would be bounced out of
  ;; their own password reset. The flow must complete and the new password must
  ;; work, all the way from the emailed link.
  (let [user (tu/gen-regular-user :db-component (test-db))
        emailer (capturing-emailer)
        _ (core/send-password-reset-link!
            (test-db) emailer
            {:email (:email user)
             :reset-url "https://liikuntapaikat.lipas.fi/passu-hukassa"})
        reset-token (first (sent-tokens emailer))
        new-password "lipas-test-new-password-2!"
        login (fn [password]
                (:status (test-app (-> (mock/request :post "/api/actions/login")
                                       (mock/content-type "application/json")
                                       (tu/auth-header (:username user) password)))))]
    (is (some? reset-token) "the reset mail must carry a link")
    (is (= 200 (:status (test-app (-> (mock/request :post "/api/actions/reset-password")
                                      (mock/content-type "application/json")
                                      (mock/body (tu/->json {:password new-password}))
                                      (tu/token-header reset-token)))))
        "the reset must complete with the very token it was started with")
    (is (= 200 (login new-password))
        "the reset must actually have changed the password")
    (is (= 401 (login tu/test-user-password))
        "and the old password must be gone")))

(deftest password-reset-link-cannot-be-replayed-test
  ;; Replay protection is a side effect of reset-password! revoking, and it is
  ;; NOT unconditional: the revocation point is truncated to whole seconds (see
  ;; lipas.backend.token-revocation on why — the alternative locks people out of
  ;; their own reset), so a link minted in the SAME second as the reset survives.
  ;;
  ;; That is why this test backdates the link instead of using a freshly minted
  ;; one. Any real reset link is at least seconds old by the time it is clicked —
  ;; it travels through email — so backdating is the realistic case, not a
  ;; contrivance. Asserting it on a same-second token made this flaky (it failed
  ;; ~4 runs in 5) and, worse, claimed a guarantee the design does not make.
  (let [user (tu/gen-regular-user :db-component (test-db))
        reset-token (token-issued-secs-ago user 60)
        new-password "lipas-test-replay-password-3!"]
    (is (= 200 (:status (test-app (-> (mock/request :post "/api/actions/reset-password")
                                      (mock/content-type "application/json")
                                      (mock/body (tu/->json {:password new-password}))
                                      (tu/token-header reset-token)))))
        "control: the link works the first time")
    (is (= 401 (probe reset-token))
        "the same link must not work again once the reset has been completed")))

;;; Link lifetimes ;;;

(deftest password-reset-link-is-valid-for-24h-test
  (let [user (tu/gen-regular-user :db-component (test-db))
        emailer (capturing-emailer)]
    (core/send-password-reset-link!
      (test-db) emailer
      {:email (:email user)
       :reset-url "https://liikuntapaikat.lipas.fi/passu-hukassa"})
    (is (= (* 24 60 60) (token-ttl-seconds (first (sent-tokens emailer))))
        (str "a reset link is a full login token sitting in an inbox; 7 days of"
             " exposure bought nothing"))))

(deftest magic-login-link-is-still-valid-for-7-days-test
  ;; The other three callers of create-magic-link keep the long span
  ;; deliberately — an org invitation may sit unread over a holiday.
  (let [user (tu/gen-regular-user :db-component (test-db))
        emailer (capturing-emailer)]
    (core/send-magic-link! (test-db) emailer
                           {:user user
                            :variant :lipas
                            :login-url "https://liikuntapaikat.lipas.fi/kirjaudu"})
    (is (= (* 7 24 60 60) (token-ttl-seconds (first (sent-tokens emailer))))
        "magic login must not have been shortened along with password reset")))

(deftest create-magic-link-default-ttl-is-7-days-test
  ;; The permissions-updated mail and the org invitation both call
  ;; create-magic-link with no TTL argument, so the default is what they get.
  (let [user (tu/gen-regular-user :db-component (test-db))
        {:keys [link valid-days]} (core/create-magic-link
                                    "https://liikuntapaikat.lipas.fi/kirjaudu" user)]
    (is (= 7 valid-days) "the figure rendered in the mail copy")
    (is (= (* 7 24 60 60)
           (token-ttl-seconds (second (str/split link #"\?token=")))))))

;;; :iat itself ;;;

(deftest every-token-carries-an-iat-test
  ;; Everything above rests on this. Both token shapes, since the terse one
  ;; (magic links) selects a different field set.
  (let [user (tu/gen-regular-user :db-component (test-db))]
    (doseq [[label token] [["full" (jwt/create-token user)]
                           ["terse" (jwt/create-token user :terse? true)]]]
      (testing label
        (let [{:keys [iat exp]} (claims token)]
          (is (int? iat) ":iat must be an epoch-seconds integer")
          (is (int? exp) ":exp must be an epoch-seconds integer")
          (is (< (abs (- iat (quot (System/currentTimeMillis) 1000))) 60)
              ":iat must be the actual issue time"))))))

(comment
  (clojure.test/run-tests *ns*))
