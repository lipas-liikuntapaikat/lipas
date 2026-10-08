(ns lipas.prop-filters-test
  "Regression tests for the map search's property filters. The enum filters
   queried `properties.<prop>.keyword`, a sub-field the explicit mapping
   doesn't have, and silently returned zero sites (e.g. Pintamateriaali)."
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [lipas.backend.core :as core]
            [lipas.backend.search :as search]
            [lipas.data.prop-types :as prop-types]
            [lipas.prop-filters :as prop-filters]
            [lipas.test-utils :refer [<-json ->json] :as tu]
            [ring.mock.request :as mock]))

(defonce test-system (atom nil))

(let [{:keys [once each]} (tu/full-system-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn test-search [] (:lipas/search @test-system))
(defn test-app [req] ((:lipas/app @test-system) req))

(defn- sample-filter
  "A filter that restricts something, shaped like the UI builds it for a
   prop of `prop-def`'s data-type (lipas.ui.search.views/add-property-filter)."
  [prop-def]
  (case (:data-type prop-def)
    "numeric" {:type :range :min 1 :max 10}
    "boolean" {:type :boolean :value true}
    "string" {:type :string :text "x"}
    ("enum" "enum-coll") {:type :enum :values [(-> prop-def :opts keys first)]}))

(deftest every-filterable-prop-queries-mapped-fields-test
  ;; Every prop the UI offers as a filter (prop-types/active), for its
  ;; data-type's filter shape, must only touch fields the index has.
  (let [mapped (tu/mapped-fields (:sports-site search/mappings))]
    (doseq [[prop-key prop-def] prop-types/active
            prop-filter (cond-> [(sample-filter prop-def)]
                          (= "boolean" (:data-type prop-def))
                          (conj {:type :boolean :value false}))]
      (let [clause (prop-filters/->es-clause prop-key prop-filter)]
        (testing (str prop-key " " prop-filter)
          (is (some? clause))
          (is (empty? (set/difference (tu/query-fields clause) mapped))))))))

(deftest empty-filters-restrict-nothing-test
  (doseq [f [{:type :range}
             {:type :boolean :value nil}
             {:type :string :text ""}
             {:type :enum :values []}]]
    (is (nil? (prop-filters/->es-clause :surface-material f)) (pr-str f))))

;;; Against Elasticsearch ;;;

(defn- index-sites!
  [sites]
  (doseq [[lipas-id props] sites]
    (core/index! (test-search)
                 (tu/make-point-site lipas-id :type-code 1340 :properties props)
                 :sync)))

(defn- matching-ids
  "lipas-ids the search API returns for a single property filter."
  [prop-key prop-filter]
  (let [body {:size 50
              :_source ["lipas-id"]
              :query {:bool {:filter [(prop-filters/->es-clause prop-key prop-filter)]}}}
        resp (test-app (-> (mock/request :post "/api/actions/search")
                           (mock/content-type "application/json")
                           (mock/body (->json body))))]
    (->> resp :body <-json :hits :hits (map (comp :lipas-id :_source)) set)))

(deftest prop-filters-match-sites-test
  (index-sites! {980001 {:surface-material ["grass"]
                         :surface-material-info "Luonnonnurmi, kastelu"
                         :ligthing? true
                         :field-length-m 100.0
                         :water-point "year-round"}
                 980002 {:surface-material ["artificial-turf" "sand"]
                         :ligthing? false
                         :field-length-m 60.0
                         :water-point "seasonal"}
                 980003 {:surface-material ["rock-dust"]}})

  (testing "enum-coll matches any of the selected values"
    (is (= #{980001} (matching-ids :surface-material {:type :enum :values ["grass"]})))
    (is (= #{980001 980002}
           (matching-ids :surface-material {:type :enum :values ["grass" "sand"]}))))

  (testing "single-valued enum"
    (is (= #{980002} (matching-ids :water-point {:type :enum :values ["seasonal"]}))))

  (testing "string: case-insensitive contains"
    (is (= #{980001} (matching-ids :surface-material-info {:type :string :text "NURMI"}))))

  (testing "boolean: false also matches sites without the prop"
    (is (= #{980001} (matching-ids :ligthing? {:type :boolean :value true})))
    (is (= #{980002 980003} (matching-ids :ligthing? {:type :boolean :value false}))))

  (testing "range bounds are inclusive and may be open-ended"
    (is (= #{980001 980002} (matching-ids :field-length-m {:type :range :min 60 :max 100})))
    (is (= #{980001} (matching-ids :field-length-m {:type :range :min 61})))
    (is (= #{980002} (matching-ids :field-length-m {:type :range :max 60})))))
