(ns lipas.backend.address.db-test
  "Integration tests for the postal caches: the sample PCF/BAF files are
  imported into the test database and then queried the way the
  reverse-geocode endpoint will query them."
  (:require
    [clojure.java.io :as io]
    [clojure.test :refer [deftest is testing use-fixtures]]
    [lipas.backend.address.db :as address-db]
    [lipas.backend.address.posti :as posti]
    [lipas.backend.address.resolve :as resolve]
    [lipas.test-utils :as test-utils]
    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as rs]))

(defonce test-system (atom nil))

(let [{:keys [once each]} (test-utils/db-only-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn- test-db [] (:lipas/db @test-system))

(defn- pcf-records []
  (:records (posti/parse-pcf-file (io/resource "posti/pcf-sample.dat"))))

(defn- baf-records []
  (:records (posti/parse-baf-file (io/resource "posti/baf-sample.dat"))))

(defn- row-count [db table]
  (:n (jdbc/execute-one! db
                         [(str "SELECT count(*) AS n FROM " table)]
                         {:builder-fn rs/as-unqualified-lower-maps})))

(deftest postal-code-import-test
  (let [db      (test-db)
        records (pcf-records)
        n       (address-db/replace-postal-codes! db records "2026-08-07")]

    (testing "every PCF record becomes a row"
      (is (= 6 (count records)))
      (is (= 6 n))
      (is (= 6 (row-count db "postal_code"))))

    (testing "a code carries its postitoimipaikka, municipality and region"
      (let [row (address-db/get-postal-code db "79700")]
        (is (= "HEINÄVESI" (:name-fi row)))
        (is (= "HEINÄVESI" (:name-sv row)))
        (is (= "normal" (:type row)))
        (is (= "090" (:municipality-code row)))
        (is (= "Heinävesi" (:municipality-name-fi row)))
        (is (= "Pohjois-Karjala" (:region-name-fi row)))
        (is (= "Norra Karelen" (:region-name-sv row)))
        (is (= "1988-11-01" (str (:valid-from row))))))

    (testing "the PCF type keyword is stored as text"
      (is (= "po-box" (:type (address-db/get-postal-code db "70101")))))

    (testing "an unknown code is nil rather than an empty row"
      (is (nil? (address-db/get-postal-code db "99999"))))

    (testing "the import is recorded as the loaded pcf run"
      (is (= {:kind "pcf" :run-date "2026-08-07"}
             (select-keys (address-db/get-data-source db "pcf") [:kind :run-date]))))

    (testing "re-importing replaces rather than appends"
      (address-db/replace-postal-codes! db (take 2 records) "2026-08-08")
      (is (= 2 (row-count db "postal_code")))
      (is (= "2026-08-08" (:run-date (address-db/get-data-source db "pcf")))))))

(deftest street-segment-import-test
  (let [db      (test-db)
        records (baf-records)
        n       (address-db/replace-street-segments! db records "2026-08-01")]

    (testing "BAF rows without a street address are not segments"
      (is (= 5 (count records)))
      (is (= 1 (count (remove address-db/street-segment? records)))
          "the sample carries one side-'0' row (postal code 00002)")
      (is (= 4 n))
      (is (= 4 (row-count db "postal_street_segment"))))

    (testing "the import is recorded as the loaded baf run"
      (is (= "2026-08-01" (:run-date (address-db/get-data-source db "baf")))))

    (testing "a street is found by its normalized Finnish name"
      (let [segments (address-db/get-street-segments db (posti/name-key "Mannerheimintie") "091")]
        (is (= 2 (count segments)))
        (is (= #{:odd :even} (set (map :side segments))))
        (is (= #{"Mannerheimintie"} (set (map :name-fi segments))))))

    (testing "and by its Swedish one"
      (is (= 2 (count (address-db/get-street-segments
                        db (posti/name-key "Mannerheimvägen") "091")))))

    (testing "the same street in another municipality is a different street"
      (is (empty? (address-db/get-street-segments
                    db (posti/name-key "Mannerheimintie") "049"))))

    (testing "bounds survive the jsonb round trip in the shape resolve expects"
      (let [segments (address-db/get-street-segments db "mannerheimintie" "091")
            odd      (first (filter #(= :odd (:side %)) segments))]
        (is (= {:number 1 :letter nil :number2 nil :letter2 nil} (:min-bound odd)))
        (is (= {:number 13 :letter "e" :number2 nil :letter2 nil} (:max-bound odd)))))

    (testing "resolving a building number against the stored segments"
      (let [segments (address-db/get-street-segments db "mannerheimintie" "091")]
        (is (= [{:postal-code "00100" :exact? true}]
               (resolve/resolve-postal-codes segments 5))
            "Mannerheimintie 5 is on the odd side, inside 1-13e")
        (is (= [{:postal-code "00100" :exact? true}]
               (resolve/resolve-postal-codes segments 40))
            "40 is the even side's upper bound")
        (is (= [{:postal-code "00100" :exact? false}]
               (resolve/resolve-postal-codes segments 401))
            "a number past every segment falls back to the street's codes")))

    (testing "a dual upper bound covers the whole range"
      (let [segments (address-db/get-street-segments db "nordenskioldinkatu" "091")]
        (is (= [{:postal-code "00250" :exact? true}]
               (resolve/resolve-postal-codes segments 22)))))))

;;; Paavo ;;;

(defn- square
  "A GeoJSON Polygon feature covering [lon, lon+1] x [lat, lat+1]."
  [postal-code lon lat year]
  {:type "Feature"
   :properties {:posti_alue postal-code
                :nimi (str "Testialue " postal-code)
                :namn (str "Testområde " postal-code)
                :kunta "091"
                :vuosi year}
   :geometry {:type "Polygon"
              :coordinates [[[lon lat]
                             [(+ lon 1) lat]
                             [(+ lon 1) (+ lat 1)]
                             [lon (+ lat 1)]
                             [lon lat]]]}})

(defn- feature-collection [year]
  {:type "FeatureCollection"
   :features [(square "00100" 24.0 60.0 year)
              (square "00200" 26.0 60.0 year)]})

(deftest paavo-import-test
  (let [db (test-db)
        n  (address-db/replace-paavo-areas! db (:features (feature-collection 2026)) 2026)]

    (testing "every feature becomes a row"
      (is (= 2 n))
      (is (= 2 (row-count db "paavo_area")))
      (is (= 2026 (address-db/get-paavo-year db))))

    (testing "the import is recorded as the loaded paavo run"
      (is (= "2026-01-01" (:run-date (address-db/get-data-source db "paavo")))))

    (testing "a point inside a polygon finds its area"
      (let [area (address-db/get-paavo-area db 24.5 60.5)]
        (is (= "00100" (:postal-code area)))
        (is (= "Testialue 00100" (:name-fi area)))
        (is (= "Testområde 00100" (:name-sv area)))
        (is (= "091" (:municipality-code area)))
        (is (= 2026 (:year area)))))

    (testing "arguments are (lon, lat), not (lat, lon)"
      (is (= "00200" (:postal-code (address-db/get-paavo-area db 26.5 60.5)))))

    (testing "a point outside every polygon is nil"
      (is (nil? (address-db/get-paavo-area db 30.0 60.5)))
      (is (nil? (address-db/get-paavo-area db 24.5 65.0))))

    (testing "re-importing replaces rather than appends"
      (address-db/replace-paavo-areas! db [(square "00100" 24.0 60.0 2027)] 2027)
      (is (= 1 (row-count db "paavo_area")))
      (is (= 2027 (address-db/get-paavo-year db)))
      (is (nil? (address-db/get-paavo-area db 26.5 60.5))))))

(deftest empty-database-test
  (let [db (test-db)]
    (testing "queries against empty tables answer nil rather than throwing"
      (is (nil? (address-db/get-data-source db "pcf")))
      (is (nil? (address-db/get-paavo-year db)))
      (is (nil? (address-db/get-paavo-area db 24.5 60.5)))
      (is (nil? (address-db/get-postal-code db "00100")))
      (is (empty? (address-db/get-street-segments db "mannerheimintie" "091"))))))
