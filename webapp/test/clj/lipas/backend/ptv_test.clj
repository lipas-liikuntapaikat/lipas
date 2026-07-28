(ns lipas.backend.ptv-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures] :as t]
            [lipas.backend.core :as core]
            [lipas.backend.db.ptv-service :as ptv-service-db]
            [lipas.backend.email :as email]
            [lipas.backend.jwt :as jwt]
            [lipas.backend.org :as backend-org]
            [lipas.backend.ptv.core :as ptv-core]
            [lipas.backend.ptv.integration :as ptv-integ]
            [lipas.data.ptv :as ptv-data]
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

;;; Audit notification ;;;
;;
;; The katselmointi notification's contents are derived server-side from
;; the current audit sample: items in the :waiting-fixes bucket become the
;; email's action list, fully-approved items only a count. Items mid-audit
;; or already fixed (awaiting DVV re-review) appear in neither.

(defn- seed-ptv-site!
  "Seeds + indexes an active city-91 site with PTV texts (:fi \"Tiivistelmä\"
   / \"Kuvaus\") and the given audit map."
  [user lipas-id site-name audit]
  (let [site {:lipas-id lipas-id
              :status "active"
              :event-date "2026-01-01T00:00:00.000Z"
              :name site-name
              :owner "city"
              :admin "city-sports"
              :type {:type-code 1210}
              :location {:city {:city-code 91}
                         :address "Test Street 1"
                         :postal-code "00100"
                         :postal-office "Helsinki"
                         :geometries {:type "FeatureCollection"
                                      :features [{:type "Feature"
                                                  :geometry {:type "Point"
                                                             :coordinates [24.9384 60.1695]}}]}}
              :ptv (cond-> {:org-id "test-ptv-org"
                            :summary {:fi "Tiivistelmä"}
                            :description {:fi "Kuvaus"}}
                     audit (assoc :audit audit))}]
    (core/upsert-sports-site!* (test-db) user site)
    (core/index! (test-search) site true)
    site))

(defn- site-audit
  "Audit map whose :description is approved-and-unchanged; :summary verdict
   and its audited snapshot come from the args, so the caller picks the
   bucket: audited-summary \"Tiivistelmä\" = unchanged content."
  [summary-status audited-summary]
  {:timestamp "2026-07-01T00:00:00.000Z"
   :auditor-id "auditor-1"
   :summary {:status summary-status
             :feedback "Palaute"
             :audited-content {:fi audited-summary}}
   :description {:status "approved"
                 :feedback ""
                 :audited-content {:fi "Kuvaus"}}})

