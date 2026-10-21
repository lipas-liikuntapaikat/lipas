(ns lipas.backend.ptv.sync-review-test
  "The PTV sync review's backend: finding what a site's last sync sent (the
   base the review tells PTV's changes from Lipas's by), and who may fetch it.
   The site form's review is open to whoever may edit the site, because
   saving a site with PTV sync on syncs it over PTV."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [lipas.backend.core :as core]
            [lipas.backend.jwt :as jwt]
            [lipas.backend.org :as backend-org]
            [lipas.backend.ptv.core :as ptv-core]
            [lipas.backend.ptv.integration :as ptv-integration]
            [lipas.test-utils :as tu]
            [ring.mock.request :as mock]))

(defonce test-system (atom nil))

(let [{:keys [once each]} (tu/full-system-fixture test-system)]
  (use-fixtures :once once)
  (use-fixtures :each each))

(defn- test-db [] (:lipas/db @test-system))
(defn- test-app [req] ((:lipas/app @test-system) req))

(def ^:private own-city 91)
(def ^:private other-city 179)
(def ^:private ptv-org-id "3d1759a2-e47a-4947-9a31-cab1c1e2512b")
(def ^:private channel-id "a1b2c3d4-0001-4abc-9def-000000000001")

(defn- seed-org! []
  (backend-org/create-org
    (test-db)
    {:id (random-uuid)
     :name (str "PTV Review Org " (rand-int 1000000))
     :data {}
     :ptv-data {:org-id ptv-org-id
                :city-codes [own-city]
                :owners ["city"]
                :supported-languages ["fi"]}}))

(defn- site
  [lipas-id event-date summary last-sync]
  {:lipas-id lipas-id
   :status "active"
   :event-date event-date
   :name "Oulunkylän tekojää"
   :owner "city"
   :admin "city-sports"
   :type {:type-code 2510}
   :location {:city {:city-code own-city}
              :address "Käärmetie 8"
              :postal-code "00640"
              :postal-office "Helsinki"
              :geometries {:type "FeatureCollection"
                           :features [{:type "Feature"
                                       :geometry {:type "Point"
                                                  :coordinates [24.9612 60.2289]}}]}}
   :ptv (cond-> {:org-id ptv-org-id
                 :sync-enabled true
                 :summary {:fi summary}
                 :description {:fi "Pidempi kuvaus"}
                 :service-ids ["svc-a"]
                 :service-channel-ids [channel-id]}
          last-sync (assoc :last-sync last-sync))})

(defn- save! [user site]
  (core/upsert-sports-site!* (test-db) user site))

