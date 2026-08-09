(ns lipas.backend.address.core-test
  "Tests for the reverse-geocode merge and for the postal data refresh jobs.

  The merge tests are pure: `build-result` takes its lookups as injected fns,
  so the summary rules can be walked through case by case with the awkward
  real-world situations they exist for — a BAF-exact address a kilometre
  away, an OSM address Posti has never heard of, a click on open water, a
  Pelias outage.

  The job tests inject the fetching fns, so the whole discover -> download ->
  parse -> import path runs against the test database without touching
  posti.fi or geo.stat.fi."
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing use-fixtures]]
    [lipas.backend.address.core :as address]
    [lipas.backend.address.db :as address-db]
    [lipas.jobs.dispatcher :as dispatcher]
    [lipas.jobs.registry :as registry]
    [lipas.test-utils :as test-utils]))

(defonce test-system (atom nil))

(let [{:keys [once each]} (test-utils/db-only-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn- test-db [] (:lipas/db @test-system))

;;; Reverse geocoding — the summary rules ;;;

(def ^:private postal-codes
  "Stand-in for the `postal_code` table. Real rows, trimmed to the columns the
  merge reads."
  {"02820" {:name-fi "ESPOO" :name-sv "ESBO" :municipality-code "049"
            :region-name-fi "Helsinki-Uusimaa" :region-name-sv "Helsingfors-Nyland"}
   "03220" {:name-fi "TERVALAMPI" :name-sv "TERVALAMPI" :municipality-code "927"
            :region-name-fi "Helsinki-Uusimaa" :region-name-sv "Helsingfors-Nyland"}
   "00100" {:name-fi "HELSINKI" :name-sv "HELSINGFORS" :municipality-code "091"
            :region-name-fi "Helsinki-Uusimaa" :region-name-sv "Helsingfors-Nyland"}})

(defn- segment [postal-code name-fi name-sv side from to]
  {:postal-code postal-code
   :name-fi name-fi
   :name-sv name-sv
   :side side
   :min-bound {:number from :letter nil :number2 nil :letter2 nil}
   :max-bound {:number to :letter nil :number2 nil :letter2 nil}})

(def ^:private mannerheimintie
  [(segment "00100" "Mannerheimintie" "Mannerheimvägen" :odd 1 13)
   (segment "00100" "Mannerheimintie" "Mannerheimvägen" :even 2 40)])

(def ^:private street-segments
  "Stand-in for `postal_street_segment`, keyed the way `get-street-segments`
  is queried. Mannerheimintie appears under both its Finnish and its Swedish
  key because the real query matches either one.

  Note what is NOT here: Valklammentie in Vihti (927). OSM has such an
  address, Posti does not — that is the phantom case."
  {["haukkaranta" "049"] [(segment "02820" "Haukkaranta" nil :odd 1 19)
                          (segment "02820" "Haukkaranta" nil :even 2 20)]
   ["mannerheimintie" "091"] mannerheimintie
   ["mannerheimvagen" "091"] mannerheimintie})

(defn- feature
  "A Pelias address feature's properties. `distance` is in kilometres, as
  Pelias reports it."
  [street housenumber localadmin postalcode distance]
  {:street street
   :housenumber housenumber
   :name (str street " " housenumber)
   :label (str street " " housenumber ", " localadmin)
   :localadmin localadmin
   :postalcode postalcode
   :distance distance})

(defn- paavo-area [postal-code name-fi name-sv municipality-code]
  {:postal-code postal-code
   :name-fi name-fi
   :name-sv name-sv
   :municipality-code municipality-code
   :year 2026})

(defn- reverse-geocode
  "`build-result` with the database halves stubbed out by lookups into the
  maps above."
  [{:keys [lat lon features paavo pelias-status]
    :or {lat 60.3095 lon 24.485 pelias-status :ok}}]
  (address/build-result
    {:lat lat
     :lon lon
     :pelias {:status pelias-status :features features}
     :paavo paavo
     :segments-fn (fn [street-key municipality-code]
                    (get street-segments [street-key municipality-code] []))
     :postal-code-fn postal-codes}))

(def ^:private nuuksio-features
  "Verbatim from Digitransit for the design's Nuuksio point: the nearest
  address is 1.17 km away in Espoo, the next one 1.19 km away in another
  municipality entirely."
  [(feature "Haukkaranta" "16" "Espoo" "02820" 1.173)
   (feature "Valklammentie" "105" "Vihti" "03220" 1.19)])

(deftest urban-exact-match-test
  (let [{:keys [summary addresses]}
        (reverse-geocode {:lat 60.1699 :lon 24.9384
                          :features [(feature "Mannerheimintie" "5" "Helsinki" "00100" 0.028)]
                          :paavo (paavo-area "00100" "Helsinki keskusta" "Helsingfors centrum" "091")})]

    (testing "a BAF-exact address a few metres away answers for the point"
      (is (= "00100" (:postal-code summary)))
      (is (= :posti (:postal-code-source summary)))
      (is (nil? (:alternative-postal-code summary)) "Posti and Paavo agree"))

    (testing "and brings its postitoimipaikka, municipality and region along"
      (is (= {:fi "HELSINKI" :sv "HELSINGFORS"} (:postal-office summary)))
      (is (= {:code "091" :name {:fi "Helsinki" :sv "Helsingfors"}} (:municipality summary)))
      (is (= {:fi "Helsinki-Uusimaa" :sv "Helsingfors-Nyland"} (:region summary))))

    (testing "the nearest address is reported with its distance in metres"
      (is (= "Mannerheimintie 5" (:address summary)))
      (is (= 28 (:address-distance-m summary))))

    (testing "the address row carries Posti's verdict on it"
      (is (= {:postal-code "00100"
              :postal-office {:fi "HELSINKI" :sv "HELSINGFORS"}
              :exact true}
             (:posti (first addresses)))))

    (testing "the street's Swedish name comes from BAF, which knows it"
      (is (= {:fi "Mannerheimintie" :sv "Mannerheimvägen"} (:street (first addresses)))))

    (testing "both sources answered"
      (is (= {:pelias :ok :paavo :ok} (:sources summary))))))

(deftest distant-exact-match-loses-to-paavo-test
  (let [{:keys [summary area addresses]}
        (reverse-geocode {:features nuuksio-features
                          :paavo (paavo-area "02820" "Nupuri-Nuuksio" "Nupurböle-Noux" "049")})]

    (testing "an exact address 1.17 km away is a neighbour, not the location"
      (is (= "02820" (:postal-code summary)))
      (is (= :paavo (:postal-code-source summary))))

    (testing "no caveat when the polygon and the far address agree anyway"
      (is (nil? (:alternative-postal-code summary))))

    (testing "the address is still shown, with the distance that disqualified it"
      (is (= "Haukkaranta 16" (:address summary)))
      (is (= 1173 (:address-distance-m summary))))

    (testing "the Paavo area is reported in full"
      (is (= {:postal-code "02820"
              :name {:fi "Nupuri-Nuuksio" :sv "Nupurböle-Noux"}
              :postal-office {:fi "ESPOO" :sv "ESBO"}
              :municipality {:code "049" :name {:fi "Espoo" :sv "Esbo"}}}
             area)))

    (testing "an OSM address Posti has never heard of says so"
      (let [phantom (second addresses)]
        (is (= "Valklammentie 105, Vihti" (:label phantom)))
        (is (= {:code "927" :name {:fi "Vihti" :sv "Vichtis"}} (:municipality phantom))
            "Pelias' localadmin still resolves to a municipality")
        (is (= "03220" (:pelias-postal-code phantom)))
        (is (nil? (:posti phantom)) "BAF has no Valklammentie in Vihti")))))

(deftest disagreement-test
  (testing "polygon and far address disagree: the polygon answers, the address is the caveat"
    (let [{:keys [summary]}
          (reverse-geocode {:features nuuksio-features
                            :paavo (paavo-area "03220" "Tervalampi" "Tervalampi" "927")})]
      (is (= "03220" (:postal-code summary)))
      (is (= :paavo (:postal-code-source summary)))
      (is (= "02820" (:alternative-postal-code summary)))
      (is (= {:code "927" :name {:fi "Vihti" :sv "Vichtis"}} (:municipality summary))
          "containing the point beats matching a name")))

  (testing "polygon and NEAR address disagree: the address answers, the polygon is the caveat"
    (let [{:keys [summary]}
          (reverse-geocode {:features [(feature "Haukkaranta" "16" "Espoo" "02820" 0.05)]
                            :paavo (paavo-area "03220" "Tervalampi" "Tervalampi" "927")})]
      (is (= "02820" (:postal-code summary)))
      (is (= :posti (:postal-code-source summary)))
      (is (= "03220" (:alternative-postal-code summary))))))

(deftest phantom-address-only-test
  (testing "the only address is one BAF doesn't know: Paavo carries the answer alone"
    (let [{:keys [summary addresses]}
          (reverse-geocode {:features [(feature "Valklammentie" "105" "Vihti" "03220" 1.19)]
                            :paavo (paavo-area "03220" "Tervalampi" "Tervalampi" "927")})]
      (is (nil? (:posti (first addresses))))
      (is (= "03220" (:postal-code summary)))
      (is (= :paavo (:postal-code-source summary)))
      (is (nil? (:alternative-postal-code summary))
          "there is no Posti answer to disagree with")))

  (testing "with no polygon either, Pelias' inherited code is the last resort"
    (let [{:keys [summary]}
          (reverse-geocode {:features [(feature "Valklammentie" "105" "Vihti" "03220" 1.19)]
                            :paavo nil})]
      (is (= "03220" (:postal-code summary)))
      (is (= :pelias (:postal-code-source summary)))
      (is (= {:pelias :ok :paavo :empty} (:sources summary))))))

(deftest far-exact-match-without-polygon-test
  (testing "no polygon: even a distant BAF-exact address beats Pelias' hint"
    (let [{:keys [summary]} (reverse-geocode {:features nuuksio-features :paavo nil})]
      (is (= "02820" (:postal-code summary)))
      (is (= :posti (:postal-code-source summary)))
      (is (= {:code "049" :name {:fi "Espoo" :sv "Esbo"}} (:municipality summary))
          "with no polygon the municipality comes from the matched address"))))

(deftest sea-click-test
  (testing "no addresses and no polygon is an empty answer, not an error"
    (let [{:keys [summary area addresses point]}
          (reverse-geocode {:lat 59.9 :lon 20.5 :features [] :paavo nil})]
      (is (= {:lat 59.9 :lon 20.5} point))
      (is (nil? area))
      (is (empty? addresses))
      (is (nil? (:postal-code summary)))
      (is (nil? (:postal-code-source summary)))
      (is (nil? (:address summary)))
      (is (nil? (:municipality summary)))
      (is (= {:pelias :empty :paavo :empty} (:sources summary))))))

(deftest pelias-failure-degrades-test
  (testing "a Pelias outage still answers from the polygon, and says it degraded"
    (let [{:keys [summary addresses]}
          (reverse-geocode {:features []
                            :pelias-status :error
                            :paavo (paavo-area "02820" "Nupuri-Nuuksio" "Nupurböle-Noux" "049")})]
      (is (empty? addresses))
      (is (= "02820" (:postal-code summary)))
      (is (= :paavo (:postal-code-source summary)))
      (is (= {:fi "ESPOO" :sv "ESBO"} (:postal-office summary)))
      (is (= {:fi "Helsinki-Uusimaa" :sv "Helsingfors-Nyland"} (:region summary)))
      (is (= {:pelias :error :paavo :ok} (:sources summary))))))

(deftest addresses-are-sorted-and-capped-test
  (let [features (for [i (range 8)]
                   (feature "Haukkaranta" (str (- 16 i)) "Espoo" "02820" (- 1.0 (* 0.01 i))))
        {:keys [addresses summary]} (reverse-geocode {:features (shuffle features)
                                                      :paavo nil})]
    (testing "at most five addresses reach the client"
      (is (= address/max-addresses (count addresses))))

    (testing "nearest first, and the summary leads with that one"
      (is (= (sort (map :distance-m addresses)) (map :distance-m addresses)))
      (is (= 930 (:distance-m (first addresses))))
      (is (= 930 (:address-distance-m summary))))))

(deftest building-number-without-a-match-is-not-exact-test
  (testing "a number in a BAF gap resolves to the street's code, flagged inexact"
    (let [{:keys [addresses summary]}
          (reverse-geocode {:features [(feature "Haukkaranta" "99" "Espoo" "02820" 0.02)]
                            :paavo nil})]
      (is (= "02820" (get-in (first addresses) [:posti :postal-code])))
      (is (false? (get-in (first addresses) [:posti :exact])))
      (is (= :pelias (:postal-code-source summary))
          "an inexact match is not A, so rule 3 falls through to Pelias' hint"))))

(deftest unstructured-pelias-properties-test
  (testing "street and number are recovered from `name` when Pelias omits them"
    (let [{:keys [addresses]}
          (reverse-geocode {:features [{:name "Mannerheimintie 5"
                                        :label "Mannerheimintie 5, Helsinki"
                                        :localadmin "Helsinki"
                                        :postalcode "00100"
                                        :distance 0.03}]
                            :paavo nil})]
      (is (= "5" (:number (first addresses))))
      (is (= "Mannerheimintie" (get-in (first addresses) [:street :fi])))
      (is (true? (get-in (first addresses) [:posti :exact]))))))

(deftest swedish-localadmin-and-street-test
  (testing "a Swedish municipality and street name find the same BAF segments"
    (let [{:keys [addresses]}
          (reverse-geocode {:features [(feature "Mannerheimvägen" "5" "Helsingfors" "00100" 0.03)]
                            :paavo nil})]
      (is (= {:code "091" :name {:fi "Helsinki" :sv "Helsingfors"}}
             (:municipality (first addresses))))
      (is (= {:fi "Mannerheimintie" :sv "Mannerheimvägen"} (:street (first addresses)))
          "the display names come from BAF, not from Pelias")
      (is (true? (get-in (first addresses) [:posti :exact]))))))

;;; Posti data files ;;;

(defn- resource-bytes [path]
  (with-open [in ^java.io.InputStream (io/input-stream (io/resource path))]
    (.readAllBytes in)))

(def ^:private listing-html
  "A webpcode page in miniature: several runs of each kind, absolute and
  relative hrefs, and unrelated links in between."
  (str "<html><body>"
       "<a href=\"https://www.posti.fi/webpcode/zip/PCF_20260808.zip\">zipped</a>"
       "<a href=\"https://www.posti.fi/webpcode/unzip/PCF_20260801.dat\">old</a>"
       "<a href=\"unzip/PCF_20260807.dat\">latest</a>"
       "<a href=\"unzip/BAF_20260725.dat\">old</a>"
       "<a href=\"https://www.posti.fi/webpcode/unzip/BAF_20260801.dat\">latest</a>"
       "</body></html>"))

(defn- fake-download [url]
  (cond
    (str/includes? url "PCF") (resource-bytes "posti/pcf-sample.dat")
    (str/includes? url "BAF") (resource-bytes "posti/baf-sample.dat")
    :else (throw (ex-info "Unexpected download" {:url url}))))

(defn- fake-fetchers []
  {:listing (fn [] listing-html)
   :download fake-download})

(deftest latest-posti-dat-test
  (testing "the newest run wins, whatever order the links appear in"
    (is (= {:url "https://www.posti.fi/webpcode/unzip/PCF_20260807.dat"
            :run-date "2026-08-07"}
           (address/latest-posti-dat listing-html "PCF")))
    (is (= {:url "https://www.posti.fi/webpcode/unzip/BAF_20260801.dat"
            :run-date "2026-08-01"}
           (address/latest-posti-dat listing-html "BAF"))))

  (testing "relative hrefs resolve against the listing url"
    (is (= "https://www.posti.fi/webpcode/unzip/BAF_20260725.dat"
           (:url (address/latest-posti-dat "<a href=\"unzip/BAF_20260725.dat\">x</a>" "BAF")))))

  (testing "a page without the expected links is an error, not 'nothing new'"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"No unzip/PCF"
                          (address/latest-posti-dat "<html>nothing here</html>" "PCF")))))

