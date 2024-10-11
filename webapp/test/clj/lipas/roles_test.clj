(ns lipas.roles-test
  (:require [clojure.spec.alpha :as s]
            [clojure.test :refer [deftest is testing]]
            [lipas.roles :as sut]
            [spec-tools.core :as st]))

(deftest check-privilege-test
  (is (true? (sut/check-privilege
               {:permissions {:roles [{:role :admin}]}}
               {}
               :users/manage)))

  (testing "missing privilege"
    (is (false? (sut/check-privilege
                  {:permissions {:roles []}}
                  {:lipas-id 1}
                  :site/create-edit))))

  (testing "default privileges"
    (is (true? (sut/check-privilege
                 {:permissions {:roles []}}
                 {:lipas-id 1}
                 :site/view))))

  (testing "lipas-id context"
    (is (true? (sut/check-privilege
                 {:permissions {:roles [{:lipas-id #{1} :role :site-manager}]}}
                 {:lipas-id 1}
                 :site/create-edit))))

  (testing "type-code context"
    (is (true? (sut/check-privilege
                 {:permissions {:roles [{:type-code #{101 102} :city-code #{837} :role :type-manager}]}}
                 {:type-code 101
                  :city-code 837}
                 :site/create-edit)))

    (is (false? (sut/check-privilege
                  {:permissions {:roles [{:type-code #{101 102} :city-code #{837} :role :type-manager}]}}
                  {:type-code 101
                   :city-code 0}
                  :site/create-edit)))

    (testing "::any context value"
      (is (true? (sut/check-privilege
                   {:permissions {:roles [{:type-code #{101 102} :city-code #{837} :role :type-manager}]}}
                   {:type-code ::sut/any
                    :city-code ::sut/any}
                   :site/create-edit)))))

  (testing "activity role context"
    (is (true? (sut/check-privilege
                 {:permissions {:roles [{:activity #{"fishing"} :role :activities-manager}]}}
                 {:activity "fishing"}
                 :activity/edit)))))

(deftest roles-conform-test
  (is (= {:roles [{:role :city-manager
                   :type-code #{1620}
                   :city-code #{20}}]}
         (sut/conform-roles [{:role "city-manager"
                              :type-code [1620]
                              :city-code [20]}])))
  (is (= {:roles [{:role :site-manager
                   :lipas-id #{1}}]}
         (sut/conform-roles [{:role "site-manager"
                              :lipas-id [1]
                              :city-code [20]}])))

  (is (= {:roles [{:role :activities-manager
                   :activity #{"fishing"}}]}
         (sut/conform-roles [{:role "activities-manager"
                              :activity ["fishing"]}]))))
