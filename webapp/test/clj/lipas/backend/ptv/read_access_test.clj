(ns lipas.backend.ptv.read-access-test
  "HTTP-level authorization tests for the ORG-SCOPED PTV read endpoints.

   These routes are addressed by an organisation — `:org-id` (a PTV
   organisation UUID or the LIPAS org uuid, depending on the endpoint) or a
   `:lipas-id` — but the gate used to ignore what the request asked for: it only
   checked whether the caller held `:ptv/manage` for ANY city it happened to
   have. A ptv-manager scoped to one municipality could therefore read every
   other organisation's PTV data. `lipas.backend.ptv.handler` now resolves the
   named organisation and requires `:ptv/manage` for one of ITS municipalities
   (see the `PTV read access` comment there for the data model).

   Three things this namespace has to get right:

   - The POSITIVE CONTROL (`ptv-manager-for-own-city-can-read-test`) is the most
     important test here. A scoping fix that accidentally denies everyone passes
     every negative test in this file while breaking PTV in production. The
     likeliest cause of exactly that is a type mismatch on `:city-code`: role
     contexts arrive as `Long` (JWT/JSON via `roles/conform-roles`) while an
     org's `[:ptv-data :city-codes]` arrives as `Integer` (jsonb). Those two
     compare fine, but a string never matches a number, so
     `city-code-types-do-not-silently-deny-test` pins the normalisation too.

   - Request bodies must satisfy each route's `:parameters` schema. Coercion
     runs BEFORE privilege-middleware, so a sloppy body yields 400 and a
     401/403 assertion would pass for the wrong reason, proving nothing. The
     positive control doubles as the guard for that: with an authorised token
     the same bodies must return 200.

   - No request may reach the real PTV API. `with-ptv-stub` replaces the
     `lipas.backend.ptv.integration` entry points these handlers use with a
     recorder, and the unauthorised cases assert it stayed untouched — so a
     removed gate shows up as a recorded call, not as silent traffic to PTV."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lipas.backend.core :as core]
            [lipas.backend.jwt :as jwt]
            [lipas.backend.org :as backend-org]
            [lipas.backend.ptv.integration :as ptv-integration]
            [lipas.test-utils :as tu]
            [ring.mock.request :as mock]))

(defonce test-system (atom nil))

