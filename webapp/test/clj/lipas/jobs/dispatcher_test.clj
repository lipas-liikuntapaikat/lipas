(ns lipas.jobs.dispatcher-test
  "Tests for job type handlers."
  (:require
   [clojure.set :as set]
   [clojure.test :refer [deftest testing is use-fixtures]]
   [lipas.backend.email :as email]
   [lipas.jobs.dispatcher :as dispatcher]
   [lipas.jobs.registry :as registry]
   [lipas.test-utils :as test-utils]
   [taoensso.timbre :as log]))

;; Test system setup using shared fixture
(defonce test-system (atom nil))

(let [{:keys [once each]} (test-utils/db-only-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn test-db [] (:lipas/db @test-system))

(defn create-mock-search []
  {:client {:host "mock"} :indices {}})

(deftest registry-dispatcher-consistency-test
  (testing "Every registered job type has a handler"
    (let [handled (set (keys (methods dispatcher/handle-job)))
          registered (set (keys registry/job-types))]
      (is (empty? (set/difference registered handled))
          "Job types registered without a dispatcher handler")))

  (testing "Every handler (except :default) is a registered job type"
    (let [handled (disj (set (keys (methods dispatcher/handle-job))) :default)
          registered (set (keys registry/job-types))]
      (is (empty? (set/difference handled registered))
          "Dispatcher handlers for unregistered job types are dead code"))))

(deftest email-job-handler-test
  (testing "Email job handler processes different email types"
    (let [test-emailer (test-utils/create-test-emailer)
          system {:db (test-db) :emailer test-emailer :search (create-mock-search)}]

      (testing "Basic email sending"
        (dispatcher/handle-job system
                               {:id 1 :type "email"
                                :payload {:to "user@example.com"
                                          :subject "Test Subject"
                                          :body "Hello World"}})
        (is (= 1 (count @(:sent-emails test-emailer))))
        (let [sent (first @(:sent-emails test-emailer))]
          (is (= "user@example.com" (:to sent)))
          (is (= "Test Subject" (:subject sent)))))

      (testing "Reminder email type"
        (with-redefs [email/send-reminder-email!
                      (fn [emailer to _link body]
                        (email/send! emailer {:to to :subject "Reminder" :body body}))]
          (reset! (:sent-emails test-emailer) [])
          (dispatcher/handle-job system
                                 {:id 2 :type "email"
                                  :payload {:type "reminder"
                                            :email "user@example.com"
                                            :link "http://example.com/reminder"
                                            :body "Don't forget!"}})
          (is (= 1 (count @(:sent-emails test-emailer)))))))))

(deftest analysis-job-handler-test
  (testing "Analysis handler recalculates the diversity grid for active sites"
    (let [recalc-called (atom nil)]
      (with-redefs [lipas.backend.core/get-sports-site
                    (fn [_db lipas-id]
                      {:lipas-id lipas-id
                       :status "active"
                       :location {:geometries {:type "FeatureCollection"
                                               :features []}}})
                    lipas.backend.gis/simplify-safe (fn [geom] geom)
                    lipas.backend.analysis.diversity/recalc-grid!
                    (fn [_search geom] (reset! recalc-called geom))]
        (dispatcher/handle-job {:db (test-db) :search (create-mock-search)}
                               {:id 1 :type "analysis" :payload {:lipas-id 12345}})
        (is (some? @recalc-called)))))

  (testing "Analysis is skipped for sites in non-analyzable status"
    (let [recalc-called (atom nil)]
      (with-redefs [lipas.backend.core/get-sports-site
                    (fn [_db lipas-id]
                      {:lipas-id lipas-id
                       :status "out-of-service-permanently"
                       :location {:geometries {:type "FeatureCollection" :features []}}})
                    lipas.backend.analysis.diversity/recalc-grid!
                    (fn [_search geom] (reset! recalc-called geom))]
        (dispatcher/handle-job {:db (test-db) :search (create-mock-search)}
                               {:id 1 :type "analysis" :payload {:lipas-id 12345}})
        (is (nil? @recalc-called) "recalc-grid! must not run for retired sites"))))

  (testing "Analysis throws for a missing sports site"
    (with-redefs [lipas.backend.core/get-sports-site (fn [_ _] nil)]
      (is (thrown-with-msg? Exception #"not found"
                            (dispatcher/handle-job {:db (test-db) :search nil}
                                                   {:id 2 :type "analysis"
                                                    :payload {:lipas-id 99999}}))))))

(deftest webhook-job-handler-test
  (testing "Webhook handler forwards payload to UTP integration"
    (let [received (atom nil)]
      (with-redefs [lipas.integration.utp.webhook/process-v2!
                    (fn [_db _config payload]
                      (log/info "Mock webhook process called" payload)
                      (reset! received payload))]
        (dispatcher/handle-job {:db (test-db)}
                               {:id 1 :type "webhook"
                                :payload {:lipas-ids [1 2]
                                          :operation-type "test-update"}})
        (is (= [1 2] (:lipas-ids @received)))))))

(deftest unknown-job-type-handler-test
  (testing "Unknown job types throw"
    (is (thrown-with-msg? Exception #"Unknown job type"
                          (dispatcher/handle-job {:db (test-db)}
                                                 {:id 1 :type "unknown-job-type"
                                                  :payload {:data "test"}})))))

(deftest handler-error-propagation-test
  (testing "Handler errors propagate so the worker can schedule a retry"
    (testing "Email job with broken emailer"
      (is (thrown? Exception
                   (dispatcher/handle-job {:db (test-db) :emailer nil}
                                          {:id 1 :type "email"
                                           :payload {:to "t@example.com"
                                                     :subject "T" :body "B"}}))))))
