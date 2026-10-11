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

(def ^:private gen-ptv
  (gen/hash-map
    :sync-enabled gen/boolean
    :publishing-status (gen/elements ["Published" "Deleted" nil])
    :org-id (gen/elements ["org-1" nil])
    :summary (gen/elements [{:fi "Lyhyt kuvaus"} nil])
    :description (gen/elements [{:fi "Pidempi kuvaus"} nil])
    :service-ids (gen/elements [[] ["s-1"]])
    :service-channel-ids (gen/elements [[] ["c-1"]])
    :error (gen/elements [nil {:message "HTTP Error: 500"}])))

(def ^:private gen-month
  (gen/fmap (fn [[y m]] (format "%d-%02d" y m))
            (gen/tuple (gen/choose 2024 2026) (gen/choose 1 12))))

(def ^:private gen-row
  (gen/let [lipas-id gen/nat
            first-month gen-month
            published-month (gen/one-of [(gen/return nil) gen-month])
            city-code (gen/elements [91 425 609 889 nil])
            status (gen/elements ["active" "incorrect-data"])
            owner (gen/elements ["city" "private"])
            ptv gen-ptv]
    {:lipas-id lipas-id
     :first-month first-month
     :first-published-month published-month
     :city-code city-code
     :site {:lipas-id lipas-id :name (str "Site " lipas-id) :status status :owner owner
            :type {:type-code 1340} :ptv ptv}}))

(defspec summarize-is-consistent 200
  (prop/for-all [rows (gen/vector gen-row 0 60)]
                (let [{:keys [totals monthly municipalities attention]} (adoption/summarize rows "2026-12")
                      ever-published (count (filter :first-published-month rows))
                      months (map :month monthly)]
                  (and (= ever-published (reduce + (map :new monthly)))
                       (= ever-published (or (:total (peek monthly)) 0))
                       ;; the municipality table adds up to the totals
                       (every? (fn [k] (= (k totals) (reduce + (map k municipalities))))
                               [:in-ptv :attention :warnings :sync-enabled :deleted])
                       ;; the attention list is exactly the flagged sites, errors first
                       (= (+ (:attention totals) (:warnings totals)) (count attention))
                       (= (:attention totals) (count (filter #(some (comp #{:error} :severity) (:reasons %)) attention)))
                       (= (:attention totals) (+ (:attention-never-reached totals) (:attention-failing totals)))
                       ;; gap-free, ascending, ending at the current month
                       (= months (sort (distinct months)))
                       (or (zero? ever-published) (= "2026-12" (last months)))
                       (every? (fn [{:keys [new by-municipality]}]
                                 (= new (reduce + (map :new by-municipality))))
                               monthly)))))

(defn- row [lipas-id city-code published-month ptv & {:keys [status] :or {status "active"}}]
  {:lipas-id lipas-id
   :first-month (or published-month "2025-01")
   :first-published-month published-month
   :city-code city-code
   :site {:lipas-id lipas-id :name (str "Site " lipas-id) :status status :owner "city"
          :type {:type-code 1340} :ptv ptv}})

(def ^:private healthy
  {:sync-enabled true :org-id "o" :publishing-status "Published"
   :summary {:fi "Lyhyt kuvaus"} :description {:fi "Pidempi kuvaus"}
   :service-ids ["s"] :service-channel-ids ["c"]})

(deftest summarize-test
  (let [rows [(row 1 889 "2025-01" healthy)
              ;; archived in PTV, sync off: not in PTV, nothing to do
              (row 2 889 "2025-03" (assoc healthy :sync-enabled false :publishing-status "Deleted"))
              (row 3 425 "2025-03" healthy)
              ;; got PTV data, never reached PTV: not adopted, needs attention
              (row 4 425 nil {:sync-enabled true})
              ;; in PTV, last update failed
              (row 5 425 "2025-04" (assoc healthy :error {:message "HTTP Error: 500"}))
              ;; marked incorrect but LIPAS still says Published: a warning
              (row 6 889 "2025-04" healthy :status "incorrect-data")]
        {:keys [totals monthly municipalities attention]} (adoption/summarize rows "2025-04")]
    (is (= {:in-ptv 4 :in-ptv-sync-off 0 :sync-enabled 5 :deleted 1
            :attention 2 :attention-never-reached 1 :attention-failing 1 :warnings 1
            :municipalities 2 :new-this-month 2}
           totals))
    (is (= [["2025-01" 1 1] ["2025-02" 0 1] ["2025-03" 2 3] ["2025-04" 2 5]]
           (map (juxt :month :new :total) monthly))
        "adoption counts first publications; months without any are filled in")
    (is (= [{:municipality "Liminka" :new 1} {:municipality "Utajärvi" :new 1}]
           (:by-municipality (nth monthly 2))))
    (is (= [4 5 6] (map :lipas-id attention)) "errors first, then warnings")
    (is (= [[:ptv/no-org :ptv/missing-texts] [:ptv/sync-error] [:ptv/published-not-candidate]]
           (map #(map :reason (:reasons %)) attention)))
    (is (= "HTTP Error: 500" (:error-message (second attention))))
    (is (= {:municipality "Liminka" :in-ptv 2 :attention 2 :first-month "2025-03" :latest-month "2025-04"}
           (select-keys (first municipalities) [:municipality :in-ptv :attention :first-month :latest-month])))))

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
        (is (= 2 (:in-ptv totals)))
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
