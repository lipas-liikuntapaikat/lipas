(ns lipas.backend.bulk-operations-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [integrant.core :as ig]
            [lipas.backend.config :as config]
            [lipas.backend.core :as core]
            [lipas.backend.jwt :as jwt]
            [lipas.test-utils :as test-utils]
            [ring.mock.request :as mock]))

;;; Test system setup ;;;

(defonce test-system (atom nil))

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
    (test-utils/prune-db! test-utils/db)
    (test-utils/prune-es!)
    (f)))

;;; Helper Functions ;;;

(defn test-app []
  (:lipas/app @test-system))

(defn test-db []
  (:lipas/db @test-system))

(defn test-search []
  (:lipas/search @test-system))

(defn gen-admin-user []
  (test-utils/gen-user {:db? true
                        :admin? true
                        :permissions {:roles [{:role "admin"}]}}))

(defn gen-city-manager-user [city-code]
  (test-utils/gen-user {:db? true
                        :permissions {:roles [{:role "city-manager"
                                               :city-code [city-code]}]}}))

(defn gen-site-manager-user [lipas-id]
  (test-utils/gen-user {:db? true
                        :permissions {:roles [{:role "site-manager"
                                               :lipas-id [lipas-id]}]}}))

(defn gen-regular-user []
  (test-utils/gen-user {:db? true
                        :permissions {:roles [{:role "default"}]}}))

(defn safe-parse-json [resp]
  (try
    (test-utils/<-json (:body resp))
    (catch Exception _
      nil)))

(defn- create-test-sports-sites
  "Creates test sports sites in database and search index"
  []
  (let [admin-user (gen-admin-user)
        timestamp (System/currentTimeMillis)

        ;; Generate base sites and customize them
        site1 (-> (test-utils/gen-sports-site)
                  (assoc :lipas-id 1)
                  (assoc :name (str "Helsinki Pool " timestamp)
                         :email "old@helsinki.fi"
                         :phone-number "+358401111111"
                         :www "old.helsinki.fi"
                         :reservations-link "old-booking.helsinki.fi")
                  (assoc-in [:type :type-code] 1110)
                  (assoc-in [:location :city :city-code] 91))

        site2 (-> (test-utils/gen-sports-site)
                  (assoc :lipas-id 2)
                  (assoc :name (str "Espoo Gym " timestamp)
                         :email "old@espoo.fi"
                         :phone-number "+358402222222"
                         :www nil
                         :reservations-link nil)
                  (assoc-in [:type :type-code] 1120)
                  (assoc-in [:location :city :city-code] 49))

        site3 (-> (test-utils/gen-sports-site)
                  (assoc :lipas-id 3)
                  (assoc :name (str "Tampere Track " timestamp)
                         :email nil
                         :phone-number nil
                         :www "tampere.fi"
                         :reservations-link "booking.tampere.fi")
                  (assoc-in [:type :type-code] 1130)
                  (assoc-in [:location :city :city-code] 837))]

    ;; Save all sites to database and search index
    (mapv (fn [site]
            (core/upsert-sports-site!* (test-db) admin-user site)
            (core/index! (test-search) site true)
            site)
          [site1 site2 site3])))

;;; Tests ;;;

(deftest mass-update-success-admin-test
  (testing "Admin user can mass update contact info for multiple sites"

    (let [admin-user (gen-admin-user)
          token (jwt/create-token admin-user)
          sites (create-test-sports-sites)
          lipas-ids (mapv :lipas-id sites)

          contact-updates {:email "contact@helsinki.fi"
                           :phone-number "+358 9 123 4567"
                           :www "helsinki.fi/sports"
                           :reservations-link "booking.helsinki.fi"}

          payload {:lipas-ids lipas-ids
                   :updates contact-updates}

          resp ((test-app) (-> (mock/request :post "/api/actions/mass-update-sports-sites")
                               (mock/content-type "application/json")
                               (mock/body (test-utils/->json payload))
                               (test-utils/token-header token)))
          body (safe-parse-json resp)]

      (is (= 200 (:status resp)))
      (is (some? body))
      (is (= (count lipas-ids) (:total-updated body)))
      (is (= lipas-ids (:updated-sites body)))

      ;; Verify the sites were actually updated
      (doseq [lipas-id lipas-ids]
        (let [updated-site (core/get-sports-site2 (test-search) lipas-id :none)]
          (is (= "contact@helsinki.fi" (:email updated-site)))
          (is (= "+358 9 123 4567" (:phone-number updated-site)))
          (is (= "helsinki.fi/sports" (:www updated-site)))
          (is (= "booking.helsinki.fi" (:reservations-link updated-site))))))))

