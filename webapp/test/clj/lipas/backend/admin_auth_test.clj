(ns lipas.backend.admin-auth-test
  "HTTP-level authorization regression tests for the administrative endpoints:
   user administration, org administration, the help CMS and LOI writes.

   The gates live in route data (`:require-privilege`) and are enforced by
   `lipas.backend.middleware/privilege-middleware`. `lipas.backend.route-auth-test`
   proves from the route data that each of these routes declares *some* auth
   decision; this namespace proves from the outside, through the real router,
   that the declaration actually rejects anonymous and under-privileged callers.

   Coercion (`coercion/coerce-request-middleware`) runs BEFORE
   privilege-middleware, so every body below has to satisfy its route's
   `:parameters` schema — otherwise the endpoint would answer 400 and the test
   would pass without ever reaching the gate it claims to test.
   `bodies-clear-coercion-test` is the control for exactly that."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lipas.backend.jwt :as jwt]
            [lipas.backend.org :as backend-org]
            [lipas.test-utils :as tu]
            [ring.mock.request :as mock]))

;;; Test system setup ;;;

(defonce test-system (atom nil))

(let [{:keys [once each]} (tu/full-system-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn test-db [] (:lipas/db @test-system))
(defn test-app [req] ((:lipas/app @test-system) req))

;;; Helpers ;;;

(defn- create-org!
  "Persists a minimal org and returns its id."
  []
  (let [id (java.util.UUID/randomUUID)]
    (backend-org/create-org (test-db)
                            {:id id
                             :name (str "Auth Test Org " id)
                             :data {}
                             :ptv-data {:org-id nil
                                        :city-codes [91]
                                        :owners ["city"]
                                        :supported-languages ["fi"]}})
    id))

(defn- call
  "Issues `method` `path` with an optional JSON body and bearer token."
  [{:keys [method path body]} token]
  (test-app (cond-> (mock/request method path)
              body (-> (mock/content-type "application/json")
                       (mock/body (tu/->json body)))
              token (tu/token-header token))))

;;; Endpoint table ;;;

(defn- admin-endpoints
  "Every admin endpoint under test, with the privilege its route data requires
   and a body that satisfies its `:parameters` schema.

   `org-id` must be a live org: the org-scoped entries take their role context
   from the body, and a body that fails coercion would 400 before the gate."
  [org-id]
  (let [uuid (str (java.util.UUID/randomUUID))]
    [;; --- User administration (:users/manage) ---------------------------
     {:method :get
      :path "/api/users"
      :privilege :users/manage}

     {:method :post
      :path "/api/actions/gdpr-remove-user"
      :privilege :users/manage
      ;; This route declares no :parameters, so nothing coerces the body.
      :body {:email "nobody@example.invalid"}}

     ;; --- Org administration, LIPAS-admin only (:org/admin) -------------
     {:method :post
      :path "/api/actions/get-all-orgs"
      :privilege :org/admin}

     {:method :post
      :path "/api/actions/list-org-takeover-requests"
      :privilege :org/admin
      :body {:status "requested"}}

     {:method :post
      :path "/api/actions/approve-org-takeover"
      :privilege :org/admin
      :body {:request-id uuid}}

     {:method :post
      :path "/api/actions/deny-org-takeover"
      :privilege :org/admin
      :body {:request-id uuid}}

     ;; Success cases live in lipas.backend.org-test; only the rejections
     ;; are pinned here.
     {:method :post
      :path "/api/actions/create-org"
      :privilege :org/admin
      :body {:name "Auth Test New Org"}}

     ;; --- Org-scoped management (:org/manage on the body's :org-id) -----
     {:method :post
      :path "/api/actions/remove-org-member"
      :privilege :org/manage
      :org-scoped? true
      :body {:org-id (str org-id) :user-id uuid}}

     {:method :post
      :path "/api/actions/revoke-site-edit"
      :privilege :org/manage
      :org-scoped? true
      :body {:org-id (str org-id)
             :lipas-id 123456
             :grantee-org-id (str (java.util.UUID/randomUUID))}}

     ;; --- Help CMS (:help/manage) ---------------------------------------
     {:method :post
      :path "/api/actions/save-help-data"
      :privilege :help/manage
      :body {:locale "fi" :data []}}

     {:method :post
      :path "/api/actions/save-help-draft"
      :privilege :help/manage
      :body {:locale "fi" :data []}}

     {:method :post
      :path "/api/actions/get-help-versions"
      :privilege :help/manage
      :body {:locale "fi"}}

     {:method :post
      :path "/api/actions/get-help-version"
      :privilege :help/manage
      :body {:id uuid}}

     ;; --- LOI writes (:loi/create-edit) ---------------------------------
     {:method :post
      :path "/api/actions/save-loi"
      :privilege :loi/create-edit
      :body {:id uuid
             :event-date "2026-01-01T00:00:00.000Z"
             :status "active"
             :loi-category "outdoor-recreation-facilities"
             :loi-type "information-board"
             :geometries {:type "FeatureCollection"
                          :features [{:type "Feature"
                                      :geometry {:type "Point"
                                                 :coordinates [25.0 60.2]}}]}}}]))

;;; Tests ;;;

(deftest admin-endpoints-reject-anonymous-callers-test
  (testing "Every admin endpoint answers 401 without a token"
    (let [org-id (create-org!)]
      (doseq [{:keys [path privilege] :as endpoint} (admin-endpoints org-id)]
        (let [resp (call endpoint nil)]
          (is (= 401 (:status resp))
              (str path " (" privilege ") must reject an anonymous caller with"
                   " 401 — a 400 would mean the request body no longer satisfies"
                   " the route schema and the auth gate was never reached")))))))

(deftest admin-endpoints-reject-under-privileged-callers-test
  (testing "Every admin endpoint answers 403 for a signed-in user without the privilege"
    (let [org-id (create-org!)
          token (jwt/create-token (tu/gen-regular-user :db-component (test-db)))]
      (doseq [{:keys [path privilege] :as endpoint} (admin-endpoints org-id)]
        (let [resp (call endpoint token)]
          (is (= 403 (:status resp))
              (str path " must reject a user lacking " privilege " with 403"
                   " — a 400 would mean the request body no longer satisfies"
                   " the route schema and the auth gate was never reached")))))))

(deftest org-scoped-endpoints-reject-foreign-org-admin-test
  (testing "Org-scoped management is scoped to the caller's OWN org"
    ;; A plain-regular-user 403 (above) would also pass if the gate were the
    ;; global :org/admin instead of the body-scoped :org/manage. This pins the
    ;; scoping: an org-admin of another org holds :org/manage, just not here.
    (let [own-org (create-org!)
          other-org (create-org!)
          token (jwt/create-token (tu/gen-org-admin-user other-org
                                                         :db-component (test-db)))]
      (doseq [{:keys [path] :as endpoint} (->> (admin-endpoints own-org)
                                               (filter :org-scoped?))]
        (let [resp (call endpoint token)]
          (is (= 403 (:status resp))
              (str path " must reject an org-admin of a DIFFERENT org with 403")))))))

(deftest bodies-clear-coercion-test
  (testing "Each test body satisfies its route schema, so the 401/403 above are real"
    ;; The control for the two tests above: they assert 401/403, and coercion
    ;; answers 400 before privilege-middleware runs, so a body that drifted out
    ;; of sync with its route schema would make them pass while proving nothing.
    ;; With a LIPAS admin token every one of these must get past coercion.
    ;; Only the status is asserted — most of these hit missing fixtures behind
    ;; the gate (unknown request-id, unknown user), which is fine; what matters
    ;; is that the failure is never 400/401/403.
    (let [org-id (create-org!)
          token (jwt/create-token (tu/gen-admin-user :db-component (test-db)))]
      (doseq [{:keys [path] :as endpoint} (admin-endpoints org-id)]
        (let [resp (call endpoint token)]
          (is (not (contains? #{400 401 403} (:status resp)))
              (str path " with a LIPAS admin token must clear coercion and the"
                   " gate — 400 means the test body no longer matches the route"
                   " schema, 401/403 means the gate rejects a caller that should"
                   " pass. Got " (:status resp))))))))

;;; upload-utp-image — authenticated, but NOT privilege-gated ;;;

(def ^:private multipart-boundary "lipasAuthTestBoundary")

(defn- utp-image-request
  "A real multipart/form-data upload request.

   The route declares `:parameters {:multipart {:file ...}}` and mounts
   `multipart/multipart-middleware` at route level, i.e. AFTER the global
   `coerce-request-middleware`. A request without a well-formed multipart body
   therefore fails coercion with 400 before the auth middleware ever runs, so
   this test has to send the real thing to observe the 401."
  []
  (let [body (str "--" multipart-boundary "\r\n"
                  "Content-Disposition: form-data; name=\"lipas-id\"\r\n\r\n"
                  "123456\r\n"
                  "--" multipart-boundary "\r\n"
                  "Content-Disposition: form-data; name=\"file\";"
                  " filename=\"test.png\"\r\n"
                  "Content-Type: image/png\r\n\r\n"
                  "not-really-a-png\r\n"
                  "--" multipart-boundary "--\r\n")]
    (-> (mock/request :post "/api/actions/upload-utp-image")
        (mock/content-type (str "multipart/form-data; boundary=" multipart-boundary))
        (mock/body body))))

(deftest upload-utp-image-requires-authentication-test
  (testing "upload-utp-image rejects an anonymous caller with 401"
    ;; NOTE: no 403 case on purpose. This route mounts token-auth + auth
    ;; manually and declares NO :require-privilege, so ANY signed-in user may
    ;; upload an image (see the "TODO: role, :activity/edit?" in the route).
    ;; That missing privilege gate is tracked separately as finding M3; when it
    ;; is added, move this endpoint into the table above.
    (let [resp (test-app (utp-image-request))]
      (is (= 401 (:status resp))
          "upload-utp-image must reject an anonymous caller with 401"))))

(comment
  (clojure.test/run-tests *ns*))