(deftest audit-notification-flow-test
  (let [org (create-org! {:name "Test Org"
                          :ptv-data {:org-id "test-ptv-org"
                                     :city-codes [91]}})
        org-id (:id org)
        ptv-manager (tu/gen-user {:db? true
                                  :db-component (test-db)
                                  :permissions {:roles [{:role :ptv-manager
                                                         :city-code [91]
                                                         :org-id [(str org-id)]}]}})
        ;; PTV managers of an org are its members who hold :ptv/manage;
        ;; membership now lives in the org document.
        _ (backend-org/add-member! (test-db) org-id (:id ptv-manager) {:roles []} nil)
        auditor (tu/gen-user {:db? true :admin? true
                              :db-component (test-db)
                              :permissions {:roles [{:role :ptv-auditor}]}})
        token (jwt/create-token auditor)]

    ;; One site per whose-move state:
    (seed-ptv-site! auditor 9990001 "Korjattava halli"
                    (site-audit "changes-requested" "Tiivistelmä"))
    (seed-ptv-site! auditor 9990002 "Hyväksytty halli"
                    (site-audit "approved" "Tiivistelmä"))
    ;; changes requested, but content edited since = municipality already
    ;; fixed it — must appear in neither number
    (seed-ptv-site! auditor 9990003 "Korjattu halli"
                    (site-audit "changes-requested" "Vanha tiivistelmä"))
    ;; verdict missing on :description = audit still in progress
    (seed-ptv-site! auditor 9990004 "Kesken halli"
                    (dissoc (site-audit "changes-requested" "Tiivistelmä")
                            :description))
    (seed-ptv-site! auditor 9990005 "Auditoimaton halli" nil)

    (t/testing "Payload: only the waiting-fixes site is actionable, only the approved one counts as passed"
      (is (= {:action-items [{:lipas-id 9990001
                              :name "Korjattava halli"
                              :fields [:summary]}]
              :approved-count 1}
             (ptv-core/site-audit-notification-data (test-db) (test-search) org-id))))

    (t/testing "Email leads with the action list and names no already-fixed items"
      (let [emailer (tu/create-test-emailer)
            result (ptv-core/send-audit-notification! (test-db) (test-search) emailer org-id)
            [msg :as sent] @(:sent-emails emailer)]
        (is (= 1 (:sent result)))
        (is (= [(:email ptv-manager)] (:recipients result)))
        (is (= 1 (count sent)))
        (is (= (:email ptv-manager) (:to msg)))
        (is (str/includes? (:subject msg) "korjauspyyntöjä"))
        (is (str/includes? (:plain msg) "Korjattava halli — Tiivistelmä"))
        (is (str/includes? (:plain msg) "Lisäksi 1 kohdetta on katselmoitu ilman muutospyyntöjä."))
        (is (not (str/includes? (:plain msg) "Korjattu halli")))
        (is (not (str/includes? (:plain msg) "Kesken halli")))))

    (t/testing "Preview endpoint returns recipients + the derived contents"
      (let [resp ((test-app) (-> (mock/request :post "/api/actions/get-ptv-audit-notification-preview")
                                 (mock/json-body {:org-id org-id :section "sites"})
                                 (tu/token-header token)))
            body (<-json (:body resp))]
        (is (= 200 (:status resp)))
        (is (= [(:email ptv-manager)] (:recipients body)))
        (is (= [{:lipas-id 9990001 :name "Korjattava halli" :fields ["summary"]}]
               (:action-items body)))
        (is (= 1 (:approved-count body)))))

    (t/testing "Send endpoint takes only the org — contents are derived server-side"
      (let [resp ((test-app) (-> (mock/request :post "/api/actions/send-audit-notification")
                                 (mock/json-body {:org-id org-id})
                                 (tu/token-header token)))
            body (<-json (:body resp))]
        (is (= 200 (:status resp)))
        (is (= 1 (:sent body)))
        (is (= (:email ptv-manager) (first (:recipients body))))))

    (t/testing "Unknown org gives 404"
      (doseq [[uri body] {"/api/actions/get-ptv-audit-notification-preview"
                          {:org-id (java.util.UUID/randomUUID) :section "sites"}
                          "/api/actions/send-audit-notification"
                          {:org-id (java.util.UUID/randomUUID)}}]
        (let [resp ((test-app) (-> (mock/request :post uri)
                                   (mock/json-body body)
                                   (tu/token-header token)))]
          (is (= 404 (:status resp)) uri))))

    (t/testing "Requires :ptv/audit privilege"
      (let [regular-token (jwt/create-token (tu/gen-user {:db? true :db-component (test-db)}))]
        (doseq [[uri body] {"/api/actions/get-ptv-audit-notification-preview"
                            {:org-id org-id :section "sites"}
                            "/api/actions/send-audit-notification"
                            {:org-id org-id}
                            "/api/actions/send-service-audit-notification"
                            {:org-id org-id}}]
          (let [resp ((test-app) (-> (mock/request :post uri)
                                     (mock/json-body body)
                                     (tu/token-header regular-token)))]
            (is (= 403 (:status resp)) uri)))))))

