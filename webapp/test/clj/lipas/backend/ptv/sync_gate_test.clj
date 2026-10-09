(ns lipas.backend.ptv.sync-gate-test
  "The PTV part of a user-facing site save (`core/check-ptv-save!`), PTV org
   listing by PTV rights (`/actions/get-ptv-orgs`) and the PTV member
   suggestions.

   Background: sites were saved with `:ptv {:sync-enabled true}` but no org-id
   or texts by a city-scoped ptv-manager who wasn't an org member. The frontend
   listed orgs by membership, so none resolved; nothing validated `:ptv` on
   save; `sync-ptv!` skipped silently. Nothing reached PTV and nobody was told.

   PTV is never called: `sync-ptv!` is stubbed where a test needs a sync to
   'succeed', and the real one is only run for its LIPAS-side guards, which
   throw before any PTV request."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lipas.backend.core :as core]
            [lipas.backend.jwt :as jwt]
            [lipas.backend.org :as backend-org]
            [lipas.backend.ptv.core :as ptv-core]
            [lipas.test-utils :as tu]
            [ring.mock.request :as mock]))

(defonce test-system (atom nil))

(let [{:keys [once each]} (tu/full-system-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn test-db [] (:lipas/db @test-system))
(defn test-app [req] ((:lipas/app @test-system) req))

(def ^:private own-city 91)
(def ^:private other-city 179) ; no PTV org covers it
(def ^:private ptv-org-a "3d1759a2-e47a-4947-9a31-cab1c1e2512b")
(def ^:private ptv-org-b "92374b0f-7d3c-4017-858e-666ee3ca2761")

(def ^:private texts
  {:summary {:fi "Lyhyt kuvaus paikasta"}
   :description {:fi "Pidempi kuvaus paikasta"}})

(defn- seed-org! [ptv-org-id city-codes]
  (backend-org/create-org
    (test-db)
    {:id (random-uuid)
     :name (str "Sync Gate Org " (rand-int 1000000))
     :data {}
     :ptv-data {:org-id ptv-org-id
                :city-codes city-codes
                :owners ["city" "city-main-owner"]
                :supported-languages ["fi"]}}))

(defn- seed-site!
  "An active, PTV-candidate site in `city-code`, saved by an admin."
  ([city-code] (seed-site! city-code nil))
  ([city-code ptv]
   (core/upsert-sports-site!*
     (test-db) (tu/gen-admin-user :db-component (test-db))
     (cond-> {:status "active"
              :event-date "2026-01-01T00:00:00.000Z"
              :name (str "Gate test site " (rand-int 1000000))
              :owner "city"
              :admin "city-sports"
              :type {:type-code 2510}
              :location {:city {:city-code city-code}
                         :address "Testitie 1"
                         :postal-code "00100"
                         :postal-office "Helsinki"
                         :geometries {:type "FeatureCollection"
                                      :features [{:type "Feature"
                                                  :geometry {:type "Point"
                                                             :coordinates [24.94 60.17]}}]}}}
       ptv (assoc :ptv ptv)))))

(defn- user-with-roles [roles]
  (tu/gen-user {:db? true :db-component (test-db) :admin? false
                :permissions {:roles roles}}))

(defn- ptv-manager [city-code]
  ;; Legacy plane: a direct, city-scoped role. Site editing comes from
  ;; city-manager; PTV rights from ptv-manager. No org membership.
  (user-with-roles [{:role "city-manager" :city-code [city-code]}
                    {:role "ptv-manager" :city-code [city-code]}]))

(defn- city-manager [city-code]
  (user-with-roles [{:role "city-manager" :city-code [city-code]}]))

(defn- post [path body user]
  (let [resp (test-app (-> (mock/request :post path)
                           (mock/content-type "application/json")
                           (mock/body (tu/->json body))
                           (tu/token-header (jwt/create-token user))))]
    (assoc resp :parsed (try (tu/<-json (:body resp)) (catch Exception _ nil)))))

(defn- save! [user site]
  (post "/api/sports-sites" (assoc site :event-date (str (java.time.Instant/now))) user))

(def ^:private sync-calls (atom []))

(defn- with-sync-stub
  "Replaces `ptv-core/sync-ptv!` (called via `resolve` from core) with a
   recorder that 'succeeds' without touching PTV."
  [f]
  (reset! sync-calls [])
  (with-redefs [ptv-core/sync-ptv! (fn [_tx _search _ptv _user args]
                                     (swap! sync-calls conj args)
                                     {:ptv (:ptv args)
                                      :event-date (-> args :sports-site :event-date)})]
    (f)))

(defn- current [lipas-id]
  (core/get-sports-site (test-db) lipas-id))

;;; Save gate ;;;

(deftest legacy-ptv-manager-enables-sync-test
  (testing "a non-member, city-scoped ptv-manager enables sync: org-id is derived server-side"
    (seed-org! ptv-org-a [own-city])
    (with-sync-stub
      (fn []
        (let [site (seed-site! own-city)
              resp (save! (ptv-manager own-city)
                          (assoc site :ptv (merge {:sync-enabled true} texts)))]
          (is (= 201 (:status resp)))
          (is (= ptv-org-a (get-in (current (:lipas-id site)) [:ptv :org-id])))
          (is (= [ptv-org-a] (map :org-id @sync-calls)) "sync ran under the derived org"))))))

(deftest sync-on-without-texts-is-rejected-test
  (seed-org! ptv-org-a [own-city])
  (with-sync-stub
    (fn []
      (let [site (seed-site! own-city)
            resp (save! (ptv-manager own-city) (assoc site :ptv {:sync-enabled true}))]
        (is (= 400 (:status resp)))
        (is (= "ptv-sync-blocked" (get-in resp [:parsed :type])))
        (is (= ["ptv/missing-texts"] (get-in resp [:parsed :blockers])))
        (is (nil? (:ptv (current (:lipas-id site)))) "nothing was saved")
        (is (empty? @sync-calls))))))

(deftest sync-on-without-any-org-is-rejected-test
  (testing "no PTV org covers the city → no org can be derived"
    (with-sync-stub
      (fn []
        (let [site (seed-site! other-city)
              resp (save! (ptv-manager other-city)
                          (assoc site :ptv (merge {:sync-enabled true} texts)))]
          (is (= 400 (:status resp)))
          (is (= ["ptv/no-org"] (get-in resp [:parsed :blockers])))
          (is (empty? @sync-calls)))))))

(deftest foreign-org-id-is-rejected-test
  (testing "the client can't point a site at an org that doesn't cover it"
    (seed-org! ptv-org-a [own-city])
    (seed-org! ptv-org-b [other-city])
    (with-sync-stub
      (fn []
        (let [site (seed-site! own-city)
              resp (save! (ptv-manager own-city)
                          (assoc site :ptv (merge {:sync-enabled true :org-id ptv-org-b} texts)))]
          (is (= 400 (:status resp)))
          (is (= "ptv-org-mismatch" (get-in resp [:parsed :type])))
          (is (empty? @sync-calls)))))))

(deftest ptv-edits-need-ptv-rights-test
  (seed-org! ptv-org-a [own-city])
  (with-sync-stub
    (fn []
      (testing "a site editor without PTV rights can't change :ptv (used to trigger PTV writes)"
        (let [site (seed-site! own-city)
              resp (save! (city-manager own-city)
                          (assoc site :ptv (merge {:sync-enabled true :org-id ptv-org-a} texts)))]
          (is (= 403 (:status resp)))
          (is (empty? @sync-calls))))
      (testing "...but can keep editing an integrated site; it still re-syncs"
        (let [site (seed-site! own-city (merge {:sync-enabled true :org-id ptv-org-a
                                                :service-ids [] :service-channel-ids []}
                                               texts))
              resp (save! (city-manager own-city) (assoc site :name "Uusi nimi"))]
          (is (= 201 (:status resp)))
          (is (= 1 (count @sync-calls))))))))

(deftest untouched-unsyncable-ptv-doesnt-block-other-edits-test
  (testing "a pre-gate broken :ptv (sync on, no org/texts) doesn't block a name edit,
            and the sync failure is stored on the site instead of being skipped silently"
    (seed-org! ptv-org-a [own-city])
    ;; real sync-ptv!: its guard throws before any PTV request
    (let [site (seed-site! own-city {:sync-enabled true})
          resp (save! (city-manager own-city) (assoc site :name "Uusi nimi"))
          stored (current (:lipas-id site))]
      (is (= 201 (:status resp)))
      (is (= "Uusi nimi" (:name stored)))
      (is (= ["ptv/no-org"] (map name (get-in stored [:ptv :error :blockers])))))))

;;; Org listing ;;;

(deftest get-ptv-orgs-test
  (let [org-a (seed-org! ptv-org-a [own-city])
        org-b (seed-org! ptv-org-b [other-city])
        ids (fn [resp] (set (map :id (:parsed resp))))]
    (testing "a non-member ptv-manager gets the org covering their city — and only it"
      (let [resp (post "/api/actions/get-ptv-orgs" {} (ptv-manager own-city))]
        (is (= 200 (:status resp)))
        (is (= #{(str (:id org-a))} (ids resp)))))
    (testing "admins get every PTV org"
      (let [resp (post "/api/actions/get-ptv-orgs" {}
                       (tu/gen-admin-user :db-component (test-db)))]
        (is (every? (ids resp) [(str (:id org-a)) (str (:id org-b))]))))
    (testing "no secrets or member lists leave the server"
      (let [resp (post "/api/actions/get-ptv-orgs" {} (ptv-manager own-city))
            org (first (:parsed resp))]
        (is (= #{:id :name :ptv-data} (set (keys org))))
        (is (not (contains? (:ptv-data org) :test-credentials)))))
    (testing "users without PTV rights are refused"
      (is (= 403 (:status (post "/api/actions/get-ptv-orgs" {} (city-manager own-city))))))))

;;; Member suggestions ;;;

(deftest ptv-member-suggestions-test
  (let [org (seed-org! ptv-org-a [own-city])
        org-id (str (:id org))
        manager (ptv-manager own-city)
        admin (tu/gen-admin-user :db-component (test-db))
        suggested (fn [] (set (map :user-id (:parsed (post "/api/actions/get-ptv-member-suggestions"
                                                           {:org-id org-id} admin)))))]
    (testing "the non-member manager is suggested, without an email"
      (is (contains? (suggested) (str (:id manager))))
      (is (not-any? :email (:parsed (post "/api/actions/get-ptv-member-suggestions"
                                          {:org-id org-id} admin)))))
    (testing "the admin overview lists them with their org"
      (let [rows (:parsed (post "/api/actions/get-ptv-managers-outside-orgs" {} admin))
            row (some #(when (= (str (:id manager)) (:user-id %)) %) rows)]
        (is (= org-id (get-in row [:org :id])))))
    (testing "adding makes them a plain member and drops them from the suggestions"
      (is (= 200 (:status (post "/api/actions/add-suggested-ptv-member"
                                {:org-id org-id :user-id (str (:id manager))} admin))))
      (let [member (some #(when (= (str (:id manager)) (:user-id %)) %)
                         (:members (backend-org/get-org (test-db) (:id org))))]
        (is (some? member))
        (is (empty? (:roles member))))
      (is (not (contains? (suggested) (str (:id manager))))))
    (testing "only suggested accounts can be added by id"
      (let [stranger (city-manager other-city)]
        (is (= 400 (:status (post "/api/actions/add-suggested-ptv-member"
                                  {:org-id org-id :user-id (str (:id stranger))} admin))))))
    (testing "non-admins of the org can't list or add"
      (is (= 403 (:status (post "/api/actions/get-ptv-member-suggestions"
                                {:org-id org-id} manager)))))))
