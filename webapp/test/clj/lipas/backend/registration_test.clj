(ns lipas.backend.registration-test
  "Self-registration with email verification, and the email-verification
  bookkeeping on the account (see the 20260911120000 account migration)."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [lipas.backend.core :as core]
            [lipas.backend.email :as email]
            [lipas.backend.jwt :as jwt]
            [lipas.backend.rate-limit :as rl]
            [lipas.test-utils :as tu :refer [->json <-json]]
            [ring.mock.request :as mock]))

(defonce test-system (atom nil))

(let [{:keys [once each]} (tu/full-system-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn test-db [] (:lipas/db @test-system))
(defn test-app [req] ((:lipas/app @test-system) req))

(def register-url "https://localhost/rekisteroidy")

(defn- request
  "Runs `req` against the app with a fresh rate limiter — both registration
  endpoints allow 5/h per IP and every mock request comes from the same one."
  [req]
  (rl/reset-all!)
  (let [resp (test-app req)]
    (update resp :body #(some-> % <-json))))

(defn- post [path body]
  (request (-> (mock/request :post (str "/api" path))
               (mock/content-type "application/json")
               (mock/body (->json body)))))

(defn- unique-email [] (str "reg-" (random-uuid) "@example.com"))

(defn- form
  "A valid step-2 payload for `token`; `overrides` are merged on top."
  [token & {:as overrides}]
  (merge {:token     token
          :username  (str "user" (rand-int 1000000000))
          :password  "salasana123"
          :user-data {:firstname "Maija" :lastname "Meikäläinen"}}
         overrides))

(defn- account [email] (core/get-user (test-db) email))

(defn- login [username password]
  (request (-> (mock/request :post "/api/actions/login")
               (tu/auth-header username password))))

(defn- refresh-login [token]
  (request (-> (mock/request :get "/api/actions/refresh-login")
               (tu/token-header token))))

;;; Step 1: request a link ;;;

(deftest request-registration-sends-link-test
  (let [emailer (tu/create-test-emailer)
        email   (unique-email)]
    (core/request-registration! (test-db) emailer {:email email :register-url register-url :lang "en"})
    (let [[msg :as sent] @(:sent-emails emailer)
          token (second (re-find #"\?token=([A-Za-z0-9._-]+)" (:plain msg)))]
      (is (= 1 (count sent)))
      (is (= email (:to msg)))
      (is (= "Complete your LIPAS registration" (:subject msg)))
      (is (str/includes? (:plain msg) (str register-url "?token=")))
      (is (str/includes? (:html msg) (str "<a href=\"" register-url "?token=")))
      (testing "the link's token proves the address"
        (is (= email (jwt/unsign-email-verification-token token))))
      (testing "no account row exists until the link is used"
        (is (nil? (account email)))))))

(deftest request-registration-for-existing-account-test
  (let [emailer (tu/create-test-emailer)
        user    (tu/gen-regular-user :db-component (test-db))]
    (core/request-registration! (test-db) emailer {:email (:email user) :register-url register-url})
    (let [[msg :as sent] @(:sent-emails emailer)]
      (is (= 1 (count sent)))
      (is (= (:email user) (:to msg)))
      (testing "defaults to Finnish"
        (is (= "LIPAS-rekisteröityminen" (:subject msg))))
      (testing "points at login and password reset instead of minting a token"
        (is (not (str/includes? (:plain msg) "token=")))
        (is (str/includes? (:plain msg) "https://localhost/kirjaudu"))
        (is (str/includes? (:plain msg) "https://localhost/passu-hukassa"))))))

(deftest request-registration-endpoint-test
  (let [user (tu/gen-regular-user :db-component (test-db))]
    (testing "same answer whether or not the address has an account"
      (let [new-resp      (post "/actions/request-registration" {:email (unique-email) :register-url register-url})
            existing-resp (post "/actions/request-registration" {:email (:email user) :register-url register-url})]
        (is (= 200 (:status new-resp) (:status existing-resp)))
        (is (= (:body new-resp) (:body existing-resp)))))
    (testing "the link must point at a LIPAS host"
      (is (= 400 (:status (post "/actions/request-registration"
                                {:email (unique-email) :register-url "https://evil.example/rekisteroidy"})))))
    (testing "the address must be an email"
      (is (= 400 (:status (post "/actions/request-registration"
                                {:email "not-an-email" :register-url register-url})))))
    (testing "only supported languages"
      (is (= 400 (:status (post "/actions/request-registration"
                                {:email (unique-email) :register-url register-url :lang "de"})))))))

;;; Step 2: complete the registration ;;;

(deftest register-creates-verified-account-test
  (let [email (unique-email)
        resp  (post "/actions/register"
                    (form (jwt/create-email-verification-token email) :username "maija.m"))]
    (is (= 201 (:status resp)))
    (let [u (account email)]
      (is (= "registration" (:email-verified-via u)))
      (is (some? (:email-verified-at u)))
      (is (= "active" (:status u)))
      (is (= [] (-> u :permissions :roles)))
      (is (= "maija.m" (:username u))))
    (testing "the password chosen in the form works"
      (is (= 200 (:status (login "maija.m" "salasana123")))))))

(deftest register-ignores-client-supplied-account-fields-test
  (let [email (unique-email)
        resp  (post "/actions/register"
                    (form (jwt/create-email-verification-token email)
                          :email "attacker@example.com"
                          :permissions {:roles [{:role "admin"}]}
                          :status "archived"
                          :email-verified-via "legacy"))]
    (is (= 201 (:status resp)))
    (testing "the email comes from the token"
      (is (nil? (account "attacker@example.com"))))
    (let [u (account email)]
      (is (= [] (-> u :permissions :roles)))
      (is (= "active" (:status u)))
      (is (= "registration" (:email-verified-via u))))))

(deftest register-token-test
  (let [email (unique-email)]
    (testing "garbage"
      (let [resp (post "/actions/register" (form "not-a-token"))]
        (is (= 400 (:status resp)))
        (is (= "invalid-registration-token" (-> resp :body :type)))))
    (testing "expired"
      (let [token (with-redefs [jwt/email-verification-valid-seconds -10]
                    (jwt/create-email-verification-token email))]
        (is (= "invalid-registration-token" (-> (post "/actions/register" (form token)) :body :type)))))
    (testing "a login token is not a registration token"
      (let [user (tu/gen-regular-user :db-component (test-db))]
        (is (= "invalid-registration-token"
               (-> (post "/actions/register" (form (jwt/create-token user))) :body :type)))))
    (testing "signed with the login key, even with the right claims"
      (let [token (jwt/sign {:email   email
                             :purpose "email-verification"
                             :exp     (.plusSeconds (java.time.Instant/now) 3600)})]
        (is (= "invalid-registration-token" (-> (post "/actions/register" (form token)) :body :type)))))
    (testing "a registration token is not a login token"
      (is (= 401 (:status (refresh-login (jwt/create-email-verification-token email))))))
    (is (nil? (account email)) "no attempt above created an account")))

(deftest register-conflicts-test
  (let [email (unique-email)
        token (jwt/create-email-verification-token email)]
    (is (= 201 (:status (post "/actions/register" (form token :username "first.user")))))
    (testing "the same link can't create a second account"
      (let [resp (post "/actions/register" (form token))]
        (is (= 409 (:status resp)))
        (is (= "email-conflict" (-> resp :body :type)))))
    (testing "usernames are unique"
      (let [resp (post "/actions/register"
                       (form (jwt/create-email-verification-token (unique-email)) :username "first.user"))]
        (is (= 409 (:status resp)))
        (is (= "username-conflict" (-> resp :body :type)))))))

(deftest register-validation-test
  (letfn [(attempt [& overrides]
            (let [email (unique-email)
                  resp  (post "/actions/register"
                              (apply form (jwt/create-email-verification-token email) overrides))]
              (assoc resp :account (account email))))]
    (testing "names are trimmed"
      (let [{:keys [status account]} (attempt :user-data {:firstname "  Maija " :lastname "Meikäläinen\t"})]
        (is (= 201 status))
        (is (= {:firstname "Maija" :lastname "Meikäläinen"} (:user-data account)))))
    (testing "blank names"
      (is (= 400 (:status (attempt :user-data {:firstname "   " :lastname "M"})))))
    (testing "control characters in names"
      (is (= 400 (:status (attempt :user-data {:firstname "Mai\u0001ja" :lastname "M"}))))
      (is (= 400 (:status (attempt :user-data {:firstname "Mai\r\nja" :lastname "M"})))))
    (testing "the permissions request may span lines"
      (is (= 201 (:status (attempt :user-data {:firstname "Maija" :lastname "M"
                                               :permissions-request "Rivi 1\nRivi 2"})))))
    (testing "usernames must be usable for login"
      (doseq [username ["maija:m" "maija@example.com" "maija m" ""]]
        (is (= 400 (:status (attempt :username username))) username)))
    (testing "passwords have a minimum length"
      (is (= 400 (:status (attempt :password "12345")))))
    (testing "required fields"
      (is (= 400 (:status (post "/actions/register"
                                {:token (jwt/create-email-verification-token (unique-email))})))))))

(deftest register-notifies-ops-test
  (let [emailer (tu/create-test-emailer)
        email   (unique-email)]
    (core/register! (test-db) emailer
                    {:token     (jwt/create-email-verification-token email)
                     :username  "html.tester"
                     :password  "salasana123"
                     :user-data {:firstname           "<b>Maija</b>"
                                 :lastname            "M"
                                 :permissions-request "<script>alert(1)</script>"}})
    (let [[msg :as sent] @(:sent-emails emailer)]
      (is (= 1 (count sent)))
      (is (= "lipasinfo@jyu.fi" (:to msg)))
      (is (str/includes? (:plain msg) email))
      (testing "user-typed values are escaped in the HTML part"
        (is (not (str/includes? (:html msg) "<script>")))
        (is (not (str/includes? (:html msg) "<b>Maija")))
        (is (str/includes? (:html msg) "&lt;script&gt;alert(1)&lt;/script&gt;")))
      (testing "and left alone in the plain-text part"
        (is (str/includes? (:plain msg) "<script>alert(1)</script>")))
      (testing "the password never leaves"
        (is (not (str/includes? (str msg) "salasana123")))))))

;;; Verification of accounts created FOR an address ;;;

(deftest first-login-verifies-account-test
  (testing "password login"
    ;; gen-user writes the row without verification, like an org invite
    (let [user (tu/gen-regular-user :db-component (test-db))]
      (is (nil? (:email-verified-via (account (:email user)))))
      (is (= 200 (:status (login (:username user) (:password user)))))
      (is (= "login" (:email-verified-via (account (:email user)))))
      (is (some? (:email-verified-at (account (:email user)))))))
  (testing "magic link (lands on refresh-login)"
    (let [user (tu/gen-regular-user :db-component (test-db))]
      (is (= 200 (:status (refresh-login (jwt/create-token user :terse? true)))))
      (is (= "login" (:email-verified-via (account (:email user)))))))
  (testing "the first proof wins"
    (let [email (unique-email)]
      (post "/actions/register" (form (jwt/create-email-verification-token email)
                                      :username "proof.wins"))
      (is (= 200 (:status (login "proof.wins" "salasana123"))))
      (is (= "registration" (:email-verified-via (account email)))))))

(deftest impersonation-does-not-verify-test
  (let [admin  (tu/gen-admin-user :db-component (test-db))
        target (tu/gen-regular-user :db-component (test-db))
        resp   (request (-> (mock/request :post "/api/actions/impersonate")
                            (mock/content-type "application/json")
                            (mock/body (->json {:id (str (:id target))}))
                            (tu/token-header (jwt/create-token admin))))
        token  (-> resp :body :token)]
    (is (= 200 (:status resp)))
    (is (string? token))
    (is (= 200 (:status (refresh-login token))))
    (is (nil? (:email-verified-via (account (:email target))))
        "the admin logged in, not the owner of the address")))

(deftest admin-magic-link-ignores-client-password-test
  (let [admin (tu/gen-admin-user :db-component (test-db))
        email (unique-email)
        resp  (request (-> (mock/request :post "/api/actions/send-magic-link")
                           (mock/content-type "application/json")
                           (mock/body (->json {:login-url "https://localhost/kirjaudu"
                                               :variant   "lipas"
                                               :user      {:email     email
                                                           :username  "invitee.one"
                                                           :password  "chosen123"
                                                           :user-data {:firstname "I" :lastname "O"}}}))
                           (tu/token-header (jwt/create-token admin))))]
    (is (= 200 (:status resp)))
    (is (some? (account email)))
    (is (= 401 (:status (login "invitee.one" "chosen123")))
        "the account is reachable only through the emailed link")
    (is (nil? (:email-verified-via (account email))))))

;;; Email escaping ;;;

(deftest render-copy-test
  (let [{:keys [subject plain html]}
        (email/render-copy "S" "Hi <b>{{name}}</b>\n\nOpen {{link}}\nnow"
                           {:name "<i>x</i>" :link "https://lipas.fi/a?b=1&c=\"2\""}
                           #{:link})]
    (is (= "S" subject))
    (is (= "Hi <b><i>x</i></b>\n\nOpen https://lipas.fi/a?b=1&c=\"2\"\nnow" plain))
    (is (str/includes? html "<p>Hi &lt;b&gt;&lt;i&gt;x&lt;/i&gt;&lt;/b&gt;</p>"))
    (is (str/includes? html (str "<a href=\"https://lipas.fi/a?b=1&amp;c=&quot;2&quot;\">"
                                 "https://lipas.fi/a?b=1&amp;c=&quot;2&quot;</a><br>now")))))

(deftest user-text-is-escaped-in-html-mail-test
  (let [evil "<img src=x onerror=alert(1)>"]
    (testing "feedback"
      (let [e (tu/create-test-emailer)]
        (email/send-feedback-email! e "ops@example.com" {:lipas.feedback/text evil})
        (is (not (str/includes? (:html (first @(:sent-emails e))) "<img")))))
    (testing "reminder"
      (let [e (tu/create-test-emailer)]
        (email/send-reminder-email! e "u@example.com" {:link "https://lipas.fi/x" :valid-days 7}
                                    {:message evil})
        (is (not (str/includes? (:html (first @(:sent-emails e))) "<img")))))
    (testing "org name"
      (let [e (tu/create-test-emailer)]
        (email/send-org-invitation-email! e "u@example.com"
                                          {:org-name evil :link "https://lipas.fi/x" :valid-days 7})
        (let [{:keys [html plain]} (first @(:sent-emails e))]
          (is (not (str/includes? html "<img")))
          (is (str/includes? plain evil)))))))
