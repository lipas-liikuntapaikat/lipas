(ns legacy-api.handler-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures] :as t]
   [integrant.core :as ig]
   [lipas.backend.config :as config]
   [lipas.schema.core]
   [lipas.test-utils :as test-utils]
   [ring.mock.request :as mock]))


;;; Utility functions ;;;

#_(defn gen-admin-user []
  (test-utils/gen-user {:db? true
                        :admin? true
                        :permissions {:roles [{:role "admin"}]}}))


;;; Test system setup ;;;

(defonce test-system (atom nil))

;;; Helper Functions ;;;

(defn test-app []
  (:lipas/app @test-system))

(defn test-db []
  (:lipas/db @test-system))

(defn test-search []
  (:lipas/search @test-system))

(defn setup-test-system! []
  ;; Ensure database is properly initialized
  (test-utils/ensure-test-database!)
  ;; Initialize test system using test config (with _test database suffix)
  (reset! test-system
          (ig/init (config/->system-config test-utils/config))))

(defn teardown-test-system! []
  (when @test-system
    (ig/halt! @test-system)
    (reset! test-system nil)))

;;; Fixtures ;;;

(use-fixtures :once
  (fn [f]
    (setup-test-system!)
    (f)
    (teardown-test-system!)))

(use-fixtures :each
  (fn [f]
    ;; Clean all tables before each test
    (test-utils/prune-db!)
    (test-utils/prune-es!)
    (f)))

;;; The tests ;;;

(deftest api-smoke-test
  (testing "API smoke test"
    
    (let [resp ((test-app) (-> (mock/request :get "/rest/api/sports-places")
                                  (mock/content-type "application/json")))]
    
      (is (= 206 (:status resp))))))


(comment
  (clojure.test/run-test-var #'api-smoke-test)
  )