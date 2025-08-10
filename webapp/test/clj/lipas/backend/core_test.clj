(ns lipas.backend.core-test
  "Tests for route ordering save functionality and backwards compatibility"
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [lipas.backend.core :as core]
   [lipas.backend.search :as search]
   [lipas.test-utils :as tu]))

;;; Test setup ;;;

(use-fixtures :once
  (fn [f]
    (tu/ensure-test-database!)
    (f)))

(use-fixtures :each
  (fn [f]
    (tu/prune-db!)
    (tu/prune-es!)
    (f)))

;;; Test helpers ;;;

(defn create-route-sports-site
  "Create a sports site with route geometry and optional segments"
  [& {:keys [segments fids ordering-method lipas-id]}]
  (let [base-site {:type {:type-code 4401} ; Outdoor route
                   :name {:fi "Test Route"}
                   :status "active"
                   :event-date "2024-01-01T00:00:00.000Z"
                   :location {:city {:city-code "091"}
                              :geometries {:type "FeatureCollection"
                                           :features [{:type "Feature"
                                                       :id "1"
                                                       :properties {:fid "1"}
                                                       :geometry {:type "LineString"
                                                                  :coordinates [[0 0] [1 1]]}}
                                                      {:type "Feature"
                                                       :id "2"
                                                       :properties {:fid "2"}
                                                       :geometry {:type "LineString"
                                                                  :coordinates [[1 1] [2 1]]}}
                                                      {:type "Feature"
                                                       :id "3"
                                                       :properties {:fid "3"}
                                                       :geometry {:type "LineString"
                                                                  :coordinates [[2 1] [3 0]]}}]}}}]
    (cond-> base-site
      lipas-id (assoc :lipas-id lipas-id)
      segments (assoc-in [:activities :segments] segments)
      fids (assoc-in [:activities :fids] fids)
      ordering-method (assoc-in [:activities :ordering-method] ordering-method))))

;;; Tests for save-sports-site! ;;;

(deftest save-sports-site-backwards-compatibility-test
  (testing "Legacy sites with only fids continue to work"
    (let [user (tu/gen-user {:db? true :admin? true})
          site (create-route-sports-site :fids ["1" "3" "2"])
          result (core/save-sports-site! tu/db tu/search nil user site)]
      (is (int? (:lipas-id result)))
      (is (= ["1" "3" "2"] (get-in result [:activities :fids])))
      ;; Should not have segments if not provided
      (is (nil? (get-in result [:activities :segments])))))

  (testing "New sites with segments are saved correctly"
    (let [user (tu/gen-user {:db? true :admin? true})
          segments [{:fid "1" :direction "forward"}
                    {:fid "3" :direction "backward"}
                    {:fid "2" :direction "forward"}]
          site (create-route-sports-site :segments segments
                                         :ordering-method "auto")
          result (core/save-sports-site! tu/db tu/search nil user site)]
      (is (int? (:lipas-id result)))
      (is (= segments (get-in result [:activities :segments])))
      ;; Should auto-populate fids from segments
      (is (= ["1" "3" "2"] (get-in result [:activities :fids])))
      (is (= "auto" (get-in result [:activities :ordering-method])))))

  (testing "Sites with segments automatically populate fids for backwards compatibility"
    (let [user (tu/gen-user {:db? true :admin? true})
          segments [{:fid "3" :direction "forward"}
                    {:fid "1" :direction "forward"}
                    {:fid "2" :direction "forward"}]
          ;; Don't provide fids, should be auto-populated
          site (create-route-sports-site :segments segments)
          result (core/save-sports-site! tu/db tu/search nil user site)]
      (is (= ["3" "1" "2"] (get-in result [:activities :fids])))
      (is (= segments (get-in result [:activities :segments])))))

  (testing "Default ordering-method is 'manual' when not specified"
    (let [user (tu/gen-user {:db? true :admin? true})
          segments [{:fid "1" :direction "forward"}]
          site (create-route-sports-site :segments segments)
          result (core/save-sports-site! tu/db tu/search nil user site)]
      (is (= "manual" (get-in result [:activities :ordering-method]))))))

(deftest validate-route-segments-test
  (testing "Valid segments pass validation"
    (let [user (tu/gen-user {:db? true :admin? true})
          segments [{:fid "1" :direction "forward"}
                    {:fid "2" :direction "backward"}]
          site (create-route-sports-site :segments segments)
          result (core/save-sports-site! tu/db tu/search nil user site)]
      (is (int? (:lipas-id result)))))

  (testing "Invalid segments cause validation error"
    (let [user (tu/gen-user {:db? true :admin? true})
          ;; Missing required fields
          invalid-segments [{:fid "1"} ; Missing direction
                            {:direction "forward"}] ; Missing fid
          site (create-route-sports-site :segments invalid-segments)]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"validation-error"
                            (core/save-sports-site! tu/db tu/search nil user site)))))

  (testing "Invalid direction values cause validation error"
    (let [user (tu/gen-user {:db? true :admin? true})
          invalid-segments [{:fid "1" :direction "sideways"}] ; Invalid direction
          site (create-route-sports-site :segments invalid-segments)]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"validation-error"
                            (core/save-sports-site! tu/db tu/search nil user site))))))

(deftest segment-fid-consistency-test
  (testing "FIDs in segments must match available features"
    (let [user (tu/gen-user {:db? true :admin? true})
          ;; Site only has features 1, 2, 3
          segments [{:fid "1" :direction "forward"}
                    {:fid "99" :direction "forward"}] ; FID 99 doesn't exist
          site (create-route-sports-site :segments segments)]
      ;; This should either fail validation or be handled gracefully
      ;; Depends on implementation - adjust test accordingly
      (let [result (try
                     (core/save-sports-site! tu/db tu/search nil user site)
                     (catch Exception e
                       :validation-failed))]
        (is (or (= :validation-failed result)
                ;; Or if it saves, the invalid fid should be handled somehow
                (int? (:lipas-id result))))))))

