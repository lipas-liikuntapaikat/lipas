(ns lipas.backend.site-notes-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lipas.backend.core :as core]
            [lipas.backend.jwt :as jwt]
            [lipas.backend.org :as backend-org]
            [lipas.test-utils :as test-utils]
            [lipas.utils :as utils]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [ring.mock.request :as mock]))

(defonce test-system (atom nil))

(let [{:keys [once each]} (test-utils/full-system-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn test-db [] (:lipas/db @test-system))
(defn test-search [] (:lipas/search @test-system))
(defn test-app [req] ((:lipas/app @test-system) req))

(defn- org! [access-level]
  (let [org-id (random-uuid)]
    (backend-org/create-org (test-db) {:id           org-id
                                       :name         (str "Notes Org " org-id)
                                       :access-level access-level})
    org-id))

(defn- site! [lipas-id owner-org-id]
  (let [admin (test-utils/gen-admin-user :db-component (test-db))
        site  (cond-> (-> (test-utils/gen-sports-site)
                          (assoc :lipas-id lipas-id
                                 :event-date (utils/timestamp)
                                 :status "active"))
                owner-org-id (assoc :owner-org-id (str owner-org-id)
                                    :owner "city"))]
    (core/upsert-sports-site!* (test-db) admin site)
    (core/index! (test-search) site true)
    lipas-id))

(defn- post [user path body]
  (let [resp (test-app (-> (mock/request :post (str "/api/actions/" path))
                           (mock/content-type "application/json")
                           (mock/body (test-utils/->json body))
                           (test-utils/token-header (jwt/create-token user))))]
    (assoc resp :json (test-utils/safe-parse-json resp))))

(deftest notes-roundtrip-test
  (let [org-id   (org! "org-management")
        lipas-id (site! 9996001 org-id)
        member   (test-utils/gen-org-user org-id :db-component (test-db))
        outsider (test-utils/gen-regular-user :db-component (test-db))
        admin    (test-utils/gen-admin-user :db-component (test-db))
        created  (post member "create-site-note"
                       {:org-id org-id :lipas-id lipas-id :body "  Avain on vahtimestarilla  "})
        note     (:json created)]

    (testing "a member writes a note on a site the org owns"
      (is (= 200 (:status created)))
      (is (= "Avain on vahtimestarilla" (:body note)) "body is trimmed")
      (is (= "active" (:status note)))
      (is (seq (:author-name note))))

    (testing "members and LIPAS admins see it on the site; outsiders don't"
      (is (= [(:id note)] (map :id (:json (post member "get-site-notes" {:lipas-id lipas-id})))))
      (is (= [(:id note)] (map :id (:json (post admin "get-site-notes" {:lipas-id lipas-id})))))
      (is (= [] (:json (post outsider "get-site-notes" {:lipas-id lipas-id})))))

    (testing "the org list carries the site name and defaults to active notes"
      (let [notes (:json (post member "get-org-site-notes" {:org-id org-id}))]
        (is (= [(:id note)] (map :id notes)))
        (is (some? (:site-name (first notes))))))

    (testing "outsiders can't write or change notes"
      (is (= 403 (:status (post outsider "create-site-note"
                                {:org-id org-id :lipas-id lipas-id :body "x"}))))
      (is (= 403 (:status (post outsider "set-site-note-status"
                                {:note-id (:id note) :status "archived"})))))

    (testing "archiving takes it out of the active list but keeps it findable"
      (is (= "archived" (:status (:json (post member "set-site-note-status"
                                              {:note-id (:id note) :status "archived"})))))
      (is (= [] (:json (post member "get-org-site-notes" {:org-id org-id}))))
      (is (= [(:id note)]
             (map :id (:json (post member "get-org-site-notes"
                                   {:org-id org-id :statuses ["archived"]})))))
      (is (= "archived" (:status (first (:json (post member "get-site-notes"
                                                     {:lipas-id lipas-id})))))))

    (testing "an archived note can be restored, and archiving twice conflicts"
      (is (= "active" (:status (:json (post member "set-site-note-status"
                                            {:note-id (:id note) :status "active"})))))
      (is (= [(:id note)] (map :id (:json (post member "get-org-site-notes" {:org-id org-id})))))
      (is (= 200 (:status (post member "set-site-note-status"
                                {:note-id (:id note) :status "archived"}))))
      (is (= 409 (:status (post member "set-site-note-status"
                                {:note-id (:id note) :status "archived"})))))

    (testing "there is no delete: the API rejects it"
      (is (= 400 (:status (post member "set-site-note-status"
                                {:note-id (:id note) :status "deleted"})))))

    (testing "every status change is kept as history"
      (is (= 1 (:count (jdbc/execute-one! (test-db) ["SELECT count(*) FROM site_note"]))))
      (is (= ["archived" "active" "archived"]
             (map :status (jdbc/execute! (test-db)
                                         ["SELECT status FROM site_note_status
                                           WHERE note_id = ? ORDER BY created_at"
                                          (parse-uuid (str (:id note)))]
                                         {:builder-fn rs/as-unqualified-maps})))))))

(deftest notes-are-append-only-test
  (let [org-id   (org! "org-management")
        lipas-id (site! 9996002 org-id)
        member   (test-utils/gen-org-user org-id :db-component (test-db))
        note-id  (-> (post member "create-site-note" {:org-id org-id :lipas-id lipas-id :body "x"})
                     :json :id str parse-uuid)]
    (is (thrown-with-msg? Exception #"append-only"
                          (jdbc/execute! (test-db) ["UPDATE site_note SET body = 'y' WHERE id = ?" note-id])))
    (is (thrown-with-msg? Exception #"append-only"
                          (jdbc/execute! (test-db) ["DELETE FROM site_note WHERE id = ?" note-id])))))

(deftest notes-scoping-test
  (let [org-id    (org! "org-management")
        other-org (org! "org-management")
        ptv-org   (org! "ptv-only")
        owned     (site! 9996003 org-id)
        foreign   (site! 9996004 other-org)
        ptv-site  (site! 9996005 ptv-org)
        member    (test-utils/gen-org-user org-id :db-component (test-db))
        ptv-user  (test-utils/gen-org-user ptv-org :db-component (test-db))
        admin     (test-utils/gen-admin-user :db-component (test-db))]

    (testing "notes only go on sites the org owns"
      (is (= 200 (:status (post member "create-site-note" {:org-id org-id :lipas-id owned :body "x"}))))
      (is (= 403 (:status (post member "create-site-note" {:org-id org-id :lipas-id foreign :body "x"}))))
      (is (= 403 (:status (post member "create-site-note" {:org-id other-org :lipas-id foreign :body "x"})))
          "not a member of the other org")
      (is (= 403 (:status (post admin "create-site-note" {:org-id org-id :lipas-id foreign :body "x"})))
          "even an admin writes only as the owning org"))

    (testing "PTV-only orgs get no notes"
      (is (= 403 (:status (post ptv-user "create-site-note" {:org-id ptv-org :lipas-id ptv-site :body "x"}))))
      (is (= 403 (:status (post ptv-user "get-org-site-notes" {:org-id ptv-org})))))

    (testing "blank notes are rejected"
      (is (= 400 (:status (post member "create-site-note" {:org-id org-id :lipas-id owned :body "   "})))))))
