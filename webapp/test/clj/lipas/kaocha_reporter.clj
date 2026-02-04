(ns lipas.kaocha-reporter
  "Custom Kaocha reporter combining documentation (namespace names) with dots (test results).

   Output format:
   lipas.backend.core-test
     ...F..
   lipas.backend.search-test
     ....
   "
  (:require [clojure.test :as t]
            [kaocha.hierarchy :as hierarchy]
            [kaocha.output :as output]
            [kaocha.report :as report]))

;; Multimethod for the docs-and-dots reporter
(defmulti docs-and-dots* :type :hierarchy #'hierarchy/hierarchy)

;; Default - ignore unknown events
(defmethod docs-and-dots* :default [_])

;; Namespace begins - print the namespace name
(defmethod docs-and-dots* :begin-test-ns [m]
  (t/with-test-out
    (println)
    (println (-> m :ns ns-name))
    (print "  ")
    (flush)))

;; Namespace ends - print newline after the dots
(defmethod docs-and-dots* :end-test-ns [_m]
  (t/with-test-out
    (println)
    (flush)))

;; Test passes - print dot
(defmethod docs-and-dots* :pass [_m]
  (t/with-test-out
    (print ".")
    (flush)))

;; Test fails - print red F
(defmethod docs-and-dots* :kaocha/fail-type [_m]
  (t/with-test-out
    (print (output/colored :red "F"))
    (flush)))

;; Test errors - print red E
(defmethod docs-and-dots* :error [_m]
  (t/with-test-out
    (print (output/colored :red "E"))
    (flush)))

;; Test pending/skipped - print yellow P
(defmethod docs-and-dots* :kaocha/pending [_m]
  (t/with-test-out
    (print (output/colored :yellow "P"))
    (flush)))

;; Summary is handled by the result reporter (shows failures at end)
(defmethod docs-and-dots* :summary [_m])

;; The reporter function combines our custom reporter with kaocha's result reporter
;; which prints detailed failure info at the end
(def docs-and-dots
  "Reporter that prints namespace names followed by dots for each test.

   Configure in tests.edn:
     :reporter lipas.kaocha-reporter/docs-and-dots"
  [docs-and-dots* report/result])
