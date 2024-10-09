(ns lipas.permissions-test
  (:require
   [clojure.test :refer [deftest testing is] :as t]
   [lipas.permissions :as permissions]
   [lipas.schema.core]
   [lipas.test-utils :as tu]))

(defn ->test-site
  [lipas-id type-code city-code]
  (-> (tu/gen-sports-site)
      (assoc-in [:lipas-id] lipas-id)
      (assoc-in [:type :type-code] type-code)
      (assoc-in [:location :city :city-code] city-code)))

#_
(deftest permissions-test
  (testing "Permission logic"
    (let [site (->test-site 12345 1170 992)]
      (is (true? (permissions/draft? {:sports-sites [12345]} site)))
      (is (true? (permissions/publish? {:admin? true} site)))
      (is (true? (permissions/publish? {:sports-sites [12345]} site)))
      (is (true? (permissions/publish? {:types [1170] :cities [992]} site)))
      (is (true? (permissions/publish? {:types [1170] :all-cities? true} site)))
      (is (true? (permissions/publish? {:all-types? true :cities [992]} site)))
      (is (true? (permissions/publish? {:all-types? true :all-cities? true} site)))
      (is (false? (permissions/publish? {} site)))
      (is (false? (permissions/publish? {:sports-sites [54321]} site)))
      (is (false? (permissions/publish? {:types [3110]} site)))
      (is (false? (permissions/publish? {:types [1170]} site)))
      (is (false? (permissions/publish? {:cities [179]} site)))
      (is (false? (permissions/publish? {:cities [992]} site)))
      (is (false? (permissions/publish? {:types [1170] :cities [179]} site)))
      (is (false? (permissions/publish? {:types [3110] :cities [992]} site)))
      (is (false? (permissions/publish? {:all-cities? true} site)))
      (is (false? (permissions/publish? {:all-types? true} site))))))

(comment
  (t/run-tests *ns*))
