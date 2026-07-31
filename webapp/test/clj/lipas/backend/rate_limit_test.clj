(ns lipas.backend.rate-limit-test
  "Unit tests for the rate limiter. No app, no DB — the middleware is a plain
   route-data middleware, so it can be compiled against a stub handler."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lipas.backend.rate-limit :as rl]))

(use-fixtures :each (fn [f] (rl/reset-all!) (f) (rl/reset-all!)))

(defn- wrap
  "Compiles the middleware for `conf` around a handler that always 200s."
  [conf]
  (let [compile-fn (:compile rl/middleware)
        wrapper (compile-fn {:rate-limit conf :name ::test-route} nil)]
    (wrapper (fn [_] {:status 200 :body {:ok true}}))))

(defn- req
  ([] (req {}))
  ([{:keys [ip user-id real-ip forwarded]}]
   (cond-> {:request-method :post :uri "/x"}
     ip (assoc :remote-addr ip)
     real-ip (assoc-in [:headers "x-real-ip"] real-ip)
     forwarded (assoc-in [:headers "x-forwarded-for"] forwarded)
     user-id (assoc :identity {:id user-id}))))

(deftest allows-up-to-the-budget-then-rejects-test
  (let [h (wrap {:key :ip :window-ms 60000 :max 3})
        statuses (mapv (fn [_] (:status (h (req {:ip "1.2.3.4"})))) (range 5))]
    (is (= [200 200 200 429 429] statuses))))

(deftest buckets-are-independent-per-ip-test
  (let [h (wrap {:key :ip :window-ms 60000 :max 2})]
    (dotimes [_ 2] (h (req {:ip "1.1.1.1"})))
    (testing "the exhausted IP is rejected"
      (is (= 429 (:status (h (req {:ip "1.1.1.1"}))))))
    (testing "a different IP is unaffected"
      (is (= 200 (:status (h (req {:ip "2.2.2.2"}))))))))

(deftest user-keyed-buckets-are-per-user-test
  ;; The point of :key :user — two colleagues behind one municipal NAT must not
  ;; consume each other's budget.
  (let [h (wrap {:key :user :window-ms 60000 :max 1})]
    (is (= 200 (:status (h (req {:ip "10.0.0.1" :user-id "user-a"})))))
    (is (= 429 (:status (h (req {:ip "10.0.0.1" :user-id "user-a"})))))
    (is (= 200 (:status (h (req {:ip "10.0.0.1" :user-id "user-b"})))))))

(deftest window-expiry-frees-the-budget-test
  (let [h (wrap {:key :ip :window-ms 50 :max 1})]
    (is (= 200 (:status (h (req {:ip "3.3.3.3"})))))
    (is (= 429 (:status (h (req {:ip "3.3.3.3"})))))
    (Thread/sleep 80)
    (is (= 200 (:status (h (req {:ip "3.3.3.3"}))))
        "a request should be allowed again once the window has passed")))

(deftest rejection-carries-retry-after-test
  (let [h (wrap {:key :ip :window-ms 60000 :max 1})]
    (h (req {:ip "4.4.4.4"}))
    (let [resp (h (req {:ip "4.4.4.4"}))
          retry (get-in resp [:headers "Retry-After"])]
      (is (= 429 (:status resp)))
      (is (some? retry) "429 must tell the client when to come back")
      (is (<= 1 (parse-long retry) 60) (str "implausible Retry-After: " retry)))))

(deftest no-rate-limit-declared-is-inert-test
  ;; The middleware sits in the global chain, so it must cost nothing on the
  ;; overwhelming majority of routes that declare no budget.
  (is (nil? ((:compile rl/middleware) {:name ::no-conf} nil))))

;;; --- client-ip: the spoofing-resistance rule -------------------------------

(deftest client-ip-prefers-x-real-ip-test
  ;; nginx OVERWRITES X-Real-IP with the real peer, so it cannot be spoofed.
  (is (= "9.9.9.9" (rl/client-ip (req {:real-ip "9.9.9.9" :ip "172.18.0.5"})))))

(deftest client-ip-ignores-x-forwarded-for-test
  ;; $proxy_add_x_forwarded_for APPENDS to whatever the client sent, so trusting
  ;; X-Forwarded-For would let a caller prepend junk and rotate buckets freely.
  (testing "a client-supplied X-Forwarded-For is not used"
    (is (= "172.18.0.5"
           (rl/client-ip (req {:forwarded "1.1.1.1, 2.2.2.2" :ip "172.18.0.5"})))))
  (testing "X-Real-IP still wins over a spoofed X-Forwarded-For"
    (is (= "9.9.9.9"
           (rl/client-ip (req {:real-ip "9.9.9.9"
                               :forwarded "1.1.1.1"
                               :ip "172.18.0.5"}))))))

(deftest spoofed-forwarded-for-cannot-rotate-buckets-test
  ;; End-to-end version of the rule above: varying X-Forwarded-For must not buy
  ;; extra requests.
  (let [h (wrap {:key :ip :window-ms 60000 :max 2})
        attempt (fn [xff] (:status (h (req {:real-ip "5.5.5.5" :forwarded xff}))))]
    (is (= [200 200 429 429]
           [(attempt "1.1.1.1") (attempt "2.2.2.2")
            (attempt "3.3.3.3") (attempt "4.4.4.4")]))))

(deftest falls-back-to-remote-addr-test
  ;; Local dev talks to :8091 directly, with no nginx in front.
  (is (= "127.0.0.1" (rl/client-ip (req {:ip "127.0.0.1"}))))
  (is (= "unknown" (rl/client-ip (req)))))

;;; --- eviction --------------------------------------------------------------

(deftest expired-buckets-are-evicted-test
  ;; The assistant's original limiter grew its map forever, so a stream of
  ;; one-shot addresses was an unbounded memory leak. Buckets whose windows have
  ;; fully expired must disappear, not just be filtered on read.
  (let [h (wrap {:key :ip :window-ms 10 :max 1})]
    (doseq [i (range 50)]
      (h (req {:ip (str "10.1.1." i)})))
    (is (pos? (count @@#'rl/state)) "buckets should exist while windows are open")
    (Thread/sleep 50)
    ;; Any further request triggers the opportunistic sweep. The sweep keeps a
    ;; bucket only while it is inside the longest window in use (a day), so
    ;; assert on the millisecond-window buckets being prunable rather than on
    ;; the day-scale sweep having removed them.
    (h (req {:ip "10.2.2.2"}))
    (let [live (->> @@#'rl/state
                    vals
                    (mapcat identity)
                    (filter #(> % (- (System/currentTimeMillis) 10)))
                    count)]
      (is (<= live 1)
          "at most the just-made request should still be inside a 10ms window"))))
