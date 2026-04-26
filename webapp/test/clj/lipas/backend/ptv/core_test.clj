(ns lipas.backend.ptv.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [lipas.backend.ptv.core :as ptv-core]))

(def ^:private strip-blanks #'ptv-core/strip-blank-localized-entries)

(deftest strip-blank-localized-entries-test
  (testing "strips blank-valued entries from localized list fields"
    (let [m {:requirements [{:value "" :language "fi"}
                            {:value "Maksuton" :language "fi"}
                            {:value nil :language "sv"}]
             :serviceDescriptions [{:type "Description" :language "fi" :value "Hello"}]
             :areas [{:type "Municipality" :areaCodes ["425"]}]
             :sourceId "lipas-x"}
          out (strip-blanks m)]
      (is (= [{:value "Maksuton" :language "fi"}]
             (:requirements out))
          "blank/nil :value entries are removed; non-blank kept")
      (is (= [{:type "Description" :language "fi" :value "Hello"}]
             (:serviceDescriptions out))
          "non-localized-list fields untouched, non-blank entries preserved")
      (is (= [{:type "Municipality" :areaCodes ["425"]}] (:areas out))
          "non-localized fields (even sequential) untouched")
      (is (= "lipas-x" (:sourceId out)))))

  (testing "all-blank list becomes empty vector, not removed"
    (let [m {:requirements [{:value "" :language "fi"}
                            {:value "  " :language "sv"}]}]
      (is (= [] (:requirements (strip-blanks m))))))

  (testing "empty/missing fields untouched"
    (is (= {} (strip-blanks {})))
    (is (= {:requirements []} (strip-blanks {:requirements []})))
    (is (= {:requirements nil} (strip-blanks {:requirements nil})))))
