(ns lipas.data.ptv-test
  (:require [clojure.test :refer [deftest is testing]]
            [lipas.data.ptv :as sut]))

(deftest parse-phone-number
  (is (= {:prefix "+358"
          :number "1234567"}
         (sut/parse-phone-number "1234567")))

  (is (= {:prefix "+358"
          :number "441234567"}
         (sut/parse-phone-number "044 1234567")))

  (is (= {:prefix "+358"
          :number "441234567"}
         (sut/parse-phone-number "+358 044 1234567")))

  (is (= {:prefix "+1111"
          :number "441234567"}
         (sut/parse-phone-number "+1111 044 1234567")))

  (is (= {:prefix "+358"
          :number "81234567"}
         (sut/parse-phone-number "+35881234567")))

  (testing "finnish service numbers"
    (is (= {:is-finnish-service-number true
            :number "060012345"}
           (sut/parse-phone-number "0600 12345")))
    (is (= {:is-finnish-service-number true
            :number "11612345"}
           (sut/parse-phone-number "116 12345")))))

(deftest parse-phone-number-finnish-separators
  (testing "no separator"
    (is (= {:prefix "+358" :number "501234567"}
           (sut/parse-phone-number "0501234567"))))
  (testing "spaces"
    (is (= {:prefix "+358" :number "501234567"}
           (sut/parse-phone-number "050 1234567")))
    (is (= {:prefix "+358" :number "501234567"}
           (sut/parse-phone-number "050 123 4567"))))
  (testing "single dash — the Eurajoki regression case"
    (is (= {:prefix "+358" :number "443124267"}
           (sut/parse-phone-number "044-3124267")))
    (is (= {:prefix "+358" :number "501234567"}
           (sut/parse-phone-number "050-1234567"))))
  (testing "multiple dashes"
    (is (= {:prefix "+358" :number "501234567"}
           (sut/parse-phone-number "050-123-4567"))))
  (testing "dots"
    (is (= {:prefix "+358" :number "501234567"}
           (sut/parse-phone-number "050.123.4567"))))
  (testing "parentheses around area code"
    (is (= {:prefix "+358" :number "501234567"}
           (sut/parse-phone-number "(050) 123 4567")))
    (is (= {:prefix "+358" :number "501234567"}
           (sut/parse-phone-number "(050)1234567"))))
  (testing "slash"
    (is (= {:prefix "+358" :number "501234567"}
           (sut/parse-phone-number "050/1234567"))))
  (testing "mixed punctuation"
    (is (= {:prefix "+358" :number "501234567"}
           (sut/parse-phone-number "(050) 123-4567"))))
  (testing "leading/trailing whitespace and tabs"
    (is (= {:prefix "+358" :number "501234567"}
           (sut/parse-phone-number "  0501234567  ")))
    (is (= {:prefix "+358" :number "501234567"}
           (sut/parse-phone-number "\t050\t1234567"))))
  (testing "embedded label like 'puh.'"
    (is (= {:prefix "+358" :number "501234567"}
           (sut/parse-phone-number "puh. 050 1234567")))))

(deftest parse-phone-number-international-formats
  (testing "+358 with various separators"
    (is (= {:prefix "+358" :number "501234567"}
           (sut/parse-phone-number "+358 50 1234567")))
    (is (= {:prefix "+358" :number "501234567"}
           (sut/parse-phone-number "+358501234567")))
    (is (= {:prefix "+358" :number "501234567"}
           (sut/parse-phone-number "+358-50-1234567"))))
  (testing "00 international prefix is rewritten as +"
    (is (= {:prefix "+358" :number "501234567"}
           (sut/parse-phone-number "00358 50 1234567")))
    (is (= {:prefix "+358" :number "501234567"}
           (sut/parse-phone-number "00358501234567"))))
  (testing "explicit (0) trunk hint is dropped"
    (is (= {:prefix "+358" :number "501234567"}
           (sut/parse-phone-number "+358 (0)50 1234567"))))
  (testing "leftover trunk 0 after country code is dropped"
    (is (= {:prefix "+358" :number "501234567"}
           (sut/parse-phone-number "+358 0501234567"))))
  (testing "non-Finnish country codes preserved"
    (is (= {:prefix "+1111" :number "441234567"}
           (sut/parse-phone-number "+1111 044 1234567")))))

