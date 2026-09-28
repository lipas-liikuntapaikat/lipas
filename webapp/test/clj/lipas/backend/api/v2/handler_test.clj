(ns lipas.backend.api.v2.handler-test
  "Integration tests for the V2 API.

   These tests verify that:
   1. Each endpoint returns correct HTTP status codes
   2. Response structure matches the V2 API contract
   3. Query parameters work as expected (filtering, pagination, etc.)"
  (:require
    [clojure.set :as set]
    [clojure.test :refer [deftest is testing use-fixtures]]
    [lipas.backend.api.v2 :as v2]
    [lipas.backend.core :as core]
    [lipas.backend.search :as search]
    [lipas.test-utils :as test-utils]
    [ring.mock.request :as mock]))

;;; Test system setup ;;;

(defonce test-system (atom nil))

;;; Helper Functions ;;;

(defn test-app []
  (:lipas/app @test-system))

(defn test-db []
  (:lipas/db @test-system))

(defn test-search []
  (:lipas/search @test-system))

;;; Fixtures ;;;

(let [{:keys [once each]} (test-utils/full-system-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

;;; Test Data Helpers ;;;

(defn create-admin-user []
  (test-utils/gen-admin-user :db-component (test-db)))

(defn- ensure-int-city-code
  "Converts city-code to integer for v2 schema compliance."
  [site]
  (let [cc (get-in site [:location :city :city-code])]
    (cond-> site
      (string? cc) (assoc-in [:location :city :city-code] (Integer/parseInt cc)))))

(defn create-sports-site!
  "Creates a sports site in the database and indexes it to the main ES index.
   Converts city-code to integer for v2 response schema compliance."
  [site]
  (let [admin (create-admin-user)
        site (ensure-int-city-code site)]
    (core/upsert-sports-site!* (test-db) admin site)
    (core/index! (test-search) site :sync)
    site))

(def polygon-loi-types #{"nature-reserve" "other-area-with-movement-restrictions"})

(defn- fix-loi-geometry
  "Ensures LOI geometry matches the required geometry type for its loi-type."
  [loi]
  (if (polygon-loi-types (:loi-type loi))
    (assoc loi :geometries {:type "FeatureCollection"
                            :features [{:type "Feature"
                                        :geometry {:type "Polygon"
                                                   :coordinates [[[25.0 60.2]
                                                                  [25.01 60.2]
                                                                  [25.01 60.21]
                                                                  [25.0 60.21]
                                                                  [25.0 60.2]]]}}]})
    loi))

(defn create-loi!
  "Creates a LOI and indexes it to ES."
  [loi]
  (let [admin (create-admin-user)
        loi (fix-loi-geometry loi)]
    (core/upsert-loi! (test-db) (test-search) admin loi)
    loi))

;;; Response parsing helpers ;;;

(defn parse-json-body [response]
  (let [body (test-utils/<-json (:body response))]
    (cond
      (sequential? body) (vec body)
      :else body)))

;;; Tests for GET /v2/sports-site-categories ;;;

(deftest get-all-categories-test
  (testing "GET /v2/sports-site-categories"

    (testing "returns 200 with categories"
      (let [resp ((test-app) (mock/request :get "/v2/sports-site-categories"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (vector? body))
        (is (pos? (count body)))
        (doseq [cat body]
          (is (integer? (:type-code cat)))
          (is (map? (:name cat)))
          (is (string? (:fi (:name cat)))))))))

(deftest get-category-by-type-code-test
  (testing "GET /v2/sports-site-categories/:type-code"

    (testing "returns 200 for valid type code"
      (let [resp ((test-app) (mock/request :get "/v2/sports-site-categories/1120"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (= 1120 (:type-code body)))
        (is (map? (:name body)))))

    (testing "returns error for invalid type code"
      (let [resp ((test-app) (mock/request :get "/v2/sports-site-categories/9999999"))]
        (is (#{400 404} (:status resp)))))))

;;; Tests for GET /v2/sports-sites (list) ;;;

(deftest list-sports-sites-empty-test
  (testing "GET /v2/sports-sites returns 200 with empty items"
    (let [resp ((test-app) (mock/request :get "/v2/sports-sites"))
          body (parse-json-body resp)]
      (is (= 200 (:status resp)))
      (is (map? body))
      (is (vector? (:items body)))
      (is (empty? (:items body)))
      (is (map? (:pagination body)))
      (is (= 0 (-> body :pagination :total-items))))))

(deftest list-sports-sites-with-results-test
  (testing "GET /v2/sports-sites returns items with pagination"
    (doseq [i (range 1 4)]
      (let [base (test-utils/make-point-site (+ 20000 i) :name (str "V2 Test Site " i))
            ;; Attach images only to the first site so we cover both branches.
            site (cond-> base
                   (= i 1) (assoc :images [{:url "https://loimaa.fi/img/list.jpg"
                                            :alt-text {:fi "Alt"}
                                            :copyright {:fi "© Loimaa"}}]))]
        (create-sports-site! site)))

    (let [resp ((test-app) (mock/request :get "/v2/sports-sites"))
          body (parse-json-body resp)]
      (is (= 200 (:status resp)))
      (is (= 3 (count (:items body))))
      (is (= 3 (-> body :pagination :total-items)))
      (is (= 1 (-> body :pagination :current-page)))

      (testing "items have expected v2 structure"
        (doseq [item (:items body)]
          (is (integer? (:lipas-id item)))
          (is (string? (:name item)))
          (is (string? (:status item)))
          (is (map? (:type item)))
          (is (integer? (-> item :type :type-code)))
          (is (map? (:location item)))))

      (testing ":images surface in list items when set"
        (let [with-images (first (filter #(= 20001 (:lipas-id %)) (:items body)))]
          (is (= [{:url "https://loimaa.fi/img/list.jpg"
                   :alt-text {:fi "Alt"}
                   :copyright {:fi "© Loimaa"}}]
                 (:images with-images))))))))

(deftest list-sports-sites-pagination-test
  (testing "GET /v2/sports-sites pagination"
    (doseq [i (range 1 16)]
      (create-sports-site! (test-utils/make-point-site (+ 30000 i) :name (str "Pagination Site " i))))

    (testing "default page size returns up to 10"
      (let [resp ((test-app) (mock/request :get "/v2/sports-sites"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (<= (count (:items body)) 10))
        (is (= 15 (-> body :pagination :total-items)))
        (is (= 2 (-> body :pagination :total-pages)))))

    (testing "custom page-size"
      (let [resp ((test-app) (mock/request :get "/v2/sports-sites?page-size=5"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (= 5 (count (:items body))))
        (is (= 3 (-> body :pagination :total-pages)))))

    (testing "page parameter returns different items"
      (let [resp1 ((test-app) (mock/request :get "/v2/sports-sites?page-size=5&page=1"))
            resp2 ((test-app) (mock/request :get "/v2/sports-sites?page-size=5&page=2"))
            body1 (parse-json-body resp1)
            body2 (parse-json-body resp2)]
        (is (= 200 (:status resp1)))
        (is (= 200 (:status resp2)))
        (let [ids1 (set (map :lipas-id (:items body1)))
              ids2 (set (map :lipas-id (:items body2)))]
          (is (empty? (set/intersection ids1 ids2))
              "Page 1 and 2 should have different items"))))))

(deftest list-sports-sites-filter-by-type-codes-test
  (testing "GET /v2/sports-sites filter by type-codes"
    (create-sports-site! (test-utils/make-point-site 40001 :name "Type 1120 Site" :type-code 1120))
    (create-sports-site! (test-utils/make-point-site 40002 :name "Type 1310 Site" :type-code 1310))

    (testing "single type-code filter"
      (let [resp ((test-app) (mock/request :get "/v2/sports-sites?type-codes=1120"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (every? #(= 1120 (-> % :type :type-code)) (:items body)))))

    (testing "multiple type-codes filter (comma-separated)"
      (let [resp ((test-app) (mock/request :get "/v2/sports-sites?type-codes=1120,1310"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (= 2 (count (:items body))))
        (is (= #{1120 1310} (set (map #(-> % :type :type-code) (:items body)))))))

    (testing "multiple type-codes filter (repeated params)"
      (let [resp ((test-app) (mock/request :get "/v2/sports-sites?type-codes=1120&type-codes=1310"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (= 2 (count (:items body))))))))

(deftest list-sports-sites-filter-by-city-codes-test
  (testing "GET /v2/sports-sites filter by city-codes"
    (create-sports-site! (test-utils/make-point-site 50001 :name "Helsinki Site" :city-code "91"))
    (create-sports-site! (test-utils/make-point-site 50002 :name "Espoo Site" :city-code "49"))

    (testing "single city-code filter"
      (let [resp ((test-app) (mock/request :get "/v2/sports-sites?city-codes=91"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (= 1 (count (:items body))))
        (is (= "91" (-> body :items first :location :city :city-code str)))))

    (testing "multiple city-codes filter"
      (let [resp ((test-app) (mock/request :get "/v2/sports-sites?city-codes=91,49"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (= 2 (count (:items body))))))))

(deftest list-sports-sites-filter-by-statuses-test
  (testing "GET /v2/sports-sites filter by statuses"
    (create-sports-site! (test-utils/make-point-site 51001 :name "Active Site" :status "active"))
    (create-sports-site! (test-utils/make-point-site 51002 :name "Temp OOS Site" :status "out-of-service-temporarily"))

    (testing "filter active only"
      (let [resp ((test-app) (mock/request :get "/v2/sports-sites?statuses=active"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (every? #(= "active" (:status %)) (:items body)))))

    (testing "filter multiple statuses"
      (let [resp ((test-app) (mock/request :get "/v2/sports-sites?statuses=active,out-of-service-temporarily"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (= 2 (count (:items body))))))))

;;; Tests for GET /v2/sports-sites/:lipas-id ;;;

(deftest get-single-sports-site-test
  (testing "GET /v2/sports-sites/:lipas-id"

    (testing "returns 200 for existing sports site"
      (let [site (test-utils/make-point-site 12345 :name "Test Lähiliikuntapaikka")
            _ (create-sports-site! site)
            resp ((test-app) (mock/request :get "/v2/sports-sites/12345"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (= 12345 (:lipas-id body)))
        (is (string? (:name body)))
        (is (map? (:type body)))
        (is (= 1120 (-> body :type :type-code)))
        (is (map? (:location body)))))

    (testing "returns correct v2 field format"
      (let [images [{:url "https://loimaa.fi/img/a.jpg"
                     :alt-text {:fi "Kenttä" :se "Plan" :en "Field"}
                     :copyright {:fi "© 2026 Loimaa CC BY 4.0"}
                     :description {:fi "Pääkenttä"}}
                    {:url "https://loimaa.fi/img/b.jpg"
                     :alt-text {:fi "Katsomo"}
                     :copyright {:fi "© Loimaa"}}]
            site (-> (test-utils/make-point-site 12346
                                                 :name "Test Football Field"
                                                 :type-code 1310)
                     (assoc :construction-year 2010
                            :event-date "2024-06-15T10:30:00.000Z"
                            :images images))
            _ (create-sports-site! site)
            resp ((test-app) (mock/request :get "/v2/sports-sites/12346"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (integer? (:lipas-id body)))
        (is (= 12346 (:lipas-id body)))
        (is (string? (:name body)))
        (is (integer? (-> body :type :type-code)))
        (is (string? (:event-date body)))
        (is (= 2010 (:construction-year body)))
        (is (map? (-> body :location :city)))

        (testing ":images round-trips all metadata fields"
          (is (vector? (:images body)))
          (is (= 2 (count (:images body))))
          (let [img (-> body :images first)]
            (is (= "https://loimaa.fi/img/a.jpg" (:url img)))
            (is (= "Kenttä" (-> img :alt-text :fi)))
            (is (= "Field" (-> img :alt-text :en)))
            (is (= "© 2026 Loimaa CC BY 4.0" (-> img :copyright :fi)))
            (is (= "Pääkenttä" (-> img :description :fi))))
          (testing "optional :description may be omitted"
            (let [img (-> body :images second)]
              (is (= "https://loimaa.fi/img/b.jpg" (:url img)))
              (is (nil? (:description img))))))))))

;;; Tests for different geometry types in v2 ;;;

(deftest geometry-types-test
  (testing "Different geometry types are correctly handled in v2"

    (testing "Point geometry site"
      (let [site (test-utils/make-point-site 80001 :name "Point Site" :type-code 1120)
            _ (create-sports-site! site)
            resp ((test-app) (mock/request :get "/v2/sports-sites/80001"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (= "Point" (-> body :location :geometries :features first :geometry :type)))))

    (testing "LineString geometry site"
      (let [site (test-utils/make-route-site 80002 :name "Route Site" :type-code 4405)
            _ (create-sports-site! site)
            resp ((test-app) (mock/request :get "/v2/sports-sites/80002"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (= "LineString" (-> body :location :geometries :features first :geometry :type)))))

    (testing "Polygon geometry site"
      (let [site (test-utils/make-area-site 80003 :name "Area Site" :type-code 103)
            _ (create-sports-site! site)
            resp ((test-app) (mock/request :get "/v2/sports-sites/80003"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (= "Polygon" (-> body :location :geometries :features first :geometry :type)))))))

;;; Tests for GET /v2/lois (list) ;;;

(deftest list-lois-empty-test
  (testing "GET /v2/lois returns 200 with empty items"
    (let [resp ((test-app) (mock/request :get "/v2/lois"))
          body (parse-json-body resp)]
      (is (= 200 (:status resp)))
      (is (map? body))
      (is (vector? (:items body)))
      (is (empty? (:items body)))
      (is (map? (:pagination body))))))

(deftest list-lois-with-results-test
  (testing "GET /v2/lois returns items with pagination"
    (doseq [_ (range 3)]
      (create-loi! (test-utils/gen-loi!)))

    (let [resp ((test-app) (mock/request :get "/v2/lois"))
          body (parse-json-body resp)]
      (is (= 200 (:status resp)))
      (is (= 3 (count (:items body))))
      (is (= 3 (-> body :pagination :total-items)))
      (is (= 1 (-> body :pagination :current-page))))))

(deftest list-lois-pagination-test
  (testing "GET /v2/lois pagination"
    (doseq [_ (range 15)]
      (create-loi! (test-utils/gen-loi!)))

    (testing "default page size returns up to 10"
      (let [resp ((test-app) (mock/request :get "/v2/lois"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (<= (count (:items body)) 10))
        (is (= 15 (-> body :pagination :total-items)))))

    (testing "custom page-size"
      (let [resp ((test-app) (mock/request :get "/v2/lois?page-size=5"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (= 5 (count (:items body))))))))

;;; Tests for GET /v2/lois/:loi-id ;;;

(deftest get-single-loi-test
  (testing "GET /v2/lois/:loi-id"

    (testing "returns 200 for existing LOI"
      (let [loi (test-utils/gen-loi!)
            _ (create-loi! loi)
            resp ((test-app) (mock/request :get (str "/v2/lois/" (:id loi))))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (= (:id loi) (:id body)))
        (is (string? (:status body)))))))

;;; Tests for pagination structure ;;;

(deftest pagination-structure-test
  (testing "Pagination response has correct structure"
    (doseq [i (range 1 26)]
      (create-sports-site! (test-utils/make-point-site (+ 60000 i) :name (str "Pagination Structure " i))))

    (let [resp ((test-app) (mock/request :get "/v2/sports-sites?page=2&page-size=10"))
          body (parse-json-body resp)
          pagination (:pagination body)]
      (is (= 200 (:status resp)))
      (is (= 2 (:current-page pagination)))
      (is (= 10 (:page-size pagination)))
      (is (= 25 (:total-items pagination)))
      (is (= 3 (:total-pages pagination))))))

;;; Tests for invalid parameters ;;;

(deftest invalid-parameters-test
  (testing "Invalid page parameter returns 400"
    (let [resp ((test-app) (mock/request :get "/v2/sports-sites?page=0"))]
      (is (= 400 (:status resp)))))

  (testing "Invalid page-size parameter returns 400"
    (let [resp ((test-app) (mock/request :get "/v2/sports-sites?page-size=0"))]
      (is (= 400 (:status resp))))

    (let [resp ((test-app) (mock/request :get "/v2/sports-sites?page-size=101"))]
      (is (= 400 (:status resp))))))

;;; Tests for deep pagination (index.max_result_window) ;;;

;; Elasticsearch rejects `from + size > index.max_result_window`, which used to
;; reach the client as a bare 500: /v2/sports-sites?page-size=100&page=601 (the
;; first page over the 60000 window) while page 600 answered 200. That rejection
;; depends only on `from + size`, never on how many documents the index holds,
;; so the real boundary is reachable in a test with a handful of documents — no
;; window rebinding and no 60000-document seed needed.

(deftest sports-sites-query-window-test
  (testing "->sports-sites-query"

    (testing "pages inside the window fetch normally"
      (let [q (v2/->sports-sites-query {:page 600 :page-size 100})]
        (is (= 59900 (:from q)))
        (is (= 100 (:size q)))))

    (testing "the page at the exact window boundary still fetches"
      (let [window (:sports-site search/max-result-window)
            q (v2/->sports-sites-query {:page (/ window 100) :page-size 100})]
        (is (= (- window 100) (:from q)))
        (is (= 100 (:size q)))
        (is (= window (+ (:from q) (:size q))))))

    (testing "the first page past the window becomes a count-only query"
      (let [q (v2/->sports-sites-query {:page 601 :page-size 100})]
        (is (= 0 (:from q)))
        (is (= 0 (:size q)))
        (is (= (:sports-site search/max-result-window) (:track_total_hits q))
            "totals must still be tracked so total-items stays correct")))

    (testing "an absurd page becomes a count-only query"
      (let [q (v2/->sports-sites-query {:page 100000 :page-size 100})]
        (is (= 0 (:from q)))
        (is (= 0 (:size q)))))

    (testing "filters survive into the count-only query"
      (let [q (v2/->sports-sites-query {:page 100000 :page-size 100 :type-codes [1120]})]
        (is (= 0 (:size q)))
        (is (= [{:terms {:type.type-code [1120]}}]
               (-> q :query :bool :filter)))))))

(deftest lois-query-window-test
  (testing "->lois-query uses the lois index window, not the sports-sites one"
    (let [window (:lois search/max-result-window)]
      (is (= 50000 window))

      (testing "the page at the exact boundary still fetches"
        (let [q (v2/->lois-query {:page (/ window 100) :page-size 100})]
          (is (= (- window 100) (:from q)))
          (is (= 100 (:size q)))))

      (testing "the first page past the window becomes a count-only query"
        (let [q (v2/->lois-query {:page (inc (/ window 100)) :page-size 100})]
          (is (= 0 (:from q)))
          (is (= 0 (:size q)))
          (is (= window (:track_total_hits q)))))

      (testing "filters survive into the count-only query"
        (let [q (v2/->lois-query {:page 100000 :page-size 100 :types ["fishing-spot"]})]
          (is (= 0 (:size q)))
          (is (= [{:terms {:loi-type.keyword ["fishing-spot"]}}]
                 (-> q :query :bool :must))))))))

(deftest list-sports-sites-beyond-window-test
  (testing "GET /v2/sports-sites past index.max_result_window"
    (doseq [i (range 1 16)]
      (create-sports-site! (test-utils/make-point-site (+ 70000 i)
                                                       :name (str "Window Site " i)
                                                       :type-code (if (odd? i) 1120 1310))))

    (testing "a page well inside the window returns items"
      (let [resp ((test-app) (mock/request :get "/v2/sports-sites?page-size=5&page=3"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (= 5 (count (:items body))))
        (is (= 15 (-> body :pagination :total-items)))))

    (testing "the page at the exact window boundary returns 200"
      ;; from 59900 + size 100 = 60000 = the window: the deepest page ES
      ;; accepts, and it does get sent to ES.
      (let [resp ((test-app) (mock/request :get "/v2/sports-sites?page-size=100&page=600"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (empty? (:items body)))
        (is (= 15 (-> body :pagination :total-items)))))

    (testing "the first page past the window returns 200 with empty items"
      ;; The regression. from 60000 + size 100 > the window, so ES rejects the
      ;; query outright — independently of how many documents the index holds,
      ;; which is why 15 of them are enough to reproduce the production 500.
      (let [resp ((test-app) (mock/request :get "/v2/sports-sites?page-size=100&page=601"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (empty? (:items body)))
        (is (= {:current-page 601
                :page-size 100
                :total-items 15
                :total-pages 1}
               (:pagination body)))))

    (testing "an absurd page returns 200 with empty items"
      (let [resp ((test-app) (mock/request :get "/v2/sports-sites?page-size=100&page=100000"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (empty? (:items body)))
        (is (= 100000 (-> body :pagination :current-page)))
        (is (= 15 (-> body :pagination :total-items)))))

    (testing "an out-of-range page with a filter reports the filtered total"
      (let [resp ((test-app) (mock/request :get "/v2/sports-sites?page-size=100&page=601&type-codes=1310"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (empty? (:items body)))
        (is (= 7 (-> body :pagination :total-items))
            "7 of the 15 sites have type-code 1310, not all 15")))))

(deftest list-lois-beyond-window-test
  (testing "GET /v2/lois past index.max_result_window"
    (doseq [_ (range 12)]
      (create-loi! (test-utils/gen-loi!)))

    (testing "a page inside the window returns items"
      (let [resp ((test-app) (mock/request :get "/v2/lois?page-size=5&page=2"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (= 5 (count (:items body))))
        (is (= 12 (-> body :pagination :total-items)))))

    (testing "the page at the exact window boundary returns 200"
      ;; The lois index has its own, smaller window: from 49900 + size 100.
      (let [resp ((test-app) (mock/request :get "/v2/lois?page-size=100&page=500"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (empty? (:items body)))
        (is (= 12 (-> body :pagination :total-items)))))

    (testing "the first page past the window returns 200 with empty items"
      (let [resp ((test-app) (mock/request :get "/v2/lois?page-size=100&page=501"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (empty? (:items body)))
        (is (= {:current-page 501
                :page-size 100
                :total-items 12
                :total-pages 1}
               (:pagination body)))))

    (testing "an absurd page returns 200 with empty items"
      (let [resp ((test-app) (mock/request :get "/v2/lois?page-size=100&page=100000"))
            body (parse-json-body resp)]
        (is (= 200 (:status resp)))
        (is (empty? (:items body)))
        (is (= 12 (-> body :pagination :total-items)))))))

;;; Health check ;;;

(deftest health-check-test
  (testing "GET /v2 returns 200 health status"
    (let [resp ((test-app) (mock/request :get "/v2"))
          body (parse-json-body resp)]
      (is (= 200 (:status resp)))
      (is (= "healthy" (:status body))))))

(comment
  (clojure.test/run-tests 'lipas.backend.api.v2.handler-test)

  (clojure.test/run-test-var #'get-all-categories-test)
  (clojure.test/run-test-var #'get-category-by-type-code-test)
  (clojure.test/run-test-var #'list-sports-sites-empty-test)
  (clojure.test/run-test-var #'list-sports-sites-with-results-test)
  (clojure.test/run-test-var #'list-sports-sites-pagination-test)
  (clojure.test/run-test-var #'list-sports-sites-filter-by-type-codes-test)
  (clojure.test/run-test-var #'list-sports-sites-filter-by-city-codes-test)
  (clojure.test/run-test-var #'list-sports-sites-filter-by-statuses-test)
  (clojure.test/run-test-var #'get-single-sports-site-test)
  (clojure.test/run-test-var #'geometry-types-test)
  (clojure.test/run-test-var #'list-lois-empty-test)
  (clojure.test/run-test-var #'list-lois-with-results-test)
  (clojure.test/run-test-var #'list-lois-pagination-test)
  (clojure.test/run-test-var #'get-single-loi-test)
  (clojure.test/run-test-var #'pagination-structure-test)
  (clojure.test/run-test-var #'invalid-parameters-test)
  (clojure.test/run-test-var #'sports-sites-query-window-test)
  (clojure.test/run-test-var #'lois-query-window-test)
  (clojure.test/run-test-var #'list-sports-sites-beyond-window-test)
  (clojure.test/run-test-var #'list-lois-beyond-window-test)
  (clojure.test/run-test-var #'health-check-test))
