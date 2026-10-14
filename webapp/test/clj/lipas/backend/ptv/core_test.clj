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

(def ^:private test-org-id "7fdd7f84-e52a-4c17-a59a-d7c2a3095ed5")
(def ^:private test-service-id "51a9d7c6-b7ef-4b60-be9a-86b21610d8fe")

(defn- live-service
  "A PTV GET response for the test service, made in PTV (no sourceId)."
  [& {:as overrides}]
  (merge {:id test-service-id
          :modified "2026-10-14T10:00:00"
          :publishingStatus "Published"
          :organizations [{:organization {:id test-org-id} :roleType "Responsible"}]
          :languages ["fi"]
          :ontologyTerms [{:uri "http://x"}]
          :serviceClasses [{:uri "http://y"}]
          :targetGroups [{:uri "http://z"}]
          :areas [{:type "Municipality" :municipalities [{:code "425"}]}]
          :serviceNames [{:type "Name" :language "fi" :value "Uimahalli"}
                         {:type "Name" :language "sv" :value "Simhall"}]
          :serviceDescriptions [{:type "Summary" :language "fi" :value "PTV fi"}
                                {:type "Description" :language "fi" :value "PTV kuvaus"}
                                {:type "Summary" :language "sv" :value "PTV sv"}
                                {:type "Description" :language "sv" :value "PTV beskrivning"}
                                {:type "ChargeTypeAdditionalInfo" :language "sv" :value "Avgift"}]}
         overrides))

(defn- with-ptv-stub
  "Runs `f` with PTV's GET returning `service` and every PUT captured into
  the returned atom; the ptv_service write is stubbed out (exercised in
  lipas.backend.ptv.handler-test)."
  [service f]
  (let [put (atom nil)
        persisted (atom nil)]
    (with-redefs [ptv/get-service (fn [_ _ _] service)
                  ptv/update-service-by-id (fn [_ id data]
                                             (reset! put [id data])
                                             (assoc data :id id :modified "2026-10-14T11:00:00"))
                  ptv/update-service (fn [_ source-id data]
                                       (reset! put [source-id data])
                                       data)
                  ptv-core/persist-ptv-service-revision! (fn [& args] (reset! persisted args))]
      (f))
    {:put @put :persisted @persisted}))

(def ^:private patched-source-id
  (ptv-data/->adopted-service-source-id test-org-id test-service-id))

(deftest upsert-mode-is-explicit
  (testing "a service-id without mode \"native\" never addresses a service
            by id: the presence of an id is not a mode switch"
    (let [{[addressed data] :put}
          (with-ptv-stub (live-service)
            #(ptv-core/upsert-ptv-service!
               :db-stub {:get-config-by-ptv-org-id-fn (fn [_] {:supported-languages ["fi"]})}
               {:id "test-user"}
               {:org-id test-org-id
                :service-id test-service-id
                :source-id (ptv-data/->service-source-id test-org-id 4300)
                :sub-category-id 4300
                :city-codes [425]
                :summary {:fi "s"} :description {:fi "d"}}))]
      (is (= (ptv-data/->service-source-id test-org-id 4300) addressed))
      (is (= addressed (:sourceId data))))))