(deftest parse-phone-number-service-numbers-with-separators
  (testing "0600/0700/0800/0900 with various separators"
    (is (= {:is-finnish-service-number true :number "060012345"}
           (sut/parse-phone-number "0600 12345")))
    (is (= {:is-finnish-service-number true :number "070012345"}
           (sut/parse-phone-number "0700-12345")))
    (is (= {:is-finnish-service-number true :number "080012345"}
           (sut/parse-phone-number "0800.12345")))
    (is (= {:is-finnish-service-number true :number "090012345"}
           (sut/parse-phone-number "(0900) 12345"))))
  (testing "116 short codes"
    (is (= {:is-finnish-service-number true :number "11612345"}
           (sut/parse-phone-number "116 12345")))
    (is (= {:is-finnish-service-number true :number "116123"}
           (sut/parse-phone-number "116-123")))))

(deftest parse-phone-number-rejects-garbage
  (testing "nil"
    (is (nil? (sut/parse-phone-number nil))))
  (testing "blank strings"
    (is (nil? (sut/parse-phone-number "")))
    (is (nil? (sut/parse-phone-number "   ")))
    (is (nil? (sut/parse-phone-number "\t\t"))))
  (testing "non-string"
    (is (nil? (sut/parse-phone-number 1234567))))
  (testing "no digits at all"
    (is (nil? (sut/parse-phone-number "abc")))
    (is (nil? (sut/parse-phone-number "+")))
    (is (nil? (sut/parse-phone-number "(--)"))))
  (testing "country code only"
    (is (nil? (sut/parse-phone-number "+358"))))
  (testing "trunk zeros only"
    (is (nil? (sut/parse-phone-number "0")))
    (is (nil? (sut/parse-phone-number "+358 0"))))
  (testing "more than 20 digits is rejected (PTV's limit)"
    (is (nil? (sut/parse-phone-number "+358 12345678901234567890123")))))

(deftest parse-phone-number-multi-phone-fields
  (testing "comma-separated: take the first phone, discard the rest"
    (is (= {:prefix "+358" :number "447801245"}
           (sut/parse-phone-number "0447801245, 0447801357, 0447801448")))
    (is (= {:prefix "+358" :number "447801357"}
           (sut/parse-phone-number "044 7801357, 044 7801448, 044 7801245")))
    (is (= {:prefix "+358" :number "447801357"}
           (sut/parse-phone-number "044 780 1357, 044 780 1245, 044 7801 448"))))
  (testing "comma without space"
    (is (= {:prefix "+358" :number "501234567"}
           (sut/parse-phone-number "0501234567,0507654321"))))
  (testing "two-phone case (current parser concatenated them into a 19-digit blob)"
    (is (= {:prefix "+358" :number "501234567"}
           (sut/parse-phone-number "0501234567, 0507654321"))))
  (testing "semicolon-separated"
    (is (= {:prefix "+358" :number "501234567"}
           (sut/parse-phone-number "0501234567; 0507654321")))
    (is (= {:prefix "+358" :number "501234567"}
           (sut/parse-phone-number "0501234567;0507654321"))))
  (testing "pipe-separated"
    (is (= {:prefix "+358" :number "501234567"}
           (sut/parse-phone-number "0501234567 | 0507654321"))))
  (testing "newline-separated"
    (is (= {:prefix "+358" :number "501234567"}
           (sut/parse-phone-number "0501234567\n0507654321")))
    (is (= {:prefix "+358" :number "501234567"}
           (sut/parse-phone-number "0501234567\r\n0507654321"))))
  (testing "slash with surrounding whitespace IS a separator"
    (is (= {:prefix "+358" :number "931041761"}
           (sut/parse-phone-number "+358 9 310 41761 / 358 40 334 4216"))))
  (testing "slash without spaces is NOT a separator (in-number formatting)"
    (is (= {:prefix "+358" :number "501234567"}
           (sut/parse-phone-number "050/1234567"))))
  (testing "leading and trailing whitespace around the multi-phone field"
    (is (= {:prefix "+358" :number "501234567"}
           (sut/parse-phone-number "  0501234567 , 0507654321  "))))
  (testing "trailing separator with empty piece is ignored"
    (is (= {:prefix "+358" :number "501234567"}
           (sut/parse-phone-number "0501234567, ")))
    (is (= {:prefix "+358" :number "501234567"}
           (sut/parse-phone-number "0501234567,"))))
  (testing "leading empty piece (e.g. just a comma) is skipped"
    (is (= {:prefix "+358" :number "507654321"}
           (sut/parse-phone-number ", 0507654321"))))
  (testing "name + phone: skip the name piece, take the phone"
    (is (= {:prefix "+358" :number "405609840"}
           (sut/parse-phone-number "Harju Eeva, 040 560 9840")))
    (is (= {:prefix "+358" :number "400346097"}
           (sut/parse-phone-number "Ruuskanen Heino, 0400 346 097")))
    (is (= {:prefix "+358" :number "405609840"}
           (sut/parse-phone-number "Anne; 040 560 9840"))))
  (testing "all pieces digit-less still returns nil"
    (is (nil? (sut/parse-phone-number "Anne, Mikko, Liisa")))))

