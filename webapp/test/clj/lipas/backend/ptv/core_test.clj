(ns lipas.backend.ptv.core-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [lipas.backend.ptv.ai :as ai]
            [lipas.backend.ptv.core :as ptv-core]
            [lipas.backend.ptv.integration :as ptv]
            [lipas.data.ptv :as ptv-data]
            [lipas.data.types]))

(def ^:private strip-blanks ptv-data/strip-blank-localized-entries)
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
          :lifeEvents [{:uri "http://urn.fi/URN:NBN:fi:au:ptvl:v3017" :code "KE7"}]
          :areas [{:type "Region" :code "17" :municipalities [{:code "425"}]}]
          :requirements [{:language "fi" :value "Ikäraja 15 vuotta"}
                         {:language "sv" :value "Åldersgräns 15 år"}]
          :serviceCollections [{:id "c1" :name [{:value "Kokoelma" :language "fi"}]}]
          :serviceNames [{:type "Name" :language "fi" :value "Uimahalli"}
                         {:type "Name" :language "sv" :value "Simhall"}]
          :serviceDescriptions [{:type "Summary" :language "fi" :value "PTV fi"}
                                {:type "Description" :language "fi" :value "PTV kuvaus"}
                                {:type "Summary" :language "sv" :value "PTV sv"}
                                {:type "Description" :language "sv" :value "PTV beskrivning"}
                                {:type "ChargeTypeAdditionalInfo" :language "sv" :value "Avgift"}]}
         overrides))

