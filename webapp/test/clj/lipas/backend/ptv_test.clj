(ns lipas.backend.ptv-test
  (:require [clojure.test :refer [deftest is testing use-fixtures] :as t]
            [clojure.pprint :refer [pprint]]
            [lipas.backend.core :as core]
            [lipas.backend.jwt :as jwt]
            [lipas.backend.org :as backend-org]
            [lipas.backend.ptv.core :as ptv-core]
            [lipas.backend.ptv.integration :as ptv-integ]
            [lipas.data.ptv :as ptv-data]
            [lipas.data.types :as types]
            [lipas.test-utils :refer [<-json] :as tu]
            [lipas.utils :as utils]
            [ring.mock.request :as mock]))

;;; Test system setup ;;;
(defonce test-system (atom nil))

;;; Fixtures ;;;
(let [{:keys [once each]} (tu/full-system-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

;;; Accessors ;;;
(defn test-app [] (:lipas/app @test-system))
(defn test-db [] (:lipas/db @test-system))
(defn test-search [] (:lipas/search @test-system))

(defn create-org! [org]
  (let [id (java.util.UUID/randomUUID)
        org (assoc org :id id)]
    (backend-org/create-org (test-db) org)
    org))

(deftest send-audit-notification-test
  (t/testing "Sends notification to PTV managers using provided stats"
    (let [;; Setup: org, users
          org (create-org! {:name "Test Org"
                            :ptv-data {:org-id "test-ptv-org"
                                       :city-codes [91]}})
          org-id (:id org)
          ptv-manager (tu/gen-user {:db? true
                                    :db-component (test-db)
                                    :permissions {:roles [{:role :ptv-manager
                                                           :city-code [91]
                                                           :org-id [(str org-id)]}]}})
          auditor (tu/gen-user {:db? true :admin? true
                                :db-component (test-db)
                                :permissions {:roles [{:role :ptv-auditor}]}})
          token (jwt/create-token auditor)

          stats {:total-sites 5
                 :summary {:approved 3
                           :changes-requested 2}
                 :description {:approved 4
                               :changes-requested 1}}]

      ;; Test
      (let [resp ((test-app) (-> (mock/request :post "/api/actions/send-audit-notification")
                                 (mock/json-body {:org-id org-id :stats stats})
                                 (tu/token-header token)))
            body (<-json (:body resp))]
        (is (= 200 (:status resp)))
        (is (= 1 (:sent body)))
        (is (= (:email ptv-manager) (first (:recipients body)))))))

  (t/testing "Requires :ptv/audit privilege"
    (let [org (create-org! {:name "Test Org 2"
                            :ptv-data {:org-id "test-ptv-org-2"
                                       :city-codes [91]}})
          org-id (:id org)
          regular-user (tu/gen-user {:db? true :db-component (test-db)})
          token (jwt/create-token regular-user)
          stats {:total-sites 0
                 :summary {:approved 0
                           :changes-requested 0}
                 :description {:approved 0
                               :changes-requested 0}}]

      (let [resp ((test-app) (-> (mock/request :post "/api/actions/send-audit-notification")
                                 (mock/json-body {:org-id org-id :stats stats})
                                 (tu/token-header token)))]
        (is (= 403 (:status resp)))))))

;; This test requires PTV training environment to be operational.
;; It's up only on weekdays between 8-17 Finnish time...

#_(deftest ^:ptv ^:integration init-site-ptv
    (let [user     (tu/gen-user {:db? true :admin? true})
          token    (jwt/create-token user)

          rev1     (-> (tu/gen-sports-site)
                       (assoc :status "active")
                     ;; need to set up realistic type and location for the ptv integration to work
                     ;; yleisurheilukenttä
                       (assoc-in [:type :type-code] 1210)
                       (assoc-in [:location :postal-code] "91900")
                       (assoc-in [:location :city :city-code] 425))
          _        (core/upsert-sports-site!* db user rev1)
          lipas-id (:lipas-id rev1)
          resp     (app (-> (mock/request :get (str "/api/sports-sites/" lipas-id))
                            (mock/content-type "application/json")
                            (tu/token-header token)))
          body     (<-json (:body resp))
          site     body

        ;; TODO: Use another id for tests run?
        ;; Liminka, org 9
          org-id "7fdd7f84-e52a-4c17-a59a-d7c2a3095ed5"
          org-langs ["fi" "se" "en"]
        ;; re-frame app-db defaults
          types types/all
          default-settings {:service-integration         "lipas-managed"
                            :service-channel-integration "lipas-managed"
                            :descriptions-integration    "lipas-managed-ptv-fields"
                            :integration-interval        "manual"}

          sports-sites (->> [body]
                            (utils/index-by :lipas-id))]

      (is (some? lipas-id))
      (is (= 200 (:status resp)))
    ;; (is (some? (:ptv body)))

      (let [;; Get list of services already on PTV
            resp (app (-> (mock/request :post (str "/api/actions/fetch-ptv-services"))
                          (mock/json-body {:org-id org-id})
                          (tu/token-header token)))
            services (->> (<-json (:body resp))
                          :itemList
                          (utils/index-by :id))

            _ (is (= 200 (:status resp)))
            _ (is (> (count services) 1))

          ;; Get list of service channels already on PTV
            resp (app (-> (mock/request :post (str "/api/actions/fetch-ptv-service-channels"))
                          (mock/json-body {:org-id org-id})
                          (tu/token-header token)))
            service-channels (->> (<-json (:body resp))
                                  :itemList
                                  (utils/index-by :id))

            _ (is (= 200 (:status resp)))
            _ (is (> (count service-channels) 1))

          ;; Initializing a PTV data for a site that wasn't previously synced to ptv works like this:
          ;; - summary and description are written or generated
          ;; - after that this ptv-input functions should return that the site is valid (this is used in the view component)
          ;; - save-ptv-service-location is called BUT this doesn't take the
          ;;   ptv-input but the lipas-id and ptv-meta as parameters

          ;; TODO: is this ptv-input data useful? Could everything (the view components) just use raw site data directly?
            ptv-sites (for [site (vals sports-sites)]
                        (ptv-data/sports-site->ptv-input {:types types
                                                          :org-id org-id
                                                          :org-defaults default-settings
                                                          :org-langs org-langs}
                                                         service-channels
                                                         services
                                                         site))]

        (is (= 1 (count ptv-sites)))

        (is (= []
               (ptv-data/resolve-missing-services org-id
                                                  services
                                                  ptv-sites)))

      ;; Add ptv summary and description to the site, enabling the
      ;; ptv integration for the site -> will create Service Location.
        (let [updated-site (assoc site :ptv (merge default-settings
                                                   {:sync-enabled true
                                                    :org-id org-id
                                                  ;; TODO: Need to setup the link to services
                                                    :service-ids []
                                                    :summary {:fi "foobar"
                                                              :se "foobar"
                                                              :en "foobar"}
                                                    :description {:fi "foobar"
                                                                  :se "foobar"
                                                                  :en "foobar"}}))
              resp (app (-> (mock/request :post (str "/api/sports-sites"))
                            (mock/json-body updated-site)
                            (tu/token-header token)))
              body (<-json (:body resp))]
        ;; Responds with 201 for both creates and updates
          (is (= 201 (:status resp)))
          (println body)
          (is (some? (:last-sync (:ptv body))))
          (is (= "Published" (:publishing-status (:ptv body))))))

    ;; TODO: Archive the site in Lipas and PTV
      ))

(deftest upsert-ptv-service-location-refetches-channel-after-connection-updates
  ;; Regression: the PUT response is captured BEFORE the separate
  ;; update-service-connections calls land, so its :services list is stale.
  ;; The FE caches that response as service-channels[id], and drift detection
  ;; in compute-service-channel-drift sees stale services → reports
  ;; :content-drift → the green "synced" chip never appears immediately
  ;; after sync. The fix refetches the channel via GET after the connection
  ;; updates so the FE caches the canonical post-sync shape.
  (let [calls (atom [])
        record! (fn [tag] (swap! calls conj tag))
        channel-id "channel-uuid"
        old-service-id "service-old"
        new-service-id "service-new"
        ;; PTV's PUT response: :services still has the OLD service-id
        ;; because update-service-connections hasn't run yet at the moment
        ;; PTV serialized this response.
        stale-put-resp {:id channel-id
                        :sourceId "lipas-org-x-12345-2026-04-26T18-12-24.376411Z"
                        :publishingStatus "Published"
                        :services [{:service {:id old-service-id}}]
                        :serviceChannelNames []
                        :serviceChannelDescriptions []}
        ;; Canonical GET response (post-connection-updates): :services has
        ;; the NEW service-id.
        fresh-get-resp (assoc stale-put-resp
                              :services [{:service {:id new-service-id}}])
        site {:lipas-id 12345
              :name "Test Halli"
              :status "active"
              :type {:type-code 1210}
              :location {:city {:city-code 425}
                         :address "Katu 1"
                         :postal-code "91900"
                         :geometries {:type "FeatureCollection"
                                      :features [{:type "Feature"
                                                  :geometry {:type "Point"
                                                             :coordinates [25.0 65.0]}}]}}
              :search-meta {:location {:wgs84-point [25.0 65.0]}}
              :ptv {:org-id "org-x"
                    :source-id "lipas-org-x-12345-2026-04-26T18-12-24.376411Z"
                    :service-channel-ids [channel-id]
                    :service-ids [new-service-id]
                    :sync-enabled true
                    :languages ["fi"]
                    :summary {:fi "summary"}
                    :description {:fi "description"}}}
        ;; First GET (pre-PUT) returns a snapshot with the OLD service-id,
        ;; used for the connection diff. Subsequent GETs (the refetch)
        ;; return the canonical post-update state.
        get-channel-call-count (atom 0)]
    (with-redefs [core/enrich identity
                  ptv-integ/get-org-ptv-config-with-fallback
                  (fn [_ _] {:supported-languages ["fi"]})
                  ptv-integ/get-org-service-channel
                  (fn [_ _ id]
                    (record! :get-channel)
                    (swap! get-channel-call-count inc)
                    (if (= 1 @get-channel-call-count)
                      {:id id
                       :services [{:service {:id old-service-id}}]}
                      fresh-get-resp))
                  ptv-integ/update-service-location
                  (fn [_ _ _]
                    (record! :put)
                    stale-put-resp)
                  ptv-integ/update-service-connections
                  (fn [_ _ _ _]
                    (record! :update-connections))]
      (let [[ptv-resp new-ptv-data]
            (ptv-core/upsert-ptv-service-location!*
              {} {:org-id "org-x" :site site :ptv (:ptv site)})]

        (testing "Returned ptv-resp reflects the post-connection-update state"
          (is (= [new-service-id]
                 (map (comp :id :service) (:services ptv-resp)))
              "ptv-resp must be the fresh GET response, not the stale PUT response"))

        (testing "Call order: pre-PUT GET → PUT → update-connections → refetch GET"
          (is (= [:get-channel :put :update-connections :update-connections :get-channel]
                 @calls)
              "GET must happen AFTER update-service-connections so its :services is canonical"))

        (testing "Sanity: ptv meta still uses LIPAS-side :service-ids"
          (is (= [new-service-id] (:service-ids new-ptv-data))))))))

(deftest upsert-ptv-service-location-falls-back-when-refetch-fails
  ;; If the post-PUT refetch fails (transient PTV error), the function
  ;; must fall back to the PUT response rather than throwing. The chip may
  ;; momentarily look stale, but the sync itself is durable.
  (let [stale-put-resp {:id "channel-uuid"
                        :sourceId "lipas-org-x-12345-2026-04-26T18-12-24.376411Z"
                        :publishingStatus "Published"
                        :services [{:service {:id "service-old"}}]
                        :serviceChannelNames []
                        :serviceChannelDescriptions []}
        site {:lipas-id 12345
              :name "Test Halli"
              :status "active"
              :type {:type-code 1210}
              :location {:city {:city-code 425}
                         :address "Katu 1"
                         :postal-code "91900"
                         :geometries {:type "FeatureCollection"
                                      :features [{:type "Feature"
                                                  :geometry {:type "Point"
                                                             :coordinates [25.0 65.0]}}]}}
              :search-meta {:location {:wgs84-point [25.0 65.0]}}
              :ptv {:org-id "org-x"
                    :source-id "lipas-org-x-12345-2026-04-26T18-12-24.376411Z"
                    :service-channel-ids ["channel-uuid"]
                    :service-ids ["service-new"]
                    :sync-enabled true
                    :languages ["fi"]
                    :summary {:fi "summary"}
                    :description {:fi "description"}}}
        get-channel-call-count (atom 0)]
    (with-redefs [core/enrich identity
                  ptv-integ/get-org-ptv-config-with-fallback
                  (fn [_ _] {:supported-languages ["fi"]})
                  ptv-integ/get-org-service-channel
                  (fn [_ _ _id]
                    (swap! get-channel-call-count inc)
                    (if (= 1 @get-channel-call-count)
                      {:services [{:service {:id "service-old"}}]}
                      (throw (ex-info "PTV down" {:status 503}))))
                  ptv-integ/update-service-location
                  (fn [_ _ _] stale-put-resp)
                  ptv-integ/update-service-connections
                  (fn [_ _ _ _] nil)]
      (let [[ptv-resp _] (ptv-core/upsert-ptv-service-location!*
                           {} {:org-id "org-x" :site site :ptv (:ptv site)})]
        (is (= stale-put-resp ptv-resp)
            "Refetch failure must fall back to PUT response, not throw")))))

(comment
  (t/run-tests *ns*)
  (t/run-test-var #'init-site-ptv))
