(ns lipas.backend.ptv.sync-gate-test
  "The PTV part of a user-facing site save (`core/check-ptv-save!`), PTV org
   listing by PTV rights (`/actions/get-ptv-orgs`) and the PTV member
   suggestions.

   Background: sites were saved with `:ptv {:sync-enabled true}` but no org-id
   or texts by a city-scoped ptv-manager who wasn't an org member. The frontend
   listed orgs by membership, so none resolved; nothing validated `:ptv` on
   save; `sync-ptv!` skipped silently. Nothing reached PTV and nobody was told.

   PTV is never called: `sync-ptv!` is stubbed where a test needs a sync to
   'succeed', and the real one is only run for its LIPAS-side guards, which
   throw before any PTV request."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [lipas.backend.core :as core]
            [lipas.backend.email :as email]
            [lipas.backend.jwt :as jwt]
            [lipas.backend.org :as backend-org]
            [lipas.backend.ptv.core :as ptv-core]
            [lipas.backend.ptv.integration :as ptv-integ]
            [lipas.data.ptv :as ptv-data]
            [lipas.test-utils :as tu]
            [ring.mock.request :as mock]))

(defonce test-system (atom nil))

(let [{:keys [once each]} (tu/full-system-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn test-db [] (:lipas/db @test-system))
(defn test-app [req] ((:lipas/app @test-system) req))

(def ^:private own-city 91)
(def ^:private other-city 179) ; no PTV org covers it
(def ^:private ptv-org-a "3d1759a2-e47a-4947-9a31-cab1c1e2512b")
(def ^:private ptv-org-b "92374b0f-7d3c-4017-858e-666ee3ca2761")

(def ^:private texts
  {:summary {:fi "Lyhyt kuvaus paikasta"}
   :description {:fi "Pidempi kuvaus paikasta"}})

(defn- seed-org! [ptv-org-id city-codes]
  (backend-org/create-org
    (test-db)
    {:id (random-uuid)
     :name (str "Sync Gate Org " (rand-int 1000000))
     :data {}
     :ptv-data {:org-id ptv-org-id
                :city-codes city-codes
                :owners ["city" "city-main-owner"]
                :supported-languages ["fi"]}}))

(defn- seed-site!
  "An active, PTV-candidate site in `city-code`, saved by an admin."
  ([city-code] (seed-site! city-code nil))
  ([city-code ptv]
   (core/upsert-sports-site!*
     (test-db) (tu/gen-admin-user :db-component (test-db))
     (cond-> {:status "active"
              :event-date "2026-01-01T00:00:00.000Z"
              :name (str "Gate test site " (rand-int 1000000))
              :owner "city"
              :admin "city-sports"
              :type {:type-code 2510}
              :location {:city {:city-code city-code}
                         :address "Testitie 1"
                         :postal-code "00100"
                         :postal-office "Helsinki"
                         :geometries {:type "FeatureCollection"
                                      :features [{:type "Feature"
                                                  :geometry {:type "Point"
                                                             :coordinates [24.94 60.17]}}]}}}
       ptv (assoc :ptv ptv)))))

(defn- user-with-roles [roles]
  (tu/gen-user {:db? true :db-component (test-db) :admin? false
                :permissions {:roles roles}}))

(defn- ptv-manager [city-code]
  ;; Legacy plane: a direct, city-scoped role. Site editing comes from
  ;; city-manager; PTV rights from ptv-manager. No org membership.
  (user-with-roles [{:role "city-manager" :city-code [city-code]}
                    {:role "ptv-manager" :city-code [city-code]}]))

(defn- org-ptv-member
  "Org plane: no account roles; PTV and site-editing rights come from the
  `org`'s role-template catalog, projected into the token the way login does
  (`enrich-org-roles`)."
  [org city-code]
  (backend-org/update-catalog! (test-db) (:id org)
                               {:ptv {:roles [{:role "ptv-manager" :city-code [city-code]}
                                              {:role "city-manager" :city-code [city-code]}]}}
                               nil)
  (let [user (user-with-roles [])]
    (backend-org/add-member! (test-db) (:id org) (:id user) {:roles ["ptv"]} nil)
    (backend-org/enrich-org-roles (test-db) user)))

(defn- city-manager [city-code]
  (user-with-roles [{:role "city-manager" :city-code [city-code]}]))

(defn- post [path body user]
  (let [resp (test-app (-> (mock/request :post path)
                           (mock/content-type "application/json")
                           (mock/body (tu/->json body))
                           (tu/token-header (jwt/create-token user))))]
    (assoc resp :parsed (try (tu/<-json (:body resp)) (catch Exception _ nil)))))

(defn- save! [user site]
  (post "/api/sports-sites" (assoc site :event-date (str (java.time.Instant/now))) user))

(def ^:private sync-calls (atom []))

(defn- with-sync-stub
  "Replaces `ptv-core/sync-ptv!` (called via `resolve` from core) with a
   recorder that 'succeeds' without touching PTV."
  [f]
  (reset! sync-calls [])
  (with-redefs [ptv-core/sync-ptv! (fn [_tx _search _ptv _user args]
                                     (swap! sync-calls conj args)
                                     {:ptv (:ptv args)
                                      :event-date (-> args :sports-site :event-date)})]
    (f)))

(defn- current [lipas-id]
  (core/get-sports-site (test-db) lipas-id))

;;; Save gate ;;;

(deftest org-member-enables-sync-test
  (testing "an org member whose PTV rights come from the org catalog enables sync: org-id is derived"
    (let [org (seed-org! ptv-org-a [own-city])
          member (org-ptv-member org own-city)]
      (with-sync-stub
        (fn []
          (let [site (seed-site! own-city)
                resp (save! member (assoc site :ptv (merge {:sync-enabled true} texts)))]
            (is (= 201 (:status resp)))
            (is (= ptv-org-a (get-in (current (:lipas-id site)) [:ptv :org-id])))
            (is (= [ptv-org-a] (map :org-id @sync-calls)))))))))

(deftest legacy-ptv-manager-enables-sync-test
  (testing "a non-member, city-scoped ptv-manager enables sync: org-id is derived server-side"
    (seed-org! ptv-org-a [own-city])
    (with-sync-stub
      (fn []
        (let [site (seed-site! own-city)
              resp (save! (ptv-manager own-city)
                          (assoc site :ptv (merge {:sync-enabled true} texts)))]
          (is (= 201 (:status resp)))
          (is (= ptv-org-a (get-in (current (:lipas-id site)) [:ptv :org-id])))
          (is (= [ptv-org-a] (map :org-id @sync-calls)) "sync ran under the derived org"))))))

(deftest sync-on-without-texts-is-rejected-test
  (seed-org! ptv-org-a [own-city])
  (with-sync-stub
    (fn []
      (let [site (seed-site! own-city)
            resp (save! (ptv-manager own-city) (assoc site :ptv {:sync-enabled true}))]
        (is (= 400 (:status resp)))
        (is (= "ptv-sync-blocked" (get-in resp [:parsed :type])))
        (is (= ["ptv/missing-texts"] (get-in resp [:parsed :blockers])))
        (is (nil? (:ptv (current (:lipas-id site)))) "nothing was saved")
        (is (empty? @sync-calls))))))

(deftest sync-on-without-any-org-is-rejected-test
  (testing "no PTV org covers the city → no org can be derived"
    (with-sync-stub
      (fn []
        (let [site (seed-site! other-city)
              resp (save! (ptv-manager other-city)
                          (assoc site :ptv (merge {:sync-enabled true} texts)))]
          (is (= 400 (:status resp)))
          (is (= ["ptv/no-org"] (get-in resp [:parsed :blockers])))
          (is (empty? @sync-calls)))))))

(deftest foreign-org-id-is-rejected-test
  (testing "the client can't point a site at an org that doesn't cover it"
    (seed-org! ptv-org-a [own-city])
    (seed-org! ptv-org-b [other-city])
    (with-sync-stub
      (fn []
        (let [site (seed-site! own-city)
              resp (save! (ptv-manager own-city)
                          (assoc site :ptv (merge {:sync-enabled true :org-id ptv-org-b} texts)))]
          (is (= 400 (:status resp)))
          (is (= "ptv-org-mismatch" (get-in resp [:parsed :type])))
          (is (empty? @sync-calls)))))))

(deftest ptv-edits-need-ptv-rights-test
  (seed-org! ptv-org-a [own-city])
  (with-sync-stub
    (fn []
      (testing "a site editor without PTV rights can't change :ptv (used to trigger PTV writes)"
        (let [site (seed-site! own-city)
              resp (save! (city-manager own-city)
                          (assoc site :ptv (merge {:sync-enabled true :org-id ptv-org-a} texts)))]
          (is (= 403 (:status resp)))
          (is (empty? @sync-calls))))
      (testing "...but can keep editing an integrated site; it still re-syncs"
        (let [site (seed-site! own-city (merge {:sync-enabled true :org-id ptv-org-a
                                                :service-ids [] :service-channel-ids []}
                                               texts))
              resp (save! (city-manager own-city) (assoc site :name "Uusi nimi"))]
          (is (= 201 (:status resp)))
          (is (= 1 (count @sync-calls))))))))

(deftest untouched-unsyncable-ptv-doesnt-block-other-edits-test
  (testing "a pre-gate broken :ptv (sync on, no org/texts) doesn't block a name edit,
            and the sync failure is stored on the site instead of being skipped silently"
    (seed-org! ptv-org-a [own-city])
    ;; real sync-ptv!: its guard throws before any PTV request
    (let [site (seed-site! own-city {:sync-enabled true})
          resp (save! (city-manager own-city) (assoc site :name "Uusi nimi"))
          stored (current (:lipas-id site))]
      (is (= 201 (:status resp)))
      (is (= "Uusi nimi" (:name stored)))
      (is (= ["ptv/no-org"] (map name (get-in stored [:ptv :error :blockers])))))))

;;; Site editors without PTV rights ;;;

(def ^:private integrated-ptv
  "`:ptv` of a site already published in PTV."
  (merge {:sync-enabled true :org-id ptv-org-a
          :service-ids ["service-1"]
          :publishing-status "Published"
          :last-sync "2026-01-13T06:06:39.000Z"
          :source-id "lipas-src-1"
          :service-channel-ids ["channel-1"]
          :previous-type-code 2510}
         texts))

(defn- org-editor
  "Org plane counterpart of `city-manager`: site editing from the org's
  catalog, no PTV rights."
  [org city-code]
  (backend-org/update-catalog! (test-db) (:id org)
                               {:editor {:roles [{:role "city-manager" :city-code [city-code]}]}}
                               nil)
  (let [user (user-with-roles [])]
    (backend-org/add-member! (test-db) (:id org) (:id user) {:roles ["editor"]} nil)
    (backend-org/enrich-org-roles (test-db) user)))

(deftest no-ptv-rights-cant-change-integrated-site-ptv-test
  (let [org (seed-org! ptv-org-a [own-city])]
    (doseq [[label user] [["legacy city-manager" (city-manager own-city)]
                          ["org editor" (org-editor org own-city)]]
            [edit f] [["sync off" #(assoc % :sync-enabled false)]
                      ["summary" #(assoc-in % [:summary :fi] "Muokattu kuvaus")]
                      ["description" #(assoc-in % [:description :fi] "Muokattu pitkä kuvaus")]
                      ["user instruction" #(assoc % :user-instruction {:fi "Ohje"})]
                      ["services" #(assoc % :service-ids ["service-2"])]
                      ["org" #(assoc % :org-id ptv-org-b)]
                      ["delete-existing" #(assoc % :delete-existing true)]
                      ["dropped :ptv" (constantly nil)]]]
      (testing (str label " → " edit)
        (with-sync-stub
          (fn []
            (let [site (seed-site! own-city integrated-ptv)
                  resp (save! user (-> site
                                       (assoc :name "Uusi nimi")
                                       (update :ptv f)))
                  stored (current (:lipas-id site))]
              (is (= 403 (:status resp)))
              (is (= (:name site) (:name stored)) "nothing was saved")
              (is (= integrated-ptv (:ptv stored)))
              (is (empty? @sync-calls)))))))))

(deftest no-ptv-rights-edit-keeps-ptv-and-resyncs-test
  (seed-org! ptv-org-a [own-city])
  (with-sync-stub
    (fn []
      (testing "a name edit by a site editor without PTV rights keeps :ptv as stored
                and re-syncs under the site's org, even when the browser's copy of
                the sync's record is stale or forged"
        (let [site (seed-site! own-city integrated-ptv)
              resp (save! (city-manager own-city)
                          (-> site
                              (assoc :name "Uusi nimi")
                              (update :ptv assoc
                                      :publishing-status "Draft"
                                      :last-sync "2020-01-01T00:00:00.000Z"
                                      :source-id "forged"
                                      :service-channel-ids [])))
              stored (current (:lipas-id site))
              [call] @sync-calls]
          (is (= 201 (:status resp)))
          (is (= "Uusi nimi" (:name stored)))
          (is (= integrated-ptv (:ptv stored)))
          (is (= 1 (count @sync-calls)))
          (is (= ptv-org-a (:org-id call)))
          (is (= integrated-ptv (:ptv call)) "the sync got the stored settings and PTV ids"))))))

(deftest no-ptv-rights-materialized-defaults-arent-an-edit-test
  (testing "the site form materializes empty :ptv defaults on a site never in PTV;
            a site editor without PTV rights can still save it"
    (seed-org! ptv-org-a [own-city])
    (with-sync-stub
      (fn []
        (let [site (seed-site! own-city)
              resp (save! (city-manager own-city)
                          (assoc site
                                 :name "Uusi nimi"
                                 :ptv {:sync-enabled false
                                       :delete-existing false
                                       :service-ids []
                                       :summary {:fi ""}
                                       :description {:fi ""}}))]
          (is (= 201 (:status resp)))
          (is (= "Uusi nimi" (:name (current (:lipas-id site)))))
          (is (empty? @sync-calls)))))))

;;; Full sync against a fake PTV ;;;

;; The real `sync-ptv!` runs; only `ptv-integ/http`, the single function every
;; PTV request goes through, is replaced. The PTV test environment is shared
;; and changes under us, so tests never call it. Response shapes follow what
;; it returned (2026-10-12, create → rename → archive → GET of a throwaway
;; channel): a channel GET is the full entity with services as
;; `{:service {:id :name}}`; a ServiceLocation POST/PUT, the status-only
;; archive included, answers with the full updated channel; an archived
;; channel 404s on reads.

(def ^:private ptv-requests (atom []))

(defn- ptv-channel [id {:keys [services publishing-status name]}]
  {:id id
   :sourceId "lipas-src-1"
   :organizationId ptv-org-a
   :serviceChannelType "ServiceLocation"
   :publishingStatus publishing-status
   :serviceChannelNames [{:language "fi" :value name :type "Name"}]
   :serviceChannelDescriptions []
   :services (mapv (fn [sid] {:service {:id sid :name "Palvelu"}}) services)})

(defn- with-fake-ptv
  "Runs `f` against a PTV holding one published channel-1 linked to
  service-1, recording each request as [auth-org-id method path body]. `fail`
  answers the ServiceLocation PUT with that HTTP status instead."
  ([f] (with-fake-ptv nil f))
  ([fail f]
   (reset! ptv-requests [])
   (let [channels (atom {"channel-1" (ptv-channel "channel-1" {:services ["service-1"]
                                                               :publishing-status "Published"
                                                               :name "Vanha nimi"})})
         http-error (fn [status] (ex-info (str "HTTP Error: " status)
                                          {:resp {:status status :body {:message "fake"}}}))]
     (with-redefs [ptv-integ/http
                   (fn [_ptv auth-org-id {:keys [url method form-params]}]
                     (let [path (subs url (str/index-of url "/v11/"))]
                       (swap! ptv-requests conj [auth-org-id method path form-params])
                       (condp re-matches path
                         #"/v11/ServiceChannel/ServiceLocation/(.+)"
                         :>> (fn [[_ id]]
                               (when fail (throw (http-error fail)))
                               (let [ch (swap! channels update id
                                               (fn [ch]
                                                 (cond-> (assoc ch :publishingStatus (:publishingStatus form-params))
                                                   (:serviceChannelNames form-params)
                                                   (assoc :serviceChannelNames (:serviceChannelNames form-params)))))]
                                 {:body (get ch id)}))

                         #"/v11/ServiceChannel/(.+)"
                         :>> (fn [[_ id]]
                               (let [ch (get @channels id)]
                                 (if (and ch (not= "Deleted" (:publishingStatus ch)))
                                   {:body ch}
                                   (throw (http-error 404)))))

                         (throw (ex-info (str "Fake PTV doesn't serve " method " " path) {})))))]
       (f)))))

(deftest no-ptv-rights-edit-syncs-to-ptv-test
  (testing "a site editor without PTV rights renames an integrated site: the
            stored settings are written to the site's existing channel, under
            the site's org, and the sync's result is stored"
    (seed-org! ptv-org-a [own-city])
    (with-fake-ptv
      (fn []
        (let [site (seed-site! own-city integrated-ptv)
              resp (save! (city-manager own-city) (assoc site :name "Uusi nimi"))
              stored (current (:lipas-id site))
              writes (remove (comp #{:get} second) @ptv-requests)
              [[org-id _ path body]] writes]
          (is (= 201 (:status resp)))
          (is (= 1 (count writes)) "one PUT, no create, no service connection changes")
          (is (= ptv-org-a org-id))
          (is (= "/v11/ServiceChannel/ServiceLocation/channel-1" path))
          (is (= "lipas-src-1" (:sourceId body)))
          (is (= "Published" (:publishingStatus body)))
          (is (some #{{:language "fi" :value "Uusi nimi" :type "Name"}}
                    (:serviceChannelNames body)))
          (is (some #(= "Pidempi kuvaus paikasta" (:value %))
                    (:serviceChannelDescriptions body)))
          (testing "stored after the sync"
            (is (= "Uusi nimi" (:name stored)))
            (is (= (select-keys integrated-ptv ptv-data/user-controlled-ptv-keys)
                   (select-keys (:ptv stored) ptv-data/user-controlled-ptv-keys)))
            (is (= "Published" (get-in stored [:ptv :publishing-status])))
            (is (= ["channel-1"] (get-in stored [:ptv :service-channel-ids])))
            (is (not= (:last-sync integrated-ptv) (get-in stored [:ptv :last-sync])))
            (is (nil? (get-in stored [:ptv :error])))))))))

(deftest no-ptv-rights-removal-archives-in-ptv-test
  (seed-org! ptv-org-a [own-city])
  (doseq [status ["out-of-service-permanently" "incorrect-data"]]
    (testing (str "a site editor without PTV rights sets an integrated site "
                  status ": its channel is archived with a status-only PUT")
      (with-fake-ptv
        (fn []
          (let [site (seed-site! own-city integrated-ptv)
                resp (save! (city-manager own-city) (assoc site :status status))
                stored (current (:lipas-id site))
                writes (remove (comp #{:get} second) @ptv-requests)]
            (is (= 201 (:status resp)))
            (is (= [[ptv-org-a :put "/v11/ServiceChannel/ServiceLocation/channel-1"
                     {:publishingStatus "Deleted"}]]
                   writes))
            (is (= status (:status stored)))
            (is (= "Deleted" (get-in stored [:ptv :publishing-status])))
            (testing "the LIPAS↔PTV link survives, so re-activating re-publishes the same channel"
              (is (= "lipas-src-1" (get-in stored [:ptv :source-id])))
              (is (= ["channel-1"] (get-in stored [:ptv :service-channel-ids]))))))))))

(deftest no-ptv-rights-edit-survives-ptv-failure-test
  (testing "PTV failing doesn't fail a save by a site editor without PTV rights;
            the edit is stored and the error is shown on the site"
    (seed-org! ptv-org-a [own-city])
    (with-fake-ptv 500
      (fn []
        (let [site (seed-site! own-city integrated-ptv)
              resp (save! (city-manager own-city) (assoc site :name "Uusi nimi"))
              stored (current (:lipas-id site))]
          (is (= 201 (:status resp)))
          (is (= "Uusi nimi" (:name stored)))
          (is (some? (get-in stored [:ptv :error :message])))
          (testing "the sync's record of what is in PTV is unchanged"
            (is (= "Published" (get-in stored [:ptv :publishing-status])))
            (is (= (:last-sync integrated-ptv) (get-in stored [:ptv :last-sync])))
            (is (= ["channel-1"] (get-in stored [:ptv :service-channel-ids])))))))))

;;; Server-owned :ptv keys ;;;

(deftest stale-ptv-lifecycle-keys-are-ignored-test
  (testing "a save carrying an outdated copy of the PTV record can't roll it back
            (prod: archived, then marked incorrect-data with the pre-archive :ptv,
            leaving LIPAS saying Published)"
    (seed-org! ptv-org-a [own-city])
    (with-sync-stub
      (fn []
        (let [published (merge {:sync-enabled true :org-id ptv-org-a
                                :publishing-status "Published"
                                :last-sync "2025-11-13T08:33:46.000Z"
                                :source-id "lipas-src-1"
                                :service-channel-ids ["channel-1"]
                                :service-ids ["service-1"]}
                               texts)
              ;; what the archive sync stored
              archived (-> published
                           (assoc :publishing-status "Deleted"
                                  :last-sync "2026-01-13T06:06:39.000Z")
                           (dissoc :source-id :service-channel-ids))
              site (seed-site! own-city archived)
              resp (save! (ptv-manager own-city)
                          (assoc site :status "incorrect-data" :ptv published))
              stored (:ptv (current (:lipas-id site)))]
          (is (= 201 (:status resp)))
          (is (= "Deleted" (:publishing-status stored)))
          (is (= "2026-01-13T06:06:39.000Z" (:last-sync stored)))
          (is (nil? (:source-id stored)))
          (is (empty? (:service-channel-ids stored)))
          (is (empty? @sync-calls) "nothing in PTV to archive any more"))))
    (testing "...and a client can't claim lifecycle state for a site never in PTV"
      (with-sync-stub
        (fn []
          (let [site (seed-site! own-city)
                _ (save! (ptv-manager own-city)
                         (assoc site :ptv {:sync-enabled false
                                           :publishing-status "Published"
                                           :source-id "forged"}))
                stored (:ptv (current (:lipas-id site)))]
            (is (nil? (:publishing-status stored)))
            (is (nil? (:source-id stored)))))))))

(deftest browser-clock-ahead-cant-outrank-ptv-sync-test
  (testing "closing a site from a browser whose clock runs ahead: the PTV sync's
            archive revision stays current (prod: the user's browser-dated,
            pre-archive revision won and was re-marked incorrect-data)"
    (seed-org! ptv-org-a [own-city])
    (let [published (merge {:sync-enabled true :org-id ptv-org-a
                            :publishing-status "Published"
                            :last-sync "2026-01-01T00:00:00.000Z"
                            :source-id "lipas-src-1"
                            :service-channel-ids ["channel-1"]
                            :service-ids ["service-1"]}
                           texts)
          site (seed-site! own-city published)
          ahead (str (.plusSeconds (java.time.Instant/now) 30))]
      ;; like the real sync: archive, then write a server-dated revision
      (with-redefs [ptv-core/sync-ptv! (fn [tx _search _ptv user {:keys [sports-site ptv]}]
                                         (let [now (str (java.time.Instant/now))
                                               archived (assoc ptv :publishing-status "Deleted"
                                                               :last-sync now)]
                                           (core/upsert-sports-site! tx user (assoc sports-site
                                                                                    :event-date now
                                                                                    :ptv archived)
                                                                     false)
                                           {:ptv archived :event-date now}))]
        (is (= 201 (:status (post "/api/sports-sites"
                                  (assoc site
                                         :status "out-of-service-permanently"
                                         :event-date ahead)
                                  (ptv-manager own-city))))))
      (let [stored (current (:lipas-id site))]
        (is (= "out-of-service-permanently" (:status stored)))
        (is (= "Deleted" (get-in stored [:ptv :publishing-status])))))))

;;; Org listing ;;;

(deftest get-ptv-orgs-test
  (let [org-a (seed-org! ptv-org-a [own-city])
        org-b (seed-org! ptv-org-b [other-city])
        ids (fn [resp] (set (map :id (:parsed resp))))]
    (testing "a non-member ptv-manager gets the org covering their city — and only it"
      (let [resp (post "/api/actions/get-ptv-orgs" {} (ptv-manager own-city))]
        (is (= 200 (:status resp)))
        (is (= #{(str (:id org-a))} (ids resp)))))
    (testing "an org member with catalog PTV rights gets the same org"
      (let [resp (post "/api/actions/get-ptv-orgs" {} (org-ptv-member org-a own-city))]
        (is (= 200 (:status resp)))
        (is (= #{(str (:id org-a))} (ids resp)))))
    (testing "admins get every PTV org"
      (let [resp (post "/api/actions/get-ptv-orgs" {}
                       (tu/gen-admin-user :db-component (test-db)))]
        (is (every? (ids resp) [(str (:id org-a)) (str (:id org-b))]))))
    (testing "no secrets or member lists leave the server"
      (let [resp (post "/api/actions/get-ptv-orgs" {} (ptv-manager own-city))
            org (first (:parsed resp))]
        (is (= #{:id :name :ptv-data} (set (keys org))))
        (is (not (contains? (:ptv-data org) :test-credentials)))))
    (testing "users without PTV rights are refused"
      (is (= 403 (:status (post "/api/actions/get-ptv-orgs" {} (city-manager own-city))))))))

;;; Member suggestions ;;;

(deftest ptv-member-suggestions-test
  (let [org (seed-org! ptv-org-a [own-city])
        org-id (str (:id org))
        manager (ptv-manager own-city)
        admin (tu/gen-admin-user :db-component (test-db))
        suggested (fn [] (set (map :user-id (:parsed (post "/api/actions/get-ptv-member-suggestions"
                                                           {:org-id org-id} admin)))))]
    (testing "the non-member manager is suggested, without an email"
      (is (contains? (suggested) (str (:id manager))))
      (is (not-any? :email (:parsed (post "/api/actions/get-ptv-member-suggestions"
                                          {:org-id org-id} admin)))))
    (testing "PTV rights from another org's template count too (same rule as the admin list)"
      (let [other-org (backend-org/create-org (test-db) {:id (random-uuid)
                                                         :name (str "No-PTV Org " (rand-int 1000000))
                                                         :data {}})
            via-other (org-ptv-member other-org own-city)]
        (is (contains? (suggested) (str (:id via-other))))))
    (testing "adding makes them a plain member, silently, and drops them from the suggestions"
      (let [mails (atom [])]
        (with-redefs [email/send-org-added-email! (fn [_ to _] (swap! mails conj to))
                      email/send-org-invitation-email! (fn [_ to _] (swap! mails conj to))]
          (is (= 200 (:status (post "/api/actions/add-suggested-ptv-member"
                                    {:org-id org-id :user-id (str (:id manager))} admin)))))
        (is (empty? @mails) "no 'added to an organization' email: users don't see org views"))
      (let [member (some #(when (= (str (:id manager)) (:user-id %)) %)
                         (:members (backend-org/get-org (test-db) (:id org))))]
        (is (some? member))
        (is (empty? (:roles member))))
      (is (not (contains? (suggested) (str (:id manager))))))
    (testing "only suggested accounts can be added by id"
      (let [stranger (city-manager other-city)]
        (is (= 400 (:status (post "/api/actions/add-suggested-ptv-member"
                                  {:org-id org-id :user-id (str (:id stranger))} admin))))))
    (testing "non-admins of the org can't list or add"
      (is (= 403 (:status (post "/api/actions/get-ptv-member-suggestions"
                                {:org-id org-id} manager)))))))

;;; Admin PTV user list ;;;

(deftest admin-ptv-user-list-test
  (testing "/users carries org memberships and org-granted roles, and the PTV
            user list derived from it covers both role models"
    (let [org (seed-org! ptv-org-a [own-city])
          org-id (str (:id org))
          legacy (ptv-manager own-city)
          member (org-ptv-member org own-city)
          admin (tu/gen-admin-user :db-component (test-db))
          users (tu/<-json (:body (test-app (-> (mock/request :get "/api/users")
                                                (tu/token-header (jwt/create-token admin))))))
          ptv-orgs (:parsed (post "/api/actions/get-ptv-orgs" {} admin))
          rows (ptv-data/ptv-user-rows users ptv-orgs)
          row-of (fn [user] (some #(when (= (str (:id user)) (:user-id %)) %) rows))
          member-account (some #(when (= (str (:id member)) (str (:id %))) %) users)]
      (testing "org-granted roles stay out of the stored :permissions"
        (is (= [] (get-in member-account [:permissions :roles])))
        (is (= [org-id] (map :id (:orgs member-account))))
        (is (some #(= "ptv-manager" (:role %)) (:org-roles member-account))))
      (testing "legacy: direct role, not a member of the covering org"
        (is (= {:direct? true :via-org? false :member? false :org-id org-id :city-codes [own-city]}
               (-> (row-of legacy)
                   (select-keys [:direct? :via-org? :member? :city-codes])
                   (assoc :org-id (get-in (row-of legacy) [:org :id]))))))
      (testing "org member: rights from the org template, already a member"
        (is (= {:direct? false :via-org? true :member? true :org-id org-id :city-codes [own-city]}
               (-> (row-of member)
                   (select-keys [:direct? :via-org? :member? :city-codes])
                   (assoc :org-id (get-in (row-of member) [:org :id])))))))))

