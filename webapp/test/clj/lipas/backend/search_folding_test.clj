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
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
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

(defn- sorted-values
  "Runs the production-shaped table sort (sort sub-field via the API) over the
   'Kirjainkoko' test sites and returns `source-path` of each hit in order."
  [sort-field order source-path]
  (let [body {:size 50
              :_source [(str/join "." (map name source-path))]
              :sort [{sort-field {:order order}}]
              :query {:simple_query_string {:query "Kirjainkoko*"
                                            :fields ["name"]
                                            :analyze_wildcard true}}}
        resp (test-app (-> (mock/request :post "/api/actions/search")
                           (mock/content-type "application/json")
                           (mock/body (->json body))))]
    (is (= 200 (:status resp)) (str "sorting by " sort-field " must not error"))
    (->> resp :body <-json :hits :hits
         (mapv #(get-in (:_source %) source-path)))))

(deftest free-text-field-sort-test
  ;; postal-office & co are user-entered free text with wildly mixed case.
  ;; Raw keyword sort is code-point order, which puts every lowercase value
  ;; after the entire uppercase alphabet (HELSINKI < VANTAA < espoo). The
  ;; icu_collation_keyword `sort` sub-field orders case-insensitively (case
  ;; only breaks ties, lowercase first). www/email/phone-number additionally
  ;; used to be unindexed ({:enabled false}), so sorting them was an ES 400.
  (doseq [[i [po www]] (map-indexed vector
                                    [["VANTAA"   "www.d.fi"]
                                     ["espoo"    "WWW.B.FI"]
                                     ["Ähtäri"   "www.e.fi"]
                                     ["HELSINKI" "www.a.fi"]
                                     ["helsinki" "www.C.fi"]])]
    (core/index! (test-search)
                 (-> (tu/make-point-site (+ 980000 i) :name (str "Kirjainkoko " i))
                     (assoc-in [:location :postal-office] po)
                     (assoc :www www
                            :email "info@example.fi"
                            :phone-number "+358 40 123 4567"))
                 :sync))

  (testing "postal-office sorts case-insensitively in Finnish collation order"
    (let [expected ["espoo" "helsinki" "HELSINKI" "VANTAA" "Ähtäri"]]
      (is (= expected (sorted-values :location.postal-office.sort "asc"
                                     [:location :postal-office])))
      (is (= (reverse expected) (sorted-values :location.postal-office.sort "desc"
                                               [:location :postal-office])))))

  (testing "www sorts case-insensitively"
    (is (= ["www.a.fi" "WWW.B.FI" "www.C.fi" "www.d.fi" "www.e.fi"]
           (sorted-values :www.sort "asc" [:www]))))

  (testing "email and phone-number sort without erroring"
    (is (= 5 (count (sorted-values :email.sort "asc" [:email]))))
    (is (= 5 (count (sorted-values :phone-number.sort "asc" [:phone-number]))))))

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
