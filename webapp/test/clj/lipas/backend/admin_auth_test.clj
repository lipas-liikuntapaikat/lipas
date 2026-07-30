(ns lipas.backend.admin-auth-test
  "HTTP-level authorization regression tests for the administrative endpoints:
   user administration, org administration, the help CMS, LOI writes and the
   UTP CMS image upload.

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
            [lipas.integration.utp.cms :as cms]
            [lipas.test-utils :as tu]
            [ring.mock.request :as mock])
  (:import [java.io ByteArrayOutputStream]))

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

;;; upload-utp-image — privilege gate + file limits (finding M3) ;;;

;; This endpoint stays out of `admin-endpoints` above: `call` sends a JSON body
;; and this route only accepts multipart/form-data, so it needs its own request
;; builder and its own cases (content-type, size, magic bytes).
;;
;; It used to declare NO :require-privilege at all — it mounted token-auth +
;; auth manually, which authenticates but authorizes nothing, so ANY signed-in
;; user could push arbitrary files into the UTP CMS (finding M3). The gate is
;; `lipas.backend.handler/utp-image-upload-access?`: :activity/edit (UTP
;; activity content editor) OR :loi/create-edit (LOI content editor), the two
;; editors this single upload endpoint serves.

(def ^:private multipart-boundary "lipasAuthTestBoundary")

(def ^:private size-cap
  "The route's `:size` cap. One byte over this must be rejected by coercion."
  (* 20 1024 1024))

(defn- ascii ^bytes [^String s] (.getBytes s "US-ASCII"))

(defn- signature ^bytes [bs] (byte-array (map unchecked-byte bs)))

(defn- file-bytes
  "Raw bytes for the multipart file part.

   `:png` / `:jpeg` carry the real magic-byte signatures that
   `lipas.backend.core/upload-utp-image!` sniffs for. `:not-an-image` is the
   spoofing case: the request still declares an image content-type, only the
   bytes disagree. `:oversized-png` is a genuine PNG one byte over the cap."
  ^bytes [kind]
  (let [out (ByteArrayOutputStream.)]
    (case kind
      :png (do (.write out (signature [0x89 0x50 0x4E 0x47 0x0D 0x0A 0x1A 0x0A]))
               (.write out (byte-array 16)))
      :jpeg (do (.write out (signature [0xFF 0xD8 0xFF]))
                (.write out (byte-array 16)))
      :not-an-image (.write out (ascii "not-really-a-png"))
      :oversized-png (do (.write out (signature [0x89 0x50 0x4E 0x47 0x0D 0x0A 0x1A 0x0A]))
                         (.write out (byte-array (- (inc size-cap) 8)))))
    (.toByteArray out)))

(defn- utp-image-request
  "A real multipart/form-data upload request.

   The body has to be built as raw bytes, not a string: PNG/JPEG signatures are
   not valid UTF-8 and would be mangled on the way in.

   `multipart/multipart-middleware` both parses the body AND coerces the route's
   `:parameters {:multipart ...}`, and it sits inside the route middleware
   vector, so a request without a well-formed multipart body answers 400 rather
   than 401/403. Every case here therefore sends the real thing."
  [& {:keys [filename content-type kind token]
      :or {filename "test.png" content-type "image/png" kind :png}}]
  (let [out (ByteArrayOutputStream.)]
    (.write out (ascii (str "--" multipart-boundary "\r\n"
                            "Content-Disposition: form-data; name=\"lipas-id\"\r\n\r\n"
                            "123456\r\n"
                            "--" multipart-boundary "\r\n"
                            "Content-Disposition: form-data; name=\"file\";"
                            " filename=\"" filename "\"\r\n"
                            "Content-Type: " content-type "\r\n\r\n")))
    (.write out ^bytes (file-bytes kind))
    (.write out (ascii (str "\r\n--" multipart-boundary "--\r\n")))
    (cond-> (-> (mock/request :post "/api/actions/upload-utp-image")
                (mock/content-type (str "multipart/form-data; boundary="
                                        multipart-boundary))
                (mock/body (.toByteArray out)))
      token (tu/token-header token))))

