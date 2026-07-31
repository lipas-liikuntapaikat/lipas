(ns lipas.backend.llm-auth-test
  "HTTP-level authorization regression tests for every endpoint that can spend
   money at an LLM provider.

   `lipas.backend.assistant-test` only exercises pure helper functions and never
   goes through the HTTP stack, so until now nothing asserted that these routes
   actually reject anonymous and under-privileged callers. The gates live in
   route data (`:require-privilege`) and are enforced by
   `lipas.backend.middleware/privilege-middleware`; this namespace pins that
   behaviour from the outside, through the real router.

   Two things every test here has to get right:

   - The request body must satisfy the route's `:parameters` schema. Coercion
     (`coercion/coerce-request-middleware`) runs BEFORE privilege-middleware in
     the global chain, so a sloppy body yields 400 and the test would pass for
     the wrong reason — never proving the 401/403 exists.
     `privileged-caller-passes-the-gate-test` is the control for that: with a
     privileged token the same bodies must NOT produce 400.

   - No request may reach a real provider. Every case runs inside
     `with-llm-tripwire`, which replaces the clj-http entry points that
     `lipas.backend.llm` and `lipas.backend.assistant` use with a recorder that
     throws. Unauthorized calls must leave it untouched (asserted), and if a
     gate were ever removed the tripwire turns the silent live API call into a
     loud failure instead of a bill."
  (:require [clj-http.client]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [lipas.backend.jwt :as jwt]
            [lipas.test-utils :as tu]
            [ring.mock.request :as mock]))

;;; Test system setup ;;;

(defonce test-system (atom nil))

