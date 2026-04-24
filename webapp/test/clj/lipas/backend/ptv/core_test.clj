(ns lipas.backend.ptv.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [lipas.backend.ptv.core :as sut]))

(def ^:private finalize @#'sut/finalize-service-channel-payload)

(defn- names [payload]
  (->> payload :serviceChannelNames
       (mapv (juxt :type :language :value))
       (sort-by (fn [[_ l _]] l))
       vec))

(defn- descs [payload]
  (->> payload :serviceChannelDescriptions
       (mapv (juxt :type :language :value))
       (sort-by (fn [[t l _]] [t l]))
       vec))

(def ^:private lipas-payload
  {:organizationId "org-x"
   :publishingStatus "Published"
   :languages #{"fi" "sv" "en"}
   :serviceChannelNames [{:type "Name" :language "fi" :value "LIPAS-fi"}]
   :serviceChannelDescriptions [{:type "Summary" :language "fi" :value "summary-fi"}
                                {:type "Description" :language "fi" :value "desc-fi"}]})

(def ^:private stored-channel
  {:serviceChannelNames [{:type "Name" :language "fi" :value "PTV-stored-fi"}
                         {:type "Name" :language "sv" :value "KUNTA-sv"}
                         {:type "Name" :language "en" :value "PTV-stored-en"}]
   :serviceChannelDescriptions [{:type "Summary" :language "fi" :value "ptv-summary-fi"}
                                {:type "Summary" :language "sv" :value "ptv-summary-sv"}
                                {:type "Summary" :language "en" :value "ptv-summary-en"}
                                {:type "Description" :language "fi" :value "ptv-desc-fi"}
                                {:type "Description" :language "sv" :value "ptv-desc-sv"}
                                {:type "Description" :language "en" :value "ptv-desc-en"}]})

(deftest finalize-update-merges-with-stored
  (testing "LIPAS fi overwrites stored fi; stored sv/en preserved (kunta edits survive)"
    (let [out (finalize lipas-payload stored-channel)]
      (is (= [["Name" "en" "PTV-stored-en"]
              ["Name" "fi" "LIPAS-fi"]
              ["Name" "sv" "KUNTA-sv"]]
             (names out)))))

  (testing "displayNameType covers every declared language"
    (let [out (finalize lipas-payload stored-channel)]
      (is (= #{{:type "Name" :language "fi"}
               {:type "Name" :language "sv"}
               {:type "Name" :language "en"}}
             (set (:displayNameType out))))))

  (testing "descriptions: LIPAS fi overwrites; stored sv/en preserved"
    (let [out (finalize lipas-payload stored-channel)]
      (is (= [["Description" "en" "ptv-desc-en"]
              ["Description" "fi" "desc-fi"]
              ["Description" "sv" "ptv-desc-sv"]
              ["Summary" "en" "ptv-summary-en"]
              ["Summary" "fi" "summary-fi"]
              ["Summary" "sv" "ptv-summary-sv"]]
             (descs out)))))

  (testing "LIPAS-entered sv wins over kunta-stored sv (non-blank = LIPAS authoritative)"
    (let [p (update lipas-payload :serviceChannelNames conj
                    {:type "Name" :language "sv" :value "LIPAS-sv"})
          out (finalize p stored-channel)]
      (is (some #(and (= "sv" (:language %)) (= "LIPAS-sv" (:value %)))
                (:serviceChannelNames out))))))

(deftest finalize-update-blank-does-not-overwrite
  (testing "a blank LIPAS value does not clobber a non-blank stored value"
    (let [p (update lipas-payload :serviceChannelNames conj
                    {:type "Name" :language "sv" :value "   "})
          out (finalize p stored-channel)]
      (is (some #(and (= "sv" (:language %)) (= "KUNTA-sv" (:value %)))
                (:serviceChannelNames out))))))

(deftest finalize-create-fills-missing-with-fi-fallback
  (testing "no stored channel → missing languages filled from Finnish"
    (let [out (finalize lipas-payload nil)]
      (is (= [["Name" "en" "LIPAS-fi"]
              ["Name" "fi" "LIPAS-fi"]
              ["Name" "sv" "LIPAS-fi"]]
             (names out)))
      (is (= #{"fi" "sv" "en"}
             (->> out :serviceChannelDescriptions
                  (filter #(= "Summary" (:type %)))
                  (map :language) set)))
      (is (= #{"fi" "sv" "en"}
             (->> out :serviceChannelDescriptions
                  (filter #(= "Description" (:type %)))
                  (map :language) set))))))

(deftest finalize-update-fills-new-org-language
  (testing "org adds a language not covered by stored OR LIPAS → fi fallback fills the gap"
    (let [stored-fi-sv (update stored-channel :serviceChannelNames
                               #(filterv (comp #{"fi" "sv"} :language) %))
          out (finalize lipas-payload stored-fi-sv)
          en-name (some #(when (= "en" (:language %)) (:value %)) (:serviceChannelNames out))]
      (is (= "LIPAS-fi" en-name)))))