(deftest parse-phone-number-output-is-ptv-compatible
  (testing "every parsed :number matches PTV's ^\\d{1,20}$ regex"
    ;; PTV rejects anything that isn't 1-20 digits. Run a battery of
    ;; realistic free-form inputs through the parser and assert the
    ;; postcondition on every successful parse — this is the contract
    ;; that drives all the tests above.
    (let [ptv-number-re #"^\d{1,20}$"
          inputs ["1234567"
                  "0501234567" "050 1234567" "050 123 4567"
                  "050-1234567" "050-123-4567" "050.123.4567"
                  "(050) 1234567" "(050)1234567" "050/1234567"
                  "(050) 123-4567" "  0501234567  " "\t050\t1234567"
                  "puh. 050 1234567"
                  "+358 50 1234567" "+358501234567" "+358-50-1234567"
                  "00358 50 1234567" "00358501234567"
                  "+358 (0)50 1234567" "+358 0501234567"
                  "+1111 044 1234567" "044-3124267"
                  "0600 12345" "0700-12345" "0800.12345" "(0900) 12345"
                  "116 12345" "116-123"]]
      (doseq [in inputs]
        (let [parsed (sut/parse-phone-number in)]
          (is (some? parsed)
              (str "expected to parse: " (pr-str in)))
          (is (re-matches ptv-number-re (:number parsed))
              (str "PTV-incompatible :number for input " (pr-str in)
                   " → " (pr-str parsed))))))))