(deftest patch-native-service-test
  (let [sync! (fn [service m]
                (with-ptv-stub service
                  #(ptv-core/upsert-ptv-service!
                     :db-stub :ptv-stub {:id "test-user"}
                     (merge {:org-id test-org-id :service-id test-service-id
                             :mode "native"
                             :expected-modified (:modified service)}
                            m))))
        native (live-service)
        patched (live-service :sourceId patched-source-id)]
    (testing "the first patch gives the service LIPAS's sourceId and changes
              nothing but the texts LIPAS sent"
      (let [{[addressed data] :put} (sync! native {:name {:fi "Uimahalli"}
                                                   :summary {:fi "PTV fi"}
                                                   :description {:fi "PTV kuvaus"}})]
        (is (= test-service-id addressed))
        (is (= patched-source-id (:sourceId data)))
        (is (= (dissoc (normalize native) :sourceId :serviceNames :serviceDescriptions)
               (dissoc data :sourceId :mainResponsibleOrganization :serviceNames :serviceDescriptions)))
        (is (= (set (:serviceDescriptions native)) (set (:serviceDescriptions data))))))

    (testing "LIPAS's Finnish texts replace PTV's; Swedish, which LIPAS left
              alone, is kept with its PTV-only entry and the classification"
      (let [{[_ data] :put} (sync! patched {:name {:fi "Uimahalli"}
                                            :summary {:fi "Uusi"}
                                            :description {:fi "Uusi kuvaus"}})]
        (is (= #{"fi" "sv"} (ptv-data/service-language-versions data)))
        (is (some #{{:type "Summary" :language "fi" :value "Uusi"}} (:serviceDescriptions data)))
        (is (some #{{:type "ChargeTypeAdditionalInfo" :language "sv" :value "Avgift"}}
                  (:serviceDescriptions data)))
        (is (= ["http://x"] (:ontologyTerms data)))
        (is (= ["fi"] (:languages data)))))

    (testing "an explicitly removed language goes, PTV-only entries included"
      (let [{[_ data] :put} (sync! patched {:name {:fi "Uimahalli"}
                                            :summary {:fi "s"} :description {:fi "d"}
                                            :remove-languages ["se"]})]
        (is (= #{"fi"} (ptv-data/service-language-versions data)))))

    (testing "a version that would be left incomplete is refused before PTV"
      (let [result (try (sync! patched {:name {:fi "Uimahalli" :se "Simhall"}
                                        :summary {:fi "s" :se ""}
                                        :description {:fi "d" :se "d"}})
                        (catch clojure.lang.ExceptionInfo e (ex-data e)))]
        (is (= :incomplete-language-versions (:type result)))
        (is (= {"sv" ["Summary"]} (:problems result)))))

    (testing "a service another system maintains (its own sourceId) is refused"
      (is (= :source-id-in-use
             (try (sync! (live-service :sourceId "kunta-erp-123") {:summary {:fi "s"}})
                  nil
                  (catch clojure.lang.ExceptionInfo e (:type (ex-data e)))))))

    (testing "a service edited in PTV since it was loaded is refused"
      (is (= :changed-in-ptv
             (try (sync! patched {:summary {:fi "s"}
                                  :expected-modified "2026-10-01T00:00:00"})
                  nil
                  (catch clojure.lang.ExceptionInfo e (:type (ex-data e)))))))))

(deftest upsert-service-uses-live-org-languages
  ;; The request's :languages is the browser's cached copy of the org config;
  ;; the live config wins, as in the service-location sync.
  (let [captured (atom nil)
        org-id "org-x"
        save! (fn [live-langs request-langs]
                (with-redefs [ptv/update-service (fn [_ _ data]
                                                   (reset! captured data)
                                                   data)
                              ptv-core/persist-ptv-service-revision! (fn [& _])]
                  (ptv-core/upsert-ptv-service!
                    :db-stub
                    {:get-config-by-ptv-org-id-fn (fn [_] {:supported-languages live-langs})}
                    {:id "test-user"}
                    {:org-id org-id
                     :sub-category-id 4300
                     :city-codes [425]
                     :languages request-langs
                     :summary {:fi "s" :en "s-en"}
                     :description {:fi "d" :en "d-en"}}))
                (set (:languages @captured)))]
    (testing "a language the request claims but the org doesn't have is not published"
      (is (= #{"fi"} (save! ["fi"] ["fi" "en"]))))
    (testing "a language the org has enabled is published even if a stale client omits it"
      (is (= #{"fi" "en"} (save! ["fi" "en"] ["fi"]))))))

(deftest select-translation-test
  ;; The model's response schema makes it return every language and a
  ;; user-instruction even when neither was asked for. Before, all of it was
  ;; merged into the site: translating fi→en overwrote existing Swedish texts
  ;; and invented a (Finnish!) user instruction.
  (let [model-output {:summary {:fi "rewritten fi" :se "model sv" :en "model en"}
                      :description {:fi "rewritten fi" :se "model sv" :en "model en"}
                      :user-instruction {:fi "invented" :se "invented" :en "invented"}}]
    (testing "keeps only the target languages and the source texts as written"
      (is (= {:summary {:fi "source summary" :en "model en"}
              :description {:fi "source description" :en "model en"}}
             (ptv-core/select-translation {:from "fi" :to #{"en"}
                                           :summary "source summary"
                                           :description "source description"}
                                          model-output))))

    (testing "user-instruction is translated only when the source had one"
      (is (= {:fi "source ui" :se "model ui sv" :en "model ui en"}
             (:user-instruction
               (ptv-core/select-translation {:from "fi" :to #{"se" "en"}
                                             :summary "s" :description "d"
                                             :user-instruction "source ui"}
                                            (assoc model-output :user-instruction
                                                   {:fi "x" :se "model ui sv" :en "model ui en"}))))))

    (testing "a missing target language stays missing rather than becoming nil text"
      (is (= {:fi "s"}
             (:summary (ptv-core/select-translation {:from "fi" :to #{"en"}
                                                     :summary "s" :description "d"}
                                                    {:summary {:se "only sv"}})))))))
