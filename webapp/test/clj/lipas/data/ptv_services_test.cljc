(ns lipas.data.ptv-services-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [lipas.data.ptv :as ptv-data]
            [lipas.data.ptv-review :as ptv-review]
            [lipas.data.ptv-services :as sut]))

(def ^:private org-id "org-1")

(def ^:private lipas-service
  "A service Lipas created, as ::subs/services gives it."
  {:service-id "S1"
   :label "Uimahallit"
   :source-id (ptv-data/->service-source-id org-id 3100)
   :management :lipas
   :name {:fi "Uimahallit"}
   :language-versions [:fi]
   :publishing-status "Published"
   :service-modified "2026-10-01T10:00:00"
   :summary {:fi "Tiivistelmä uimahalleista"}
   :description {:fi "Kuvaus uimahalleista, jossa on riittävästi tekstiä."}
   :user-instruction {:fi "Toimintaohje uimahalleihin."}
   :service-channels []
   :city-codes [889]})

(def ^:private native-service
  "A service made in PTV that Lipas hasn't written."
  {:service-id "S2"
   :label "Liikuntaneuvonta"
   :source-id nil
   :management :native
   :name {:fi "Liikuntaneuvonta"}
   :language-versions [:fi]
   :publishing-status "Published"
   :service-modified "2026-10-01T10:00:00"
   :summary {:fi "Neuvontaa liikkumiseen"}
   :description {:fi "Kuvaus neuvonnasta, jossa on riittävästi tekstiä."}
   :user-instruction {}
   :service-channels []
   :ptv-entries {:serviceNames [{:type "Name" :language "fi" :value "Liikuntaneuvonta"}]
                 :serviceDescriptions [{:type "Summary" :language "fi" :value "Neuvontaa liikkumiseen"}
                                       {:type "Description" :language "fi"
                                        :value "Kuvaus neuvonnasta, jossa on riittävästi tekstiä."}]
                 :publishingStatus "Published"}})

(defn- state
  [m]
  (sut/service-state (merge {:org-id org-id :org-languages ["fi"]} m)))

(deftest sync-status-test
  (testing "a service Lipas created and nobody has edited is up to date"
    (is (= :ok (:sync-status (state {:service lipas-service})))))

  (testing "an unsaved edit makes it ready to sync"
    (let [s (state {:service lipas-service
                    :descriptions {:summary {:fi "Uusi tiivistelmä uimahalleista"}}})]
      (is (:has-local-edits? s))
      (is (= :ready (:sync-status s)))))

  (testing "an edit PTV would reject blocks the sync, and doesn't read ready"
    (let [s (state {:service lipas-service
                    :descriptions {:summary {:fi "Lyh"}}})]
      (is (seq (:text-errors s)))
      (is (:invalid-texts? s))
      (is (= :blocked (:sync-status s)))))

  (testing "an edit in a language the org doesn't publish is no edit"
    (is (= :ok (:sync-status (state {:service lipas-service
                                     :descriptions {:summary {:en "Swimming halls"}}})))))

  (testing "PTV's unpublished draft blocks the sync, edits or not"
    (let [modified (assoc lipas-service :publishing-status "Modified")]
      (is (= :blocked (:sync-status (state {:service modified}))))
      (is (= :blocked (:sync-status (state {:service modified
                                            :descriptions {:summary {:fi "Uusi tiivistelmä"}}}))))))

  (testing "a field changed in both Lipas and PTV since Lipas's last write waits for a choice"
    (let [s (state {:service (assoc-in lipas-service [:summary :fi] "PTV muutti tämän tiivistelmän")
                    :descriptions {:summary {:fi "Lipas muutti tämän tiivistelmän"}}
                    :base {:summary {:fi "Tiivistelmä uimahalleista"}}})]
      (is (= :awaiting-choice (:sync-status s)))
      (is (= [[:summary "fi"]] (mapv :key (:open-rows s))))))

  (testing "a choice recorded for the conflict lets it sync"
    (let [args {:service (assoc-in lipas-service [:summary :fi] "PTV muutti tämän tiivistelmän")
                :descriptions {:summary {:fi "Lipas muutti tämän tiivistelmän"}}
                :base {:summary {:fi "Tiivistelmä uimahalleista"}}}
          row (first (:open-rows (state args)))
          choices {(:key row) (ptv-review/service-kept-choice row)}]
      (is (= :ready (:sync-status (state (assoc args :choices choices)))))))

  (testing "a service made in PTV that Lipas hasn't written has no status until edited"
    (is (nil? (:sync-status (state {:service native-service}))))
    (is (= :ready (:sync-status (state {:service native-service
                                        :descriptions {:summary {:fi "Uusi neuvontatiivistelmä"}}})))))

  (testing "once Lipas has written it (it has Lipas's sourceId), it is up to date"
    (let [adopted (assoc native-service :source-id (ptv-data/->adopted-service-source-id org-id "S2"))]
      (is (= :ok (:sync-status (state {:service adopted}))))))

  (testing "another system's service is read-only, and its sourceId doesn't break anything"
    (let [s (state {:service (assoc native-service :management :other-system :source-id "other-system-1")})]
      (is (= :other-system (:sync-status s)))
      (is (nil? (:review s))))))

(deftest parse-service-source-id-test
  (is (= 3100 (ptv-data/parse-service-source-id (ptv-data/->service-source-id org-id 3100))))
  (is (nil? (ptv-data/parse-service-source-id nil)))
  (is (nil? (ptv-data/parse-service-source-id "other-system-1"))))