(deftest mass-update-city-manager-permissions-test
  (testing "City manager can only update sites in their city"
    (let [sites (create-test-sports-sites)
          helsinki-sites (filter #(= 91 (get-in % [:location :city :city-code])) sites)
          espoo-sites (filter #(= 49 (get-in % [:location :city :city-code])) sites)
          tampere-sites (filter #(= 837 (get-in % [:location :city :city-code])) sites)

          ;; Create city manager for Helsinki (city-code 91)
          helsinki-manager (gen-city-manager-user 91)
          token (jwt/create-token helsinki-manager)

          contact-updates {:email "helsinki@city.fi"}]

      ;; Test 1: Should succeed for Helsinki sites
      (when (seq helsinki-sites)
        (let [helsinki-lipas-ids (mapv :lipas-id helsinki-sites)
              payload {:lipas-ids helsinki-lipas-ids :updates contact-updates}
              resp ((test-app) (-> (mock/request :post "/api/actions/mass-update-sports-sites")
                                   (mock/content-type "application/json")
                                   (mock/body (test-utils/->json payload))
                                   (test-utils/token-header token)))
              body (safe-parse-json resp)]

          (is (= 200 (:status resp)))
          (is (= (count helsinki-lipas-ids) (:total-updated body)))))

      ;; Test 2: Should fail for Espoo sites (permission denied)
      (when (seq espoo-sites)
        (let [espoo-lipas-ids (mapv :lipas-id espoo-sites)
              payload {:lipas-ids espoo-lipas-ids :updates contact-updates}
              resp ((test-app) (-> (mock/request :post "/api/actions/mass-update-sports-sites")
                                   (mock/content-type "application/json")
                                   (mock/body (test-utils/->json payload))
                                   (test-utils/token-header token)))]

          (is (= 500 (:status resp))) ; Should throw exception for unauthorized sites
          ))

      ;; Test 3: Should fail for mixed sites (some authorized, some not)
      (when (and (seq helsinki-sites) (seq tampere-sites))
        (let [mixed-lipas-ids (concat (mapv :lipas-id helsinki-sites)
                                      (mapv :lipas-id tampere-sites))
              payload {:lipas-ids mixed-lipas-ids :updates contact-updates}
              resp ((test-app) (-> (mock/request :post "/api/actions/mass-update-sports-sites")
                                   (mock/content-type "application/json")
                                   (mock/body (test-utils/->json payload))
                                   (test-utils/token-header token)))]

          (is (= 500 (:status resp))) ; Should throw exception for unauthorized sites
          )))))

(deftest mass-update-site-manager-permissions-test
  (testing "Site manager can only update their specific sites"
    (let [sites (create-test-sports-sites)
          target-site (first sites)
          other-sites (rest sites)

          ;; Create site manager for one specific site
          site-manager (gen-site-manager-user (:lipas-id target-site))
          token (jwt/create-token site-manager)

          contact-updates {:email "site@manager.fi"}]

      ;; Test 1: Should succeed for authorized site
      (let [payload {:lipas-ids [(:lipas-id target-site)] :updates contact-updates}
            resp ((test-app) (-> (mock/request :post "/api/actions/mass-update-sports-sites")
                                 (mock/content-type "application/json")
                                 (mock/body (test-utils/->json payload))
                                 (test-utils/token-header token)))
            body (safe-parse-json resp)]

        (is (= 200 (:status resp)))
        (is (= 1 (:total-updated body)))
        (is (= [(:lipas-id target-site)] (:updated-sites body))))

      ;; Test 2: Should fail for unauthorized sites
      (when (seq other-sites)
        (let [other-lipas-ids (mapv :lipas-id other-sites)
              payload {:lipas-ids other-lipas-ids :updates contact-updates}
              resp ((test-app) (-> (mock/request :post "/api/actions/mass-update-sports-sites")
                                   (mock/content-type "application/json")
                                   (mock/body (test-utils/->json payload))
                                   (test-utils/token-header token)))]

          (is (= 500 (:status resp))) ; Should throw exception
          )))))

(deftest mass-update-empty-fields-test
  (testing "Mass update can clear fields by setting them to nil"
    (let [admin-user (gen-admin-user)
          token (jwt/create-token admin-user)
          sites (create-test-sports-sites)
          target-site (first sites)

          ;; Clear all contact fields
          contact-updates {:email nil
                           :phone-number nil
                           :www nil
                           :reservations-link nil}

          payload {:lipas-ids [(:lipas-id target-site)] :updates contact-updates}

          resp ((test-app) (-> (mock/request :post "/api/actions/mass-update-sports-sites")
                               (mock/content-type "application/json")
                               (mock/body (test-utils/->json payload))
                               (test-utils/token-header token)))
          body (safe-parse-json resp)]

      (is (= 200 (:status resp)))
      (is (= 1 (:total-updated body)))

      ;; Verify the fields were actually cleared
      (let [updated-site (core/get-sports-site2 (test-search) (:lipas-id target-site) :none)]
        (is (nil? (:email updated-site)))
        (is (nil? (:phone-number updated-site)))
        (is (nil? (:www updated-site)))
        (is (nil? (:reservations-link updated-site)))))))

(deftest mass-update-authentication-required-test
  (testing "Mass update requires authentication"
    (let [sites (create-test-sports-sites)
          lipas-ids (mapv :lipas-id sites)
          payload {:lipas-ids lipas-ids :updates {:email "test@example.com"}}

          ;; Request without token
          resp ((test-app) (-> (mock/request :post "/api/actions/mass-update-sports-sites")
                               (mock/content-type "application/json")
                               (mock/body (test-utils/->json payload))))]

      (is (= 401 (:status resp))))))

(deftest mass-update-invalid-payload-test
  (testing "Mass update validates request payload"
    (let [admin-user (gen-admin-user)
          token (jwt/create-token admin-user)]

      ;; Test with invalid lipas-ids (should be integers)
      (let [invalid-payload {:lipas-ids ["not-a-number"] :updates {:email "test@example.com"}}
            resp ((test-app) (-> (mock/request :post "/api/actions/mass-update-sports-sites")
                                 (mock/content-type "application/json")
                                 (mock/body (test-utils/->json invalid-payload))
                                 (test-utils/token-header token)))]

        (is (= 400 (:status resp))))

      ;; Test with invalid email format
      (let [sites (create-test-sports-sites)
            site-id (:lipas-id (first sites))
            invalid-payload {:lipas-ids [site-id] :updates {:email "not-an-email"}}
            resp ((test-app) (-> (mock/request :post "/api/actions/mass-update-sports-sites")
                                 (mock/content-type "application/json")
                                 (mock/body (test-utils/->json invalid-payload))
                                 (test-utils/token-header token)))]

        (is (= 400 (:status resp)))))))

(deftest get-editable-sites-admin-test
  (testing "Admin user can retrieve all sites"
    (let [admin-user (gen-admin-user)
          token (jwt/create-token admin-user)
          _ (create-test-sports-sites)

          ;; Get editable sites
          resp ((test-app) (-> (mock/request :get "/api/actions/get-editable-sports-sites")
                               (test-utils/token-header token)))

          body (safe-parse-json resp)]

      (is (= 200 (:status resp)))
      (is (coll? body))
      (is (>= (count body) 3)) ;; Should have at least the 3 test sites

      ;; Check that all expected fields are present
      (when (seq body)
        (let [site (first body)]
          (is (contains? site :lipas-id))
          (is (contains? site :name))
          (is (contains? site :type))
          (is (contains? site :location))
          (is (contains? site :admin))
          (is (contains? site :owner))
          (is (contains? site :email))
          (is (contains? site :phone-number))
          (is (contains? site :www))
          (is (contains? site :reservations-link)))))))

(deftest get-editable-sites-city-manager-test
  (testing "City manager can only retrieve sites in their city"
    (let [_ (create-test-sports-sites)
          ;; Create city manager for Helsinki (city-code 91)
          helsinki-manager (gen-city-manager-user 91)
          token (jwt/create-token helsinki-manager)

          ;; Get editable sites
          resp ((test-app) (-> (mock/request :get "/api/actions/get-editable-sports-sites")
                               (test-utils/token-header token)))

          body (safe-parse-json resp)]

      (is (= 200 (:status resp)))
      (is (coll? body))

      ;; Check that only Helsinki sites are returned
      (when (seq body)
        (doseq [site body]
          (is (= 91 (get-in site [:location :city :city-code]))))))))

(deftest get-editable-sites-authentication-required-test
  (testing "Get editable sites requires authentication"
    (let [_ (create-test-sports-sites)

          ;; Request without token
          resp ((test-app) (mock/request :get "/api/actions/get-editable-sports-sites"))]

      (is (= 401 (:status resp))))))

(comment
  (setup-test-system!)
  (create-test-sports-sites)
  (def xxxx *1)
  (test-utils/prune-es!)
  (test-utils/prune-db!)
  (def admin (gen-admin-user))
  (actions/get-editable-sites (test-search) admin)
  (require '[lipas.backend.search :as search])
  (search/search (:client (test-search))
                 (get-in (test-search) [:indices :sports-site :search])
                 {:query {:match_all {}}, :size 10000})
  @test-system
  ;; Run individual tests
  (clojure.test/run-test-var #'mass-update-success-admin-test)
  (clojure.test/run-test-var #'mass-update-city-manager-permissions-test)
  (clojure.test/run-test-var #'mass-update-site-manager-permissions-test)
  (clojure.test/run-test-var #'mass-update-empty-fields-test)
  (clojure.test/run-test-var #'mass-update-authentication-required-test)
  (clojure.test/run-test-var #'mass-update-invalid-payload-test))
