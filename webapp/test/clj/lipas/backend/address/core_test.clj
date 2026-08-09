(ns lipas.backend.address.core-test
  "Tests for the postal data refresh jobs. The fetching fns are injected, so
  the whole discover -> download -> parse -> import path runs against the test
  database without touching posti.fi or geo.stat.fi."
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
