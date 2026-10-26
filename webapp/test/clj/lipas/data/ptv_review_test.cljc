(ns lipas.data.ptv-review-test
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [lipas.data.ptv :as ptv-data]
            [lipas.data.ptv-review :as sut]))

(def ^:private langs ["fi" "se" "en"])

(def ^:private site
  {:lipas-id 1
   :name "Halli"
   :ptv {:source-id "lipas-src-1"
         :last-sync "T1"
         :languages ["fi" "se" "en"]
         :summary {:fi "summary-fi" :en "summary-en"}
         :description {:fi "desc-fi"}
         :service-ids ["svc-a"]
         :service-channel-ids ["C1"]}})

(def ^:private channel
  {:id "C1"
   :sourceId "lipas-src-1"
   :serviceChannelNames [{:type "Name" :language "fi" :value "Halli"}
                         {:type "Name" :language "sv" :value "KUNTA-Hall"}
                         {:type "Name" :language "en" :value "Halli"}]
   :serviceChannelDescriptions [{:type "Summary" :language "fi" :value "summary-fi"}
                                {:type "Summary" :language "sv" :value "kunta-summary-sv"}
                                {:type "Summary" :language "en" :value "summary-en"}
                                {:type "Description" :language "fi" :value "desc-fi"}
                                {:type "Description" :language "sv" :value "desc-fi"}
                                {:type "Description" :language "en" :value "desc-fi"}]
   :services [{:service {:id "svc-a"}}
              {:service {:id "svc-b"}}]})

(def ^:private services
  {"svc-a" {:serviceNames [{:type "Name" :language "fi" :value "Pallokentät"}]}
   "svc-b" {:serviceNames [{:type "Name" :language "fi" :value "Liikuntahallit"}]}})

