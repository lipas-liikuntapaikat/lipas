(ns lipas.data.ptv-review-projection-test
  "The sync review against locations recorded from the PTV test env: what a
   sync sent, projected, equals the location PTV returns afterwards."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [lipas.data.ptv-review :as sut]))

(def ^:private fixtures
  ["ptv_review/synced_506495.edn"
   "ptv_review/synced_600093.edn"])

(defn- load-fixture [path]
  (edn/read-string (slurp (io/resource path))))

(deftest synced-location-projects-equal-test
  (doseq [path fixtures
          :let [{:keys [base channel]} (load-fixture path)
                langs (-> base :ptv :languages)]]
    (testing path
      (testing "what the sync sent and what PTV returns project the same"
        (is (= (sut/location-values (sut/site->location base langs))
               (sut/location-values channel))))

      (testing "the saved site, unchanged since, has nothing to review"
        (is (= [] (:rows (sut/sync-review base base channel {} langs)))))

      (testing "a summary edited in PTV is PTV's change"
        (let [edited (update channel :serviceChannelDescriptions
                             (fn [ds] (mapv #(if (and (= "Summary" (:type %)) (= "fi" (:language %)))
                                               (assoc % :value "Muokattu PTV:ssä")
                                               %)
                                            ds)))
              rows (:rows (sut/sync-review base base edited {} langs))]
          (is (= [[[:summary "fi"] :ptv]] (mapv (juxt :key :origin) rows))))))))
