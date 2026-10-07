(ns lipas.backend.config-test
  (:require [clojure.test :refer [deftest is testing]]
            [environ.core :as e]
            [lipas.backend.config :as config]))

(deftest secret!-test
  (testing "a set value is returned"
    (with-redefs [e/env {:auth-key "s3cret"}]
      (is (= "s3cret" (config/secret! :auth-key)))))
  ;; buddy signs and verifies with a nil or "" key, so neither may get through.
  (testing "unset and blank both throw"
    (doseq [env [{} {:auth-key ""} {:auth-key "  "}]]
      (with-redefs [e/env env]
        (is (thrown? Exception (config/secret! :auth-key)))))))

(deftest auth-key-loaded-test
  (is (string? config/auth-key)))
