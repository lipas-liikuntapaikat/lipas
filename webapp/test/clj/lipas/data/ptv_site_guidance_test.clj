(ns lipas.data.ptv-site-guidance-test
  "The Service Location guidance is keyed by type-code, so it drifts silently
  when LIPAS gains a type: the new type simply renders no accordion and the
  assistant loses its group block. These tests fail instead.

  DVV groups types into ten groups of their own that cut across LIPAS
  sub-categories, so `type-code -> group` is a genuine many-to-one mapping
  that cannot be derived from the type hierarchy — it has to be maintained
  by hand, and therefore checked."
  (:require
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing]]
    [lipas.data.ptv-site-guidance :as guidance]
    [lipas.data.types :as types]))

(def ui-fields [:summary :description :avoid])
(def locales [:fi :se :en])

(deftest every-active-type-has-guidance
  (doseq [type-code (keys types/active)]
    (is (some? (guidance/for-type-code type-code))
        (str "type " type-code " (" (get-in types/active [type-code :name :fi])
             ") has no DVV group — add it to lipas.data.ptv-site-guidance"))))

(deftest no-type-belongs-to-two-groups
  (let [pairs (for [[k {:keys [type-codes]}] guidance/groups
                    type-code type-codes]
                [type-code k])]
    (doseq [[type-code group-keys] (group-by first pairs)]
      (is (= 1 (count group-keys))
          (str "type " type-code " is in several groups: "
               (str/join ", " (map second group-keys)))))))

(deftest guidance-covers-only-real-types
  (doseq [[group-key {:keys [type-codes]}] guidance/groups
          type-code type-codes]
    (is (contains? types/all type-code)
        (str group-key " lists unknown type " type-code))))

(deftest ui-fields-are-localized
  (doseq [[group-key group] guidance/groups
          field ui-fields
          locale locales]
    (is (not (str/blank? (get-in group [field locale])))
        (str group-key " " field " missing " locale))))

(deftest assistant-fields-present
  (doseq [[group-key group] guidance/groups]
    (testing (str group-key)
      (is (not (str/blank? (:definition group))))
      (is (not (str/blank? (:usable-info group))))
      (is (not (str/blank? (get-in group [:name :fi])))))))

(deftest text-falls-back-to-finnish
  (let [type-code (first (keys types/active))]
    (is (= (guidance/text type-code :summary :fi)
           (guidance/text type-code :summary "de"))
        "an unsupported locale falls back to the source language")))

(deftest text-is-nil-for-unknown-type
  (is (nil? (guidance/text -1 :summary :fi))))

(deftest lipas-assigned-codes-are-flagged-and-mapped
  (testing "codes LIPAS assigned rather than DVV are declared"
    (doseq [type-code guidance/lipas-assigned-type-codes]
      (is (contains? types/all type-code))
      (is (some? (guidance/for-type-code type-code))))))

(deftest general-principles-are-complete
  (is (seq guidance/general-principles))
  (doseq [principle guidance/general-principles]
    (doseq [k [:topic :question :guidance :avoid]]
      (is (not (str/blank? (get principle k)))
          (str (:topic principle) " missing " k)))))
