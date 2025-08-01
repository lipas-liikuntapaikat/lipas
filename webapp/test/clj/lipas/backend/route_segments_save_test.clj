(ns lipas.backend.route-segments-save-test
  "Test to verify that route segments are saved correctly to the database"
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [lipas.test-utils :as tu]
            [lipas.backend.core :as core]
            [lipas.utils :as utils]
            [taoensso.timbre :as log]))

(use-fixtures :once (fn [f] (tu/init-db!) (f)))
(use-fixtures :each (fn [f] (tu/prune-db!) (tu/prune-es!) (f)))

(deftest route-segments-save-test
  (testing "Route segments are saved correctly to database"
    ;; TODO
    ))

(deftest route-segments-validation-test
  (testing "Route segments validation"


    (testing "Invalid segment direction is rejected"
      ;; TODO
      )

    (testing "Missing fid is rejected"
      ;; TODO
      )

    (testing "Non-existent fid reference is rejected"
      ;; TODO
      )))
