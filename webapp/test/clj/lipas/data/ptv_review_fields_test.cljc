(ns lipas.data.ptv-review-fields-test
  "The sync review beyond texts and connections: contacts, web pages, the
   address, municipality and point, language versions, PTV's postal
   addresses, the type-change swap, and the evidence that tells a change
   made in PTV from a change in how Lipas derives the payload."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [lipas.data.ptv :as ptv-data]
            [lipas.data.ptv-review :as sut]))

(defn- annotate
  "annotate-review for a draft that publishes the review's languages."
  [review draft]
  (sut/annotate-review review draft (:langs review)))

(def ^:private last-sync "2026-10-01T10:00:00.000Z")
(def ^:private right-after "2026-10-01T10:00:02.123456")
(def ^:private days-later "2026-10-05T08:30:00.000000")

(def ^:private site
  "A site as its last sync left it, published in fi and en."
  {:lipas-id 1
   :name "Halli"
   :name-localized {:en "Hall"}
   :status "active"
   :type {:type-code 2510}
   :email "liikunta@kunta.fi"
   :phone-number "08 5875 5700"
   :www "https://www.kunta.fi/liikunta"
   :location {:address "Urheilutie 4"
              :postal-code "91600"
              :postal-office "Kunta"
              :city {:city-code 889}
              :geometries {:type "FeatureCollection"
                           :features [{:type "Feature"
                                       :geometry {:type "Point"
                                                  :coordinates [26.4131 64.7631]}}]}}
   :ptv {:org-id "o"
         :source-id "lipas-o-1"
         :last-sync last-sync
         :languages ["fi" "en"]
         :previous-type-code 2510
         :summary {:fi "Tiivistelmä" :en "Summary"}
         :description {:fi "Kuvaus" :en "Description"}
         :service-ids ["svc-a"]
         :service-channel-ids ["C1"]}})

(def ^:private langs ["fi" "en"])

(defn- as-channel
  "The location as PTV returns it after a sync of `site`: the payload in
   the GET shape, with what PTV adds (the street in every language, its
   post office in capitals, a municipality object, string coordinates)."
  [site & {:keys [modified] :or {modified right-after}}]
  (let [payload (sut/site->location site langs)]
    (-> payload
        (assoc :id "C1"
               :sourceId "lipas-o-1"
               :modified modified
               :languages (vec (sort (:languages payload))))
        (update :services (fn [ids] (mapv (fn [id] {:service {:id id}}) ids)))
        (update :addresses
                (fn [addresses]
                  (mapv (fn [{:keys [streetAddress] :as a}]
                          (assoc a :streetAddress
                                 (-> streetAddress
                                     (update :street (fn [s] (into (vec s) [{:value (:value (first s)) :language "sv"}
                                                                            {:value (:value (first s)) :language "smn"}])))
                                     (assoc :postOffice [{:value "KUNTA" :language "fi"}
                                                         {:value "KUNTA" :language "sv"}])
                                     (update :municipality (fn [code] {:code code}))
                                     (update :latitude str)
                                     (update :longitude str))))
                        addresses))))))

(def ^:private base (sut/->review-base site))

(defn- review
  ([channel] (review site channel))
  ([s channel] (sut/sync-review s base channel {} langs)))

(defn- row [r k]
  (some #(when (= k (:key %)) %) (:rows r)))

(defn- keys-of [r] (set (map :key (:rows r))))

(defn- with-phones [channel phones]
  (assoc channel :phoneNumbers
         (vec (for [[prefix number lang] phones]
                {:prefixNumber prefix :number number :isFinnishServiceNumber false
                 :language lang :serviceChargeType "Chargeable"}))))

(defn- set-street [channel street & {:keys [number]}]
  (update-in channel [:addresses 0 :streetAddress]
             #(cond-> (assoc % :street [{:value street :language "fi"}
                                        {:value street :language "sv"}])
                number (assoc :streetNumber number))))

;;; Projection ;;;