(deftest service-audit-notification-data-test
  (let [org (create-org! {:name "Palvelu Org"
                          :ptv-data {:org-id "test-ptv-org"
                                     :city-codes [91]}})
        author (tu/gen-user {:db? true :db-component (test-db)})
        fixes-id (java.util.UUID/randomUUID)
        ok-id (java.util.UUID/randomUUID)
        fixed-id (java.util.UUID/randomUUID)
        foreign-id (java.util.UUID/randomUUID)
        mk-svc (fn [id svc-name]
                 {:id (str id)
                  :sourceId (str "lipas-" id)
                  :serviceNames [{:type "Name" :language "fi" :value svc-name}]
                  :serviceDescriptions [{:type "Summary" :language "fi" :value "Tiivistelmä"}
                                        {:type "Description" :language "fi" :value "Kuvaus"}]})
        seed-audit! (fn [svc-id audit]
                      (ptv-service-db/insert-service-rev!
                        (test-db)
                        {:org-id (:id org)
                         :source-id (str "lipas-" svc-id)
                         :service-id svc-id
                         :status "active"
                         :author-id (:id author)
                         :event-date (utils/timestamp)
                         :document {:source-id (str "lipas-" svc-id)
                                    :service-id (str svc-id)
                                    :audit audit}}))]
    (seed-audit! fixes-id (site-audit "changes-requested" "Tiivistelmä"))
    (seed-audit! ok-id (site-audit "approved" "Tiivistelmä"))
    (seed-audit! fixed-id (site-audit "changes-requested" "Vanha tiivistelmä"))

    (with-redefs [ptv-core/fetch-ptv-services
                  (fn [_ _] {:itemList [(mk-svc fixes-id "Korjattava palvelu")
                                        (mk-svc ok-id "Hyväksytty palvelu")
                                        (mk-svc fixed-id "Korjattu palvelu")
                                        ;; not LIPAS-managed -> not in the sample
                                        (assoc (mk-svc foreign-id "Vieras palvelu")
                                               :sourceId "muu-lahde")]})]
      (is (= {:action-items [{:service-id (str fixes-id)
                              :name "Korjattava palvelu"
                              :fields [:summary]}]
              :approved-count 1}
             (ptv-core/service-audit-notification-data (test-db) nil (:id org)))))))

(deftest ptv-audit-notification-message-test
  (let [fixes (email/ptv-audit-notification-message
                {:org-name "Testilä"
                 :section :sites
                 :action-items [{:name "Halli" :fields [:summary :description]}]
                 :approved-count 2})
        all-ok (email/ptv-audit-notification-message
                 {:org-name "Testilä"
                  :section :services
                  :action-items []
                  :approved-count 3})]
    (t/testing "Change-request variant"
      (is (str/includes? (:subject fixes) "korjauspyyntöjä"))
      (is (str/includes? (:plain fixes) "Seuraavat kohteet vaativat korjauksia (1 kpl):"))
      (is (str/includes? (:plain fixes) "Halli — Tiivistelmä, Kuvaus"))
      (is (str/includes? (:plain fixes) "Lisäksi 2 kohdetta on katselmoitu ilman muutospyyntöjä."))
      (is (str/includes? (:html fixes) "<li>Halli — Tiivistelmä, Kuvaus</li>")))
    (t/testing "All-approved variant"
      (is (str/includes? (:subject all-ok) "valmis"))
      (is (str/includes? (:plain all-ok) "3 palvelua on katselmoitu ja hyväksytty"))
      (is (not (str/includes? (:plain all-ok) "vaativat korjauksia"))))))

