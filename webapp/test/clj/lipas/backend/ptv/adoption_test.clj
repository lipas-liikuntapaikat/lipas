(ns lipas.backend.ptv.adoption-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [lipas.backend.core :as core]
            [lipas.backend.jwt :as jwt]
            [lipas.backend.ptv.adoption :as adoption]
            [lipas.test-utils :as tu]
            [ring.mock.request :as mock]))

;;; summarize ;;;

(def ^:private gen-row
  (gen/hash-map
    :lipas-id gen/nat
    :first-month (gen/fmap (fn [[y m]] (format "%d-%02d" y m))
                           (gen/tuple (gen/choose 2024 2026) (gen/choose 1 12)))
    :city-code (gen/elements [91 425 609 889 nil])
    :ptv-org-id (gen/elements ["org-1" "org-2" nil])
    :sync-enabled? gen/boolean
    :publishing-status (gen/elements ["Published" "Deleted" nil])))

(defspec summarize-is-consistent 200
  (prop/for-all [rows (gen/vector gen-row 0 60)]
                (let [{:keys [totals monthly municipalities]} (adoption/summarize rows "2026-12")
                      months (map :month monthly)]
                  (and (= (count rows) (:sites totals))
                       (= (count rows) (reduce + (map :new monthly)))
                       (= (count rows) (reduce + (map :sites municipalities)))
                       (= (count rows) (or (:total (peek monthly)) 0))
           ;; gap-free, ascending, ending at the current month
                       (= months (sort (distinct months)))
                       (or (empty? rows) (= "2026-12" (last months)))
                       (every? (fn [{:keys [new by-municipality]}]
                                 (= new (reduce + (map :new by-municipality))))
                               monthly)))))

(deftest summarize-test
  (let [rows [{:lipas-id 1 :first-month "2025-01" :city-code 889 :ptv-org-id "o"
               :sync-enabled? true :publishing-status "Published"}
              {:lipas-id 2 :first-month "2025-03" :city-code 889 :ptv-org-id "o"
               :sync-enabled? false :publishing-status "Deleted"}
              {:lipas-id 3 :first-month "2025-03" :city-code 425 :ptv-org-id "o"
               :sync-enabled? true :publishing-status "Published"}]
        {:keys [totals monthly municipalities]} (adoption/summarize rows "2025-04")]
    (is (= {:sites 3 :integrated 3 :sync-enabled 2 :sync-pending 0 :published 2 :deleted 1 :municipalities 2}
           totals))
    (is (= [["2025-01" 1 1] ["2025-02" 0 1] ["2025-03" 2 3] ["2025-04" 0 3]]
           (map (juxt :month :new :total) monthly))
        "months without adoption are filled in, carrying the total")
    (is (= [{:municipality "Liminka" :new 1} {:municipality "Utajärvi" :new 1}]
           (:by-municipality (nth monthly 2))))
    (is (= {:municipality "Utajärvi" :sites 2 :first-month "2025-01" :latest-month "2025-03"
            :published 1 :deleted 1}
           (select-keys (first municipalities)
                        [:municipality :sites :first-month :latest-month :published :deleted])))))

;;; Endpoint ;;;

(defonce test-system (atom nil))

(let [{:keys [once each]} (tu/full-system-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn- test-db [] (:lipas/db @test-system))
(defn- test-app [req] ((:lipas/app @test-system) req))

(def ^:private ptv
  {:org-id "test-org-id"
   :sync-enabled true
   :publishing-status "Published"
   :summary {:fi "s" :se "s" :en "s"}
   :description {:fi "d" :se "d" :en "d"}
   :service-channel-ids []
   :service-ids []})

(defn- save-revision! [user site event-date ptv?]
  (core/upsert-sports-site!* (test-db) user
                             (cond-> (assoc site :event-date event-date)
                               ptv? (assoc :ptv ptv))))

(defn- fetch-stats [user]
  (test-app (-> (mock/request :post "/api/actions/get-ptv-adoption-stats")
                (mock/content-type "application/json")
                (mock/body (tu/->json {}))
                (tu/token-header (jwt/create-token user)))))

(deftest get-ptv-adoption-stats-test
  (let [admin (tu/gen-admin-user :db-component (test-db))
        ;; fixed ids: the each-fixture prunes the DB between tests
        a (tu/make-point-site 900001 :city-code 889)
        b (tu/make-point-site 900002 :city-code 425)
        c (tu/make-point-site 900003 :city-code 91)]
    ;; a: integrated in March 2025, later edited; the first PTV revision counts
    (save-revision! admin a "2025-01-10T10:00:00.000Z" false)
    (save-revision! admin a "2025-03-15T10:00:00.000Z" true)
    (save-revision! admin a "2025-06-01T10:00:00.000Z" true)
    ;; b: 22:30 UTC on 31 March is already April in Helsinki
    (save-revision! admin b "2025-03-31T22:30:00.000Z" true)
    ;; c: never integrated
    (save-revision! admin c "2025-02-01T10:00:00.000Z" false)

    (testing "admin gets adoption stats from the revision history"
      (let [resp (fetch-stats admin)
            {:keys [totals monthly municipalities]} (tu/safe-parse-json resp)
            by-month (into {} (map (juxt :month identity)) monthly)]
        (is (= 200 (:status resp)))
        (is (= 2 (:sites totals)))
        (is (= 2 (:municipalities totals)))
        (is (= "2025-03" (:month (first monthly))))
        (is (= [{:municipality "Utajärvi" :new 1}] (:by-municipality (by-month "2025-03"))))
        (is (= [{:municipality "Liminka" :new 1}] (:by-municipality (by-month "2025-04"))))
        (is (= 2 (:total (peek monthly))))
        (is (= #{"Utajärvi" "Liminka"} (set (map :municipality municipalities))))))

    (testing "anonymous requests are refused"
      (is (= 401 (:status (test-app (-> (mock/request :post "/api/actions/get-ptv-adoption-stats")
                                        (mock/content-type "application/json")
                                        (mock/body (tu/->json {}))))))))

    (testing "non-admins are refused, including PTV managers and auditors"
      (doseq [[label user] [["regular user" (tu/gen-regular-user :db-component (test-db))]
                            ["PTV manager" (tu/gen-user {:db? true
                                                         :db-component (test-db)
                                                         :permissions {:roles [{:role :ptv-manager
                                                                                :city-code #{889}}]}})]
                            ["PTV auditor" (tu/gen-ptv-auditor :db-component (test-db))]]]
        (is (= 403 (:status (fetch-stats user))) label)))))
