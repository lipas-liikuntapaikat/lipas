(ns lipas.backend.business-logic-test
  (:require [clojure.test :as t :refer [deftest is testing]]
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
      (is (false? (core/gdpr-remove? now user))))))

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
  (testing "claiming a legacy (unowned) existing site, or clearing ownership, is admin-only"
    (is (true? (core/ownership-change-authorized? lipas-admin false nil org-a)))
    (is (false? (core/ownership-change-authorized? editor-of-a false nil org-a)))
    (is (true? (core/ownership-change-authorized? lipas-admin false org-a nil)))
    (is (false? (core/ownership-change-authorized? admin-of-a false org-a nil)))))

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
