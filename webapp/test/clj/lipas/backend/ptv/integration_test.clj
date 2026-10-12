(ns lipas.backend.ptv.integration-test
  "Error reporting of the PTV HTTP wrapper (`ptv/http`). No PTV calls: the
   clj-http request and the token lookup are stubbed."
  (:require [clj-http.client :as client]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [lipas.backend.ptv.integration :as sut]
            [taoensso.timbre :as log]))

(def ^:private token "secret-bearer-token")

(defn- call-failing
  "Calls `sut/http` with PTV answering `status`/`body`. Returns the thrown
   exception and the log messages written meanwhile."
  [status body & {:keys [form-params]
                  :or {form-params {:sourceId "lipas-org-1-516089"
                                    :publishingStatus "Published"}}}]
  (let [logged (atom [])]
    (with-redefs [sut/get-token (constantly token)
                  client/request (fn [_] (throw (ex-info "clj-http: status"
                                                         {:status status :body body})))]
      (log/with-config {:min-level :info
                        :appenders {:capture {:enabled? true
                                              :fn (fn [d] (swap! logged conj (force (:msg_ d))))}}}
        (let [e (try
                  (sut/http {} "org-1" {:url "https://ptv.example/api/v11/ServiceChannel/ServiceLocation/ch-1"
                                        :method :put
                                        :form-params form-params})
                  nil
                  (catch clojure.lang.ExceptionInfo e e))]
          {:e e :logged @logged})))))

(deftest ptv-5xx-logs-the-request-body-test
  (testing "a PTV 5xx logs the method, url, response and the exact body we sent"
    (let [{:keys [e logged]} (call-failing 500 "{\"errorMessage\":\"An unexpected error occurred. Trace id: abc-123\"}")
          msg (first (filter #(str/includes? % "Request body") logged))]
      (is (some? e))
      (is (some? msg))
      (is (str/includes? msg "PUT https://ptv.example/api/v11/ServiceChannel/ServiceLocation/ch-1 -> 500"))
      (is (str/includes? msg "Trace id: abc-123"))
      (is (str/includes? msg "\"sourceId\":\"lipas-org-1-516089\""))))
  (testing "4xx errors are self-explanatory and aren't logged here"
    (is (empty? (:logged (call-failing 400 "{\"ServiceChannelNames\":[\"taken\"]}"))))))

(deftest ptv-error-carries-no-bearer-token-test
  (testing "the exception's ex-data (printed into logs with the stack trace) has no token"
    (let [{:keys [e logged]} (call-failing 500 "{}")]
      (is (nil? (get-in (ex-data e) [:req :headers :Authorization])))
      (is (not (str/includes? (pr-str (ex-data e)) token)))
      (is (not-any? #(str/includes? % token) logged)))))

(deftest ptv-5xx-request-body-is-printable-test
  (testing "non-ASCII is escaped so the log pipeline can't mangle it"
    (let [{:keys [logged]} (call-failing 500 "{}" :form-params {:name "Ylämyllyn tenniskenttä"})
          msg (first (filter #(str/includes? % "Request body") logged))]
      (is (str/includes? msg "Yl\\u00E4myllyn tenniskentt\\u00E4"))))
  (testing "a body cheshire can't encode still logs, and the PTV error still surfaces"
    (let [{:keys [e logged]} (call-failing 500 "{\"errorMessage\":\"Trace id: x\"}"
                                           :form-params {:weird (Object.)})
          msg (first (filter #(str/includes? % "Request body") logged))]
      (is (str/starts-with? (ex-message e) "HTTP Error: 500"))
      (is (str/includes? msg ":weird")))))

