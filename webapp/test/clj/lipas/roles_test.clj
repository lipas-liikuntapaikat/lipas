(ns lipas.roles-test
  (:require [clojure.test :refer [deftest is testing]]
            [lipas.roles :as sut]))

(deftest site-roles-context
  (is (= {:lipas-id 1
          :type-code 101
          :city-code 837
          :activity nil}
         (sut/site-roles-context {:lipas-id 1
                                  :type {:type-code 101}
                                  :location {:city {:city-code 837}}
                                  :activities {}})))

  (is (= {:lipas-id 1
          :type-code 101
          :city-code 837
          :activity #{"fishing"}}
         (sut/site-roles-context {:lipas-id 1
                                  :type {:type-code 101}
                                  :location {:city {:city-code 837}}
                                  :activities {:fishing {:foo "bar"}}}))))

(deftest check-privilege-test
  ;; site-roles-context includes all the context keys always, so most test
  ;; cases should also work like that?

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
                 {:lipas-id 1
                  :type-code 101
                  :city-code 837
                  :activity #{"fishing"}}
                 :site/create-edit))))

  (testing "type-code context"
    (is (true? (sut/check-privilege
                 {:permissions {:roles [{:type-code #{101 102} :city-code #{837} :role :type-manager}]}}
                 {:lipas-id 1
                  :type-code 101
                  :city-code 837
                  :activity #{"fishing"}}
                 :site/create-edit)))

    (is (false? (sut/check-privilege
                  {:permissions {:roles [{:type-code #{101 102} :city-code #{837} :role :type-manager}]}}
                  {:lipas-id 1
                   :type-code 101
                   :city-code 0
                   :activity #{"fishing"}}
                  :site/create-edit)))

    (testing "::any context value"
      (is (true? (sut/check-privilege
                   {:permissions {:roles [{:type-code #{101 102} :city-code #{837} :role :type-manager}]}}
                   {:lipas-id 1
                    :type-code ::sut/any
                    :city-code ::sut/any
                    :activity #{"fishing"}}
                   :site/create-edit)))))

  (testing "activity role context"
    (is (true? (sut/check-privilege
                 {:permissions {:roles [{:activity #{"fishing"} :role :activities-manager}]}}
                 {:lipas-id 1
                  :type-code 101
                  :city-code 837
                  :activity #{"fishing"}}
                 :activity/edit)))

    (testing "permission to use site save endpoint"
      (is (true? (sut/check-privilege
                   {:permissions {:roles [{:activity #{"fishing"} :role :activities-manager}]}}
                   {:lipas-id 1
                    :type-code 101
                    :city-code 837
                    :activity #{"fishing"}}
                   :site/save-api))))

    (testing "user has multiple acitivity permissions, site has multiple acitivites keys in data"
      (is (true? (sut/check-privilege
                   {:permissions {:roles [{:activity #{"fishing" "paddling"} :role :activities-manager}]}}
                   {:lipas-id 1
                    :type-code 101
                    :city-code 837
                    :activity #{"fishing" "cycling"}}
                   :site/save-api))))

    (testing "no activities data"
      (is (false? (sut/check-privilege
                    {:permissions {:roles [{:activity #{"fishing"} :role :activities-manager}]}}
                    {:lipas-id 1
                     :type-code 101
                     :city-code 837
                     :activity nil}
                    :site/save-api))))))

(deftest roles-conform-test
  (is (= [{:role :city-manager
           :type-code #{1620}
           :city-code #{20}}]
         (sut/conform-roles [{:role "city-manager"
                              :type-code [1620]
                              :city-code [20]}])))
  (is (= [{:role :site-manager
           :lipas-id #{1}}]
         (sut/conform-roles [{:role "site-manager"
                              :lipas-id [1]}])))

  (is (= [{:role :activities-manager
           :activity #{"fishing"}}]
         (sut/conform-roles [{:role "activities-manager"
                              :activity ["fishing"]}]))))

(deftest wrap-es-query-site-has-privileget-test
  (is (= {:a 1}
         (sut/wrap-es-query-site-has-privilege
           {:a 1}
           {:permissions {:roles [{:role :admin}]}}
           :site/create-edit)))

  (is (= {:bool {:must [{:a 1}
                        {:bool {:should [{:terms {:location.city.city-code #{837}}}]}}]}}
         (sut/wrap-es-query-site-has-privilege
           {:a 1}
           {:permissions {:roles [{:role :city-manager
                                   :city-code #{837}}
                                  ;; Ignored because this role doesn't provide the asked privilege
                                  {:role :floorball-manager
                                   :type-code #{2240}}]}}
           :site/create-edit)))

  (is (= {:bool {:must [{:a 1}
                        {:bool {:should [{:terms {:location.city.city-code #{837}}}
                                         {:bool {:must [{:terms {:location.city.city-code #{91 92 49}}}
                                                        {:terms {:type.type-code #{2240}}}]}}
                                         {:terms {:lipas-id #{1}}}]}}]}}
         (sut/wrap-es-query-site-has-privilege
           {:a 1}
           {:permissions {:roles [{:role :city-manager
                                   :city-code #{837}}
                                  {:role :type-manager
                                   :type-code #{2240}
                                   :city-code #{91 92 49}}
                                  {:role :site-manager
                                   :lipas-id #{1}}]}}
           :site/create-edit))))
