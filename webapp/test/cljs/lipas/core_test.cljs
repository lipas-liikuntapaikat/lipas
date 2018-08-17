(ns lipas.ui.core-test
  (:require [cljs.test :refer-macros [deftest testing is]]
            [lipas.ui.core :as core]))

(deftest fake-test
  (testing "fake description"
    (is (= 1 2))))
