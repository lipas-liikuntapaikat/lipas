(ns lipas.backend.backdated-closure-api-test
  "Backdated closures end to end: the save endpoint (core/save-sports-site!:
  DB, response, search index), the year rule's status codes, LOIs, and the
  strict mapping of an index created before :decommission-year existed."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lipas.backend.core :as core]
            [lipas.backend.jwt :as jwt]
            [lipas.backend.search :as search]
            [lipas.test-utils :as tu]
            [ring.mock.request :as mock]))

(defonce test-system (atom nil))

(let [{:keys [once each]} (tu/full-system-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn test-db [] (:lipas/db @test-system))
(defn test-search [] (:lipas/search @test-system))
(defn test-app [req] ((:lipas/app @test-system) req))

(def ^:private closed "out-of-service-permanently")

(defn- post [path body user]
  (let [resp (test-app (-> (mock/request :post path)
                           (mock/content-type "application/json")
                           (mock/body (tu/->json body))
                           (tu/token-header (jwt/create-token user))))]
    (assoc resp :parsed (try (tu/<-json (:body resp)) (catch Exception _ nil)))))

(defn- point-site
  "A site the save endpoint accepts (make-point-site's city-code is a string)."
  [lipas-id & opts]
  (-> (apply tu/make-point-site lipas-id opts)
      (assoc-in [:location :city :city-code] 91)))

(defn- indexed-site [lipas-id]
  (let [{:keys [client indices]} (test-search)]
    (-> (search/fetch-document client (get-in indices [:sports-site :search]) lipas-id)
        :body :_source)))

(deftest backdated-closure-through-the-save-endpoint
  (let [admin (tu/gen-admin-user :db-component (test-db))
        id 9950001
        site (point-site id :name "Kenttä")]
    (core/upsert-sports-site!* (test-db) admin (assoc site :event-date "2015-03-01T10:00:00.000Z"))
    (core/upsert-sports-site!* (test-db) admin (assoc site :event-date "2021-05-01T10:00:00.000Z"
                                                      :name "Kenttä 2021"))

    (testing "without the year: 400"
      (let [resp (post "/api/sports-sites"
                       (assoc site :status closed :event-date "2018-12-31T23:59:59.999Z")
                       admin)]
        (is (= 400 (:status resp)))
        (is (= "decommission-year-required" (get-in resp [:parsed :type])))))

    (testing "with the year"
      (let [resp (post "/api/sports-sites"
                       (assoc site :status closed :event-date "2018-12-31T23:59:59.999Z"
                              :name "Kenttä 2021" :decommission-year 2018)
                       admin)
            body (:parsed resp)]
        (is (= 201 (:status resp)))
        (testing "the response is the new current revision: the later edit, closed"
          (is (= [closed 2018 "Kenttä 2021" "2021-05-01T10:00:00.000Z"]
                 ((juxt :status :decommission-year :name :event-date) body))))
        (testing "the database agrees"
          (is (= [closed 2018] ((juxt :status :decommission-year) (core/get-sports-site (test-db) id)))))
        (testing "so does the search index"
          (is (= [closed 2018] ((juxt :status :decommission-year) (indexed-site id)))))))

    (testing "reactivating drops the year"
      (let [resp (post "/api/sports-sites"
                       (assoc site :status "active" :event-date (str (java.time.Instant/now))
                              :decommission-year 2018)
                       admin)]
        (is (= 201 (:status resp)))
        (is (not (contains? (:parsed resp) :decommission-year)))
        (is (not (contains? (indexed-site id) :decommission-year)))))))

(deftest unauthorized-closure-is-403-not-400
  (let [admin (tu/gen-admin-user :db-component (test-db))
        stranger (tu/gen-regular-user :db-component (test-db))
        id 9950002
        site (point-site id)]
    (core/upsert-sports-site!* (test-db) admin site)
    (is (= 403 (:status (post "/api/sports-sites"
                              (assoc site :status closed :event-date (str (java.time.Instant/now)))
                              stranger))))))

(deftest backdated-loi-closure-indexes-the-current-loi
  (let [admin (tu/gen-admin-user :db-component (test-db))
        ;; fixed, not generated: random LOIs don't always pass the endpoint's
        ;; request coercion
        loi {:id (str (java.util.UUID/randomUUID))
             :loi-category "outdoor-recreation-facilities"
             :loi-type "dog-swimming-area"
             :geometries {:type "FeatureCollection"
                          :features [{:type "Feature"
                                      :geometry {:type "Point" :coordinates [25.0 65.0]}}]}}
        save! #(post "/api/actions/save-loi" (merge loi %) admin)]
    (is (= 200 (:status (save! {:event-date "2015-03-01T10:00:00.000Z" :status "active"}))))
    (is (= 200 (:status (save! {:event-date "2021-03-01T10:00:00.000Z" :status "active"}))))
    (is (= 400 (:status (save! {:event-date "2018-12-31T23:59:59.999Z" :status closed}))))
    (is (= 200 (:status (save! {:event-date "2018-12-31T23:59:59.999Z" :status closed
                                :decommission-year 2018}))))
    (let [{:keys [client indices]} (test-search)
          indexed (-> (search/fetch-document client (get-in indices [:lois :search]) (:id loi))
                      :body :_source)]
      (is (= [closed 2018 "2021-03-01T10:00:00.000Z"]
             ((juxt :status :decommission-year :event-date) indexed))))))

(deftest new-fields-reach-an-index-created-before-them
  ;; Production's live index predates :decommission-year. Its strict mapping
  ;; would reject every document carrying the field until a reindex; startup
  ;; adds the field (search/put-added-fields!).
  (let [{:keys [client]} (test-search)
        idx "decommission_mapping_probe_test"
        old-mapping (update-in (search/generate-explicit-mapping)
                               [:mappings :properties] dissoc :decommission-year)
        doc {:lipas-id 1 :status closed :decommission-year 2018}]
    (try
      (search/create-index! client idx old-mapping)
      (is (thrown? Exception (search/index! client idx :lipas-id doc :sync))
          "the old strict mapping rejects the field")
      (search/put-added-fields! client idx)
      (is (= 201 (:status (search/index! client idx :lipas-id doc :sync))))
      (testing "the added fields match the full mapping"
        (is (= search/added-sports-site-fields
               (select-keys (get-in (search/generate-explicit-mapping) [:mappings :properties])
                            (keys search/added-sports-site-fields)))))
      (finally
        (try (search/delete-index! client idx) (catch Exception _ nil))))))
