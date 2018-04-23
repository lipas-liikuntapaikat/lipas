(ns auth-service.refresh-token.refresh-token-deletion-tests
  (:require [clojure.test :refer :all]
            [auth-service.handler :refer :all]
            [auth-service.test-utils :as helper]
            [auth-service.query-defs :as query]
            [ring.mock.request                         :as mock]
            [taoensso.timbre                           :as timbre]
            [mount.core                                :as mount]
            [cheshire.core                             :as ch]))

(use-fixtures :once (fn [f]
                      (try
                        (timbre/merge-config! {:level :warn})
                        (mount/start)
                        (query/insert-permission! {:permission "basic"})
                        (helper/add-users)
                        (f)
                        (finally (query/truncate-all-tables-in-database!)))))

(deftest testing-refresh-token-deletion

  (testing "Can delete refresh token with valid refresh token"
    (let [user-id                 (:id (query/get-registered-user-by-username {:username "JarrodCTaylor"}))
          initial-response        (app (-> (mock/request :get "/api/v1/auth")
                                           (helper/basic-auth-header "JarrodCTaylor:pass")))
          initial-body            (helper/parse-body (:body initial-response))
          refresh-token           (:refreshToken initial-body)
          refresh-delete-response (app (mock/request :delete (str "/api/v1/refresh-token/" refresh-token)))
          body                    (helper/parse-body (:body refresh-delete-response))
          registered-user-row     (query/get-registered-user-by-id {:id user-id})]
      (is (= 200 (:status refresh-delete-response)))
      (is (= "Refresh token successfully deleted" (:message body)))
      (is (= nil (:refresh_token registered-user-row)))))

  (testing "Attempting to delete an invalid refresh token returns an error"
    (let [refresh-delete-response (app (mock/request :delete (str "/api/v1/refresh-token/" "123abc")))
          body                    (helper/parse-body (:body refresh-delete-response))]
      (is (= 404 (:status refresh-delete-response)))
      (is (= "The refresh token does not exist" (:error body))))))
