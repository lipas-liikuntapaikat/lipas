(ns lipas.backend.bulk-operations-test
  "Bulk contact update is now an ORG operation: CQRS POST actions
  /actions/get-org-sites-for-bulk and /actions/mass-update-org-sites.
  The read-only candidate listing is member-visible (org-member-or-admin?,
  F33); the mass-update WRITE is gated by :site/create-edit for the org
  (admits lipas-admin + org-editor members). The candidate/authorized set is
  the org's editable sites (owner-org-id = org OR edit-grants contains org)."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lipas.backend.core :as core]
            [lipas.backend.jwt :as jwt]
            [lipas.backend.org :as backend-org]
            [lipas.test-utils :as test-utils]
            [lipas.utils :as utils]
            [ring.mock.request :as mock]))

(defonce test-system (atom nil))

(let [{:keys [once each]} (test-utils/full-system-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn test-app [] (:lipas/app @test-system))
(defn test-db [] (:lipas/db @test-system))
(defn test-search [] (:lipas/search @test-system))

;;; Helpers ;;;

(defn- org-editor-user
  "A user whose JWT carries the org-editor role for `org-id` (as projected from
  org membership at login). Real projections (org/member->roles) always include
  the org-user baseline alongside catalog roles — org-editor never exists
  without :org/member — so the fixture must carry both."
  [org-id]
  (test-utils/gen-user {:db? true
                        :db-component (test-db)
                        :permissions {:roles [{:role "org-user" :org-id [(str org-id)]}
                                              {:role "org-editor" :org-id [(str org-id)]}]}}))

(defn- mk-site!
  [lipas-id city-code type-code extra]
  (let [admin (test-utils/gen-admin-user :db-component (test-db))
        site (merge (-> (test-utils/gen-sports-site)
                        (assoc :lipas-id lipas-id
                               :event-date (utils/timestamp)
                               :status "active"
                               :email "old@x.fi"
                               :phone-number "+358401111111"
                               :www "old.x.fi"
                               :reservations-link "old-book.x.fi")
                        (assoc-in [:type :type-code] type-code)
                        (assoc-in [:location :city :city-code] city-code))
                    extra)]
    (core/upsert-sports-site!* (test-db) admin site)
    (core/index! (test-search) site true)
    site))

(defn- seed-org-sites!
  "owned1/owned2 owned by org; granted owned by another org but edit-granted to
  org; unrelated belongs to nobody. Returns their lipas-ids."
  [org-id]
  (let [other (str (java.util.UUID/randomUUID))]
    {:owned1    (:lipas-id (mk-site! 9995001 91 1110 {:owner-org-id (str org-id)}))
     :owned2    (:lipas-id (mk-site! 9995002 49 1120 {:owner-org-id (str org-id)}))
     :granted   (:lipas-id (mk-site! 9995003 837 1130 {:owner-org-id other :edit-grants [(str org-id)]}))
     :unrelated (:lipas-id (mk-site! 9995004 91 1110 {}))}))

(defn- persisted-catalog-org!
  "Create a REAL persisted org with an editor role-template catalog (so
  membership projection has a catalog ceiling to draw from). Returns its id."
  []
  (let [org-id (java.util.UUID/randomUUID)]
    (backend-org/create-org (test-db)
                            {:id org-id
                             :name (str "Bulk Test Org " (System/currentTimeMillis))
                             :data {:primary-contact {:email "bulk@test.fi"}}})
    (backend-org/update-catalog! (test-db) org-id
                                 {:editor {:label "Muokkaaja"
                                           :roles [{:role "org-editor"}]}}
                                 nil)
    org-id))

;;; Tests ;;;