(defn- with-ptv-stub
  "Runs `f` with PTV's GET returning `service` (or calling `get-service`,
  when given, as PTV's GET) and every PUT captured into the returned atom;
  the ptv_service write is stubbed out (exercised in
  lipas.backend.ptv.handler-test)."
  [service f & {:keys [get-service]}]
  (let [put (atom nil)
        persisted (atom nil)]
    (with-redefs [ptv/get-service (or get-service (fn [_ _ _] service))
                  ptv/update-service-by-id (fn [_ org-id id data]
                                             (reset! put [id data org-id])
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
          (with-ptv-stub (live-service :sourceId (ptv-data/->service-source-id test-org-id 4300))
            #(ptv-core/upsert-ptv-service!
               :db-stub {:get-config-by-ptv-org-id-fn (fn [_] {:supported-languages ["fi"]})}
               {:id "test-user"}
               {:org-id test-org-id
                :service-id test-service-id
                :source-id (ptv-data/->service-source-id test-org-id 4300)
                :sub-category-id 4300
                :city-codes [425]
                :summary {:fi "Tiivistelmä"} :description {:fi "Kuvaus"}}))]
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
      (let [{[addressed data org-id] :put} (sync! native {:name {:fi "Uimahalli"}
                                                          :summary {:fi "PTV fi"}
                                                          :description {:fi "PTV kuvaus"}})]
        (is (= test-service-id addressed))
        (is (= test-org-id org-id) "written as the org, which isn't put in the body")
        (is (= patched-source-id (:sourceId data)))
        (is (= (set (:serviceDescriptions native)) (set (:serviceDescriptions data))))
        (is (= (:requirements native) (:requirements data))
            "resent as PTV has them: a PUT without them clears them")))

    (testing "only what LIPAS writes and what PTV needs is sent; a PUT keeps
              every field it isn't sent, and GET shapes don't survive a
              write-back (classifications, areas, GET-only fields)"
      (let [{[_ data] :put} (sync! patched {:name {:fi "Uimahalli"}
                                            :summary {:fi "PTV fi"} :description {:fi "PTV kuvaus"}})]
        (is (= #{:sourceId :publishingStatus :serviceNames :serviceDescriptions :requirements
                 :targetGroups :ontologyTerms :serviceClasses}
               (set (keys data))))
        (is (= {:targetGroups ["http://z"] :ontologyTerms ["http://x"] :serviceClasses ["http://y"]}
               (select-keys data [:targetGroups :ontologyTerms :serviceClasses]))
            "without a general description PTV requires these, as URIs")
        (is (= "Published" (:publishingStatus data)))))

    (testing "with a general description, its id stands in for the ontology
              terms and service classes, which the GET lists merged with the
              description's (PTV counts all sent against the service's limits);
              the target groups still go along, PTV checks its rules on them"
      (let [{[_ data] :put} (sync! (live-service :sourceId patched-source-id :generalDescriptionId "gd-1")
                                   {:name {:fi "Uimahalli"}
                                    :summary {:fi "PTV fi"} :description {:fi "PTV kuvaus"}})]
        (is (= "gd-1" (:generalDescriptionId data)))
        (is (= ["http://z"] (:targetGroups data)))
        (is (not-any? #(contains? data %) [:ontologyTerms :serviceClasses :lifeEvents :areas]))))

    (testing "LIPAS's Finnish texts replace PTV's; Swedish, which LIPAS left
              alone, is kept with its PTV-only entry and the classification"
      (let [{[_ data] :put} (sync! patched {:name {:fi "Uimahalli"}
                                            :summary {:fi "Uusi tiivistelmä"}
                                            :description {:fi "Uusi kuvaus"}})]
        (is (= #{"fi" "sv"} (ptv-data/service-language-versions data)))
        (is (some #{{:type "Summary" :language "fi" :value "Uusi tiivistelmä"}} (:serviceDescriptions data)))
        (is (some #{{:type "ChargeTypeAdditionalInfo" :language "sv" :value "Avgift"}}
                  (:serviceDescriptions data)))
        (is (= ["http://x"] (:ontologyTerms data)))
        (is (not (contains? data :languages))
            "the provision languages are PTV's: not sent, so kept")))

    (testing "an explicitly removed language goes, PTV-only entries included"
      (let [{[_ data] :put} (sync! patched {:name {:fi "Uimahalli"}
                                            :summary {:fi "Tiivistelmä"} :description {:fi "Kuvaus"}
                                            :remove-languages ["se"]})]
        (is (= #{"fi"} (ptv-data/service-language-versions data)))
        (is (= [{:language "fi" :value "Ikäraja 15 vuotta"}] (:requirements data))
            "a requirement left in the language would keep its version alive")))

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

    (testing "a text under PTV's minimum length is refused before PTV, also
              one LIPAS only passes on as PTV has it"
      (is (= {:type :texts-too-short :problems [{:type "Summary" :language "fi"}]}
             (try (sync! patched {:name {:fi "Uimahalli"}
                                  :summary {:fi "Uusi"} :description {:fi "Uusi kuvaus"}})
                  nil
                  (catch clojure.lang.ExceptionInfo e (ex-data e)))))
      (is (= [{:type "ChargeTypeAdditionalInfo" :language "sv"}]
             (try (sync! (live-service :sourceId patched-source-id
                                       ;; its Swedish charge info shortened in PTV
                                       :serviceDescriptions
                                       (mapv #(cond-> %
                                                (= "ChargeTypeAdditionalInfo" (:type %))
                                                (assoc :value "Avg"))
                                             (:serviceDescriptions (live-service))))
                         {:name {:fi "Uimahalli"}
                          :summary {:fi "Uusi tiivistelmä"} :description {:fi "Uusi kuvaus"}})
                  nil
                  (catch clojure.lang.ExceptionInfo e (:problems (ex-data e)))))))

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
                     :summary {:fi "Tiivistelmä" :en "Summary"}
                     :description {:fi "Kuvaus" :en "Description"}}))
                (set (:languages @captured)))]
    (testing "a language the request claims but the org doesn't have is not published"
      (is (= #{"fi"} (save! ["fi"] ["fi" "en"]))))
    (testing "a language the org has enabled is published even if a stale client omits it"
      (is (= #{"fi" "en"} (save! ["fi" "en"] ["fi"]))))))

(deftest lipas-service-keeps-what-only-ptv-has
  ;; A PUT replaces names, descriptions and requirements whole, so a sync of a
  ;; service LIPAS created used to wipe what the org added to them in PTV.
  (let [source-id (ptv-data/->service-source-id test-org-id 4300)
        live (live-service :sourceId source-id
                           :serviceNames [{:type "Name" :language "fi" :value "Vanha nimi"}
                                          {:type "AlternativeName" :language "fi" :value "Hallit"}]
                           :serviceDescriptions [{:type "Summary" :language "fi" :value "Vanha tiivistelmä"}
                                                 {:type "ChargeTypeAdditionalInfo" :language "fi" :value "Maksu 5 euroa"}
                                                 {:type "ChargeTypeAdditionalInfo" :language "sv" :value "Avgift 5 euro"}]
                           :requirements [{:language "fi" :value "Ikäraja 15 vuotta"}])
        sync! (fn [service m]
                (with-ptv-stub service
                  #(ptv-core/upsert-ptv-service!
                     :db-stub {:get-config-by-ptv-org-id-fn (fn [_] {:supported-languages ["fi"]})}
                     {:id "test-user"}
                     (merge {:org-id test-org-id :source-id source-id :sub-category-id 4300
                             :city-codes [425]
                             :summary {:fi "Uusi tiivistelmä"} :description {:fi "Uusi kuvaus"}}
                            m))))
        refusal (fn [service m]
                  (try (sync! service m)
                       nil
                       (catch clojure.lang.ExceptionInfo e (ex-data e))))]
    (testing "with the service's id, PTV's own entries in the languages LIPAS
              publishes are carried over; the write is still by source-id"
      (let [{[addressed data] :put} (sync! live {:service-id test-service-id})]
        (is (= source-id addressed))
        (is (not-any? #(contains? data %) ptv-data/create-only-service-fields)
            "producers, area and funding are set only on creation")
        (is (= ["http://z"] (:targetGroups data)) "PTV's own target groups")
        (is (some #{{:type "AlternativeName" :language "fi" :value "Hallit"}} (:serviceNames data)))
        (is (some #{{:type "ChargeTypeAdditionalInfo" :language "fi" :value "Maksu 5 euroa"}}
                  (:serviceDescriptions data)))
        (is (= [{:language "fi" :value "Ikäraja 15 vuotta"}] (:requirements data)))
        (is (= #{"fi"} (ptv-data/service-language-versions data))
            "the Swedish charge info would keep a version LIPAS doesn't publish alive")
        (is (some #{{:type "Summary" :language "fi" :value "Uusi tiivistelmä"}} (:serviceDescriptions data)))
        (is (not-any? #(= "Vanha tiivistelmä" (:value %)) (:serviceDescriptions data))
            "LIPAS's own texts replace PTV's, as before")))

    (testing "a service PTV no longer has is written as before, nothing carried over"
      (let [{[addressed data] :put}
            (with-ptv-stub nil
              #(ptv-core/upsert-ptv-service!
                 :db-stub {:get-config-by-ptv-org-id-fn (fn [_] {:supported-languages ["fi"]})}
                 {:id "test-user"}
                 {:org-id test-org-id :source-id source-id :service-id test-service-id
                  :sub-category-id 4300 :city-codes [425]
                  :summary {:fi "Uusi tiivistelmä"} :description {:fi "Uusi kuvaus"}})
              :get-service (fn [_ _ _] (throw (ex-info "HTTP Error: 404" {:resp {:status 404}}))))]
        (is (= source-id addressed))
        (is (not (contains? data :requirements)))))

    (testing "a service PTV doesn't know yet is created with LIPAS's full defaults"
      (let [created (atom nil)]
        (with-redefs [ptv/get-service (fn [_ _ _] (throw (ex-info "HTTP Error: 404" {:resp {:status 404}})))
                      ptv/update-service (fn [_ _ _] (throw (ex-info "HTTP Error: 404" {:resp {:status 404}})))
                      ptv/create-service (fn [_ data] (reset! created data) data)
                      ptv-core/persist-ptv-service-revision! (fn [& _])]
          (ptv-core/upsert-ptv-service!
            :db-stub {:get-config-by-ptv-org-id-fn (fn [_] {:supported-languages ["fi"]})}
            {:id "test-user"}
            {:org-id test-org-id :source-id source-id :service-id test-service-id
             :sub-category-id 4300 :city-codes [425]
             :summary {:fi "Uusi tiivistelmä"} :description {:fi "Uusi kuvaus"}}))
        (is (every? #(contains? @created %) ptv-data/create-only-service-fields))
        (is (= [{:provisionType "SelfProducedServices" :organizations [test-org-id]}]
               (:serviceProducers @created)))
        (is (= ["http://uri.suomi.fi/codelist/ptv/ptvkohderyhmat/code/KR1"] (:targetGroups @created)))))

    (testing "an id of another service is refused before anything is written"
      (is (= "lipas-other-1"
             (:live-source-id (refusal (live-service :sourceId "lipas-other-1")
                                       {:service-id test-service-id})))))

    (testing "a service changed in PTV since the client loaded it is refused: the
              client sends PTV's texts as they were then"
      (is (= :changed-in-ptv
             (:type (refusal live {:service-id test-service-id
                                   :expected-modified "2026-10-01T00:00:00"}))))
      (is (some? (:put (sync! live {:service-id test-service-id
                                    :expected-modified (:modified live)})))
          "unchanged since: written"))

    (testing "a text under PTV's minimum length is refused before PTV, carried-over ones included"
      (is (= {:type :texts-too-short :problems [{:type "Summary" :language "fi"}]}
             (refusal live {:service-id test-service-id :summary {:fi "Uusi"}})))
      (is (= [{:type "Requirement" :language "fi"}]
             (:problems (refusal (assoc live :requirements [{:language "fi" :value "Ei"}])
                                 {:service-id test-service-id})))))))

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

;;; fit-translation ;;;

(defn- chars-of [n] (apply str (repeat n "a")))

(def ^:private fi->en {:from "fi" :to #{"en"}})

(def ^:private over-long-en
  ;; 163 characters, a sentence ending at 101
  (str (chars-of 100) ". " (chars-of 60) "."))

(deftest fit-translation-test
  ;; A faithful translation can run over PTV's limits. The limits win, but
  ;; only the steps that may lose something are reported to the user.
  (testing "within the limits nothing is asked or changed"
    (with-redefs [ai/shorten-summaries (fn [& _] (throw (ex-info "must not be called" {})))]
      (is (= {:summary {:fi "s" :en "t"}}
             (ptv-core/fit-translation fi->en {:summary {:fi "s" :en "t"}})))))

  (testing "rewording that fits keeps every fact, so it isn't reported"
    (with-redefs [ai/shorten-summaries (fn [_ & {:keys [mode]}]
                                         (case mode :wording {"0/en" "reworded"}))]
      (is (= {:summary {:fi "s" :en "reworded"}}
             (ptv-core/fit-translation fi->en {:summary {:fi "s" :en over-long-en}})))))

  (testing "condensing, when rewording isn't enough, is reported"
    (with-redefs [ai/shorten-summaries (fn [_ & {:keys [mode]}]
                                         (case mode
                                           :wording {"0/en" (subs over-long-en 1)}
                                           :condense {"0/en" "condensed"}))]
      (is (= {:summary {:fi "s" :en "condensed"}
              :shortened [{:field :summary :lang "en"}]}
             (ptv-core/fit-translation fi->en {:summary {:fi "s" :en over-long-en}})))))

  (testing "when both calls fail the text is cut after its last whole sentence"
    (with-redefs [ai/shorten-summaries (fn [& _] (throw (ex-info "LLM down" {})))]
      (is (= {:summary {:fi "s" :en (str (chars-of 100) ".")}
              :shortened [{:field :summary :lang "en"}]}
             (ptv-core/fit-translation fi->en {:summary {:fi "s" :en over-long-en}})))))

  (testing "the source language is the user's own text and stays as written"
    (with-redefs [ai/shorten-summaries (fn [& _] (throw (ex-info "must not be called" {})))]
      (is (= {:summary {:fi over-long-en :en "t"}}
             (ptv-core/fit-translation fi->en {:summary {:fi over-long-en :en "t"}})))))

  (testing "descriptions are held to their limit too"
    (let [long-description (str (chars-of 4990) ". " (chars-of 20) ".")]
      (is (= {:description {:fi "d" :en (str (chars-of 4990) ".")}
              :shortened [{:field :description :lang "en"}]}
             (ptv-core/fit-translation fi->en {:description {:fi "d" :en long-description}}))))))

(defspec cut-to-limit-fits-and-keeps-the-start 200
  (prop/for-all [words (gen/vector (gen/elements ["Latu" "on" "auki." "Reitillä" "kissa." "ja,"
                                                  "1,7" "km!" "Onko?" "talvella" "–"])
                                   0 60)
                 limit (gen/choose 1 150)]
                (let [s (str/join " " words)
                      cut (ptv-core/cut-to-limit s limit)]
                  (and (<= (count cut) limit)
                       (str/starts-with? s cut)
                       (or (> (count s) limit) (= s cut))))))
