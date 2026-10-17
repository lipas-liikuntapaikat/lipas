(ns lipas.backend.address.posti-test
  "Parser tests against real-data samples of Posti's PCF and BAF files
  (`test/resources/posti/*-sample.dat`, six PCF and five BAF records lifted
  from production files)."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [lipas.backend.address.posti :as posti]))

(def pcf-lines (posti/read-lines (io/resource "posti/pcf-sample.dat") vec))
(def baf-lines (posti/read-lines (io/resource "posti/baf-sample.dat") vec))

(defn- parse-pcf [line]
  (let [parsed (posti/parse-pcf-line line)]
    (is (not (posti/parse-error? parsed)) (:error parsed))
    parsed))

(defn- parse-baf [line]
  (let [parsed (posti/parse-baf-line line)]
    (is (not (posti/parse-error? parsed)) (:error parsed))
    parsed))

(defn- splice
  "`line` with `s` written over it starting at 1-based `start`."
  [line start s]
  (str (subs line 0 (dec start)) s (subs line (+ (dec start) (count s)))))

;;; Decoding ;;;

(deftest read-lines-test
  (testing "the samples decode as ISO-8859-1, not as UTF-8"
    (is (= 6 (count pcf-lines)))
    (is (= 5 (count baf-lines)))
    (is (every? #(= posti/pcf-line-length (count %)) pcf-lines))
    (is (every? #(= posti/baf-line-length (count %)) baf-lines))
    (is (str/includes? (first pcf-lines) "HEINÄVESI")))

  (testing "byte arrays are accepted as a source"
    (let [bytes (.getBytes "Mannerheimvägen\n" posti/charset)]
      (is (= ["Mannerheimvägen"] (posti/read-lines bytes vec))))))

;;; PCF ;;;

(deftest parse-pcf-line-test
  (testing "every real-data fixture line parses"
    (is (every? (complement posti/parse-error?) (map posti/parse-pcf-line pcf-lines))))

  (testing "a normal postal code record"
    (is (= {:run-date "2026-08-07"
            :postal-code "79700"
            :name-fi "HEINÄVESI"
            :name-sv "HEINÄVESI"
            :abbreviation-fi nil
            :abbreviation-sv nil
            :valid-from "1988-11-01"
            :type-code "1"
            :type :normal
            :region-code "FI1D3"
            :region-name-fi "Pohjois-Karjala"
            :region-name-sv "Norra Karelen"
            :municipality-code "090"
            :municipality-name-fi "Heinävesi"
            :municipality-name-sv "Heinävesi"
            :municipality-language-code "1"}
           (parse-pcf (nth pcf-lines 0)))))

  (testing "the PO box type"
    (let [kuopio (parse-pcf (nth pcf-lines 2))]
      (is (= "70101" (:postal-code kuopio)))
      (is (= :po-box (:type kuopio)))))

  (testing "an Åland record with a Swedish-language municipality"
    (let [fisko (parse-pcf (nth pcf-lines 5))]
      (is (= {:postal-code "22930"
              :name-fi "FISKÖ"
              :region-name-sv "Åland"
              :municipality-code "035"
              :municipality-name-fi "Brändö"
              :municipality-language-code "4"}
             (select-keys fisko [:postal-code :name-fi :region-name-sv :municipality-code
                                 :municipality-name-fi :municipality-language-code]))))))

(deftest parse-pcf-line-errors-test
  (let [line (nth pcf-lines 0)
        error #(:error (posti/parse-pcf-line %))]
    (testing "wrong length"
      (is (= "expected 220 chars, got 15" (error "PONOT too short")))
      (is (= "expected 220 chars, got 221" (error (str line " ")))))

    (testing "wrong record id"
      (is (= "expected record id PONOT, got 'KATUN'" (error (splice line 1 "KATUN")))))

    (testing "unparseable dates and postal codes"
      (is (= "invalid run date 'abcdefgh'" (error (splice line 6 "abcdefgh"))))
      (is (= "invalid postal code '7970'" (error (splice line 14 "7970 "))))
      (is (= "invalid postal code '797x0'" (error (splice line 14 "797x0")))))

    (testing "a blank valid-from is not an error, just missing"
      (is (nil? (:valid-from (parse-pcf (splice line 103 "        "))))))

    (testing "an unknown type code parses as :unknown"
      (is (= :unknown (:type (parse-pcf (splice line 111 "9"))))))))

;;; BAF ;;;

(deftest parse-baf-line-test
  (testing "every real-data fixture line parses"
    (is (every? (complement posti/parse-error?) (map posti/parse-baf-line baf-lines))))

  (testing "an odd-side street segment with a lettered max number"
    (is (= {:run-date "2026-08-01"
            :postal-code "00100"
            :postal-code-name-fi "HELSINKI"
            :postal-code-name-sv "HELSINGFORS"
            :abbreviation-fi "HKI"
            :abbreviation-sv "HFORS"
            :street-name-fi "Mannerheimintie"
            :street-name-sv "Mannerheimvägen"
            :side :odd
            :min-bound {:number 1 :letter nil :number2 nil :letter2 nil}
            :max-bound {:number 13 :letter "e" :number2 nil :letter2 nil}
            :municipality-code "091"
            :municipality-name-fi "Helsinki"
            :municipality-name-sv "Helsingfors"}
           (parse-baf (nth baf-lines 0)))))

  (testing "an even-side segment with a lettered min number"
    (is (= {:side :even
            :min-bound {:number 2 :letter "a" :number2 nil :letter2 nil}
            :max-bound {:number 40 :letter nil :number2 nil :letter2 nil}}
           (select-keys (parse-baf (nth baf-lines 1)) [:side :min-bound :max-bound]))))

  (testing "dual building numbers (\"20/22\")"
    (let [record (parse-baf (nth baf-lines 3))]
      (is (= "Nordenskiöldinkatu" (:street-name-fi record)))
      (is (= {:number 20 :letter nil :number2 22 :letter2 nil} (:max-bound record)))))

  (testing "a street-less record (side code '0')"
    (is (= {:postal-code "00002"
            :street-name-fi nil
            :street-name-sv nil
            :side nil
            :min-bound nil
            :max-bound nil
            :municipality-code "091"}
           (select-keys (parse-baf (nth baf-lines 4))
                        [:postal-code :street-name-fi :street-name-sv
                         :side :min-bound :max-bound :municipality-code])))))

(deftest parse-baf-line-errors-test
  (let [line (nth baf-lines 0)
        error #(:error (posti/parse-baf-line %))]
    (testing "wrong length"
      (is (= "expected 256 chars, got 15" (error "KATUN too short"))))

    (testing "wrong record id"
      (is (= "expected record id KATUN, got 'PONOT'" (error (splice line 1 "PONOT")))))

    (testing "unparseable dates and postal codes"
      (is (= "invalid run date '2026080'" (error (splice line 6 "2026080 "))))
      (is (= "invalid postal code '0010x'" (error (splice line 14 "0010x")))))

    (testing "unknown side codes"
      (is (= "invalid side code '3'" (error (splice line 187 "3"))))
      (is (nil? (:side (parse-baf (splice line 187 "0")))))
      (is (nil? (:side (parse-baf (splice line 187 " "))))))

    (testing "non-numeric building numbers"
      (is (= "invalid building number 'abcde/'" (error (splice line 188 "abcde"))))
      (is (= "invalid second building number '9x'" (error (splice line 195 "   9x"))))
      (is (= "invalid building number 'x/'" (error (splice line 201 "  x   ")))))))

;;; Whole files ;;;

(deftest parse-lines-test
  (testing "sample files parse without a single error"
    (let [{:keys [records errors]} (posti/parse-pcf-lines pcf-lines)]
      (is (= 6 (count records)))
      (is (= [] errors)))
    (let [{:keys [records errors]} (posti/parse-baf-lines baf-lines)]
      (is (= 5 (count records)))
      (is (= [] errors))))

  (testing "malformed lines are reported, not dropped"
    (let [lines (concat (take 2 baf-lines) ["oops"] (drop 2 baf-lines))
          {:keys [records errors]} (posti/parse-baf-lines lines)]
      (is (= 5 (count records)))
      (is (= [{:line-number 3
               :line "oops"
               :error "expected 256 chars, got 4"}]
             errors))))

  (testing "empty lines (trailing newline) are skipped silently"
    (let [{:keys [records errors]} (posti/parse-pcf-lines (concat pcf-lines [""]))]
      (is (= 6 (count records)))
      (is (= [] errors)))))

;;; Name normalization ;;;

(deftest name-key-test
  (testing "lowercases and folds Finnish/Swedish diacritics"
    (is (= "hameentie" (posti/name-key "Hämeentie")))
    (is (= "mannerheimvagen" (posti/name-key "Mannerheimvägen")))
    (is (= "akerlundinkatu" (posti/name-key "Åkerlundinkatu")))
    (is (= "tornea" (posti/name-key "TORNEÅ"))))

  (testing "collapses punctuation and whitespace to single spaces"
    (is (= "7 linja" (posti/name-key "7. Linja")))
    (is (= "castren snellmanin tie" (posti/name-key "Castrén-Snellmanin  tie"))))

  (testing "a name that folds away entirely gives an empty key"
    (is (= "" (posti/name-key "  -- ")))))

;;; Generative ;;;

(defn- truncate [s length]
  (str/trim (subs s 0 (min length (count s)))))

(def ^:private field-gen
  (gen/fmap #(str/join " " %)
            (gen/vector (gen/not-empty gen/string-alphanumeric) 1 3)))

(def ^:private letter-gen (gen/elements [nil "a" "b" "z"]))

(def ^:private bound-gen
  (gen/let [number (gen/choose 1 99999)
            letter letter-gen
            number2 (gen/one-of [(gen/return nil) (gen/choose 1 99999)])
            letter2 letter-gen]
    {:number number :letter letter :number2 number2 :letter2 letter2}))

(def ^:private baf-record-gen
  (gen/let [postal-code (gen/fmap #(format "%05d" %) (gen/choose 0 99999))
            names (gen/vector field-gen 7)
            side (gen/elements [nil :odd :even])
            min-bound bound-gen
            max-bound bound-gen
            municipality-code (gen/fmap #(format "%03d" %) (gen/choose 0 999))]
    (let [[name-fi name-sv abbr-fi abbr-sv street-fi street-sv muni-fi] names]
      {:run-date "2026-08-01"
       :postal-code postal-code
       :postal-code-name-fi (truncate name-fi 30)
       :postal-code-name-sv (truncate name-sv 30)
       :abbreviation-fi (truncate abbr-fi 12)
       :abbreviation-sv (truncate abbr-sv 12)
       :street-name-fi (truncate street-fi 30)
       :street-name-sv (truncate street-sv 30)
       :side side
       :min-bound min-bound
       :max-bound max-bound
       :municipality-code municipality-code
       :municipality-name-fi (truncate muni-fi 20)
       :municipality-name-sv (truncate muni-fi 20)})))

(defn- pad [s length]
  (str s (str/join (repeat (- length (count s)) \space))))

(defn- render-bound
  "The 13 characters BAF spends on one building number group."
  [{:keys [number letter number2 letter2]}]
  (str (format "%5d" number)
       (or letter " ")
       (if number2 "/" " ")
       (if number2 (format "%5d" number2) "     ")
       (or letter2 " ")))

(defn- render-baf-line
  [{:keys [run-date postal-code postal-code-name-fi postal-code-name-sv
           abbreviation-fi abbreviation-sv street-name-fi street-name-sv
           side min-bound max-bound
           municipality-code municipality-name-fi municipality-name-sv]}]
  (str "KATUN"
       (str/replace run-date "-" "")
       postal-code
       (pad postal-code-name-fi 30)
       (pad postal-code-name-sv 30)
       (pad abbreviation-fi 12)
       (pad abbreviation-sv 12)
       (pad street-name-fi 30)
       (pad street-name-sv 30)
       (pad "" 24)
       (case side :odd "1" :even "2" "0")
       (render-bound min-bound)
       (render-bound max-bound)
       municipality-code
       (pad municipality-name-fi 20)
       (pad municipality-name-sv 20)))

(defspec baf-line-round-trip 100
  (prop/for-all [record baf-record-gen]
                (let [line (render-baf-line record)]
                  (and (= posti/baf-line-length (count line))
                       (= record (posti/parse-baf-line line))))))

(def ^:private corrupted-line-gen
  "A real fixture line with a short slice of junk written over it, anywhere in
  the first 200 columns — record id, dates, codes, side and building numbers
  all get hit."
  (gen/let [line (gen/elements (concat pcf-lines baf-lines))
            start (gen/choose 1 200)
            junk (gen/fmap str/join
                           (gen/vector (gen/elements [\K \A \T \U \N \P \O \x
                                                      \0 \1 \9 \/ \space \ä])
                                       0 10))]
    (splice line start junk)))

(defspec parsers-report-instead-of-throwing 200
  ;; Import jobs must be able to report every bad line: a parser blowing up on
  ;; corrupted input would take the whole file with it, and a record must never
  ;; come back half-parsed.
  (prop/for-all [line corrupted-line-gen]
                (every? (fn [parse]
                          (let [parsed (parse line)]
                            (or (posti/parse-error? parsed)
                                (some? (re-matches #"\d{5}" (:postal-code parsed))))))
                        [posti/parse-pcf-line posti/parse-baf-line])))
