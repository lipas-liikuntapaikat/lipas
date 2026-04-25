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
