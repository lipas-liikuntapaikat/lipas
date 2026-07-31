(ns lipas.backend.rate-limit-http-test
  "The rate limiter as actually wired into the router.

   `rate-limit-test` covers the limiter's logic in isolation. This namespace
   answers the different question: are the budgets really mounted on the
   endpoints that need them? A limiter that works perfectly but is attached to
   nothing is the failure mode worth guarding against.

   Two levels, deliberately:

   - a route-data walk asserting every endpoint that should have a budget has
     one. Cheap, needs no system, and covers endpoints whose handlers write to
     the DB (`register`) or call an external service (`subscribe-newsletter`) —
     bursting those 40 times would create junk accounts and hit Mailchimp.
   - a behavioural burst against one non-mutating endpoint, proving the wiring
     actually rejects rather than merely being declared."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lipas.backend.handler :as handler]
            [lipas.backend.rate-limit :as rl]
            [lipas.test-utils :refer [->json] :as tu]
            [reitit.core :as r]
            [reitit.ring :as ring]
            [ring.mock.request :as mock]))

(defonce test-system (atom nil))

(let [{:keys [once each]} (tu/full-system-fixture test-system)]
  (use-fixtures :once once)
  ;; Both :each fixtures in ONE call. `clojure.test/use-fixtures` ASSOCs the
  ;; namespace's fixture list rather than appending to it, so registering the
  ;; limiter reset separately would silently drop `each` — and with it the
  ;; prune between tests.
  ;;
  ;; Limiter state is process-wide, so a leftover bucket from another namespace
  ;; would make these fail depending on test order.
  (use-fixtures :each each (fn [f] (rl/reset-all!) (f) (rl/reset-all!))))

(defn test-app [req] ((:lipas/app @test-system) req))

;;; --- level 1: is a budget declared at all? ---------------------------------

(def ^:private must-have-a-budget
  "Unauthenticated endpoints that send mail or write to an external service.
   Each is free work for an anonymous caller, so each needs a ceiling."
  #{"/api/actions/request-password-reset"
    "/api/actions/order-magic-link"
    "/api/actions/register"
    "/api/actions/send-feedback"
    "/api/actions/subscribe-newsletter"})

(deftest declared-budgets-test
  ;; Route data is pure, so this needs no db/search — same trick as
  ;; lipas.backend.route-auth-test.
  (let [router (ring/get-router (handler/create-app {}))
        limited (into {}
                      (for [[path data] (r/routes router)
                            [method mdata] data
                            :when (and (map? mdata) (:rate-limit mdata))]
                        [path (assoc (:rate-limit mdata) :method method)]))]
    (doseq [path must-have-a-budget]
      (testing path
        (let [conf (get limited path)]
          (is (some? conf) (str path " must declare :rate-limit"))
          (when conf
            (is (contains? #{:ip :user} (:key conf))
                (str path " :key must be :ip or :user, got " (:key conf)))
            (is (pos-int? (:max conf)) (str path " :max must be a positive int"))
            (is (pos-int? (:window-ms conf))
                (str path " :window-ms must be a positive int"))))))))

;;; --- level 2: does the wiring actually reject? -----------------------------

;; request-password-reset is the probe: unauthenticated, writes nothing, and
;; sends only through the TestEmailer the test system installs.
(def ^:private probe
  {:path "/api/actions/request-password-reset"
   :body {:email "nobody@example.invalid"
          :reset-url "https://localhost/passu-hukassa"}})

;; Comfortably above every budget declared in route data, so this file needs no
;; edit when one is tuned.
(def ^:private burst 40)

(defn- post* [{:keys [path body]} ip]
  (test-app (-> (mock/request :post path)
                (mock/content-type "application/json")
                (mock/body (->json body))
                (assoc-in [:headers "x-real-ip"] ip))))

(deftest budget-is-enforced-test
  (rl/reset-all!)
  (let [statuses (mapv (fn [_] (:status (post* probe "203.0.113.7"))) (range burst))]
    (is (some #{429} statuses)
        (str "expected a 429 within " burst " requests: "
             (pr-str (frequencies statuses))))
    ;; A budget that rejects the FIRST request is an outage, not a limit.
    ;; Deliberately `not= 429` rather than `= 200`: this endpoint answers 404
    ;; for an unknown address today, and that is fine — what matters is that
    ;; the limiter itself let it past.
    (is (not= 429 (first statuses))
        "the first request must not be rate limited")))

(deftest budgets-are-per-ip-not-global-test
  ;; Guards the bug the missing nginx X-Real-IP header would have caused: every
  ;; request sharing one bucket, so one abuser locks out every other user.
  (rl/reset-all!)
  (dotimes [_ burst] (post* probe "203.0.113.8"))
  (is (= 429 (:status (post* probe "203.0.113.8")))
      "the exhausted address should be rejected")
  (is (not= 429 (:status (post* probe "198.51.100.9")))
      "a DIFFERENT address must be unaffected — a global bucket is an outage"))

(deftest rate-limited-response-shape-test
  (rl/reset-all!)
  (dotimes [_ burst] (post* probe "203.0.113.10"))
  (let [resp (post* probe "203.0.113.10")]
    (is (= 429 (:status resp)))
    (is (some? (get-in resp [:headers "Retry-After"]))
        "a 429 should tell the client when to retry")))

(deftest unlimited-endpoints-are-not-limited-test
  ;; The middleware sits in the global chain, so a mistake there would throttle
  ;; the whole API. Search is the busiest public endpoint — the map fires it on
  ;; every pan and zoom.
  (rl/reset-all!)
  (let [statuses (mapv (fn [_]
                         (:status (test-app (-> (mock/request :post "/api/actions/search")
                                                (mock/content-type "application/json")
                                                (mock/body (->json {:size 1 :query {:match_all {}}}))
                                                (assoc-in [:headers "x-real-ip"] "203.0.113.11")))))
                       (range burst))]
    (is (not-any? #{429} statuses)
        (str "/actions/search declares no budget and must not be limited: "
             (pr-str (frequencies statuses))))))
