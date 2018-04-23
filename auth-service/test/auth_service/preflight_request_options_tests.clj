(ns auth-service.preflight-request-options-tests
  (:require [clojure.test :refer :all]
            [auth-service.handler :refer [app]]
            [ring.mock.request :as mock]))

(deftest preflight-request-options-returns-success-for-valid-path
  (testing "Prefligh request options returns success for valid path"
    (let [response (app (mock/request :options "/api/v1/user/token"))]
      (is (= 200 (:status response))))))

(deftest preflight-request-options-returns-success-for-invalid-path
  (testing "Prefligh request options returns success for invalid path"
    (let [response (app (mock/request :options "/api/v1/invalid/thing"))]
      (is (= 200 (:status response))))))