(let [{:keys [once each]} (tu/full-system-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn test-db [] (:lipas/db @test-system))
(defn test-app [req] ((:lipas/app @test-system) req))

;;; PTV API boundary ;;;

(def ^:private ptv-calls
  "Every PTV API call attempted while the stub is armed."
  (atom []))

(defn- with-ptv-stub
  "Runs `f` with the `lipas.backend.ptv.integration` reads these routes use
   replaced by a recorder. Keeps the suite offline and turns a missing gate into
   a recorded call instead of a live PTV request."
  [f]
  (reset! ptv-calls [])
  (let [record (fn [name*]
                 (fn [& args]
                   (swap! ptv-calls conj {:fn name* :args (vec (rest args))})
                   {:itemList []}))]
    (with-redefs [ptv-integration/get-org (record :get-org)
                  ptv-integration/get-org-services (record :get-org-services)
                  ptv-integration/get-org-service-channels (record :get-org-service-channels)
                  ptv-integration/get-org-service-collections (record :get-org-service-collections)]
      (f))))

;;; Fixtures ;;;

(def ^:private own-city 91) ; Helsinki — the org's municipality
(def ^:private other-city 179) ; Jyväskylä — a municipality the org does NOT cover

;; PTV uses UUIDs for organisations and service channels.
(def ^:private ptv-org-id "3d1759a2-e47a-4947-9a31-cab1c1e2512b")
(def ^:private unmapped-ptv-org-id "b105f00d-dead-4bee-8fff-000000000000")
(def ^:private channel-id "a1b2c3d4-0001-4abc-9def-000000000001")

(defn- seed-org!
  "Seeds a LIPAS org whose PTV config claims `ptv-org-id*` and `city-codes`.
   Returns the org map (with `:id`, the LIPAS org uuid)."
  [ptv-org-id* city-codes]
  (backend-org/create-org
    (test-db)
    {:id (random-uuid)
     :name (str "PTV Read Access Org " (rand-int 1000000))
     :data {}
     :ptv-data {:org-id ptv-org-id*
                :city-codes city-codes
                :owners ["city"]
                :supported-languages ["fi"]}}))

(defn- seed-site!
  "Seeds a PTV-synced sports-site in `own-city`, bound to `channel-id`."
  [user]
  (core/upsert-sports-site!*
    (test-db) user
    {:status "active"
     :event-date "2026-01-01T00:00:00.000Z"
     :name "Oulunkylän tekojää"
     :owner "city"
     :admin "city-sports"
     :type {:type-code 2510}
     :location {:city {:city-code own-city}
                :address "Käärmetie 8"
                :postal-code "00640"
                :postal-office "Helsinki"
                :geometries {:type "FeatureCollection"
                             :features [{:type "Feature"
                                         :geometry {:type "Point"
                                                    :coordinates [24.9612 60.2289]}}]}}
     :ptv {:org-id ptv-org-id
           :sync-enabled true
           :summary {:fi "Kuvaus"}
           :description {:fi "Pidempi kuvaus"}
           :service-ids []
           :service-channel-ids [channel-id]}}))

;;; Endpoint tables ;;;

(defn- read-endpoints
  "Every org-scoped PTV read endpoint, with a body that names the seeded org /
   site and satisfies the route's `:parameters` schema.

   Note the two `:org-id` conventions: the four `fetch-ptv-*` routes take the
   PTV organisation UUID, while `fetch-ptv-service-audits` takes the LIPAS org
   uuid (same as the audit-notification endpoints)."
  [{:keys [lipas-org-id lipas-id]}]
  [{:path "/api/actions/fetch-ptv-org"
    :body {:org-id ptv-org-id}}
   {:path "/api/actions/fetch-ptv-services"
    :body {:org-id ptv-org-id}}
   {:path "/api/actions/fetch-ptv-service-channels"
    :body {:org-id ptv-org-id}}
   {:path "/api/actions/fetch-ptv-service-collections"
    :body {:org-id ptv-org-id}}
   {:path "/api/actions/fetch-ptv-service-audits"
    :body {:org-id (str lipas-org-id)}}
   {:path "/api/actions/check-ptv-service-channel-link"
    :body {:lipas-id lipas-id :service-channel-id channel-id}}])

(defn- unmapped-endpoints
  "The same endpoints, each naming a target that resolves to no LIPAS org / no
   sports-site. Schema-valid, so these reach the gate and must be denied rather
   than falling through to a permissive branch."
  []
  [{:path "/api/actions/fetch-ptv-org"
    :body {:org-id unmapped-ptv-org-id}
    :why "a PTV org id no LIPAS org claims"}
   {:path "/api/actions/fetch-ptv-services"
    :body {:org-id unmapped-ptv-org-id}
    :why "a PTV org id no LIPAS org claims"}
   {:path "/api/actions/fetch-ptv-service-channels"
    :body {:org-id unmapped-ptv-org-id}
    :why "a PTV org id no LIPAS org claims"}
   {:path "/api/actions/fetch-ptv-service-collections"
    :body {:org-id unmapped-ptv-org-id}
    :why "a PTV org id no LIPAS org claims"}
   {:path "/api/actions/fetch-ptv-service-audits"
    :body {:org-id (str (random-uuid))}
    :why "a LIPAS org uuid that does not exist"}
   {:path "/api/actions/check-ptv-service-channel-link"
    :body {:lipas-id 999999 :service-channel-id channel-id}
    :why "a lipas-id that does not exist"}])

;;; Callers ;;;

(defn- ptv-manager
  "A user whose only privilege is `:ptv/manage` in `city-code`, via the
   city-scoped `:ptv-manager` role."
  [city-code]
  (tu/gen-user {:db? true
                :db-component (test-db)
                :admin? false
                :permissions {:roles [{:role "ptv-manager"
                                       :city-code [city-code]}]}}))

(defn- post*
  "POSTs `body` as JSON to `path`, with a bearer token when one is given."
  [path body token]
  (test-app (cond-> (-> (mock/request :post path)
                        (mock/content-type "application/json")
                        (mock/body (tu/->json body)))
              token (tu/token-header token))))

;;; Tests ;;;

(deftest ptv-manager-for-own-city-can-read-test
  (testing "A ptv-manager for the org's own municipality reads every endpoint"
    ;; THE POSITIVE CONTROL. Without it a deny-all regression would pass every
    ;; other test in this namespace. 200 (not merely "not 403") also proves the
    ;; bodies still satisfy the route schemas, so the negative tests below are
    ;; reaching the gate rather than dying in coercion.
    (let [org (seed-org! ptv-org-id [own-city])
          admin (tu/gen-admin-user :db-component (test-db))
          site (seed-site! admin)
          token (jwt/create-token (ptv-manager own-city))]
      (with-ptv-stub
        (fn []
          (doseq [{:keys [path body]} (read-endpoints {:lipas-org-id (:id org)
                                                       :lipas-id (:lipas-id site)})]
            (let [resp (post* path body token)]
              (is (= 200 (:status resp))
                  (str path " must serve a ptv-manager scoped to the org's own"
                       " municipality (403 here = the scoping fix denies the"
                       " very users PTV is for; 400 = the test body no longer"
                       " matches the route schema). Got " (:status resp))))))))))

(deftest ptv-manager-for-another-city-is-denied-test
  (testing "A ptv-manager for a DIFFERENT municipality gets 403 on every endpoint"
    ;; The finding itself: holding :ptv/manage somewhere must not grant reads of
    ;; another organisation's PTV data.
    (let [org (seed-org! ptv-org-id [own-city])
          admin (tu/gen-admin-user :db-component (test-db))
          site (seed-site! admin)
          token (jwt/create-token (ptv-manager other-city))]
      (with-ptv-stub
        (fn []
          (doseq [{:keys [path body]} (read-endpoints {:lipas-org-id (:id org)
                                                       :lipas-id (:lipas-id site)})]
            (let [resp (post* path body token)]
              (is (= 403 (:status resp))
                  (str path " must reject a ptv-manager scoped to city "
                       other-city " when the request names an org covering city "
                       own-city))))
          (is (empty? @ptv-calls)
              (str "No PTV API call may be attempted for a request that is not"
                   " authorized for the org. Attempted: " (pr-str @ptv-calls))))))))

(deftest ptv-auditor-can-read-any-org-test
  (testing "A :ptv/audit holder keeps global read — auditors review every org"
    (let [org (seed-org! ptv-org-id [other-city]) ; an org the auditor has no city for
          admin (tu/gen-admin-user :db-component (test-db))
          site (seed-site! admin)
          token (jwt/create-token (tu/gen-ptv-auditor :db-component (test-db)))]
      (with-ptv-stub
        (fn []
          (doseq [{:keys [path body]} (read-endpoints {:lipas-org-id (:id org)
                                                       :lipas-id (:lipas-id site)})]
            (let [resp (post* path body token)]
              (is (= 200 (:status resp))
                  (str path " must stay open to :ptv/audit regardless of city"
                       " scope. Got " (:status resp)))))
          ;; Auditors are also not required to name a resolvable org: the audit
          ;; privilege short-circuits before the org lookup.
          (doseq [{:keys [path body why]} (unmapped-endpoints)]
            (let [resp (post* path body token)]
              (is (not= 403 (:status resp))
                  (str path " with " why " must not 403 a :ptv/audit holder")))))))))

(deftest unmapped-target-is-denied-test
  (testing "A target that resolves to no LIPAS org / no site is denied, not allowed"
    ;; Guards against the permissive fallthrough: "we couldn't work out which
    ;; org this is, so let the ptv-manager through".
    (let [_org (seed-org! ptv-org-id [own-city])
          token (jwt/create-token (ptv-manager own-city))]
      (with-ptv-stub
        (fn []
          (doseq [{:keys [path body why]} (unmapped-endpoints)]
            (let [resp (post* path body token)]
              (is (= 403 (:status resp))
                  (str path " with " why " must be denied even for a caller"
                       " that legitimately manages another org"))))
          (is (empty? @ptv-calls)
              (str "No PTV API call may be attempted for an unresolvable target."
                   " Attempted: " (pr-str @ptv-calls))))))))

(deftest anonymous-callers-get-401-test
  (testing "Every endpoint answers 401 without a token"
    (let [org (seed-org! ptv-org-id [own-city])
          admin (tu/gen-admin-user :db-component (test-db))
          site (seed-site! admin)]
      (with-ptv-stub
        (fn []
          (doseq [{:keys [path body]} (read-endpoints {:lipas-org-id (:id org)
                                                       :lipas-id (:lipas-id site)})]
            (let [resp (post* path body nil)]
              (is (= 401 (:status resp))
                  (str path " must reject an anonymous caller with 401"
                       " (400 here would mean the body no longer satisfies the"
                       " route schema and the gate was never reached)"))))
          (is (empty? @ptv-calls)
              (str "No PTV API call may be attempted for an unauthenticated"
                   " request. Attempted: " (pr-str @ptv-calls))))))))

(deftest signed-in-user-without-ptv-privileges-is-denied-test
  (testing "A signed-in user with no PTV privilege at all gets 403 on every endpoint"
    (let [org (seed-org! ptv-org-id [own-city])
          admin (tu/gen-admin-user :db-component (test-db))
          site (seed-site! admin)
          token (jwt/create-token (tu/gen-user {:db? true
                                                :db-component (test-db)
                                                :admin? false}))]
      (with-ptv-stub
        (fn []
          (doseq [{:keys [path body]} (read-endpoints {:lipas-org-id (:id org)
                                                       :lipas-id (:lipas-id site)})]
            (let [resp (post* path body token)]
              (is (= 403 (:status resp))
                  (str path " must reject a user with no PTV privilege")))))))))

(deftest city-code-types-do-not-silently-deny-test
  (testing "City-code comparison survives the Integer/Long/string mismatch"
    ;; The deny-all trap. A role context's :city-code is a Long (JWT -> JSON ->
    ;; roles/conform-roles) while an org's [:ptv-data :city-codes] comes back
    ;; from jsonb as Integer; a string city-code (hand-edited or legacy org
    ;; document) would match NEITHER without normalisation. Any of those
    ;; mismatches silently turns the whole gate into a deny-all, which looks
    ;; like a passing test suite and a broken feature.
    (let [numeric-org (seed-org! "aaaaaaaa-0001-4aaa-8aaa-000000000001" [own-city])
          string-org (seed-org! "aaaaaaaa-0002-4aaa-8aaa-000000000002" [(str own-city)])
          foreign-org (seed-org! "aaaaaaaa-0003-4aaa-8aaa-000000000003" [other-city])
          token (jwt/create-token (ptv-manager own-city))
          status (fn [org]
                   (:status (post* "/api/actions/fetch-ptv-org"
                                   {:org-id (-> org :ptv-data :org-id)}
                                   token)))]
      (with-ptv-stub
        (fn []
          (is (= 200 (status numeric-org))
              (str "An Integer city-code from jsonb must match the Long city-code"
                   " a JWT role context carries"))
          (is (= 200 (status string-org))
              (str "A string city-code in the org document must be normalised;"
                   " a bare set lookup would never match and would deny a"
                   " legitimate ptv-manager"))
          ;; Proves the two assertions above are not vacuous (i.e. the gate is
          ;; not simply allowing everything).
          (is (= 403 (status foreign-org))
              "An org in another municipality must still be denied"))))))

(comment
  (clojure.test/run-tests *ns*))
