(ns lipas.data.ptv-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [lipas.data.ptv :as sut]
            [lipas.schema.sports-sites.ptv :as ptv-schema]
            [malli.core :as m]
            [malli.error :as me]))

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

  (testing "additional scheme-shape repairs (data-driven, verified against PTV test API)"
    (is (= "https://korsholm.fi/valfard"
           (sut/parse-www "https:/korsholm.fi/valfard"))
        "single-slash scheme")
    (is (= "http://www.multia.fi"
           (sut/parse-www "http//www.multia.fi"))
        "missing colon after scheme")
    (is (= "http://www.virolahti.fi"
           (sut/parse-www "http.//www.virolahti.fi"))
        "period typed where colon was meant")
    (is (= "https://www.jamsa.fi/vapaa-aika"
           (sut/parse-www "https://whttps://www.jamsa.fi/vapaa-aika"))
        "doubled scheme prefix")
    (is (= "https://www.iisalmi.fi/Suomeksi/Palvelut"
           (sut/parse-www "ps://www.iisalmi.fi/Suomeksi/Palvelut"))
        "truncated `ps://` scheme")
    (is (= "https://www.pietarsaari.fi"
           (sut/parse-www "www. pietarsaari.fi"))
        "schemeless host with space after www.")
    (is (= "https://www.pietarsaari.fi"
           (sut/parse-www "https://www. pietarsaari.fi"))
        "scheme present, space after www.")
    (is (= "https://www.vihti.fi"
           (sut/parse-www "www,vihti.fi"))
        "comma typed where dot was meant"))

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

  (testing "email addresses are not web pages"
    ;; Regression: ~480 :reservations-link values are emails, which became
    ;; `https://tilavaraukset@vihti.fi`.
    (is (nil? (sut/parse-www "tilavaraukset@vihti.fi")))
    (is (nil? (sut/parse-www "Liikuntasihteeri: sari.kautto@vierema.fi")))
    (is (nil? (sut/parse-www "https://info@example.fi")))
    (is (= "https://x.fi/varaa?email=a@b.fi"
           (sut/parse-www "https://x.fi/varaa?email=a@b.fi"))
        "an @ after the host is part of the URL"))

  (testing "values longer than 500 chars are rejected"
    (let [too-long (str "https://example.com/" (apply str (repeat 500 "x")))]
      (is (nil? (sut/parse-www too-long))))))

(deftest parse-email
  (is (= nil
         (sut/parse-email "foo")))

  (is (= "juho@example.com"
         (sut/parse-email "juho@example.com")))

  (testing "uppercase characters in local part (PTV accepts)"
    (is (= "Vantaa-info@vantaa.fi"
           (sut/parse-email "Vantaa-info@vantaa.fi")))
    (is (= "Juha.Husu@ruokolahti.fi"
           (sut/parse-email "Juha.Husu@ruokolahti.fi"))))

  (testing "Unicode characters in local part (PTV accepts)"
    (is (= "kenttämiehet@naantali.fi"
           (sut/parse-email "kenttämiehet@naantali.fi")))
    (is (= "sorsapuistonurheilukenttä@tampere.fi"
           (sut/parse-email "sorsapuistonurheilukenttä@tampere.fi"))))

  (testing "Unicode characters in domain — IDN (PTV accepts)"
    (is (= "info@itäharjunkuntosali.fi"
           (sut/parse-email "info@itäharjunkuntosali.fi"))))

  (testing "surrounding whitespace is trimmed"
    (is (= "admin@example.fi"
           (sut/parse-email " admin@example.fi  "))))

  (testing "blank or missing input"
    (is (nil? (sut/parse-email nil)))
    (is (nil? (sut/parse-email "")))
    (is (nil? (sut/parse-email "   ")))))

(deftest parse-postal-code
  (testing "5-digit input passes through"
    (is (= "00100" (sut/parse-postal-code "00100")))
    (is (= "91900" (sut/parse-postal-code "91900"))))

  (testing "5-digit token is extracted from real LIPAS shapes"
    (is (= "82300" (sut/parse-postal-code "82300 Rääkkylä")))
    (is (= "20750" (sut/parse-postal-code "20750 Turku")))
    (is (= "90670" (sut/parse-postal-code "Oulu 90670")))
    (is (= "00100" (sut/parse-postal-code "  00100  "))))

  (testing "values without a 5-digit run return nil"
    (is (nil? (sut/parse-postal-code nil)))
    (is (nil? (sut/parse-postal-code "")))
    (is (nil? (sut/parse-postal-code "abc")))
    (is (nil? (sut/parse-postal-code "123")))))