(def ^:private text-gen
  (gen/fmap #(str "Teksti " %) gen/string-alphanumeric))

(defspec no-edits-nothing-to-send 100
  (prop/for-all [summary text-gen
                 description text-gen
                 modified? gen/boolean]
                (let [service (-> lipas-service
                                  (assoc :summary {:fi summary} :description {:fi description})
                                  (cond-> modified? (assoc :publishing-status "Modified")))
          ;; edits that repeat PTV's own texts send nothing either
                      s (state {:service service
                                :descriptions {:summary {:fi summary} :description {:fi description}}})]
                  (and (not (:has-local-edits? s))
                       (empty? (:open-rows s))
                       (= (if modified? :blocked :ok) (:sync-status s))))))

(deftest drop-written-edits-test
  (let [written {:serviceNames [{:type "Name" :language "fi" :value "Uimahallit"}]
                 :serviceDescriptions [{:type "Summary" :language "fi" :value "Uusi tiivistelmä"}
                                       {:type "Description" :language "fi" :value "Kuvaus"}]}]
    (testing "what the write sent is PTV's now"
      (is (nil? (sut/drop-written-edits {:summary {:fi "Uusi  tiivistelmä "}
                                         :name {:fi "Uimahallit"}
                                         :remove-languages [:en]}
                                        written))))
    (testing "what was typed during the write stays"
      (is (= {:summary {:fi "Uusi tiivistelmä, ja lisää"}}
             (sut/drop-written-edits {:summary {:fi "Uusi tiivistelmä, ja lisää"}
                                      :description {:fi "Kuvaus"}}
                                     written))))
    (testing "a text cleared in Lipas and absent in PTV is no edit"
      (is (nil? (sut/drop-written-edits {:user-instruction {:fi ""}} written))))))

(deftest service-channels-test
  (let [service {:service-channels [{:id "C1" :name "Uimahalli"}
                                    {:id "C2" :name "Ajanvaraus"}
                                    {:id "C3" :name "Asukaspuisto"}
                                    {:id "C4" :name "Toisen organisaation kanava"}]}
        org-channels {"C1" {:id "C1" :serviceChannelType "ServiceLocation"
                            :serviceChannelNames [{:language "fi" :value "Uimahalli"}]}
                      "C2" {:id "C2" :serviceChannelType "EChannel"}
                      "C3" {:id "C3" :serviceChannelType "ServiceLocation"}}
        {:keys [locations other-channels]} (sut/service-channels service org-channels {"C1" #{1}})]
    (testing "service locations, by name, with the Lipas sites whose location each is"
      (is (= ["Asukaspuisto" "Uimahalli"] (mapv :name locations)))
      (is (= [#{} #{1}] (mapv :lipas-ids locations))))
    (testing "the other channels with their types; another org's channel has none"
      (is (= [["Toisen organisaation kanava" nil] ["Ajanvaraus" "EChannel"]]
             (mapv (juxt :name :type) other-channels))))))

(deftest concerns-lipas-test
  (is (sut/concerns-lipas? lipas-service {}))
  (is (not (sut/concerns-lipas? native-service {})))
  (is (sut/concerns-lipas? (assoc native-service :source-id "lipas-org-1-ptv-S2") {}))
  (is (sut/concerns-lipas? native-service {:linked? true}))
  (testing "a Lipas site's service location among its channels in PTV"
    (is (sut/concerns-lipas? native-service {:lipas-location? true})))
  (testing "another system's sourceId alone doesn't make it Lipas's"
    (let [other (assoc native-service :management :other-system :source-id "other-system-1")]
      (is (not (sut/concerns-lipas? other {})))
      (is (sut/concerns-lipas? other {:linked? true})))))

(deftest sort-key-test
  (testing "what needs the user comes first, then the services Lipas created"
    (let [rows [{:id :plain-native :management :native}
                {:id :plain-lipas :management :lipas :sync-status :ok}
                {:id :ready :management :lipas :sync-status :ready}
                {:id :change-request :management :native :sync-status :ok :audit-status :changes-requested}
                {:id :blocked :management :lipas :sync-status :blocked}
                {:id :conflict :management :native :sync-status :awaiting-choice}]]
      (is (= [:conflict :blocked :change-request :ready :plain-lipas :plain-native]
             (mapv :id (sort-by sut/sort-key rows))))))
  (testing "an open row keeps the rank it was opened with"
    (is (= [4 0] (sut/sort-key {:management :lipas :sync-status :ready} 4)))))

(deftest matches-test
  (let [row {:label "Uimahallit" :sync-status :ok :locations []}]
    (is (sut/matches? row "" :all))
    (is (sut/matches? row "  uimahal " :all))
    (is (not (sut/matches? row "kirjasto" :all)))
    (is (sut/matches? row nil :ok))
    (is (not (sut/matches? row nil :ready)))
    (is (sut/matches? row nil :no-locations))
    (is (not (sut/matches? (assoc row :locations [{:id "C1"}]) nil :no-locations))))
  (testing "a service without a Finnish name is found by its other names"
    (is (sut/matches? {:label nil :name {:se "Simhallar"}} "simhall" :all))
    (is (= "Simhallar" (sut/display-name {:label nil :name {:se "Simhallar"}})))))
