(ns lipas.data.ptv-services-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
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

(def ^:private written
  "Lipas's mirror of lipas-service after its last write, which PTV still
   has as written."
  {:name {:fi "Uimahallit"}
   :summary {:fi "Tiivistelmä uimahalleista"}
   :description {:fi "Kuvaus uimahalleista, jossa on riittävästi tekstiä."}
   :user-instruction {:fi "Toimintaohje uimahalleihin."}
   :languages ["fi"]
   :ptv-modified "2026-10-01T10:00:00"
   :last-sync "2026-10-01T10:00:02.000Z"})

(defn- edited-in-ptv
  "`service` as PTV has it after `f` changed its texts ({field {locale
   text}}) there: its :modified moves on."
  [service f]
  (let [texts (f (select-keys service [:name :summary :description :user-instruction]))]
    (-> (merge service texts)
        (assoc :label (get-in texts [:name :fi]) :service-modified "2026-10-02T09:00:00"))))

(defn- state
  [m]
  (sut/service-state (merge {:org-id org-id :org-languages ["fi"]} m)))

(defn- take-rows
  "`args` of service-state after the user took `side`'s version of `rows`,
   as ::events/service-review-take does."
  [args rows side]
  (let [writes (sut/review-writes rows side)]
    (-> args
        (update :descriptions sut/apply-review-writes writes)
        (update :choices sut/review-choices rows side))))

(defn- origins [s]
  (into {} (map (juxt :key :origin)) (:rows (:review s))))