(deftest parse-address
  (testing "short addresses are passed through, trimmed"
    (is (= "Alapääntie 7" (sut/parse-address "Alapääntie 7")))
    (is (= "Alapääntie 7" (sut/parse-address "  Alapääntie 7  "))))

  (testing "blank or missing input"
    (is (nil? (sut/parse-address nil)))
    (is (nil? (sut/parse-address "")))
    (is (nil? (sut/parse-address "   "))))

  (testing "over-100 truncates at the rightmost natural break"
    (is (= "Autiolahdentie 120"
           (sut/parse-address
             "Autiolahdentie 120, mistä opastus parkkialueelle. Partiomajalta on noin 550 metrin kävely parkkialueelle.")))
    (let [no-breaks (apply str (repeat 120 "a"))
          out (sut/parse-address no-breaks)]
      (is (= 100 (count out)) "hard truncate when no breaks fit"))))

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
         :description {:fi "desc-fi" :se "desc-sv" :en "desc-en"}}
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
      (is (= #{"fi" "sv" "en"} (desc-langs p "Summary")))
      (is (= #{"fi" "sv" "en"} (desc-langs p "Description")))))

  (testing "a language without its own summary and description is not declared"
    ;; Regression: an org enabling English pushed the Finnish texts to PTV
    ;; labelled as English for every site that had no English texts yet.
    (doseq [[label ptv] [["no en texts" {:summary {:fi "s-fi" :se "s-sv"}
                                         :description {:fi "d-fi" :se "d-sv"}}]
                         ["en summary only" {:summary {:fi "s-fi" :se "s-sv" :en "s-en"}
                                             :description {:fi "d-fi" :se "d-sv"}}]
                         ["blank en description" {:summary {:fi "s-fi" :se "s-sv" :en "s-en"}
                                                  :description {:fi "d-fi" :se "d-sv" :en "  "}}]]]
      (let [p (build-location (-> base-site
                                  (assoc :name "Halli")
                                  (update :ptv merge ptv)))]
        (is (= #{"fi" "sv"} (set (:languages p))) label)
        (is (= #{"fi" "sv"} (desc-langs p "Summary")) label)
        (is (= #{"fi" "sv"} (desc-langs p "Description")) label)
        (is (= #{"fi" "sv"} (set (map :language (:displayNameType p)))) label)
        (is (not-any? #(= "en" (:language %)) (:serviceChannelNames p)) label)))))

(defn- web-pages [payload]
  (mapv (juxt :language :orderNumber :url :value) (:webPages payload)))

(deftest ->ptv-service-location-web-pages
  (let [site (assoc base-site
                    :name "Halli"
                    :www "www.halli.fi"
                    :reservations-link "https://varaa.halli.fi")]
    (testing "both pages in every declared language, named in that language"
      (is (= [["fi" 1 "https://www.halli.fi" "Liikuntapaikan verkko-osoite"]
              ["fi" 2 "https://varaa.halli.fi" "Tilavarausten verkkosivu"]
              ["sv" 1 "https://www.halli.fi" "Idrottsplatsens webbadress"]
              ["sv" 2 "https://varaa.halli.fi" "Webbplats för lokalbokning"]
              ["en" 1 "https://www.halli.fi" "Sports facility website"]
              ["en" 2 "https://varaa.halli.fi" "Facility booking website"]]
             (web-pages (build-location site))))
      (is (not (contains? (build-location site) :deleteAllWebPages))))

    (testing "web page languages are exactly the payload's languages"
      ;; PTV rejects a page in an undeclared language as an incomplete new
      ;; language version — and in PTV "se" is Northern Sami, not Swedish.
      (doseq [[label langs ptv] [["fi only" ["fi"] {}]
                                 ["sv lacks texts" ["fi" "se" "en"]
                                  {:summary {:fi "s" :en "s"} :description {:fi "d" :en "d"}}]
                                 ["Swedish-only org" ["se"] {}]
                                 ["fi + se" ["fi" "se"] {}]]]
        (let [p (build-location (-> site
                                    (assoc-in [:ptv :languages] langs)
                                    (update :ptv merge ptv)))
              page-langs (set (map :language (:webPages p)))]
          (is (seq page-langs) label)
          (is (= (set (:languages p)) page-langs) label)
          (is (not (contains? page-langs "se")) label)
          (is (every? #(not (str/blank? (:value %))) (:webPages p)) label))))

    (testing "a reservations link equal to :www is sent once"
      (is (= [["fi" 1 "https://www.halli.fi" "Liikuntapaikan verkko-osoite"]]
             (web-pages (build-location (assoc-in (assoc site :reservations-link "https://www.halli.fi")
                                                  [:ptv :languages] ["fi"]))))))

    (testing "an unusable reservations link leaves just the :www page"
      (doseq [v [nil "" "Vapaasti käytettävissä" "040 123 4567" "tilavaraukset@halli.fi"]]
        (is (= ["https://www.halli.fi"]
               (->> (build-location (-> site
                                        (assoc :reservations-link v)
                                        (assoc-in [:ptv :languages] ["fi"])))
                    :webPages
                    (map :url)))
            (pr-str v))))

    (testing "reservations link alone is page 1"
      (is (= [["fi" 1 "https://varaa.halli.fi" "Tilavarausten verkkosivu"]]
             (web-pages (build-location (-> site
                                            (dissoc :www)
                                            (assoc-in [:ptv :languages] ["fi"])))))))

    (testing "no usable link deletes all web pages, without a :webPages key"
      ;; deleteAllWebPages only works when :webPages is absent.
      (let [p (build-location (-> site (dissoc :www) (assoc :reservations-link "ei ole")))]
        (is (true? (:deleteAllWebPages p)))
        (is (not (contains? p :webPages)))))

    (testing "every name fits PTV's 100-char limit"
      (doseq [[_ by-lang] sut/web-page-names
              [_ n] by-lang]
        (is (<= 1 (count n) 100) n)))))

(deftest effective-languages-test
  (let [site (fn [summary description] {:ptv {:summary summary :description description}})]
    (testing "fi is always published, whatever its texts"
      (is (= ["fi"] (sut/effective-languages (site {} {}) ["fi"]))))
    (testing "se/en only with both a summary and a description"
      (is (= ["fi" "en"] (sut/effective-languages (site {:fi "a" :se "b" :en "c"}
                                                        {:fi "a" :en "c"})
                                                  ["fi" "se" "en"])))
      (is (= ["fi"] (sut/effective-languages (site {:fi "a" :en ""} {:fi "a" :en "c"})
                                             ["fi" "en"]))))
    (testing "languages the org doesn't support are never published"
      (is (= ["fi"] (sut/effective-languages (site {:fi "a" :en "c"} {:fi "a" :en "c"})
                                             ["fi"]))))))

(defn- area-codes [payload]
  (mapv :areaCodes (:areas payload)))

(deftest ->ptv-service-languages
  ;; Regression: a language without texts was sent with the placeholder
  ;; "TODO: Value missing!", which PTV published as the service's text. With
  ;; only Finnish texts in LIPAS (Uusikaupunki's 12 LIPAS-created services),
  ;; enabling English would have published it for every service synced.
  (let [base {:org-id "org-x" :city-codes [895] :sub-category-id 2200
              :languages ["fi" "se" "en"]
              :summary {:fi "s-fi"} :description {:fi "d-fi"} :user-instruction {:fi "u-fi"}}
        descs (fn [p] (set (map (juxt :type :language) (:serviceDescriptions p))))
        name-langs (fn [p] (set (map :language (:serviceNames p))))]
    (testing "Finnish-only texts: only the Finnish version, no placeholder"
      (let [p (sut/->ptv-service base)]
        (is (= #{"fi"} (:languages p)))
        (is (= #{"fi"} (name-langs p)))
        (is (= #{["Summary" "fi"] ["Description" "fi"] ["UserInstruction" "fi"]} (descs p)))
        (is (not-any? #(= sut/placeholder (:value %)) (:serviceDescriptions p)))))

    (testing "a language with summary and description is published; a missing instruction is left out"
      (let [p (sut/->ptv-service (-> base
                                     (assoc-in [:summary :en] "s-en")
                                     (assoc-in [:description :en] "d-en")))]
        (is (= #{"fi" "en"} (:languages p)))
        (is (= #{"fi" "en"} (name-langs p)))
        (is (= #{["Summary" "fi"] ["Description" "fi"] ["UserInstruction" "fi"]
                 ["Summary" "en"] ["Description" "en"]}
               (descs p)))))

    (testing "a summary alone doesn't publish the language"
      (is (= #{"fi"} (:languages (sut/->ptv-service (assoc-in base [:summary :se] "s-sv"))))))))

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

(deftest normalize-ws-test
  (testing "nil / blank / whitespace-only collapse to nil"
    (is (nil? (sut/normalize-ws nil)))
    (is (nil? (sut/normalize-ws "")))
    (is (nil? (sut/normalize-ws "   ")))
    (is (nil? (sut/normalize-ws "\t \t")))
    (is (nil? (sut/normalize-ws "\n\n")))
    (is (nil? (sut/normalize-ws "  \n  \n  "))))

  (testing "plain text is unchanged"
    (is (= "hello" (sut/normalize-ws "hello")))
    (is (= "hello world" (sut/normalize-ws "hello world"))))

  (testing "leading/trailing whitespace is trimmed"
    (is (= "hello" (sut/normalize-ws "  hello  ")))
    (is (= "hello" (sut/normalize-ws "\t hello \n")))
    (is (= "a b" (sut/normalize-ws "\n  a b  \n"))))

  (testing "internal horizontal whitespace runs collapse to a single space"
    ;; This is the PTV double-space-in-name case.
    (is (= "a b" (sut/normalize-ws "a  b")))
    (is (= "a b" (sut/normalize-ws "a     b")))
    (is (= "a b" (sut/normalize-ws "a\tb")))
    (is (= "a b" (sut/normalize-ws "a\t\tb")))
    (is (= "a b" (sut/normalize-ws "a \t b")))
    (is (= "a b c" (sut/normalize-ws "a   b   c"))))

  (testing "line endings are normalized to LF"
    (is (= "a\nb" (sut/normalize-ws "a\r\nb")))
    (is (= "a\nb" (sut/normalize-ws "a\rb")))
    (is (= "a\nb\nc" (sut/normalize-ws "a\r\nb\rc"))))

  (testing "newlines (paragraph structure) are preserved"
    (is (= "a\nb" (sut/normalize-ws "a\nb")))
    (is (= "a\n\nb" (sut/normalize-ws "a\n\nb")))
    ;; horizontal whitespace hugging a newline is dropped, the newline stays
    (is (= "a\nb" (sut/normalize-ws "a  \n  b")))
    (is (= "a\nb" (sut/normalize-ws "a\t\n\tb")))
    (is (= "a\n\nb" (sut/normalize-ws "a \n \n b")))
    ;; a blank paragraph break survives even with surrounding spaces
    (is (= "para one\n\npara two" (sut/normalize-ws "para one  \n\n  para two"))))

  (testing "combination: collapse horizontal ws but keep multi-paragraph layout"
    (is (= "Title here\n\nBody line one\nBody line two"
           (sut/normalize-ws "  Title   here \r\n\r\n Body  line one\r\nBody\tline two  "))))

  (testing "idempotent"
    (doseq [s ["a  b" "a \n b" "  x\ty  " "p\r\n\r\nq" "a\n\n\nb"]]
      (is (= (sut/normalize-ws s)
             (sut/normalize-ws (sut/normalize-ws s)))
          (str "idempotent for " (pr-str s)))))

  (testing "the real PTV name case: double space vs PTV-collapsed single space compare equal"
    (is (= (sut/normalize-ws "Rantakylän stadionalueen  puolikota")
           (sut/normalize-ws "Rantakylän stadionalueen puolikota")
           "Rantakylän stadionalueen puolikota"))))

(deftest texts-match?-locales-test
  (testing "a draft in a language the org doesn't publish isn't a difference"
    ;; Regression: a generated service has fi/se/en texts; an org publishing
    ;; fi+en never sends the Swedish, so the panel showed differences forever.
    (let [draft {:summary {:fi "a" :se "b" :en "c"}}
          ptv {:summary {:fi "a" :en "c"}}]
      (is (not (sut/texts-match? draft ptv)))
      (is (sut/texts-match? draft ptv [:summary] [:fi :en]))
      (is (not (sut/texts-match? (assoc-in draft [:summary :en] "changed") ptv
                                 [:summary] [:fi :en]))))))

(deftest texts-match?-whitespace-test
  (testing "texts differing only by collapsible whitespace match"
    (is (sut/texts-match? {:summary {:fi "a  b"}}
                          {:summary {:fi "a b"}}
                          [:summary]))
    (is (sut/texts-match? {:description {:fi "line1\r\n\r\nline2"}}
                          {:description {:fi "line1\n\nline2"}}
                          [:description]))
    ;; nil vs all-whitespace are treated as equal (both normalize to nil)
    (is (sut/texts-match? {:summary {:fi nil}}
                          {:summary {:fi "   "}}
                          [:summary])))
  (testing "genuine content differences still don't match"
    (is (not (sut/texts-match? {:summary {:fi "a b"}}
                               {:summary {:fi "a c"}}
                               [:summary])))))

(deftest compute-service-channel-drift-whitespace-test
  (let [ptv-channel {:serviceChannelNames [{:type "Name" :language "fi" :value "Rantakylä stadion"}]
                     :serviceChannelDescriptions [{:type "Summary" :language "fi" :value "Tiivistelmä"}
                                                  {:type "Description" :language "fi" :value "Kuvaus rivi 1\n\nrivi 2"}]
                     :services []}
        base-site {:ptv {:summary {:fi "Tiivistelmä"}
                         :description {:fi "Kuvaus rivi 1\n\nrivi 2"}
                         :service-ids []}}]
    (testing "PTV-collapsed whitespace in the name is NOT reported as drift"
      (let [site (assoc base-site :name "Rantakylä  stadion")] ; double space, PTV stored single
        (is (empty? (sut/compute-service-channel-drift site ptv-channel {} ["fi"])))))

    (testing "whitespace-only differences in description are NOT drift"
      (let [site (assoc base-site
                        :name "Rantakylä stadion"
                        :ptv (assoc (:ptv base-site)
                                    :description {:fi "Kuvaus   rivi 1\r\n\r\nrivi 2"}))]
        (is (empty? (sut/compute-service-channel-drift site ptv-channel {} ["fi"])))))

    (testing "a genuine name change IS still reported as drift"
      (let [site (assoc base-site :name "Eri nimi")
            drift (sut/compute-service-channel-drift site ptv-channel {} ["fi"])]
        (is (seq drift))
        (is (= #{:name} (set (map :field drift))))))))

(deftest sync-status-unlink-test
  (let [ctx {:org-id "o" :types {} :org-defaults {} :org-langs ["fi"]}
        status (fn [site] (:sync-status (sut/sports-site->ptv-input ctx {} {} site)))
        base {:lipas-id 1 :name "X" :event-date "T1"
              :ptv {:last-sync "T1"
                    :summary {:fi "aaaaaa"}
                    :description {:fi "bbbbbb"}}}]
    (testing "synced, linked and up to date is :ok"
      (is (= :ok (status (assoc-in base [:ptv :service-channel-ids] ["C1"])))))

    (testing "never-synced (no last-sync) is :not-synced"
      (is (= :not-synced
             (status (-> base
                         (update :ptv dissoc :last-sync)
                         (assoc-in [:ptv :service-channel-ids] []))))))

    (testing "unlinking a previously-synced site re-activates sync (:out-of-date)"
      ;; Clearing the single-select autocomplete can leave service-channel-ids
      ;; as [], [nil] or [""] — all must be detected as a removed link.
      (doseq [cleared [[] [nil] [""] ["   "]]]
        (is (= :out-of-date
               (status (assoc-in base [:ptv :service-channel-ids] cleared)))
            (str "cleared as " (pr-str cleared)))))))

(deftest sync-status-languages-test
  ;; An org enabling a language must make its already-synced sites need a sync
  ;; once they have texts in it. Before, change detection compared only the
  ;; languages stored at the last sync (["fi"]), so English texts never
  ;; activated the sync button.
  (let [status (fn [org-langs channels site]
                 (:sync-status (sut/sports-site->ptv-input
                                 {:org-id "o" :types {} :org-defaults {} :org-langs org-langs}
                                 channels {} site)))
        base {:lipas-id 1 :name "X" :event-date "T1"
              :ptv {:last-sync "T1"
                    :languages ["fi"]
                    :service-channel-ids ["C1"]
                    :summary {:fi "aaaaaa"}
                    :description {:fi "bbbbbb"}}}
        with-en (-> base
                    (assoc-in [:ptv :summary :en] "summary")
                    (assoc-in [:ptv :description :en] "description"))
        fi-channel {"C1" {:id "C1"
                          :languages ["fi"]
                          :serviceChannelNames [{:type "Name" :language "fi" :value "X"}]
                          :serviceChannelDescriptions [{:type "Summary" :language "fi" :value "aaaaaa"}
                                                       {:type "Description" :language "fi" :value "bbbbbb"}]}}]
    (testing "org enabled en, site has no en texts yet: nothing to sync"
      (is (= :ok (status ["fi" "en"] {} base)))
      (is (= :ok (status ["fi" "en"] fi-channel base))))

    (testing "en texts written: needs a sync, channel cached or not"
      (is (= :out-of-date (status ["fi" "en"] {} with-en)))
      (is (= :content-drift (status ["fi" "en"] fi-channel with-en))))

    (testing "en texts in LIPAS but the org doesn't publish en: nothing to sync"
      (is (= :ok (status ["fi"] {} with-en)))
      (is (= :ok (status ["fi"] fi-channel with-en))))

    (testing "a language published in PTV that LIPAS would no longer send needs a sync"
      (is (= :out-of-date (status ["fi"] {} (assoc-in with-en [:ptv :languages] ["fi" "en"])))))

    (testing ":languages reports what the next sync publishes"
      (is (= ["fi" "en"] (:languages (sut/sports-site->ptv-input
                                       {:types {} :org-langs ["fi" "en"]} {} {} with-en))))
      (is (= ["fi"] (:languages (sut/sports-site->ptv-input
                                  {:types {} :org-langs ["fi" "en"]} {} {} base)))))))

(deftest unpublished-ptv-languages-test
  ;; Linking a site to an existing PTV service location whose texts exist in
  ;; languages LIPAS won't publish: the first sync removes them (Uusikaupunki
  ;; lost their English this way). The UI warns from this.
  (let [channel (fn [& langs]
                  {:serviceChannelNames (for [l langs] {:type "Name" :language l :value "n"})
                   :serviceChannelDescriptions (for [l langs] {:type "Summary" :language l :value "s"})})
        fi-site {:ptv {:summary {:fi "s"} :description {:fi "d"}}}
        en-site {:ptv {:summary {:fi "s" :en "s"} :description {:fi "d" :en "d"}}}]
    (testing "nothing to warn about"
      (is (nil? (sut/unpublished-ptv-languages fi-site nil ["fi"])) "no channel")
      (is (nil? (sut/unpublished-ptv-languages fi-site (channel "fi") ["fi"])))
      (is (nil? (sut/unpublished-ptv-languages en-site (channel "fi" "en") ["fi" "en"]))))

    (testing "a language the org hasn't enabled"
      (is (= {:not-enabled ["en" "se"]}
             (sut/unpublished-ptv-languages fi-site (channel "fi" "sv" "en") ["fi"]))
          "PTV's sv is LIPAS se"))

    (testing "an enabled language the site has no texts in"
      (is (= {:missing-texts ["en"]}
             (sut/unpublished-ptv-languages fi-site (channel "fi" "en") ["fi" "en"]))))

    (testing "a language LIPAS doesn't support at all"
      (is (= {:not-enabled ["RU"]}
             (sut/unpublished-ptv-languages fi-site (channel "fi" "ru") ["fi"])))
      (is (= {:not-enabled ["SE"]}
             (sut/unpublished-ptv-languages fi-site (channel "fi" "se") ["fi"]))
          "PTV's se is Northern Sámi, not LIPAS se (Swedish)"))

    (testing "blank PTV values don't count as texts"
      (is (nil? (sut/unpublished-ptv-languages
                  fi-site
                  {:serviceChannelNames [{:type "Name" :language "fi" :value "n"}
                                         {:type "Name" :language "en" :value " "}]}
                  ["fi"]))))))

(deftest sync-status-archived-resync-test
  (let [ctx {:org-id "o" :types {} :org-defaults {} :org-langs ["fi"]}
        status (fn [site] (:sync-status (sut/sports-site->ptv-input ctx {} {} site)))
        ;; Archived in PTV but still linked, event-date == last-sync (the
        ;; archive). service-channels {} so the archived channel can't be diffed.
        base {:lipas-id 1 :name "X" :event-date "T1"
              :ptv {:last-sync "T1"
                    :publishing-status "Deleted"
                    :service-channel-ids ["C1"]
                    :summary {:fi "aaaaaa"}
                    :description {:fi "bbbbbb"}}}]
    (testing "re-enabling an archived site needs a resurrect sync (:out-of-date)"
      (is (= :out-of-date (status (assoc-in base [:ptv :sync-enabled] true)))))

    (testing "an archived site left disabled is not forced to needs-sync"
      (is (= :ok (status (assoc-in base [:ptv :sync-enabled] false)))))

    (testing "a published, up-to-date site stays :ok"
      (is (= :ok (status (-> base
                             (assoc-in [:ptv :publishing-status] "Published")
                             (assoc-in [:ptv :sync-enabled] true))))))))

(deftest resolve-missing-services-test
  ;; Shared by the FE (wizard "missing services" list) and the BE
  ;; (sync-ptv! guard). A sub-category needs a new service ONLY when the
  ;; site has no service-id yet AND no LIPAS-managed service exists for it.
  (let [org-id "org-1"
        managed-source-id (sut/->service-source-id org-id 2100)
        ;; services keyed by :sourceId (as get-org-services / app-db hold them).
        ;; A service the org created in PTV directly has :sourceId nil.
        services {managed-source-id {:id "managed" :sourceId managed-source-id}
                  "native" {:id "native" :sourceId nil}}
        site (fn [service-ids] {:service-ids service-ids
                                :sub-category-id 2100
                                :sub-category "Kuntoilukeskukset ja liikuntasalit"})]

    (testing "site with no service-ids and no managed service -> reported missing"
      (is (= [{:source-id managed-source-id
               :sub-category "Kuntoilukeskukset ja liikuntasalit"
               :sub-category-id 2100}]
             (sut/resolve-missing-services org-id {} [(site [])]))))

    (testing "site already referencing a service is never missing (origin-agnostic)"
      ;; native (no LIPAS sourceId), managed, or any id all count as satisfied.
      (is (empty? (sut/resolve-missing-services org-id services [(site ["native"])])))
      (is (empty? (sut/resolve-missing-services org-id {} [(site ["any-existing-id"])]))))

    (testing "managed service exists -> not missing even with no service-id on the site yet"
      (is (empty? (sut/resolve-missing-services org-id services [(site [])]))))

    (testing "dedups and only reports genuinely missing sub-categories"
      (is (empty? (sut/resolve-missing-services org-id services [(site []) (site [])]))))))
(deftest audit-field-state-test
  (testing "no verdict -> :pending"
    (is (= :pending (sut/audit-field-state nil {:fi "x"})))
    (is (= :pending (sut/audit-field-state {:feedback ""} {:fi "x"}))))

  (testing "verdict without snapshot is grandfathered as unchanged"
    (is (= :approved (sut/audit-field-state {:status "approved" :feedback ""} {:fi "x"})))
    (is (= :changes-requested (sut/audit-field-state {:status "changes-requested" :feedback "fix"} {:fi "x"}))))

  (testing "verdict with matching snapshot"
    (is (= :approved (sut/audit-field-state {:status "approved"
                                             :feedback ""
                                             :audited-content {:fi "x"}}
                                            {:fi "x"}))))

  (testing "approved content changed since verdict -> :stale (flagged, still approved)"
    (is (= :stale (sut/audit-field-state {:status "approved"
                                          :feedback ""
                                          :audited-content {:fi "x"}}
                                         {:fi "y"}))))

  (testing "changes-requested content changed since verdict -> :fixed (municipality responded)"
    (is (= :fixed (sut/audit-field-state {:status "changes-requested"
                                          :feedback "fix"
                                          :audited-content {:fi "x"}}
                                         {:fi "y"}))))

  (testing "only the Finnish text is audited: other languages never change a verdict"
    (is (= :approved (sut/audit-field-state {:status "approved"
                                             :feedback ""
                                             :audited-content {:fi "x" :se "gammal"}}
                                            {:fi "x" :se "ny svensk text"})))
    ;; e.g. a language version not re-published after a PTV archive restore
    (is (= :approved (sut/audit-field-state {:status "approved"
                                             :feedback ""
                                             :audited-content {:fi "x" :se "text" :en "text"}}
                                            {:fi "x"})))
    ;; an edited translation is not a fix of the Finnish text
    (is (= :changes-requested (sut/audit-field-state {:status "changes-requested"
                                                      :feedback "fix"
                                                      :audited-content {:fi "x" :en "old"}}
                                                     {:fi "x" :en "new"}))))

  (testing "whitespace-only differences do not count as change"
    (is (= :approved (sut/audit-field-state {:status "approved"
                                             :feedback ""
                                             :audited-content {:fi "x  y\r\nz"}}
                                            {:fi " x y\nz "})))
    (is (= :changes-requested (sut/audit-field-state {:status "changes-requested"
                                                      :feedback "fix"
                                                      :audited-content {:fi "x"}}
                                                     {:fi "x "}))))

  (testing "paragraph structure is content"
    (is (= :stale (sut/audit-field-state {:status "approved"
                                          :feedback ""
                                          :audited-content {:fi "x y"}}
                                         {:fi "x\ny"})))))

(deftest audit-bucket-test
  (let [fields (fn [svc] (sut/service-audit-fields svc))
        svc {:summary {:fi "tiivistelmä"}
             :description {:fi "kuvaus"}
             :user-instruction {:fi "ohje"}}
        approved (fn [content] {:status "approved" :feedback "" :audited-content content})
        changes (fn [content] {:status "changes-requested" :feedback "fix" :audited-content content})]

    (testing "no audit record -> not in sample (nil)"
      (is (nil? (sut/audit-bucket nil (fields svc)))))

    (testing "scoped via empty audit -> waiting-audit"
      (is (= :waiting-audit (sut/audit-bucket {:timestamp "2026-07-11T00:00:00.000Z"
                                               :auditor-id "a"}
                                              (fields svc)))))

    (testing "partially audited -> waiting-audit"
      (is (= :waiting-audit (sut/audit-bucket {:timestamp "t" :auditor-id "a"
                                               :summary (approved (:summary svc))}
                                              (fields svc)))))

    (testing "changes requested, content unchanged -> waiting-fixes"
      (is (= :waiting-fixes (sut/audit-bucket {:timestamp "t" :auditor-id "a"
                                               :summary (changes (:summary svc))
                                               :description (approved (:description svc))
                                               :user-instruction (approved (:user-instruction svc))}
                                              (fields svc)))))

    (testing "all approved and unchanged -> done"
      (is (= :done (sut/audit-bucket {:timestamp "t" :auditor-id "a"
                                      :summary (approved (:summary svc))
                                      :description (approved (:description svc))
                                      :user-instruction (approved (:user-instruction svc))}
                                     (fields svc)))))

    (testing "approved but content edited afterwards -> stays done (DVV audited it once)"
      (is (= :done (sut/audit-bucket {:timestamp "t" :auditor-id "a"
                                      :summary (approved {:fi "vanha tiivistelmä"})
                                      :description (approved (:description svc))
                                      :user-instruction (approved (:user-instruction svc))}
                                     (fields svc)))))

    (testing "changes requested, then content fixed -> done (accepted without re-review)"
      (is (= :done (sut/audit-bucket {:timestamp "t" :auditor-id "a"
                                      :summary (changes {:fi "vanha tiivistelmä"})
                                      :description (approved (:description svc))
                                      :user-instruction (approved (:user-instruction svc))}
                                     (fields svc)))))

    (testing "one field fixed but another still awaiting fixes -> waiting-fixes"
      (is (= :waiting-fixes (sut/audit-bucket {:timestamp "t" :auditor-id "a"
                                               :summary (changes {:fi "vanha tiivistelmä"})
                                               :description (changes (:description svc))
                                               :user-instruction (approved (:user-instruction svc))}
                                              (fields svc)))))

    (testing "fix on one field and an edited approval on another -> done as well"
      (is (= :done (sut/audit-bucket {:timestamp "t" :auditor-id "a"
                                      :summary (changes {:fi "vanha tiivistelmä"})
                                      :description (approved {:fi "vanha kuvaus"})
                                      :user-instruction (approved (:user-instruction svc))}
                                     (fields svc)))))

    (testing "toimintaohje not required when the service has none"
      (let [svc' (dissoc svc :user-instruction)]
        (is (= :done (sut/audit-bucket {:timestamp "t" :auditor-id "a"
                                        :summary (approved (:summary svc'))
                                        :description (approved (:description svc'))}
                                       (fields svc'))))))

    (testing "toimintaohje without a Finnish text is not auditable, so not required"
      (let [svc' (assoc svc :user-instruction {:se "instruktion" :fi " "})]
        (is (= :done (sut/audit-bucket {:timestamp "t" :auditor-id "a"
                                        :summary (approved (:summary svc'))
                                        :description (approved (:description svc'))}
                                       (fields svc'))))))

    (testing "site fields spec works the same way"
      (let [site {:ptv {:summary {:fi "s"} :description {:fi "d"}}}]
        (is (= :done (sut/audit-bucket {:timestamp "t" :auditor-id "a"
                                        :summary (approved {:fi "s"})
                                        :description (approved {:fi "d"})}
                                       (sut/site-audit-fields site))))
        (is (= :done (sut/audit-bucket {:timestamp "t" :auditor-id "a"
                                        :summary (approved {:fi "muokattu"})
                                        :description (approved {:fi "d"})}
                                       (sut/site-audit-fields site))))
        (is (= :waiting-audit (sut/audit-bucket {:timestamp "t" :auditor-id "a"
                                                 :summary (approved {:fi "s"})}
                                                (sut/site-audit-fields site))))))))

(deftest determine-audit-status-test
  (let [site (fn [audit] {:ptv {:summary {:fi "s"}
                                :description {:fi "d"}
                                :audit audit}})
        approved (fn [content] {:status "approved" :feedback "" :audited-content content})
        changes (fn [content] {:status "changes-requested" :feedback "fix" :audited-content content})]
    (testing "no audit -> :none"
      (is (= :none (sut/determine-audit-status (site nil)))))
    (testing "unfinished audit -> :none (nothing the municipality can act on)"
      (is (= :none (sut/determine-audit-status
                     (site {:timestamp "t" :auditor-id "a"}))))
      (is (= :none (sut/determine-audit-status
                     (site {:timestamp "t" :auditor-id "a"
                            :summary (approved {:fi "s"})})))))
    (testing "approved, then edited -> still :approved (DVV audited the item once)"
      (is (= :approved (sut/determine-audit-status
                         (site {:timestamp "t" :auditor-id "a"
                                :summary (approved {:fi "vanha"})
                                :description (approved {:fi "d"})})))))
    (testing "an approved field edited meanwhile does not hide an open change request"
      (is (= :changes-requested (sut/determine-audit-status
                                  (site {:timestamp "t" :auditor-id "a"
                                         :summary (approved {:fi "vanha"})
                                         :description (changes {:fi "d"})})))))
    (testing "a change request in an unfinished audit is not shown yet"
      (is (= :none (sut/determine-audit-status
                     (site {:timestamp "t" :auditor-id "a"
                            :description (changes {:fi "d"})})))))
    (testing "fix on one field, approved field edited meanwhile -> :approved"
      (is (= :approved (sut/determine-audit-status
                         (site {:timestamp "t" :auditor-id "a"
                                :summary (approved {:fi "vanha"})
                                :description (changes {:fi "vanha"})})))))
    (testing "changes requested and unchanged -> :changes-requested"
      (is (= :changes-requested (sut/determine-audit-status
                                  (site {:timestamp "t" :auditor-id "a"
                                         :summary (changes {:fi "s"})
                                         :description (approved {:fi "d"})})))))
    (testing "requested changes fixed -> :approved (DVV accepts fixes as such)"
      (is (= :approved (sut/determine-audit-status
                         (site {:timestamp "t" :auditor-id "a"
                                :summary (changes {:fi "vanha"})
                                :description (approved {:fi "d"})})))))
    (testing "all approved -> :approved"
      (is (= :approved (sut/determine-audit-status
                         (site {:timestamp "t" :auditor-id "a"
                                :summary (approved {:fi "s"})
                                :description (approved {:fi "d"})})))))
    (testing "approved, but the auditor left a comment -> :approved-with-feedback"
      (is (= :approved-with-feedback
             (sut/determine-audit-status
               (site {:timestamp "t" :auditor-id "a"
                      :summary (assoc (approved {:fi "s"}) :feedback "hyvä, mutta")
                      :description (approved {:fi "d"})}))))
      ;; the remark stays readable after an edit, too
      (is (= :approved-with-feedback
             (sut/determine-audit-status
               (site {:timestamp "t" :auditor-id "a"
                      :summary (assoc (approved {:fi "vanha"}) :feedback "hyvä, mutta")
                      :description (approved {:fi "d"})})))))
    (testing "the comment of a fixed change request is not an approval remark"
      (is (= :approved (sut/determine-audit-status
                         (site {:timestamp "t" :auditor-id "a"
                                :summary (changes {:fi "vanha"})
                                :description (approved {:fi "d"})})))))))

(deftest audit-notification-summary-test
  (let [fields (sut/site-audit-fields {:ptv {:summary {:fi "s"} :description {:fi "d"}}})
        approved (fn [content] {:status "approved" :feedback "" :audited-content content})
        changes (fn [content] {:status "changes-requested" :feedback "fix" :audited-content content})
        entry (fn [id audit] {:ref {:id id} :audit audit :fields fields})
        audit (fn [summary description]
                (cond-> {:timestamp "t" :auditor-id "a"}
                  summary (assoc :summary summary)
                  description (assoc :description description)))]
    (is (= {:action-items [{:id :open :fields [:description]}
                           {:id :open+stale :fields [:description]}
                           {:id :both-open :fields [:summary :description]}]
            :approved-count 2}
           (sut/audit-notification-summary
             [(entry :approved (audit (approved {:fi "s"}) (approved {:fi "d"})))
              (entry :open (audit (approved {:fi "s"}) (changes {:fi "d"})))
              ;; an approved field edited meanwhile must not hide the request
              (entry :open+stale (audit (approved {:fi "vanha"}) (changes {:fi "d"})))
              (entry :both-open (audit (changes {:fi "s"}) (changes {:fi "d"})))
              ;; approved without change requests, edited since: still approved
              (entry :stale (audit (approved {:fi "vanha"}) (approved {:fi "d"})))
              ;; neither actionable nor approved:
              (entry :fixed (audit (approved {:fi "s"}) (changes {:fi "vanha"})))
              (entry :unfinished (audit (approved {:fi "s"}) nil))
              (entry :unfinished-with-request (audit (changes {:fi "s"}) nil))
              (entry :not-sampled nil)
              ;; verdicts without the record's :timestamp are not a sample entry
              (entry :no-timestamp {:description (changes {:fi "d"})})])))))

(deftest anchor-audit-snapshots-test
  (let [approved (fn [content] {:status "approved" :feedback "" :audited-content content})
        changes (fn [content] {:status "changes-requested" :feedback "fix" :audited-content content})
        contents {:summary {:fi "s2"} :description {:fi "d2"}}]

    (testing "first verdicts are anchored to the shown content; fields without a verdict are not"
      (is (= {:summary {:status "approved" :feedback "" :audited-content {:fi "s2"}}
              :description {:feedback "kesken"}}
             (sut/anchor-audit-snapshots {:summary {:status "approved" :feedback ""}
                                          :description {:feedback "kesken"}}
                                         nil
                                         contents))))

    (testing "an untouched fix keeps its snapshot when another field is saved"
      (let [persisted {:timestamp "t" :auditor-id "a"
                       :summary (approved {:fi "s"})      ; :stale, s -> s2
                       :description (changes {:fi "d"})}  ; :fixed, d -> d2
            saved (sut/anchor-audit-snapshots persisted persisted contents)
            fields [{:field :summary :content (:summary contents) :required? true}
                    {:field :description :content (:description contents) :required? true}]]
        (is (= {:fi "d"} (get-in saved [:description :audited-content])))
        ;; the untouched stale approval is re-confirmed by the save
        (is (= {:summary :approved :description :fixed}
               (sut/audit-field-states saved fields)))))

    (testing "touching the fixed field's verdict re-anchors it"
      (let [persisted {:summary (approved {:fi "s2"})
                       :description (changes {:fi "d"})}]
        ;; auditor accepts the fix explicitly
        (is (= {:fi "d2"}
               (get-in (sut/anchor-audit-snapshots
                         (assoc-in persisted [:description :status] "approved")
                         persisted contents)
                       [:description :audited-content])))
        ;; auditor rejects the fix by rewording the request
        (is (= {:fi "d2"}
               (get-in (sut/anchor-audit-snapshots
                         (assoc-in persisted [:description :feedback] "ei vieläkään")
                         persisted contents)
                       [:description :audited-content])))))

    (testing "an unanswered change request is simply re-anchored (content unchanged)"
      (let [persisted {:description (changes {:fi "d2"})}]
        (is (= persisted
               (sut/anchor-audit-snapshots persisted persisted
                                           {:description {:fi "d2"}})))))))

(deftest with-persisted-audit-content-test
  (let [changes (fn [content] {:status "changes-requested" :feedback "fix" :audited-content content})
        approved (fn [content] {:status "approved" :feedback "" :audited-content content})
        audit {:timestamp "t" :auditor-id "a"
               :summary (changes {:fi "s"})
               :description (approved {:fi "d"})}
        ;; The user has typed into the summary draft but nothing is saved yet.
        site {:ptv {:summary {:fi "s (muokattu)"}
                    :description {:fi "d"}
                    :audit audit}
              :ptv-persisted {:summary {:fi "s"}
                              :description {:fi "d"}}}]

    (testing "no snapshot -> site unchanged (falls back to live content)"
      (let [site' (dissoc site :ptv-persisted)]
        (is (= site' (sut/with-persisted-audit-content site')))))

    (testing "audited fields come from the snapshot, other :ptv keys survive"
      (is (= {:summary {:fi "s"}
              :description {:fi "d"}
              :audit audit}
             (:ptv (sut/with-persisted-audit-content site)))))

    (testing "unsaved keystrokes don't complete the municipality's move"
      (is (= :changes-requested
             (sut/determine-audit-status (sut/with-persisted-audit-content site))))
      ;; ...but a persisted fix (snapshot refreshed on sync success) does.
      (is (= :approved
             (sut/determine-audit-status
               (sut/with-persisted-audit-content
                 (assoc site :ptv-persisted {:summary {:fi "s (muokattu)"}
                                             :description {:fi "d"}}))))))

    (testing "unsaved keystrokes don't invalidate an approval either"
      (let [site' (assoc-in site [:ptv :audit :summary] (approved {:fi "s"}))]
        (is (= :approved
               (sut/determine-audit-status (sut/with-persisted-audit-content site'))))))

    (testing "a field absent from the snapshot is absent, not the draft value"
      (let [site' (assoc site :ptv-persisted {:description {:fi "d"}})]
        (is (nil? (get-in (sut/with-persisted-audit-content site')
                          [:ptv :summary])))))))

(deftest audit-feedback-missing-test
  (testing "a change request without feedback text is missing feedback"
    (is (sut/audit-feedback-missing? {:status "changes-requested" :feedback ""}))
    (is (sut/audit-feedback-missing? {:status "changes-requested" :feedback "   "}))
    (is (sut/audit-feedback-missing? {:status "changes-requested"})))
  (testing "feedback is optional for approvals and satisfied by any text"
    (is (not (sut/audit-feedback-missing? {:status "approved" :feedback ""})))
    (is (not (sut/audit-feedback-missing? {:status "changes-requested" :feedback "Tarkenna."})))))

(deftest audit-data-schema-requires-feedback-for-change-requests-test
  (is (m/validate ptv-schema/audit-data
                  {:summary {:status "approved" :feedback ""}
                   :description {:status "changes-requested" :feedback "Liian pitkä."}}))
  (is (not (m/validate ptv-schema/audit-data
                       {:description {:status "changes-requested" :feedback ""}}))
      "the submission schema rejects a wordless change request")
  (is (m/validate ptv-schema/ptv-audit
                  {:timestamp "2026-07-13T15:33:52.689078Z" :auditor-id "x"
                   :description {:status "changes-requested" :feedback ""}})
      "the stored-audit schema still accepts legacy wordless change requests"))

;;; Org resolution + sync gate (fix/ptv-sync-org-gate) ;;;

(def ^:private org-a {:id "a" :name "A" :ptv-data {:org-id "ptv-a" :city-codes [91] :owners ["city" "city-main-owner"]}})
(def ^:private org-b {:id "b" :name "B" :ptv-data {:org-id "ptv-b" :city-codes [179]}})
(def ^:private org-a-twin {:id "a2" :name "A twin" :ptv-data {:org-id "ptv-a" :city-codes [91]}})
(def ^:private unconfigured {:id "x" :name "X" :ptv-data {:city-codes [91]}})

(defn- site [city owner & {:as ptv}]
  (cond-> {:status "active" :owner owner :type {:type-code 2510}
           :location {:city {:city-code city}}}
    ptv (assoc :ptv ptv)))

(deftest resolve-org-id-test
  (testing "the site's city (and owner) decides the org, not the user's org count"
    (is (= "ptv-a" (sut/resolve-org-id (site 91 "city") [org-a org-b])))
    (is (= "ptv-b" (sut/resolve-org-id (site 179 "city") [org-a org-b])))
    ;; regression: the old "user has exactly one org → that org" rule picked
    ;; org-a for a Jyväskylä site
    (is (nil? (sut/resolve-org-id (site 179 "city") [org-a]))))
  (testing "a persisted org-id is authoritative"
    (is (= "ptv-b" (sut/resolve-org-id (site 91 "city" :org-id "ptv-b") [org-a]))))
  (testing "owner restriction"
    (is (nil? (sut/resolve-org-id (site 91 "private") [org-a]))))
  (testing "orgs without a PTV org-id never match"
    (is (nil? (sut/resolve-org-id (site 91 "city") [unconfigured]))))
  (testing "two LIPAS orgs sharing one PTV org are unambiguous; two PTV orgs aren't"
    (is (= "ptv-a" (sut/resolve-org-id (site 91 "city") [org-a org-a-twin])))
    (is (nil? (sut/resolve-org-id (site 91 "city")
                                  [org-a (assoc-in org-b [:ptv-data :city-codes] [91])])))))

(deftest sync-blockers-test
  (let [texts {:summary {:fi "Lyhyt kuvaus"} :description {:fi "Pidempi kuvaus"}}]
    (testing "nothing to block when sync is off or archiving"
      (is (= #{} (sut/sync-blockers (site 91 "city"))))
      (is (= #{} (sut/sync-blockers (site 91 "city" :sync-enabled false))))
      (is (= #{} (sut/sync-blockers (site 91 "city" :sync-enabled true :delete-existing true)))))
    (testing "sync on: org and (for candidates) texts are required"
      (is (= #{:ptv/no-org :ptv/missing-texts}
             (sut/sync-blockers (site 91 "city" :sync-enabled true))))
      (is (= #{:ptv/missing-texts}
             (sut/sync-blockers (site 91 "city" :sync-enabled true :org-id "ptv-a"))))
      (is (= #{} (sut/sync-blockers (apply site 91 "city" :sync-enabled true :org-id "ptv-a"
                                           (mapcat identity texts))))))
    (testing "a non-candidate needs an org but not texts"
      (is (= #{:ptv/no-org} (sut/sync-blockers (site 91 "private" :sync-enabled true)))))))

(deftest ptv-edited?-test
  (let [prev (site 91 "city" :sync-enabled true :org-id "ptv-a" :service-ids ["s"]
                   :last-sync "2026-01-01" :publishing-status "Published")]
    (is (not (sut/ptv-edited? prev prev)))
    (testing "sync-written keys don't count"
      (is (not (sut/ptv-edited? prev (assoc-in prev [:ptv :last-sync] "2026-02-02")))))
    (testing "materialized empty values mean not set"
      (is (not (sut/ptv-edited? (site 91 "city") (site 91 "city" :service-ids [] :summary {:fi ""}
                                                       :sync-enabled false)))))
    (testing "user-controlled keys count"
      (is (sut/ptv-edited? prev (assoc-in prev [:ptv :sync-enabled] false)))
      (is (sut/ptv-edited? prev (assoc-in prev [:ptv :org-id] "ptv-b")))
      (is (sut/ptv-edited? nil (site 91 "city" :sync-enabled true))))))

;;; ptv-user-rows (admin PTV user list) ;;;

(def ^:private users-org-a {:id "a" :name "A" :ptv-data {:org-id "ptv-a" :city-codes [91]}})
(def ^:private users-org-b {:id "b" :name "B" :ptv-data {:org-id "ptv-b" :city-codes [92]}})
(def ^:private users-org-b2 {:id "b2" :name "B2" :ptv-data {:org-id "ptv-b2" :city-codes [92]}})

(defn- account [id & {:keys [roles org-roles orgs status] :or {status "active"}}]
  {:id id :username id :status status
   :permissions {:roles (vec roles)}
   :org-roles (vec org-roles)
   :orgs (vec orgs)})

(defn- rows-by-user [users orgs]
  (into {} (map (juxt :user-id identity)) (sut/ptv-user-rows users orgs)))

(deftest ptv-user-rows-test
  (let [rows (rows-by-user
               [(account "legacy" :roles [{:role "ptv-manager" :city-code [91]}])
                (account "member" :org-roles [{:role "ptv-manager" :city-code [91]}]
                         :orgs [{:id "a" :name "A"}])
                (account "both" :roles [{:role "ptv-manager" :city-code [91]}]
                         :org-roles [{:role "ptv-manager" :city-code [91]}]
                         :orgs [{:id "a" :name "A"}])
                (account "in-b2" :org-roles [{:role "ptv-manager" :city-code [92]}]
                         :orgs [{:id "b2" :name "B2"}])
                (account "unsure" :roles [{:role "ptv-manager" :city-code [92]}])
                (account "no-org" :roles [{:role "ptv-manager" :city-code [93]}])
                (account "editor" :roles [{:role "city-manager" :city-code [91]}])
                (account "gone" :status "archived" :roles [{:role "ptv-manager" :city-code [91]}])]
               [users-org-a users-org-b users-org-b2])
        pick #(select-keys % [:direct? :via-org? :member? :org :ambiguous-org?])]
    (testing "legacy direct role: covering org suggested, not a member"
      (is (= {:direct? true :via-org? false :member? false
              :org {:id "a" :name "A"} :ambiguous-org? false}
             (pick (rows "legacy")))))
    (testing "rights from the org template: already a member"
      (is (= {:direct? false :via-org? true :member? true
              :org {:id "a" :name "A"} :ambiguous-org? false}
             (pick (rows "member")))))
    (testing "both routes collapse into one row"
      (is (= [true true] ((juxt :direct? :via-org?) (rows "both")))))
    (testing "two orgs cover the city: the one the account is in wins"
      (is (= "b2" (get-in (rows "in-b2") [:org :id])))
      (is (:member? (rows "in-b2"))))
    (testing "two orgs cover the city and the account is in neither: no suggestion"
      (is (nil? (:org (rows "unsure"))))
      (is (:ambiguous-org? (rows "unsure"))))
    (testing "no PTV org covers the city"
      (is (nil? (:org (rows "no-org"))))
      (is (false? (:ambiguous-org? (rows "no-org")))))
    (testing "accounts without ptv-manager, and inactive accounts, are left out"
      (is (not (contains? rows "editor")))
      (is (not (contains? rows "gone"))))))

;;; attention (admin "Vaatii huomiota") ;;;

(def ^:private healthy-ptv
  {:sync-enabled true :org-id "o" :publishing-status "Published"
   :summary {:fi "Lyhyt kuvaus"} :description {:fi "Pidempi kuvaus"}
   :service-ids ["s"] :service-channel-ids ["c"] :source-id "src"})

(defn- attention-site [ptv & {:keys [status owner] :or {status "active" owner "city"}}]
  {:status status :owner owner :type {:type-code 1340} :ptv ptv})

(defn- reasons [site] (mapv :reason (sut/attention site)))

(deftest attention-test
  (testing "a healthy integrated site needs nothing"
    (is (= [] (sut/attention (attention-site healthy-ptv)))))
  (testing "errors: sync on but not (correctly) in PTV"
    (is (= [:ptv/no-org :ptv/missing-texts]
           (reasons (attention-site {:sync-enabled true}))))
    (is (= [:ptv/sync-error]
           (reasons (attention-site (assoc healthy-ptv :error {:message "HTTP Error: 500"})))))
    (is (= [:ptv/never-sent]
           (reasons (attention-site (assoc healthy-ptv :publishing-status nil :service-channel-ids []))))))
  (testing "warnings: in PTV but in a state to look at"
    (is (= [:ptv/published-not-candidate]
           (reasons (attention-site healthy-ptv :status "incorrect-data"))))
    (is (= [:ptv/no-service]
           (reasons (attention-site (assoc healthy-ptv :service-ids [])))))
    (is (= [:ptv/published-sync-off]
           (reasons (attention-site (assoc healthy-ptv :sync-enabled false)))))
    (is (= [:ptv/archived-sync-on]
           (reasons (attention-site (assoc healthy-ptv :publishing-status "Deleted"))))))
  (testing "nothing to do"
    (is (= [] (sut/attention (attention-site (assoc healthy-ptv :publishing-status "Deleted")
                                             :status "out-of-service-permanently")))
        "archived because the site closed down, even with sync still on")
    (is (= [] (sut/attention (attention-site {:sync-enabled false})))
        "PTV data saved but never switched on")
    (is (every? #(= :error (:severity %)) (sut/attention (attention-site {:sync-enabled true}))))))

;;; Patching services made in PTV ;;;

;; "se" is Northern Sámi in PTV and "ru" Russian: languages LIPAS doesn't model.
(def ^:private gen-ptv-lang (gen/elements ["fi" "sv" "en" "se" "ru"]))

(def ^:private gen-text (gen/one-of [(gen/return "")
                                     (gen/not-empty gen/string-alphanumeric)]))

(defn- gen-entries [types]
  (gen/vector-distinct-by (juxt :type :language)
                          (gen/hash-map :type (gen/elements types)
                                        :language gen-ptv-lang
                                        :value gen-text)
                          {:max-elements 12}))

(def ^:private gen-requirements
  (gen/vector-distinct-by :language
                          (gen/hash-map :language gen-ptv-lang :value gen-text)
                          {:max-elements 5}))

(def ^:private gen-service
  (gen/hash-map :serviceNames (gen-entries ["Name" "AlternativeName"])
                :serviceDescriptions (gen-entries ["Summary" "Description" "UserInstruction"
                                                   "ChargeTypeAdditionalInfo" "DeadLine"])
                :requirements gen-requirements
                :languages (gen/vector-distinct gen-ptv-lang)
                :ontologyTerms (gen/return ["http://www.yso.fi/onto/koko/p1"])))

(def ^:private gen-locale-texts
  (gen/map (gen/elements [:fi :se :en]) gen-text {:max-elements 3}))

(def ^:private gen-texts
  (gen/hash-map :name gen-locale-texts
                :summary gen-locale-texts
                :description gen-locale-texts
                :user-instruction gen-locale-texts))

(defn- all-entries [service]
  (concat (:serviceNames service) (:serviceDescriptions service)))

(defn- language-entries
  "Every entry that makes a language version: names, descriptions, requirements."
  [service]
  (concat (all-entries service) (:requirements service)))

(defspec native-patch-never-drops-a-language-version 300
  (prop/for-all [service gen-service
                 texts gen-texts]
                (let [out (sut/patch-service-texts service texts #{})]
                  (every? (sut/service-language-versions out)
                          (sut/service-language-versions service)))))

(defspec native-patch-keeps-what-lipas-does-not-own 300
  (prop/for-all [service gen-service
                 texts gen-texts]
                (let [out (sut/patch-service-texts service texts #{})
                      out-entries (set (all-entries out))
                      written? (fn [{:keys [language]}]
                                 (let [locale (sut/lang->locale language)]
                                   (some #(seq (get % locale)) (vals texts))))
                      lipas-owned? (fn [{:keys [type] :as e}]
                                     (and (written? e)
                                          (or (= "Name" type)
                                              (contains? sut/patched-description-types type))))]
                  (and
        ;; other fields untouched, :languages (provision languages) included
                    (= (dissoc service :serviceNames :serviceDescriptions)
                       (dissoc out :serviceNames :serviceDescriptions))
        ;; every entry LIPAS doesn't own survives as it was
                    (every? out-entries (remove lipas-owned? (all-entries service)))))))

(defspec native-patch-writes-lipas-texts 300
  (prop/for-all [service gen-service
                 texts gen-texts]
                (let [out (sut/patch-service-texts service texts #{})
                      entry (fn [type lang]
                              (some #(when (and (= type (:type %)) (= lang (:language %))) (:value %))
                                    (all-entries out)))]
                  (every? (fn [[k type]]
                            (every? (fn [[locale v]]
                                      (or (empty? v)
                                          (= v (entry type (sut/locale->lang locale)))))
                                    (get texts k)))
                          {:name "Name" :summary "Summary" :description "Description"
                           :user-instruction "UserInstruction"}))))

(defspec native-patch-removes-a-language-only-explicitly-and-fully 300
  (prop/for-all [service gen-service
                 texts gen-texts
                 remove-lang (gen/elements ["sv" "en"])]
                (let [out (sut/patch-service-texts service texts #{remove-lang})]
                  (and (not-any? #(= remove-lang (:language %)) (language-entries out))
                       (every? (sut/service-language-versions out)
                               (disj (sut/service-language-versions service) remove-lang))))))

(deftest patch-service-texts-test
  (let [service {:languages ["fi"]
                 :serviceNames [{:type "Name" :language "fi" :value "Uimahalli"}
                                {:type "Name" :language "sv" :value "Simhall"}
                                {:type "AlternativeName" :language "fi" :value "Halli"}]
                 :serviceDescriptions [{:type "Summary" :language "fi" :value "PTV fi"}
                                       {:type "Description" :language "fi" :value "PTV kuvaus"}
                                       {:type "UserInstruction" :language "fi" :value "Ohje"}
                                       {:type "Summary" :language "sv" :value "PTV sv"}
                                       {:type "Description" :language "sv" :value "PTV beskrivning"}
                                       {:type "ChargeTypeAdditionalInfo" :language "sv" :value "Avgift"}]}]
    (testing "a language LIPAS has no texts for is left as PTV has it"
      (let [out (sut/patch-service-texts service
                                         {:name {:fi "Uimahalli"}
                                          :summary {:fi "Uusi fi"}
                                          :description {:fi "Uusi kuvaus"}}
                                         #{})]
        (is (= #{"fi" "sv"} (sut/service-language-versions out)))
        (is (some #{{:type "Summary" :language "sv" :value "PTV sv"}} (:serviceDescriptions out)))
        (is (some #{{:type "Summary" :language "fi" :value "Uusi fi"}} (:serviceDescriptions out)))
        (is (some #{{:type "AlternativeName" :language "fi" :value "Halli"}} (:serviceNames out)))
        (is (= ["fi"] (:languages out)) "provision languages are not touched")))

    (testing "a blank user instruction removes it"
      (let [out (sut/patch-service-texts service
                                         {:name {:fi "Uimahalli"}
                                          :summary {:fi "s"} :description {:fi "d"}
                                          :user-instruction {:fi ""}}
                                         #{})]
        (is (not-any? #(and (= "UserInstruction" (:type %)) (= "fi" (:language %)))
                      (:serviceDescriptions out)))))

    (testing "removing a language drops its PTV-only entries too"
      (let [out (sut/patch-service-texts service {} #{"sv"})]
        (is (= #{"fi"} (sut/service-language-versions out)))
        (is (empty? (sut/language-version-problems out)))))

    (testing "a version left without a required text is reported"
      ;; sv keeps only its name
      (is (= {"sv" ["Summary" "Description"]}
             (sut/language-version-problems
               (sut/patch-service-texts (update service :serviceDescriptions
                                                (partial remove #(= "sv" (:language %))))
                                        {}
                                        #{})))))))

(deftest cleared-ptv-texts-test
  ;; A cleared text field stores nil. It used to fail the string schema
  ;; with the "too long" message (tester finding), and would have made the
  ;; backend reject the save.
  (let [errors (fn [summary]
                 (->> (m/explain ptv-schema/ptv-meta
                                 {:org-id "org" :sync-enabled true
                                  :service-channel-ids [] :service-ids []
                                  :summary summary :description {:fi "kuvaus"}})
                      :errors
                      (map me/error-message)
                      (filter keyword?)))]
    (testing "drop-blank-texts removes missing and blank texts only"
      (is (= {:fi "teksti"}
             (sut/drop-blank-texts {:fi "teksti" :se nil :en "  "}))))

    (testing "a cleared text is never reported as too long"
      (is (empty? (errors {:fi nil})))
      (is (empty? (errors (sut/drop-blank-texts {:fi "tiivistelmä" :se nil})))))

    (testing "a text over the limit still is"
      (is (= [:ptv/error-summary-too-long]
             (errors {:fi (apply str (repeat (inc sut/max-summary-length) "x"))}))))))

(deftest service-text-errors-test
  (let [texts {:summary {:fi "Tiivistelmä"} :description {:fi "Kuvaus"}}]
    (testing "a service created in LIPAS needs only its Finnish summary and description"
      (is (empty? (sut/service-text-errors {:management :lipas :texts texts})))
      (is (empty? (sut/service-text-errors
                    {:management :lipas
                     :texts (assoc-in texts [:summary :se] "Sammanfattning")}))
          "a language without a description is not an error, just not published")
      (is (= [{:field :summary :locale :fi :error :missing}]
             (sut/service-text-errors {:management :lipas
                                       :texts (assoc-in texts [:summary :fi] "")}))))

    (testing "a service made in PTV reports each incomplete language version"
      (is (= [{:field :name :locale :se :error :missing}
              {:field :summary :locale :se :error :missing}]
             (sut/service-text-errors {:management :native
                                       :texts texts
                                       :version-problems {"sv" ["Name" "Summary"]}}))))

    (testing "a text over PTV's limit, in any language"
      (is (= [{:field :description :locale :se :error :too-long
               :max sut/max-description-length}]
             (sut/service-text-errors
               {:management :lipas
                :texts (assoc-in texts [:description :se]
                                 (apply str (repeat (inc sut/max-description-length) "x")))}))))

    (testing "a service another system maintains is not checked"
      (is (empty? (sut/service-text-errors {:management :other-system :texts {}}))))))

(deftest service-versions-to-drop-test
  (let [texts {:summary {:fi "s" :se "s"} :description {:fi "d" :se "d"}}]
    (is (empty? (sut/service-versions-to-drop [:fi :se] texts ["fi" "se"])))
    (is (= [:se] (sut/service-versions-to-drop [:fi :se] (update texts :description dissoc :se) ["fi" "se"]))
        "a language that lost its description")
    (is (= [:se] (sut/service-versions-to-drop [:fi :se] texts ["fi"]))
        "a language the org hasn't enabled")))

;;; PTV's minimum text length ;;;

(deftest text-too-short?-test
  (testing "under five characters after trimming is too short"
    (is (sut/text-too-short? "Test"))
    (is (sut/text-too-short? " abcd "))
    (is (sut/text-too-short? "x")))
  (testing "five characters is enough, inner spaces count"
    (is (not (sut/text-too-short? "Test.")))
    (is (not (sut/text-too-short? "a b c"))))
  (testing "a missing or blank text is no text, not a short one"
    (is (not (sut/text-too-short? nil)))
    (is (not (sut/text-too-short? "")))
    (is (not (sut/text-too-short? "   ")))))

(deftest short-service-texts-test
  (is (= [{:type "Summary" :language "en"}
          {:type "ChargeTypeAdditionalInfo" :language "fi"}
          {:type "Requirement" :language "fi"}]
         (sut/short-service-texts
           {:serviceNames [{:type "Name" :language "fi" :value "X"}]
            :serviceDescriptions [{:type "Summary" :language "fi" :value "Testi"}
                                  {:type "Summary" :language "en" :value "Test"}
                                  {:type "ChargeTypeAdditionalInfo" :language "fi" :value "5 €"}
                                  {:type "ChargeTypeAdditionalInfo" :language "en" :value nil}]
            :requirements [{:language "fi" :value "Ei"}
                           {:language "en" :value "None at all"}]}))
      "names have no minimum; a nil text is not a short one"))

(deftest service-text-errors-too-short-test
  (let [texts {:summary {:fi "Tiivistelmä" :en "Test"} :description {:fi "Kuvaus"}}
        body {:serviceDescriptions [{:type "Summary" :language "fi" :value "Tiivistelmä"}
                                    {:type "Summary" :language "en" :value "Test"}
                                    {:type "Summary" :language "se" :value "Čoah"}
                                    {:type "DeadLine" :language "fi" :value "Heti"}]
              :requirements [{:language "fi" :value "Ei"}]}]
    (is (= [{:field :summary :locale :en :error :too-short :min sut/min-text-length}
            {:ptv-type "Summary" :language "se" :error :too-short-in-ptv :min sut/min-text-length}
            {:ptv-type "DeadLine" :language "fi" :error :too-short-in-ptv :min sut/min-text-length}
            {:ptv-type "Requirement" :language "fi" :error :too-short-in-ptv :min sut/min-text-length}]
           (sut/service-text-errors {:management :native :texts texts :body body}))
        "a text LIPAS writes is the user's to fix; the rest (incl. Sámi \"se\") PTV's")
    (is (empty? (sut/service-text-errors
                  {:management :lipas :texts texts
                   :body {:serviceDescriptions [{:type "Summary" :language "fi" :value "Tiivistelmä"}]}}))
        "a short text the sync won't publish (en lacks a description) doesn't block it")))

(deftest service-location-short-texts-test
  (let [texts {:summary {:fi "Uimahalli keskustassa" :se "Sim"}
               :description {:fi "Uimahalli keskustassa." :se "Simhall"}}]
    (is (= [{:field :summary :locale :se}]
           (sut/service-location-short-texts {:ptv texts} ["fi" "se"])))
    (is (empty? (sut/service-location-short-texts {:ptv texts} ["fi"]))
        "a language the org hasn't enabled isn't published, nor shown to fix")
    (is (empty? (sut/service-location-short-texts {:ptv (update texts :description dissoc :se)}
                                                  ["fi" "se"]))
        "nor is one without both texts")
    (is (= #{} (sut/sync-blockers (apply site 91 "city" :sync-enabled true :org-id "ptv-a"
                                         (mapcat identity texts))))
        "not a save blocker: which languages are published is the org's live config")))

(deftest text-too-short?-unicode-whitespace-test
  ;; Trimmed as PTV (.NET) trims, the same on the JVM and in the browser
  (is (sut/text-too-short? "\u00a0abcd"))
  (is (sut/text-too-short? "abcd\u2003"))
  (is (not (sut/text-too-short? "\u00a0")) "no-break space alone is no text")
  (is (not (sut/text-too-short? "ab\u00a0cd")) "inner spaces count"))

(deftest lipas-service-text-errors-test
  (let [m {:source-id "lipas-org-x-1300" :sub-category-id 1300 :languages ["fi" "en"]
           :summary {:fi "Pallokentät kunnassa" :en "Pool"}
           :description {:fi "Kunnan pallokentät." :en "The fields of the town."}}]
    (is (= [{:field :summary :locale :en :error :too-short :min sut/min-text-length}]
           (sut/lipas-service-text-errors m nil)))
    (is (empty? (sut/lipas-service-text-errors (assoc m :languages ["fi"]) nil))
        "English the org doesn't publish isn't sent")
    (is (= [{:ptv-type "Requirement" :language "fi" :error :too-short-in-ptv
             :min sut/min-text-length}]
           (sut/lipas-service-text-errors (assoc m :languages ["fi"])
                                          {:requirements [{:language "fi" :value "Ei"}]}))
        "what the sync carries over from PTV is checked too")))

;;; Carrying PTV's own entries over into a LIPAS-created service's sync ;;;

(def ^:private gen-lipas-body
  ;; What ->ptv-service builds: a Name and LIPAS's description types per language
  (gen/hash-map :serviceNames (gen-entries ["Name"])
                :serviceDescriptions (gen-entries ["Summary" "Description" "UserInstruction"])
                :languages (gen/vector-distinct gen-ptv-lang)))

(def ^:private gen-live-service
  (gen/let [service gen-service
            requirements (gen/vector-distinct-by :language
                                                 (gen/hash-map :language gen-ptv-lang
                                                               :value gen-text)
                                                 {:max-elements 5})]
    (assoc service :requirements requirements)))

(defn- lipas-written? [{:keys [type]}]
  (or (= "Name" type) (contains? sut/patched-description-types type)))

(defspec carry-over-never-adds-a-language-version 300
  (prop/for-all [body gen-lipas-body
                 live gen-live-service]
                (let [out (sut/carry-over-service-entries body live)
                      langs (sut/service-language-versions body)]
                  (and (= langs (sut/service-language-versions out))
                       (every? (comp langs :language) (:requirements out))))))

(defspec carry-over-keeps-lipas-entries-as-lipas-wrote-them 300
  (prop/for-all [body gen-lipas-body
                 live gen-live-service]
                (let [out (sut/carry-over-service-entries body live)]
                  (and (= (all-entries body) (filter lipas-written? (all-entries out)))
                       (= (dissoc body :serviceNames :serviceDescriptions :requirements)
                          (dissoc out :serviceNames :serviceDescriptions :requirements))))))

(defspec carry-over-keeps-what-only-ptv-has 300
  (prop/for-all [body gen-lipas-body
                 live gen-live-service]
                (let [out (sut/carry-over-service-entries body live)
                      langs (sut/service-language-versions body)
                      kept? (fn [e] (and (langs (:language e))
                                         (not (str/blank? (:value e)))))]
                  (and (= (set (filter kept? (remove lipas-written? (all-entries live))))
                          (set (remove lipas-written? (all-entries out))))
                       (= (set (filter kept? (:requirements live)))
                          (set (:requirements out)))))))

(deftest carry-over-service-entries-test
  (let [body {:serviceNames [{:type "Name" :language "fi" :value "Uimahallit"}]
              :serviceDescriptions [{:type "Summary" :language "fi" :value "Uusi tiivistelmä"}]
              :languages ["fi"]}
        live {:serviceNames [{:type "Name" :language "fi" :value "Vanha nimi"}
                             {:type "AlternativeName" :language "fi" :value "Hallit"}
                             {:type "Name" :language "sv" :value "Simhallar"}]
              :serviceDescriptions [{:type "Summary" :language "fi" :value "Vanha tiivistelmä"}
                                    {:type "ChargeTypeAdditionalInfo" :language "fi" :value "Maksu 5 euroa"}
                                    {:type "ChargeTypeAdditionalInfo" :language "sv" :value "Avgift 5 euro"}
                                    {:type "DeadLine" :language "fi" :value nil}]
              :requirements [{:language "fi" :value "Ikäraja 15 vuotta"}
                             {:language "sv" :value "Åldersgräns 15 år"}]
              :keywords [{:language "fi" :value "uinti"}]}]
    (is (= {:serviceNames [{:type "Name" :language "fi" :value "Uimahallit"}
                           {:type "AlternativeName" :language "fi" :value "Hallit"}]
            :serviceDescriptions [{:type "Summary" :language "fi" :value "Uusi tiivistelmä"}
                                  {:type "ChargeTypeAdditionalInfo" :language "fi" :value "Maksu 5 euroa"}]
            :requirements [{:language "fi" :value "Ikäraja 15 vuotta"}]
            :languages ["fi"]}
           (sut/carry-over-service-entries body live))
        "PTV's Finnish extras are kept; Swedish, which LIPAS no longer publishes, isn't; nor are blank entries or fields PTV keeps by itself")
    (is (= body (sut/carry-over-service-entries body {}))
        "nothing to carry over leaves the body as it is (no empty :requirements)")))

(deftest requirements-make-a-language-version-test
  ;; Verified in the PTV test env 2026-10-17: a requirement left alone in a
  ;; language made PTV demand that language's summary and description
  (let [service {:serviceNames [{:type "Name" :language "fi" :value "Uimahalli"}]
                 :serviceDescriptions [{:type "Summary" :language "fi" :value "Tiivistelmä"}
                                       {:type "Description" :language "fi" :value "Kuvaus"}]
                 :requirements [{:language "fi" :value "Ikäraja 15 vuotta"}
                                {:language "en" :value "Age limit 15"}]}]
    (is (= #{"fi" "en"} (sut/service-language-versions service)))
    (is (= {"en" ["Name" "Summary" "Description"]} (sut/language-version-problems service))
        "reported before the sync, like any other incomplete version")
    (let [out (sut/patch-service-texts service {} #{"en"})]
      (is (= [{:language "fi" :value "Ikäraja 15 vuotta"}] (:requirements out)))
      (is (= #{"fi"} (sut/service-language-versions out))))))

(deftest lipas-service-update-body-test
  ;; LIPAS sets producers, area and funding only when it creates a service,
  ;; and the target groups only as their first value: an update leaves them
  ;; to PTV (verified in the PTV test env 2026-10-17)
  (let [body (sut/->ptv-service {:org-id "org-x" :source-id "lipas-org-x-1300" :sub-category-id 1300
                                 :city-codes [889] :languages ["fi"]
                                 :summary {:fi "Pallokentät kunnassa"} :description {:fi "Kunnan pallokentät."}})
        live {:targetGroups [{:uri "http://urn.fi/URN:NBN:fi:au:ptvl:v2001" :code "KR1"}
                             {:uri "http://urn.fi/URN:NBN:fi:au:ptvl:v2009" :code "KR2.1"}]
              :requirements [{:language "fi" :value "Ikäraja 15 vuotta"}]}]
    (testing "the create body has LIPAS's defaults"
      (is (every? #(contains? body %) sut/create-only-service-fields))
      (is (= ["http://uri.suomi.fi/codelist/ptv/ptvkohderyhmat/code/KR1"] (:targetGroups body))))
    (testing "an update leaves them out and resends PTV's own target groups"
      (let [out (sut/lipas-service-update-body body live)]
        (is (not-any? #(contains? out %) sut/create-only-service-fields))
        (is (= ["http://urn.fi/URN:NBN:fi:au:ptvl:v2001" "http://urn.fi/URN:NBN:fi:au:ptvl:v2009"]
               (:targetGroups out))
            "as the URIs a PUT takes, never mixed with LIPAS's newer form")
        (is (= (:requirements live) (:requirements out)) "and carries over what LIPAS never writes")
        (is (= (select-keys body [:serviceNames :serviceClasses :ontologyTerms :languages :type])
               (select-keys out [:serviceNames :serviceClasses :ontologyTerms :languages :type]))
            "what LIPAS maintains is written as before")))
    (testing "without the live service: create-only fields out, LIPAS's target group in
              (PTV requires target groups in a PUT without a general description)"
      (let [out (sut/lipas-service-update-body body nil)]
        (is (not-any? #(contains? out %) sut/create-only-service-fields))
        (is (= (:targetGroups body) (:targetGroups out)))))
    (testing "a live service without target groups keeps LIPAS's"
      (is (= (:targetGroups body)
             (:targetGroups (sut/lipas-service-update-body body {:targetGroups []})))))))
