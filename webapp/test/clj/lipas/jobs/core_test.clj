(ns lipas.jobs.core-test
  "Pure (non-database) tests for the job registry and core helpers."
  (:require
   [clojure.test :refer [deftest testing is]]
   [lipas.jobs.core :as jobs]
   [lipas.jobs.registry :as registry]
   [malli.core :as m]))

(deftest lane-classification-test
  (testing "Job duration classification comes from the registry"
    (is (registry/fast-job? "email") "Email should be fast")
    (is (registry/fast-job? "webhook") "Webhook should be fast")
    (is (not (registry/fast-job? "analysis")) "Analysis should be slow")
    (is (not (registry/fast-job? "elevation")) "Elevation should be slow"))

  (testing "Every job type is in exactly one lane"
    (is (= (set (keys registry/job-types))
           (into registry/fast-job-types registry/slow-job-types)))
    (is (empty? (clojure.set/intersection registry/fast-job-types
                                          registry/slow-job-types)))))

(deftest job-type-schema-test
  (testing "Malli schema validation"
    (is (m/validate jobs/job-type-schema "email"))
    (is (m/validate jobs/job-type-schema "analysis"))
    (is (not (m/validate jobs/job-type-schema "invalid")))
    (is (not (m/validate jobs/job-type-schema "produce-reminders"))
        "Scheduler-internal work is no longer a job type")
    (is (not (m/validate jobs/job-type-schema "cleanup-jobs"))
        "Scheduler-internal work is no longer a job type")))

(deftest registry-entries-test
  (testing "Every registry entry has the required configuration"
    (doseq [[job-type job-def] registry/job-types]
      (testing job-type
        (is (contains? #{:fast :slow} (:lane job-def)))
        (is (pos-int? (:timeout-min job-def)))
        (is (pos-int? (:priority job-def)))
        (is (pos-int? (:max-attempts job-def)))
        (is (some? (:payload-schema job-def))))))

  (testing "Unknown job type throws"
    (is (thrown-with-msg? Exception #"Unknown job type"
                          (registry/get-def "no-such-type")))))

(deftest payload-validation-test
  (testing "Valid payloads pass"
    (is (:valid? (registry/validate-payload "analysis" {:lipas-id 123})))
    (is (:valid? (registry/validate-payload "email" {:to "a@b.fi"
                                                     :subject "Hi"
                                                     :body "Hello"}))))

  (testing "Invalid payloads fail with humanized errors"
    (let [result (registry/validate-payload "analysis" {})]
      (is (not (:valid? result)))
      (is (some? (:errors result))))
    (is (not (:valid? (registry/validate-payload "email" {:no-to-field true}))))))

(deftest stuck-job-timeout-test
  (testing "Stuck-job threshold exceeds every registered job timeout"
    (let [longest (apply max (map :timeout-min (vals registry/job-types)))]
      (is (> jobs/stuck-job-timeout-minutes longest)
          "A legitimately long-running job must never be recovered mid-run"))))