(deftest synced-location-has-nothing-to-review-test
  (testing "PTV's own rewriting (post office, streets in other languages, a municipality object, string coordinates) isn't a difference"
    (is (= [] (:rows (review (as-channel site))))))
  (testing "nor are a capitalised street, a street number kept apart, or an upper-case email"
    (is (= [] (:rows (review (-> (as-channel site)
                                 (set-street "URHEILUTIE" :number "4")
                                 (assoc :emails [{:value "Liikunta@Kunta.fi" :language "fi"}])))))))
  (testing "nor a web page synced before Lipas named its pages"
    (is (= [] (:rows (review (update (as-channel site) :webPages
                                     (fn [ps] (mapv #(dissoc % :value) ps)))))))))

;;; Evidence ;;;

(deftest ptv-changed-since-sync-test
  (let [s (fn [& {:as ptv}] {:ptv (merge {:last-sync last-sync} ptv)})]
    (testing "PTV's :modified compared with the one PTV reported after the sync"
      (is (false? (sut/ptv-changed-since-sync? (s :ptv-modified right-after) {:modified right-after})))
      (is (true? (sut/ptv-changed-since-sync? (s :ptv-modified right-after) {:modified days-later}))))
    (testing "a site synced before that was stored: :modified over 2 min after the sync"
      (is (false? (sut/ptv-changed-since-sync? (s) {:modified right-after})))
      (is (false? (sut/ptv-changed-since-sync? (s) {:modified "2026-10-01T10:01:59.000000"})))
      (is (true? (sut/ptv-changed-since-sync? (s) {:modified "2026-10-01T10:02:01.000000"})))
      (is (true? (sut/ptv-changed-since-sync? (s) {:modified days-later}))))
    (testing "when it can't be told, a difference is asked about"
      (is (true? (sut/ptv-changed-since-sync? (s) {})))
      (is (true? (sut/ptv-changed-since-sync? {:ptv {:last-sync "T1"}} {:modified right-after}))))))

(deftest derivation-change-is-lipas-change-test
  ;; The sync sent http://; today parse-www sends https://. Nobody touched PTV.
  (let [old-channel (update (as-channel site) :webPages
                            (fn [ps] (mapv #(assoc % :url "http://www.kunta.fi/liikunta") ps)))]
    (testing "without evidence it is Lipas's change, sent by the sync"
      (is (= :lipas (:origin (row (review old-channel) [:www]))))
      (is (not (sut/review-choice-required? (row (review old-channel) [:www])))))
    (testing "with evidence it is PTV's"
      (is (= :ptv (:origin (row (review (assoc old-channel :modified days-later)) [:www])))))))

;;; Contacts ;;;

(deftest phone-test
  (let [channel (-> (as-channel site :modified days-later)
                    (with-phones [["+358" "401234567" "fi"]]))
        r (review channel)
        phone (row r [:phone])]
    (testing "a phone changed in PTV"
      (is (= {:origin :ptv :kind :one-slot :group :contacts}
             (select-keys phone [:origin :kind :group])))
      (is (= #{{:prefix "+358" :number "858755700" :service? false}} (:lipas phone)))
      (is (sut/review-choice-required? phone)))
    (testing "taking it writes the site's phone, as Lipas keeps it"
      (let [taken (sut/apply-review-writes site (sut/review-writes site phone :ptv))]
        (is (= "+358 401234567" (:phone-number taken)))
        (is (= :ptv (:current (row (annotate r taken) [:phone]))))
        (is (nil? (row (review taken channel) [:phone])) "nothing left to review")))
    (testing "keeping Lipas's puts the saved value back"
      (is (= {[:phone-number] "08 5875 5700"} (sut/review-writes site phone :lipas)))))

  (testing "several numbers in PTV: each can be taken, and the result is one"
    (let [channel (-> (as-channel site :modified days-later)
                      (with-phones [["+358" "858755700" "fi"] ["+358" "401234567" "sv"]]))
          r (review channel)
          phone (row r [:phone])
          new-number (some #(when (= "401234567" (:number %)) %) (sut/ptv-values phone))]
      (is (= 2 (count (:ptv phone))))
      (is (not (sut/takeable-in-bulk? phone)) "which one is the user's pick")
      (testing "PTV keeping Lipas's number beside the new one decides nothing"
        (let [annotated (row (annotate r site) [:phone])]
          (is (= :lipas (:current annotated)))
          (is (= [annotated] (sut/open-review-rows [annotated] {})) "a sync would remove PTV's other")))
      (let [taken (sut/apply-review-writes site (sut/review-value-writes phone new-number))]
        (is (= "+358 401234567" (:phone-number taken)))
        (is (= :ptv (:current (row (annotate r taken) [:phone])))))
      (testing "taking Lipas's own number from PTV's is keeping Lipas's version"
        (is (sut/lipas-value? phone (some #(when (= "858755700" (:number %)) %) (sut/ptv-values phone))))
        (is (not (sut/lipas-value? phone new-number))))))

  (testing "a Finnish service number has no prefix"
    (is (= "0600123456" (sut/format-phone {:prefix nil :number "0600123456" :service? true})))
    (is (= {:is-finnish-service-number true :number "0600123456"}
           (ptv-data/parse-phone-number (sut/format-phone {:number "0600123456"}))))))

(deftest email-test
  (let [r (review (assoc (as-channel site :modified days-later)
                         :emails [{:value "uusi@kunta.fi" :language "fi"}]))]
    (is (= #{"uusi@kunta.fi"} (:ptv (row r [:email]))))
    (is (= {[:email] "uusi@kunta.fi"} (sut/review-writes site (row r [:email]) :ptv)))))

(deftest web-pages-test
  (testing "PTV replaced the page with its own, under its own name"
    (let [channel (assoc (as-channel site :modified days-later)
                         :webPages [{:url "https://skeittiparkit.fi" :value "Skeittiparkit" :language "fi"}])
          r (review channel)
          other (row r [:other-web-pages])]
      (is (= #{[:www] [:other-web-pages]} (keys-of r)))
      (is (= #{"https://skeittiparkit.fi"} (:ptv other)))
      (is (not (sut/takeable-in-bulk? other)) "into which slot is the user's pick")
      (testing "taken as the site's web page"
        (let [taken (sut/apply-review-writes site (sut/review-value-writes
                                                    other {:slot :www :url "https://skeittiparkit.fi"}))
              annotated (annotate r taken)]
          (is (= "https://skeittiparkit.fi" (:www taken)))
          (is (= :ptv (:current (row annotated [:other-web-pages]))))
          (is (= :ptv (:current (row annotated [:www]))) "the slot holds PTV's page")
          (is (= [] (sut/open-review-rows (:rows annotated) {})))))))

  (testing "a booking link Lipas added since the last sync (#269) is Lipas's"
    (let [booked (assoc site :reservations-link "https://varaus.kunta.fi")
          r (review booked (as-channel site))]
      (is (= :lipas (:origin (row r [:reservations-link]))))))

  (testing "a page under Lipas's name but in another language counts for its slot"
    (let [channel (update (as-channel site) :webPages
                          (fn [ps] (mapv #(assoc % :value "Sports facility website") ps)))]
      (is (= [] (:rows (review channel)))))))

;;; Address, municipality, point ;;;

(deftest address-test
  (let [channel (-> (as-channel site :modified days-later)
                    (set-street "Ahmatie" :number "6")
                    (assoc-in [:addresses 0 :streetAddress :postalCode] "91610")
                    (assoc-in [:addresses 0 :streetAddress :street]
                              [{:value "Ahmatie" :language "fi"} {:value "Ahmavägen" :language "sv"}]))
        r (review channel)
        address (row r [:address])]
    (is (= :ptv (:origin address)))
    (is (= {:street "Ahmatie 6" :postal-code "91610" :post-office "KUNTA"
            :other-streets {"sv" "Ahmavägen"}}
           (select-keys (:ptv address) [:street :postal-code :post-office :other-streets])))
    (testing "taking it writes the street, postal code and office"
      (let [taken (sut/apply-review-writes site (sut/review-writes site address :ptv))]
        (is (= {:address "Ahmatie 6" :postal-code "91610" :postal-office "Kunta"}
               (select-keys (:location taken) [:address :postal-code :postal-office])))
        (is (= :ptv (:current (row (annotate r taken) [:address]))))))))

(deftest municipality-and-point-test
  (testing "another municipality in PTV is information, not a choice"
    (let [m (row (review (assoc-in (as-channel site :modified days-later)
                                   [:addresses 0 :streetAddress :municipality] {:code "091"}))
                 [:municipality])]
      (is (= ["889" "091"] ((juxt :lipas :ptv) m)))
      (is (not (sut/review-choice-required? m)))
      (is (= {} (sut/review-writes site m :ptv)))))

  (let [move (fn [metres]
               (update-in (as-channel site :modified days-later) [:addresses 0 :streetAddress :latitude]
                          #(str (+ (parse-double %) metres))))]
    (testing "a point within 50 m is the same place"
      (is (nil? (row (review (move 49)) [:point]))))
    (testing "beyond it, information"
      (let [p (row (review (move 200)) [:point])]
        (is (= 200 (:distance-m p)))
        (is (not (sut/review-choice-required? p)))))))

;;; Language versions ;;;

(deftest language-removal-test
  (let [channel (-> (as-channel site :modified days-later)
                    (update :languages conj "sv")
                    (update :serviceChannelNames conj {:type "Name" :language "sv" :value "Hallen"})
                    (update :serviceChannelDescriptions into
                            [{:type "Summary" :language "sv" :value "Sammanfattning"}
                             {:type "Description" :language "sv" :value "Beskrivning"}]))
        r (review channel)
        sv (row r [:languages "sv"])]
    (testing "a language version Lipas won't publish is removed: information with its texts"
      (is (= {:origin :ptv :locale :se
              :texts {:name "Hallen" :summary "Sammanfattning" :description "Beskrivning"}}
             (select-keys sv [:origin :locale :texts])))
      (is (not (sut/review-choice-required? sv)))
      (is (= :ptv-manager (:takeable-by sv)) "a PTV manager may keep the language")
      (is (not-any? #(and (= "sv" (:language %)) (not= :languages (:field %))) (:rows r))))
    (testing "taking PTV's texts writes the language into the site"
      (let [taken (sut/apply-review-writes site (sut/review-writes site sv :ptv))]
        (is (= "Hallen" (get-in taken [:name-localized :se])))
        (is (= "Sammanfattning" (get-in taken [:ptv :summary :se])))
        (is (= "Beskrivning" (get-in taken [:ptv :description :se])))
        (testing "and a sync that publishes it keeps it"
          (is (nil? (row (sut/sync-review taken base channel {} ["fi" "se" "en"]) [:languages "sv"]))))
        (testing "in the edit, the language then reads as PTV's version"
          (is (= :ptv (:current (row (sut/annotate-review r taken (ptv-data/effective-languages
                                                                    taken ["fi" "se" "en"]))
                                     [:languages "sv"])))))
        (testing "unless the org doesn't publish it"
          (is (= :lipas (:current (row (sut/annotate-review r taken (ptv-data/effective-languages
                                                                      taken ["fi" "en"]))
                                       [:languages "sv"])))))))))

;;; PTV's postal addresses (D8) ;;;

(def ^:private po-box
  {:type "Postal" :subType "PostOfficeBox"
   :postOfficeBoxAddress {:postOfficeBox [{:value "PL 12" :language "fi"}
                                          {:value "PB 12" :language "sv"}]
                          :postalCode "91601"
                          :municipality {:code "889"}
                          :additionalInformation []}})

(def ^:private postal-street
  {:type "Postal" :subType "Street"
   :streetAddress {:street [{:value "Kirkkotie" :language "fi"} {:value "Kirkkotie" :language "smn"}]
                   :streetNumber "2" :postalCode "91600" :municipality {:code "889"}
                   :additionalInformation [{:value "" :language "fi"}]
                   :latitude nil :longitude nil}})

(def ^:private unsendable
  ;; PTV's API rejects a street address with neither a number nor coordinates
  {:type "Postal" :subType "Street"
   :streetAddress {:street [{:value "Liikekuja 3" :language "fi"}] :streetNumber ""
                   :postalCode "91600" :latitude nil :longitude nil}})

(deftest postal-addresses-test
  (let [channel (update (as-channel site) :addresses into [po-box postal-street unsendable])
        {:keys [kept dropped]} (ptv-data/ptv-postal-addresses channel #{"fi" "en"})]
    (testing "sent back as the PUT takes them, kept to the payload's languages"
      (is (= [{:type "Postal" :subType "PostOfficeBox" :country "FI"
               :postOfficeBoxAddress {:postOfficeBox [{:value "PL 12" :language "fi"}]
                                      :postalCode "91601" :municipality "889"}}
              {:type "Postal" :subType "Street" :country "FI"
               :streetAddress {:street [{:value "Kirkkotie" :language "fi"}]
                               :streetNumber "2" :postalCode "91600" :municipality "889"}}]
             kept)))
    (testing "one PTV's API rejects drops out"
      (is (= [unsendable] dropped)))
    (testing "the review says what PTV keeps and what drops"
      (is (= {:kept 2 :dropped 1}
             (update-vals (:postal-addresses (review channel)) count))))
    (testing "postal addresses are no difference to choose"
      (is (= [] (:rows (review channel)))))))

;;; The type-change swap ;;;

(deftest type-change-swap-test
  (let [services {"svc-a" {:sourceId "lipas-o-2500" :serviceNames [{:type "Name" :language "fi" :value "Jäähallit"}]}
                  "svc-u" {:sourceId "lipas-o-3100" :serviceNames [{:type "Name" :language "fi" :value "Uimahallit"}]}
                  "svc-x" {:serviceNames [{:type "Name" :language "fi" :value "PTV:n oma"}]}}
        source-id->service (into {} (for [[id s] services :when (:sourceId s)] [(:sourceId s) (assoc s :id id)]))
        swimming (assoc-in site [:type :type-code] 3110)]
    (testing "the old sub-category's service is swapped for the new one's, others stay"
      (is (= ["svc-x" "svc-u"]
             (ptv-data/rebound-service-ids source-id->service
                                           (assoc-in swimming [:ptv :service-ids] ["svc-a" "svc-x"]))))
      (is (= ["svc-a"] (ptv-data/rebound-service-ids source-id->service site)) "type unchanged"))
    (testing "the review shows the connections the sync changes as Lipas's"
      (let [r (sut/sync-review swimming base (as-channel site) services langs)]
        (is (= {[:services "svc-a"] [:lipas false] [:services "svc-u"] [:lipas true]}
               (into {} (for [x (:rows r)] [(:key x) [(:origin x) (:lipas? x)]]))))
        (testing "and the edit, projected the same way, holds them"
          (is (every? #(= :lipas (:current %)) (:rows (annotate r swimming)))))))))

;;; Properties ;;;

(def ^:private value-change-gen
  "A change to one field, made in Lipas (the site) or in PTV (the channel)."
  (gen/elements
    [[:phone :site #(assoc % :phone-number "040 555 1234")]
     [:phone :ptv #(with-phones % [["+358" "405551234" "fi"]])]
     [:email :site #(assoc % :email "toinen@kunta.fi")]
     [:email :ptv #(assoc % :emails [{:value "toinen@kunta.fi" :language "fi"}])]
     [:www :site #(assoc % :www "https://kunta.fi/uusi")]
     [:www :ptv #(update % :webPages (fn [ps] (mapv (fn [p] (assoc p :url "https://kunta.fi/uusi")) ps)))]
     [:address :site #(assoc-in % [:location :address] "Kirkkotie 1")]
     [:address :ptv #(set-street % "Kirkkotie 1")]]))

(defspec origin-follows-who-changed-it 200
  (prop/for-all [[field side change] value-change-gen
                 evidence? gen/boolean]
                (let [modified (if evidence? days-later right-after)
                      [s channel] (if (= :site side)
                                    [(change site) (as-channel site :modified modified)]
                                    [site (change (as-channel site :modified modified))])
                      origin (:origin (row (review s channel) [field]))]
                  (= origin (cond
                              (= :site side) :lipas
                              evidence? :ptv
                              ;; no evidence: PTV still has what was sent
                              :else :lipas)))))

(defspec taking-ptv-leaves-no-value-difference 200
  (prop/for-all [changes (gen/vector value-change-gen 1 4)]
                (let [channel (reduce (fn [c [_ side change]] (if (= :ptv side) (change c) c))
                                      (as-channel site :modified days-later)
                                      changes)
                      s (reduce (fn [s [_ side change]] (if (= :site side) (change s) s)) site changes)
                      rows (filter sut/takeable-in-bulk? (:rows (review s channel)))
                      taken (reduce #(sut/apply-review-writes %1 (sut/review-writes %1 %2 :ptv)) s rows)]
                  (empty? (filter sut/takeable-in-bulk? (:rows (review taken channel)))))))

(defspec keeping-a-choice-counts-until-ptv-changes 100
  (prop/for-all [[field _ change] (gen/such-that #(= :ptv (second %)) value-change-gen)]
                (let [r (review (change (as-channel site :modified days-later)))
                      x (row r [field])
                      choices {[field] {:choice :lipas :seen (sut/review-choice-seen x)}}
                      later (row (review (-> (as-channel site :modified "2026-10-09T08:00:00.000000")
                                             change
                                             (with-phones [["+358" "409999999" "fi"]])
                                             (assoc :emails [{:value "kolmas@kunta.fi" :language "fi"}])
                                             (update :webPages (fn [ps] (mapv #(assoc % :url "https://kolmas.fi") ps)))
                                             (set-street "Kolmas tie 3")))
                                 [field])]
                  (and (sut/review-choice choices x)
                       (nil? (sut/review-choice choices later))))))

(defn- with-fi-summary [channel text]
  (update channel :serviceChannelDescriptions
          (fn [ds] (mapv #(cond-> % (and (= "Summary" (:type %)) (= "fi" (:language %)))
                                  (assoc :value text))
                         ds))))

(deftest decisions-hold-against-ptvs-value-now-test
  ;; PTV changed the fi summary to T1; after the user decided, PTV changes
  ;; it again to T2. Nothing decided against T1 may be sent over T2 unseen.
  (let [k [:summary "fi"]
        t1 (review (with-fi-summary (as-channel site :modified days-later) "PTV:n T1"))
        t2 (review (with-fi-summary (as-channel site :modified "2026-10-09T08:00:00.000000") "PTV:n T2"))
        edit (fn [text] (assoc-in site [:ptv :summary :fi] text))
        annotated (fn [r draft] (row (annotate r draft) k))
        open? (fn [x choices] (= [x] (sut/open-review-rows [x] choices)))]

    (testing "PTV's version taken: decided, until PTV changes it again"
      (is (not (open? (annotated t1 (edit "PTV:n T1")) {})))
      (let [x (annotated t2 (edit "PTV:n T1"))]
        (is (= :edited (:current x)) "the edit holds neither side now")
        (is (open? x {}))))

    (testing "a kept version stops counting once the edit holds something else"
      (let [x (annotated t1 site)
            choices {k {:choice :lipas :seen (sut/review-choice-seen x)}}]
        (is (not (open? x choices)))
        (is (open? (annotated t1 (edit "Oma teksti")) choices))))

    (testing "a value of the user's own counts once confirmed, for that value and PTV's value of the time"
      (let [x (annotated t1 (edit "Oma teksti"))
            choices {k (sut/edited-choice x)}]
        (is (open? x {}) "an edit alone, e.g. written by AI generation, decides nothing")
        (is (not (open? x choices)))
        (is (open? (annotated t1 (edit "Oma teksti, jatkettu")) choices) "another value than the one confirmed")
        (is (open? (annotated t2 (edit "Oma teksti")) choices) "PTV changed after the confirmation"))))

  (testing "one of PTV's values taken into a one-slot field, then PTV's values change"
    (let [ch (fn [modified phones] (with-phones (as-channel site :modified modified) phones))
          taken (assoc site :phone-number "+358 401111111")
          before (row (annotate (review (ch days-later [["+358" "401111111" "fi"]])) taken) [:phone])
          after (row (annotate (review (ch "2026-10-09T08:00:00.000000"
                                           [["+358" "402222222" "fi"] ["+358" "403333333" "fi"]]))
                               taken)
                     [:phone])]
      (is (= :ptv (:current before)))
      (is (= [after] (sut/open-review-rows [after] {}))))))

(deftest sync-keeps-ptvs-address-and-phone-extras-test
  ;; Lipas has one street and one phone, without languages, extra text or a
  ;; charge: what PTV holds of those stays while it belongs to what Lipas
  ;; sends (D8's rule for postal addresses)
  (let [payload (sut/site->location site langs)
        channel (-> (as-channel site)
                    (update-in [:addresses 0 :streetAddress] assoc
                               :street [{:value "Urheilutie 4" :language "fi"}
                                        {:value "Sports Road 4" :language "en"}]
                               :additionalInformation [{:value "2. krs." :language "fi"}
                                                       {:value "Ingång B" :language "sv"}])
                    (with-phones [["+358" "858755700" "fi"]])
                    (update-in [:phoneNumbers 0] assoc
                               :additionalInformation "Vaihde" :serviceChargeType "FreeOfCharge"))
        kept (sut/with-ptv-extras payload channel)
        street (fn [d lang] (some #(when (= lang (:language %)) (:value %))
                                  (-> d :addresses first :streetAddress :street)))]
    (testing "the same address: PTV's street in another language and its extra text stay"
      (is (= "Urheilutie 4" (street kept "fi")) "the Finnish street is Lipas's")
      (is (= "Sports Road 4" (street kept "en")))
      (is (= [{:value "2. krs." :language "fi"}]
             (-> kept :addresses first :streetAddress :additionalInformation))
          "in the payload's languages only"))

    (testing "PTV's street number kept apart goes with its other-language street"
      (let [split (update-in channel [:addresses 0 :streetAddress] assoc
                             :street [{:value "Urheilutie" :language "fi"}
                                      {:value "Sports Road" :language "en"}]
                             :streetNumber "4")]
        (is (= "Sports Road 4" (street (sut/with-ptv-extras payload split) "en")))))

    (testing "another address in Lipas: PTV's extras belonged to the old one"
      (let [moved (sut/with-ptv-extras (sut/site->location (assoc-in site [:location :address] "Kolmas tie 3") langs)
                    channel)]
        (is (= "Kolmas tie 3" (street moved "en")))
        (is (nil? (-> moved :addresses first :streetAddress :additionalInformation)))))

    (testing "the phone with the number Lipas sends keeps PTV's information and charge"
      (is (= {:additionalInformation "Vaihde" :serviceChargeType "FreeOfCharge"}
             (select-keys (first (:phoneNumbers kept)) [:additionalInformation :serviceChargeType]))))

    (testing "another number doesn't"
      (let [other (sut/with-ptv-extras (sut/site->location (assoc site :phone-number "040 111 2222") langs)
                    channel)]
        (is (nil? (:additionalInformation (first (:phoneNumbers other)))))))

    (testing "kept extras are no difference in the review afterwards"
      (is (= [] (:rows (review (merge channel (select-keys kept [:addresses :phoneNumbers])))))))))

(defspec a-one-slot-field-ends-with-one-value 100
  (prop/for-all [numbers (gen/vector-distinct (gen/elements ["401111111" "402222222" "403333333"])
                                              {:min-elements 1 :max-elements 3})
                 pick gen/nat]
                (let [channel (with-phones (as-channel site :modified days-later)
                                (for [n numbers] ["+358" n "fi"]))
                      phone (row (review channel) [:phone])
                      value (nth (sut/ptv-values phone) (mod pick (count numbers)))
                      taken (sut/apply-review-writes site (sut/review-value-writes phone value))]
                  (= 1 (count (get (sut/location-values (sut/site->location taken langs)) [:phone]))))))

;;; Services (the Palvelut tab) ;;;

(deftest service-review-test
  (let [ptv {:name {:fi "Uimahallit"} :summary {:fi "PTV:n tiivistelmä"}
             :description {:fi "Kuvaus"} :user-instruction {:fi "Ohje"}}
        base (assoc ptv :summary {:fi "Lipaksen viimeksi viemä"})
        review (fn [edit base] (sut/service-review {:edit edit :ptv ptv :base base :locales [:fi]}))
        keys-of (fn [r] (into {} (map (juxt :key :origin)) (:rows r)))]
    (testing "nothing edited: no rows, whatever PTV did"
      (is (= [] (:rows (review ptv base))))
      (testing "but PTV's change since Lipas's last write is listed as kept"
        (is (= [{:field :summary :language "fi"}] (:ptv-changes (review ptv base))))))
    (testing "an edit of a field PTV didn't change is Lipas's"
      (is (= {[:description "fi"] :lipas}
             (keys-of (review (assoc-in ptv [:description :fi] "Uusi kuvaus") base)))))
    (testing "an edit of a field PTV changed too is a conflict to decide"
      (let [r (review (assoc-in ptv [:summary :fi] "Oma tiivistelmä") base)
            row (first (:rows r))]
        (is (= {[:summary "fi"] :both} (keys-of r)))
        (is (sut/review-choice-required? row))
        (is (= :lipas (:current row)) "the edit holds the user's text")
        (is (empty? (sut/open-review-rows (:rows r) {[:summary "fi"] {:choice :lipas
                                                                      :seen (sut/review-choice-seen row)}})))))
    (testing "without a base (Lipas hasn't written the service) every edit is Lipas's"
      (is (= {[:summary "fi"] :lipas}
             (keys-of (review (assoc-in ptv [:summary :fi] "Oma tiivistelmä") nil)))))
    (testing "taking PTV's text leaves no row"
      (is (= [] (:rows (review (assoc-in ptv [:summary :fi] (get-in ptv [:summary :fi])) base)))))
    (testing "whitespace PTV collapses is no difference"
      (is (= [] (:rows (review (assoc-in ptv [:summary :fi] "PTV:n  tiivistelmä ") base)))))))
