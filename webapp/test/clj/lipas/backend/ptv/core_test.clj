(ns lipas.backend.ptv.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [lipas.backend.ptv.core :as ptv-core]
            [lipas.backend.ptv.integration :as ptv]
            [lipas.data.ptv :as ptv-data]
            [lipas.data.types]))

(def ^:private strip-blanks #'ptv-core/strip-blank-localized-entries)
(def ^:private normalize #'ptv-core/normalize-ptv-service-for-update)
(def ^:private site->missing-services #'ptv-core/site->missing-services)

(deftest site->missing-services-test
  ;; The sync-ptv! guard throws "Site needs a PTV Service that doesn't
  ;; exists" iff this returns non-empty. Regression: it used to hard-code
  ;; :service-ids #{} and so rejected sites that reference a service PTV
  ;; created directly (no LIPAS sourceId) or an adopted service.
  (let [org-id "7fdd7f84-e52a-4c17-a59a-d7c2a3095ed5"
        types lipas.data.types/all
        ;; type-code 1370 -> sub-category 1300 (Pallokentät)
        sub-cat-id (-> 1370 types :sub-category)
        managed-source-id (ptv-data/->service-source-id org-id sub-cat-id)
        ;; org's services keyed by :sourceId, as get-org-services returns them
        no-managed-service {}
        with-managed-service {managed-source-id {:id "managed-svc" :sourceId managed-source-id}}
        site (fn [service-ids]
               {:type {:type-code 1370}
                :ptv {:org-id org-id :service-ids service-ids}})]

    (testing "no service for the sub-category and site references none -> missing"
      (let [missing (site->missing-services types no-managed-service org-id (site []))]
        (is (= 1 (count missing)))
        (is (= managed-source-id (-> missing first :source-id)))
        (is (= sub-cat-id (-> missing first :sub-category-id)))))

    (testing "site references an existing PTV-created service (no LIPAS sourceId) -> NOT missing"
      ;; The native service isn't in source-id->service at all, yet the
      ;; site is satisfied because it already carries a service-id.
      (is (empty? (site->missing-services types no-managed-service org-id
                                          (site ["77100eae-native-service"])))))

    (testing "site references an adopted service -> NOT missing"
      (is (empty? (site->missing-services types no-managed-service org-id
                                          (site [(str "adopted-" org-id)])))))

    (testing "LIPAS sub-category service exists, site references none yet -> NOT missing"
      (is (empty? (site->missing-services types with-managed-service org-id (site [])))))))

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

(deftest normalize-drops-get-only-fields
  (testing "GET-response-only fields are removed before PUT"
    (let [out (normalize {:id "abc"
                          :modified "2026-04-26T00:00:00Z"
                          :organizations [{:organization {:id "x"} :roleType "Responsible"}]
                          :serviceChannels [{:id "ch1"}]
                          :sourceId "lipas-x"
                          :ontologyTerms []
                          :serviceClasses []
                          :targetGroups []
                          :areas []})]
      (is (not (contains? out :id)))
      (is (not (contains? out :modified)))
      (is (not (contains? out :organizations))
          "PUT uses :mainResponsibleOrganization; the GET-shape :organizations crashes the PUT validator")
      (is (not (contains? out :serviceChannels)))
      (is (= "lipas-x" (:sourceId out)))))

  (testing "nil-valued top-level fields are dropped"
    (let [out (normalize {:sourceId "x"
                          :subType nil
                          :generalDescriptionId nil
                          :responsibleSoteOrganization nil
                          :ontologyTerms []
                          :serviceClasses []
                          :targetGroups []
                          :areas []})]
      (is (not (contains? out :subType)))
      (is (not (contains? out :generalDescriptionId)))
      (is (not (contains? out :responsibleSoteOrganization))))))

(deftest upsert-adopts-with-adopted-source-id
  (testing "When :service-id is present, sourceId is forced to the adopted
            pattern (lipas-{org}-ptv-{service-id}) instead of the
            sub-category pattern. Reusing the sub-category sourceId on a
            service PTV originally created collides with previously
            soft-archived LIPAS services in PTV's database and crashes the
            PUT with a 500."
    (let [captured (atom nil)
          org-id "7fdd7f84-e52a-4c17-a59a-d7c2a3095ed5"
          service-id "51a9d7c6-b7ef-4b60-be9a-86b21610d8fe"]
      (with-redefs [ptv/get-service (fn [_ _ _]
                                      {:publishingStatus "Published"
                                       :ontologyTerms [{:uri "http://x"}]
                                       :serviceClasses [{:uri "http://y"}]
                                       :targetGroups [{:uri "http://z"}]
                                       :areas [{:type "Municipality" :municipalities [{:code "425"}]}]
                                       :serviceNames [{:type "Name" :language "fi" :value "Old"}]})
                    ptv/update-service-by-id (fn [_ _ data]
                                               (reset! captured data)
                                               data)]
        (ptv-core/upsert-ptv-service! :ptv-stub
                                      {:org-id org-id
                                       :service-id service-id
                                       :sub-category-id 4300
                                       :city-codes [425]
                                       :languages ["fi"]
                                       :source-id (ptv-data/->service-source-id org-id 4300)
                                       :summary {:fi "s"}
                                       :description {:fi "d"}})
        (is (= (ptv-data/->adopted-service-source-id org-id service-id)
               (:sourceId @captured))
            "sourceId is overridden to the adopted pattern")
        (is (not (re-find #"-4300$" (:sourceId @captured)))
            "the sub-category-style sourceId is not used")))))
