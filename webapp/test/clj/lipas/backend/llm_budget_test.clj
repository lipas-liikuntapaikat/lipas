(ns lipas.backend.llm-budget-test
  "Cost controls on the endpoints that can spend money at an LLM provider.

   `lipas.backend.llm-auth-test` proves those endpoints reject the wrong
   CALLER. This namespace covers the other half: a caller who legitimately
   holds the privilege still cannot spend without limit. Two ceilings, tested
   from the outside through the real router:

   - a per-user request budget declared in route data and enforced by
     `lipas.backend.rate-limit`. Before this, the PTV generators were gated
     only by `:ptv/manage`, so one loop in the frontend — or one impatient
     click — could fan out into an unbounded number of Gemini calls.
   - `:max` lengths on the strings and vectors that flow into a prompt.
     `translate-to-other-langs` took bare `:string` summaries, descriptions
     and user instructions, and `generate-ptv-descriptions-batch` took an
     unbounded `:lipas-ids` vector, so a single request could carry arbitrary
     volume into a paid model.

   Two things every test here has to get right:

   - No request may reach a real provider. Everything runs inside
     `with-llm-tripwire`, which replaces the clj-http entry points the LLM
     code paths use with a recorder that throws. Rejections must leave it
     untouched (asserted); a removed control turns into a loud failure rather
     than a bill.
   - The limiter's state is process-wide, so it is reset around every test.
     Without that, a burst here would decide whether some other namespace's
     request is rejected, and the failures would depend on test order.

   Budgets are read FROM route data rather than hard-coded, so tuning a
   budget doesn't mean editing this file. The one exception is the assistant,
   where the exact numbers are the regression being checked."
  (:require [clj-http.client]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [lipas.backend.handler :as handler]
            [lipas.backend.jwt :as jwt]
            [lipas.backend.rate-limit :as rl]
            [lipas.data.ptv :as ptv-data]
            [lipas.test-utils :as tu]
            [reitit.core :as r]
            [reitit.ring :as ring]
            [ring.mock.request :as mock]
            [taoensso.timbre :as log]))

;;; Test system setup ;;;

(defonce test-system (atom nil))

(let [{:keys [once each]} (tu/full-system-fixture test-system)]
  (use-fixtures :once once)
  ;; Both :each fixtures in ONE call. `clojure.test/use-fixtures` ASSOCs the
  ;; namespace's fixture list rather than appending to it, so registering the
  ;; limiter reset separately would silently drop `each` — and with it the
  ;; prune between tests.
  ;;
  ;; The limiter is a process-wide atom: a leftover bucket from another
  ;; namespace would make these fail depending on test order, and a bucket left
  ;; behind here would do the same to everyone else.
  (use-fixtures :each each (fn [f] (rl/reset-all!) (f) (rl/reset-all!))))

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

   Deliberately the same shape as `lipas.backend.llm-auth-test`'s tripwire and
   deliberately duplicated: neither namespace should depend on the other's
   private test helper, and a tripwire that can be weakened from elsewhere is
   worth less than twelve duplicated lines.

   The recorder throws an ex-info with no `:status`, which matters:
   `llm/gemini-complete` only retries when it sees a 503/429, so each request
   attempts exactly one provider call and the counts below stay exact."
  [f]
  (reset! llm-calls [])
  (let [tripwire (fn [& args]
                   (swap! llm-calls conj (first args))
                   (throw (ex-info "LLM provider was called from a budget test"
                                   {:args (vec args)})))]
    (with-redefs [clj-http.client/request tripwire
                  clj-http.client/post tripwire
                  clj-http.client/get tripwire]
      (f))))