(defn- row [review k]
  (some #(when (= k (:key %)) %) (:rows review)))

(deftest sync-review-without-base-test
  (testing "nil until the channel is fetched"
    (is (nil? (sut/sync-review site nil nil services langs))))

  (let [review (sut/sync-review site nil channel services langs)]
    (testing "without a base, who changed what is unknown"
      (is (false? (:base? review)))
      (is (every? #(= :unknown (:origin %)) (:rows review))))

    (testing "a language Lipas doesn't publish (no sv texts) has no text rows: the sync removes it"
      (is (not-any? #(and (= "sv" (:language %)) (not= :languages (:field %))) (:rows review)))
      (is (= {:field :languages :kind :languages :locale :se}
             (select-keys (row review [:languages "sv"]) [:field :kind :locale])))
      (is (not (sut/review-choice-required? (row review [:languages "sv"]))) "information only"))

    (let [review (sut/sync-review (-> site
                                      (assoc-in [:ptv :summary :se] "summary-sv")
                                      (assoc-in [:ptv :description :se] "desc-fi"))
                                  nil channel services langs)]
      (testing "an sv name edited in PTV; Lipas sends the Finnish name for it"
        (is (= {:lipas "Halli" :ptv "KUNTA-Hall" :base nil}
               (select-keys (row review [:name "sv"]) [:lipas :ptv :base]))))

      (testing "an sv summary edited in PTV"
        (is (= ["summary-sv" "kunta-summary-sv"]
               ((juxt :lipas :ptv) (row review [:summary "sv"]))))))

    (testing "a connection made in PTV carries the service's name"
      (is (= {:field :services :id "svc-b" :name "Liikuntahallit" :lipas? false :ptv? true}
             (select-keys (row review [:services "svc-b"])
                          [:field :id :name :lipas? :ptv?]))))

    (testing "what agrees is listed as matching"
      (is (some #{{:field :summary :language "fi"}} (:matching review)))
      (is (not-any? #{{:field :services}} (:matching review)))))

  (testing "an unknown service is named by its id"
    (let [review (sut/sync-review (assoc-in site [:ptv :service-ids] ["svc-x"])
                                  nil channel services langs)]
      (is (= "svc-x" (:name (row review [:services "svc-x"]))))))

  (testing "marketing name: set in Lipas only, or in PTV only"
    (let [review (sut/sync-review (assoc site :marketing-name "Hallikauppa")
                                  nil channel services langs)]
      (is (= ["Hallikauppa" nil] ((juxt :lipas :ptv) (row review [:marketing-name "fi"])))))
    (let [channel (update channel :serviceChannelNames conj
                          {:type "AlternativeName" :language "fi" :value "PTV-markkinointi"})
          review (sut/sync-review site nil channel services langs)]
      (is (= [nil "PTV-markkinointi"] ((juxt :lipas :ptv) (row review [:marketing-name "fi"]))))))

  (testing "whitespace PTV collapses is no difference"
    (let [ws-site (-> site
                      (assoc :name "Halli  ")
                      (assoc-in [:ptv :description :fi] "desc-fi\r\n"))
          review (sut/sync-review ws-site nil channel services ["fi"])]
      (is (nil? (row review [:name "fi"])))
      (is (nil? (row review [:description "fi"]))))))

(deftest sync-review-first-link-test
  (let [linked (-> site
                   (update :ptv dissoc :last-sync)
                   (assoc-in [:ptv :source-id] nil)
                   ;; published in sv, so sv texts are compared
                   (assoc-in [:ptv :summary :se] "summary-sv")
                   (assoc-in [:ptv :description :se] "desc-fi"))
        review (sut/sync-review linked nil channel services langs)]
    (testing "a location LIPAS hasn't synced the site to is a first link"
      (is (true? (:first-link? review)))
      (is (every? #(= :first-link (:origin %)) (:rows review))))

    (testing "its texts need a choice, its connections don't (they're kept)"
      (is (sut/review-choice-required? (row review [:name "sv"])))
      (is (not (sut/review-choice-required? (row review [:services "svc-b"])))))))

(def ^:private synced-channel
  "What a sync of `site` with languages fi and en sent."
  {:id "C1"
   :sourceId "lipas-src-1"
   :languages ["fi" "en"]
   :serviceChannelNames [{:type "Name" :language "fi" :value "Halli"}
                         {:type "Name" :language "en" :value "Halli"}]
   :serviceChannelDescriptions [{:type "Summary" :language "fi" :value "summary-fi"}
                                {:type "Summary" :language "en" :value "summary-en"}
                                {:type "Description" :language "fi" :value "desc-fi"}
                                {:type "Description" :language "en" :value "desc-fi"}]
   :services [{:service {:id "svc-a"}}]})

(def ^:private synced-site
  (-> site
      (assoc-in [:ptv :languages] ["fi" "en"])
      (assoc-in [:ptv :description :en] "desc-fi")))

(def ^:private base (sut/->review-base synced-site))

(defn- set-ptv-text [channel type lang v]
  (update channel :serviceChannelDescriptions
          (fn [ds]
            (conj (vec (remove #(and (= type (:type %)) (= lang (:language %))) ds))
                  {:type type :language lang :value v}))))

(deftest sync-review-three-way-test
  (testing "right after a sync nothing differs"
    (is (= [] (:rows (sut/sync-review synced-site base synced-channel services ["fi" "en"])))))

  (testing "changed in PTV"
    (let [review (sut/sync-review synced-site base
                                  (set-ptv-text synced-channel "Summary" "fi" "PTV:n teksti")
                                  services ["fi" "en"])]
      (is (= :ptv (:origin (row review [:summary "fi"]))))
      (is (sut/review-choice-required? (row review [:summary "fi"])))))

  (testing "changed in Lipas"
    (let [review (sut/sync-review (assoc-in synced-site [:ptv :summary :fi] "Lipaksen teksti")
                                  base synced-channel services ["fi" "en"])]
      (is (= :lipas (:origin (row review [:summary "fi"]))))
      (is (not (sut/review-choice-required? (row review [:summary "fi"]))))))

  (testing "changed on both sides differently is a conflict"
    (let [review (sut/sync-review (assoc-in synced-site [:ptv :summary :fi] "Lipaksen teksti")
                                  base
                                  (set-ptv-text synced-channel "Summary" "fi" "PTV:n teksti")
                                  services ["fi" "en"])]
      (is (= :both (:origin (row review [:summary "fi"]))))))

  (testing "changed on both sides the same way is no difference"
    (is (nil? (row (sut/sync-review (assoc-in synced-site [:ptv :summary :fi] "Sama")
                                    base
                                    (set-ptv-text synced-channel "Summary" "fi" "Sama")
                                    services ["fi" "en"])
                   [:summary "fi"]))))

  (testing "a language added in Lipas since the last sync is Lipas's change"
    (let [with-sv (-> synced-site
                      (assoc-in [:ptv :summary :se] "sammanfattning")
                      (assoc-in [:ptv :description :se] "beskrivning"))
          review (sut/sync-review with-sv base synced-channel services ["fi" "se" "en"])
          rows (filter #(= "sv" (:language %)) (:rows review))]
      (is (= #{:name :summary :description} (set (map :field rows))))
      (is (every? #(and (= :lipas (:origin %)) (:language-added? %)) rows))))

  (testing "connections: made in PTV, removed in PTV, added in Lipas"
    (let [review (sut/sync-review (assoc-in synced-site [:ptv :service-ids] ["svc-a" "svc-c"])
                                  base
                                  (assoc synced-channel :services [{:service {:id "svc-b"}}])
                                  services ["fi" "en"])]
      (is (= {:origin :ptv :lipas? false :ptv? true}
             (select-keys (row review [:services "svc-b"]) [:origin :lipas? :ptv?])))
      (is (= {:origin :ptv :lipas? true :ptv? false}
             (select-keys (row review [:services "svc-a"]) [:origin :lipas? :ptv?])))
      (is (= {:origin :lipas :lipas? true :ptv? false}
             (select-keys (row review [:services "svc-c"]) [:origin :lipas? :ptv?])))))

  (testing "a base from another sync or another location isn't used"
    (let [changed (set-ptv-text synced-channel "Summary" "fi" "PTV:n teksti")]
      (is (= :unknown (:origin (row (sut/sync-review synced-site
                                                     (assoc-in base [:ptv :last-sync] "T0")
                                                     changed services ["fi" "en"])
                                    [:summary "fi"]))))
      (is (= :unknown (:origin (row (sut/sync-review synced-site
                                                     (assoc-in base [:ptv :service-channel-ids] ["C0"])
                                                     changed services ["fi" "en"])
                                    [:summary "fi"])))))))

(deftest review-decisions-test
  (let [channel (-> synced-channel
                    (set-ptv-text "Summary" "fi" "PTV:n teksti")
                    (assoc :services [{:service {:id "svc-a"}} {:service {:id "svc-b"}}]))
        review (sut/sync-review synced-site base channel services ["fi" "en"])
        rows-of (fn [draft] (:rows (sut/annotate-review review draft)))
        take (fn [draft side & keys]
               (reduce (fn [draft k]
                         (let [r (row review k)]
                           (sut/apply-review-writes draft (sut/review-writes draft r side))))
                       draft
                       keys))
        keep-lipas (fn [k] {k {:choice :lipas :seen (sut/review-choice-seen (row review k))}})
        open-keys (fn [draft choices] (set (map :key (sut/open-review-rows (rows-of draft) choices))))]
    (testing "the review is of the saved site; untouched, PTV's changes are open"
      (is (= #{[:summary "fi"] [:services "svc-b"]} (open-keys synced-site {}))))

    (testing "taking PTV's version writes it into the edit, which decides the row"
      (let [draft (take synced-site :ptv [:summary "fi"] [:services "svc-b"])]
        (is (= "PTV:n teksti" (get-in draft [:ptv :summary :fi])))
        (is (= ["svc-a" "svc-b"] (get-in draft [:ptv :service-ids])))
        (is (= #{} (open-keys draft {})))
        (is (= #{:ptv} (set (map :current (rows-of draft)))))))

    (testing "the rows stay while the user works; only the edit changes"
      (is (= (map :key (:rows review))
             (map :key (rows-of (take synced-site :ptv [:summary "fi"]))))))

    (testing "keeping Lipas's version puts the saved one back and is recorded"
      (let [draft (-> synced-site (take :ptv [:summary "fi"]) (take :lipas [:summary "fi"]))]
        (is (= "summary-fi" (get-in draft [:ptv :summary :fi])))
        (is (contains? (open-keys draft {}) [:summary "fi"]) "without the record: undecided")
        (is (not (contains? (open-keys draft (keep-lipas [:summary "fi"])) [:summary "fi"])))))

    (testing "a text of one's own counts as decided"
      (let [draft (assoc-in synced-site [:ptv :summary :fi] "Oma teksti")
            r (some #(when (= [:summary "fi"] (:key %)) %) (rows-of draft))]
        (is (= :edited (:current r)))
        (is (= "Oma teksti" (:draft r)))
        (is (not (contains? (open-keys draft {}) [:summary "fi"])))))

    (testing "a record made against a PTV value that has changed since doesn't count"
      (is (nil? (sut/review-choice {[:summary "fi"] {:choice :lipas :seen "jotain muuta"}}
                                   (row review [:summary "fi"])))))

    (testing "the saved site, from the edit and the saved snapshot"
      (let [draft (-> synced-site
                      (assoc :marketing-name "Uusi")
                      (assoc-in [:ptv :summary :fi] "Luonnos"))]
        (is (= synced-site (sut/review-saved-site draft base)))
        (is (= draft (sut/review-saved-site draft nil)))))))

(deftest sync-status-review-test
  (let [status (fn [site & [org-langs]]
                 (:sync-status (sut/sports-site->ptv-input
                                 {:org-id "o" :types {} :org-defaults {}
                                  :org-langs (or org-langs ["fi" "en"])}
                                 {"C1" synced-channel} services
                                 (assoc site :event-date "T1"))))
        site (assoc synced-site :ptv-base base)]
    (testing "in sync"
      (is (= :ok (status site))))
    (testing "changes made only in Lipas just need a sync"
      (is (= :ready (status (assoc-in site [:ptv :summary :fi] "Lipaksen teksti")))))
    (testing "a language added in Lipas just needs a sync"
      (is (= :ready (status (-> site
                                (assoc-in [:ptv :summary :se] "sammanfattning")
                                (assoc-in [:ptv :description :se] "beskrivning"))
                            ["fi" "se" "en"]))))
    (testing "a rename in Lipas just needs a sync"
      (is (= :ready (status (assoc site :name "Uusi nimi")))))
    (testing "a change made in PTV needs a choice"
      (is (= :awaiting-choice
             (:sync-status (sut/sports-site->ptv-input
                             {:org-id "o" :types {} :org-defaults {} :org-langs ["fi" "en"]}
                             {"C1" (set-ptv-text synced-channel "Summary" "fi" "PTV:n teksti")}
                             services
                             (assoc site :event-date "T1"))))))
    (testing "without the base every difference needs a look"
      (is (= :awaiting-choice (status (-> site
                                          (dissoc :ptv-base)
                                          (assoc-in [:ptv :summary :fi] "Lipaksen teksti"))))))))

(deftest sync-status-follows-decisions-test
  (let [channel (set-ptv-text synced-channel "Summary" "fi" "PTV:n teksti")
        status (fn [site & {:keys [choices channel] :or {channel channel}}]
                 (:sync-status (sut/sports-site->ptv-input
                                 {:org-id "o" :types {} :org-defaults {} :org-langs ["fi" "en"]}
                                 {"C1" channel} services
                                 ;; :review-saved: the site as loaded, which the
                                 ;; review compares PTV with (the tool sets it)
                                 (assoc site :event-date "T1" :ptv-base base
                                        :review-saved base :review-choices choices))))
        row (row (sut/sync-review synced-site base channel services ["fi" "en"]) [:summary "fi"])]
    (testing "a change made in PTV waits for a choice"
      (is (= :awaiting-choice (status synced-site))))
    (testing "once decided, the site is ready: the chip doesn't stay"
      (is (= :ready (status synced-site :choices {[:summary "fi"] {:choice :lipas
                                                                   :seen (sut/review-choice-seen row)}})))
      (is (= :ready (status (assoc-in synced-site [:ptv :summary :fi] "PTV:n teksti")))
          "taking PTV's version is a decision too"))
    (testing "PTV's unpublished draft or a failed sync blocks it"
      (is (= :blocked (status synced-site :channel (assoc channel :publishingStatus "Modified"))))
      (is (= :blocked (status (assoc-in synced-site [:ptv :error] {:message "PTV 502"})))))))

(deftest review-site-patch-test
  (let [saved (sut/->review-base (assoc synced-site :marketing-name "Vanha markkinointinimi"
                                        :name-localized {:se "Hallen"}
                                        :phone-number "08 123 4567"
                                        :location {:address "Katu 1" :postal-code "00100"
                                                   :city {:city-code 91}}))]
    (testing "nothing changed, no patch"
      (is (nil? (sut/review-site-patch saved (assoc synced-site
                                                    :marketing-name "Vanha markkinointinimi"
                                                    :name-localized {:se "Hallen"}
                                                    :phone-number "08 123 4567"
                                                    :location {:address "Katu 1" :postal-code "00100"
                                                               :city {:city-code 91}})))))

    (testing "what the edit changes, shaped like the site; nil removes a value"
      (is (= {:name "Uusi halli" :marketing-name "Halli X" :name-localized {:se nil}
              :phone-number nil :location {:address "Katu 2" :postal-office "Kunta"}}
             (sut/review-site-patch saved (assoc synced-site
                                                 :name "Uusi halli"
                                                 :marketing-name "Halli X"
                                                 :location {:address "Katu 2" :postal-code "00100"
                                                            :postal-office "Kunta"
                                                            :city {:city-code 91}})))))

    (testing "written into the site; nil removes an optional value, never a required one"
      (is (= {:name "Uusi halli" :name-localized {:en "Hall"} :email "a@b.fi"
              :location {:address "Katu 1" :postal-code "00200" :city {:city-code 91}}}
             (select-keys (sut/apply-site-patch (assoc synced-site
                                                       :marketing-name "Vanha"
                                                       :name-localized {:se "Hallen" :en "Hall"}
                                                       :location {:address "Katu 1" :postal-code "00100"
                                                                  :postal-office "Kunta"
                                                                  :city {:city-code 91}})
                                                {:name "Uusi halli"
                                                 :name-localized {:se nil}
                                                 :marketing-name nil
                                                 :email "a@b.fi"
                                                 :location {:address nil :postal-code "00200"
                                                            :postal-office nil}})
                          [:name :name-localized :marketing-name :email :location]))))

    (testing "only the fields a site editor may take are written"
      (is (= synced-site (sut/apply-site-patch synced-site {:status "incorrect-data"
                                                            :location {:city {:city-code 5}}
                                                            :ptv {:summary {:fi "x"}}}))))

    (testing "the review lists every connected service on both sides"
      (let [review (sut/sync-review synced-site (sut/->review-base synced-site)
                                    (assoc synced-channel :services [{:service {:id "svc-a"}}
                                                                     {:service {:id "svc-b"}}])
                                    services ["fi" "en"])]
        (is (= {:lipas [{:id "svc-a" :name "Pallokentät"}]
                :ptv [{:id "svc-b" :name "Liikuntahallit"} {:id "svc-a" :name "Pallokentät"}]}
               (:services review)))))))

;;; Properties ;;;

(def ^:private text-gen (gen/elements [nil "" "a" "a " "b" "c"]))

(defn- site-with-summary [v]
  (assoc-in synced-site [:ptv :summary :fi] v))

(defspec summary-origin-follows-who-changed-it 200
  (prop/for-all [b text-gen
                 l text-gen
                 p text-gen]
                (let [base (sut/->review-base (site-with-summary b))
                      review (sut/sync-review (site-with-summary l) base
                                              (set-ptv-text synced-channel "Summary" "fi" p)
                                              services ["fi"])
                      r (row review [:summary "fi"])
                      [b l p] (map ptv-data/normalize-ws [b l p])]
                  (and (= (some? r) (not= l p))
                       (or (nil? r)
                           (= (:origin r) (cond (= b p) :lipas
                                                (= b l) :ptv
                                                :else :both)))))))

(defspec taking-ptv-leaves-no-difference 200
  (prop/for-all [b text-gen
                 l text-gen
                 p text-gen
                 base-ids (gen/set (gen/elements ["svc-a" "svc-b"]))
                 lipas-ids (gen/set (gen/elements ["svc-a" "svc-b"]))
                 ptv-ids (gen/set (gen/elements ["svc-a" "svc-b"]))]
                (let [mk (fn [text ids] (-> (site-with-summary text)
                                            (assoc-in [:ptv :service-ids] (vec (sort ids)))))
                      base (sut/->review-base (mk b base-ids))
                      channel (-> synced-channel
                                  (set-ptv-text "Summary" "fi" p)
                                  (assoc :services (vec (for [id (sort ptv-ids)] {:service {:id id}}))))
                      site (mk l lipas-ids)
                      ;; information rows (here: the en version PTV has and
                      ;; a fi-only sync removes) have nothing to take
                      decidable #(remove (comp sut/info-kinds :kind) %)
                      rows (decidable (:rows (sut/sync-review site base channel services ["fi"])))
                      taken (reduce (fn [draft r]
                                      (sut/apply-review-writes draft (sut/review-writes draft r :ptv)))
                                    site
                                    rows)]
                  (and (empty? (decidable (:rows (sut/sync-review taken base channel services ["fi"]))))
                       ;; and every row of the saved site's review now reads as PTV's
                       (every? #(= :ptv (:current %))
                               (decidable (:rows (sut/annotate-review
                                                   (sut/sync-review site base channel services ["fi"])
                                                   taken))))))))

(defspec keeping-lipas-restores-the-saved-site 200
  (prop/for-all [b text-gen
                 l text-gen
                 p text-gen
                 lipas-ids (gen/set (gen/elements ["svc-a" "svc-b"]))
                 ptv-ids (gen/set (gen/elements ["svc-a" "svc-b"]))]
                (let [site (-> (site-with-summary l)
                               (assoc-in [:ptv :service-ids] (vec (sort lipas-ids))))
                      base (sut/->review-base (site-with-summary b))
                      channel (-> synced-channel
                                  (set-ptv-text "Summary" "fi" p)
                                  (assoc :services (vec (for [id (sort ptv-ids)] {:service {:id id}}))))
                      rows (:rows (sut/sync-review site base channel services ["fi"]))
                      there-and-back (reduce (fn [draft r]
                                               (let [draft (sut/apply-review-writes draft (sut/review-writes draft r :ptv))]
                                                 (sut/apply-review-writes draft (sut/review-writes draft r :lipas))))
                                             site
                                             rows)]
                  (and (= (ptv-data/normalize-ws (get-in site [:ptv :summary :fi]))
                          (ptv-data/normalize-ws (get-in there-and-back [:ptv :summary :fi])))
                       (= (set (-> site :ptv :service-ids))
                          (set (-> there-and-back :ptv :service-ids)))))))

;;; Coverage and projection ;;;

(def ^:private full-site
  "A site with every field a sync derives from."
  (-> synced-site
      (assoc :marketing-name "Markkinointinimi"
             :name-localized {:se "Hallen" :en "Hall"}
             :email "info@kunta.fi"
             :phone-number "040 123 4567"
             :www "www.kunta.fi"
             :reservations-link "https://varaus.kunta.fi"
             :status "active"
             :location {:address "Katu 1" :postal-code "00100" :city {:city-code 91}})))

(deftest payload-coverage-test
  (let [payload-keys (fn [site] (set (keys (ptv-data/->ptv-service-location
                                             "o" (constantly [1 2]) "now" site))))
        all-keys (into (payload-keys full-site)
                       ;; without contacts: the deleteAll* flags
                       (payload-keys (dissoc full-site :email :phone-number :www :reservations-link)))
        reviewed (into #{} (mapcat :reads) sut/review-fields)
        explained (set (keys sut/not-reviewed-payload-keys))]
    (testing "every key a sync sends is reviewed or explained"
      (is (= #{} (set/difference all-keys reviewed explained))))
    (testing "nothing is explained that a sync doesn't send"
      (is (= #{} (set/difference explained all-keys))))
    (testing "nothing is both"
      (is (= #{} (set/intersection reviewed explained))))))

(deftest location-values-test
  (let [langs ["fi" "en"]
        payload (sut/site->location full-site langs)
        values (sut/location-values payload)]
    (testing "texts by field and PTV language, as the sync sends them"
      (is (= "Halli" (get values [:name "fi"])))
      (is (= "Hall" (get values [:name "en"])))
      (is (= "Markkinointinimi" (get values [:marketing-name "fi"])))
      (is (= "desc-fi" (get values [:description "en"]))))
    (testing "a language not published has no texts"
      (is (nil? (get values [:name "sv"]))))
    (testing "languages and connections"
      (is (= #{"fi" "en"} (get values [:languages])))
      (is (= #{"svc-a"} (get values [:services]))))
    (testing "PTV's GET shape gives the same values"
      (is (= values (sut/location-values
                      (assoc payload :services [{:service {:id "svc-a" :name "Pallokentät"}}])))))
    (testing "the placeholder for a missing Finnish text is no text"
      (is (nil? (get (sut/location-values
                       (sut/site->location (update-in full-site [:ptv :summary] dissoc :fi) langs))
                     [:summary "fi"]))))))

;;; Properties over every text field ;;;

(def ^:private text-key-gens
  "[field language locale] of the texts compared for synced-site (fi and en
   published), with values that can be on each side: PTV has a name in every
   language version and a summary and description in Finnish; Lipas a
   Finnish name."
  (let [some-text (gen/elements ["a" "a " "b" "c"])
        any-text (gen/elements [nil "" "a" "b" "c"])]
    {[:name "fi" :fi] {:lipas some-text :ptv some-text}
     [:name "en" :en] {:lipas any-text :ptv some-text}
     [:marketing-name "fi" :fi] {:lipas any-text :ptv any-text}
     [:summary "fi" :fi] {:lipas any-text :ptv some-text}
     [:description "fi" :fi] {:lipas any-text :ptv some-text}}))

(defn- with-text [site [field _ locale] v]
  (sut/apply-review-writes site {(sut/review-text-path {:field field :locale locale}) v}))

(defn- as-channel
  "The location a sync of `site` leaves in PTV."
  [site]
  (-> (ptv-data/->ptv-service-location "o" (constantly nil) "now" site)
      (assoc :id "C1")
      (update :services (fn [ids] (mapv (fn [id] {:service {:id id}}) ids)))))

(def ^:private text-case-gen
  (gen/bind (gen/elements (keys text-key-gens))
            (fn [k]
              (let [{:keys [lipas ptv]} (text-key-gens k)]
                (gen/tuple (gen/return k) lipas lipas ptv)))))

(defspec text-origin-follows-who-changed-it 300
  (prop/for-all [[k b l p] text-case-gen]
                (let [base-site (with-text synced-site k b)
                      review (sut/sync-review (with-text synced-site k l)
                                              (sut/->review-base base-site)
                                              (as-channel (with-text synced-site k p))
                                              services ["fi" "en"])
                      r (row review (vec (take 2 k)))
                      value (fn [v] (-> (with-text synced-site k v)
                                        (sut/site->location ["fi" "en"])
                                        sut/location-values
                                        (get (vec (take 2 k)))
                                        ptv-data/normalize-ws))
                      [nb nl np] (map value [b l p])]
                  (and (= (some? r) (not= nl np))
                       (or (nil? r)
                           (= (:origin r) (cond (= nb np) :lipas
                                                (= nb nl) :ptv
                                                :else :both)))))))

(defspec taking-ptv-leaves-no-text-difference 300
  (prop/for-all [[k b l p] text-case-gen]
                (let [base (sut/->review-base (with-text synced-site k b))
                      channel (as-channel (with-text synced-site k p))
                      site (with-text synced-site k l)
                      rows (:rows (sut/sync-review site base channel services ["fi" "en"]))
                      taken (reduce (fn [draft r]
                                      (sut/apply-review-writes draft (sut/review-writes draft r :ptv)))
                                    site
                                    rows)]
                  (empty? (:rows (sut/sync-review taken base channel services ["fi" "en"]))))))
