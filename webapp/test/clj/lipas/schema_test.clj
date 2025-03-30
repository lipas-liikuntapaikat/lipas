(ns lipas.schema-test
  (:require [clojure.spec.alpha :refer [valid?]]
            [clojure.test :refer [deftest is testing] :as t]
            [lipas.schema.core]))

(deftest email-validity-test
  (testing "valid emails"
    (is (valid? :lipas/email "a@b.co"))
    (is (valid? :lipas/email "ääkkö@set.com")))
  (testing "invalid emails"
    (is (not (valid? :lipas/email "a..b@.com")))
    (is (not (valid? :lipas/email "ab@..com")))
    (is (not (valid? :lipas/email "ab@...com")))
    (is (not (valid? :lipas/email "ab@...........................com")))
    (is (not (valid? :lipas/email "@.com")))
    (is (not (valid? :lipas/email "a@")))
    (is (not (valid? :lipas/email "a@b")))
    (is (not (valid? :lipas/email "@b")))
    (is (not (valid? :lipas/email "@")))
    (is (not (valid? :lipas/email "a.b.com")))))