(defmacro ^:private quietly
  "Silences logging for `body`.

   Every allowed request in a burst throws at the tripwire, and the API's
   default 500 handler logs a stack trace for each one (see
   `handler/exception-handlers`). Without this, a 60-request burst buries the
   actual assertion output."
  [& body]
  `(log/with-min-level :fatal ~@body))

;;; Route table ;;;

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

(def ^:private translate-path "/api/actions/translate-to-other-langs")
(def ^:private batch-path "/api/actions/generate-ptv-descriptions-batch")
(def ^:private chat-path "/api/actions/assistant-chat")
(def ^:private escalate-path "/api/actions/assistant-escalate")

(def ^:private translate-body
  {:from "fi"
   :to ["se" "en"]
   :summary "Tiivistelmä"
   :description "Kuvaus"})

(def ^:private llm-routes
  "Every route that can call an LLM provider, with the privilege its route
   data requires and a body that satisfies its `:parameters` schema. Same set
   as `lipas.backend.llm-auth-test`; if a new one appears there it belongs
   here too."
  [{:path chat-path
    :privilege :ai-assistant/use
    :body {:message "Miten lisään uuden liikuntapaikan?"}}

   ;; Doesn't call a model itself — it mails a support request drafted from an
   ;; assistant conversation. Budgeted all the same: unbounded, it is an
   ;; ops-inbox flood.
   {:path escalate-path
    :privilege :ai-assistant/use
    :body {:summary "En saa reittiä tallennettua."}}

   {:path "/api/actions/generate-ptv-descriptions"
    :privilege :ptv/manage
    :body {:lipas-id 123456}}

   {:path "/api/actions/generate-ptv-descriptions-from-data"
    :privilege :ptv/manage
    :body sample-site}

   {:path batch-path
    :privilege :ptv/manage
    :body {:lipas-ids [123456]}}

   {:path "/api/actions/generate-ptv-service-descriptions"
    :privilege :ptv/manage
    :body {:city-codes [91] :sub-category-id 1300}}

   {:path translate-path
    :privilege :ptv/manage
    :body translate-body}])

(def ^:private declared-budgets
  "path -> the `:rate-limit` map the real router carries for it. Route data is
   pure, so this needs no db/search — same trick as
   `lipas.backend.rate-limit-http-test`."
  (delay
    (let [router (ring/get-router (handler/create-app {}))]
      (into {}
            (for [[path data] (r/routes router)
                  [_method mdata] data
                  :when (and (map? mdata) (:rate-limit mdata))]
              [path (:rate-limit mdata)])))))

(defn- budget-of [path] (:max (get @declared-budgets path)))

;;; Requests & users ;;;

(defn- post*
  "POSTs `body` as JSON to `path` with a bearer token."
  [path body token]
  (test-app (-> (mock/request :post path)
                (mock/content-type "application/json")
                (mock/body (tu/->json body))
                (tu/token-header token))))

(defn- privileged-token
  "A token for a user holding `privilege` and nothing else. Each call makes a
   NEW user, which is what the per-user budget test needs.

   `:ai-assistant/use` sits outside the `:admin` blanket grant during the
   assistant rollout, so it needs the standalone `:assistant-tester` role.
   `:ptv/manage` comes from a city-scoped `:ptv-manager`; the PTV LLM routes
   ask for it with `{:city-code ::roles/any}`, so any city matches."
  [privilege]
  (jwt/create-token
    (tu/gen-user {:db? true
                  :db-component (test-db)
                  :admin? false
                  :permissions {:roles (case privilege
                                         :ai-assistant/use [{:role "assistant-tester"}]
                                         :ptv/manage [{:role "ptv-manager"
                                                       :city-code [91]}])}})))

(defn- text-of-length [n] (apply str (repeat n \x)))

;;; --- level 1: is a budget declared at all? ---------------------------------

(deftest every-llm-route-declares-a-per-user-budget-test
  ;; The cheap, complete check: a limiter that works perfectly but is attached
  ;; to nothing is the failure mode worth guarding against, and a route added
  ;; later is exactly when it gets forgotten.
  (doseq [{:keys [path]} llm-routes]
    (testing path
      (let [conf (get @declared-budgets path)]
        (is (some? conf) (str path " spends money at a provider and must declare :rate-limit"))
        (when conf
          ;; :user, not :ip. Every one of these sits behind a privilege gate,
          ;; and PTV managers in one municipality can share a NAT — with :ip
          ;; one colleague's batch run would lock out the rest.
          (is (= :user (:key conf))
              (str path " must bucket by :user (privilege-gated endpoint), got " (:key conf)))
          (is (pos-int? (:max conf))
              (str path " :max must be a positive int, got " (pr-str (:max conf))))
          (is (pos-int? (:window-ms conf))
              (str path " :window-ms must be a positive int, got "
                   (pr-str (:window-ms conf)))))))))

;;; --- level 2: does the budget actually reject? -----------------------------

(deftest budget-rejects-a-burst-test
  (testing "a privileged caller is cut off at the declared budget, not before"
    (let [token (privileged-token :ptv/manage)
          budget (budget-of translate-path)]
      (with-llm-tripwire
        (fn []
          (let [statuses (quietly
                           (mapv (fn [_] (:status (post* translate-path translate-body token)))
                                 (range (inc budget))))]
            ;; A budget that rejects the FIRST request is an outage, not a
            ;; limit. Deliberately `not= 429` rather than `= 200`: with the
            ;; tripwire armed the handler answers 500, and that is fine —
            ;; what matters is that the limiter let it through.
            (is (not= 429 (first statuses))
                "the first request must not be rate limited")
            (is (not-any? #{429} (butlast statuses))
                (str "no request within the budget of " budget " may be rejected: "
                     (pr-str (frequencies (butlast statuses)))))
            (is (= 429 (last statuses))
                (str "request " (inc budget) " is past the budget and must be 429, got "
                     (last statuses)))
            ;; The point of the whole exercise: the number of paid calls is
            ;; bounded by the budget, and the rejected request bought nothing.
            (is (= budget (count @llm-calls))
                (str "exactly " budget " provider calls should have been attempted"
                     " (one per allowed request, none for the 429): "
                     (count @llm-calls)))))))))

(deftest budgets-are-per-user-not-global-test
  ;; A global bucket would mean one PTV manager's legitimate batch run
  ;; silences the whole feature for every other municipality — an outage
  ;; dressed up as a cost control.
  (let [token-a (privileged-token :ptv/manage)
        token-b (privileged-token :ptv/manage)
        budget (budget-of translate-path)]
    (with-llm-tripwire
      (fn []
        (quietly (dotimes [_ (inc budget)] (post* translate-path translate-body token-a)))
        (is (= 429 (:status (quietly (post* translate-path translate-body token-a))))
            "the exhausted user should stay rejected")
        (is (not= 429 (:status (quietly (post* translate-path translate-body token-b))))
            "a DIFFERENT user must be unaffected")))))

;;; --- level 3: are the prompt inputs bounded? -------------------------------

(deftest over-length-prompt-inputs-are-rejected-test
  (testing "an over-long text field is refused before any model is called"
    (let [token (privileged-token :ptv/manage)
          too-long (text-of-length (inc ptv-data/max-description-length))]
      (with-llm-tripwire
        (fn []
          (doseq [[field body]
                  [[:summary (assoc translate-body :summary too-long)]
                   [:description (assoc translate-body :description too-long)]
                   [:user-instruction (assoc translate-body :user-instruction too-long)]
                   ;; :from and :to are interpolated straight into the prompt,
                   ;; so they carry the tightest bound of the five.
                   [:from (assoc translate-body :from (text-of-length 50))]]]
            (is (= 400 (:status (quietly (post* translate-path body token))))
                (str "an over-length :" (name field) " must be rejected with 400")))

          ;; One request = one Gemini call carrying every site's document, so
          ;; an unbounded :lipas-ids vector is unbounded prompt volume.
          (is (= 400 (:status (quietly (post* batch-path
                                              {:lipas-ids (vec (range 100000 100200))}
                                              token))))
              "an over-long :lipas-ids vector must be rejected with 400")

          ;; Coercion runs before the limiter (see the global middleware chain
          ;; in handler/create-app), so these cost neither budget nor money.
          (is (empty? @llm-calls)
              (str "no provider call may be attempted for a rejected body. Attempted: "
                   (count @llm-calls))))))))

(deftest legitimate-long-payloads-are-accepted-test
  ;; The control for the test above. An over-tight `:max` would make that one
  ;; pass while quietly breaking the feature: PTV allows 5000-character
  ;; descriptions and user instructions (lipas.data.ptv, mirroring the API's
  ;; own 400s), and a municipality really does write them that long. A 400
  ;; here means the bound is tighter than the real data.
  (let [token (privileged-token :ptv/manage)
        max-legit {:from "fi"
                   :to ["se" "en"]
                   ;; Deliberately longer than PTV's 150-char summary limit:
                   ;; generation can overshoot it and the UI clamps only the
                   ;; RESULT, so an over-long draft summary is a normal
                   ;; editing state that must still be translatable.
                   :summary (text-of-length 400)
                   :description (text-of-length ptv-data/max-description-length)
                   :user-instruction (text-of-length ptv-data/max-user-instruction-length)}]
    (with-llm-tripwire
      (fn []
        (let [resp (quietly (post* translate-path max-legit token))]
          (is (not= 400 (:status resp))
              (str "a payload at PTV's own field limits must clear coercion, got "
                   (:status resp)))
          (is (not= 429 (:status resp))
              "a first request must not be rate limited"))

        ;; The batch the frontend actually sends: 10 ids
        ;; (lipas.ui.ptv.events/batch-size) plus a style reference at PTV's
        ;; summary and description limits.
        (let [resp (quietly (post* batch-path
                                   {:lipas-ids (vec (range 100000 100010))
                                    :reference {:summary (text-of-length ptv-data/max-summary-length)
                                                :description (text-of-length ptv-data/max-description-length)}}
                                   token))]
          (is (not= 400 (:status resp))
              (str "the frontend's real batch shape must clear coercion, got "
                   (:status resp))))))))

;;; --- level 4: the assistant migration ------------------------------------

(deftest assistant-budgets-test
  ;; The assistant used to run its own limiter inside `assistant/chat!`
  ;; (`chat-rate-limit` 30/h, `escalation-rate-limit` 5/day, both per user).
  ;; That code is gone. These two are asserted literally rather than read from
  ;; route data, because each encodes a decision worth breaking a test over.
  (testing "escalation is still 5 per 24h per user"
    ;; Deliberately NOT raised when the other LLM budgets were. This one does
    ;; not spend model tokens — it mails lipasinfo through the job queue — so
    ;; its budget bounds an ops-inbox flood, and 5 support requests a day from
    ;; one user is already generous for that.
    (is (= {:key :user :window-ms rl/day-ms :max 5}
           (get @declared-budgets escalate-path))))

  (testing "chat is 300 per hour per user"
    ;; Raised from the 30/h the private limiter enforced. A budget a real user
    ;; can hit mid-conversation is a support problem rather than a saving; this
    ;; is a runaway/abuse backstop. Note one chat can fan out to
    ;; `assistant/max-tool-iterations` rounds, so the provider-call ceiling is a
    ;; multiple of this.
    (is (= {:key :user :window-ms rl/hour-ms :max 300}
           (get @declared-budgets chat-path))))

  (testing "chat still rejects after the same number of requests as before"
    (let [token (privileged-token :ai-assistant/use)
          budget (budget-of chat-path)
          body {:message "Miten lisään uuden liikuntapaikan?"}]
      (with-llm-tripwire
        (fn []
          (let [statuses (quietly (mapv (fn [_] (:status (post* chat-path body token)))
                                        (range (inc budget))))]
            (is (not-any? #{429} (butlast statuses))
                (str "the first " budget " messages must go through: "
                     (pr-str (frequencies (butlast statuses)))))
            (is (= 429 (last statuses))
                (str "message " (inc budget) " must be rejected, got " (last statuses))))))))

  (testing "escalation still rejects after the same number of requests as before"
    (let [token (privileged-token :ai-assistant/use)
          budget (budget-of escalate-path)
          body {:summary "En saa reittiä tallennettua."}]
      (with-llm-tripwire
        (fn []
          (let [statuses (quietly (mapv (fn [_] (:status (post* escalate-path body token)))
                                        (range (inc budget))))]
            (is (not-any? #{429} (butlast statuses))
                (str "the first " budget " support requests must go through: "
                     (pr-str (frequencies (butlast statuses)))))
            (is (= 429 (last statuses))
                (str "support request " (inc budget) " must be rejected, got "
                     (last statuses)))
            ;; escalate! only drafts an email through the job queue.
            (is (empty? @llm-calls)
                "escalation must not call a model at all")))))))

(comment
  (clojure.test/run-tests *ns*))