(deftest sync-status-test
  (testing "a service Lipas created and nobody has edited is up to date"
    (is (= :ok (:sync-status (state {:service lipas-service :base written})))))

  (testing "an unsaved edit makes it ready to sync"
    (let [s (state {:service lipas-service
                    :base written
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
    (let [s (state {:service (edited-in-ptv lipas-service #(assoc-in % [:summary :fi] "PTV muutti tämän tiivistelmän"))
                    :descriptions {:summary {:fi "Lipas muutti tämän tiivistelmän"}}
                    :base written})]
      (is (= :awaiting-choice (:sync-status s)))
      (is (= {[:summary "fi"] :both} (origins s)))
      (is (= [[:summary "fi"]] (mapv :key (:open-rows s))))))

  (testing "a choice recorded for the conflict lets it sync"
    (let [args {:service (edited-in-ptv lipas-service #(assoc-in % [:summary :fi] "PTV muutti tämän tiivistelmän"))
                :descriptions {:summary {:fi "Lipas muutti tämän tiivistelmän"}}
                :base written}
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

(deftest change-made-in-ptv-test
  (let [changed (edited-in-ptv lipas-service #(assoc-in % [:summary :fi] "PTV:ssä kirjoitettu tiivistelmä"))
        args {:service changed :base written}
        s (state args)
        row (first (:rows (:review s)))]
    (testing "a text changed only in PTV is a change to decide, not taken over"
      (is (= {[:summary "fi"] :ptv} (origins s)))
      (is (= :awaiting-choice (:sync-status s)))
      (is (= [[:summary "fi"]] (mapv :key (:open-rows s))))
      (is (= :lipas (:current row)))
      (is (= "Tiivistelmä uimahalleista" (:lipas row)))
      (is (= "PTV:ssä kirjoitettu tiivistelmä" (:ptv row))))

    (testing "the form and a sync have Lipas's version until PTV's is taken"
      (is (= "Tiivistelmä uimahalleista" (get-in s [:texts :summary :fi])))
      (is (= "Tiivistelmä uimahalleista" (get-in s [:data :summary :fi])))
      (is (:has-local-edits? s)))

    (testing "taking PTV's version leaves nothing to send, but PTV's version to record"
      (let [taken (state (take-rows args [row] :ptv))
            row' (first (:rows (:review taken)))]
        (is (= "PTV:ssä kirjoitettu tiivistelmä" (get-in taken [:texts :summary :fi])))
        (is (= [:ptv :ptv] [(:origin row') (:current row')]) "the row stays, decided")
        (is (empty? (:open-rows taken)))
        (is (not (:has-local-edits? taken)))
        (is (:take-ptv-version? taken))
        (is (= :ready (:sync-status taken)))

        (testing "and keeping Lipas's after all brings Lipas's version back"
          (let [args' (take-rows (take-rows args [row] :ptv) [row'] :lipas)
                kept (state args')]
            (is (= "Tiivistelmä uimahalleista" (get-in kept [:texts :summary :fi])))
            (is (empty? (:open-rows kept)))
            (is (not (:take-ptv-version? kept)))
            (is (= :ready (:sync-status kept)))))))

    (testing "keeping Lipas's version is recorded, and the sync sends it"
      (let [kept (state (take-rows args [row] :lipas))]
        (is (empty? (:open-rows kept)))
        (is (= "Tiivistelmä uimahalleista" (get-in kept [:data :summary :fi])))
        (is (:has-local-edits? kept))
        (is (= :ready (:sync-status kept)))
        (testing "until PTV changes the text again"
          (let [again (state (assoc (take-rows args [row] :lipas)
                                    :service (edited-in-ptv changed #(assoc-in % [:summary :fi] "PTV:ssä taas muutettu"))))]
            (is (= [[:summary "fi"]] (mapv :key (:open-rows again))))))))

    (testing "a name changed in PTV is asked about too, and the form shows Lipas's"
      (let [s (state {:service (edited-in-ptv lipas-service #(assoc-in % [:name :fi] "Uimahallit (PTV)"))
                      :base written})]
        (is (= {[:name "fi"] :ptv} (origins s)))
        (is (= "Uimahallit" (get-in s [:name-data :fi])))
        (is (= "Uimahallit" (get-in s [:data :name :fi])))))

    (testing "without evidence of an edit in PTV, PTV's text is Lipas's version"
      ;; e.g. a text PTV stores differently from what it answered to the
      ;; write: its :modified is still the write's
      (let [s (state {:service (assoc-in lipas-service [:summary :fi] "Tiivistelmä uimahalleista.")
                      :base written})]
        (is (empty? (:rows (:review s))))
        (is (= "Tiivistelmä uimahalleista." (get-in s [:texts :summary :fi])))
        (is (= :ok (:sync-status s)))))

    (testing "a mirror from before PTV's :modified was stored: a change over 2 minutes after the write"
      (let [old-base (dissoc written :ptv-modified)
            at (fn [modified] (state {:service (assoc changed :service-modified modified) :base old-base}))]
        (is (= :ptv (get (origins (at "2026-10-01T12:00:00")) [:summary "fi"])))
        (is (empty? (:rows (:review (at "2026-10-01T10:01:00")))))))

    (testing "a language version added in PTV since Lipas's write is PTV's: nothing to decide"
      (let [s (state {:service (edited-in-ptv lipas-service #(-> %
                                                                 (assoc-in [:name :en] "Swimming halls")
                                                                 (assoc-in [:summary :en] "Swimming halls in Utajärvi")
                                                                 (assoc-in [:description :en] "Swimming halls, described at length.")))
                      :org-languages ["fi" "en"]
                      :base written})]
        (is (empty? (:rows (:review s))))
        (is (= "Swimming halls in Utajärvi" (get-in s [:data :summary :en])))
        (is (= :ok (:sync-status s)))))

    (testing "a service Lipas hasn't written shows PTV's texts, which have nothing to decide"
      (let [s (state {:service (assoc native-service :service-modified "2026-10-05T10:00:00")})]
        (is (= "Neuvontaa liikkumiseen" (get-in s [:texts :summary :fi])))
        (is (empty? (:rows (:review s))))))))

(deftest native-change-made-in-ptv-test
  (let [adopted (assoc native-service
                       :source-id (ptv-data/->adopted-service-source-id org-id "S2")
                       :name {:fi "Liikuntaneuvonta"})
        base {:name {:fi "Liikuntaneuvonta" :en "Exercise counselling"}
              :summary {:fi "Neuvontaa liikkumiseen" :en "Counselling on exercise"}
              :description {:fi "Kuvaus neuvonnasta, jossa on riittävästi tekstiä."
                            :en "Exercise counselling, described at length."}
              :ptv-modified "2026-10-01T10:00:00"}]
    (testing "a language version PTV removed since Lipas's write is asked about, as a sync would bring it back"
      (let [s (state {:service (assoc adopted :service-modified "2026-10-03T08:00:00") :base base})]
        (is (contains? (set (:edit-languages s)) "en"))
        (is (= {[:name "en"] :ptv [:summary "en"] :ptv [:description "en"] :ptv} (origins s)))
        (is (= :awaiting-choice (:sync-status s)))
        (testing "taking PTV's version leaves it out of the sync"
          (let [args {:service (assoc adopted :service-modified "2026-10-03T08:00:00") :base base}
                taken (state (take-rows args (:open-rows s) :ptv))]
            (is (empty? (:open-rows taken)))
            (is (str/blank? (get-in taken [:data :summary :en])))
            (is (:take-ptv-version? taken))))))))

(deftest last-modified-in-test
  (is (= :lipas (:last-modified-in (state {:service lipas-service :base written}))))
  (is (= :ptv (:last-modified-in (state {:service (assoc lipas-service :service-modified "2026-10-02T09:00:00")
                                         :base written}))))
  (testing "PTV's version recorded as Lipas's: the last change was PTV's"
    (is (= :ptv (:last-modified-in (state {:service lipas-service
                                           :base (assoc written :read-from-ptv true)})))))
  (testing "a service Lipas hasn't written"
    (is (= :ptv (:last-modified-in (state {:service native-service}))))))

(deftest review-writes-test
  (let [rows [{:key [:summary "fi"] :field :summary :locale :fi :ptv "PTV:n" :current :lipas}
              {:key [:description "fi"] :field :description :locale :fi :ptv nil :current :lipas}
              {:key [:name "fi"] :field :name :locale :fi :ptv "Nimi" :current :ptv}]]
    (testing "taking PTV's writes its texts, \"\" for none"
      (is (= {[:summary :fi] "PTV:n" [:description :fi] "" [:name :fi] "Nimi"}
             (sut/review-writes rows :ptv))))
    (testing "keeping Lipas's drops the edit only where it holds PTV's"
      (is (= {[:name :fi] nil} (sut/review-writes rows :lipas))))
    (testing "a dropped edit leaves no empty field behind"
      (is (= {:summary {:fi "Oma"}}
             (sut/apply-review-writes {:summary {:fi "Oma"} :name {:fi "Nimi"}} {[:name :fi] nil}))))
    (testing "undoing a take brings back what the edit had before, or nothing"
      (let [before {:summary {:fi "Oma"}}
            writes (sut/review-writes rows :ptv)
            undo (ptv-review/record-review-writes nil before writes)
            after (sut/apply-review-writes before writes)]
        (is (= before (sut/apply-review-writes after (ptv-review/undo-review-writes undo after))))))))

(def ^:private change-gen
  "Who changed a text since Lipas's last write: nobody, PTV, Lipas (an
   unsaved edit), both differently, or both alike."
  (gen/elements [:none :ptv :lipas :both :same]))

(def ^:private reviewed-fields [:summary :description :user-instruction])

(defn- scenario
  "service-state args for `changes` ({field change}) to lipas-service over
   its `written` base."
  [changes]
  (let [ptv-text #(str "PTV:n teksti, " (name %) ", riittävän pitkä")
        lipas-text #(str "Lipaksen teksti, " (name %) ", riittävän pitkä")
        ptv-changes (into {} (for [[f c] changes :when (#{:ptv :both :same} c)]
                               [f (if (= :same c) (lipas-text f) (ptv-text f))]))
        edits (into {} (for [[f c] changes :when (#{:lipas :both :same} c)]
                         [f {:fi (lipas-text f)}]))]
    {:service (if (seq ptv-changes)
                (edited-in-ptv lipas-service #(reduce-kv (fn [t f v] (assoc-in t [f :fi] v)) % ptv-changes))
                lipas-service)
     :base written
     :descriptions edits}))

(defspec origin-follows-who-changed-it 200
  (prop/for-all [changes (gen/map (gen/elements reviewed-fields) change-gen)]
                (let [s (state (scenario changes))
                      by-field (into {} (map (juxt :field identity)) (:rows (:review s)))
                      open (set (map :field (:open-rows s)))]
                  (every? (fn [f]
                            (let [row (get by-field f)]
                              (case (get changes f :none)
                                :none (nil? row)
                                :ptv (and (= [:ptv :lipas] [(:origin row) (:current row)]) (open f))
                                :lipas (and (= [:lipas :lipas] [(:origin row) (:current row)]) (not (open f)))
                                :both (and (= [:both :lipas] [(:origin row) (:current row)]) (open f))
                                ;; both made the same text: PTV's, decided
                                :same (and (= [:ptv :ptv] [(:origin row) (:current row)]) (not (open f))))))
                          reviewed-fields))))

(defspec taking-every-ptv-change-sends-only-lipas-own 200
  (prop/for-all [changes (gen/map (gen/elements reviewed-fields) change-gen)]
                (let [args (scenario changes)
                      s (state (take-rows args (:open-rows (state args)) :ptv))
                      lipas-only? (some #{:lipas} (vals changes))
                      ptv-change? (some #{:ptv :both :same} (vals changes))]
                  (and (empty? (:open-rows s))
                       (= (boolean lipas-only?) (:has-local-edits? s))
                       (= (boolean (and ptv-change? (not lipas-only?))) (:take-ptv-version? s))
                       (= (if (or lipas-only? ptv-change?) :ready :ok) (:sync-status s))
                       ;; what PTV changed is what a sync sends
                       (every? (fn [[f c]]
                                 (case c
                                   (:ptv :both :same) (= (get-in s [:data f :fi]) (get-in args [:service f :fi]))
                                   true))
                               changes)))))

(defspec keeping-lipas-sends-lipas-version 200
  (prop/for-all [changes (gen/map (gen/elements reviewed-fields) change-gen)]
                (let [args (scenario changes)
                      s (state (take-rows args (:open-rows (state args)) :lipas))]
                  (and (empty? (:open-rows s))
                       (every? (fn [[f c]]
                                 (= (get-in s [:data f :fi])
                                    (case c
                                      (:lipas :both :same) (get-in args [:descriptions f :fi])
                                      (get-in written [f :fi]))))
                               changes)))))

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
        {:keys [locations] :as channels} (sut/service-channels service org-channels {"C1" #{1}})]
    (testing "service locations, by name, with the Lipas sites whose location each is"
      (is (= ["Asukaspuisto" "Uimahalli"] (mapv :name locations)))
      (is (= [#{} #{1}] (mapv :lipas-ids locations))))
    (testing "only service locations: other channel types and another org's channel are left out"
      (is (= [:locations] (keys channels))))))

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