(deftest sync-base-is-the-syncs-own-revision-test
  (let [admin (tu/gen-admin-user :db-component (test-db))
        lipas-id (:lipas-id (save! admin (site nil "2026-01-01T00:00:00.000Z" "Ennen" nil)))
        base #(get (ptv-core/get-ptv-sync-bases (test-db) [lipas-id]) lipas-id)]
    (testing "a site never synced has no base"
      (is (nil? (base))))

    ;; A sync writes a revision dated with its own last-sync; later edits
    ;; carry that last-sync forward.
    (save! admin (site lipas-id "2026-02-01T10:00:00.123456Z" "Synkattu" "2026-02-01T10:00:00.123456Z"))
    (save! admin (site lipas-id "2026-03-01T00:00:00.000Z" "Muokattu sen jälkeen" "2026-02-01T10:00:00.123456Z"))

    (testing "the base is what the sync sent, not a later edit"
      (is (= "Synkattu" (get-in (base) [:ptv :summary :fi])))
      (is (= "2026-02-01T10:00:00.123456Z" (get-in (base) [:ptv :last-sync]))))

    (testing "a backdated edit made after the sync doesn't pass for it"
      (save! admin (site lipas-id "2026-01-15T00:00:00.000Z" "Taannehtiva" "2026-02-01T10:00:00.123456Z"))
      (is (= "Synkattu" (get-in (base) [:ptv :summary :fi]))))

    (testing "the next sync replaces the base"
      (save! admin (site lipas-id "2026-04-01T08:00:00.000Z" "Toinen synkka" "2026-04-01T08:00:00.000Z"))
      (is (= "Toinen synkka" (get-in (base) [:ptv :summary :fi]))))

    (testing "only what the review compares is sent"
      (is (= #{:lipas-id :name :ptv} (set (keys (base)))))
      (is (not (contains? (:ptv (base)) :sync-enabled))))))

;;; HTTP ;;;

(defn- post* [path body token]
  (test-app (cond-> (-> (mock/request :post path)
                        (mock/content-type "application/json")
                        (mock/body (tu/->json body)))
              token (tu/token-header token))))

(defn- user-with-role [role city-code]
  (tu/gen-user {:db? true
                :db-component (test-db)
                :admin? false
                :permissions {:roles [{:role role :city-code [city-code]}]}}))

(def ^:private ptv-calls (atom []))

(defn- with-ptv-stub [f]
  (reset! ptv-calls [])
  (with-redefs [ptv-integration/get-org-service-channel
                (fn [_ptv org-id id]
                  (swap! ptv-calls conj [:channel org-id id])
                  {:id id
                   :sourceId "lipas-source"
                   :serviceChannelNames [{:type "Name" :language "fi" :value "Oulunkylän tekojää"}]
                   :serviceChannelDescriptions []
                   :services [{:service {:id "svc-b" :name "Luistelu"}}]})
                ptv-integration/get-service
                (fn [_ptv _org-id id]
                  (swap! ptv-calls conj [:service id])
                  {:id id :serviceNames [{:type "Name" :language "fi" :value "Jäähallit"}]})
                ptv-integration/get-org-ptv-config-with-fallback
                (fn [_ptv _org-id] {:supported-languages ["fi"]})]
    (f)))

(deftest fetch-sync-bases-test
  (let [_org (seed-org!)
        admin (tu/gen-admin-user :db-component (test-db))
        lipas-id (:lipas-id (save! admin (site nil "2026-02-01T10:00:00.000Z" "Synkattu" "2026-02-01T10:00:00.000Z")))
        body {:lipas-ids [lipas-id]}]
    (testing "a PTV manager gets the bases"
      (let [resp (post* "/api/actions/fetch-ptv-sync-bases" body
                        (jwt/create-token (user-with-role "ptv-manager" own-city)))]
        (is (= 200 (:status resp)))
        (is (= "Synkattu" (get-in (tu/<-json (:body resp)) [(keyword (str lipas-id)) :ptv :summary :fi])))))
    (testing "a user without PTV rights doesn't"
      (is (= 403 (:status (post* "/api/actions/fetch-ptv-sync-bases" body
                                 (jwt/create-token (user-with-role "city-manager" own-city)))))))
    (testing "nor does an anonymous caller"
      (is (= 401 (:status (post* "/api/actions/fetch-ptv-sync-bases" body nil)))))))

(deftest fetch-site-review-test
  (let [_org (seed-org!)
        admin (tu/gen-admin-user :db-component (test-db))
        lipas-id (:lipas-id (save! admin (site nil "2026-02-01T10:00:00.000Z" "Synkattu" "2026-02-01T10:00:00.000Z")))
        body {:lipas-id lipas-id}
        status (fn [user]
                 (:status (post* "/api/actions/fetch-ptv-site-review" body
                                 (some-> user jwt/create-token))))]
    (with-ptv-stub
      (fn []
        (testing "whoever may edit the site gets the review: a save syncs it"
          (let [resp (post* "/api/actions/fetch-ptv-site-review" body
                            (jwt/create-token (user-with-role "city-manager" own-city)))
                review (tu/<-json (:body resp))]
            (is (= 200 (:status resp)))
            (is (= "Synkattu" (get-in review [:base :ptv :summary :fi])))
            (is (= channel-id (get-in review [:channel :id])))
            (is (= [:svc-a] (keys (:services review)))
                "names are read for the services only Lipas connects (JSON keywordises the ids)")
            (is (= ["fi"] (:org-langs review)))))

        (testing "and the municipality's PTV manager"
          (is (= 200 (status (user-with-role "ptv-manager" own-city)))))

        (testing "nobody else"
          (reset! ptv-calls [])
          (is (= 403 (status (user-with-role "city-manager" other-city))))
          (is (= 403 (status (user-with-role "ptv-manager" other-city))))
          (is (= 401 (status nil)))
          (is (empty? @ptv-calls) "no PTV call for a refused request"))))))

(deftest sync-writes-chosen-names-test
  (let [_org (seed-org!)
        admin (tu/gen-admin-user :db-component (test-db))
        lipas-id (:lipas-id (save! admin (site nil "2026-02-01T10:00:00.000Z" "Synkattu" "2026-02-01T10:00:00.000Z")))
        sync! (fn [user]
                (with-redefs [ptv-integration/get-org-service-channel
                              (fn [_ _ id] {:id id :sourceId "lipas-source" :services []})
                              ptv-integration/update-service-location
                              (fn [_ id _] {:id id :sourceId "lipas-source" :publishingStatus "Published"})
                              ptv-integration/get-org-ptv-config-with-fallback
                              (fn [_ _] {:supported-languages ["fi"]})]
                  (ptv-core/upsert-ptv-service-location!
                    (test-db) (:lipas/ptv @test-system) (:lipas/search @test-system) user
                    {:lipas-id lipas-id
                     :org-id ptv-org-id
                     :ptv {:org-id ptv-org-id
                           :sync-enabled true
                           :service-channel-ids [channel-id]
                           :service-ids []
                           :summary {:fi "Synkattu"}
                           :description {:fi "Pidempi kuvaus"}}
                     :site-names {:name "PTV:n nimi" :marketing-name "Tekojää"}})))]
    (testing "taking PTV's name needs the right to edit the site"
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"needs the right to edit"
                            (sync! (user-with-role "ptv-manager" own-city))))
      (is (= "Oulunkylän tekojää" (:name (core/get-sports-site (test-db) lipas-id)))))

    (testing "with it, the sync writes the names into the site"
      (sync! admin)
      (let [saved (core/get-sports-site (test-db) lipas-id)]
        (is (= "PTV:n nimi" (:name saved)))
        (is (= "Tekojää" (:marketing-name saved)))))))

(comment
  (clojure.test/run-tests *ns*))
