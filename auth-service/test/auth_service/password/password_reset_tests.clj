(ns auth-service.password.password-reset-tests
  (:require [clojure.test                           :refer :all]
            [auth-service.handler :refer :all]
            [auth-service.test-utils :as helper]
            [auth-service.query-defs :as query]
            [taoensso.timbre                           :as timbre]
            [mount.core                                :as mount]
            [buddy.hashers                             :as hashers]
            [ring.mock.request                         :as mock]
            [cheshire.core                             :as ch]
            [clj-time.core                             :as t]
            [clj-time.coerce                           :as c]))

(use-fixtures :once (fn [f]
                      (try
                        (timbre/merge-config! {:level :warn})
                        (mount/start)
                        (query/insert-permission! {:permission "basic"})
                        (helper/add-users)
                        (f)
                        (finally (query/truncate-all-tables-in-database!)))))

(deftest test-password-resetting

  (testing "Test password is reset with valid resetKey"
    (let [user-id      (:id (query/get-registered-user-by-username {:username "JarrodCTaylor"}))
          _            (query/insert-password-reset-key-with-default-valid-until! {:reset_key "123" :user_id user-id})
          response     (app (-> (mock/request :post "/api/v1/password/reset-confirm")
                                (mock/content-type "application/json")
                                (mock/body (ch/generate-string {:resetKey "123" :newPassword "new-pass"}))))
          body         (helper/parse-body (:body response))
          updated-user (query/get-registered-user-by-id {:id user-id})]
      (is (= 200 (:status response)))
      (is (= true (hashers/check "new-pass" (:password updated-user))))
      (is (= "Password successfully reset" (:message body)))))

  (testing "Not found 404 is returned when invalid reset key is used"
    (let [response (app (-> (mock/request :post "/api/v1/password/reset-confirm")
                            (mock/content-type "application/json")
                            (mock/body (ch/generate-string {:resetKey "321" :newPassword "new-pass"}))))
          body     (helper/parse-body (:body response))]
      (is (= 404 (:status response)))
      (is (= "Reset key does not exist" (:error body)))))

  (testing "Not found 404 is returned when valid reset key has expired"
    (let [user-id      (:id (query/get-registered-user-by-username {:username "JarrodCTaylor"}))
          _            (query/insert-password-reset-key-with-valid-until-date! {:reset_key "456" :user_id user-id :valid_until (c/to-sql-time (t/minus (t/now) (t/hours 24)))})
          response     (app (-> (mock/request :post "/api/v1/password/reset-confirm")
                                (mock/content-type "application/json")
                                (mock/body (ch/generate-string {:resetKey "456" :newPassword "new-pass"}))))
          body         (helper/parse-body (:body response))
          updated-user (query/get-registered-user-by-id {:id user-id})]
      (is (= 404 (:status response)))
      (is (= "Reset key has expired" (:error body)))))

  (testing "Password is not reset if reset key has already been used"
    (let [user-id          (:id (query/get-registered-user-by-username {:username "JarrodCTaylor"}))
          _                (query/insert-password-reset-key-with-default-valid-until! {:reset_key "789" :user_id user-id})
          initial-response (app (-> (mock/request :post "/api/v1/password/reset-confirm")
                                    (mock/content-type "application/json")
                                    (mock/body (ch/generate-string {:resetKey "789" :newPassword "new-pass"}))))
          second-response  (app (-> (mock/request :post "/api/v1/password/reset-confirm")
                                    (mock/content-type "application/json")
                                    (mock/body (ch/generate-string {:resetKey "789" :newPassword "nono"}))))
          body             (helper/parse-body (:body second-response))
          updated-user     (query/get-registered-user-by-id {:id user-id})]
      (is (= 200 (:status initial-response)))
      (is (= 404 (:status second-response)))
      (is (= true (hashers/check "new-pass" (:password updated-user))))
      (is (= "Reset key already used" (:error body))))))