(deftest parse-www
  (testing "schemeless host gets https://"
    (is (= "https://example.com"
           (sut/parse-www "example.com")))
    (is (= "https://www.example.fi"
           (sut/parse-www "www.example.fi"))))

  (testing "existing scheme is preserved"
    (is (= "http://example.com"
           (sut/parse-www "http://example.com")))
    (is (= "https://example.com"
           (sut/parse-www "https://example.com")))
    (is (= "ftp://example.com"
           (sut/parse-www "ftp://example.com"))))

  (testing "leading and trailing whitespace is trimmed"
    (is (= "https://laaksontalli.com"
           (sut/parse-www "https://laaksontalli.com ")))
    (is (= "http://retkipaikka.fi/foo"
           (sut/parse-www " http://retkipaikka.fi/foo"))))

  (testing "scheme typos observed in real LIPAS data are repaired"
    (is (= "https://salo.fi/x"
           (sut/parse-www "hhttps://salo.fi/x")))
    (is (= "https://palaute.kuopio.fi"
           (sut/parse-www "htpps://palaute.kuopio.fi")))
    (is (= "https://palaute.kuopio.fi"
           (sut/parse-www "hpps://palaute.kuopio.fi")))
    (is (= "http://example.fi"
           (sut/parse-www "htpp://example.fi"))))

  (testing "internal commas in query strings and anchors are kept"
    (is (= "https://saimaageopark.fi/path/#filter=r-fullyTranslatedLangus-,r-openState-"
           (sut/parse-www "https://saimaageopark.fi/path/#filter=r-fullyTranslatedLangus-,r-openState-")))
    (is (= "https://x.fi/route/?id=37804892"
           (sut/parse-www "https://x.fi/route/?id=37804892"))))

  (testing "first URL is taken when several are separated by whitespace"
    (is (= "https://a.fi"
           (sut/parse-www "https://a.fi https://b.fi")))
    (is (= "https://a.fi"
           (sut/parse-www "https://a.fi, https://b.fi")))
    (is (= "https://www.salla.fi/"
           (sut/parse-www "https://www.salla.fi/      https://www.visitsalla.fi/"))))

  (testing "schemeless multi-URL: first host is salvaged"
    (is (= "https://www.sastamala.fi"
           (sut/parse-www "www.sastamala.fi, sastamala.sometec.fi")))
    (is (= "https://www.kaakonkaksikkoliikkuu.fi"
           (sut/parse-www "www.kaakonkaksikkoliikkuu.fi, www.virolahti.fi"))))

  (testing "schemeless with breadcrumb-style trailing junk"
    (is (= "https://www.rauma.fi"
           (sut/parse-www "www.rauma.fi › Koe kaupunki › Meri ja saaristo")))
    (is (= "https://www.sipoo.fi/"
           (sut/parse-www "www.sipoo.fi/ ulkoilusaaret"))))

  (testing "trailing separator punctuation is stripped"
    (is (= "https://a.fi"
           (sut/parse-www "https://a.fi,"))))

  (testing "unsalvageable input returns nil"
    (is (nil? (sut/parse-www nil)))
    (is (nil? (sut/parse-www "")))
    (is (nil? (sut/parse-www "   ")))
    (is (nil? (sut/parse-www "fkjglkaldkfgäkljhklfh"))
        "gibberish without a host shape")
    (is (nil? (sut/parse-www "013686511"))
        "phone number wrongly placed in :www field")
    (is (nil? (sut/parse-www "https:// pirkaa.fi/hiihto"))
        "space immediately after scheme — pathological"))

  (testing "values longer than 500 chars are rejected"
    (let [too-long (str "https://example.com/" (apply str (repeat 500 "x")))]
      (is (nil? (sut/parse-www too-long))))))

(deftest parse-email
  (is (= nil
         (sut/parse-email "foo")))

  (is (= "juho@example.com"
         (sut/parse-email "juho@example.com"))))

(defn- names [payload]
  (mapv (juxt :type :language :value) (:serviceChannelNames payload)))

