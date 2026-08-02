(ns lipas.backend.business-logic-test
  (:require [clojure.test :as t :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [lipas.backend.core :as core]
            [lipas.roles :as roles]))

(deftest gdpr-removal-logic-test
  (testing "More than 5 years of total user inactivity"
    (let [now           (java.time.Instant/now)
          long-time-ago (java.time.Instant/parse "2015-01-01T00:00:00.000Z")
          user          {:email      "kissa@koira.fi"
                         :created-at (java.sql.Timestamp/from long-time-ago)
                         :history    {}}]
      (is (true? (core/gdpr-remove? now user)))))

  (testing "Less than 5 years of total user inactivity"
    (let [now         (java.time.Instant/parse "2020-01-01T00:00:00.000Z")
          a-while-ago (java.time.Instant/parse "2017-01-01T00:00:00.000Z")
          user        {:email      "kissa@koira.fi"
                       :created-at (java.sql.Timestamp/from a-while-ago)
                       :history    {}}]
      (is (false? (core/gdpr-remove? now user)))))

  (testing "User was created > 5 years and has very old activity"
    (let [now           (java.time.Instant/now)
          long-time-ago (java.time.Instant/parse "2015-01-01T00:00:00.000Z")
          user          {:email      "kissa@koira.fi"
                         :created-at (java.sql.Timestamp/from long-time-ago)
                         :history    {:events [{:event-date "2015-01-01T00:00:00.000Z"}
                                               {:event-date "2015-09-12T00:00:00.000Z"}]}}]
      (is (true? (core/gdpr-remove? now user)))))

  (testing "User was created > 5 years and has recent activity"
    (let [now           (java.time.Instant/parse "2024-01-01T00:00:00.000Z")
          long-time-ago (java.time.Instant/parse "2015-01-01T00:00:00.000Z")
          user          {:email      "kissa@koira.fi"
                         :created-at (java.sql.Timestamp/from long-time-ago)
                         :history    {:events [{:event-date "2015-01-01T00:00:00.000Z"}
                                               {:event-date "2023-09-12T00:00:00.000Z"}]}}]
      (is (false? (core/gdpr-remove? now user)))))

  (testing "System users with @lipas.fi email are ignored"
    (let [now           (java.time.Instant/now)
          long-time-ago (java.time.Instant/parse "2015-01-01T00:00:00.000Z")
          user          {:email      "whatever@lipas.fi"
                         :created-at (java.sql.Timestamp/from long-time-ago)
                         :history    {}}]
      (is (false? (core/gdpr-remove? now user)))))

  (testing "Account without created-at is never eligible (nullable column, fail closed)"
    (let [now  (java.time.Instant/now)
          user {:email      "kissa@koira.fi"
                :created-at nil
                :history    {}}]
      (is (false? (core/gdpr-remove? now user)))))

  (testing "Impersonation events are not the owner's activity and don't reset the clock"
    (let [now           (java.time.Instant/now)
          long-time-ago (java.time.Instant/parse "2015-01-01T00:00:00.000Z")
          recent        (str (.minus now 30 java.time.temporal.ChronoUnit/DAYS))
          user          {:email      "kissa@koira.fi"
                         :created-at (java.sql.Timestamp/from long-time-ago)
                         :history    {:events [{:event-date recent
                                                :event "impersonation-started"
                                                :impersonator-id "an-admin-uuid"}]}}]
      (is (true? (core/gdpr-remove? now user)))
      (testing "but the owner's own recent event still protects the account"
        (is (false? (core/gdpr-remove?
                      now
                      (assoc-in user [:history :events 0 :event] "login"))))))))

;;; --- gdpr-remove? generative spec -------------------------------------------
;;;
;;; The predicate decides irreversible bulk anonymization, so it gets a
;;; property-based spec on top of the examples above. Users are built from
;;; blocks that are KNOWN recent or ancient by construction: every generated
;;; timestamp stays a 40-day guard band away from the 5-year boundary, so no
;;; property depends on leap-day arithmetic and the expected outcome needs no
;;; oracle. Exact boundary semantics are pinned by gdpr-boundary-semantics-test.

(def ^:private spec-now (java.time.Instant/parse "2026-08-02T00:00:00Z"))

(def ^:private five-years-days (* 5 365))
(def ^:private margin-days 40)

(defn- days-ago-ts [days]
  (java.sql.Timestamp/from (.minus spec-now days java.time.temporal.ChronoUnit/DAYS)))

(defn- days-ago-str [days]
  (str (.minus spec-now days java.time.temporal.ChronoUnit/DAYS)))

(def ^:private gen-recent-days
  "Clearly within the 5-year window."
  (gen/choose 0 (- five-years-days margin-days)))

(def ^:private gen-ancient-days
  "Clearly beyond the 5-year window."
  (gen/choose (+ five-years-days margin-days) (* 30 365)))

(def ^:private gen-owner-event-type
  ;; Alphanumeric strings can't collide with the dashed names in
  ;; core/non-activity-events, so anything generated here must count as the
  ;; owner's own activity.
  (gen/one-of [(gen/elements ["login" "password-reset" "permissions-updated"
                              "status-changed" "magic-link-sent"])
               gen/string-alphanumeric]))

(def ^:private gen-impersonation-event-type
  (gen/elements (vec core/non-activity-events)))

(defn- event [type days] {:event type :event-date (days-ago-str days)})

(def ^:private gen-ancient-owner-events
  (gen/vector (gen/fmap (fn [[t d]] (event t d))
                        (gen/tuple gen-owner-event-type gen-ancient-days))
              0 5))

(def ^:private gen-impersonation-events
  "Impersonation events at ANY age, sometimes without a date at all - none
  of it may count as the owner's activity."
  (gen/vector (gen/fmap (fn [[t d dateless?]]
                          (if dateless? {:event t} (event t d)))
                        (gen/tuple gen-impersonation-event-type
                                   (gen/choose 0 (* 30 365))
                                   (gen/frequency [[4 (gen/return false)]
                                                   [1 (gen/return true)]])))
              0 5))

(def ^:private gen-email
  (gen/fmap #(str % "@example.com") (gen/not-empty gen/string-alphanumeric)))

(def ^:private gen-dormant-user
  "The by-construction-eligible case: created long ago, every owner event
  long ago, impersonated whenever."
  (gen/fmap (fn [[email created-days evts ievts]]
              {:email email
               :created-at (days-ago-ts created-days)
               :history {:events (vec (concat evts ievts))}})
            (gen/tuple gen-email gen-ancient-days
                       gen-ancient-owner-events gen-impersonation-events)))

(def ^:private gen-mixed-user
  "Arbitrary shape: any email (incl. @lipas.fi), any event mix, sometimes no
  created-at. For properties about the predicate's form, not its outcome."
  (gen/fmap (fn [[email created-days nil-created? evts]]
              {:email email
               :created-at (when-not nil-created? (days-ago-ts created-days))
               :history {:events evts}})
            (gen/tuple (gen/one-of [gen-email
                                    (gen/fmap #(str % "@lipas.fi") gen/string-alphanumeric)])
                       (gen/one-of [gen-recent-days gen-ancient-days])
                       (gen/frequency [[9 (gen/return false)] [1 (gen/return true)]])
                       (gen/vector
                         (gen/fmap (fn [[t d]] (event t d))
                                   (gen/tuple (gen/one-of [gen-owner-event-type
                                                           gen-impersonation-event-type])
                                              (gen/one-of [gen-recent-days gen-ancient-days])))
                         0 6))))

(defspec dormant-user-is-eligible 300
  ;; Completeness: the predicate must not silently become "never remove
  ;; anyone" - and impersonation at any age must not protect the account.
  (prop/for-all [user gen-dormant-user]
                (true? (core/gdpr-remove? spec-now user))))

(defspec any-recent-owner-activity-protects 300
  (prop/for-all [user gen-dormant-user
                 evt-type gen-owner-event-type
                 days gen-recent-days]
                (false? (core/gdpr-remove?
                          spec-now
                          (update-in user [:history :events] conj (event evt-type days))))))

(defspec recent-account-age-protects 300
  (prop/for-all [user gen-mixed-user
                 days gen-recent-days]
                (false? (core/gdpr-remove? spec-now (assoc user :created-at (days-ago-ts days))))))

(defspec lipas-fi-email-always-protects 300
  (prop/for-all [user gen-dormant-user
                 local gen/string-alphanumeric]
                (false? (core/gdpr-remove? spec-now (assoc user :email (str local "@lipas.fi"))))))

(defspec nil-created-at-always-protects 300
  (prop/for-all [user gen-mixed-user]
                (false? (core/gdpr-remove? spec-now (assoc user :created-at nil)))))

(defspec dateless-owner-event-fails-closed 300
  ;; An owner event with no date is unprovable inactivity: garbage dates
  ;; already fail closed (throw -> batch skips), a missing date must too.
  (prop/for-all [user gen-dormant-user
                 evt-type gen-owner-event-type]
                (false? (core/gdpr-remove?
                          spec-now
                          (update-in user [:history :events] conj {:event evt-type})))))

(defspec event-order-is-irrelevant 200
  (prop/for-all [[user shuffled]
                 (gen/let [user gen-mixed-user
                           evts (gen/shuffle (-> user :history :events))]
                   [user (assoc-in user [:history :events] (vec evts))])]
                (= (core/gdpr-remove? spec-now user)
                   (core/gdpr-remove? spec-now shuffled))))

(defspec eligibility-is-monotone-in-time 300
  ;; Once dormant long enough, waiting longer can never flip the decision
  ;; back - the batch cap relies on this to drain a backlog across nights.
  (prop/for-all [user gen-mixed-user
                 extra-days (gen/choose 0 3650)]
                (if (core/gdpr-remove? spec-now user)
                  (core/gdpr-remove? (.plus spec-now extra-days java.time.temporal.ChronoUnit/DAYS) user)
                  true)))

(deftest gdpr-boundary-semantics-test
  (let [now (java.time.Instant/parse "2026-08-02T00:00:00Z")
        at-boundary (java.sql.Timestamp/from (java.time.Instant/parse "2021-08-02T00:00:00Z"))]
    (testing "exactly 5 years of account age is not yet eligible - strictly after"
      (is (false? (core/gdpr-remove? now {:email "a@b.fi" :created-at at-boundary :history {}}))))
    (testing "one second past 5 years is eligible"
      (is (true? (core/gdpr-remove? (.plusSeconds now 1)
                                    {:email "a@b.fi" :created-at at-boundary :history {}}))))
    (testing "an activity event exactly 5 years old still protects; a second later it doesn't"
      (let [user {:email "a@b.fi"
                  :created-at (java.sql.Timestamp/from (java.time.Instant/parse "2015-01-01T00:00:00Z"))
                  :history {:events [{:event "login" :event-date "2021-08-02T00:00:00Z"}]}}]
        (is (false? (core/gdpr-remove? now user)))
        (is (true? (core/gdpr-remove? (.plusSeconds now 1) user)))))))

;;; --- Ownership & edit-grant authorization (core business rule) --------------
;;; Pure predicates, so we can enumerate the cases exhaustively (handler tests in
;;; org-test cover the wiring). Users are built with conformed roles, exactly as
;;; the JWT carries them.

(defn- user-with
  "A user carrying `roles` (conformed to keyword/set shape like the token)."
  [& roles]
  {:permissions {:roles (roles/conform-roles (vec roles))}})

(def ^:private org-a "11111111-1111-1111-1111-111111111111")
(def ^:private org-b "22222222-2222-2222-2222-222222222222")

(def ^:private lipas-admin (user-with {:role :admin}))
(def ^:private admin-of-a (user-with {:role :org-admin :org-id #{org-a}}))
(def ^:private editor-of-a (user-with {:role :org-editor :org-id #{org-a}}))
(def ^:private nobody (user-with))

(deftest lipas-admin?-test
  (is (true? (core/lipas-admin? lipas-admin)))
  (is (false? (core/lipas-admin? admin-of-a)))
  (is (false? (core/lipas-admin? editor-of-a)))
  (is (false? (core/lipas-admin? nobody))))

(deftest owns-site-org?-test
  (testing "org-admin of the owning org owns it; admin of another org does not"
    (is (true? (core/owns-site-org? admin-of-a org-a)))
    (is (false? (core/owns-site-org? admin-of-a org-b))))
  (testing "an org-editor is not an org admin (no :org/manage)"
    (is (false? (core/owns-site-org? editor-of-a org-a))))
  (testing "lipas-admin effectively owns any org; nil owner owns nothing"
    (is (true? (core/owns-site-org? lipas-admin org-a)))
    (is (false? (core/owns-site-org? admin-of-a nil)))
    (is (false? (core/owns-site-org? nobody org-a)))))

(deftest ownership-change-authorized?-test
  (testing "an unchanged owner is always allowed (no-op), for anyone"
    (is (true? (core/ownership-change-authorized? nobody false org-a org-a)))
    (is (true? (core/ownership-change-authorized? nobody true nil nil)))
    (is (true? (core/ownership-change-authorized? editor-of-a false org-b org-b))))
  (testing "creating: may claim an org you can :site/create-edit on, or none"
    (is (true? (core/ownership-change-authorized? editor-of-a true nil org-a)) "own org")
    (is (true? (core/ownership-change-authorized? nobody true nil nil)) "no owner")
    (is (true? (core/ownership-change-authorized? lipas-admin true nil org-b)) "admin any")
    (is (false? (core/ownership-change-authorized? editor-of-a true nil org-b)) "foreign org")
    (is (false? (core/ownership-change-authorized? nobody true nil org-a)) "no rights"))
  (testing "existing-site ownership change is LIPAS-admin only"
    (is (true? (core/ownership-change-authorized? lipas-admin false org-a org-b)))
    (is (false? (core/ownership-change-authorized? admin-of-a false org-a org-b)) "even owner admin")
    (is (false? (core/ownership-change-authorized? editor-of-a false org-a org-b))))
  (testing "claiming a legacy (unowned) existing site is admin-only"
    (is (true? (core/ownership-change-authorized? lipas-admin false nil org-a)))
    (is (false? (core/ownership-change-authorized? editor-of-a false nil org-a))))
  (testing "release (clearing ownership): owning-org admin or LIPAS admin — shedding authority is self-service, gaining is not"
    (is (true? (core/ownership-change-authorized? lipas-admin false org-a nil)))
    (is (true? (core/ownership-change-authorized? admin-of-a false org-a nil)) "owner-org admin")
    (is (false? (core/ownership-change-authorized? admin-of-a false org-b nil)) "admin of ANOTHER org")
    (is (false? (core/ownership-change-authorized? editor-of-a false org-a nil)) "editor is not admin")
    (is (false? (core/ownership-change-authorized? nobody false org-a nil)))))

(deftest edit-grant-change-authorized?-test
  (testing "only LIPAS admin or an admin of the OWNING org may change grants"
    (is (true? (core/edit-grant-change-authorized? lipas-admin org-a)))
    (is (true? (core/edit-grant-change-authorized? admin-of-a org-a)) "owner-org admin")
    (is (false? (core/edit-grant-change-authorized? admin-of-a org-b)) "admin of another org")
    (is (false? (core/edit-grant-change-authorized? editor-of-a org-a)) "editor is not admin")
    (is (false? (core/edit-grant-change-authorized? nobody org-a))))
  (testing "on a legacy (unowned) site only a LIPAS admin may change grants"
    (is (true? (core/edit-grant-change-authorized? lipas-admin nil)))
    (is (false? (core/edit-grant-change-authorized? admin-of-a nil)))))

(comment
  (t/run-tests *ns*))
