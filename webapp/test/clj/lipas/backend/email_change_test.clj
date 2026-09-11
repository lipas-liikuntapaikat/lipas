(ns lipas.backend.email-change-test
  "Email change, self-service and admin-initiated, confirmed by the new
  address. See docs/wip/email-change.md."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [lipas.backend.core :as core]
            [lipas.backend.jwt :as jwt]
            [lipas.backend.rate-limit :as rl]
            [lipas.test-utils :as tu :refer [->json <-json]]
            [next.jdbc :as next-jdbc]
            [ring.mock.request :as mock]))

(defonce test-system (atom nil))

(let [{:keys [once each]} (tu/full-system-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn test-db [] (:lipas/db @test-system))
(defn test-app [req] ((:lipas/app @test-system) req))

(def confirm-url "https://localhost/vahvista-sahkoposti")

(defn- request
  "Runs `req` with a fresh rate limiter (every mock request shares one IP)."
  [req]
  (rl/reset-all!)
  (update (test-app req) :body #(some-> % <-json)))

(defn- post
  ([path body] (post path body nil))
  ([path body token]
   (request (cond-> (-> (mock/request :post (str "/api" path))
                        (mock/content-type "application/json")
                        (mock/body (->json body)))
              token (tu/token-header token)))))

(defn- refresh-login [token]
  (request (-> (mock/request :get "/api/actions/refresh-login")
               (tu/token-header token))))

(defn- unique-email [] (str "chg-" (random-uuid) "@example.com"))
(defn- account [identifier] (core/get-user (test-db) (str identifier)))
(defn- events [user] (->> (account (:id user)) :history :events (map :event) set))

(defn- link-token
  "The token in the confirmation link of `msg`."
  [msg]
  (second (re-find #"\?token=([A-Za-z0-9._-]+)" (:plain msg))))

(defn- request-change!
  "Runs core/request-email-change! with a capturing emailer; returns the mails."
  [user new-email & {:as opts}]
  (let [emailer (tu/create-test-emailer)]
    (core/request-email-change! (test-db) emailer
                                (merge {:user user :new-email new-email :confirm-url confirm-url
                                        :lang "en" :actor user :admin? false}
                                       opts))
    @(:sent-emails emailer)))

(defn- confirm! [token]
  (let [emailer (tu/create-test-emailer)]
    (core/confirm-email-change! (test-db) emailer token)
    @(:sent-emails emailer)))

;;; Step 1 ;;;

(deftest request-sends-link-to-new-address-test
  (let [user      (tu/gen-regular-user :db-component (test-db))
        new-email (unique-email)
        [msg :as sent] (request-change! user new-email)
        claims    (jwt/unsign-email-change-token (link-token msg))]
    (is (= 1 (count sent)))
    (is (= new-email (:to msg)))
    (is (= "Confirm your new email address" (:subject msg)))
    (is (str/includes? (:plain msg) (str confirm-url "?token=")))
    (is (= {:account-id (str (:id user)) :old-email (:email user) :new-email new-email
            :by (str (:id user)) :lang "en"}
           (select-keys claims [:account-id :old-email :new-email :by :lang])))
    (testing "nothing changes before the link is opened"
      (is (= (:email user) (:email (account (:id user))))))
    (testing "audited without addresses"
      (is (contains? (events user) "email-change-requested"))
      (is (not (str/includes? (str (:history (account (:id user)))) new-email))))))

(deftest request-to-taken-address-test
  (let [user  (tu/gen-regular-user :db-component (test-db))
        other (tu/gen-regular-user :db-component (test-db))]
    (testing "self-service: the owner of the address gets a note, no link"
      (let [[msg :as sent] (request-change! user (str/upper-case (:email other)))]
        (is (= 1 (count sent)))
        (is (= "LIPAS: email address change" (:subject msg)))
        (is (nil? (link-token msg)))))
    (testing "an address used as someone's username counts as taken"
      (let [invitee-email (unique-email)
            _             (core/add-user! (test-db) {:email (unique-email) :username invitee-email})
            [msg]         (request-change! user invitee-email)]
        (is (nil? (link-token msg)))))
    (testing "admin: reported as a conflict"
      (let [ex (try (request-change! user (:email other) :admin? true) nil
                    (catch clojure.lang.ExceptionInfo e (ex-data e)))]
        (is (= :email-conflict (:type ex)))))
    (testing "same address, any case"
      (let [ex (try (request-change! user (str/upper-case (:email user))) nil
                    (catch clojure.lang.ExceptionInfo e (ex-data e)))]
        (is (= :same-email (:type ex)))))))

(deftest request-endpoints-test
  (let [user  (tu/gen-regular-user :db-component (test-db))
        admin (tu/gen-admin-user :db-component (test-db))
        other (tu/gen-regular-user :db-component (test-db))
        body  {:new-email (unique-email) :confirm-url confirm-url}]
    (testing "self-service"
      (is (= 200 (:status (post "/actions/request-email-change" body (jwt/create-token user)))))
      (is (= 401 (:status (post "/actions/request-email-change" body))))
      (testing "same answer for a taken address"
        (is (= 200 (:status (post "/actions/request-email-change"
                                  (assoc body :new-email (:email other))
                                  (jwt/create-token user)))))))
    (testing "validation"
      (is (= 400 (:status (post "/actions/request-email-change"
                                (assoc body :confirm-url "https://evil.example/x")
                                (jwt/create-token user)))))
      (is (= 400 (:status (post "/actions/request-email-change"
                                (assoc body :new-email "not-an-email")
                                (jwt/create-token user)))))
      (is (= "same-email" (-> (post "/actions/request-email-change"
                                    (assoc body :new-email (:email user))
                                    (jwt/create-token user))
                              :body :type))))
    (testing "admin endpoint"
      (let [admin-body (assoc body :id (str (:id user)))]
        (is (= 200 (:status (post "/actions/request-email-change-for-user" admin-body
                                  (jwt/create-token admin)))))
        (is (= 403 (:status (post "/actions/request-email-change-for-user" admin-body
                                  (jwt/create-token other)))))
        (is (= "email-conflict"
               (-> (post "/actions/request-email-change-for-user"
                         (assoc admin-body :new-email (:email other))
                         (jwt/create-token admin))
                   :body :type)))))
    (testing "nothing changed"
      (is (= (:email user) (:email (account (:id user))))))))

;;; Step 2 ;;;

(deftest confirm-changes-email-test
  (let [user      (tu/gen-regular-user :db-component (test-db))
        ;; Backdated: revocation compares epoch SECONDS and deliberately lets a
        ;; token issued in the revoking second through (see
        ;; lipas.backend.token-revocation-test), so a freshly minted token
        ;; would survive this fast test.
        now       (.getEpochSecond (java.time.Instant/now))
        session   (jwt/sign (-> (select-keys user [:id :email :username :permissions])
                                (assoc :iat (- now 10) :exp (+ now 3600))))
        new-email (unique-email)
        token     (link-token (first (request-change! user new-email)))
        [notice :as sent] (confirm! token)
        changed   (account (:id user))]
    (is (= new-email (:email changed)))
    (is (= "change" (:email-verified-via changed)))
    (is (some? (:email-verified-at changed)))
    (testing "a username that isn't the old address stays"
      (is (= (:username user) (:username changed))))
    (testing "every existing session and link is revoked"
      (is (= 401 (:status (refresh-login session)))))
    (testing "the old address is told, with the new one masked"
      (is (= 1 (count sent)))
      (is (= (:email user) (:to notice)))
      (is (str/includes? (:plain notice) (core/mask-email new-email)))
      (is (not (str/includes? (str notice) new-email))))
    (testing "logging in with the new address works"
      (is (= 200 (:status (request (-> (mock/request :post "/api/actions/login")
                                       (tu/auth-header new-email (:password user))))))))
    (testing "audited without addresses"
      (is (contains? (events user) "email-changed"))
      (is (not (str/includes? (str (:history changed)) new-email))))))

(deftest confirm-moves-email-shaped-username-test
  ;; Org-invite accounts have username = email.
  (let [old-email (unique-email)
        _         (core/add-user! (test-db) {:email old-email :username old-email})
        user      (account old-email)
        new-email (unique-email)]
    (confirm! (link-token (first (request-change! user new-email))))
    (let [changed (account (:id user))]
      (is (= new-email (:email changed) (:username changed))))
    (testing "the old address no longer resolves to the account"
      (is (nil? (account old-email))))))

(deftest confirm-link-is-single-use-and-supersedable-test
  (let [user  (tu/gen-regular-user :db-component (test-db))
        older (link-token (first (request-change! user (unique-email))))
        newer (link-token (first (request-change! user (unique-email))))]
    (confirm! newer)
    (testing "the same link twice"
      (is (= :invalid-email-change-token
             (try (confirm! newer) nil (catch clojure.lang.ExceptionInfo e (:type (ex-data e)))))))
    (testing "an older link after a newer change"
      (is (= :invalid-email-change-token
             (try (confirm! older) nil (catch clojure.lang.ExceptionInfo e (:type (ex-data e)))))))))

(deftest confirm-rejects-address-taken-meanwhile-test
  (let [user      (tu/gen-regular-user :db-component (test-db))
        new-email (unique-email)
        token     (link-token (first (request-change! user new-email)))]
    (core/add-user! (test-db) {:email (str/upper-case new-email) :username (str "u" (rand-int 1e9))})
    (is (= "email-conflict" (-> (post "/actions/confirm-email-change" {:token token}) :body :type)))
    (is (= (:email user) (:email (account (:id user)))))))

(deftest confirm-rejects-archived-account-test
  (let [user  (tu/gen-regular-user :db-component (test-db))
        token (link-token (first (request-change! user (unique-email))))]
    (core/update-user-status! (test-db) (assoc user :status "archived"))
    (is (= "invalid-email-change-token"
           (-> (post "/actions/confirm-email-change" {:token token}) :body :type)))))

(deftest confirm-token-test
  (let [user   (tu/gen-regular-user :db-component (test-db))
        claims {:account-id (str (:id user)) :old-email (:email user)
                :new-email (unique-email) :by (str (:id user)) :lang "fi"}]
    (doseq [[label token] [["garbage" "not-a-token"]
                           ["expired" (with-redefs [jwt/email-change-valid-seconds -10]
                                        (jwt/create-email-change-token claims))]
                           ["a login token" (jwt/create-token user)]
                           ["a registration token" (jwt/create-email-verification-token (:new-email claims))]
                           ["signed with the login key" (jwt/sign (assoc claims :purpose "email-change"
                                                                         :exp (.plusSeconds (java.time.Instant/now) 3600)))]]]
      (testing label
        (let [resp (post "/actions/confirm-email-change" {:token token})]
          (is (= 400 (:status resp)))
          (is (= "invalid-email-change-token" (-> resp :body :type))))))
    (testing "an email-change token is not a login token"
      (is (= 401 (:status (refresh-login (jwt/create-email-change-token claims))))))
    (is (= (:email user) (:email (account (:id user)))) "nothing above changed the account")))

;;; Case-insensitive uniqueness ;;;

(deftest email-unique-ignoring-case-test
  (let [email (unique-email)]
    (core/add-user! (test-db) {:email email :username (str "u" (rand-int 1e9))})
    (testing "the app check"
      (is (= :email-conflict
             (try (core/add-user! (test-db) {:email (str/upper-case email) :username (str "u" (rand-int 1e9))})
                  nil
                  (catch clojure.lang.ExceptionInfo e (:type (ex-data e)))))))
    (testing "the database index, for anything that bypasses it"
      (is (thrown? org.postgresql.util.PSQLException
                   (next-jdbc/execute! (test-db)
                                       ["INSERT INTO account (email, username, password, status, user_data, permissions)
                                         VALUES (?, ?, 'x', 'active', '{}'::jsonb, '{}'::jsonb)"
                                        (str/upper-case email) (str "u" (rand-int 1e9))]))))))

(deftest mask-email-test
  (is (= "m***@example.fi" (core/mask-email "maija.meikalainen@example.fi")))
  (is (= "a***@x.fi" (core/mask-email "a@x.fi"))))
