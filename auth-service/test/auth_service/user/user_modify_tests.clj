(ns auth-service.user.user-modify-tests
  (:require [clojure.test                           :refer :all]
            [auth-service.handler :refer [app]]
            [auth-service.test-utils :as helper]
            [auth-service.query-defs :as query]
            [taoensso.timbre                           :as timbre]
            [mount.core                                :as mount]
            [buddy.hashers                             :as hashers]
            [cheshire.core                             :as ch]
            [ring.mock.request                         :as mock]))

(use-fixtures :once (fn [f]
                      (timbre/merge-config! {:level :warn})
                      (mount/start)
                      (f)))

(use-fixtures :each (fn [f]
                      (try
                        (query/insert-permission! {:permission "basic"})
                        (query/insert-permission! {:permission "admin"})
                        (helper/add-users)
                        (f)
                        (finally (query/truncate-all-tables-in-database!)))))

(deftest can-modify-a-users-username-with-valid-token-and-admin-permissions
  (testing "Can modify a users username with valid token and admin permissions"
    (helper/add-permission-for-username "JarrodCTaylor" "admin")
    (let [response   (app (-> (mock/request :patch (str "/api/v1/user/" (helper/get-id-for-user "JarrodCTaylor")))
                              (mock/content-type "application/json")
                              (mock/body (ch/generate-string {:username "Newman"}))
                              (helper/get-token-auth-header-for-user "JarrodCTaylor:pass")))
          body       (helper/parse-body (:body response))]
      (is (= 200         (:status response)))
      (is (= "Newman"    (:username body)))
      (is (= "j@man.com" (:email body))))))

(deftest can-modify-a-users-email-with-valid-token-and-admin-permissions
  (testing "Can modify a users email with valid token and admin permissions"
    (helper/add-permission-for-username "JarrodCTaylor" "admin")
    (let [user-id      (:id (query/get-registered-user-by-username {:username "JarrodCTaylor"}))
          response     (app (-> (mock/request :patch (str "/api/v1/user/" user-id))
                                (mock/content-type "application/json")
                                (mock/body (ch/generate-string {:email "new@email.com"}))
                                (helper/get-token-auth-header-for-user "JarrodCTaylor:pass")))
          body         (helper/parse-body (:body response))
          updated-user (query/get-registered-user-by-id {:id user-id})]
      (is (= 200             (:status response)))
      (is (= "JarrodCTaylor" (:username body)))
      (is (= "new@email.com" (:email body)))
      (is (= "new@email.com" (str (:email updated-user)))))))

(deftest can-modify-a-users-password-with-valid-token-and-admin-permissions
  (testing "Can modify a users password with valid token and admin permissions"
    (helper/add-permission-for-username "JarrodCTaylor" "admin")
    (let [user-id      (:id (query/get-registered-user-by-username {:username "JarrodCTaylor"}))
          response     (app (-> (mock/request :patch (str "/api/v1/user/" user-id))
                                (mock/content-type "application/json")
                                (mock/body (ch/generate-string {:password "newPass"}))
                                (helper/get-token-auth-header-for-user "JarrodCTaylor:pass")))
          body         (helper/parse-body (:body response))
          updated-user (query/get-registered-user-by-id {:id user-id})]
      (is (= 200  (:status response)))
      (is (= true (hashers/check "newPass" (:password updated-user)))))))

(deftest can-modify-your-own-password-with-valid-token-and-no-admin-permissions
  (testing "Can modify your own password with valid token and no admin permissions"
    (let [user-id      (:id (query/get-registered-user-by-username {:username "JarrodCTaylor"}))
          response     (app (-> (mock/request :patch (str "/api/v1/user/" user-id))
                                (mock/content-type "application/json")
                                (mock/body (ch/generate-string {:password "newPass"}))
                                (helper/get-token-auth-header-for-user "JarrodCTaylor:pass")))
          body         (helper/parse-body (:body response))
          updated-user (query/get-registered-user-by-id {:id user-id})]
      (is (= 200  (:status response)))
      (is (= true (hashers/check "newPass" (:password updated-user)))))))

(deftest can-not-modify-a-user-with-valid-token-and-no-admin-permissions
  (testing "Can not modify a user with valid token and no admin permissions"
    (let [user-id          (:id (query/get-registered-user-by-username {:username "Everyman"}))
          response         (app (-> (mock/request :patch (str "/api/v1/user/" user-id))
                                    (mock/content-type "application/json")
                                    (mock/body (ch/generate-string {:email "bad@mail.com"}))
                                    (helper/get-token-auth-header-for-user "JarrodCTaylor:pass")))
          body             (helper/parse-body (:body response))
          non-updated-user (query/get-registered-user-by-id {:id user-id})]
      (is (= 401              (:status response)))
      (is (= "e@man.com"      (str (:email non-updated-user))))
      (is (= "Not authorized" (:error body))))))

(deftest trying-to-modify-a-user-that-does-not-exist-return-a-404
  (testing "Trying to modify a user that does not exist returns a 404"
    (helper/add-permission-for-username "JarrodCTaylor" "admin")
    (let [response   (app (-> (mock/request :patch "/api/v1/user/83b811-edf0-48ec-84-5a142e2c3a75")
                              (mock/content-type "application/json")
                              (mock/body (ch/generate-string {:email "not@real.com"}))
                              (helper/get-token-auth-header-for-user "JarrodCTaylor:pass")))
          body       (helper/parse-body (:body response))]
      (is (= 404                     (:status response)))
      (is (= "Userid does not exist" (:error body))))))
