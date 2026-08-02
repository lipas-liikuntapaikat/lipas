(ns lipas.backend.gdpr-test
  "End-to-end tests for the GDPR removal batch — the only bulk-destructive
  operation in LIPAS. The batch must anonymize exactly the eligible users
  and provably leave every other account byte-for-byte untouched."
  (:require
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing use-fixtures]]
    [lipas.backend.core :as core]
    [lipas.backend.db.db :as db]
    [lipas.test-utils :as tu]
    [next.jdbc :as jdbc]))

(defonce test-system (atom nil))

(let [{:keys [once each]} (tu/db-only-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn test-db [] (:lipas/db @test-system))

(defn- instant-ago [days]
  (.minus (java.time.Instant/now) days java.time.temporal.ChronoUnit/DAYS))

(def ^:private six-years-days 2200)

(defn- gen-user-created-ago!
  "A persisted user whose account is `days` old with exactly `events` in its
  history (generated users get random history, which would make eligibility
  nondeterministic)."
  [db days events]
  (let [user (tu/gen-regular-user :db-component db)]
    (tu/backdate-user-created-at! db (:id user) (instant-ago days))
    (db/update-user-history! db (assoc user :history {:events events}))
    user))

(defn- fetch [db user]
  (db/get-user-by-id db {:id (:id user)}))

(defn- row-fingerprint
  "The full account row with every column stringified, so equality means
  'nothing about this row changed' without depending on PGobject/timestamp
  equality semantics."
  [db user]
  (update-vals
    (first (jdbc/execute! db ["SELECT * FROM account WHERE id = ?::uuid"
                              (str (:id user))]))
    str))

(defn- anonymized? [db user]
  (let [{:keys [email username status user-data]} (fetch db user)]
    (and (str/starts-with? email "gdpr_removed_")
         (str/ends-with? email "@lipas.fi")
         (str/starts-with? username "gdpr_removed_")
         (= "archived" status)
         (empty? user-data))))

(deftest gdpr-remove-user!-removes-exactly-the-personal-data-test
  (let [db (test-db)
        user (tu/gen-regular-user :db-component db)
        before (fetch db user)
        password-before (:account/password (row-fingerprint db user))]
    (core/gdpr-remove-user! db user)
    (let [after (fetch db user)]
      (testing "personal data is gone"
        (is (anonymized? db user))
        (is (not= (:email before) (:email after)))
        (is (not= (:username before) (:username after)))
        (is (empty? (:user-data after))))
      (testing "the removal is recorded in the user's history"
        (is (some #(= "GDPR removal" (:event %)) (-> after :history :events))))
      (testing "nothing else is touched"
        (is (= (:id before) (:id after)))
        (is (= (:permissions before) (:permissions after)))
        (is (= (:created-at before) (:created-at after)))
        (is (= password-before (:account/password (row-fingerprint db user))))))))

(deftest process-gdpr-removals!-end-to-end-test
  (let [db (test-db)
        eligible (gen-user-created-ago! db six-years-days [])
        old-active (gen-user-created-ago! db six-years-days
                                          [{:event-date (str (instant-ago 30))
                                            :event "login"}])
        recent (gen-user-created-ago! db 30 [])
        system-user (let [u (gen-user-created-ago! db six-years-days [])]
                      (db/update-user-email! db (assoc u :email "robot-gdpr-test@lipas.fi"))
                      u)
        no-created-at (let [u (gen-user-created-ago! db six-years-days [])]
                        (jdbc/execute! db ["UPDATE account SET created_at = NULL WHERE id = ?::uuid"
                                           (str (:id u))])
                        u)
        broken-history (gen-user-created-ago! db six-years-days
                                              [{:event-date "not-a-timestamp"
                                                :event "login"}])
        untouchables [old-active recent system-user no-created-at broken-history]
        before (mapv #(row-fingerprint db %) untouchables)
        result (core/process-gdpr-removals! db)]

    (testing "exactly the one eligible user is removed"
      (is (= {:eligible 1 :batch 1 :removed 1 :failed 0 :check-errors 1 :dry-run? false}
             result))
      (is (anonymized? db eligible)))

    (testing "an unparseable history fails closed: skipped, reported, never removed"
      (is (= 1 (:check-errors result))))

    (testing "every ineligible account is byte-for-byte untouched"
      (is (= before (mapv #(row-fingerprint db %) untouchables))))

    (testing "a second run is a no-op"
      (is (= {:eligible 0 :batch 0 :removed 0 :failed 0 :check-errors 1 :dry-run? false}
             (core/process-gdpr-removals! db)))
      (is (= before (mapv #(row-fingerprint db %) untouchables))))))

(deftest process-gdpr-removals!-cap-test
  (let [db (test-db)
        users (vec (repeatedly 3 #(gen-user-created-ago! db six-years-days [])))
        removed-count (fn [] (count (filter #(anonymized? db %) users)))]

    (testing "a run removes at most :limit users"
      (is (= {:eligible 3 :batch 2 :removed 2 :failed 0 :check-errors 0 :dry-run? false}
             (core/process-gdpr-removals! db {:limit 2})))
      (is (= 2 (removed-count))))

    (testing "the next run drains the remainder"
      (is (= {:eligible 1 :batch 1 :removed 1 :failed 0 :check-errors 0 :dry-run? false}
             (core/process-gdpr-removals! db {:limit 2})))
      (is (= 3 (removed-count))))))

(deftest process-gdpr-removals!-dry-run-test
  (let [db (test-db)
        user (gen-user-created-ago! db six-years-days [])
        before (row-fingerprint db user)]
    (testing "dry run reports what a live run would do but writes nothing"
      (is (= {:eligible 1 :batch 1 :removed 0 :failed 0 :check-errors 0 :dry-run? true}
             (core/process-gdpr-removals! db {:dry-run? true})))
      (is (= before (row-fingerprint db user))))))

(deftest process-gdpr-removals!-failure-isolation-test
  ;; Fault injection: a removal that blows up for one user must not stop the
  ;; batch, but the batch must then fail loudly so the job system retries /
  ;; dead-letters it instead of reporting quiet success.
  (let [db (test-db)
        user-a (gen-user-created-ago! db six-years-days [])
        user-b (gen-user-created-ago! db six-years-days [])
        orig core/gdpr-remove-user!
        ex (with-redefs [core/gdpr-remove-user!
                         (fn [db user]
                           (if (= (:id user-a) (:id user))
                             (throw (ex-info "injected removal failure" {}))
                             (orig db user)))]
             (is (thrown? clojure.lang.ExceptionInfo
                          (core/process-gdpr-removals! db))))]
    (testing "the batch summary rides on the exception"
      (is (= {:removed 1 :failed 1} (select-keys (ex-data ex) [:removed :failed]))))
    (testing "the healthy user was still removed, the failed one left intact"
      (is (anonymized? db user-b))
      (is (not (anonymized? db user-a)))
      (is (not (str/starts-with? (:email (fetch db user-a)) "gdpr_removed_"))))))
