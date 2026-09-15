(ns lipas.maintenance-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [lipas.maintenance :as maint]
            [malli.core :as m]))

(deftest subsidy-issuer-normalization-test
  (testing "historical issuers fold into the current agency"
    (is (= "LVV" (maint/normalize-subsidy-issuer "ELY")))
    (is (= "LVV" (maint/normalize-subsidy-issuer "AVI")))
    (is (= "OKM" (maint/normalize-subsidy-issuer "OPM"))))
  (testing "current issuers pass through"
    (is (= "LVV" (maint/normalize-subsidy-issuer "LVV")))
    (is (= "OKM" (maint/normalize-subsidy-issuer "OKM"))))
  (testing "db schema accepts exactly the normalized issuers"
    (doseq [issuer (distinct (vals maint/subsidy-issuer-normalization))]
      (is (m/validate (m/schema [:enum "LVV" "OKM"]) issuer)))))

(defn- csv-file [header rows]
  (let [f (java.io.File/createTempFile "subsidies" ".csv")]
    (.deleteOnExit f)
    (with-open [w (io/writer f)]
      (.write w (str header "\n"))
      (doseq [r rows] (.write w (str r "\n"))))
    (.getPath f)))

(deftest read-subsidies-csv-test
  (let [;; Header as exported from the 2026 sheet: trailing/double spaces.
        header (str "Avustuksen myöntäjä ,Tyyppikoodi (numero),Lipas-ID,"
                    "\"Saajataho, luokiteltu (Lipas-luokitus omistaja)\","
                    "Avustuksen saaja,Avustuksen selite,Kunta myöntövuonna,"
                    "Myöntövuosi,Myönnetty avustus tuhatta  e")
        rows ["LVV,3110;2210,505864;88919,Kunta,Ylitornion kunta,Uimahalli,Ylitornio,2026,600"
              "AVI,1120,,Kunta,Vantaan kaupunki,,Vantaa,2025,135"
              "OKM,1220,75921,Säätiö,Stadion-säätiö sr,Perusparannus,Helsinki,2026,324.34"]
        entries (maint/read-subsidies-csv (csv-file header rows))]
    (testing "header whitespace is tolerated"
      (is (every? (comp number? :amount) entries))
      (is (every? #(contains? % :issuer) entries)))
    (testing "entries validate against the db schema"
      (is (m/validate maint/subsidy-db-entries-schema entries)
          (pr-str (m/explain maint/subsidy-db-entries-schema entries))))
    (testing "field parsing"
      (let [[a b c] entries]
        (is (= {:issuer "LVV" :type-codes #{3110 2210} :lipas-ids [505864 88919]
                :city-code 976 :owner "city" :year 2026 :amount 600}
               (select-keys a [:issuer :type-codes :lipas-ids :city-code :owner :year :amount])))
        (is (= "LVV" (:issuer b)) "AVI folds into LVV")
        (is (= [] (:lipas-ids b)))
        (is (= {:issuer "OKM" :owner "foundation" :amount 324.34}
               (select-keys c [:issuer :owner :amount])))))))