(deftest refresh-postal-data-test
  (let [db (test-db)]

    (testing "an empty database imports both files"
      (let [result (address/refresh-postal-data! db (fake-fetchers))]
        (is (= {:status :imported :run-date "2026-08-07" :stored nil :count 6}
               (get result "PCF")))
        (is (= {:status :imported :run-date "2026-08-01" :stored nil :count 4}
               (get result "BAF")))
        (is (= "2026-08-07" (:run-date (address-db/get-data-source db "pcf"))))
        (is (= "2026-08-01" (:run-date (address-db/get-data-source db "baf"))))))

    (testing "a second run with the same published files imports nothing"
      (let [downloads (atom [])
            result (address/refresh-postal-data!
                     db (assoc (fake-fetchers)
                               :download (fn [url]
                                           (swap! downloads conj url)
                                           (fake-download url))))]
        (is (= {:status :skipped :run-date "2026-08-07" :stored "2026-08-07"}
               (get result "PCF")))
        (is (= {:status :skipped :run-date "2026-08-01" :stored "2026-08-01"}
               (get result "BAF")))
        (is (empty? @downloads) "a skipped file is never downloaded")))

    (testing "only the file with a newer run is imported"
      (let [newer (str/replace listing-html "PCF_20260807" "PCF_20260808")
            result (address/refresh-postal-data!
                     db (assoc (fake-fetchers) :listing (fn [] newer)))]
        (is (= :imported (:status (get result "PCF"))))
        (is (= "2026-08-08" (:run-date (get result "PCF"))))
        (is (= :skipped (:status (get result "BAF"))))))))