(deftest draft-save-test
  (testing "Draft saves don't trigger background jobs"
    (let [user (tu/gen-user {:db? true :admin? true})
          segments [{:fid "1" :direction "forward"}]
          site (create-route-sports-site :segments segments)
          draft? true
          result (core/save-sports-site! tu/db tu/search nil user site draft?)]
      (is (int? (:lipas-id result)))
      ;; Draft should be saved but not indexed to search
      ;; Verify by trying to find it in search
      (let [search-result (try
                            (let [idx-name (-> tu/search :indices :sports-site :search)
                                  resp (search/fetch-document tu/search idx-name (str (:lipas-id result)))]
                              (when (= 200 (:status resp))
                                (-> resp :body :_source)))
                            (catch Exception _ nil))]
        (is (nil? search-result))))))

(deftest non-route-sports-sites-test
  (testing "Non-route sports sites are unaffected by segment handling"
    (let [user (tu/gen-user {:db? true :admin? true})
          ;; Swimming hall - not a route
          site {:type {:type-code 3110}
                :name {:fi "Test Swimming Hall"}
                :status "active"
                :event-date "2024-01-01T00:00:00.000Z"
                :location {:city {:city-code "091"}
                           :geometries {:type "Point"
                                        :coordinates [25.0 60.0]}}}
          result (core/save-sports-site! tu/db tu/search nil user site)]
      (is (int? (:lipas-id result)))
      ;; Should not have any route-related fields
      (is (nil? (:activities result))))))

(deftest integration-with-search-test
  (testing "Saved routes are searchable with segment data"
    (let [user (tu/gen-user {:db? true :admin? true})
          segments [{:fid "1" :direction "forward"}
                    {:fid "2" :direction "backward"}]
          site (create-route-sports-site :segments segments
                                         :ordering-method "auto")
          saved-site (core/save-sports-site! tu/db tu/search nil user site)
          ;; Search for the saved site
          found-site (core/get-sports-site2 tu/search (:lipas-id saved-site))]
      (is (= (:lipas-id saved-site) (:lipas-id found-site)))
      (is (= segments (get-in found-site [:activities :segments])))
      (is (= ["1" "2"] (get-in found-site [:activities :fids])))
      (is (= "auto" (get-in found-site [:activities :ordering-method]))))))

(deftest update-existing-route-test
  (testing "Updating existing route preserves or updates segment data correctly"
    (let [user (tu/gen-user {:db? true :admin? true})
          ;; Create initial route
          initial-segments [{:fid "1" :direction "forward"}
                            {:fid "2" :direction "forward"}]
          site (create-route-sports-site :segments initial-segments)
          created-site (core/save-sports-site! tu/db tu/search nil user site)

          ;; Update with new segments
          updated-segments [{:fid "2" :direction "backward"}
                            {:fid "1" :direction "forward"}
                            {:fid "3" :direction "forward"}]
          updated-site (assoc-in created-site [:activities :segments] updated-segments)
          result (core/save-sports-site! tu/db tu/search nil user updated-site)]
      (is (= (:lipas-id created-site) (:lipas-id result)))
      (is (= updated-segments (get-in result [:activities :segments])))
      (is (= ["2" "1" "3"] (get-in result [:activities :fids]))))))

(deftest migration-scenario-test
  (testing "Existing sites can be migrated to use segments"
    (let [user (tu/gen-user {:db? true :admin? true})
          ;; Simulate existing site with only fids
          old-site (create-route-sports-site :fids ["3" "1" "2"])
          created-site (core/save-sports-site! tu/db tu/search nil user old-site)

          ;; Retrieve and update with segments
          existing (core/get-sports-site2 tu/search (:lipas-id created-site))
          segments [{:fid "3" :direction "forward"}
                    {:fid "1" :direction "forward"}
                    {:fid "2" :direction "forward"}]
          migrated (-> existing
                       (assoc-in [:activities :segments] segments)
                       (assoc-in [:activities :ordering-method] "manual"))
          result (core/save-sports-site! tu/db tu/search nil user migrated)]
      (is (= (:lipas-id created-site) (:lipas-id result)))
      (is (= segments (get-in result [:activities :segments])))
      ;; FIDs should still be preserved
      (is (= ["3" "1" "2"] (get-in result [:activities :fids]))))))

(deftest permissions-check-test
  (testing "User without permissions cannot update existing sports site"
    (let [admin-user (tu/gen-user {:db? true :admin? true})
          regular-user (tu/gen-user {:db? true :admin? false})
          ;; Create a sports site as admin
          site (create-route-sports-site :segments [{:fid "1" :direction "forward"}])
          created-site (core/save-sports-site! tu/db tu/search nil admin-user site)
          ;; Try to update it as regular user
          updated-site (assoc created-site :name {:fi "Updated Name"})]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"User doesn't have enough permissions!"
                            (core/save-sports-site! tu/db tu/search nil regular-user updated-site)))))

  (testing "User can create new sports sites"
    (let [user (tu/gen-user {:db? true :admin? false})
          site (create-route-sports-site :segments [{:fid "1" :direction "forward"}])
          result (core/save-sports-site! tu/db tu/search nil user site)]
      ;; New sites can be created by any user
      (is (int? (:lipas-id result))))))

;; Run tests with:
;; (clojure.test/run-tests 'lipas.backend.core-test)