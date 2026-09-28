(ns lipas.data.admins-owners-test
  "Guards on the :admin and :owner enums.

  These two maps are load-bearing in three places at once: they are the
  Malli enum the save endpoint validates against, they are the editor's
  select options (lipas.ui.sports-sites.db), and their keys are what gets
  persisted verbatim into sports_site.document. So renaming a key does
  not migrate anything - it silently makes the editor write a new value
  that older rows do not use, and makes older rows fail validation.

  That is exactly what happened between de4d8c92 (2025-12-14) and
  e7668755 (2026-01-01): \"unknown\" was renamed to \"no-information\" to
  match V1 API output, and 11 sites were saved with the legacy value
  before it was renamed back. See
  lipas.migrations.clean-legacy-admin-owner-values.

  The spot-check tests in lipas.schema.sports-sites-test did not catch it
  because they assert a handful of values, never the whole key set. These
  do. If you are here because one of them failed: changing a key is a data
  migration, not an edit."
  (:require [clojure.test :refer [deftest is testing]]
            [lipas.backend.api.v1.transform :as v1]
            [lipas.data.admins :as admins]
            [lipas.data.owners :as owners]))

(def expected-admins
  #{"city-sports"
    "city-education"
    "city-technical-services"
    "city-other"
    "municipal-consortium"
    "private-association"
    "private-company"
    "private-foundation"
    "state"
    "other"
    "unknown"})

(def expected-owners
  #{"city"
    "city-main-owner"
    "municipal-consortium"
    "state"
    "foundation"
    "company-ltd"
    "registered-association"
    "other"
    "unknown"})

(deftest persisted-admin-values-test
  (testing "the stored :admin values are exactly these"
    (is (= expected-admins (set (keys admins/all)))))

  (testing "the display variant does not add or drop keys"
    ;; admins/all is admins/old with a few :fi labels replaced via assoc-in.
    ;; Rename a key in old and those assoc-ins silently graft a bogus entry on.
    (is (= (set (keys admins/old)) (set (keys admins/all))))))

(deftest persisted-owner-values-test
  (testing "the stored :owner values are exactly these"
    (is (= expected-owners (set (keys owners/all))))))

(deftest no-information-is-v1-output-only-test
  (testing "\"no-information\" is never a stored value"
    (is (not (contains? admins/all "no-information")))
    (is (not (contains? owners/all "no-information"))))

  (testing "\"unknown\" is the stored spelling of \"Ei tietoa\""
    (is (= "Ei tietoa" (get-in admins/all ["unknown" :fi])))
    (is (= "Ei tietoa" (get-in owners/all ["unknown" :fi]))))

  (testing "V1 output renames it on the way out, and only on the way out"
    (let [site {:name "Testikohde"
                :admin "unknown"
                :owner "unknown"
                :type {:type-code 1530}
                :location {:city {:city-code 179}}}
          out (v1/->old-lipas-sports-site site)]
      (is (= "no-information" (:admin out)))
      (is (= "no-information" (:owner out)))
      (is (not (contains? admins/all (:admin out)))
          "if this fails, V1 output has become a legal stored value again")
      (is (not (contains? owners/all (:owner out)))))))