(deftest parse-failure-fails-the-job-test
  (let [db (test-db)]

    (testing "a malformed line aborts the import with its line number"
      (let [broken (.getBytes "not a PCF record at all\n" "ISO-8859-1")
            ex (is (thrown-with-msg?
                     clojure.lang.ExceptionInfo #"PCF: 1 unparseable line"
                     (address/refresh-postal-data!
                       db (assoc (fake-fetchers) :download (constantly broken)))))]
        (is (= 1 (:error-count (ex-data ex))))
        (is (= 1 (:line-number (first (:errors (ex-data ex))))))))

    (testing "and leaves the previous data in place"
      (is (nil? (address-db/get-data-source db "pcf")))
      (is (nil? (address-db/get-postal-code db "79700"))))))

;;; Paavo ;;;

(defn- paavo-collection [year]
  {:features [{:type "Feature"
               :properties {:posti_alue "00100" :nimi "Testialue" :namn "Testområde"
                            :kunta "091" :vuosi year}
               :geometry {:type "Polygon"
                          :coordinates [[[24.0 60.0] [25.0 60.0] [25.0 61.0]
                                         [24.0 61.0] [24.0 60.0]]]}}
              {:type "Feature"
               :properties {:posti_alue "00200" :nimi "Toinen" :namn "Andra"
                            :kunta "091" :vuosi year}
               :geometry {:type "Polygon"
                          :coordinates [[[26.0 60.0] [27.0 60.0] [27.0 61.0]
                                         [26.0 61.0] [26.0 60.0]]]}}]})