(deftest list-org-editable-sites-test
  (testing "org-editor lists the org's editable sites (owned ∪ granted), not others"
    (let [org-id (java.util.UUID/randomUUID)
          {:keys [owned1 owned2 granted unrelated]} (seed-org-sites! org-id)
          token (jwt/create-token (org-editor-user org-id))
          resp ((test-app) (-> (mock/request :post "/api/actions/get-org-sites-for-bulk")
                               (mock/content-type "application/json")
                               (mock/body (test-utils/->json {:org-id org-id}))
                               (test-utils/token-header token)))
          body (test-utils/safe-parse-json resp)
          ids  (set (map :lipas-id body))]
      (is (= 200 (:status resp)))
      (is (contains? ids owned1))
      (is (contains? ids owned2))
      (is (contains? ids granted) "granted (cross-org) site is in the editable set")
      (is (not (contains? ids unrelated)) "unrelated site is excluded")
      (is (true? (:owned? (first (filter #(= owned1 (:lipas-id %)) body)))) "owned site flagged owned?")
      (is (false? (:owned? (first (filter #(= granted (:lipas-id %)) body)))) "granted site not owned?"))))

(deftest mass-update-org-editor-success-test
  (testing "org-editor can mass-update owned + granted sites"
    (let [org-id (java.util.UUID/randomUUID)
          {:keys [owned1 granted]} (seed-org-sites! org-id)
          token (jwt/create-token (org-editor-user org-id))
          payload {:org-id org-id :lipas-ids [owned1 granted] :updates {:email "new@org.fi"}}
          resp ((test-app) (-> (mock/request :post "/api/actions/mass-update-org-sites")
                               (mock/content-type "application/json")
                               (mock/body (test-utils/->json payload))
                               (test-utils/token-header token)))
          body (test-utils/safe-parse-json resp)]
      (is (= 200 (:status resp)))
      (is (= 2 (:total-updated body)))
      (is (= "new@org.fi" (:email (core/get-sports-site2 (test-search) owned1 :none)))))))

(deftest mass-update-rejects-foreign-sites-test
  (testing "an org-editor cannot update a site outside the org's editable set"
    (let [org-id (java.util.UUID/randomUUID)
          {:keys [owned1 unrelated]} (seed-org-sites! org-id)
          token (jwt/create-token (org-editor-user org-id))
          payload {:org-id org-id :lipas-ids [owned1 unrelated] :updates {:email "x@x.fi"}}
          resp ((test-app) (-> (mock/request :post "/api/actions/mass-update-org-sites")
                               (mock/content-type "application/json")
                               (mock/body (test-utils/->json payload))
                               (test-utils/token-header token)))]
      (is (= 500 (:status resp)) "unauthorized lipas-ids are rejected"))))

(deftest non-editor-denied-test
  (testing "a user without org-editor for the org is denied (403) on both endpoints"
    (let [org-id (java.util.UUID/randomUUID)
          {:keys [owned1]} (seed-org-sites! org-id)
          token (jwt/create-token (test-utils/gen-regular-user :db-component (test-db)))
          get-resp ((test-app) (-> (mock/request :post "/api/actions/get-org-sites-for-bulk")
                                   (mock/content-type "application/json")
                                   (mock/body (test-utils/->json {:org-id org-id}))
                                   (test-utils/token-header token)))
          post-resp ((test-app) (-> (mock/request :post "/api/actions/mass-update-org-sites")
                                    (mock/content-type "application/json")
                                    (mock/body (test-utils/->json {:org-id org-id :lipas-ids [owned1] :updates {:email "x@x.fi"}}))
                                    (test-utils/token-header token)))]
      (is (= 403 (:status get-resp)))
      (is (= 403 (:status post-resp))))))

(deftest org-admin-without-editor-template-reads-but-cannot-write-test
  ;; F33 (live testing): an org-admin whose member roles are only ["admin"]
  ;; projects org-admin + org-user — NO :site/create-edit. The Kohteet tab's
  ;; count chips (gated org-member-or-admin?) showed e.g. 210 owned, but the
  ;; list endpoint 403'd and the FE swallowed it → silently empty list.
  ;; The read-only candidate listing is member-visible now; the mass-update
  ;; WRITE keeps the strict gate. Fails on the old gate: (a) returned 403.
  (testing "org-admin with roles [\"admin\"] only: list 200, mass-update 403"
    (let [org-id (persisted-catalog-org!)
          {:keys [owned1 owned2 granted unrelated]} (seed-org-sites! org-id)
          ;; real membership path: add-member! roles ["admin"], then the org
          ;; roles are projected into the token exactly as login does
          user   (test-utils/gen-org-admin-user org-id :db-component (test-db))
          token  (jwt/create-token user)
          list-resp ((test-app) (-> (mock/request :post "/api/actions/get-org-sites-for-bulk")
                                    (mock/content-type "application/json")
                                    (mock/body (test-utils/->json {:org-id org-id}))
                                    (test-utils/token-header token)))
          ids    (set (map :lipas-id (test-utils/safe-parse-json list-resp)))
          write-resp ((test-app) (-> (mock/request :post "/api/actions/mass-update-org-sites")
                                     (mock/content-type "application/json")
                                     (mock/body (test-utils/->json {:org-id org-id
                                                                    :lipas-ids [owned1]
                                                                    :updates {:email "nope@org.fi"}}))
                                     (test-utils/token-header token)))]
      (is (= 200 (:status list-resp))
          "member-visible read-only listing (old gate 403'd — fails on old)")
      (is (= #{owned1 owned2 granted} ids)
          "the org's editable set (owned ∪ granted), nothing else")
      (is (not (contains? ids unrelated)))
      (is (= 403 (:status write-resp))
          "mass-update WRITE stays gated by :site/create-edit")
      (is (= "old@x.fi" (:email (core/get-sports-site (test-db) owned1)))
          "the site's stored document is untouched")))

  (testing "a PLAIN org member (roles []) also gets the read-only list, no write"
    (let [org-id (persisted-catalog-org!)
          {:keys [owned1]} (seed-org-sites! org-id)
          user   (test-utils/gen-org-user org-id :db-component (test-db))
          token  (jwt/create-token user)
          list-resp ((test-app) (-> (mock/request :post "/api/actions/get-org-sites-for-bulk")
                                    (mock/content-type "application/json")
                                    (mock/body (test-utils/->json {:org-id org-id}))
                                    (test-utils/token-header token)))
          write-resp ((test-app) (-> (mock/request :post "/api/actions/mass-update-org-sites")
                                     (mock/content-type "application/json")
                                     (mock/body (test-utils/->json {:org-id org-id
                                                                    :lipas-ids [owned1]
                                                                    :updates {:email "nope@org.fi"}}))
                                     (test-utils/token-header token)))]
      (is (= 200 (:status list-resp)))
      (is (= 403 (:status write-resp))))))

(deftest org-editor-of-other-org-denied-test
  (testing "an org-editor of a DIFFERENT org cannot bulk-edit this org's sites"
    (let [org-id   (java.util.UUID/randomUUID)
          other-id (java.util.UUID/randomUUID)
          {:keys [owned1]} (seed-org-sites! org-id)
          token (jwt/create-token (org-editor-user other-id))
          resp ((test-app) (-> (mock/request :post "/api/actions/get-org-sites-for-bulk")
                               (mock/content-type "application/json")
                               (mock/body (test-utils/->json {:org-id org-id}))
                               (test-utils/token-header token)))]
      (is (= 403 (:status resp))))))

(deftest admin-can-mass-update-test
  (testing "lipas-admin can mass-update any org's sites"
    (let [org-id (java.util.UUID/randomUUID)
          {:keys [owned1]} (seed-org-sites! org-id)
          token (jwt/create-token (test-utils/gen-admin-user :db-component (test-db)))
          payload {:org-id org-id :lipas-ids [owned1] :updates {:email "admin@org.fi"}}
          resp ((test-app) (-> (mock/request :post "/api/actions/mass-update-org-sites")
                               (mock/content-type "application/json")
                               (mock/body (test-utils/->json payload))
                               (test-utils/token-header token)))
          body (test-utils/safe-parse-json resp)]
      (is (= 200 (:status resp)))
      (is (= 1 (:total-updated body))))))

(deftest auth-required-test
  (testing "endpoints require authentication"
    (let [org-id (java.util.UUID/randomUUID)]
      (is (= 401 (:status ((test-app) (-> (mock/request :post "/api/actions/get-org-sites-for-bulk")
                                          (mock/content-type "application/json")
                                          (mock/body (test-utils/->json {:org-id org-id}))))))))))

(deftest invalid-payload-test
  (testing "invalid email is rejected"
    (let [org-id (java.util.UUID/randomUUID)
          {:keys [owned1]} (seed-org-sites! org-id)
          token (jwt/create-token (test-utils/gen-admin-user :db-component (test-db)))
          payload {:org-id org-id :lipas-ids [owned1] :updates {:email "not-an-email"}}
          resp ((test-app) (-> (mock/request :post "/api/actions/mass-update-org-sites")
                               (mock/content-type "application/json")
                               (mock/body (test-utils/->json payload))
                               (test-utils/token-header token)))]
      (is (= 400 (:status resp))))))

(deftest authorization-uses-db-state-test
  (testing "a grant revoked in the DB (ES not yet reindexed) blocks mass-update"
    (let [org-id (java.util.UUID/randomUUID)
          {:keys [owned1]} (seed-org-sites! org-id)
          admin (test-utils/gen-admin-user :db-component (test-db))
          ;; Revoke the org's edit access in the DB only: append a newer
          ;; revision WITHOUT :owner-org-id and deliberately do NOT reindex —
          ;; ES `search-meta.editor-org-ids` still claims the org may edit.
          stored (core/get-sports-site (test-db) owned1)
          _ (core/upsert-sports-site!* (test-db) admin
                                       (-> stored
                                           (dissoc :owner-org-id)
                                           (assoc :event-date (utils/timestamp))))
          token (jwt/create-token (org-editor-user org-id))
          payload {:org-id org-id :lipas-ids [owned1] :updates {:email "stale@org.fi"}}
          resp ((test-app) (-> (mock/request :post "/api/actions/mass-update-org-sites")
                               (mock/content-type "application/json")
                               (mock/body (test-utils/->json payload))
                               (test-utils/token-header token)))
          saved (core/get-sports-site (test-db) owned1)]
      (is (= 500 (:status resp))
          "revoked-in-DB site is rejected even though the ES cache is stale")
      (is (= "old@x.fi" (:email saved))
          "the site's stored document is untouched"))))

(deftest no-clobber-of-newer-db-revision-test
  (testing "mass-update derives from the DB current revision, not the stale ES copy"
    (let [org-id (java.util.UUID/randomUUID)
          {:keys [owned1]} (seed-org-sites! org-id)
          admin (test-utils/gen-admin-user :db-component (test-db))
          grantee (str (java.util.UUID/randomUUID))
          ;; A newer DB revision (e.g. a just-approved change) that ES hasn't
          ;; reindexed yet: new name + a fresh edit-grant, still org-owned.
          stored (core/get-sports-site (test-db) owned1)
          newer (assoc stored
                       :event-date (utils/timestamp)
                       :name "Newer DB Name"
                       :edit-grants [grantee])
          _ (core/upsert-sports-site!* (test-db) admin newer)
          token (jwt/create-token (org-editor-user org-id))
          payload {:org-id org-id :lipas-ids [owned1] :updates {:email "new@org.fi"}}
          resp ((test-app) (-> (mock/request :post "/api/actions/mass-update-org-sites")
                               (mock/content-type "application/json")
                               (mock/body (test-utils/->json payload))
                               (test-utils/token-header token)))
          saved (core/get-sports-site (test-db) owned1)]
      (is (= 200 (:status resp)))
      (is (= "new@org.fi" (:email saved)) "contact update applied")
      (is (= "Newer DB Name" (:name saved)) "newer DB content preserved, ES copy not clobbered")
      (is (= [grantee] (:edit-grants saved)) "newer DB edit-grants preserved")
      (is (= (str org-id) (some-> saved :owner-org-id str)) "ownership preserved")
      (is (pos? (compare (:event-date saved) (:event-date newer)))
          "bulk update stamps a fresh event-date, not a reused one")
      (is (= "Newer DB Name" (:name (core/get-sports-site2 (test-search) owned1 :none)))
          "ES is reindexed from what was actually written to the DB"))))

(comment
  (clojure.test/run-test-var #'list-org-editable-sites-test)
  (clojure.test/run-test-var #'mass-update-org-editor-success-test)
  (clojure.test/run-test-var #'non-editor-denied-test))
