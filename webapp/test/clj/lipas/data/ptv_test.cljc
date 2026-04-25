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

(deftest parse-www
  (is (= "http://example.com"
         (sut/parse-www "example.com")))

  (is (= "http://example.com"
         (sut/parse-www "http://example.com")))

  (is (= "https://example.com"
         (sut/parse-www "https://example.com"))))

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