(defn- paavo-fetchers
  "Fetchers for a published layer of `year`. `downloads` counts how often the
  full layer was actually pulled."
  [year downloads]
  {:fetch-year (fn [] year)
   :fetch (fn [] (swap! downloads inc) (paavo-collection year))})

(deftest refresh-paavo-areas-test
  (let [db (test-db)
        downloads (atom 0)]

    (testing "an empty table self-seeds regardless of the year"
      (is (= {:status :imported :year 2026 :stored nil :count 2}
             (address/refresh-paavo-areas! db (paavo-fetchers 2026 downloads))))
      (is (= 1 @downloads)))

    (testing "the same year is a no-op and never downloads the layer"
      (is (= {:status :skipped :year 2026 :stored 2026}
             (address/refresh-paavo-areas! db (paavo-fetchers 2026 downloads))))
      (is (= 1 @downloads)))

    (testing "an older year is a no-op too"
      (is (= {:status :skipped :year 2025 :stored 2026}
             (address/refresh-paavo-areas! db (paavo-fetchers 2025 downloads))))
      (is (= 1 @downloads)))

    (testing "a newer year imports"
      (is (= {:status :imported :year 2027 :stored 2026 :count 2}
             (address/refresh-paavo-areas! db (paavo-fetchers 2027 downloads))))
      (is (= 2 @downloads))
      (is (= 2027 (address-db/get-paavo-year db)))
      (is (= "00100" (:postal-code (address-db/get-paavo-area db 24.5 60.5)))))

    (testing "an empty feature collection is an error, not an empty import"
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"no features"
                            (address/refresh-paavo-areas!
                              db {:fetch-year (fn [] 2028)
                                  :fetch (fn [] {:features []})})))
      (is (= 2027 (address-db/get-paavo-year db)) "the previous areas survive"))

    (testing "a layer without a vuosi is an error"
      (is (thrown-with-msg?
            clojure.lang.ExceptionInfo #"no vuosi"
            (address/refresh-paavo-areas!
              db {:fetch-year (fn [] nil)
                  :fetch (fn [] (paavo-collection 2028))}))))))

(deftest job-registration-test
  (testing "both refresh jobs are registered, dispatchable and self-deduplicating"
    (doseq [job-type ["fetch-postal-data" "fetch-paavo-areas"]]
      (let [job-def (registry/get-def job-type)]
        (is (= :slow (:lane job-def)) job-type)
        (is (= 3 (:max-attempts job-def)) job-type)
        (is (<= 30 (:timeout-min job-def)) job-type)
        (is (= {:valid? true} (registry/validate-payload job-type {})) job-type)
        (is (= job-type ((:dedup-key-fn job-def) {}))
            "one pending run at a time, whatever the payload")
        (is (contains? (methods dispatcher/handle-job) job-type)
            (str job-type " has no dispatcher handler"))))))
