(ns lipas.utils-test
  (:require [clojure.test :refer [deftest is testing]]
            [lipas.utils :as utils]))

(deftest sortable-name-test
  (testing "sortable name"
    (is (= (utils/->sortable-name "\"Bantis\" beachvolleykenttä (2)") "bantis beachvolleykenttä 2")))
  (testing "sortable name with nil"
    (is (= (utils/->sortable-name nil) ""))))
  
  
(comment
  (t/run-tests *ns*)
  )
