(ns auth-service.auth.credential-retrieval-tests
  (:require [clojure.test                           :refer :all]
            [environ.core                           :refer [env]]
            [auth-service.handler :refer :all]
            [auth-service.test-utils :as helper]
            [auth-service.query-defs :as query]
            [buddy.sign.jwt                            :as jwt]
            [taoensso.timbre                           :as timbre]
            [mount.core                                :as mount]
            [ring.mock.request                         :as mock]))

(def test-perms {:type-codes [123 321]
                 :city-codes [123 432]})

(use-fixtures :once
  (fn [f]
    (try
      (timbre/merge-config! {:level :warn})
      (mount/start)
      (query/insert-permission! {:permission "basic"})
      (query/insert-permission! {:permission "admin"})
      (query/insert-permission-with-data! {:permission "test"
                                           :permission_data (pr-str test-perms)})
      (helper/add-users)
      (helper/add-permission-for-username "JarrodCTaylor" "admin")
      (helper/add-permission-for-username "Everyman" "test")
      (f)
      (finally (query/truncate-all-tables-in-database!)))))

(deftest credential-retrieval-tests

  (testing "Valid username and password return correct auth credentials"
    (let [response       (app (-> (mock/request :get "/api/v1/auth")
                                  (helper/basic-auth-header "Everyman:pass")))
          body           (helper/parse-body (:body response))
          id             (:id body)
          token-contents (jwt/unsign (:token body) (env :auth-key) {:alg :hs512})]
      (is (= 6            (count body)))
      (is (= 200          (:status response)))
      (is (= "Everyman"   (:username body)))
      (is (= "basic,test" (:permissions body)))
      (is (= 36           (count (:refreshToken body))))
      (is (= 6            (count            token-contents)))
      (is (= [test-perms] (:permission_data token-contents)))
      (is (= "basic,test" (:permissions     token-contents)))
      (is (= id           (:id              token-contents)))
      (is (= "e@man.com"  (:email           token-contents)))
      (is (= "Everyman"   (:username        token-contents)))
      (is (number?        (:exp             token-contents)))))

  (testing "Valid email and password return correct auth credentials"
    (let [response       (app (-> (mock/request :get "/api/v1/auth")
                                  (helper/basic-auth-header "e@man.com:pass")))
          body           (helper/parse-body (:body response))
          id             (:id body)
          token-contents (jwt/unsign (:token body) (env :auth-key) {:alg :hs512})]
      (is (= 6            (count body)))
      (is (= 200          (:status response)))
      (is (= "Everyman"   (:username body)))
      (is (= "basic,test" (:permissions body)))
      (is (= 36           (count (:refreshToken body))))
      (is (= 6            (count        token-contents)))
      (is (= "basic,test" (:permissions token-contents)))
      (is (= id           (:id          token-contents)))
      (is (= "e@man.com"  (:email       token-contents)))
      (is (= "Everyman"   (:username    token-contents)))
      (is (number?        (:exp         token-contents)))))

  (testing "Multiple permissions are properly formated"
    (let [response (app (-> (mock/request :get "/api/v1/auth")
                            (helper/basic-auth-header "JarrodCTaylor:pass")))
          body     (helper/parse-body (:body response))]
      (is (= 200              (:status response)))
      (is (= "JarrodCTaylor"  (:username body)))
      (is (= 36               (count (:refreshToken body))))
      (is (= "basic,admin"    (:permissions (jwt/unsign (:token body) (env :auth-key) {:alg :hs512}))))))

  (testing "Invalid password does not return auth credentials"
    (let [response (app (-> (mock/request :get "/api/v1/auth")
                            (helper/basic-auth-header "JarrodCTaylor:badpass")))
          body     (helper/parse-body (:body response))]
      (is (= 401              (:status response)))
      (is (= "Not authorized" (:error body)))))

  (testing "Invalid username does not return auth credentials"
    (let [response (app (-> (mock/request :get "/api/v1/auth")
                            (helper/basic-auth-header "baduser:badpass")))
          body     (helper/parse-body (:body response))]
      (is (= 401              (:status response)))
      (is (= "Not authorized" (:error body)))))

  (testing "No auth credentials are returned when no username and password provided"
    (let [response (app (mock/request :get "/api/v1/auth"))
          body     (helper/parse-body (:body response))]
      (is (= 401              (:status response)))
      (is (= "Not authorized" (:error body)))))

  (testing "User can generate a new tokens with a valid refresh-token"
    (let [initial-response   (app (-> (mock/request :get "/api/v1/auth")
                                      (helper/basic-auth-header "JarrodCTaylor:pass")))
          initial-body       (helper/parse-body (:body initial-response))
          id                 (:id initial-body)
          refresh-token      (:refreshToken initial-body)
          refreshed-response (app (mock/request :get (str "/api/v1/refresh-token/" refresh-token)))
          body               (helper/parse-body (:body refreshed-response))
          token-contents     (jwt/unsign (:token body) (env :auth-key) {:alg :hs512})]
      (is (= 200              (:status refreshed-response)))
      (is (= 2                (count body)))
      (is (= true             (contains? body :token)))
      (is (= true             (contains? body :refreshToken)))
      (is (not= refresh-token (:refreshToken body)))
      (is (= 5                (count        token-contents)))
      (is (= "basic,admin"    (:permissions token-contents)))
      (is (= id               (:id          token-contents)))
      (is (= "j@man.com"      (:email       token-contents)))
      (is (= "JarrodCTaylor"  (:username    token-contents)))
      (is (number?            (:exp         token-contents)))))

  (testing "Invalid refresh token does not return a new token"
    (let [response (app (mock/request :get "/api/v1/refresh-token/abcd1234"))
          body     (helper/parse-body (:body response))]
      (is (= 400           (:status response)))
      (is (= "Bad Request" (:error body))))))
