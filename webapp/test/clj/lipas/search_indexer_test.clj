(ns lipas.search-indexer-test
  "A full reindex goes through `index-search-sports-sites!` type by type.
  On 2026-11-22 one site it couldn't enrich aborted the run before the alias
  swap, while the deploy reported success."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lipas.backend.db.db :as db]
            [lipas.backend.search :as search]
            [lipas.search-indexer :as sut]
            [lipas.test-utils :as tu]))

(defonce test-system (atom nil))

(def ^:private idx "zzsearch-indexer-test")

(def ^:private ^:dynamic *client* nil)

(defn- with-fresh-index [f]
  (binding [*client* (search/create-cli
                       (let [{:keys [hosts user pass]} (:search tu/config)]
                         {:hosts hosts :user user :password pass}))]
    (try (search/delete-index! *client* idx) (catch Exception _ nil))
    (search/create-index! *client* idx (:sports-site search/mappings))
    (try (f)
         (finally (try (search/delete-index! *client* idx) (catch Exception _ nil))))))

(let [{:keys [once each]} (tu/db-only-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each with-fresh-index))

(defn- indexed [lipas-id]
  (try (-> (search/fetch-document *client* idx lipas-id) :body :_source)
       (catch Exception e
         (when-not (= 404 (:status (ex-data e))) (throw e)))))

(def ^:private square [[[25.0 60.0] [25.01 60.0] [25.01 60.01] [25.0 60.01] [25.0 60.0]]])

(def ^:private point-repeated [[[25.005 60.005] [25.005 60.005] [25.005 60.005] [25.005 60.005]]])

(def ^:private spike
  [[[25.0 60.0] [25.002 60.0] [25.002 60.002] [25.001 60.002] [25.001 60.003]
    [25.001 60.002] [25.0 60.002] [25.0 60.0]]])

(deftest one-bad-site-does-not-stop-the-reindex-test
  (let [db (:lipas/db @test-system)
        user (tu/gen-admin-user :db-component db)
        site (fn [lipas-id polygons] (tu/make-area-site lipas-id :type-code 103 :polygons polygons))]
    (db/upsert-sports-site! db user (site 9990001 [square]))
    (db/upsert-sports-site! db user (site 9990002 [point-repeated]))
    (db/upsert-sports-site! db user (site 9990003 [point-repeated square]))
    (db/upsert-sports-site! db user (site 9990004 [spike]))

    (let [skipped (sut/index-search-sports-sites! db *client* idx [103])]
      (testing "the site without any polygon area is left out, and reported"
        (is (= [9990002] skipped))
        (is (nil? (indexed 9990002))))

      (testing "the other sites are indexed"
        (is (= 1 (count (get-in (indexed 9990001) [:location :geometries :features])))))

      (testing "a site loses only its polygon without area"
        (is (= [(first square)]
               (map #(first (get-in % [:geometry :coordinates]))
                    (get-in (indexed 9990003) [:location :geometries :features])))))

      (testing "a repaired polygon without an id gets no id key"
        (let [fs (get-in (indexed 9990004) [:location :geometries :features])]
          (is (= 1 (count fs)))
          (is (not-any? #(contains? % :id) fs)))))))
