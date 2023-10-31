;; these tests are not run
(ns lipas.search-test
  (:require [clojure.test :refer-macros [deftest testing is]]
            [lipas.ui.search.events :as se]))

(deftest sorting-order-test
  (testing "user clicks a new column"
    (let [db {:search {:sort {:sort-fn :name}}}
          new-sort {:sort-fn :location}
          result (se/resolve-sort-change db new-sort)
          expected {:asc? true :sort-fn :location}]
      (is (= (:sort-fn expected) (:sort-fn result)))))
  (testing "user clicks a existing column"
        (let [db {:search {:sort {:sort-fn :name}}}
          new-sort {:sort-fn :name}
          result (se/resolve-sort-change db new-sort)
          expected {:asc? true :sort-fn :name}]
      (is (not (= (:sort-fn expected) (:sort-fn result)))))))

(comment
  (cljoure.test/run-tests *ns*)
  )
