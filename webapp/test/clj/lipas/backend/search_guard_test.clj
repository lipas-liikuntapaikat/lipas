(ns lipas.backend.search-guard-test
  "Unit tests for the untrusted-ES-query guard plus HTTP-level tests that the
   real app rejects the reproduction payloads on /api/actions/search."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lipas.backend.core :as core]
            [lipas.backend.search-guard :as search-guard]
            [lipas.test-utils :refer [->json <-json] :as tu]
            [ring.mock.request :as mock]))

(defonce test-system (atom nil))

(let [{:keys [once each]} (tu/full-system-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn test-search [] (:lipas/search @test-system))
(defn test-app [req] ((:lipas/app @test-system) req))

;;; Helpers ;;;

(defn- rule
  "The rule the guard trips on, or nil when the query is accepted."
  [q]
  (:rule (search-guard/violation q)))

(defn- rejects? [q]
  (some? (search-guard/violation q)))

(defn- thrown-type [q]
  (try
    (search-guard/check-query! q)
    nil
    (catch clojure.lang.ExceptionInfo e
      (:type (ex-data e)))))

;;; Rule 1: scripting ;;;

(deftest scripting-key-predicate-test
  (testing "every ES parameter that can carry code is caught by the substring"
    (doseq [k [:script :script_fields :script_score :scripted_metric
               :init_script :map_script :combine_script :reduce_script
               :bucket_script :script_query :some_future_script_param
               ;; case-insensitive
               :SCRIPT :Script_Score
               ;; string keys (transit clients)
               "script" "script_fields"
               ;; runtime fields carry scripts under a name without "script"
               :runtime_mappings "runtime_mappings"]]
      (is (search-guard/scripting-key? k) (str "should reject " (pr-str k)))))

  (testing "innocent keys are not caught"
    (doseq [k [:query :size :from :aggs :terms :field :sort :_source
               ;; contains "score", not "script" — the frontend uses this
               :function_score :score_mode :_score
               :track_total_hits :simple_query_string :geo_shape
               ;; non-name keys must not blow up
               1 nil]]
      (is (not (search-guard/scripting-key? k)) (str "should allow " (pr-str k)))))

  (testing "the substring rule is deliberately over-broad"
    ;; \"description\" contains \"script\". No field in any index this guard
    ;; protects is named that (verified against lipas.backend.search/mappings:
    ;; no mapping key anywhere contains the substring), and it is not an ES
    ;; search parameter, so the over-breadth costs nothing today. Documented
    ;; here so a future field named like this fails loudly in tests rather
    ;; than silently in production.
    (is (search-guard/scripting-key? :description))))

(deftest rejects-scripting-at-top-level-test
  (is (= :scripting (rule {:script_fields {:d {:script {:source "1"}}}})))
  (is (= :scripting (rule {:runtime_mappings {:evil {:type "long"
                                                     :script {:source "emit(42)"}}}}))))

(deftest rejects-scripting-nested-in-query-test
  (testing "script_score buried under :query"
    (is (= :scripting
           (rule {:size 1
                  :query {:script_score
                          {:query {:match_all {}}
                           :script {:source "Math.log(2 + doc['lipas-id'].value)"}}}}))))

  (testing "script inside a vector of :must clauses"
    (is (= :scripting
           (rule {:query {:bool {:must [{:match_all {}}
                                        {:term {:status "active"}}
                                        {:script {:script {:source "true"}}}]}}}))))

  (testing "script inside a sort clause vector"
    (is (= :scripting
           (rule {:query {:match_all {}}
                  :sort [{:_script {:type "number"
                                    :script {:source "doc['lipas-id'].value"}}}]})))))

(deftest rejects-scripting-nested-in-aggs-test
  (testing "scripted_metric several levels down"
    (is (= :scripting
           (rule {:size 0
                  :aggs {:outer
                         {:terms {:field :type.type-code :size 10}
                          :aggs {:inner
                                 {:scripted_metric
                                  {:init_script "state.x = 0"
                                   :map_script "state.x += 1"
                                   :combine_script "return state.x"
                                   :reduce_script "return 1"}}}}}}))))

  (testing "bucket_script pipeline aggregation"
    (is (= :scripting
           (rule {:size 0
                  :aggs {:by-year
                         {:terms {:field :construction-year :size 20}
                          :aggs {:a {:sum {:field :x}}
                                 :ratio {:bucket_script
                                         {:buckets_path {:a "a"}
                                          :script "params.a * 2"}}}}}})))))

(deftest rejects-scripting-with-string-keys-test
  (testing "transit/JSON clients can send string keys — both forms are handled"
    (is (= :scripting (rule {"query" {"script_score" {"script" {"source" "1"}}}})))
    (is (= :scripting (rule {"runtime_mappings" {"evil" {"type" "long"}}})))
    (testing "mixed keyword and string keys"
      (is (= :scripting (rule {:query {:bool {"must" [{"script" {}}]}}}))))))

;;; Rule 2: size caps ;;;

(deftest top-level-size-cap-test
  (is (not (rejects? {:size search-guard/max-size :query {:match_all {}}}))
      "exactly at the cap is fine")
  (is (= :size (rule {:size (inc search-guard/max-size) :query {:match_all {}}})))
  (is (= :size (rule {:size 1000000})))

  (testing "the largest size any real client sends (5000) is allowed"
    (is (not (rejects? {:from 0 :size 5000 :query {:match_all {}}})))))

(deftest top-level-from-cap-test
  (is (not (rejects? {:from search-guard/max-from :size 10})))
  (is (= :from (rule {:from (inc search-guard/max-from) :size 10})))
  (is (= :from (rule {:from 2000000000}))))

(deftest nested-agg-size-cap-test
  (testing "a 5000-bucket terms aggregation is rejected"
    (let [v (search-guard/violation
              {:size 0
               :aggs {:a {:terms {:field :type.type-code :size 5000}}}})]
      (is (= :size (:rule v)))
      (is (= search-guard/max-agg-size (:limit v))
          "nested sizes get the tighter aggregation cap, not the top-level one")))

  (testing "the top-level cap does not leak into aggregations"
    (is (= :size (rule {:size 0
                        :aggs {:a {:terms {:field :x
                                           :size (inc search-guard/max-agg-size)}}}}))))

  (testing "deeply nested agg size"
    (is (= :size (rule {:aggs {:a {:terms {:field :x :size 10}
                                   :aggs {:b {:terms {:field :y :size 50}
                                              :aggs {:c {:top_hits {:size 99999}}}}}}}}))))

  (testing "the largest agg size any real client sends (composite 1000) is allowed"
    (is (not (rejects? {:size 0
                        :aggs {:years {:composite
                                       {:size 1000
                                        :sources [{:construction-year
                                                   {:histogram {:field :construction-year
                                                                :interval 10}}}]}}}})))))

(deftest non-numeric-size-is-not-a-limit-test
  (testing "a field literally named \"size\" must not be mistaken for a cap"
    (is (not (rejects? {:query {:range {:size {:gte 1 :lte 999999}}}})))
    (is (not (rejects? {:query {:terms {:size [1 2 3]}}})))))

;;; Good queries pass through untouched ;;;

(deftest good-queries-pass-untouched-test
  (testing "production map search shape"
    (let [q {:from 0
             :size 250
             :track_total_hits 60000
             :_source {:includes ["lipas-id" "name" "location.geometries"]}
             :sort [{:search-meta.name.sort {:order "asc"}}]
             :query {:function_score
                     {:score_mode "max"
                      :query {:bool
                              {:must [{:simple_query_string
                                       {:query "uima*"
                                        :fields ["name^3"]
                                        :default_operator "AND"
                                        :analyze_wildcard true}}]
                               :filter [{:terms {:status ["active"]}}
                                        {:geo_shape {:search-meta.location.geometries
                                                     {:shape {:type "envelope"
                                                              :coordinates [[20 70] [30 60]]}
                                                      :relation "intersects"}}}]}}
                      :functions [{:exp {:search-meta.location.wgs84-point
                                         {:origin "62,25" :offset "1000m" :scale "1000m"}}}]}}}]
      (is (nil? (search-guard/violation q)))
      (is (identical? q (search-guard/check-query! q))
          "check-query! returns the body unchanged, it never rewrites")))

  (testing "finance-report shape"
    (is (nil? (search-guard/violation
                {:size 0
                 :query {:bool {:filter [{:terms {:year [2023]}}
                                         {:terms {:city-code [91 92]}}]}}
                 :aggs {:by_grouping
                        {:terms {:field :city-code :size 400}
                         :aggs {:by_year {:terms {:field :year :size 20}
                                          :aggs {:population {:stats {:field :population}}}}}}}}))))

  (testing "empty / nil / non-map bodies are harmless"
    (is (nil? (search-guard/violation {})))
    (is (nil? (search-guard/violation nil)))
    (is (nil? (search-guard/violation [])))))

(deftest check-query-throws-tagged-ex-info-test
  (is (nil? (thrown-type {:query {:match_all {}}})))
  (is (= :invalid-search-query (thrown-type {:script_fields {}})))
  (is (= :invalid-search-query (thrown-type {:size 999999})))
  (testing "the message names the offending path"
    (is (re-find #"aggs\.a\.terms\.size"
                 (try
                   (search-guard/check-query!
                     {:aggs {:a {:terms {:field :x :size 999999}}}})
                   (catch clojure.lang.ExceptionInfo e (ex-message e)))))))

;;; HTTP level ;;;

(defn- post-search [body]
  (test-app (-> (mock/request :post "/api/actions/search")
                (mock/content-type "application/json")
                (mock/body (->json body)))))

(deftest search-endpoint-rejects-attack-payloads-test
  (testing "script_score — anonymous Painless execution"
    (let [resp (post-search
                 {:size 1
                  :query {:script_score
                          {:query {:match_all {}}
                           :script {:source "Math.log(2 + doc['lipas-id'].value)"}}}})
          ;; NOTE: :body is an InputStream, so parse it exactly once.
          body (<-json (:body resp))]
      (is (= 400 (:status resp)))
      (is (= "invalid-search-query" (:type body)))
      (is (re-find #"(?i)scripting" (:message body)))))

  (testing "runtime_mappings — Painless once per document across the index"
    (let [resp (post-search
                 {:size 0
                  :runtime_mappings {:evil {:type "long"
                                            :script {:source "emit(42)"}}}
                  :aggs {:a {:sum {:field :evil}}}})
          body (<-json (:body resp))]
      (is (= 400 (:status resp)))
      (is (= "invalid-search-query" (:type body)))))

  (testing "5000-bucket terms aggregation — unbounded heap"
    (let [resp (post-search
                 {:size 0
                  :aggs {:a {:terms {:field :type.type-code :size 5000}}}})
          body (<-json (:body resp))]
      (is (= 400 (:status resp)))
      (is (= "invalid-search-query" (:type body)))
      (is (re-find #"exceeds the maximum" (:message body))))))

(deftest search-endpoint-still-serves-normal-queries-test
  (let [site (tu/gen-sports-site)
        lipas-id (:lipas-id site)
        _ (core/index! (test-search) site :sync)
        resp (post-search {:size 100
                           :query {:bool {:must [{:query_string {:query (:name site)}}]}}})
        sites (->> resp :body <-json :hits :hits (map :_source))]
    (is (= 200 (:status resp)))
    (is (some (comp #{lipas-id} :lipas-id) sites))))

(deftest report-endpoint-rejects-scripting-test
  (let [resp (test-app
               (-> (mock/request :post "/api/actions/create-sports-sites-report")
                   (mock/content-type "application/json")
                   (mock/body (->json {:search-query
                                       {:query {:script_score
                                                {:query {:match_all {}}
                                                 :script {:source "1"}}}}
                                       :fields ["lipas-id" "name"]
                                       :locale "fi"
                                       :format "xlsx"}))))
        body (<-json (:body resp))]
    (is (= 400 (:status resp)))
    (is (= "invalid-search-query" (:type body)))))

(deftest query-subsidies-rejects-oversized-agg-test
  (let [resp (test-app
               (-> (mock/request :post "/api/actions/query-subsidies")
                   (mock/content-type "application/json")
                   (mock/body (->json {:size 0
                                       :aggs {:a {:terms {:field :city-code
                                                          :size 100000}}}}))))
        body (<-json (:body resp))]
    (is (= 400 (:status resp)))
    (is (= "invalid-search-query" (:type body)))))

(comment
  (clojure.test/run-tests *ns*))
