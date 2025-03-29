(ns lipas.schema-test
  (:require [clojure.spec.alpha :refer [valid?]]
            [clojure.test :refer [deftest is testing] :as t]
            [lipas.schema.core]
            [lipas.schema.help :as help-schema]
            [lipas.data.help :as help-data]
            [malli.core :as m]
            [malli.error :as me]))

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

(deftest help-data-schema-test
  (testing "Help data model conforms to schema"
    (let [explanation (m/explain help-schema/HelpData help-data/sections)
          humanized (when explanation (me/humanize explanation))]
      (println "Block-id format in schema:" (pr-str (:schema (first (:errors explanation)))))
      (println "Block-id value in data:" (pr-str (:value (first (:errors explanation)))))
      (is (nil? explanation)
          (str "The help data should validate against the schema, but got errors: " 
               (pr-str humanized))))))
