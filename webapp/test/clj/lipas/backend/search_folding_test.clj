(ns lipas.backend.search-folding-test
  "Integration tests for diacritic-insensitive search and locale-correct name
   sorting. Exercises the real /api/actions/search path against Elasticsearch,
   so the test ES instance must have the analysis-icu plugin (collation sort)
   and a freshly created index carrying the folding analyzer + collation
   sub-fields from lipas.backend.search/mappings.

   NOTE: locally, run `(lipas.test-utils/reset-test-infra! search)` after pulling
   these mapping changes - prune-es! empties docs but does not re-map an existing
   index, so the new analyzer/collation only appears on a recreated index. CI
   always starts fresh."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lipas.backend.core :as core]
            [lipas.test-utils :refer [<-json ->json] :as tu]
            [ring.mock.request :as mock]))

(defonce test-system (atom nil))

(let [{:keys [once each]} (tu/full-system-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn test-search [] (:lipas/search @test-system))
(defn test-app [req] ((:lipas/app @test-system) req))

(defn- index-named-sites!
  "Indexes one active point site per name, returns the names indexed."
  [names]
  (doseq [[i name] (map-indexed vector names)]
    (core/index! (test-search)
                 (tu/make-point-site (+ 970000 i) :name name)
                 :sync))
  names)

(defn- search-names
  "Runs the production-shaped query (simple_query_string + trailing wildcard,
   analyze_wildcard) and returns the set of result names."
  [query]
  (let [body {:size 50
              :_source ["name"]
              :query {:simple_query_string
                      {:query (str query "*")
                       :fields ["name" "search-meta.name"]
                       :default_operator "AND"
                       :analyze_wildcard true}}}
        resp (test-app (-> (mock/request :post "/api/actions/search")
                           (mock/content-type "application/json")
                           (mock/body (->json body))))]
    (->> resp :body <-json :hits :hits (map (comp :name :_source)) set)))

(deftest diacritic-insensitive-search-test
  ;; Each [indexed-name ascii-query] pair: typing the accent-free query must
  ;; find the accented facility. Mirrors the bug report's examples.
  (let [cases [["Njálla-halli"  "Njalla"]      ; á -> a
               ["Deatŋu-halli"  "Deatngu"]     ; ŋ -> ng (Sami eng)
               ["Þorsteinn-talli" "Thorsteinn"] ; Þ -> th (Icelandic horse stable)
               ["Čáhci-areena"  "Cahci"]       ; č -> c, á -> a
               ["Iđut-kenttä"   "Idut"]]       ; đ -> d
        control "Helsinki Areena"]
    (index-named-sites! (conj (mapv first cases) control))

    (doseq [[indexed-name query] cases]
      (testing (str "'" query "' finds '" indexed-name "'")
        (is (contains? (search-names query) indexed-name))))

    (testing "symmetry: an accented query finds an accent-free name"
      (index-named-sites! ["Njalla-rinne"])
      (is (contains? (search-names "Njálla") "Njalla-rinne")))

    (testing "negative control: unrelated facility is not matched"
      (is (not (contains? (search-names "Njalla") control))))))

(defn- sorted-names
  "Returns result names sorted by the collation sort sub-field in `order`."
  [order]
  (let [body {:size 50
              :_source ["name"]
              :sort [{:search-meta.name.sort {:order order}}]
              :query {:simple_query_string {:query "Lajittelu*"
                                            :fields ["name"]
                                            :analyze_wildcard true}}}
        resp (test-app (-> (mock/request :post "/api/actions/search")
                           (mock/content-type "application/json")
                           (mock/body (->json body))))]
    (->> resp :body <-json :hits :hits (mapv (comp :name :_source)))))

(deftest locale-correct-name-sort-test
  ;; Finnish collation orders å, ä, ö after z (and å before ä). Raw code-point
  ;; order gets å/ä wrong: ä (U+00E4) sorts before å (U+00E5). This asserts the
  ;; icu_collation_keyword sort sub-field produces Finnish order. All names share
  ;; the "Lajittelu" prefix purely to isolate them from any other indexed sites.
  (index-named-sites! ["Lajittelu Aho"
                       "Lajittelu Åke"
                       "Lajittelu Äänekoski"
                       "Lajittelu Östersundom"])
  (let [expected ["Lajittelu Aho"        ; a
                  "Lajittelu Åke"         ; å  (before ä - the case raw order breaks)
                  "Lajittelu Äänekoski"   ; ä
                  "Lajittelu Östersundom"]] ; ö
    (testing "ascending is Finnish collation order"
      (is (= expected (sorted-names "asc"))))
    (testing "descending is the reverse"
      (is (= (reverse expected) (sorted-names "desc"))))))
