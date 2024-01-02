(ns lipas.backend.business-logic-test
  (:require [clojure.test :as t :refer [deftest is testing]]
            [lipas.backend.core :as core]))

(deftest gdpr-removal-logic-test
  (testing "More than 5 years of total user inactivity"
    (let [now           (java.time.Instant/now)
          long-time-ago (java.time.Instant/parse "2015-01-01T00:00:00.000Z")
          user          {:email      "kissa@koira.fi"
                         :created-at (java.sql.Timestamp/from long-time-ago)
                         :history    {}}]
      (is (true? (core/gdpr-remove? now user)))))

  (testing "Less than 5 years of total user inactivity"
    (let [now         (java.time.Instant/parse "2020-01-01T00:00:00.000Z")
          a-while-ago (java.time.Instant/parse "2017-01-01T00:00:00.000Z")
          user        {:email      "kissa@koira.fi"
                       :created-at (java.sql.Timestamp/from a-while-ago)
                       :history    {}}]
      (is (false? (core/gdpr-remove? now user)))))

  (testing "User was created > 5 years and has very old activity"
    (let [now           (java.time.Instant/now)
          long-time-ago (java.time.Instant/parse "2015-01-01T00:00:00.000Z")
          user          {:email      "kissa@koira.fi"
                         :created-at (java.sql.Timestamp/from long-time-ago)
                         :history    {:events [{:event-date "2015-01-01T00:00:00.000Z"}
                                               {:event-date "2015-09-12T00:00:00.000Z"}]}}]
      (is (true? (core/gdpr-remove? now user)))))

  (testing "User was created > 5 years and has recent activity"
    (let [now           (java.time.Instant/parse "2024-01-01T00:00:00.000Z")
          long-time-ago (java.time.Instant/parse "2015-01-01T00:00:00.000Z")
          user          {:email      "kissa@koira.fi"
                         :created-at (java.sql.Timestamp/from long-time-ago)
                         :history    {:events [{:event-date "2015-01-01T00:00:00.000Z"}
                                               {:event-date "2023-09-12T00:00:00.000Z"}]}}]
      (is (false? (core/gdpr-remove? now user)))))

  (testing "System users with @lipas.fi email are ignored"
    (let [now           (java.time.Instant/now)
          long-time-ago (java.time.Instant/parse "2015-01-01T00:00:00.000Z")
          user          {:email      "whatever@lipas.fi"
                         :created-at (java.sql.Timestamp/from long-time-ago)
                         :history    {}}]
      (is (false? (core/gdpr-remove? now user))))))

(comment
  (t/run-tests *ns*)
  )
