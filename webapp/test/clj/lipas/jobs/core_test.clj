(ns lipas.jobs.core-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [lipas.jobs.core :as jobs]
   [malli.core :as m]))

(deftest fast-job-classification-test
  (testing "Job duration classification"
    (is (jobs/fast-job? "email") "Email should be fast")
    (is (jobs/fast-job? "produce-reminders") "Reminders should be fast")
    (is (jobs/fast-job? "integration") "Integration should be fast")
    (is (not (jobs/fast-job? "analysis")) "Analysis should be slow")
    (is (not (jobs/fast-job? "elevation")) "Elevation should be slow")))

(deftest malli-schema-validation-test
  (testing "Malli schema validation"
    (is (m/validate jobs/job-type-schema "email") "Valid job type should pass")
    (is (m/validate jobs/job-type-schema "analysis") "Valid job type should pass")
    (is (not (m/validate jobs/job-type-schema "invalid")) "Invalid job type should fail")))

;; TODO: Add database integration tests once test fixtures are fixed
;; (deftest enqueue-and-fetch-job-test ...)
