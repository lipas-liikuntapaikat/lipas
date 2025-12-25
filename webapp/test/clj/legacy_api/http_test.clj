(ns legacy-api.http-test
  "Unit tests for legacy-api.http namespace.

   These tests verify:
   1. Pagination link generation
   2. Dynamic base path support for different entry points"
  (:require
   [clojure.test :refer [deftest is testing]]
   [legacy-api.http :as http]))

;;; Tests for create-page-links ;;;

(deftest create-page-links-test
  (testing "create-page-links generates correct pagination links"

    (testing "with /v1 prefix (api.lipas.fi)"
      (let [links (http/create-page-links "/v1/sports-places"
                                          {:pageSize 10}
                                          1 10 100)]
        (is (= "/v1/sports-places/?pageSize=10&page=1" (:first links)))
        (is (= "/v1/sports-places/?pageSize=10&page=2" (:next links)))
        (is (= "/v1/sports-places/?pageSize=10&page=1" (:prev links)))
        (is (= "/v1/sports-places/?pageSize=10&page=10" (:last links)))
        (is (= 100 (:total links)))))

    (testing "with /rest/api prefix (lipas.fi)"
      (let [links (http/create-page-links "/rest/api/sports-places"
                                          {:pageSize 10}
                                          1 10 100)]
        (is (= "/rest/api/sports-places/?pageSize=10&page=1" (:first links)))
        (is (= "/rest/api/sports-places/?pageSize=10&page=2" (:next links)))
        (is (= "/rest/api/sports-places/?pageSize=10&page=1" (:prev links)))
        (is (= "/rest/api/sports-places/?pageSize=10&page=10" (:last links)))))

    (testing "with /api prefix (lipas.cc.jyu.fi)"
      (let [links (http/create-page-links "/api/sports-places"
                                          {:pageSize 10}
                                          1 10 100)]
        (is (= "/api/sports-places/?pageSize=10&page=1" (:first links)))
        (is (= "/api/sports-places/?pageSize=10&page=2" (:next links)))))

    (testing "preserves query parameters"
      (let [links (http/create-page-links "/v1/sports-places"
                                          {:pageSize 10 :typeCodes 1120 :cityCodes 91}
                                          1 10 100)]
        (is (re-find #"typeCodes=1120" (:first links)))
        (is (re-find #"cityCodes=91" (:first links)))
        (is (re-find #"page=1" (:first links)))))

    (testing "calculates last page correctly"
      ;; 25 results with page size 10 = 3 pages
      (let [links (http/create-page-links "/v1/sports-places" {} 1 10 25)]
        (is (= 3 (http/last-page 25 10)))
        (is (re-find #"page=3" (:last links))))

      ;; 30 results with page size 10 = 3 pages
      (let [links (http/create-page-links "/v1/sports-places" {} 1 10 30)]
        (is (= 3 (http/last-page 30 10)))
        (is (re-find #"page=3" (:last links))))

      ;; 5 results with page size 10 = 1 page
      (let [links (http/create-page-links "/v1/sports-places" {} 1 10 5)]
        (is (= 1 (http/last-page 5 10)))
        (is (re-find #"page=1" (:last links)))))))

;;; Tests for extract-base-path ;;;

(deftest extract-base-path-test
  (testing "extract-base-path returns correct base path from request"

    (testing "uses X-Forwarded-Prefix header when present"
      (is (= "/v1"
             (http/extract-base-path {:headers {"x-forwarded-prefix" "/v1"}})))
      (is (= "/rest/api"
             (http/extract-base-path {:headers {"x-forwarded-prefix" "/rest/api"}})))
      (is (= "/api"
             (http/extract-base-path {:headers {"x-forwarded-prefix" "/api"}}))))

    (testing "falls back to extracting from request URI when no header"
      ;; Request URI: /v1/sports-places -> base path: /v1
      (is (= "/v1"
             (http/extract-base-path {:uri "/v1/sports-places"
                                      :headers {}})))
      ;; Request URI: /v1/sports-places/123 -> base path: /v1
      (is (= "/v1"
             (http/extract-base-path {:uri "/v1/sports-places/123"
                                      :headers {}}))))

    (testing "handles case-insensitive header names"
      (is (= "/v1"
             (http/extract-base-path {:headers {"X-Forwarded-Prefix" "/v1"}})))
      (is (= "/v1"
             (http/extract-base-path {:headers {"X-FORWARDED-PREFIX" "/v1"}}))))))

;;; Tests for build-sports-places-path ;;;

(deftest build-sports-places-path-test
  (testing "build-sports-places-path constructs correct path"

    (testing "with different prefixes"
      (is (= "/v1/sports-places"
             (http/build-sports-places-path "/v1")))
      (is (= "/rest/api/sports-places"
             (http/build-sports-places-path "/rest/api")))
      (is (= "/api/sports-places"
             (http/build-sports-places-path "/api"))))))

(comment
  (clojure.test/run-tests 'legacy-api.http-test)
  (clojure.test/run-test-var #'create-page-links-test)
  (clojure.test/run-test-var #'extract-base-path-test))