(let [{:keys [once each]} (tu/full-system-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn test-db [] (:lipas/db @test-system))
(defn test-app [req] ((:lipas/app @test-system) req))

;;; Provider tripwire ;;;

(def ^:private llm-calls
  "Every outbound HTTP call attempted while a tripwire is armed."
  (atom []))

(defn- with-llm-tripwire
  "Runs `f` with the clj-http entry points used by the LLM code paths
   (`lipas.backend.llm` requires clj-http.client as `client`,
   `lipas.backend.assistant` as `http`; both go through `post`, which itself
   delegates to `request`) replaced by a recorder that throws.

   Guarantees the suite can never issue a live OpenAI/Gemini request, and makes
   a missing gate fail loudly rather than quietly costing money."
  [f]
  (reset! llm-calls [])
  (let [tripwire (fn [& args]
                   (swap! llm-calls conj (first args))
                   (throw (ex-info "LLM provider was called from an auth test"
                                   {:args (vec args)})))]
    (with-redefs [clj-http.client/request tripwire
                  clj-http.client/post tripwire
                  clj-http.client/get tripwire]
      (f))))

;;; Endpoint table ;;;

(def ^:private sample-site
  "Minimal schema-valid sports site (yleisurheilukenttä, no :lipas-id, so it
   matches the `new-sports-site` branch of `new-or-existing-sports-site`)."
  {:status "active"
   :event-date "2026-01-01T00:00:00.000Z"
   :name "Testikentän yleisurheilualue"
   :owner "city"
   :admin "city-sports"
   :type {:type-code 1210}
   :location {:city {:city-code 91}
              :address "Testikatu 1"
              :postal-code "00100"
              :postal-office "Helsinki"
              :geometries {:type "FeatureCollection"
                           :features [{:type "Feature"
                                       :geometry {:type "Point"
                                                  :coordinates [24.9384 60.1695]}}]}}})

(def ^:private llm-endpoints
  "Every route that can call an LLM provider, with the privilege its route data
   requires and a body that satisfies its `:parameters` schema."
  [{:path "/api/actions/assistant-chat"
    :privilege :ai-assistant/use
    :body {:message "Miten lisään uuden liikuntapaikan?"}}

   ;; Doesn't itself call a model — it drafts a support request from an
   ;; assistant conversation. Listed here because it is part of the assistant
   ;; surface and shares the `:ai-assistant/use` gate.
   {:path "/api/actions/assistant-escalate"
    :privilege :ai-assistant/use
    :body {:summary "En saa reittiä tallennettua."}}

   {:path "/api/actions/generate-ptv-descriptions"
    :privilege :ptv/manage
    :body {:lipas-id 123456}}

   {:path "/api/actions/generate-ptv-descriptions-from-data"
    :privilege :ptv/manage
    :body sample-site}

   {:path "/api/actions/generate-ptv-descriptions-batch"
    :privilege :ptv/manage
    :body {:lipas-ids [123456]}}

   {:path "/api/actions/generate-ptv-service-descriptions"
    :privilege :ptv/manage
    :body {:city-codes [91] :sub-category-id 1300}}

   {:path "/api/actions/translate-to-other-langs"
    :privilege :ptv/manage
    :body {:from "fi"
           :to ["se" "en"]
           :summary "Tiivistelmä"
           :description "Kuvaus"}}])

(defn- post*
  "POSTs `body` as JSON to `path`, with a bearer token when one is given."
  [path body token]
  (test-app (cond-> (-> (mock/request :post path)
                        (mock/content-type "application/json")
                        (mock/body (tu/->json body)))
              token (tu/token-header token))))

;;; Users ;;;

(defn- privileged-user
  "A user holding `privilege` and nothing else.

   `:ai-assistant/use` deliberately sits outside the `:admin` blanket grant
   during the assistant rollout, so it needs the standalone `:assistant-tester`
   role. `:ptv/manage` comes from a city-scoped `:ptv-manager`; the PTV LLM
   routes ask for it with `{:city-code ::roles/any}`, so any city matches."
  [privilege]
  (tu/gen-user {:db? true
                :db-component (test-db)
                :admin? false
                :permissions {:roles (case privilege
                                       :ai-assistant/use [{:role "assistant-tester"}]
                                       :ptv/manage [{:role "ptv-manager"
                                                     :city-code [91]}])}}))

;;; Tests ;;;

(deftest llm-endpoints-reject-anonymous-callers-test
  (testing "Every LLM endpoint answers 401 without a token, before any provider call"
    (with-llm-tripwire
      (fn []
        (doseq [{:keys [path body]} llm-endpoints]
          (let [resp (post* path body nil)]
            (is (= 401 (:status resp))
                (str path " must reject an anonymous caller with 401"
                     " (400 here would mean the request body no longer satisfies"
                     " the route schema and the auth gate was never reached)"))))
        (is (empty? @llm-calls)
            (str "No LLM provider call may be attempted for an unauthenticated"
                 " request. Attempted: " (pr-str @llm-calls)))))))

(deftest llm-endpoints-reject-under-privileged-callers-test
  (testing "Every LLM endpoint answers 403 for a signed-in user without the privilege"
    (let [token (jwt/create-token (tu/gen-regular-user :db-component (test-db)))]
      (with-llm-tripwire
        (fn []
          (doseq [{:keys [path body privilege]} llm-endpoints]
            (let [resp (post* path body token)]
              (is (= 403 (:status resp))
                  (str path " must reject a user lacking " privilege " with 403"
                       " (400 here would mean the request body no longer satisfies"
                       " the route schema and the auth gate was never reached)"))))
          (is (empty? @llm-calls)
              (str "No LLM provider call may be attempted for an unauthorized"
                   " request. Attempted: " (pr-str @llm-calls))))))))

(deftest llm-endpoints-reject-wrong-privilege-test
  (testing "Holding the OTHER LLM privilege is not enough — the gates don't leak into each other"
    ;; An assistant tester must not reach the PTV generators and a PTV manager
    ;; must not reach the assistant. Without this, one over-broad role would
    ;; silently open every LLM endpoint and the tests above would still pass.
    (let [assistant-token (jwt/create-token (privileged-user :ai-assistant/use))
          ptv-token (jwt/create-token (privileged-user :ptv/manage))]
      (with-llm-tripwire
        (fn []
          (doseq [{:keys [path body privilege]} llm-endpoints]
            (let [token (if (= :ptv/manage privilege) assistant-token ptv-token)
                  resp (post* path body token)]
              (is (= 403 (:status resp))
                  (str path " requires " privilege
                       " — a user holding only the other LLM privilege must get 403"))))
          (is (empty? @llm-calls)
              (str "No LLM provider call may be attempted for an unauthorized"
                   " request. Attempted: " (pr-str @llm-calls))))))))

(deftest privileged-caller-passes-the-gate-test
  (testing "With the required privilege the same bodies clear coercion and the gate"
    ;; The control for the tests above. If a body stopped satisfying its route
    ;; schema, the 401/403 assertions would still pass (coercion answers 400
    ;; before privilege-middleware runs) while proving nothing. Here a 400 or a
    ;; 401/403 is a failure: the request must get past coercion AND past the
    ;; privilege check, into the handler — where the tripwire stops it.
    (let [tokens {:ai-assistant/use (jwt/create-token (privileged-user :ai-assistant/use))
                  :ptv/manage (jwt/create-token (privileged-user :ptv/manage))}]
      (with-llm-tripwire
        (fn []
          (doseq [{:keys [path body privilege]} llm-endpoints]
            (let [resp (post* path body (get tokens privilege))]
              (is (not (contains? #{400 401 403} (:status resp)))
                  (str path " with a " privilege
                       " token must reach the handler: 400 means the test body no"
                       " longer matches the route schema, 401/403 means the gate"
                       " rejects a caller that should pass. Got "
                       (:status resp)))))
          ;; Not every handler reaches the provider: assistant-escalate only
          ;; drafts a support request (no model call at all), and the two
          ;; lipas-id-driven PTV generators resolve the site from the search
          ;; index first, which the test fixture has no document for. So this
          ;; asserts the tripwire is live overall rather than per endpoint.
          (is (seq @llm-calls)
              (str "At least one privileged request must reach the provider"
                   " boundary, otherwise the tripwire proves nothing about the"
                   " tests above.")))))))

(comment
  (clojure.test/run-tests *ns*))