;;; CMS tripwire ;;;

(def ^:private cms-uploads
  "Filenames of every UTP CMS upload attempted while the tripwire is armed."
  (atom []))

(defn- with-cms-tripwire
  "Runs `f` with `lipas.integration.utp.cms/upload-image!` — the single outbound
   call `core/upload-utp-image!` makes — replaced by a recorder.

   Two jobs: the suite can never issue a live request to the real UTP CMS, and
   a rejection that silently still reached the CMS shows up as a non-empty
   counter instead of passing quietly."
  [f]
  (reset! cms-uploads [])
  (with-redefs [cms/upload-image!
                (fn [params]
                  (swap! cms-uploads conj (:filename params))
                  {:public-urls {:original "https://cms.invalid/stub.png"}})]
    (f)))

(defn- privileged-token
  "Token for an :activities-manager — the assignable role that carries BOTH
   :activity/edit and :loi/create-edit (see lipas.roles)."
  []
  (jwt/create-token
    (tu/gen-user {:db? true
                  :db-component (test-db)
                  :permissions {:roles [{:role "activities-manager"
                                         :activity ["outdoor-recreation-areas"]}]}})))

;;; Tests ;;;

(deftest upload-utp-image-requires-privilege-test
  (with-cms-tripwire
    (fn []
      (testing "anonymous caller"
        (is (= 401 (:status (test-app (utp-image-request))))
            "upload-utp-image must reject an anonymous caller with 401"))

      (testing "signed-in user with no roles"
        (let [token (jwt/create-token (tu/gen-regular-user :db-component (test-db)))]
          (is (= 403 (:status (test-app (utp-image-request :token token))))
              (str "a user holding neither :activity/edit nor :loi/create-edit"
                   " must be rejected with 403 — this is finding M3; it used to"
                   " answer 200 and reach the CMS"))))

      (testing "plain sports-site editor"
        ;; :city-manager holds `basic` (:site/create-edit, :activity/view,
        ;; :analysis-tool/use) — editing sites is not editing UTP content.
        (let [token (jwt/create-token (tu/gen-city-manager-user 91 :db-component (test-db)))]
          (is (= 403 (:status (test-app (utp-image-request :token token))))
              "a plain sports-site editor must be rejected with 403")))

      (is (empty? @cms-uploads)
          (str "no rejected upload may reach the UTP CMS, got " @cms-uploads))

      ;; Positive control: without this a deny-everything bug would look like a
      ;; clean pass above.
      (testing ":activities-manager passes the gate and reaches the CMS"
        (let [resp (test-app (utp-image-request :token (privileged-token)))]
          (is (= 200 (:status resp))
              (str "an :activities-manager must be able to upload, got "
                   (:status resp)))
          (is (= 1 (count @cms-uploads))
              "the accepted upload must actually reach the CMS layer"))))))

(deftest upload-utp-image-validates-the-file-test
  (with-cms-tripwire
    (fn []
      (let [token (privileged-token)
            status (fn [& opts]
                     (:status (test-app (apply utp-image-request :token token opts))))]
        (testing "disallowed content-type"
          (is (= 400 (status :filename "evil.html" :content-type "text/html"
                             :kind :not-an-image))
              "only image/png, image/jpeg, image/jpg and image/webp are accepted"))

        (testing "size over the cap"
          (is (= 400 (status :filename "big.png" :kind :oversized-png))
              (str "a file larger than " size-cap " bytes must be rejected")))

        (testing "allowed content-type but the bytes are not an image"
          (is (= 400 (status :kind :not-an-image))
              (str "content-type is client-supplied and trivially spoofed, so"
                   " the magic bytes must be verified too")))

        (is (empty? @cms-uploads)
            (str "no rejected upload may reach the UTP CMS, got " @cms-uploads))

        (testing "a real JPEG is accepted"
          (is (= 200 (status :filename "photo.jpg" :content-type "image/jpeg"
                             :kind :jpeg)))
          (is (= 1 (count @cms-uploads))
              "the accepted upload must actually reach the CMS layer"))))))

(comment
  (clojure.test/run-tests *ns*))
