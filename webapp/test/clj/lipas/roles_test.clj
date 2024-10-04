(ns lipas.roles-test
  (:require [clojure.test :refer [deftest is testing]]
            [lipas.roles :as sut]))

(deftest check-privilege-test
  (is (true? (sut/check-privilege
               {:permissions {:roles [{:role :admin}]}}
               {}
               :user-management)))

  (testing ":admin should be a global role, context doesn't matter"
    (is (false? (sut/check-privilege
                  {:permissions {:roles [{:lipas-id 1 :role :admin}]}}
                  {}
                  :user-management))))

  (testing "missing privilege"
    (is (false? (sut/check-privilege
                  {:permissions {:roles []}}
                  {:site {:lipas-id 1}}
                  :create))))

  (testing "default privileges"
    (is (true? (sut/check-privilege
                 {:permissions {:roles []}}
                 {:site {:lipas-id 1}}
                 :view))))

  (testing "lipas-id context"
    (is (true? (sut/check-privilege
                 {:permissions {:roles [{:lipas-id 1 :role :basic-manager}]}}
                 {:site {:lipas-id 1}}
                 :create))))

  (testing "type-code context"
    (is (true? (sut/check-privilege
                 {:permissions {:roles [{:type-code 100 :city-code 837 :role :basic-manager}]}}
                 {:site {:type {:type-code 100}
                         :location {:city {:city-code 837}}}}
                 :edit)))

    (is (false? (sut/check-privilege
                 {:permissions {:roles [{:type-code 100 :city-code 837 :role :basic-manager}]}}
                 {:site {:type {:type-code 100}
                         :location {:city {:city-code 100}}}}
                 :edit))))

  (testing "activity role context"
    (is (true? (sut/check-privilege
                 {:permissions {:roles [{:activity "fishing" :role :activities-manager}]}}
                 {:site {:lipas-id 1}
                  :activity "fishing"}
                 :edit-activity)))))
