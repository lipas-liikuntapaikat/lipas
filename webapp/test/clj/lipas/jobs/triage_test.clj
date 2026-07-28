(ns lipas.jobs.triage-test
  (:require
    [clojure.test :refer [deftest testing is]]
    [lipas.jobs.triage :as triage]))

(deftest classify-error-test
  (testing "timeout errors"
    (is (= :timeout (triage/classify-error "Job execution timed out after 60 minutes")))
    (is (= :timeout (triage/classify-error "Analysis timed out"))))

  (testing "MML / HTTP connectivity errors"
    (is (= :mml-api (triage/classify-error "clj-http: status 500")))
    (is (= :mml-api (triage/classify-error "java.net.SocketException: Connection reset")))
    (is (= :mml-api (triage/classify-error "java.net.SocketTimeoutException: Read timed out"))
        "SocketTimeout wins over the generic timed-out pattern by order")
    (is (= :mml-api (triage/classify-error "Circuit breaker is open"))))

  (testing "site not found"
    (is (= :site-not-found (triage/classify-error "Sports site not found: 123456"))))

  (testing "search errors"
    (is (= :search (triage/classify-error "spandex.utils :: Response Exception")))
    (is (= :search (triage/classify-error "Elasticsearch bulk index failed"))))

  (testing "out of memory"
    (is (= :oom (triage/classify-error "java.lang.OutOfMemoryError: Java heap space"))))

  (testing "fallback"
    (is (= :other (triage/classify-error "Something completely different")))
    (is (= :other (triage/classify-error "")))
    (is (= :other (triage/classify-error nil)))))
