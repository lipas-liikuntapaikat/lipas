(ns auth-service.password.request-password-reset-tests
    (:require [clojure.test                  :refer :all]
            [auth-service.handler :refer :all]
            [auth-service.test-utils                                      :as helper]
            [auth-service.query-defs                                      :as query]
            [auth-service.route-functions.password.request-password-reset :as unit-test]
            [ring.mock.request                                                              :as mock]
            [taoensso.timbre                                                                :as timbre]
            [mount.core                                                                     :as mount]
            [cheshire.core                                                                  :as ch]
            [clj-time.core                                                                  :as t]
            [clj-time.coerce                                                                :as c]))

(defn gen-reset-json [email]
  (ch/generate-string {:userEmail        email
                       :fromEmail        "admin@something.com"
                       :subject          "Password reset"
                       :emailBodyPlain   "Here is your link.\nThanks,"
                       :responseBaseLink "http://something/reset"}))

(use-fixtures :once (fn [f]
                      (try
                        (timbre/merge-config! {:level :warn})
                        (mount/start)
                        (query/insert-permission! {:permission "basic"})
                        (helper/add-users)
                        (f)
                        (finally (query/truncate-all-tables-in-database!)))))

(deftest test-html-email-body-returns-desired-string
  (testing "test add response link to html body returns desired string"
    (let [body           "<html><body><p>Hello There</p></body></html>"
          response-link  "http://somesite/reset/234"
          body-with-link (unit-test/html-email-body body response-link)]
      (is (= "<html><body><p>Hello There</p><br><p>http://somesite/reset/234</p></body></html>" body-with-link)))))

(deftest test-plain-email-body-returns-desired-string
  (testing "Test add response link to plain body reutrns desired string"
    (let [body           "Hello there"
          response-link  "http://somesite/reset/123"
          body-with-link (unit-test/plain-email-body body response-link)]
      (is (= "Hello there\n\nhttp://somesite/reset/123" body-with-link)))))

(deftest test-requesting-password-reset

  (testing "Successfully request password reset with email for a valid registered user"
    (with-redefs [unit-test/send-reset-email (fn [to-email from-email subject html-body plain-body] nil)]
      (let [user-id          (:id (query/get-registered-user-by-username {:username "JarrodCTaylor"}))
            reset-info-json  (gen-reset-json "j@man.com")
            response         (app (-> (mock/request :post "/api/v1/password/reset-request")
                                      (mock/content-type "application/json")
                                      (mock/body reset-info-json)))
            body             (helper/parse-body (:body response))
            pass-reset-row   (query/get-password-reset-keys-for-userid {:userid user-id})
            pass-reset-key   (:reset_key (first pass-reset-row))
            valid-until-ts   (:valid_until (first pass-reset-row))
            ; shave off the last four digits so we can compare
            valid-until-str  (subs (str (c/to-long (c/from-sql-time valid-until-ts))) 0 8)
            one-day-from-now (subs (str (c/to-long (t/plus (t/now) (t/hours 24)))) 0 8)]
        (is (= 200                                                         (:status response)))
        (is (= 1                                                           (count pass-reset-row)))
        (is (= valid-until-str                                             one-day-from-now))
        (is (= "Reset email successfully sent to j@man.com" (:message body))))))

  (testing "Invalid user email returns 404 when requesting password reset"
    (let [reset-info-json (gen-reset-json "J@jrock.com")
          response        (app (-> (mock/request :post "/api/v1/password/reset-request")
                                   (mock/content-type "application/json")
                                   (mock/body reset-info-json)))
          body            (helper/parse-body (:body response))]
      (is (= 404                                         (:status response)))
      (is (= "No user exists with the email J@jrock.com" (:error body))))))