(defn- desc-langs [payload type-v]
  (->> payload :serviceChannelDescriptions
       (filter #(= type-v (:type %)))
       (map :language)
       set))

(defn- build-location
  "Small helper that builds a ServiceLocation PUT payload for a test site
   using a pure coordinate transform, so tests stay CLJ/CLJS portable."
  [site]
  (sut/->ptv-service-location
    "org-x"
    (fn [[lon lat]] [lon lat])
    "2026-04-24T00:00:00.000Z"
    site))

(def ^:private base-site
  {:ptv {:languages ["fi" "se" "en"]
         :summary {:fi "summary-fi" :se "summary-sv" :en "summary-en"}
         :description {:fi "desc-fi"}}
   :search-meta {:location {:wgs84-point [0 0]}}
   :location {:city {:city-code 889} :address "Katu 1" :postal-code "99999"}})

(deftest ->ptv-service-location-names
  (testing "every declared language gets a Name entry"
    (testing "no localized names — sv/en fall back to fi"
      (is (= [["Name" "fi" "Halli"]
              ["Name" "sv" "Halli"]
              ["Name" "en" "Halli"]]
             (names (build-location (assoc base-site :name "Halli"))))))

    (testing "sv entered, en falls back to fi"
      (is (= [["Name" "fi" "Halli"]
              ["Name" "sv" "Hall"]
              ["Name" "en" "Halli"]]
             (names (build-location (assoc base-site
                                           :name "Halli"
                                           :name-localized {:se "Hall"}))))))

    (testing "blank sv counts as not entered → falls back to fi"
      (is (= [["Name" "fi" "Halli"]
              ["Name" "sv" "Halli"]
              ["Name" "en" "English name"]]
             (names (build-location (assoc base-site
                                           :name "Halli"
                                           :name-localized {:se "   " :en "English name"}))))))

    (testing "marketing name becomes AlternativeName/fi"
      (is (some #(= ["AlternativeName" "fi" "Hallikauppa"] %)
                (names (build-location (assoc base-site
                                              :name "Halli"
                                              :marketing-name "Hallikauppa")))))))

  (testing "displayNameType has one Name entry per declared org language"
    (is (= [{:type "Name" :language "fi"}
            {:type "Name" :language "sv"}
            {:type "Name" :language "en"}]
           (:displayNameType
             (build-location (assoc base-site :name "Halli"))))))

  (testing "descriptions: every declared language has a Summary and a Description"
    (let [p (build-location (assoc base-site :name "Halli"))]
      (is (= #{"fi" "sv" "en"} (desc-langs p "Summary"))
          "ptv map has summary in all three languages")
      (is (= #{"fi" "sv" "en"} (desc-langs p "Description"))
          "ptv map only has fi description, but sv/en fall back to fi"))))

(defn- area-codes [payload]
  (mapv :areaCodes (:areas payload)))

(deftest ->ptv-service-area-codes
  (testing "city-codes < 100 are zero-padded to 3 digits (PTV's expected format)"
    ;; Regression: Eurajoki (51) hit "The code '51' was not found!" because
    ;; LIPAS sent the raw int and PTV's codelist keys on the zero-padded form.
    (is (= [["051"]]
           (area-codes (sut/->ptv-service {:org-id "org-x"
                                           :city-codes [51]
                                           :sub-category-id 2200
                                           :languages ["fi"]}))))
    (is (= [["049"]]
           (area-codes (sut/->ptv-service {:org-id "org-x"
                                           :city-codes [49]
                                           :sub-category-id 2200
                                           :languages ["fi"]})))))

  (testing "city-codes >= 100 are emitted as-is (existing pilot orgs)"
    (is (= [["889"]]
           (area-codes (sut/->ptv-service {:org-id "org-x"
                                           :city-codes [889]
                                           :sub-category-id 2200
                                           :languages ["fi"]})))))

  (testing "mixed codes each get their own zero-padded entry"
    (is (= [["051"] ["889"]]
           (area-codes (sut/->ptv-service {:org-id "org-x"
                                           :city-codes [51 889]
                                           :sub-category-id 2200
                                           :languages ["fi"]}))))))

(def ^:private drift-site
  {:name "Halli"
   :ptv {:summary {:fi "summary-fi" :en "summary-en"}
         :description {:fi "desc-fi"}
         :service-ids ["svc-a"]}})

(def ^:private drift-channel
  {:serviceChannelNames [{:type "Name" :language "fi" :value "Halli"}
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

(def ^:private drift-services
  {"svc-a" {:serviceNames [{:type "Name" :language "fi" :value "Pallokentät"}]}
   "svc-b" {:serviceNames [{:type "Name" :language "fi" :value "Liikuntahallit"}]}})

(defn- by-field [drift k]
  (filter #(= k (:field %)) drift))

(deftest compute-service-channel-drift-test
  (testing "returns nil when channel hasn't been fetched"
    (is (nil? (sut/compute-service-channel-drift drift-site nil drift-services
                                                 ["fi" "se" "en"]))))

  (testing "returns empty when no drift"
    (let [synced-channel
          {:serviceChannelNames [{:type "Name" :language "fi" :value "Halli"}
                                 {:type "Name" :language "sv" :value "Halli"}
                                 {:type "Name" :language "en" :value "Halli"}]
           :serviceChannelDescriptions [{:type "Summary" :language "fi" :value "summary-fi"}
                                        {:type "Summary" :language "sv" :value "summary-fi"}
                                        {:type "Summary" :language "en" :value "summary-en"}
                                        {:type "Description" :language "fi" :value "desc-fi"}
                                        {:type "Description" :language "sv" :value "desc-fi"}
                                        {:type "Description" :language "en" :value "desc-fi"}]
           :services [{:service {:id "svc-a"}}]}]
      (is (= [] (sut/compute-service-channel-drift drift-site synced-channel
                                                   drift-services
                                                   ["fi" "se" "en"])))))

  (let [drift (sut/compute-service-channel-drift drift-site drift-channel
                                                 drift-services
                                                 ["fi" "se" "en"])]
    (testing "kunta-edited sv name appears as drift; LIPAS-pushed value uses fi fallback"
      (let [[name-drift] (by-field drift :name)]
        (is (= {:field :name :type "Name" :language "sv" :locale :se
                :lipas "Halli" :ptv "KUNTA-Hall"}
               name-drift))))

    (testing "marketing-name absent from LIPAS, kunta added it in PTV — also drift"
      ;; drift-site has no :marketing-name; drift-channel has none either
      (is (empty? (by-field drift :marketing-name))))

    (testing "kunta-added sv summary appears as drift"
      (let [[s] (by-field drift :summary)]
        (is (= "summary-fi" (:lipas s))
            "LIPAS would push fi-fallback for sv summary")
        (is (= "kunta-summary-sv" (:ptv s)))))

    (testing "service link drift carries human-readable names, not just UUIDs"
      (let [[svc] (by-field drift :services)]
        (is (= [{:id "svc-b" :name "Liikuntahallit"}] (:added svc))
            "PTV has svc-b that LIPAS doesn't")
        (is (= [] (:removed svc)))
        (is (= [{:id "svc-a" :name "Pallokentät"}] (:lipas svc)))
        (is (= #{"Liikuntahallit" "Pallokentät"}
               (set (map :name (:ptv svc)))))))

    (testing "unknown service id falls back to the id as its name"
      (let [site (assoc-in drift-site [:ptv :service-ids] ["svc-mystery"])
            drift (sut/compute-service-channel-drift site drift-channel
                                                     drift-services
                                                     ["fi" "se" "en"])
            [svc] (by-field drift :services)]
        (is (some #(= {:id "svc-mystery" :name "svc-mystery"} %) (:lipas svc)))))))

(deftest compute-service-channel-drift-marketing-name-test
  (testing "LIPAS marketing-name appears as drift when PTV has no AlternativeName"
    (let [site (assoc drift-site :marketing-name "Hallikauppa")
          drift (sut/compute-service-channel-drift site drift-channel
                                                   drift-services
                                                   ["fi" "se" "en"])
          [m] (by-field drift :marketing-name)]
      (is (= {:field :marketing-name :type "AlternativeName" :language "fi" :locale :fi
              :lipas "Hallikauppa" :ptv nil}
             m))))

  (testing "blank LIPAS marketing-name + non-blank PTV AlternativeName → drift (LIPAS will clear it)"
    (let [channel (update drift-channel :serviceChannelNames conj
                          {:type "AlternativeName" :language "fi" :value "PTV-marketing"})
          drift (sut/compute-service-channel-drift drift-site channel
                                                   drift-services
                                                   ["fi" "se" "en"])
          [m] (by-field drift :marketing-name)]
      (is (= "PTV-marketing" (:ptv m)))
      (is (nil? (:lipas m))))))

(deftest get-all-pages-test
  (is (= {:pageCount 2
          :itemList ["a" "b" "h" "i"]}
         (sut/get-all-pages (fn [i]
                              (case i
                                1 {:pageNumber 1
                                   :pageCount 2
                                   :itemList ["a" "b"]}
                                2 {:pageNumber 2
                                   :pageCount 2
                                   :itemList ["h" "i"]}))))))