(deftest get-ptv-managers-catalog-grant-test
  (t/testing "Catalog-granted ptv-manager is included alongside direct-role managers (F10)"
    ;; Catalog grants are projection-only (JWT, never persisted to
    ;; account.permissions) — the old implementation filtered stored account
    ;; roles only, so the catalog-granted member below was invisible (this
    ;; test's second assertion fails on the old code).
    (let [org (create-org! {:name "Catalog PTV Org"
                            :ptv-data {:org-id nil
                                       :city-codes [889]}})
          org-id (:id org)
          _ (backend-org/update-catalog!
              (test-db) org-id
              {:ptv {:label "PTV" :roles [{:role "ptv-manager" :city-code [889]}]}}
              nil)
          ;; direct (stored account role) manager — the pre-catalog mechanism
          direct (tu/gen-user {:db? true :db-component (test-db)
                               :permissions {:roles [{:role :ptv-manager
                                                      :city-code [889]}]}})
          _ (backend-org/add-member! (test-db) org-id (:id direct) {:roles []} nil)
          ;; catalog-granted manager — nothing on the account, only the
          ;; membership's template assignment
          catalog-user (tu/gen-user {:db? true :db-component (test-db)
                                     :permissions {:roles []}})
          _ (backend-org/add-member! (test-db) org-id (:id catalog-user) {:roles ["ptv"]} nil)
          ;; plain member — must NOT be listed
          plain (tu/gen-user {:db? true :db-component (test-db)
                              :permissions {:roles []}})
          _ (backend-org/add-member! (test-db) org-id (:id plain) {:roles []} nil)
          managers (ptv-core/get-ptv-managers (test-db) org-id)
          emails (set (map :email managers))]
      (is (contains? emails (:email direct)) "Direct-role manager still found")
      (is (contains? emails (:email catalog-user))
          "Catalog-granted manager found (old code: stored roles only — fails on old)")
      (is (not (contains? emails (:email plain))) "Plain member is not a manager"))))

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

;;; Archiving behaviour ;;;

(defn- sent-ptv [extra]
  (merge {:org-id "org-x" :source-id "src-1" :service-channel-ids ["chan-1"]
          :publishing-status "Published" :service-ids [] :sync-enabled true
          :languages ["fi"] :summary {:fi "summary"} :description {:fi "description"}}
         extra))

(deftest upsert-ptv-service-location-preserves-audit-test
  ;; :audit is server-owned and deliberately not in persisted-ptv-keys — a
  ;; sync (e.g. the municipality fixing a changes-requested text) must not
  ;; wipe the auditor's verdicts. Regression: tester observed an audited
  ;; site returning to "unaudited" after a fix + sync, because the rebuilt
  ;; :ptv meta dropped :audit.
  (let [audit {:timestamp "2026-07-01T00:00:00.000Z"
               :auditor-id "auditor-1"
               :summary {:status "changes-requested"
                         :feedback "Tarkenna tekstiä"
                         :audited-content {:fi "vanha tiivistelmä"}}}
        site {:lipas-id 12345 :name "Auditoitu halli" :status "active"
              :type {:type-code 1210}
              :location {:city {:city-code 425} :address "Katu 1" :postal-code "91900"
                         :geometries {:type "FeatureCollection"
                                      :features [{:type "Feature"
                                                  :geometry {:type "Point" :coordinates [25.0 65.0]}}]}}
              :search-meta {:location {:wgs84-point [25.0 65.0]}}
              ;; The DB copy of the site carries the audit...
              :ptv (sent-ptv {:audit audit})}
        published-resp {:id "chan-1" :sourceId "src-1" :publishingStatus "Published"
                        :services [] :serviceChannelNames [] :serviceChannelDescriptions []}]
    (with-redefs [core/enrich identity
                  ptv-integ/get-org-ptv-config-with-fallback (fn [_ _] {:supported-languages ["fi"]})
                  ptv-integ/get-org-service-channel (fn [_ _ _] published-resp)
                  ptv-integ/update-service-location (fn [_ _ _] published-resp)
                  ptv-integ/update-service-connections (fn [_ _ _ _] nil)]
      (let [[_ new-ptv-data]
            (ptv-core/upsert-ptv-service-location!*
              {} {:org-id "org-x"
                  :site site
                  ;; ...while the client's sync payload does not.
                  :ptv (dissoc (:ptv site) :audit)})]
        (is (= audit (:audit new-ptv-data))
            "Auditor's verdicts survive the sync")))))

(deftest to-archive?-decision-test
  (let [sent (fn [status & [ptv-extra]]
               {:status status :ptv (sent-ptv ptv-extra)})]
    (testing "Previously-published site is archived only on a genuine removal"
      (is (true?  (ptv-core/to-archive? (sent "out-of-service-permanently") {}))
          "status permanently-out archives")
      (is (true?  (ptv-core/to-archive? (sent "incorrect-data") {}))
          "status incorrect-data archives")
      (is (true?  (ptv-core/to-archive? (sent "active") {:delete-existing true}))
          "explicit :delete-existing archives"))
    (testing "Edits that previously auto-archived no longer do"
      (is (false? (ptv-core/to-archive? (sent "active") {}))
          "plain active save does not archive")
      (is (false? (ptv-core/to-archive?
                    {:status "active" :owner "private"
                     :ptv (sent-ptv nil)} {}))
          "owner change does not archive")
      (is (false? (ptv-core/to-archive?
                    (sent "active" {:summary {:fi ""} :description {:fi ""}}) {}))
          "blanked texts do not archive a published site"))
    (testing "Never-published site is never archived"
      (is (false? (ptv-core/to-archive?
                    {:status "out-of-service-permanently" :ptv {}} {}))))))

(deftest archive-preserves-ptv-link-test
  (let [channel-id "chan-1"
        site {:lipas-id 12345 :name "Arkistoitava" :status "out-of-service-permanently"
              :type {:type-code 1210}
              :location {:city {:city-code 425} :address "Katu 1" :postal-code "91900"
                         :geometries {:type "FeatureCollection"
                                      :features [{:type "Feature"
                                                  :geometry {:type "Point" :coordinates [25.0 65.0]}}]}}
              :search-meta {:location {:wgs84-point [25.0 65.0]}}
              :ptv (sent-ptv {:delete-existing true})}
        deleted-resp {:id channel-id :sourceId "src-1" :publishingStatus "Deleted"
                      :services [] :serviceChannelNames [] :serviceChannelDescriptions []}]
    (with-redefs [core/enrich identity
                  ptv-integ/get-org-ptv-config-with-fallback (fn [_ _] {:supported-languages ["fi"]})
                  ptv-integ/get-org-service-channel (fn [_ _ _] deleted-resp)
                  ptv-integ/update-service-location (fn [_ _ data] (assoc deleted-resp :publishingStatus (:publishingStatus data)))
                  ptv-integ/update-service-connections (fn [_ _ _ _] nil)]
      (let [[ptv-resp new-ptv-data]
            (ptv-core/upsert-ptv-service-location!*
              {} {:org-id "org-x" :site site :ptv (:ptv site) :archive? true})]
        (testing "Archive sends publishingStatus Deleted"
          (is (= "Deleted" (:publishingStatus ptv-resp))))
        (testing "LIPAS↔PTV link is preserved (source-id + channel-id kept)"
          (is (= "src-1" (:source-id new-ptv-data)))
          (is (= [channel-id] (:service-channel-ids new-ptv-data))))
        (testing "Stored publishing-status is Deleted (so is-sent-to-ptv? stays false)"
          (is (= "Deleted" (:publishing-status new-ptv-data)))
          (is (not (ptv-data/is-sent-to-ptv? {:ptv new-ptv-data}))))
        (testing "One-shot :delete-existing flag is cleared"
          (is (not (contains? new-ptv-data :delete-existing))))))))

(deftest resurrect-reuses-channel-test
  (let [channel-id "chan-1"
        calls (atom [])
        site {:lipas-id 12345 :name "Palautettava" :status "active"
              :type {:type-code 1210}
              :location {:city {:city-code 425} :address "Katu 1" :postal-code "91900"
                         :geometries {:type "FeatureCollection"
                                      :features [{:type "Feature"
                                                  :geometry {:type "Point" :coordinates [25.0 65.0]}}]}}
              :search-meta {:location {:wgs84-point [25.0 65.0]}}
              ;; Previously archived: link kept, status "Deleted".
              :ptv (sent-ptv {:publishing-status "Deleted"})}
        published-resp {:id channel-id :sourceId "src-1" :publishingStatus "Published"
                        :services [] :serviceChannelNames [] :serviceChannelDescriptions []}
        notfound (ex-info "HTTP Error: 404" {:resp {:status 404}})]
    (with-redefs [core/enrich identity
                  ptv-integ/get-org-ptv-config-with-fallback (fn [_ _] {:supported-languages ["fi"]})
                  ;; Archived channels 404 on every read endpoint.
                  ptv-integ/get-org-service-channel (fn [_ _ _] (swap! calls conj :get) (throw notfound))
                  ptv-integ/update-service-location (fn [_ _ data] (swap! calls conj :put) (assoc published-resp :publishingStatus (:publishingStatus data)))
                  ptv-integ/create-service-location (fn [_ _] (swap! calls conj :create) published-resp)
                  ptv-integ/update-service-connections (fn [_ _ _ _] nil)]
      (let [[ptv-resp new-ptv-data]
            (ptv-core/upsert-ptv-service-location!*
              {} {:org-id "org-x" :site site :ptv (:ptv site)})]
        (testing "Re-publishes the SAME channel (PUT), never creates a duplicate"
          (is (some #{:put} @calls))
          (is (not (some #{:create} @calls))))
        (testing "Pre-fetch 404 on the archived channel is tolerated"
          (is (= "Published" (:publishingStatus ptv-resp))))
        (testing "Channel-id is retained and status flips back to Published"
          (is (= [channel-id] (:service-channel-ids new-ptv-data)))
          (is (= "Published" (:publishing-status new-ptv-data))))))))

(comment
  (t/run-tests *ns*)
  (t/run-test-var #'init-site-ptv))
