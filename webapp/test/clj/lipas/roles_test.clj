(ns lipas.roles-test
  (:require [clojure.test :refer [deftest is testing]]
            [lipas.roles :as sut]))

(deftest site-roles-context
  ;; type-code 101 has no activity mapping -> behaves as before
  (is (= {:lipas-id 1
          :type-code 101
          :city-code 837
          :activity nil
          :org-id nil}
         (sut/site-roles-context {:lipas-id 1
                                  :type {:type-code 101}
                                  :location {:city {:city-code 837}}
                                  :activities {}})))

  (is (= {:lipas-id 1
          :type-code 101
          :city-code 837
          :activity #{"fishing"}
          :org-id nil}
         (sut/site-roles-context {:lipas-id 1
                                  :type {:type-code 101}
                                  :location {:city {:city-code 837}}
                                  :activities {:fishing {:foo "bar"}}})))

  (testing ":activity is derived from the type-code even without UTP data (PR #193 F34)"
    ;; A fresh cycling route has no :activities doc data yet, but an
    ;; activities-manager must still be able to add the FIRST activity data.
    (is (= #{"cycling"}
           (:activity (sut/site-roles-context {:lipas-id 1
                                               :type {:type-code 4412}
                                               :location {:city {:city-code 837}}}))))
    (is (= #{"cycling"}
           (:activity (sut/site-roles-context {:lipas-id 1
                                               :type {:type-code 4411}
                                               :activities {}}))))

    (testing "activities-manager can edit a typed-but-UTP-empty site"
      (let [rc (sut/site-roles-context {:lipas-id 1
                                        :type {:type-code 4412}
                                        :location {:city {:city-code 837}}})]
        (is (true? (sut/check-privilege
                     {:permissions {:roles [{:role :activities-manager :activity #{"cycling"}}]}}
                     rc
                     :activity/edit)))
        (is (true? (sut/check-privilege
                     {:permissions {:roles [{:role :activities-manager :activity #{"cycling"}}]}}
                     rc
                     :site/save-api)))
        ;; manager scoped to a different activity still gets nothing
        (is (false? (sut/check-privilege
                      {:permissions {:roles [{:role :activities-manager :activity #{"fishing"}}]}}
                      rc
                      :activity/edit))))))

  (testing ":activity is the union of doc activities and the type-derived activity"
    (is (= #{"cycling" "fishing"}
           (:activity (sut/site-roles-context {:lipas-id 1
                                               :type {:type-code 4412}
                                               :activities {:fishing {:foo "bar"}}})))))

  (testing ":org-id is the set of a site's editor orgs (owner + grants), as strings"
    (is (= #{"11111111-1111-1111-1111-111111111111"}
           (:org-id (sut/site-roles-context
                      {:lipas-id 1 :owner-org-id "11111111-1111-1111-1111-111111111111"}))))
    (is (= #{"11111111-1111-1111-1111-111111111111"
             "22222222-2222-2222-2222-222222222222"}
           (:org-id (sut/site-roles-context
                      {:lipas-id 1
                       :owner-org-id "11111111-1111-1111-1111-111111111111"
                       :edit-grants ["22222222-2222-2222-2222-222222222222"]}))))
    ;; no owner, no grants -> nil (legacy sites carry no org context)
    (is (nil? (:org-id (sut/site-roles-context {:lipas-id 1}))))))

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
                    :site/save-api)))))

  (testing "itrs-assessor role"
    ;; Basic privilege check
    (is (true? (sut/check-privilege
                 {:permissions {:roles [{:role :itrs-assessor}]}}
                 {}
                 :itrs/edit)))

    ;; With city-code context
    (is (true? (sut/check-privilege
                 {:permissions {:roles [{:role :itrs-assessor :city-code #{837}}]}}
                 {:city-code 837}
                 :itrs/edit)))

    ;; Wrong city-code
    (is (false? (sut/check-privilege
                  {:permissions {:roles [{:role :itrs-assessor :city-code #{837}}]}}
                  {:city-code 91}
                  :itrs/edit)))

    ;; Activities-manager does NOT get itrs/edit
    (is (false? (sut/check-privilege
                  {:permissions {:roles [{:role :activities-manager :activity #{"cycling"}}]}}
                  {:activity #{"cycling"}}
                  :itrs/edit)))

    ;; Admin gets itrs/edit
    (is (true? (sut/check-privilege
                 {:permissions {:roles [{:role :admin}]}}
                 {}
                 :itrs/edit)))

    ;; itrs-assessor also gets site/save-api
    (is (true? (sut/check-privilege
                 {:permissions {:roles [{:role :itrs-assessor}]}}
                 {}
                 :site/save-api))))

  (testing "images-manager role"
    ;; Scoped to a city-code, grants :site/edit-images
    (is (true? (sut/check-privilege
                 {:permissions {:roles [{:role :images-manager :city-code #{430}}]}}
                 {:city-code 430}
                 :site/edit-images)))

    ;; Wrong city-code denies
    (is (false? (sut/check-privilege
                  {:permissions {:roles [{:role :images-manager :city-code #{430}}]}}
                  {:city-code 431}
                  :site/edit-images)))

    ;; Does NOT imply :site/save-api — the backend's check-permissions! must
    ;; additionally verify the incoming diff is images-only.
    (is (false? (sut/check-privilege
                  {:permissions {:roles [{:role :images-manager :city-code #{430}}]}}
                  {:city-code 430}
                  :site/save-api)))

    ;; Does NOT grant edit on other narrow privileges
    (is (false? (sut/check-privilege
                  {:permissions {:roles [{:role :images-manager :city-code #{430}}]}}
                  {:city-code 430}
                  :activity/edit)))))

(deftest org-id-role-context-test
  ;; Pins the select-role semantic the FE permission subs rely on (PR #193
  ;; review finding F4): an org-id-scoped role activates only when the
  ;; context carries :org-id — "can the user do this anywhere" checks must
  ;; pass :org-id ::sut/any or org-scoped roles silently never match.
  (let [org-id "11111111-1111-1111-1111-111111111111"
        user {:permissions {:roles [{:role :org-editor :org-id #{org-id}}]}}]

    (testing "org-id-scoped role does not activate when context lacks :org-id"
      (is (false? (sut/check-privilege
                    user
                    {:type-code ::sut/any :city-code ::sut/any}
                    :site/create-edit))))

    (testing "org-id-scoped role activates with :org-id ::any"
      (is (true? (sut/check-privilege
                   user
                   {:type-code ::sut/any :city-code ::sut/any :org-id ::sut/any}
                   :site/create-edit))))

    (testing "org-id-scoped role activates for a matching concrete org-id"
      (is (true? (sut/check-privilege
                   user
                   {:org-id #{org-id}}
                   :site/create-edit))))

    (testing "org-id-scoped role does not activate for a different org"
      (is (false? (sut/check-privilege
                    user
                    {:org-id #{"22222222-2222-2222-2222-222222222222"}}
                    :site/create-edit))))))

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
                              :activity ["fishing"]}])))

  (is (= [{:role :images-manager
           :city-code #{430}}]
         (sut/conform-roles [{:role "images-manager"
                              :city-code [430]}]))))

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
           :site/create-edit)))

  (testing ":org-editor compiles to an editor-org-ids terms filter"
    (is (= {:bool {:must [{:a 1}
                          {:bool {:should [{:terms {:search-meta.editor-org-ids
                                                    #{"11111111-1111-1111-1111-111111111111"}}}]}}]}}
           (sut/wrap-es-query-site-has-privilege
             {:a 1}
             {:permissions {:roles [{:role :org-editor
                                     :org-id #{"11111111-1111-1111-1111-111111111111"}}]}}
             :site/create-edit)))))
