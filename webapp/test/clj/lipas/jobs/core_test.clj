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

(deftest save-triggers-test
  (let [geo (fn [coords] {:type "FeatureCollection"
                          :features [{:type "Feature"
                                      :geometry {:type "LineString"
                                                 :coordinates coords}}]})
        ;; 4402 = hiking route, a LineString type
        route-site {:status "active"
                    :type {:type-code 4402}
                    :location {:geometries (geo [[25.0 62.0] [25.1 62.1]])}
                    :phone-number "123"}
        moved (assoc-in route-site [:location :geometries]
                        (geo [[25.0 62.0] [25.2 62.2]]))
        with-z (assoc-in route-site [:location :geometries]
                         (geo [[25.0 62.0 100.0] [25.1 62.1 101.0]]))]

    (testing "Irrelevant edits (e.g. phone number) trigger neither job"
      (let [new (assoc route-site :phone-number "456")]
        (is (not (registry/should-enqueue? "analysis" route-site new)))
        (is (not (registry/should-enqueue? "elevation" route-site new)))))

    (testing "Geometry edits trigger both jobs"
      (is (registry/should-enqueue? "analysis" route-site moved))
      (is (registry/should-enqueue? "elevation" route-site moved)))

    (testing "Type-code and status changes trigger analysis but not elevation"
      (let [retyped (assoc-in route-site [:type :type-code] 4401)
            deactivated (assoc route-site :status "out-of-service-permanently")]
        (is (registry/should-enqueue? "analysis" route-site retyped))
        (is (not (registry/should-enqueue? "elevation" route-site retyped)))
        (is (registry/should-enqueue? "analysis" route-site deactivated))
        (is (not (registry/should-enqueue? "elevation" route-site deactivated)))))

    (testing "New sites (no previous revision) trigger everything applicable"
      (is (registry/should-enqueue? "analysis" nil route-site))
      (is (registry/should-enqueue? "elevation" nil route-site)))

    (testing "Elevation applies only to route (LineString) types"
      (is (not (registry/should-enqueue? "elevation" nil
                                         (assoc-in route-site [:type :type-code] 1120)))))

    (testing "A save that only adds z (enrichment-shaped) does not re-trigger"
      (is (not (registry/should-enqueue? "elevation" route-site with-z))
          "The 2D comparison makes enrichment output invisible to the diff")
      (is (not (registry/should-enqueue? "analysis" route-site with-z))))

    (testing "A save that loses previously-enriched z data re-triggers elevation"
      (is (registry/should-enqueue? "elevation" with-z route-site)
          "Re-enrichment restores the lost z coordinates"))

    (testing "Types without a trigger-fn always enqueue"
      (is (registry/should-enqueue? "email" route-site route-site)))))

(deftest stuck-job-timeout-test
  (testing "Stuck-job threshold exceeds every registered job timeout"
    (let [longest (apply max (map :timeout-min (vals registry/job-types)))]
      (is (> jobs/stuck-job-timeout-minutes longest)
          "A legitimately long-running job must never be recovered mid-run"))))
